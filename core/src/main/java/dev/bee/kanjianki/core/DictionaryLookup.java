package dev.bee.kanjianki.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DictionaryLookup {
    public static final String SOURCE_JMDICT = "JMdict_e";
    public static final String SOURCE_KANJIDIC2 = "KANJIDIC2";
    public static final String SOURCE_ANKI = "anki";
    private static final String LIST_SEPARATOR = "\u001f";

    private final Map<String, List<WordEntry>> wordsByExpressionReading;
    private final Map<String, List<WordEntry>> wordsByExpression;
    private final Map<String, KanjiEntry> kanjiByLiteral;

    public DictionaryLookup(List<WordEntry> words, List<KanjiEntry> kanji) {
        Map<String, List<WordEntry>> byExpressionReading = new HashMap<>();
        Map<String, List<WordEntry>> byExpression = new HashMap<>();
        for (WordEntry entry : words == null ? Collections.<WordEntry>emptyList() : words) {
            byExpressionReading.computeIfAbsent(wordKey(entry.expression, entry.reading), ignored -> new ArrayList<>()).add(entry);
            byExpression.computeIfAbsent(normalize(entry.expression), ignored -> new ArrayList<>()).add(entry);
        }
        for (List<WordEntry> entries : byExpressionReading.values()) {
            entries.sort(WordEntry::compareTo);
        }
        for (List<WordEntry> entries : byExpression.values()) {
            entries.sort(WordEntry::compareTo);
        }
        Map<String, KanjiEntry> byLiteral = new HashMap<>();
        for (KanjiEntry entry : kanji == null ? Collections.<KanjiEntry>emptyList() : kanji) {
            byLiteral.put(entry.literal, entry);
        }
        this.wordsByExpressionReading = freezeListMap(byExpressionReading);
        this.wordsByExpression = freezeListMap(byExpression);
        this.kanjiByLiteral = Collections.unmodifiableMap(byLiteral);
    }

    public static DictionaryLookup empty() {
        return new DictionaryLookup(Collections.emptyList(), Collections.emptyList());
    }

    public static DictionaryLookup fromTsv(InputStream words, InputStream kanji) throws IOException {
        return new DictionaryLookup(readWords(words), readKanji(kanji));
    }

    public StudyCue studyCue(
            String kanji,
            String ankiMeaning,
            String rowReading,
            String sourceExpression,
            String sourceReading
    ) {
        String expression = normalize(sourceExpression);
        String reading = normalizeReading(sourceReading);
        WordEntry word = lookupWord(expression, reading);
        KanjiEntry kanjiEntry = kanjiByLiteral.get(normalize(kanji));
        String cueReading = firstNonEmpty(
                word == null ? "" : word.reading,
                sourceReading,
                rowReading,
                kanjiEntry == null ? "" : kanjiEntry.firstReading()
        );
        String fromExpression = expression.isEmpty() && word != null ? word.expression : expression;
        if (kanjiEntry != null) {
            return new StudyCue(
                    StudyCueFormatter.displayGlosses(kanjiEntry.meanings, 2),
                    cueReading,
                    fromExpression,
                    SOURCE_KANJIDIC2
            );
        }
        return new StudyCue(
                StudyCueFormatter.cleanFallbackMeaning(ankiMeaning, "", 96),
                cueReading,
                fromExpression,
                SOURCE_ANKI
        );
    }

    public WordEntry lookupWord(String expression, String reading) {
        String normalizedExpression = normalize(expression);
        if (normalizedExpression.isEmpty()) {
            return null;
        }
        String normalizedReading = normalizeReading(reading);
        if (!normalizedReading.isEmpty()) {
            WordEntry exact = first(wordsByExpressionReading.get(wordKey(normalizedExpression, normalizedReading)));
            if (exact != null) {
                return exact;
            }
        }
        return unambiguous(wordsByExpression.get(normalizedExpression));
    }

    public KanjiEntry lookupKanji(String literal) {
        return kanjiByLiteral.get(normalize(literal));
    }

    public int wordCount() {
        int count = 0;
        Set<WordEntry> seen = new HashSet<>();
        for (List<WordEntry> entries : wordsByExpressionReading.values()) {
            for (WordEntry entry : entries) {
                if (seen.add(entry)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int kanjiCount() {
        return kanjiByLiteral.size();
    }

    private static WordEntry first(List<WordEntry> entries) {
        return entries == null || entries.isEmpty() ? null : entries.get(0);
    }

    private static WordEntry unambiguous(List<WordEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        WordEntry first = entries.get(0);
        for (WordEntry entry : entries) {
            if (!first.sameMeaning(entry)) {
                return null;
            }
        }
        return first;
    }

    private static List<WordEntry> readWords(InputStream input) throws IOException {
        if (input == null) {
            return Collections.emptyList();
        }
        List<WordEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("expression\t")) {
                    continue;
                }
                String[] cells = line.split("\t", -1);
                if (cells.length < 6) {
                    continue;
                }
                entries.add(new WordEntry(
                        cells[0],
                        cells[1],
                        splitList(cells[2]),
                        splitList(cells[3]),
                        splitList(cells[4]),
                        parseInt(cells[5], 999)
                ));
            }
        }
        return entries;
    }

    private static List<KanjiEntry> readKanji(InputStream input) throws IOException {
        if (input == null) {
            return Collections.emptyList();
        }
        List<KanjiEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("literal\t")) {
                    continue;
                }
                String[] cells = line.split("\t", -1);
                if (cells.length < 9) {
                    continue;
                }
                entries.add(new KanjiEntry(
                        cells[0],
                        splitList(cells[1]),
                        splitList(cells[2]),
                        splitList(cells[3]),
                        splitList(cells[4]),
                        parseInt(cells[5], 0),
                        parseInt(cells[6], 0),
                        parseInt(cells[7], 0),
                        parseInt(cells[8], 0)
                ));
            }
        }
        return entries;
    }

    private static Map<String, List<WordEntry>> freezeListMap(Map<String, List<WordEntry>> source) {
        Map<String, List<WordEntry>> out = new HashMap<>();
        for (Map.Entry<String, List<WordEntry>> entry : source.entrySet()) {
            out.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    private static String wordKey(String expression, String reading) {
        return normalize(expression) + "\u0000" + normalizeReading(reading);
    }

    private static String normalizeReading(String value) {
        return StudyCueFormatter.hiraganaReading(normalize(value));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        String[] cells = value.split(LIST_SEPARATOR, -1);
        List<String> out = new ArrayList<>();
        for (String cell : cells) {
            String trimmed = cell.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return "";
    }

    public static final class WordEntry implements Comparable<WordEntry> {
        public final String expression;
        public final String reading;
        public final List<String> glosses;
        public final List<String> partsOfSpeech;
        public final List<String> priorities;
        public final int commonness;

        public WordEntry(
                String expression,
                String reading,
                List<String> glosses,
                List<String> partsOfSpeech,
                List<String> priorities,
                int commonness
        ) {
            this.expression = normalize(expression);
            this.reading = normalizeReading(reading);
            this.glosses = Collections.unmodifiableList(new ArrayList<>(glosses == null ? Collections.<String>emptyList() : glosses));
            this.partsOfSpeech = Collections.unmodifiableList(new ArrayList<>(partsOfSpeech == null ? Collections.<String>emptyList() : partsOfSpeech));
            this.priorities = Collections.unmodifiableList(new ArrayList<>(priorities == null ? Collections.<String>emptyList() : priorities));
            this.commonness = commonness;
        }

        private boolean sameMeaning(WordEntry other) {
            return other != null && glosses.equals(other.glosses);
        }

        @Override
        public int compareTo(WordEntry other) {
            int common = Integer.compare(commonness, other.commonness);
            if (common != 0) {
                return common;
            }
            int gloss = Integer.compare(other.glosses.size(), glosses.size());
            if (gloss != 0) {
                return gloss;
            }
            int expressionOrder = expression.compareTo(other.expression);
            if (expressionOrder != 0) {
                return expressionOrder;
            }
            return reading.compareTo(other.reading);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WordEntry)) {
                return false;
            }
            WordEntry entry = (WordEntry) other;
            return commonness == entry.commonness
                    && expression.equals(entry.expression)
                    && reading.equals(entry.reading)
                    && glosses.equals(entry.glosses)
                    && partsOfSpeech.equals(entry.partsOfSpeech)
                    && priorities.equals(entry.priorities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(expression, reading, glosses, partsOfSpeech, priorities, commonness);
        }
    }

    public static final class KanjiEntry {
        public final String literal;
        public final List<String> meanings;
        public final List<String> onReadings;
        public final List<String> kunReadings;
        public final List<String> nanoriReadings;
        public final int strokeCount;
        public final int grade;
        public final int radical;
        public final int frequency;

        public KanjiEntry(
                String literal,
                List<String> meanings,
                List<String> onReadings,
                List<String> kunReadings,
                List<String> nanoriReadings,
                int strokeCount,
                int grade,
                int radical,
                int frequency
        ) {
            this.literal = normalize(literal);
            this.meanings = Collections.unmodifiableList(new ArrayList<>(meanings == null ? Collections.<String>emptyList() : meanings));
            this.onReadings = Collections.unmodifiableList(new ArrayList<>(onReadings == null ? Collections.<String>emptyList() : onReadings));
            this.kunReadings = Collections.unmodifiableList(new ArrayList<>(kunReadings == null ? Collections.<String>emptyList() : kunReadings));
            this.nanoriReadings = Collections.unmodifiableList(new ArrayList<>(nanoriReadings == null ? Collections.<String>emptyList() : nanoriReadings));
            this.strokeCount = strokeCount;
            this.grade = grade;
            this.radical = radical;
            this.frequency = frequency;
        }

        private String firstReading() {
            if (!kunReadings.isEmpty()) {
                return kunReadings.get(0);
            }
            if (!onReadings.isEmpty()) {
                return onReadings.get(0);
            }
            return nanoriReadings.isEmpty() ? "" : nanoriReadings.get(0);
        }
    }
}
