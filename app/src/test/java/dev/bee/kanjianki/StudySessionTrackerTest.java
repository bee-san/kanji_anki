package dev.bee.kanjianki;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.Records;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudySessionTrackerTest {
    @Test
    public void progressCountsUniqueSeenAndCompletedTasks() {
        StudySessionTracker tracker = new StudySessionTracker();

        assertTrue(tracker.includePendingTask("task-a"));
        assertFalse(tracker.includePendingTask("task-a"));
        assertFalse(tracker.includePendingTask(""));
        assertEquals(1, tracker.targetCount());
        assertFalse(tracker.atHardCap(false));

        tracker.markTaskCompleted("task-a");
        tracker.markTaskCompleted("task-a");
        assertEquals(1, tracker.completedCount());
        assertEquals(1, tracker.targetCount());
        assertTrue(tracker.atHardCap(false));
        assertFalse(tracker.atHardCap(true));

        tracker.registerTaskShown("task-b");
        tracker.markTaskCompleted("task-b");
        assertEquals(2, tracker.completedCount());
        assertEquals(2, tracker.targetCount());

        tracker.resetProgress();
        assertEquals(0, tracker.completedCount());
        assertEquals(0, tracker.targetCount());
    }

    @Test
    public void targetInitializationUsesRemainingThenTargetAndClampsManualValues() {
        StudySessionTracker tracker = new StudySessionTracker();

        tracker.initializeTarget(new Records.AdaptiveLoadPlan(40, 7, 3, Arrays.asList("裂", "浅"), 1, false, "focus"));
        assertEquals(3, tracker.targetCount());

        tracker.initializeTarget(new Records.AdaptiveLoadPlan(40, 10, 9, Collections.singletonList("謎"), 1, false, "ignored"));
        assertEquals(3, tracker.targetCount());

        tracker.resetProgress();
        tracker.initializeTarget(new Records.AdaptiveLoadPlan(40, 7, 0, Collections.singletonList("語"), 1, false, "fallback"));
        assertEquals(7, tracker.targetCount());

        tracker.setTargetCount(-12);
        assertEquals(0, tracker.targetCount());
        tracker.registerTaskShown("visible");
        assertEquals(1, tracker.targetCount());
    }

    @Test
    public void sessionAndRepairKeysAreStableAndNullSafe() {
        Records.StudySession session = new Records.StudySession(
                item("裂", Records.LadderRung.KANJI_MEANING, 0, 0),
                row("裂"),
                "token-1",
                BridgeScheduler.TASK_KANJI_MEANING,
                false,
                "split"
        );
        Records.SimilarKanjiWritingRepair repair = new Records.SimilarKanjiWritingRepair(
                42L,
                "裂",
                "烈",
                "裂|烈",
                "烈",
                "split",
                "pending",
                100L,
                "token-2",
                1,
                90L,
                100L,
                0L
        );

        assertEquals("", StudySessionTracker.sessionTaskKey(null));
        assertEquals("session:kanji_meaning:裂:token-1", StudySessionTracker.sessionTaskKey(session));
        assertEquals("", StudySessionTracker.similarRepairProgressKey(null));
        assertEquals("repair:42", StudySessionTracker.similarRepairProgressKey(repair));
        assertEquals("", StudySessionTracker.similarRepairStudyTaskKey(null));
        assertEquals("repair:42:token-2", StudySessionTracker.similarRepairStudyTaskKey(repair));
    }

    @Test
    public void reviewOutcomesTrackMovedForwardAndMissedKanji() {
        StudySessionTracker tracker = new StudySessionTracker();
        Records.StudyItem before = item("裂", Records.LadderRung.KANJI_MEANING, 0, 0);
        Records.StudyItem locallyImproved = item("裂", Records.LadderRung.KANJI_MEANING, 1, 0);
        Records.StudyItem streakImproved = item("裂", Records.LadderRung.KANJI_MEANING, 0, 2);

        tracker.recordReviewOutcome(" 裂 ", BridgeScheduler.RATING_AGAIN, before, before);
        assertEquals(0, tracker.movedForwardCount());
        assertEquals(1, tracker.missedCount());

        tracker.recordReviewOutcome("裂", BridgeScheduler.RATING_GOOD, before, before);
        assertEquals(1, tracker.movedForwardCount());
        assertEquals(0, tracker.missedCount());

        tracker.recordReviewOutcome("浅", BridgeScheduler.RATING_AGAIN, before, locallyImproved);
        tracker.recordReviewOutcome("語", BridgeScheduler.RATING_AGAIN, before, streakImproved);
        assertEquals(3, tracker.movedForwardCount());
        assertEquals(0, tracker.missedCount());

        tracker.recordReviewOutcome(null, BridgeScheduler.RATING_AGAIN, before, before);
        assertEquals(3, tracker.movedForwardCount());
        assertEquals(0, tracker.missedCount());
    }

    @Test
    public void repairOutcomesDoNotDemoteAlreadyMovedKanjiToMissed() {
        StudySessionTracker tracker = new StudySessionTracker();

        tracker.recordRepairOutcome("裂", false);
        assertEquals(0, tracker.movedForwardCount());
        assertEquals(1, tracker.missedCount());

        tracker.recordRepairOutcome("裂", true);
        assertEquals(1, tracker.movedForwardCount());
        assertEquals(0, tracker.missedCount());

        tracker.recordRepairOutcome("裂", false);
        tracker.recordRepairOutcome("   ", false);
        assertEquals(1, tracker.movedForwardCount());
        assertEquals(0, tracker.missedCount());
    }

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

    private static Records.DashboardRow row(String kanji) {
        return new Records.DashboardRow(
                kanji,
                100,
                "meaning",
                "reading",
                "search",
                1,
                "reason",
                "reason text",
                1,
                0,
                0,
                Collections.emptyList()
        );
    }

    private static Records.StudyItem item(String kanji, Records.LadderRung rung, int writingLevel, int realPassStreak) {
        return new Records.StudyItem(kanji, "review", 100L, 1.0, 5.0, 1, 0, 0, 0, null, 0L)
                .copyBuilder()
                .rung(rung)
                .writingLevel(writingLevel)
                .realPassStreak(realPassStreak)
                .build();
    }
}
