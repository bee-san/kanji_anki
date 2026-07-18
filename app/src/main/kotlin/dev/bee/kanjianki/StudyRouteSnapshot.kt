package dev.bee.kanjianki

import java.util.Collections
import java.util.LinkedHashSet

@JvmInline
internal value class StudyRouteVersion(val value: Long) {
    fun next(): StudyRouteVersion = StudyRouteVersion(Math.addExact(value, 1L))
}

@JvmInline
internal value class StudySessionGeneration(val value: Long) {
    fun next(): StudySessionGeneration = StudySessionGeneration(Math.addExact(value, 1L))
}

internal enum class StudyRouteCompletionReason {
    HARD_CAP,
    FOCUS_COMPLETE,
    LEARN_AHEAD_REPEAT,
    REPAIR,
    NO_SESSION,
    TARGET_RECONCILIATION,
    EXPLICIT_CONTINUE,
    UNDO,
    RESTORE,
    STALE_CALLBACK_DROPPED,
}

@ConsistentCopyVisibility
internal data class StudyRoutePendingWork private constructor(
    val pendingTaskKeys: Set<String>,
    val requeuedTaskKeys: Set<String>,
    val learnAheadRepeatTaskKeys: Set<String>,
    val repairTaskKeys: Set<String>,
) {
    val taskKeys: Set<String>
        get() = pendingTaskKeys +
            requeuedTaskKeys +
            learnAheadRepeatTaskKeys +
            repairTaskKeys

    val blockerCount: Int
        get() = taskKeys.size

    val hasBlockers: Boolean
        get() = blockerCount > 0

    fun mergedWith(other: StudyRoutePendingWork): StudyRoutePendingWork = of(
        pendingTaskKeys + other.pendingTaskKeys,
        requeuedTaskKeys + other.requeuedTaskKeys,
        learnAheadRepeatTaskKeys + other.learnAheadRepeatTaskKeys,
        repairTaskKeys + other.repairTaskKeys,
    )

    fun resolving(resolvedTaskKeys: Set<String>): StudyRoutePendingWork = of(
        pendingTaskKeys - resolvedTaskKeys,
        requeuedTaskKeys - resolvedTaskKeys,
        learnAheadRepeatTaskKeys - resolvedTaskKeys,
        repairTaskKeys - resolvedTaskKeys,
    )

    companion object {
        val NONE = StudyRoutePendingWork(emptySet(), emptySet(), emptySet(), emptySet())

        fun of(
            pendingTaskKeys: Iterable<String> = emptyList(),
            requeuedTaskKeys: Iterable<String> = emptyList(),
            learnAheadRepeatTaskKeys: Iterable<String> = emptyList(),
            repairTaskKeys: Iterable<String> = emptyList(),
        ): StudyRoutePendingWork {
            val normalizedPending = immutableKeys(pendingTaskKeys)
            val normalizedRequeued = immutableKeys(requeuedTaskKeys)
            val normalizedRepeats = immutableKeys(learnAheadRepeatTaskKeys)
            val normalizedRepairs = immutableKeys(repairTaskKeys)
            if (
                normalizedPending.isEmpty() &&
                normalizedRequeued.isEmpty() &&
                normalizedRepeats.isEmpty() &&
                normalizedRepairs.isEmpty()
            ) {
                return NONE
            }
            return StudyRoutePendingWork(
                normalizedPending,
                normalizedRequeued,
                normalizedRepeats,
                normalizedRepairs,
            )
        }

        private fun immutableKeys(keys: Iterable<String>): Set<String> {
            val normalized = LinkedHashSet<String>()
            for (key in keys) {
                if (key.isNotBlank()) normalized.add(key)
            }
            return if (normalized.isEmpty()) emptySet() else Collections.unmodifiableSet(normalized)
        }
    }
}

internal data class StudyRouteActionClaim(
    val sessionToken: String,
    val sessionGeneration: StudySessionGeneration,
    val routeVersion: StudyRouteVersion,
)

internal data class StudyRouteSnapshot(
    val version: StudyRouteVersion = StudyRouteVersion(0L),
    val sessionGeneration: StudySessionGeneration = StudySessionGeneration(0L),
    val sessionToken: String? = null,
    val phase: StudySessionPhase = StudySessionPhase.IDLE,
    val feedback: StudyAnswerFeedbackSnapshot? = null,
    val progress: StudySessionProgressUiState = StudySessionProgressUiState(),
    val pendingWork: StudyRoutePendingWork = StudyRoutePendingWork.NONE,
    val completionEvidenceReason: StudyRouteCompletionReason? = null,
    val completionReason: StudyRouteCompletionReason? = null,
) {
    val displayedCompletedCount: Int
        get() = progress.completedCount

    val displayedTargetCount: Int
        get() = if (
            progress.activeTask &&
            progress.completedCount == progress.targetCount &&
            progress.targetCount < Int.MAX_VALUE
        ) {
            progress.targetCount + 1
        } else {
            progress.targetCount
        }

    val remainingCount: Int
        get() = progress.targetCount - progress.completedCount

    val canComplete: Boolean
        get() = progress.completedCount == progress.targetCount &&
            !progress.activeTask &&
            !pendingWork.hasBlockers &&
            feedback?.phase != StudyAnswerFeedbackPhase.SUBMITTING &&
            feedback?.phase != StudyAnswerFeedbackPhase.APPLIED

    val isComplete: Boolean
        get() = phase == StudySessionPhase.COMPLETE && canComplete
}
