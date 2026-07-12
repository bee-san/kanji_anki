package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdaptiveStudyHealthPolicyTest {
    @Test
    fun emptyMetricFactoryReturnsAZeroImmutableView() {
        val metric = AdaptiveStudyHealthPolicy.Metric.empty()

        assertEquals(0, metric.totalAdaptiveItems)
        assertEquals(0, metric.contextualCompleteCount)
        assertEquals(0, metric.countFor(CoreSkill.RECOGNITION))
        assertEquals(0, metric.repairCountFor(StudyTaskTypes.TYPE_READING))
        assertEquals(0, metric.failureCountFor(FailureKind.WRONG_READING))
    }

    @Test
    fun summarizeCountsCoreOwnershipAndRepairRiskOverlays() {
        val recognition = evidence(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION))
        val repair = evidence(
            AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                activeRepairTasks = listOf(StudyTaskTypes.KANJI_READING, StudyTaskTypes.TYPE_READING),
                repairTaskIndex = 0,
                recurringFailure = FailureKind.WRONG_READING,
                recurringFailureCount = 2,
                repairAttemptCount = AdaptiveStudyHealthPolicy.STUCK_REPAIR_ATTEMPTS,
            ),
            phase = RecordsBase.SchedulerPhase.RELEARNING,
        )
        val revalidation = evidence(
            AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                recurringFailure = FailureKind.HOMOPHONE_CONFUSION,
                recurringFailureCount = 1,
                revalidationPending = true,
            ),
        )

        val metric = AdaptiveStudyHealthPolicy.summarize(listOf(recognition, repair, revalidation), 3)

        assertEquals(3, metric.totalAdaptiveItems)
        assertEquals(1, metric.countFor(CoreSkill.RECOGNITION))
        assertEquals(2, metric.countFor(CoreSkill.CONTEXTUAL_READING))
        assertEquals(1, metric.activeRepairCount)
        assertEquals(1, metric.repairCountFor(StudyTaskTypes.KANJI_READING))
        assertEquals(1, metric.failureCountFor(FailureKind.WRONG_READING))
        assertEquals(1, metric.revalidationPendingCount)
        assertEquals(2, metric.recentCoreMissCount)
        assertEquals(1, metric.escalationRiskCount)
        assertEquals(1, metric.stuckRepairCount)
    }

    @Test
    fun contextualCompletionRequiresAContextualPassOutsideRepairAndRevalidation() {
        val completed = evidence(
            AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                contextualReadingReviewCount = 1,
            ),
            contextualPasses = 1,
        )
        val copiedPromotionMemory = evidence(
            AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING),
            contextualPasses = 1,
        )
        val validating = evidence(
            AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                contextualReadingReviewCount = 1,
                revalidationPending = true,
            ),
            contextualPasses = 1,
        )

        val metric = AdaptiveStudyHealthPolicy.summarize(
            listOf(completed, copiedPromotionMemory, validating),
            escalationThreshold = 3,
        )

        assertEquals(1, metric.contextualCompleteCount)
    }

    @Test
    fun legacyRetiredAndMalformedRowsDoNotPolluteAdaptiveDistribution() {
        val legacy = AdaptiveStudyHealthPolicy.ItemEvidence(
            "review", RecordsBase.SchedulerPhase.REVIEW, 1, "", 0,
        )
        val retired = AdaptiveStudyHealthPolicy.ItemEvidence(
            StudyLadderRules.STATE_RETIRED,
            RecordsBase.SchedulerPhase.REVIEW,
            AdaptiveStudyItemPolicy.ROUTING_VERSION,
            AdaptiveRouteStateCodec.encode(AdaptiveRouteState()),
            0,
        )
        val malformed = AdaptiveStudyHealthPolicy.ItemEvidence(
            "review", RecordsBase.SchedulerPhase.REVIEW, AdaptiveStudyItemPolicy.ROUTING_VERSION, "not-json", 0,
        )

        val metric = AdaptiveStudyHealthPolicy.summarize(listOf(null, legacy, retired, malformed), 0)

        assertEquals(0, metric.totalAdaptiveItems)
        assertEquals(1, metric.malformedStateCount)
        @Suppress("UNCHECKED_CAST")
        val mutable = metric.coreCounts as MutableMap<CoreSkill, Int>
        assertThrows(UnsupportedOperationException::class.java) {
            mutable[CoreSkill.RECOGNITION] = 2
        }
    }

    private fun evidence(
        route: AdaptiveRouteState,
        phase: RecordsBase.SchedulerPhase = RecordsBase.SchedulerPhase.REVIEW,
        contextualPasses: Int = 0,
    ) = AdaptiveStudyHealthPolicy.ItemEvidence(
        state = "review",
        phase = phase,
        routingVersion = AdaptiveStudyItemPolicy.ROUTING_VERSION,
        adaptiveRouteStateJson = AdaptiveRouteStateCodec.encode(route),
        contextualReadingConsecutivePasses = contextualPasses,
    )
}
