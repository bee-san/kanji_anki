package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFailureCausePolicyTest {
    @Test
    fun recognitionChecksAskMeaningVersusShape() {
        val expected = listOf(FailureKind.MEANING_UNKNOWN, FailureKind.VISUAL_CONFUSION)
        assertEquals(expected, StudyFailureCausePolicy.choices(StudyTaskTypes.KANJI_MEANING))
        assertEquals(expected, StudyFailureCausePolicy.choices(StudyTaskTypes.FONT_MEANING))
    }

    @Test
    fun contextualChecksAskReadingVersusShape() {
        val expected = listOf(FailureKind.WRONG_READING, FailureKind.VISUAL_CONFUSION)
        assertEquals(expected, StudyFailureCausePolicy.choices(StudyTaskTypes.WORD_READING))
        assertEquals(expected, StudyFailureCausePolicy.choices(StudyTaskTypes.SENTENCE_READING))
    }

    @Test
    fun repairAndObjectiveTasksNeverAskAndOnlyReviewPhaseAsks() {
        for (task in listOf(
            StudyTaskTypes.SIMILAR_KANJI, StudyTaskTypes.MEANING_KANJI, StudyTaskTypes.TYPE_MEANING,
            StudyTaskTypes.WRITE_KANJI, StudyTaskTypes.KANJI_READING, StudyTaskTypes.READING_KANJI,
            StudyTaskTypes.TYPE_READING, null, "",
        )) {
            assertTrue(StudyFailureCausePolicy.choices(task).isEmpty())
            assertFalse(StudyFailureCausePolicy.requiresCause(task, RecordsBase.SchedulerPhase.REVIEW))
        }
        assertTrue(StudyFailureCausePolicy.requiresCause(StudyTaskTypes.WORD_READING, RecordsBase.SchedulerPhase.REVIEW))
        assertTrue(StudyFailureCausePolicy.requiresCause(StudyTaskTypes.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW))
        assertFalse(StudyFailureCausePolicy.requiresCause(StudyTaskTypes.WORD_READING, RecordsBase.SchedulerPhase.RELEARNING))
        assertFalse(StudyFailureCausePolicy.requiresCause(StudyTaskTypes.KANJI_MEANING, RecordsBase.SchedulerPhase.NEW_LEARNING))
        assertFalse(StudyFailureCausePolicy.requiresCause(StudyTaskTypes.KANJI_MEANING, null))
    }

    @Test
    fun everyFailureKindHasANonBlankLabel() {
        for (kind in FailureKind.entries) {
            assertTrue(StudyFailureCausePolicy.label(kind).isNotBlank())
        }
        assertEquals(StudyTextCopy.contextualFailureReadingChoice(), StudyFailureCausePolicy.label(FailureKind.WRONG_READING))
        assertEquals(StudyTextCopy.recognitionFailureVisualChoice(), StudyFailureCausePolicy.label(FailureKind.VISUAL_CONFUSION))
        assertEquals(StudyTextCopy.recognitionFailureMeaningChoice(), StudyFailureCausePolicy.label(FailureKind.MEANING_UNKNOWN))
    }
}
