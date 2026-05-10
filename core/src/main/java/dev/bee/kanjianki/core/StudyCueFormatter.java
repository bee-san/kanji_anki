package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StudyCueFormatter {
    private static final Set<String> LEADING_METADATA = new HashSet<>(Arrays.asList(
            "noun",
            "nouns",
            "suru",
            "intransitive",
            "transitive",
            "ichidan",
            "godan",
            "adverb",
            "adverbial",
            "auxiliary",
            "counter",
            "expression",
            "interjection",
            "prefix",
            "suffix",
            "pronoun",
            "conjunction",
            "particle"
    ));

    private StudyCueFormatter() {
    }

    public static List<String> answerLines(StudyCue cue) {
        List<String> lines = new ArrayList<>();
        StudyCue safe = cue == null ? new StudyCue("", "", "", "") : cue;
        if (!safe.meaning.isEmpty()) {
            lines.add(safe.meaning);
        }
        if (!safe.reading.isEmpty()) {
            lines.add("Reading: " + hiraganaReading(safe.reading));
        }
        if (!safe.fromExpression.isEmpty()) {
            lines.add("From: " + safe.fromExpression);
        }
        if (lines.isEmpty()) {
            lines.add("Collection clue");
        }
        return lines;
    }

    public static String displayGlosses(List<String> glosses, int maxGlosses) {
        List<String> cleaned = new ArrayList<>();
        if (glosses != null) {
            for (String gloss : glosses) {
                String value = cleanInline(gloss);
                if (!value.isEmpty() && !cleaned.contains(value)) {
                    cleaned.add(value);
                }
                if (cleaned.size() >= Math.max(1, maxGlosses)) {
                    break;
                }
            }
        }
        if (cleaned.isEmpty()) {
            return "";
        }
        String joined = String.join(", ", cleaned);
        return capitalize(joined);
    }

    public static String cleanFallbackMeaning(String raw, String fallback, int maxChars) {
        String value = raw == null ? "" : raw;
        value = value.replaceAll("\\[\\d{4}-\\d{2}-\\d{2}\\]", " ");
        value = value.replaceAll("(?i)\\bJMdict\\s*\\[[^\\]]*\\]\\s*", " ");
        value = value.replaceAll("(?i)\\bJitendex\\.org\\s*", " ");
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        boolean changed = true;
        while (changed && value.startsWith("(")) {
            changed = false;
            int end = value.indexOf(')');
            if (end > 0 && end < 180) {
                String metadata = value.substring(1, end).toLowerCase(Locale.ROOT);
                if (metadata.contains("jitendex")
                        || metadata.contains("priority")
                        || metadata.contains("form")
                        || metadata.contains("noun")
                        || metadata.contains("verb")
                        || metadata.contains("transitive")
                        || metadata.contains("suru")) {
                    value = value.substring(end + 1).trim();
                    changed = true;
                }
            }
        }
        value = value.replaceAll("^\\d+\\.\\s*", "");
        value = value.replaceAll("(?i)^(5-dan|godan)\\s+(intransitive|transitive)\\s+", "");
        value = value.replaceAll("(?i)^(ichidan|suru|na-adjective|i-adjective|no-adjective)\\s+", "");
        value = stripLeadingMetadataWords(value);
        value = cleanInline(value);
        if (value.isEmpty()) {
            value = fallback == null || fallback.trim().isEmpty() ? "Collection clue" : fallback.trim();
        }
        return compact(capitalize(value), maxChars);
    }

    public static String hiraganaReading(String reading) {
        if (reading == null || reading.isEmpty()) {
            return "";
        }
        StringBuilder converted = new StringBuilder(reading.length());
        for (int i = 0; i < reading.length(); i++) {
            char c = reading.charAt(i);
            if (c >= 'ァ' && c <= 'ヶ') {
                converted.append((char) (c - 0x60));
            } else {
                converted.append(c);
            }
        }
        return converted.toString();
    }

    private static String stripLeadingMetadataWords(String value) {
        String[] words = value.trim().split("\\s+");
        int firstMeaningWord = 0;
        while (firstMeaningWord < words.length && isLeadingMetadataWord(words[firstMeaningWord])) {
            firstMeaningWord++;
        }
        if (firstMeaningWord == 0) {
            return value;
        }
        return String.join(" ", Arrays.copyOfRange(words, firstMeaningWord, words.length));
    }

    private static boolean isLeadingMetadataWord(String word) {
        String normalized = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return normalized.equals("5-dan")
                || normalized.equals("na-adjective")
                || normalized.equals("i-adjective")
                || normalized.equals("no-adjective")
                || LEADING_METADATA.contains(normalized);
    }

    private static String cleanInline(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String compact(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        int cut = value.lastIndexOf(' ', maxChars - 3);
        if (cut < 32) {
            cut = maxChars - 3;
        }
        return value.substring(0, cut).trim() + "...";
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (!Character.isLowerCase(first)) {
            return value;
        }
        return Character.toUpperCase(first) + value.substring(1);
    }
}
