package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionProgressTrackerTest {
    @Test
    fun stateEquivalenceComparesTheCompleteProgressState() {
        val tracker = StudySessionProgressTracker()
        tracker.setTargetCount(1)
        tracker.markTaskCompleted("task-a")
        val same = tracker.copyForStaging()

        assertTrue(tracker.hasSameStateAs(same))
        same.recordReviewOutcome("裂", BridgeScheduler.RATING_AGAIN, null, null)
        assertFalse(tracker.hasSameStateAs(same))
    }

    @Test
    fun progressCountsUniqueSeenAndCompletedTasks() {
        val tracker = StudySessionProgressTracker()

        assertTrue(tracker.includePendingTask("task-a"))
        assertFalse(tracker.includePendingTask("task-a"))
        assertFalse(tracker.includePendingTask(""))
        assertEquals(1, tracker.targetCount())
        assertFalse(tracker.atHardCap(false))

        tracker.markTaskCompleted("task-a")
        tracker.markTaskCompleted("task-a")
        assertEquals(1, tracker.completedCount())
        assertEquals(1, tracker.targetCount())
        assertTrue(tracker.atHardCap(false))
        assertFalse(tracker.atHardCap(true))

        tracker.registerTaskShown("task-b")
        tracker.markTaskCompleted("task-b")
        assertEquals(2, tracker.completedCount())
        assertEquals(2, tracker.targetCount())

        tracker.resetProgress()
        assertEquals(0, tracker.completedCount())
        assertEquals(0, tracker.targetCount())
    }

    @Test
    fun snapshotReturnsOneCoherentProgressFrame() {
        val tracker = StudySessionProgressTracker()
        val item = item("裂", RecordsBase.LadderRung.KANJI_MEANING, 0, 0)
        tracker.setTargetCount(3)
        tracker.markTaskCompleted("session:kanji_meaning:裂:token")
        tracker.recordReviewOutcome("裂", BridgeScheduler.RATING_AGAIN, item, item)

        val snapshot = tracker.snapshot()

        assertEquals(3, snapshot.targetCount)
        assertEquals(1, snapshot.completedCount)
        assertEquals(0, snapshot.movedForwardCount)
        assertEquals(1, snapshot.missedCount)
    }

    @Test
    fun stagedCopyIsIsolatedUntilItsWholeProgressFrameIsCommitted() {
        val tracker = StudySessionProgressTracker()
        tracker.setTargetCount(2)
        tracker.markTaskCompleted("task-a")
        tracker.recordRepairOutcome("裂", false)
        val accepted = tracker.snapshot()

        val staged = tracker.copyForStaging()
        staged.markTaskCompleted("task-b")
        staged.recordRepairOutcome("裂", true)

        assertEquals(accepted, tracker.snapshot())

        tracker.replaceStateFrom(staged)

        assertEquals(staged.snapshot(), tracker.snapshot())
        assertEquals(2, tracker.completedCount())
        assertEquals(1, tracker.movedForwardCount())
        assertEquals(0, tracker.missedCount())
    }

    @Test
    fun targetInitializationUsesRemainingThenTargetAndClampsManualValues() {
        val tracker = StudySessionProgressTracker()

        tracker.initializeTarget(RecordsSchedulerModels.AdaptiveLoadPlan(40, 7, 3, listOf("裂", "浅"), 1, false, "focus"))
        assertEquals(3, tracker.targetCount())

        tracker.initializeTarget(RecordsSchedulerModels.AdaptiveLoadPlan(40, 10, 9, listOf("謎"), 1, false, "ignored"))
        assertEquals(3, tracker.targetCount())

        tracker.resetProgress()
        tracker.initializeTarget(RecordsSchedulerModels.AdaptiveLoadPlan(40, 7, 0, listOf("語"), 1, false, "fallback"))
        assertEquals(7, tracker.targetCount())

        tracker.setTargetCount(-12)
        assertEquals(0, tracker.targetCount())
        tracker.registerTaskShown("visible")
        assertEquals(1, tracker.targetCount())
    }

    @Test
    fun pendingTaskFromZeroStartsTargetAtOne() {
        val tracker = StudySessionProgressTracker()

        assertTrue(tracker.includePendingTask("first"))
        assertEquals(1, tracker.targetCount())
        assertEquals(0, tracker.completedCount())
    }

    @Test
    fun pendingTaskNormallyIncrementsTargetOnce() {
        val tracker = StudySessionProgressTracker()
        tracker.setTargetCount(7)

        assertTrue(tracker.includePendingTask("pending"))
        assertFalse(tracker.includePendingTask("pending"))
        assertEquals(8, tracker.targetCount())
        assertEquals(8, tracker.snapshot().targetCount)
    }

    @Test
    fun pendingTaskOneBelowTargetLimitReachesTheLimit() {
        val tracker = StudySessionProgressTracker()
        tracker.setTargetCount(Int.MAX_VALUE - 1)

        assertTrue(tracker.includePendingTask("last"))
        assertEquals(Int.MAX_VALUE, tracker.targetCount())
        assertEquals(Int.MAX_VALUE, tracker.snapshot().targetCount)
    }

    @Test
    fun pendingTaskAtTargetLimitIsRejectedWithoutCorruptingProgress() {
        val tracker = StudySessionProgressTracker()
        tracker.setTargetCount(Int.MAX_VALUE)

        assertFalse(tracker.includePendingTask("overflow"))
        assertEquals(Int.MAX_VALUE, tracker.targetCount())
        assertEquals(0, tracker.completedCount())
        assertEquals(Int.MAX_VALUE, tracker.snapshot().targetCount)

        tracker.setTargetCount(0)
        assertTrue(tracker.includePendingTask("overflow"))
        assertEquals(1, tracker.targetCount())
    }

    @Test
    fun pendingTaskAdmissionDistinguishesExistingWorkFromCapacityRejection() {
        val tracker = StudySessionProgressTracker()

        assertEquals(
            StudySessionProgressTracker.PendingTaskAdmission.ADDED,
            tracker.admitPendingTask("existing"),
        )
        tracker.setTargetCount(Int.MAX_VALUE)

        assertEquals(
            StudySessionProgressTracker.PendingTaskAdmission.EXISTING,
            tracker.admitPendingTask("existing"),
        )
        assertEquals(
            StudySessionProgressTracker.PendingTaskAdmission.REJECTED,
            tracker.admitPendingTask("overflow"),
        )

        tracker.setTargetCount(0)
        assertEquals(
            StudySessionProgressTracker.PendingTaskAdmission.ADDED,
            tracker.admitPendingTask("overflow"),
        )
    }

    @Test
    fun topBarProgressPreservesVisibleCountsAndFractions() {
        val tracker = StudySessionProgressTracker()

        assertTopBar(tracker.topBarProgress(false, false), 0, 0, 0f)

        tracker.setTargetCount(3)
        tracker.markTaskCompleted("a")
        assertTopBar(tracker.topBarProgress(false, false), 1, 3, 1f / 3f)

        tracker.markTaskCompleted("b")
        tracker.markTaskCompleted("c")
        tracker.markTaskCompleted("d")
        assertTopBar(tracker.topBarProgress(false, false), 4, 4, 1f)
        assertTopBar(tracker.topBarProgress(true, false), 4, 4, 1f)
        assertTopBar(tracker.topBarProgress(true, true), 4, 5, 0.8f)
    }

    @Test
    fun topBarProgressKeepsActiveTaskVisibleBeforeTargetExists() {
        val tracker = StudySessionProgressTracker()

        assertTopBar(tracker.topBarProgress(true, false), 0, 1, 0f)
    }

    @Test
    fun sessionAndRepairKeysAreStableAndNullSafe() {
        val session = RecordsSchedulerModels.StudySession(
            item("裂", RecordsBase.LadderRung.KANJI_MEANING, 0, 0),
            row("裂"),
            "token-1",
            BridgeScheduler.TASK_KANJI_MEANING,
            false,
            "split"
        )
        val repair = RecordsImportModels.SimilarKanjiWritingRepair(
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
        )

        assertEquals("", StudySessionProgressTracker.sessionTaskKey(null))
        assertEquals("session:kanji_meaning:裂:token-1", StudySessionProgressTracker.sessionTaskKey(session))
        assertEquals("", StudySessionProgressTracker.similarRepairProgressKey(null))
        assertEquals("repair:42", StudySessionProgressTracker.similarRepairProgressKey(repair))
        assertEquals("", StudySessionProgressTracker.similarRepairStudyTaskKey(null))
        assertEquals("repair:42:token-2", StudySessionProgressTracker.similarRepairStudyTaskKey(repair))
    }

    @Test
    fun reviewOutcomesTrackMovedForwardAndMissedKanji() {
        val tracker = StudySessionProgressTracker()
        val before = item("裂", RecordsBase.LadderRung.KANJI_MEANING, 0, 0)
        val locallyImproved = item("裂", RecordsBase.LadderRung.KANJI_MEANING, 1, 0)
        val streakImproved = item("裂", RecordsBase.LadderRung.KANJI_MEANING, 0, 2)

        tracker.recordReviewOutcome(" 裂 ", BridgeScheduler.RATING_AGAIN, before, before)
        assertEquals(0, tracker.movedForwardCount())
        assertEquals(1, tracker.missedCount())

        tracker.recordReviewOutcome("裂", BridgeScheduler.RATING_GOOD, before, before)
        assertEquals(1, tracker.movedForwardCount())
        assertEquals(0, tracker.missedCount())

        tracker.recordReviewOutcome("浅", BridgeScheduler.RATING_AGAIN, before, locallyImproved)
        tracker.recordReviewOutcome("語", BridgeScheduler.RATING_AGAIN, before, streakImproved)
        assertEquals(3, tracker.movedForwardCount())
        assertEquals(0, tracker.missedCount())

        tracker.recordReviewOutcome(null, BridgeScheduler.RATING_AGAIN, before, before)
        assertEquals(3, tracker.movedForwardCount())
        assertEquals(0, tracker.missedCount())
    }

    @Test
    fun repairOutcomesDoNotDemoteAlreadyMovedKanjiToMissed() {
        val tracker = StudySessionProgressTracker()

        tracker.recordRepairOutcome("裂", false)
        assertEquals(0, tracker.movedForwardCount())
        assertEquals(1, tracker.missedCount())

        tracker.recordRepairOutcome("裂", true)
        assertEquals(1, tracker.movedForwardCount())
        assertEquals(0, tracker.missedCount())

        tracker.recordRepairOutcome("裂", false)
        tracker.recordRepairOutcome("   ", false)
        assertEquals(1, tracker.movedForwardCount())
        assertEquals(0, tracker.missedCount())
    }

    @Test
    fun reviewOutcomeKanjiTrimKeepsJavaWhitespaceSemantics() {
        val tracker = StudySessionProgressTracker()
        val before = item("裂", RecordsBase.LadderRung.KANJI_MEANING, 0, 0)

        tracker.recordReviewOutcome("　", BridgeScheduler.RATING_AGAIN, before, before)
        tracker.recordReviewOutcome("　裂　", BridgeScheduler.RATING_AGAIN, before, before)

        assertEquals(0, tracker.movedForwardCount())
        assertEquals(2, tracker.missedCount())
    }

    @Test
    fun completedTaskBreakdownGroupsStudySummaryCategories() {
        val tracker = StudySessionProgressTracker()
        tracker.markTaskCompleted("session:write_kanji:裂:token-1")
        tracker.markTaskCompleted("session:targeted_writing:浅:token-2")
        tracker.markTaskCompleted("session:similar_kanji:裂:token-3")
        tracker.markTaskCompleted("session:word_reading:語:token-4")
        tracker.markTaskCompleted("repair:42")
        tracker.markTaskCompleted("repair:42")
        tracker.markTaskCompleted("session:kanji_meaning:宮:token-5")

        val breakdown = tracker.completedTaskBreakdown()

        assertEquals(2, breakdown.writingChecks)
        assertEquals(1, breakdown.similarKanjiChoices)
        assertEquals(1, breakdown.similarKanjiRepairs)
        assertEquals(1, breakdown.wordReadingReviews)
        assertEquals(1, breakdown.otherReviews)
        assertEquals(6, breakdown.total)
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
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
            emptyList<RecordsImportModels.Example>()
        )

    private fun item(
        kanji: String,
        rung: RecordsBase.LadderRung,
        writingLevel: Int,
        realPassStreak: Int
    ): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(kanji, "review", 100L, 1.0, 5.0, 1, 0, 0, 0, null, 0L)
            .copyBuilder()
            .rung(rung)
            .writingLevel(writingLevel)
            .realPassStreak(realPassStreak)
            .build()

    private fun assertTopBar(
        progress: StudySessionProgressTracker.TopBarProgress,
        completed: Int,
        target: Int,
        fraction: Float
    ) {
        assertEquals(completed, progress.completed)
        assertEquals(target, progress.target)
        assertEquals(fraction, progress.fraction, 0.0001f)
    }
}
