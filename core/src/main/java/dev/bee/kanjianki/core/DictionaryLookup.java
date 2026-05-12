package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class DictionaryLookup {
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    public static final String SOURCE_KANJIDIC2 = "KANJIDIC2";
    public static final String SOURCE_ANKI = "anki";

    public abstract KanjiEntry lookupKanji(String literal);

    public abstract int kanjiCount();

    public JitenKanjiRanks jitenRanks() {
        return JitenKanjiRanks.empty();
    }

    public StudyCue studyCue(
            String kanji,
            String ankiMeaning,
            String rowReading,
            String sourceExpression,
            String sourceReading
    ) {
        KanjiEntry kanjiEntry = lookupKanji(normalize(kanji));
        String cueReading = firstNonEmpty(
                sourceReading,
                rowReading,
                kanjiEntry == null ? "" : kanjiEntry.firstReading()
        );
        String fromExpression = normalize(sourceExpression);
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

    public static DictionaryLookup empty() {
        return MemoryDictionaryLookup.EMPTY;
    }

    public static DictionaryLookup fromKanjiEntries(List<KanjiEntry> kanji) {
        return new MemoryDictionaryLookup(kanji);
    }

    protected static String normalize(String value) {
        return value == null ? "" : MULTI_WHITESPACE.matcher(value.trim()).replaceAll(" ");
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

    private static final class MemoryDictionaryLookup extends DictionaryLookup {
        private static final MemoryDictionaryLookup EMPTY = new MemoryDictionaryLookup(Collections.emptyList());

        private final Map<String, KanjiEntry> kanjiByLiteral;

        private MemoryDictionaryLookup(List<KanjiEntry> kanji) {
            Map<String, KanjiEntry> byLiteral = new HashMap<>();
            for (KanjiEntry entry : kanji == null ? Collections.<KanjiEntry>emptyList() : kanji) {
                byLiteral.put(entry.literal, entry);
            }
            kanjiByLiteral = Collections.unmodifiableMap(byLiteral);
        }

        @Override
        public KanjiEntry lookupKanji(String literal) {
            return kanjiByLiteral.get(normalize(literal));
        }

        @Override
        public int kanjiCount() {
            return kanjiByLiteral.size();
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
        public final int kanjidicFrequency;
        public final Integer jitenRank;

        public KanjiEntry(KanjiEntryFields fields) {
            KanjiEntryFields safeFields = fields == null ? KanjiEntryFields.empty() : fields;
            this.literal = normalize(safeFields.literal);
            this.meanings = immutableList(safeFields.meanings);
            this.onReadings = immutableList(safeFields.onReadings);
            this.kunReadings = immutableList(safeFields.kunReadings);
            this.nanoriReadings = immutableList(safeFields.nanoriReadings);
            this.strokeCount = safeFields.strokeCount;
            this.grade = safeFields.grade;
            this.radical = safeFields.radical;
            this.kanjidicFrequency = safeFields.kanjidicFrequency;
            this.jitenRank = safeFields.jitenRank;
        }

        private static List<String> immutableList(List<String> values) {
            return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.<String>emptyList() : values));
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

    public record KanjiEntryFields(
            String literal,
            List<String> meanings,
            List<String> onReadings,
            List<String> kunReadings,
            List<String> nanoriReadings,
            int strokeCount,
            int grade,
            int radical,
            int kanjidicFrequency,
            Integer jitenRank
    ) {
        private static KanjiEntryFields empty() {
            return new KanjiEntryFields(
                    "",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    0,
                    0,
                    0,
                    0,
                    null
            );
        }
    }
}
