package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionTrackerTest {
    @Test
    fun activeTaskLifecycleIgnoresEmptyAndDuplicateKeys() {
        val tracker = StudySessionTracker()

        tracker.startActiveTask("", "裂", BridgeScheduler.TASK_KANJI_MEANING, -5L, false)
        assertFalse(tracker.hasActiveTask())

        tracker.startActiveTask("task", null, null, -5L, false)
        assertTrue(tracker.hasActiveTask())
        tracker.startActiveTask("task", "ignored", BridgeScheduler.TASK_WORD_READING, 50L, false)
        assertTrue(tracker.hasActiveTask())

        tracker.abandonActiveTask()
        assertFalse(tracker.hasActiveTask())
    }

    @Test
    fun activeTaskTracksVisibleElapsedTimeWithManualClock() {
        val task = StudySessionTracker.ActiveStudyTask(
            "task",
            null,
            null,
            -5L,
        )

        assertEquals("", task.kanji)
        assertEquals("", task.taskType)
        assertEquals(0L, task.startedAtMillis)

        task.pause(10L)
        assertEquals(0L, task.activeElapsedMillis)
        task.resume(10L)
        task.resume(12L)
        task.pause(25L)
        assertEquals(15L, task.activeElapsedMillis)
        task.pause(30L)
        assertEquals(15L, task.activeElapsedMillis)
        task.resume(40L)
        task.pause(35L)
        assertEquals(15L, task.activeElapsedMillis)
    }

    @Test
    fun sessionPlanSkipsCompletedTaskAndResetsForNewRun() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:裂", "word_reading:謎"))

        assertEquals("kanji_meaning:裂", tracker.nextPlannedSessionTaskKey())

        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "裂")
        assertEquals("word_reading:謎", tracker.nextPlannedSessionTaskKey())

        tracker.markPlannedSessionTaskCompleted("word_reading", "謎")
        assertEquals("", tracker.nextPlannedSessionTaskKey())

        tracker.resetProgress()
        assertEquals("", tracker.nextPlannedSessionTaskKey())
    }

    @Test
    fun completedLearningRepeatDoesNotGrowPendingPlanOrProgress() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("word_reading:done", "kanji_meaning:stale"))
        tracker.markTaskCompleted("session:word_reading:done:initial-token")
        tracker.markPlannedSessionTaskCompleted("word_reading", "done")

        // A lapse may change the rung (and therefore the planned key) for the
        // same learning/relearning repeat. It remains practice for the already
        // completed task, rather than becoming a new unit of session progress.
        tracker.initializeSessionPlan(listOf("kanji_meaning:done", "kanji_meaning:pending"))
        tracker.markTaskCompleted("session:kanji_meaning:done:repeat-token")

        val repeat = RecordsStudyModels.StudyItem(
            "done",
            "learning",
            1_000L,
            1.0,
            2.0,
            1,
            0,
            0,
            0,
            "",
            1_000L,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .build()

        assertEquals(listOf("kanji_meaning:pending"), tracker.pendingPlannedSessionTaskKeys())
        assertEquals(listOf("kanji_meaning:done"), tracker.dueCompletedLearningRepeatTaskKeys(listOf(repeat), 1_000L))
        assertEquals(1, tracker.completedCount())
        assertEquals(2, tracker.targetCount())
    }

    @Test
    fun emptySessionPlanClearsStalePendingKeys() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:first", "kanji_meaning:second"))

        tracker.initializeSessionPlan(emptyList())

        assertEquals("", tracker.nextPlannedSessionTaskKey())
        assertEquals(emptyList<String>(), tracker.pendingPlannedSessionTaskKeys())
        assertEquals(0, tracker.targetCount())
    }

    @Test
    fun sessionPlanPrunesPartialOverlapWhilePreservingSurvivorOrder() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(
            listOf("kanji_meaning:first", "kanji_meaning:second", "kanji_meaning:third")
        )

        tracker.initializeSessionPlan(
            listOf("kanji_meaning:third", "kanji_meaning:second", "kanji_meaning:new", "kanji_meaning:new")
        )

        assertEquals(
            listOf("kanji_meaning:second", "kanji_meaning:third", "kanji_meaning:new"),
            tracker.pendingPlannedSessionTaskKeys(),
        )
        assertEquals(3, tracker.targetCount())
    }

    @Test
    fun sessionPlanReplacesDisjointPendingKeysAndKeepsCompletedTarget() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:done", "kanji_meaning:old"))
        tracker.markTaskCompleted("session:kanji_meaning:done:token")
        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "done")

        // A sync or settings change can replace the due queue while Study remains
        // open. Keeping the stale key would make selection return null even though
        // the current scheduler plan contains a runnable task. The progress target
        // must shrink to completed + current pending at the same time.
        tracker.initializeSessionPlan(listOf("kanji_meaning:new", "word_reading:newer"))

        assertEquals("kanji_meaning:new", tracker.nextPlannedSessionTaskKey())
        assertEquals(
            listOf("kanji_meaning:new", "word_reading:newer"),
            tracker.pendingPlannedSessionTaskKeys(),
        )
        assertEquals(1, tracker.completedCount())
        assertEquals(3, tracker.targetCount())
    }

}
