package dev.bee.kanjianki.core;

import java.util.Objects;

public final class StudySessionRoute {
    private StudySessionRoute() {
    }

    public enum Destination {
        WRITING,
        SIMILAR_KANJI,
        MEANING_KANJI,
        FLASHCARD
    }

    public static Destination destination(RecordsSchedulerModels.StudySession session) {
        Objects.requireNonNull(session, "session");
        if (session.writingRequired) {
            return Destination.WRITING;
        }
        if (StudyTaskTypes.SIMILAR_KANJI.equals(session.taskType)) {
            return Destination.SIMILAR_KANJI;
        }
        if (StudyTaskTypes.MEANING_KANJI.equals(session.taskType)) {
            return Destination.MEANING_KANJI;
        }
        return Destination.FLASHCARD;
    }
}
