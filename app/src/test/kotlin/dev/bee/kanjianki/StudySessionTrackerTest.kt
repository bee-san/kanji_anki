package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
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
}
