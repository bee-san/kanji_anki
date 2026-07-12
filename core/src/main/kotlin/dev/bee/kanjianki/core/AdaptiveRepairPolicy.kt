package dev.bee.kanjianki.core

import java.util.LinkedHashSet

/** Pure selection and progression rules for inline targeted repair. */
object AdaptiveRepairPolicy {
    const val SYNTHETIC_REPAIR_STEP_MINUTES: Int = 10

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
            FailureRecurrence(observed, current.count.coerceAtLeast(0) + 1)
        } else {
            FailureRecurrence(observed, 1)
        }
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

    private fun priorityFallback(request: RepairRequest): List<String> {
        val relevant = relevantRepairs(request.coreSkill)
        val selected = request.priorityTaskTypes.firstOrNull { it in relevant && request.isUsable(it) }
            ?: relevant.firstOrNull { request.isUsable(it) }
        return selected?.let(::listOf).orEmpty()
    }

    private fun relevantRepairs(coreSkill: CoreSkill): List<String> = when (coreSkill) {
        CoreSkill.RECOGNITION -> listOf(
            StudyTaskTypes.SIMILAR_KANJI,
            StudyTaskTypes.MEANING_KANJI,
            StudyTaskTypes.TYPE_MEANING,
            StudyTaskTypes.WRITE_KANJI,
        )

        CoreSkill.CONTEXTUAL_READING -> listOf(
            StudyTaskTypes.READING_KANJI,
            StudyTaskTypes.KANJI_READING,
            StudyTaskTypes.TYPE_READING,
        )
    }

    private fun List<String>.distinctInOrder(): List<String> = LinkedHashSet(this).toList()
}
