package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudySessionProgressTrackerTest {
    @Test
    public void progressCountsUniqueSeenAndCompletedTasks() {
        StudySessionProgressTracker tracker = new StudySessionProgressTracker();

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
        StudySessionProgressTracker tracker = new StudySessionProgressTracker();

        tracker.initializeTarget(new RecordsSchedulerModels.AdaptiveLoadPlan(40, 7, 3, Arrays.asList("裂", "浅"), 1, false, "focus"));
        assertEquals(3, tracker.targetCount());

        tracker.initializeTarget(new RecordsSchedulerModels.AdaptiveLoadPlan(40, 10, 9, Collections.singletonList("謎"), 1, false, "ignored"));
        assertEquals(3, tracker.targetCount());

        tracker.resetProgress();
        tracker.initializeTarget(new RecordsSchedulerModels.AdaptiveLoadPlan(40, 7, 0, Collections.singletonList("語"), 1, false, "fallback"));
        assertEquals(7, tracker.targetCount());

        tracker.setTargetCount(-12);
        assertEquals(0, tracker.targetCount());
        tracker.registerTaskShown("visible");
        assertEquals(1, tracker.targetCount());
    }

    @Test
    public void sessionAndRepairKeysAreStableAndNullSafe() {
        RecordsSchedulerModels.StudySession session = new RecordsSchedulerModels.StudySession(
                item("裂", RecordsBase.LadderRung.KANJI_MEANING, 0, 0),
                row("裂"),
                "token-1",
                BridgeScheduler.TASK_KANJI_MEANING,
                false,
                "split"
        );
        RecordsImportModels.SimilarKanjiWritingRepair repair = new RecordsImportModels.SimilarKanjiWritingRepair(
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

        assertEquals("", StudySessionProgressTracker.sessionTaskKey(null));
        assertEquals("session:kanji_meaning:裂:token-1", StudySessionProgressTracker.sessionTaskKey(session));
        assertEquals("", StudySessionProgressTracker.similarRepairProgressKey(null));
        assertEquals("repair:42", StudySessionProgressTracker.similarRepairProgressKey(repair));
        assertEquals("", StudySessionProgressTracker.similarRepairStudyTaskKey(null));
        assertEquals("repair:42:token-2", StudySessionProgressTracker.similarRepairStudyTaskKey(repair));
    }

    @Test
    public void reviewOutcomesTrackMovedForwardAndMissedKanji() {
        StudySessionProgressTracker tracker = new StudySessionProgressTracker();
        RecordsStudyModels.StudyItem before = item("裂", RecordsBase.LadderRung.KANJI_MEANING, 0, 0);
        RecordsStudyModels.StudyItem locallyImproved = item("裂", RecordsBase.LadderRung.KANJI_MEANING, 1, 0);
        RecordsStudyModels.StudyItem streakImproved = item("裂", RecordsBase.LadderRung.KANJI_MEANING, 0, 2);

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
        StudySessionProgressTracker tracker = new StudySessionProgressTracker();

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

    private static RecordsImportModels.DashboardRow row(String kanji) {
        return new RecordsImportModels.DashboardRow(
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

    private static RecordsStudyModels.StudyItem item(String kanji, RecordsBase.LadderRung rung, int writingLevel, int realPassStreak) {
        return new RecordsStudyModels.StudyItem(kanji, "review", 100L, 1.0, 5.0, 1, 0, 0, 0, null, 0L)
                .copyBuilder()
                .rung(rung)
                .writingLevel(writingLevel)
                .realPassStreak(realPassStreak)
                .build();
    }
}
