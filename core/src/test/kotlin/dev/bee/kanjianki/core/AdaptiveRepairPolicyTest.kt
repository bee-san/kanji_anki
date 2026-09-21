package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
    fun passResolvesRecurrenceOnlyOnRealDuePromotionStrengthMemory() {
        val recurrence = AdaptiveRepairPolicy.FailureRecurrence(FailureKind.VISUAL_CONFUSION, 2)
        val promotionDays = 21
        val strong = (promotionDays + 1).toLong() * StudyLadderRules.DAY
        val weak = promotionDays.toLong() * StudyLadderRules.DAY

        // A one-day revalidation pass keeps the same-cause history alive.
        assertEquals(recurrence, AdaptiveRepairPolicy.recordPass(recurrence, true, weak, promotionDays))
        // Study-ahead passes never resolve it, however strong the memory looks.
        assertEquals(recurrence, AdaptiveRepairPolicy.recordPass(recurrence, false, strong, promotionDays))
        // A real-due pass with promotion-strength memory does.
        assertEquals(
            AdaptiveRepairPolicy.FailureRecurrence(),
            AdaptiveRepairPolicy.recordPass(recurrence, true, strong, promotionDays),
        )
        // Nothing to resolve stays untouched, and a non-positive threshold is clamped to one day.
        val empty = AdaptiveRepairPolicy.FailureRecurrence()
        assertSame(empty, AdaptiveRepairPolicy.recordPass(empty, true, strong, promotionDays))
        assertEquals(
            AdaptiveRepairPolicy.FailureRecurrence(),
            AdaptiveRepairPolicy.recordPass(recurrence, true, 2L * StudyLadderRules.DAY, 0),
        )
    }

    @Test
    fun exhaustionTriggersOnTheLastAllowedAppearanceAndSaturates() {
        val limit = AdaptiveRepairPolicy.MAX_REPAIR_ATTEMPTS
        assertFalse(AdaptiveRepairPolicy.isExhausted(0))
        assertFalse(AdaptiveRepairPolicy.isExhausted(limit - 2))
        assertTrue(AdaptiveRepairPolicy.isExhausted(limit - 1))
        assertTrue(AdaptiveRepairPolicy.isExhausted(limit))
        assertTrue(AdaptiveRepairPolicy.isExhausted(Int.MAX_VALUE))
        assertFalse(AdaptiveRepairPolicy.isExhausted(-5))
        assertTrue(AdaptiveRepairPolicy.isExhausted(0, maxAttempts = 0))
        assertEquals(limit, AdaptiveStudyHealthPolicy.STUCK_REPAIR_ATTEMPTS)
    }

    @Test
    fun recurrenceAndTaskProgressionSaturateAtIntegerLimits() {
        val recurrence = AdaptiveRepairPolicy.recordFailure(
            AdaptiveRepairPolicy.FailureRecurrence(FailureKind.WRONG_READING, Int.MAX_VALUE),
            FailureKind.WRONG_READING,
            true,
        )

        assertEquals(Int.MAX_VALUE, recurrence.count)
        assertEquals(3, AdaptiveRepairPolicy.nextTaskIndex(Int.MAX_VALUE, 3, StudyRatings.GOOD))
        assertEquals(
            Int.MAX_VALUE,
            AdaptiveRepairPolicy.nextTaskIndex(Int.MAX_VALUE, Int.MAX_VALUE, StudyRatings.GOOD),
        )
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
