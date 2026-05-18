package dev.bee.kanjianki.core;

import java.util.UUID;

public final class StudyTokenPolicy {
    private StudyTokenPolicy() {
    }

    public static String studyItem(String kanji, String activeToken) {
        return existingOrNew(activeToken, kanji + "-");
    }

    private static String existingOrNew(String activeToken, String prefix) {
        if (activeToken != null && !activeToken.isEmpty()) {
            return activeToken;
        }
        return prefix + UUID.randomUUID();
    }
}
