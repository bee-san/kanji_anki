package dev.bee.kanjianki.core

import java.util.LinkedHashSet

/** Pure selection and progression rules for inline targeted repair. */
object AdaptiveRepairPolicy {
    const val SYNTHETIC_REPAIR_STEP_MINUTES: Int = 10

    /**
     * Maximum repair appearances in one repair episode. Hard repeats and Again
     * restarts would otherwise let a card loop on ten-minute repair steps with
     * no exit (most visibly `write_kanji`, which downgrades Good to Hard until a
     * clean write). Once this many appearances have been answered the episode is
     * exhausted: the card returns to core revalidation and the same-cause
     * history decides the next repair tool.
     */
    const val MAX_REPAIR_ATTEMPTS: Int = 6

    /** True when answering one more appearance would exceed [MAX_REPAIR_ATTEMPTS]. */
    @JvmStatic
    fun isExhausted(attemptsBefore: Int, maxAttempts: Int = MAX_REPAIR_ATTEMPTS): Boolean =
        saturatingAddNonNegative(attemptsBefore.coerceAtLeast(0), 1) >= maxAttempts.coerceAtLeast(1)

    data class RepairRequest(
        val coreSkill: CoreSkill,
        val failureKind: FailureKind,
        val sameIssueCount: Int,
        val escalationThreshold: Int,
        val enabledTaskTypes: Set<String>,
        val availableTaskTypes: Set<String>,
        val priorityTaskTypes: List<String>,
    )

    data class RepairPlan(
        val taskTypes: List<String>,
        val escalated: Boolean,
    )

    data class RepairSchedule(
        val taskTypes: List<String>,
        val delayMinutes: List<Int>,
    )

    /**
     * Returns the minimum targeted sequence for this occurrence. Before the
     * same-issue threshold only the first usable repair is required; at the
     * threshold the complete usable escalation chain is required.
     */
    @JvmStatic
    fun select(request: RepairRequest): RepairPlan {
        val preferred = preferredChain(request.failureKind)
        val usablePreferred = preferred.filter { request.isUsable(it) }
        val thresholdReached = request.sameIssueCount.coerceAtLeast(1) >=
            request.escalationThreshold.coerceAtLeast(1)
        val selected = when {
            usablePreferred.isNotEmpty() && thresholdReached -> usablePreferred
            usablePreferred.isNotEmpty() -> listOf(usablePreferred.first())
            else -> priorityFallback(request)
        }
        return RepairPlan(selected.distinctInOrder(), thresholdReached && selected.size > 1)
    }

    /**
     * Aligns the task sequence with the snapshotted relearning delays. The
     * final task or delay is reused when one list is shorter, so neither a
     * multi-task escalation nor a multi-step relearning configuration is lost.
     */
    @JvmStatic
    fun schedule(taskTypes: List<String>?, configuredStepMinutes: List<Int>?): RepairSchedule {
        val tasks = taskTypes.orEmpty().filter { it.isNotBlank() }
        if (tasks.isEmpty()) {
            return RepairSchedule(emptyList(), emptyList())
        }
        val delays = configuredStepMinutes.orEmpty().filter { it > 0 }.ifEmpty {
            listOf(SYNTHETIC_REPAIR_STEP_MINUTES)
        }
        val appearances = maxOf(tasks.size, delays.size)
        return RepairSchedule(
            taskTypes = List(appearances) { tasks[minOf(it, tasks.lastIndex)] },
            delayMinutes = List(appearances) { delays[minOf(it, delays.lastIndex)] },
        )
    }

    /** Again restarts repair, Hard repeats, and Good advances one appearance. */
    @JvmStatic
    fun nextTaskIndex(currentIndex: Int, taskCount: Int, rating: String?): Int {
        if (taskCount <= 0) {
            return 0
        }
        val current = currentIndex.coerceIn(0, taskCount - 1)
        return when (rating) {
            StudyRatings.AGAIN -> 0
            StudyRatings.GOOD -> (current + 1).coerceAtMost(taskCount)
            StudyRatings.HARD -> current
            else -> current
        }
    }

    /** Counts only real-due core/revalidation failures toward escalation. */
    @JvmStatic
    fun recordFailure(
        current: FailureRecurrence,
        observed: FailureKind,
        realDueCoreOrRevalidation: Boolean,
    ): FailureRecurrence {
        if (!realDueCoreOrRevalidation) {
            return current
        }
        return if (current.kind == observed) {
            FailureRecurrence(observed, saturatingAddNonNegative(current.count, 1))
        } else {
            FailureRecurrence(observed, 1)
        }
    }

