package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class StudyRepairActionsTest {
    @Test
    fun activateSimilarWritingRepairStoresActiveTokenAndProgressKeys() {
        val repair = repair("")
        val saved = AtomicReference<RecordsImportModels.SimilarKanjiWritingRepair>()

        val active = StudyRepairActions.activateSimilarWritingRepair(repair, 1234L, saved::set)

        assertSame(saved.get(), active.repair)
        assertEquals(active.token, active.repair.activeToken)
        assertEquals(1234L, active.repair.updatedAtMillis)
        assertTrue(active.token.startsWith("repair-42-"))
        assertEquals("repair:42", active.progressKey)
        assertEquals("repair:42:${active.token}", active.studyTaskKey)
    }

    @Test
    fun activateSimilarWritingRepairKeepsExistingActiveToken() {
        val repair = repair("existing-token")

        val active = StudyRepairActions.activateSimilarWritingRepair(repair, 1234L) { _ -> }

        assertEquals("existing-token", active.token)
        assertEquals("existing-token", active.repair.activeToken)
        assertEquals("repair:42:existing-token", active.studyTaskKey)
    }

    @Test
    fun resultTypesKeepJavaRecordSemantics() {
        val repair = repair("active-token")

        assertTrue(StudyRepairActions.ActiveRepair::class.java.isRecord)
        assertTrue(StudyRepairActions.RepairCompletion::class.java.isRecord)
        assertEquals(
            StudyRepairActions.ActiveRepair(repair, "token", "progress", "task"),
            StudyRepairActions.ActiveRepair(repair, "token", "progress", "task"),
        )
        assertEquals(
            StudyRepairActions.RepairCompletion(true, false),
            StudyRepairActions.RepairCompletion(true, false),
        )
    }

    @Test
    fun completeSimilarWritingRepairRecordsAndMarksSavedPass() {
        val repair = repair("active-token")
        val events = mutableListOf<String>()
        val finisher = RecordingFinisher(events, true)
        val recorder = RecordingOutcomeRecorder(events)
        val marker = RecordingMarker(events)

        val completion = StudyRepairActions.completeSimilarWritingRepair(
            repair,
            MainActivityBase.RATING_GOOD,
            2222L,
            finisher,
            recorder,
            marker,
        )

        assertTrue(completion.saved)
        assertTrue(completion.passed)
        assertEquals(listOf("finish", "record", "mark"), events)
        assertEquals(42L, finisher.repairId)
        assertEquals("active-token", finisher.activeToken)
        assertTrue(finisher.passed)
        assertEquals(2222L, finisher.nowMillis)
        assertEquals("未", recorder.kanji)
        assertTrue(recorder.passed)
        assertEquals("repair:42", marker.taskKey)
    }

    @Test
    fun completeSimilarWritingRepairRecordsSavedFailureWithoutMarkingComplete() {
        val repair = repair("active-token")
        val events = mutableListOf<String>()
        val finisher = RecordingFinisher(events, true)
        val recorder = RecordingOutcomeRecorder(events)
        val marker = RecordingMarker(events)

        val completion = StudyRepairActions.completeSimilarWritingRepair(
            repair,
            MainActivityBase.RATING_AGAIN,
            2222L,
            finisher,
            recorder,
            marker,
        )

        assertTrue(completion.saved)
        assertFalse(completion.passed)
        assertEquals(listOf("finish", "record"), events)
        assertFalse(finisher.passed)
        assertFalse(recorder.passed)
        assertNull(marker.taskKey)
    }

    @Test
    fun completeSimilarWritingRepairSkipsOutcomeAndMarkerWhenStoreRejects() {
        val repair = repair("active-token")
        val events = mutableListOf<String>()
        val finisher = RecordingFinisher(events, false)
        val recorder = RecordingOutcomeRecorder(events)
        val marker = RecordingMarker(events)

        val completion = StudyRepairActions.completeSimilarWritingRepair(
            repair,
            MainActivityBase.RATING_GOOD,
            2222L,
            finisher,
            recorder,
            marker,
        )

        assertFalse(completion.saved)
        assertTrue(completion.passed)
        assertEquals(listOf("finish"), events)
        assertNull(recorder.kanji)
        assertNull(marker.taskKey)
    }

    private fun repair(activeToken: String): RecordsImportModels.SimilarKanjiWritingRepair {
        return RecordsImportModels.SimilarKanjiWritingRepair(
            42L,
            "末",
            "未",
            "末|未",
            "末",
            "not yet",
            "pending",
            1000L,
            activeToken,
            0,
            900L,
            901L,
            0L,
        )
    }

    private class RecordingFinisher(
        private val events: MutableList<String>,
        private val saved: Boolean,
    ) : StudyRepairActions.SimilarWritingRepairFinisher {
        var repairId: Long = 0L
        var activeToken: String? = null
        var passed: Boolean = false
        var nowMillis: Long = 0L

        override fun finishSimilarWritingRepair(
            repairId: Long,
            activeToken: String?,
            passed: Boolean,
            nowMillis: Long,
        ): Boolean {
            events.add("finish")
            this.repairId = repairId
            this.activeToken = activeToken
            this.passed = passed
            this.nowMillis = nowMillis
            return saved
        }
    }

    private class RecordingOutcomeRecorder(
        private val events: MutableList<String>,
    ) : StudyRepairActions.RepairOutcomeRecorder {
        var kanji: String? = null
        var passed: Boolean = false

        override fun recordRepairOutcome(kanji: String, passed: Boolean) {
            events.add("record")
            this.kanji = kanji
            this.passed = passed
        }
    }

    private class RecordingMarker(
        private val events: MutableList<String>,
    ) : StudyRepairActions.RepairTaskMarker {
        var taskKey: String? = null

        override fun markStudyTaskCompleted(taskKey: String) {
            events.add("mark")
            this.taskKey = taskKey
        }
    }
}
