package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyItemReconciliationPolicyTest {
    @Test
    fun mergePreservesComplementaryDurableEvidenceIndependentlyOfInputOrder() {
        val reviewedMemory = RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            900L,
            4.0,
            5.0,
            2,
            1,
            0,
            "good",
            4,
            1,
            800L,
            850L,
        )
        val leftRoute = AdaptiveRouteState(
            coreDueAtMillis = 500L,
            recognitionReviewCount = 3,
            contextualReadingReviewCount = 1,
            repairAttemptCount = 2,
        )
        val rightRoute = AdaptiveRouteState(
            coreDueAtMillis = 500L,
            recognitionReviewCount = 1,
            contextualReadingReviewCount = 3,
            recurringFailure = FailureKind.WRONG_READING,
            recurringFailureCount = 4,
            repairAttemptCount = 2,
        )
        val left = baseItem()
            .withTaskMemory(StudyTaskTypes.WORD_READING, reviewedMemory)
            .copyBuilder()
            .lapses(2)
            .realPassStreak(1)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(leftRoute))
            .build()
        val right = baseItem()
            .withTaskMemory(StudyTaskTypes.TYPING_MEANING, reviewedMemory)
            .copyBuilder()
            .lapses(3)
            .realPassStreak(2)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(rightRoute))
            .build()
        val strongestSingleRecord = baseItem()
            .withTaskMemory(
                StudyTaskTypes.SENTENCE_READING,
                RecordsStudyModels.TaskMemory(
                    StudyLadderRules.STATE_REVIEW,
                    950L,
                    5.0,
                    4.5,
                    3,
                    0,
                    0,
                    "easy",
                    5,
                    0,
                    900L,
                    925L,
                ),
            )
            .copyBuilder()
            .realPassStreak(9)
            .build()

        val merged = StudyItemReconciliationPolicy.mergeAll(listOf(left, right, strongestSingleRecord))
        val reordered = StudyItemReconciliationPolicy.mergeAll(listOf(right, strongestSingleRecord, left))
        val route = AdaptiveRouteStateCodec.decode(merged.adaptiveRouteStateJson)!!

        assertEquals(3, merged.lapses)
        assertEquals(reviewedMemory.encode(), merged.wordReadingMemory.encode())
        assertEquals(reviewedMemory.encode(), merged.typingMeaningMemory.encode())
        assertEquals(3, route.recognitionReviewCount)
        assertEquals(3, route.contextualReadingReviewCount)
        assertEquals(FailureKind.WRONG_READING, route.recurringFailure)
        assertEquals(4, route.recurringFailureCount)
        assertEquals(9, merged.realPassStreak)
        assertEquals(merged.realPassStreak, reordered.realPassStreak)
        assertEquals(merged.adaptiveRouteStateJson, reordered.adaptiveRouteStateJson)
        assertEquals(merged.wordReadingMemory.encode(), reordered.wordReadingMemory.encode())
        assertEquals(merged.typingMeaningMemory.encode(), reordered.typingMeaningMemory.encode())
    }

    private fun baseItem(): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            "裂", "review", 1_000L, 4.0, 5.0, 5,
            1, 0, 0, 0, 0, 0L, false, "", 0L, 5,
            "裂|裂ける|さける|split", "", 100L,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .schedulerRevision(7L)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .lastRealReviewDueAtMillis(700L)
            .build()
    }
}