    /**
     * Same-cause history survives a pass. A single revalidation pass one day
     * after a repair is not evidence that the underlying confusion is gone; the
     * chronic pattern (fail, repair, pass, fail again for the same cause weeks
     * later) must still accumulate toward the escalation threshold. The
     * recurrence is resolved only when a real-due pass shows promotion-strength
     * memory: the FSRS interval at fixed 0.90 retention exceeds
     * `ladder_promotion_interval_days`, the same gate recognition uses to
     * unlock contextual reading.
     */
    @JvmStatic
    fun recordPass(
        current: FailureRecurrence,
        realDue: Boolean,
        promotionIntervalMillis: Long,
        promotionIntervalDays: Int,
    ): FailureRecurrence {
        if (current.kind == null && current.count == 0) {
            return current
        }
        val resolved = realDue &&
            promotionIntervalMillis > promotionIntervalDays.coerceAtLeast(1).toLong() * StudyLadderRules.DAY
        return if (resolved) FailureRecurrence() else current
    }

    @JvmStatic
    fun clearAfterValidationPass(): FailureRecurrence = FailureRecurrence()

    data class FailureRecurrence(
        val kind: FailureKind? = null,
        val count: Int = 0,
    )

    private fun RepairRequest.isUsable(taskType: String): Boolean {
        return enabledTaskTypes.contains(taskType) && availableTaskTypes.contains(taskType)
    }

    private fun preferredChain(failureKind: FailureKind): List<String> = when (failureKind) {
        FailureKind.MEANING_UNKNOWN -> listOf(
            StudyTaskTypes.MEANING_KANJI,
            StudyTaskTypes.TYPE_MEANING,
        )

        FailureKind.VISUAL_CONFUSION -> listOf(
            StudyTaskTypes.SIMILAR_KANJI,
            StudyTaskTypes.WRITE_KANJI,
        )

        FailureKind.WRONG_READING -> listOf(
            StudyTaskTypes.KANJI_READING,
            StudyTaskTypes.TYPE_READING,
        )

        FailureKind.HOMOPHONE_CONFUSION -> listOf(
            StudyTaskTypes.READING_KANJI,
            StudyTaskTypes.KANJI_READING,
            StudyTaskTypes.TYPE_READING,
        )

        FailureKind.WRITING_SHAPE -> listOf(StudyTaskTypes.WRITE_KANJI)
        FailureKind.UNKNOWN -> emptyList()
    }

    /**
     * Fallback when the cause's preferred chain has no usable tool. Each tier is
     * searched in the learner's stored priority order first, then in the tier's
     * own order; a later tier is consulted only when nothing earlier is usable.
     *
     * A known cause stays inside its own tool family (reading causes -> reading
     * tools, shape/meaning causes -> recognition tools) so a reading failure with
     * no usable reading tool still exits to revalidation rather than receiving an
     * off-target drill. An unknown cause on the contextual core prefers reading
     * tools but can reach the shape/meaning tools, so it is never unrepairable
     * when the reading tools are disabled or lack data.
     */
    private fun priorityFallback(request: RepairRequest): List<String> {
        for (tier in fallbackTiers(request.coreSkill, request.failureKind)) {
            val selected = request.priorityTaskTypes.firstOrNull { it in tier && request.isUsable(it) }
                ?: tier.firstOrNull { request.isUsable(it) }
            if (selected != null) {
                return listOf(selected)
            }
        }
        return emptyList()
    }

    private fun fallbackTiers(coreSkill: CoreSkill, failureKind: FailureKind): List<List<String>> = when (failureKind) {
        FailureKind.WRONG_READING,
        FailureKind.HOMOPHONE_CONFUSION,
        -> listOf(READING_REPAIRS)

        FailureKind.MEANING_UNKNOWN,
        FailureKind.VISUAL_CONFUSION,
        FailureKind.WRITING_SHAPE,
        -> listOf(RECOGNITION_REPAIRS)

        FailureKind.UNKNOWN -> when (coreSkill) {
            CoreSkill.RECOGNITION -> listOf(RECOGNITION_REPAIRS)
            CoreSkill.CONTEXTUAL_READING -> listOf(READING_REPAIRS, RECOGNITION_REPAIRS)
        }
    }

    private val RECOGNITION_REPAIRS = listOf(
        StudyTaskTypes.SIMILAR_KANJI,
        StudyTaskTypes.MEANING_KANJI,
        StudyTaskTypes.TYPE_MEANING,
        StudyTaskTypes.WRITE_KANJI,
    )

    private val READING_REPAIRS = listOf(
        StudyTaskTypes.READING_KANJI,
        StudyTaskTypes.KANJI_READING,
        StudyTaskTypes.TYPE_READING,
    )

    private fun List<String>.distinctInOrder(): List<String> = LinkedHashSet(this).toList()
}
