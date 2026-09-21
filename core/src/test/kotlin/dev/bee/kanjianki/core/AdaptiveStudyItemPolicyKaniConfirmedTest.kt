package dev.bee.kanjianki.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveStudyItemPolicyKaniConfirmedTest {
    private val settings = RecordsSyncModels.Settings.kikuDefaults()

    @Test
    fun matureContextualItemWithEnoughRealDuePassesIsConfirmed() {
        val item = contextualItem(
            consecutivePasses = settings.ladderPromotionMinPasses,
            intervalDays = settings.matureDays,
        )

        assertTrue(AdaptiveStudyItemPolicy.isKaniConfirmed(item, settings))
        assertTrue(AdaptiveStudyItemPolicy.isKaniConfirmed(item, null))
    }

    @Test
    fun shortIntervalOrTooFewPassesIsNotConfirmed() {
        val shortInterval = contextualItem(
            consecutivePasses = settings.ladderPromotionMinPasses,
            intervalDays = settings.matureDays - 1,
        )
        val fewPasses = contextualItem(
            consecutivePasses = settings.ladderPromotionMinPasses - 1,
            intervalDays = settings.matureDays,
        )

        assertFalse(AdaptiveStudyItemPolicy.isKaniConfirmed(shortInterval, settings))
        assertFalse(AdaptiveStudyItemPolicy.isKaniConfirmed(fewPasses, settings))
    }

    @Test
    fun repairRevalidationRecognitionCoreOrLegacyItemIsNotConfirmed() {
        val inRepair = contextualItem(
            consecutivePasses = 5,
            intervalDays = 60,
            route = AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                contextualReadingReviewCount = 5,
                activeRepairTasks = listOf(StudyTaskTypes.KANJI_READING),
                repairStepMinutes = listOf(10),
            ),
        )
        val revalidating = contextualItem(
            consecutivePasses = 5,
            intervalDays = 60,
            route = AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                contextualReadingReviewCount = 5,
                revalidationPending = true,
            ),
        )
        val recognition = contextualItem(
            consecutivePasses = 5,
            intervalDays = 60,
            route = AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION, recognitionReviewCount = 5),
        )
        val legacy = contextualItem(consecutivePasses = 5, intervalDays = 60)
            .copyBuilder()
            .routingVersion(1)
            .adaptiveRouteStateJson("")
            .build()

        assertFalse(AdaptiveStudyItemPolicy.isKaniConfirmed(inRepair, settings))
        assertFalse(AdaptiveStudyItemPolicy.isKaniConfirmed(revalidating, settings))
        assertFalse(AdaptiveStudyItemPolicy.isKaniConfirmed(recognition, settings))
        assertFalse(AdaptiveStudyItemPolicy.isKaniConfirmed(legacy, settings))
        assertFalse(AdaptiveStudyItemPolicy.isKaniConfirmed(null, settings))
    }

    private fun contextualItem(
        consecutivePasses: Int,
        intervalDays: Int,
        route: AdaptiveRouteState = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            contextualReadingReviewCount = consecutivePasses.coerceAtLeast(1),
        ),
    ): RecordsStudyModels.StudyItem {
        val memory = RecordsStudyModels.TaskMemory.fromFields(
            RecordsStudyModels.TaskMemory.Fields(
                state = StudyLadderRules.STATE_REVIEW,
                dueAtMillis = NOW + intervalDays * StudyLadderRules.DAY,
                stability = intervalDays.toDouble(),
                difficulty = 5.0,
                totalReviews = consecutivePasses.coerceAtLeast(1),
                lapses = 0,
                learningStep = 0,
                lastRating = StudyRatings.GOOD,
                matureIntervalDays = intervalDays,
                consecutivePasses = consecutivePasses,
                lastPassedDueAtMillis = NOW - StudyLadderRules.DAY,
                lastReviewedAtMillis = NOW,
            ),
        )
        return RecordsStudyModels.StudyItem("徴", StudyLadderRules.STATE_REVIEW, NOW, 4.0, 5.0, 4, 0, 0, 2, null, NOW)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .matureIntervalDays(intervalDays)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
            .withTaskMemory(StudyTaskTypes.WORD_READING, memory)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
