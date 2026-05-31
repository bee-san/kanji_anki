package dev.bee.kanjianki;

import java.util.Arrays;

import dev.bee.kanjianki.core.BridgeScheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudySessionTrackerTest {
    @Test
    public void activeTaskLifecycleIgnoresEmptyAndDuplicateKeys() {
        StudySessionTracker tracker = new StudySessionTracker();

        tracker.startActiveTask("", "裂", BridgeScheduler.TASK_KANJI_MEANING, -5L, false);
        assertFalse(tracker.hasActiveTask());

        tracker.startActiveTask("task", null, null, -5L, false);
        assertTrue(tracker.hasActiveTask());
        tracker.startActiveTask("task", "ignored", BridgeScheduler.TASK_WORD_READING, 50L, false);
        assertTrue(tracker.hasActiveTask());

        tracker.abandonActiveTask();
        assertFalse(tracker.hasActiveTask());
    }

    @Test
    public void activeTaskTracksVisibleElapsedTimeWithManualClock() {
        StudySessionTracker.ActiveStudyTask task = new StudySessionTracker.ActiveStudyTask(
                "task",
                null,
                null,
                -5L
        );

        assertEquals("", task.kanji);
        assertEquals("", task.taskType);
        assertEquals(0L, task.startedAtMillis);

        task.pause(10L);
        assertEquals(0L, task.activeElapsedMillis);
        task.resume(10L);
        task.resume(12L);
        task.pause(25L);
        assertEquals(15L, task.activeElapsedMillis);
        task.pause(30L);
        assertEquals(15L, task.activeElapsedMillis);
        task.resume(40L);
        task.pause(35L);
        assertEquals(15L, task.activeElapsedMillis);
    }

    @Test
    public void sessionPlanSkipsCompletedTaskAndResetsForNewRun() {
        StudySessionTracker tracker = new StudySessionTracker();
        tracker.initializeSessionPlan(Arrays.asList("kanji_meaning:裂", "word_reading:謎"));

        assertEquals("kanji_meaning:裂", tracker.nextPlannedSessionTaskKey());

        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "裂");
        assertEquals("word_reading:謎", tracker.nextPlannedSessionTaskKey());

        tracker.markPlannedSessionTaskCompleted("word_reading", "謎");
        assertEquals("", tracker.nextPlannedSessionTaskKey());

        tracker.resetProgress();
        assertEquals("", tracker.nextPlannedSessionTaskKey());
    }
}
