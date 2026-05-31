package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionProgressTrackerTest {
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
