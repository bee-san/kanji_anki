package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class StudySessionRouteTest {
    @Test
    public void writingRequiredSessionsAlwaysRouteToWriting() {
        assertEquals(
                StudySessionRoute.Destination.WRITING,
                StudySessionRoute.destination(session(StudyTaskTypes.SIMILAR_KANJI, true))
        );
        assertEquals(
                StudySessionRoute.Destination.WRITING,
                StudySessionRoute.destination(session(StudyTaskTypes.MEANING_KANJI, true))
        );
    }

    @Test
    public void nonWritingSpecialTasksRouteToChoiceScreens() {
        assertEquals(
                StudySessionRoute.Destination.SIMILAR_KANJI,
                StudySessionRoute.destination(session(StudyTaskTypes.SIMILAR_KANJI, false))
        );
        assertEquals(
                StudySessionRoute.Destination.MEANING_KANJI,
                StudySessionRoute.destination(session(StudyTaskTypes.MEANING_KANJI, false))
        );
    }

    @Test
    public void otherOrMissingSessionsRouteToFlashcard() {
        assertEquals(
                StudySessionRoute.Destination.FLASHCARD,
                StudySessionRoute.destination(session(StudyTaskTypes.KANJI_MEANING, false))
        );
        assertEquals(StudySessionRoute.Destination.FLASHCARD, StudySessionRoute.destination(null));
    }

    private static RecordsSchedulerModels.StudySession session(String taskType, boolean writingRequired) {
        return new RecordsSchedulerModels.StudySession(
                new RecordsStudyModels.StudyItem(
                        "x",
                        "review",
                        0L,
                        1.0,
                        5.0,
                        1,
                        0,
                        1,
                        1,
                        0,
                        0,
                        0L,
                        false,
                        null,
                        0L
                ),
                null,
                "token",
                taskType,
                writingRequired,
                "prompt"
        );
    }
}
