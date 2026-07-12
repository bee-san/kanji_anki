package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRepairPolicyTest {
    private val allRepairs = setOf(
        StudyTaskTypes.MEANING_KANJI,
        StudyTaskTypes.TYPE_MEANING,
        StudyTaskTypes.SIMILAR_KANJI,
        StudyTaskTypes.WRITE_KANJI,
        StudyTaskTypes.KANJI_READING,
        StudyTaskTypes.READING_KANJI,
        StudyTaskTypes.TYPE_READING,
    )

    @Test
    fun knownFailuresUseTargetedFirstRepairAndThresholdEscalation() {
        assertEquals(
            listOf(StudyTaskTypes.MEANING_KANJI),
            select(FailureKind.MEANING_UNKNOWN, 1).taskTypes,
        )
        assertEquals(
            listOf(StudyTaskTypes.MEANING_KANJI, StudyTaskTypes.TYPE_MEANING),
            select(FailureKind.MEANING_UNKNOWN, 3).taskTypes,
        )
        assertEquals(
            listOf(StudyTaskTypes.SIMILAR_KANJI, StudyTaskTypes.WRITE_KANJI),
            select(FailureKind.VISUAL_CONFUSION, 3).taskTypes,
        )
        assertEquals(
            listOf(StudyTaskTypes.READING_KANJI, StudyTaskTypes.KANJI_READING, StudyTaskTypes.TYPE_READING),
            select(FailureKind.HOMOPHONE_CONFUSION, 3, CoreSkill.CONTEXTUAL_READING).taskTypes,
        )
        assertTrue(select(FailureKind.HOMOPHONE_CONFUSION, 3, CoreSkill.CONTEXTUAL_READING).escalated)
    }

    @Test
    fun unavailableExactChoiceFallsBackToTypedFullWordReading() {
        val result = AdaptiveRepairPolicy.select(
            request(
                failure = FailureKind.WRONG_READING,
                count = 1,
                core = CoreSkill.CONTEXTUAL_READING,
                available = setOf(StudyTaskTypes.TYPE_READING),
            ),
        )

        assertEquals(listOf(StudyTaskTypes.TYPE_READING), result.taskTypes)
        assertFalse(result.escalated)
    }

    @Test
    fun unknownFailureHonorsStoredPriorityWithinCurrentCore() {
        val result = AdaptiveRepairPolicy.select(
            request(
                failure = FailureKind.UNKNOWN,
                count = 1,
                core = CoreSkill.RECOGNITION,
                priority = listOf(StudyTaskTypes.TYPE_MEANING, StudyTaskTypes.SIMILAR_KANJI),
            ),
        )

        assertEquals(listOf(StudyTaskTypes.TYPE_MEANING), result.taskTypes)
    }

    @Test
    fun scheduleHonorsBothTaskSequenceAndConfiguredSteps() {
        assertEquals(
            AdaptiveRepairPolicy.RepairSchedule(
                listOf(StudyTaskTypes.MEANING_KANJI, StudyTaskTypes.TYPE_MEANING),
                listOf(10, 10),
            ),
            AdaptiveRepairPolicy.schedule(
                listOf(StudyTaskTypes.MEANING_KANJI, StudyTaskTypes.TYPE_MEANING),
                emptyList(),
            ),
        )
        assertEquals(
            AdaptiveRepairPolicy.RepairSchedule(
                listOf(StudyTaskTypes.TYPE_READING, StudyTaskTypes.TYPE_READING),
                listOf(5, 30),
            ),
            AdaptiveRepairPolicy.schedule(listOf(StudyTaskTypes.TYPE_READING), listOf(5, 30)),
        )
    }

    @Test
    fun recurrenceCountsOnlyMatchingRealDueFailuresAndValidationClearsIt() {
        val first = AdaptiveRepairPolicy.recordFailure(
            AdaptiveRepairPolicy.FailureRecurrence(),
            FailureKind.WRONG_READING,
            true,
        )
        val practice = AdaptiveRepairPolicy.recordFailure(first, FailureKind.WRONG_READING, false)
        val repeated = AdaptiveRepairPolicy.recordFailure(practice, FailureKind.WRONG_READING, true)
        val changed = AdaptiveRepairPolicy.recordFailure(repeated, FailureKind.HOMOPHONE_CONFUSION, true)

        assertEquals(1, first.count)
        assertEquals(first, practice)
        assertEquals(2, repeated.count)
        assertEquals(AdaptiveRepairPolicy.FailureRecurrence(FailureKind.HOMOPHONE_CONFUSION, 1), changed)
        assertEquals(AdaptiveRepairPolicy.FailureRecurrence(), AdaptiveRepairPolicy.clearAfterValidationPass())
    }

    @Test
    fun repairRatingsRestartRepeatAndAdvance() {
        assertEquals(0, AdaptiveRepairPolicy.nextTaskIndex(2, 3, StudyRatings.AGAIN))
        assertEquals(1, AdaptiveRepairPolicy.nextTaskIndex(1, 3, StudyRatings.HARD))
        assertEquals(2, AdaptiveRepairPolicy.nextTaskIndex(1, 3, StudyRatings.GOOD))
        assertEquals(3, AdaptiveRepairPolicy.nextTaskIndex(2, 3, StudyRatings.GOOD))
    }

    private fun select(
        failure: FailureKind,
        count: Int,
        core: CoreSkill = CoreSkill.RECOGNITION,
    ): AdaptiveRepairPolicy.RepairPlan = AdaptiveRepairPolicy.select(request(failure, count, core))

    private fun request(
        failure: FailureKind,
        count: Int,
        core: CoreSkill,
        available: Set<String> = allRepairs,
        priority: List<String> = allRepairs.toList(),
    ): AdaptiveRepairPolicy.RepairRequest = AdaptiveRepairPolicy.RepairRequest(
        coreSkill = core,
        failureKind = failure,
        sameIssueCount = count,
        escalationThreshold = 3,
        enabledTaskTypes = allRepairs,
        availableTaskTypes = available,
        priorityTaskTypes = priority,
    )
}
