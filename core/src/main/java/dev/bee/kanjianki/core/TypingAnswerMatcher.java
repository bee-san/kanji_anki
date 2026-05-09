package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TypingAnswerMatcher {
    private TypingAnswerMatcher() {
    }

    public static boolean matches(DictionaryLookup lookup, String kanji, String typedAnswer, String collectionMeaning) {
        String normalizedAnswer = normalizeAnswer(typedAnswer);
        if (normalizedAnswer.isEmpty()) {
            return false;
        }
        for (String accepted : acceptedMeanings(lookup, kanji, collectionMeaning)) {
            if (normalizedAnswer.equals(normalizeAnswer(accepted))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> acceptedMeanings(DictionaryLookup lookup, String kanji, String collectionMeaning) {
        List<String> accepted = new ArrayList<>();
        DictionaryLookup safeLookup = lookup == null ? DictionaryLookup.empty() : lookup;
        DictionaryLookup.KanjiEntry entry = safeLookup.lookupKanji(kanji);
        if (entry != null) {
            for (String meaning : entry.meanings) {
                addMeaningVariants(accepted, meaning);
            }
        }
        addMeaningVariants(accepted, StudyCueFormatter.cleanFallbackMeaning(collectionMeaning, "", 160));
        return accepted;
    }

    private static void addMeaningVariants(List<String> accepted, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "Collection clue".equals(value)) {
            return;
        }
        for (String part : value.split("[,;/]")) {
            String normalized = normalizeAnswer(part);
            if (!normalized.isEmpty() && !containsNormalized(accepted, normalized)) {
                accepted.add(part.trim());
            }
        }
    }

    private static boolean containsNormalized(List<String> values, String normalizedNeedle) {
        for (String value : values) {
            if (normalizeAnswer(value).equals(normalizedNeedle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeAnswer(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
