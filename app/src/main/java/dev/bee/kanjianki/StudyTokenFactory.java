package dev.bee.kanjianki;

import java.util.UUID;

final class StudyTokenFactory {
    private StudyTokenFactory() {
    }

    static String learningRepeat(String kanji, String activeToken) {
        return existingOrNew(activeToken, kanji + "-repeat-");
    }

    static String studyItem(String kanji, String activeToken) {
        return existingOrNew(activeToken, kanji + "-");
    }

    static String similarRepair(String kanji, String activeToken) {
        return existingOrNew(activeToken, kanji + "-similar-repair-");
    }

    private static String existingOrNew(String activeToken, String prefix) {
        if (activeToken != null && !activeToken.isEmpty()) {
            return activeToken;
        }
        return prefix + UUID.randomUUID();
    }
}
