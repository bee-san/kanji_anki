package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class StudyCueFormatter {
    private static final Pattern DATE_METADATA_PATTERN = Pattern.compile("\\[\\d{4}-\\d{2}-\\d{2}\\]");
    private static final Pattern MEANING_LABEL_PATTERN = Pattern.compile("(?i)^\\s*meaning\\s*:\\s*");
    private static final Pattern JM_DICT_PATTERN = Pattern.compile("(?i)\\bJMdict(?:\\s*\\[[^\\]]*\\])?\\s*");
    private static final Pattern JITENDEX_PATTERN = Pattern.compile("(?i)(?<!\\()\\bJitendex(?:\\.org)?\\s*");
    private static final Pattern NUMBERED_PREFIX_PATTERN = Pattern.compile("^\\d+\\.\\s*");
    private static final Pattern GODAN_PATTERN = Pattern.compile("(?i)^(5-dan|godan)\\s+(intransitive|transitive)\\s+");
    private static final Pattern ADJECTIVE_VERB_PATTERN = Pattern.compile("(?i)^(ichidan|suru|na-adjective|i-adjective|no-adjective)\\s+");
    private static final Pattern LEADING_METADATA_SEPARATOR_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHA_NUMERIC_PATTERN = Pattern.compile("[^a-z0-9-]");
    private static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("\\s+");

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
        String value = cleanMeaningText(raw);
        if (value.isEmpty()) {
            value = cleanMeaningText(fallback);
        }
        if (value.isEmpty()) {
            value = "Collection clue";
        }
        return compact(capitalize(value), maxChars);
    }

    public static String cleanCollectionMeaning(String raw, int maxChars) {
        return compact(cleanMeaningText(raw), maxChars);
    }

    public static String cleanMeaningText(String raw) {
        String value = DictionaryTextUtil.stripHtml(raw);
        value = MEANING_LABEL_PATTERN.matcher(value).replaceAll(" ");
        value = DATE_METADATA_PATTERN.matcher(value).replaceAll(" ");
        value = JM_DICT_PATTERN.matcher(value).replaceAll(" ");
        value = JITENDEX_PATTERN.matcher(value).replaceAll(" ");
        value = MEANING_LABEL_PATTERN.matcher(value).replaceAll(" ");
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
                        || metadata.contains("adjective")
                        || metadata.contains("verb")
                        || metadata.contains("transitive")
                        || metadata.contains("suru")) {
                    value = value.substring(end + 1).trim();
                    changed = true;
                }
            }
        }
        value = NUMBERED_PREFIX_PATTERN.matcher(value).replaceAll("");
        value = GODAN_PATTERN.matcher(value).replaceAll("");
        value = ADJECTIVE_VERB_PATTERN.matcher(value).replaceAll("");
        value = stripLeadingMetadataWords(value);
        return cleanInline(value);
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
        String[] words = LEADING_METADATA_SEPARATOR_PATTERN.split(value.trim());
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
        String normalized = NON_ALPHA_NUMERIC_PATTERN.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("");
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
        String normalized = value.replace('\t', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ');
        return MULTI_WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    public static String compact(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        int cut = value.lastIndexOf(' ', maxChars - 3);
        if (cut < 32) {
            cut = maxChars - 3;
        }
        return value.substring(0, cut).trim() + "...";
    }

    private static String capitalize(String value) {
        char first = value.charAt(0);
        if (!Character.isLowerCase(first)) {
            return value;
        }
        return Character.toUpperCase(first) + value.substring(1);
    }
}
