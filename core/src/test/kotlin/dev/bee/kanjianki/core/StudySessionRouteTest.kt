package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StudySessionRouteTest {
    @Test
    fun writingRequiredSessionsAlwaysRouteToWriting() {
        assertEquals(
            StudySessionRoute.Destination.WRITING,
            StudySessionRoute.destination(session(StudyTaskTypes.SIMILAR_KANJI, true))
        )
        assertEquals(
            StudySessionRoute.Destination.WRITING,
            StudySessionRoute.destination(session(StudyTaskTypes.MEANING_KANJI, true))
        )
    }

    @Test
    fun nonWritingSpecialTasksRouteToChoiceScreens() {
        assertEquals(
            StudySessionRoute.Destination.SIMILAR_KANJI,
            StudySessionRoute.destination(session(StudyTaskTypes.SIMILAR_KANJI, false))
        )
        assertEquals(
            StudySessionRoute.Destination.MEANING_KANJI,
            StudySessionRoute.destination(session(StudyTaskTypes.MEANING_KANJI, false))
        )
    }

    @Test
    fun otherOrMissingSessionsRouteToFlashcard() {
        assertEquals(
            StudySessionRoute.Destination.FLASHCARD,
            StudySessionRoute.destination(session(StudyTaskTypes.KANJI_MEANING, false))
        )
    }

    @Test
    fun missingSessionIsUnsupported() {
        val exception = assertThrows(NullPointerException::class.java) {
            StudySessionRoute.destination(RecordsSchedulerModels.StudySession::class.java.cast(null))
        }

        assertNotNull(exception.message)
    }

    private fun session(taskType: String, writingRequired: Boolean): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            RecordsStudyModels.StudyItem(
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
        )
    }
}
