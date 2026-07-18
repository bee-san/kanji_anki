package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun rejectsEmptyOrMixedKanjiFamilies() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyItemReconciliationPolicy.mergeAll(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudyItemReconciliationPolicy.merge(baseItem("裂"), baseItem("痛"))
        }
    }

    @Test
    fun primaryDonorFollowsDurableEvidenceBeforeRevision() {
        assertPrimaryDonor(
            baseItem().copyBuilder().totalReviews(5).schedulerRevision(100L).realPassStreak(1).build(),
            baseItem().copyBuilder().totalReviews(6).schedulerRevision(1L).realPassStreak(2).build(),
            expectedMarker = 2,
        )
        assertPrimaryDonor(
            baseItem().withTaskMemory(StudyTaskTypes.WORD_READING, memory(totalReviews = 1))
                .copyBuilder().realPassStreak(1).build(),
            baseItem().withTaskMemory(StudyTaskTypes.WORD_READING, memory(totalReviews = 2))
                .copyBuilder().realPassStreak(2).build(),
            expectedMarker = 2,
        )
        assertPrimaryDonor(
            baseItem().withTaskMemory(
                StudyTaskTypes.WORD_READING,
                memory(totalReviews = 2, lastReviewedAt = 100L),
            ).copyBuilder().realPassStreak(1).build(),
            baseItem().withTaskMemory(
                StudyTaskTypes.WORD_READING,
                memory(totalReviews = 2, lastReviewedAt = 200L),
            ).copyBuilder().realPassStreak(2).build(),
            expectedMarker = 2,
        )
        assertPrimaryDonor(
            baseItem().copyBuilder().lapses(2).realPassStreak(1).build(),
            baseItem().copyBuilder().lapses(3).realPassStreak(2).build(),
            expectedMarker = 2,
        )
        assertPrimaryDonor(
            baseItem().copyBuilder().lastRealReviewDueAtMillis(700L).realPassStreak(1).build(),
            baseItem().copyBuilder().lastRealReviewDueAtMillis(800L).realPassStreak(2).build(),
            expectedMarker = 2,
        )
        assertPrimaryDonor(
            baseItem().copyBuilder().schedulerRevision(7L).realPassStreak(1).build(),
            baseItem().copyBuilder().schedulerRevision(8L).realPassStreak(2).build(),
            expectedMarker = 2,
        )
    }

    @Test
    fun exactDurableEvidenceTieUsesStableFingerprint() {
        val earlier = baseItem().copyBuilder().dueAtMillis(900L).build()
        val later = baseItem().copyBuilder().dueAtMillis(1_100L).build()

        val forward = StudyItemReconciliationPolicy.merge(earlier, later)
        val reverse = StudyItemReconciliationPolicy.merge(later, earlier)

        assertEquals(forward.dueAtMillis, reverse.dueAtMillis)
        assertEquals(forward.answerSignature, reverse.answerSignature)
    }

    @Test
    fun taskMemoryDonorUsesReviewRecencyAndSchedulingEvidenceInOrder() {
        assertMemoryDonor(memory(totalReviews = 1), memory(totalReviews = 2))
        assertMemoryDonor(
            memory(totalReviews = 2, lastReviewedAt = 100L),
            memory(totalReviews = 2, lastReviewedAt = 200L),
        )
        assertMemoryDonor(
            memory(totalReviews = 2, lastReviewedAt = 200L, lapses = 1),
            memory(totalReviews = 2, lastReviewedAt = 200L, lapses = 2),
        )
        assertMemoryDonor(
            memory(totalReviews = 2, lastReviewedAt = 200L, lapses = 2, lastPassedDueAt = 300L),
            memory(totalReviews = 2, lastReviewedAt = 200L, lapses = 2, lastPassedDueAt = 400L),
        )
        assertMemoryDonor(
            memory(totalReviews = 2, lastReviewedAt = 200L, lapses = 2, lastPassedDueAt = 400L, dueAt = 500L),
            memory(totalReviews = 2, lastReviewedAt = 200L, lapses = 2, lastPassedDueAt = 400L, dueAt = 600L),
        )

        val again = memory(totalReviews = 2, lastReviewedAt = 200L, lastRating = "again")
        val good = memory(totalReviews = 2, lastReviewedAt = 200L, lastRating = "good")
        val forward = mergedWordMemory(again, good)
        val reverse = mergedWordMemory(good, again)
        assertEquals(forward.encode(), reverse.encode())
    }

    @Test
    fun routeDonorUsesActivityAttemptsReviewsAndRecencyInOrder() {
        assertRouteDonor(
            AdaptiveRouteState(repairStepMinutes = listOf(1)),
            AdaptiveRouteState(revalidationPending = true, repairStepMinutes = listOf(2)),
            expectedMarker = 2,
        )
        assertRouteDonor(
            AdaptiveRouteState(revalidationPending = true, repairStepMinutes = listOf(1)),
            AdaptiveRouteState(
                activeRepairTasks = listOf(StudyTaskTypes.TYPE_READING),
                repairStepMinutes = listOf(2),
            ),
            expectedMarker = 2,
        )
        assertRouteDonor(
            AdaptiveRouteState(repairAttemptCount = 1, repairStepMinutes = listOf(1)),
            AdaptiveRouteState(repairAttemptCount = 2, repairStepMinutes = listOf(2)),
            expectedMarker = 2,
        )
        assertRouteDonor(
            AdaptiveRouteState(recognitionReviewCount = 1, repairStepMinutes = listOf(1)),
            AdaptiveRouteState(recognitionReviewCount = 2, repairStepMinutes = listOf(2)),
            expectedMarker = 2,
        )
        assertRouteDonor(
            AdaptiveRouteState(coreDueAtMillis = 100L, repairStepMinutes = listOf(1)),
            AdaptiveRouteState(repairStartedAtMillis = 200L, repairStepMinutes = listOf(2)),
            expectedMarker = 2,
        )

        val first = AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION, repairStepMinutes = listOf(1))
        val second = AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING, repairStepMinutes = listOf(2))
        val forward = mergedRoute(first, second)
        val reverse = mergedRoute(second, first)
        assertEquals(forward.repairStepMinutes, reverse.repairStepMinutes)
    }

    @Test
    fun routeMergePreservesFailureAndAnswerEvidenceFromCompatibleDonors() {
        val active = AdaptiveRouteState(
            activeRepairTasks = listOf(StudyTaskTypes.TYPE_READING),
            repairStepMinutes = listOf(10),
        )
        val failure = AdaptiveRouteState(
            recurringFailure = FailureKind.WRONG_READING,
            recurringFailureCount = 3,
        )
        val evidence = AnswerEvidence(
            coreSkill = CoreSkill.CONTEXTUAL_READING,
            selectedAnswer = "いたみ",
            correctAnswer = "いたむ",
        )
        val answered = AdaptiveRouteState(answerEvidence = evidence)

        val merged = StudyItemReconciliationPolicy.mergeAll(
            listOf(itemWithRoute(active), itemWithRoute(failure), itemWithRoute(answered)),
        )
        val route = AdaptiveRouteStateCodec.decode(merged.adaptiveRouteStateJson)!!

        assertEquals(listOf(10), route.repairStepMinutes)
        assertEquals(FailureKind.WRONG_READING, route.recurringFailure)
        assertEquals(3, route.recurringFailureCount)
        assertEquals(evidence, route.answerEvidence)
    }

    @Test
    fun routeMergeKeepsFailureCountWithSelectedFailureKind() {
        val selectedFailure = AdaptiveRouteState(
            activeRepairTasks = listOf(StudyTaskTypes.TYPE_READING),
            recurringFailure = FailureKind.WRONG_READING,
            recurringFailureCount = 2,
        )
        val otherFailure = AdaptiveRouteState(
            recurringFailure = FailureKind.MEANING_UNKNOWN,
            recurringFailureCount = 5,
        )

        val forward = mergedRoute(selectedFailure, otherFailure)
        val reverse = mergedRoute(otherFailure, selectedFailure)

        assertEquals(FailureKind.WRONG_READING, forward.recurringFailure)
        assertEquals(2, forward.recurringFailureCount)
        assertEquals(forward, reverse)

        val sameKindWithHigherCount = mergedRoute(
            selectedFailure.copy(recurringFailureCount = 1),
            otherFailure.copy(recurringFailure = FailureKind.WRONG_READING),
        )
        assertEquals(FailureKind.WRONG_READING, sameKindWithHigherCount.recurringFailure)
        assertEquals(5, sameKindWithHigherCount.recurringFailureCount)
    }

    @Test
    fun routeMergeStaysEmptyWithoutPersistedRouteState() {
        val merged = StudyItemReconciliationPolicy.merge(baseItem(), baseItem())

        assertEquals("", merged.adaptiveRouteStateJson)
    }

    private fun assertPrimaryDonor(
        weaker: RecordsStudyModels.StudyItem,
        stronger: RecordsStudyModels.StudyItem,
        expectedMarker: Int,
    ) {
        assertEquals(expectedMarker, StudyItemReconciliationPolicy.merge(weaker, stronger).realPassStreak)
        assertEquals(expectedMarker, StudyItemReconciliationPolicy.merge(stronger, weaker).realPassStreak)
    }

    private fun assertMemoryDonor(
        weaker: RecordsStudyModels.TaskMemory,
        stronger: RecordsStudyModels.TaskMemory,
    ) {
        assertEquals(stronger.encode(), mergedWordMemory(weaker, stronger).encode())
        assertEquals(stronger.encode(), mergedWordMemory(stronger, weaker).encode())
    }

    private fun mergedWordMemory(
        left: RecordsStudyModels.TaskMemory,
        right: RecordsStudyModels.TaskMemory,
    ): RecordsStudyModels.TaskMemory {
        return StudyItemReconciliationPolicy.merge(
            baseItem().withTaskMemory(StudyTaskTypes.WORD_READING, left),
            baseItem().withTaskMemory(StudyTaskTypes.WORD_READING, right),
        ).wordReadingMemory
    }

    private fun assertRouteDonor(
        weaker: AdaptiveRouteState,
        stronger: AdaptiveRouteState,
        expectedMarker: Int,
    ) {
        assertEquals(listOf(expectedMarker), mergedRoute(weaker, stronger).repairStepMinutes)
        assertEquals(listOf(expectedMarker), mergedRoute(stronger, weaker).repairStepMinutes)
    }

    private fun mergedRoute(left: AdaptiveRouteState, right: AdaptiveRouteState): AdaptiveRouteState {
        val merged = StudyItemReconciliationPolicy.merge(itemWithRoute(left), itemWithRoute(right))
        return AdaptiveRouteStateCodec.decode(merged.adaptiveRouteStateJson)!!
    }

    private fun itemWithRoute(route: AdaptiveRouteState): RecordsStudyModels.StudyItem {
        return baseItem().copyBuilder()
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
    }

    private fun memory(
        totalReviews: Int,
        lastReviewedAt: Long = 0L,
        lapses: Int = 0,
        lastPassedDueAt: Long = 0L,
        dueAt: Long = 0L,
        lastRating: String = "good",
    ): RecordsStudyModels.TaskMemory {
        return RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            dueAt,
            4.0,
            5.0,
            totalReviews,
            lapses,
            0,
            lastRating,
            0,
            0,
            lastReviewedAt,
            lastPassedDueAt,
        )
    }

    private fun baseItem(kanji: String = "裂"): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji, "review", 1_000L, 4.0, 5.0, 5,
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
