package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LadderCompletionForecastPolicyTest {
    @Test fun singleItemClimbsAllValidRungsAndFinishesAfterCeilingValidation() {
        val result = forecast(listOf(row("裂")), horizonDays = 730)
        assertEquals(1, result.totalItems)
        assertFalse(result.beyondHorizon)
        assertNotNull(result.projectedCompletionMonthMillis)
        assertEquals(0, result.burnDown.last().remainingItems)
        assertEquals(
            listOf("all_passes", "adaptive_repairs_and_revalidation", "anki_retirement_separate"),
            result.assumptionCopyIds,
        )
    }

    @Test fun activeQueueCapProducesVisibleAdmissionWaveAndRunIsDeterministic() {
        val settings = settings(activeCap = 1, newPerDay = 1)
        val first = forecast(listOf(row("裂"), row("弱")), settings = settings, horizonDays = 730)
        val second = forecast(listOf(row("裂"), row("弱")), settings = settings, horizonDays = 730)
        assertEquals(first, second)
        assertEquals(2, first.totalItems)
        assertTrue(first.burnDown.map { it.completedItems }.distinct().size >= 2)
        assertTrue(first.burnDown.zipWithNext().all { (left, right) -> right.completedItems >= left.completedItems })
    }

    @Test fun unavailableConditionalRungsAreCrossedAndHorizonCutoffIsReported() {
        val ladder = RecordsBase.StudyLadderSettings(
            listOf(
                RecordsBase.LadderRung.KANJI_MEANING,
                RecordsBase.LadderRung.SIMILAR_KANJI,
                RecordsBase.LadderRung.WORD_READING,
            ),
            listOf(
                RecordsBase.LadderRung.KANJI_MEANING,
                RecordsBase.LadderRung.SIMILAR_KANJI,
                RecordsBase.LadderRung.WORD_READING,
            ),
        )
        val completed = forecast(listOf(row("裂")), ladder = ladder, horizonDays = 730)
        assertFalse(completed.beyondHorizon)
        val cutoff = forecast(listOf(row("裂")), ladder = ladder, horizonDays = 0)
        assertTrue(cutoff.beyondHorizon)
        assertNull(cutoff.projectedCompletionMonthMillis)
    }

    @Test fun parkedAndRetiredStartingItemsAreCountedAsAlreadyDone() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()
        val parked = reviewItem("裂", ladder.highestRung(RecordsBase.RungAvailability.none()), 100)
        val retired = reviewItem("退", ladder.highestRung(RecordsBase.RungAvailability.none()), 100)
            .copyBuilder().state(StudyLadderRules.STATE_RETIRED).build()
        val result = LadderCompletionForecastPolicy.forecast(
            rows = emptyList(),
            startingItems = listOf(parked, retired),
            settings = settings(activeCap = 1, newPerDay = 1),
            parameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            learningSettings = RecordsSchedulerModels.LearningStepSettings.defaults(),
            ladder = ladder,
            nowMillis = START,
            horizonDays = 1,
        )
        assertEquals(1, result.alreadyAtCeiling)
        assertEquals(1, result.alreadyParked)
        assertEquals(1, result.alreadyRetired)
        assertFalse(result.beyondHorizon)
    }

    @Test fun emptyDeckHasStableZeroBurnDown() {
        val result = forecast(emptyList(), horizonDays = 0)
        assertEquals(0, result.totalItems)
        assertFalse(result.beyondHorizon)
        assertNull(result.projectedCompletionMonthMillis)
        assertEquals(listOf(0), result.burnDown.map { it.remainingItems }.distinct())
    }

    @Test fun goldenSmallDeckHasExactBurnDownAndCompletionMonth() {
        val ladder = RecordsBase.StudyLadderSettings(
            listOf(RecordsBase.LadderRung.KANJI_MEANING),
            listOf(RecordsBase.LadderRung.KANJI_MEANING),
        )
        val result = forecast(listOf(row("裂")), ladder = ladder, horizonDays = 60)
        assertEquals(1_701_388_800_000L, result.projectedCompletionMonthMillis)
        assertEquals(
            listOf(
                LadderCompletionForecastPolicy.MonthPoint(1_698_796_800_000L, 0, 1),
                LadderCompletionForecastPolicy.MonthPoint(1_701_388_800_000L, 1, 0),
                LadderCompletionForecastPolicy.MonthPoint(1_704_067_200_000L, 1, 0),
            ),
            result.burnDown,
        )
    }

    @Test fun legacyCeilingValidationConvertsAndContinuesThroughContextualCore() {
        val ladder = RecordsBase.StudyLadderSettings(
            listOf(RecordsBase.LadderRung.KANJI_MEANING),
            listOf(RecordsBase.LadderRung.KANJI_MEANING),
        )
        val freshPromotion = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 7)
            .copyBuilder().realPassStreak(0).dueAtMillis(START + 7 * BridgeScheduler.DAY).build()
        val result = LadderCompletionForecastPolicy.forecast(
            listOf(row("裂")), listOf(freshPromotion), settings(1, 1),
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            RecordsSchedulerModels.LearningStepSettings.defaults(), ladder, START, 730,
        )
        assertEquals(1, result.alreadyAtCeiling)
        assertFalse(result.beyondHorizon)
        assertNotNull(result.projectedCompletionMonthMillis)
        assertTrue(result.projectedCompletionMonthMillis!! > 1_698_796_800_000L)
    }

    @Test fun adaptiveContextualCompletionRequiresAnActualContextualReview() {
        val copiedPromotionMemory = adaptiveContextualItem(
            "裂",
            contextualReviews = 0,
            repairTasks = emptyList(),
            revalidationPending = false,
        )
        val completed = adaptiveContextualItem(
            "済",
            contextualReviews = 1,
            repairTasks = emptyList(),
            revalidationPending = false,
        )

        val result = LadderCompletionForecastPolicy.forecast(
            rows = listOf(row("裂"), row("済")),
            startingItems = listOf(copiedPromotionMemory, completed),
            settings = settings(2, 1),
            parameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            learningSettings = RecordsSchedulerModels.LearningStepSettings.defaults(),
            ladder = RecordsBase.StudyLadderSettings.defaults(),
            nowMillis = START,
            horizonDays = 0,
        )

        assertEquals(2, result.alreadyAtCeiling)
        assertTrue(result.beyondHorizon)
        assertEquals(1, result.burnDown.first().completedItems)
    }

    @Test fun adaptiveRepairAndRevalidationAreSimulatedBeforeCompletion() {
        val repairing = adaptiveContextualItem(
            "裂",
            contextualReviews = 1,
            repairTasks = listOf(StudyTaskTypes.TYPE_READING),
            revalidationPending = false,
        )

        val result = LadderCompletionForecastPolicy.forecast(
            rows = listOf(row("裂")),
            startingItems = listOf(repairing),
            settings = settings(1, 1),
            parameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            learningSettings = RecordsSchedulerModels.LearningStepSettings.defaults(),
            ladder = RecordsBase.StudyLadderSettings.defaults(),
            nowMillis = START,
            horizonDays = 30,
        )

        assertEquals(1, result.alreadyAtCeiling)
        assertFalse(result.beyondHorizon)
        assertEquals(0, result.burnDown.last().remainingItems)
    }

    @Test fun horizonAndMonthPointsAreIndependentOfDefaultTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            val honolulu = forecast(emptyList(), horizonDays = 90)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val tokyo = forecast(emptyList(), horizonDays = 90)
            assertEquals(honolulu.burnDown, tokyo.burnDown)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun forecast(
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings = settings(activeCap = 24, newPerDay = 3),
        ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults(),
        horizonDays: Int,
    ) = LadderCompletionForecastPolicy.forecast(
        rows, emptyList(), settings,
        RecordsSchedulerModels.SchedulerParameters.defaults(),
        RecordsSchedulerModels.LearningStepSettings.defaults(),
        ladder, START, horizonDays,
    )

    private fun row(kanji: String) = RecordsImportModels.DashboardRow(
        kanji, 900, "meaning", "reading", "search", 30, "weak", "Needs practice", 1, 1, 0,
        ArrayList<RecordsImportModels.Example>(),
    )

    private fun settings(activeCap: Int, newPerDay: Int) = RecordsSyncModels.Settings(
        "Kiku", "Mining", "Expression", "Reading", "Meaning", "Sentence",
        "Frequency", "FreqSort", 21, 2, 9000, activeCap, newPerDay,
    )

    private fun reviewItem(kanji: String, rung: RecordsBase.LadderRung, interval: Int) =
        RecordsStudyModels.StudyItem(kanji, "new", START, 4.0, 5.0, 4, 0, 0, 0, 0, 0, 0L, false, null, START)
            .copyBuilder()
            .state("review")
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .rung(rung)
            .matureIntervalDays(interval)
            .build()

    private fun adaptiveContextualItem(
        kanji: String,
        contextualReviews: Int,
        repairTasks: List<String>,
        revalidationPending: Boolean,
    ): RecordsStudyModels.StudyItem {
        val memory = RecordsStudyModels.TaskMemory(
            "review",
            START + 30 * BridgeScheduler.DAY,
            8.0,
            5.0,
            contextualReviews.coerceAtLeast(1),
            0,
            0,
            "good",
            30,
            1,
            START,
        )
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            contextualReadingReviewCount = contextualReviews,
            activeRepairTasks = repairTasks,
            repairStepMinutes = if (repairTasks.isEmpty()) emptyList() else listOf(10),
            repairDueAtMillis = if (repairTasks.isEmpty()) 0L else START,
            coreDueAtMillis = START + 30 * BridgeScheduler.DAY,
            recurringFailure = if (repairTasks.isEmpty() && !revalidationPending) null else FailureKind.WRONG_READING,
            recurringFailureCount = if (repairTasks.isEmpty() && !revalidationPending) 0 else 1,
            revalidationPending = revalidationPending,
        )
        val due = if (repairTasks.isEmpty()) START + 30 * BridgeScheduler.DAY else START
        return reviewItem(kanji, RecordsBase.LadderRung.WORD_READING, 30)
            .withTaskMemory(StudyTaskTypes.WORD_READING, memory)
            .copyBuilder()
            .state("review")
            .phase(if (repairTasks.isEmpty()) RecordsBase.SchedulerPhase.REVIEW else RecordsBase.SchedulerPhase.RELEARNING)
            .rung(RecordsBase.LadderRung.WORD_READING)
            .dueAtMillis(due)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
    }

    private companion object { const val START = 1_700_000_000_000L }
}
