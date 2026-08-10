package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCapabilityPolicyTest {
    @Test
    fun aWritingTaskIsReroutedToCoreRecognitionWhenRecognitionIsAbsent() {
        val writing = session(StudyTaskTypes.WRITE_KANJI, writingRequired = true)

        val rerouted = StudyCapabilityPolicy.reroute(writing, writingRecognitionAvailable = false)

        // Core recognition, and no longer a writing card — the ADR's "route to the
        // next enabled compatible core revalidation".
        assertEquals(StudyTaskTypes.KANJI_MEANING, rerouted.session?.taskType)
        assertFalse("a re-routed card is not a writing card", rerouted.session?.writingRequired == true)
        assertEquals(StudyCapabilityPolicy.WRITE_UNAVAILABLE_TRACE, rerouted.traceReason)
    }

    @Test
    fun theItemRowAndTokenAreCarriedThroughUnchanged() {
        // The ADR forbids minting a review/timeline token for unavailability: a
        // re-route is the same card presented differently, not a new one.
        val writing = session(StudyTaskTypes.REPAIR_WRITING, writingRequired = true)

        val rerouted = StudyCapabilityPolicy.reroute(writing, writingRecognitionAvailable = false)

        assertSame(writing.item, rerouted.session?.item)
        assertSame(writing.row, rerouted.session?.row)
        assertEquals(writing.token, rerouted.session?.token)
    }

    @Test
    fun everyWritingWireNameIsFilteredWhenRecognitionIsAbsent() {
        val writingTasks = listOf(
            StudyTaskTypes.WRITE_KANJI,
            StudyTaskTypes.WRITING_REMEDIATION,
            StudyTaskTypes.TARGETED_WRITING,
            StudyTaskTypes.REPAIR_WRITING,
            StudyTaskTypes.CONTEXT_WRITING,
            StudyTaskTypes.GUIDED_WRITING,
            StudyTaskTypes.BLIND_WRITING,
            StudyTaskTypes.SAMPLED_HANDWRITING,
        )
        for (task in writingTasks) {
            assertTrue(task, StudyCapabilityPolicy.isWritingTask(task))
            val rerouted = StudyCapabilityPolicy.reroute(session(task, true), writingRecognitionAvailable = false)
            assertEquals(task, StudyTaskTypes.KANJI_MEANING, rerouted.session?.taskType)
            assertEquals(task, StudyCapabilityPolicy.WRITE_UNAVAILABLE_TRACE, rerouted.traceReason)
        }
    }

    @Test
    fun aWritingTaskPassesThroughUnchangedWhenRecognitionIsAvailable() {
        val writing = session(StudyTaskTypes.WRITE_KANJI, writingRequired = true)

        val kept = StudyCapabilityPolicy.reroute(writing, writingRecognitionAvailable = true)

        // Untouched on a capable host, and no trace — this is the Android-unchanged
        // case the ADR requires.
        assertSame(writing, kept.session)
        assertNull(kept.traceReason)
    }

    @Test
    fun aNonWritingTaskIsNeverRerouted() {
        val recognition = session(StudyTaskTypes.KANJI_MEANING, writingRequired = false)

        for (available in listOf(true, false)) {
            val kept = StudyCapabilityPolicy.reroute(recognition, writingRecognitionAvailable = available)
            assertSame("available=$available", recognition, kept.session)
            assertNull("available=$available", kept.traceReason)
        }
        assertFalse(StudyCapabilityPolicy.isWritingTask(StudyTaskTypes.KANJI_MEANING))
        assertFalse(StudyCapabilityPolicy.isWritingTask(null))
    }

    @Test
    fun aNullSessionIsReturnedUnchanged() {
        val kept = StudyCapabilityPolicy.reroute(null, writingRecognitionAvailable = false)
        assertNull(kept.session)
        assertNull(kept.traceReason)
    }

    @Test
    fun reroutingIsDeterministicForTheSameInput() {
        // The ADR's "reloading the same state/capabilities chooses the same
        // non-writing task": no randomness, no state read, so two calls agree.
        val writing = session(StudyTaskTypes.WRITE_KANJI, writingRequired = true)
        val first = StudyCapabilityPolicy.reroute(writing, writingRecognitionAvailable = false)
        val second = StudyCapabilityPolicy.reroute(writing, writingRecognitionAvailable = false)
        assertEquals(first.session?.taskType, second.session?.taskType)
        assertEquals(first.traceReason, second.traceReason)
    }

    private fun session(taskType: String, writingRequired: Boolean) =
        RecordsSchedulerModels.StudySession(
            RecordsStudyModels.StudyItem("脱", "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L),
            RecordsImportModels.DashboardRow(
                "脱", 900, "take off", "だつ", "deck:current", 50, "reason", "reason text", 1, 1, 0,
                emptyList<RecordsImportModels.Example>(),
            ),
            "token-脱",
            taskType,
            writingRequired,
            "reason text",
        )
}
