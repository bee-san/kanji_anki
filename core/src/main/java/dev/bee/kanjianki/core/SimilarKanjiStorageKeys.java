package dev.bee.kanjianki.core;

public final class SimilarKanjiStorageKeys {
    private static final String PAIR_DELIMITER = "\u0000";
    private static final String CHOICE_DELIMITER = "\u0001";

    private SimilarKanjiStorageKeys() {
    }

    public static String[] canonicalPair(String first, String second) {
        if (first.compareTo(second) <= 0) {
            return new String[]{first, second};
        }
        return new String[]{second, first};
    }

    public static String pairKey(String first, String second, String source) {
        return first + PAIR_DELIMITER + second + PAIR_DELIMITER + source;
    }

    public static String choiceKey(String targetKanji, String choiceSignature) {
        return targetKanji + CHOICE_DELIMITER + (choiceSignature == null ? "" : choiceSignature);
    }

    public static String[] splitChoiceKey(String key) {
        if (key == null) {
            return new String[0];
        }
        return key.split(CHOICE_DELIMITER, 2);
    }
}
