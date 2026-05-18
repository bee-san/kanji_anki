package dev.bee.kanjianki;

import dev.bee.kanjianki.core.StudyTokenPolicy;

final class StudyTokenFactory {
    private StudyTokenFactory() {
    }

    static String studyItem(String kanji, String activeToken) {
        return StudyTokenPolicy.studyItem(kanji, activeToken);
    }
}
