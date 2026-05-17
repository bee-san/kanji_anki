package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyRating

class StudyProgressCalculator {
    fun reset(): StudyProgressState = StudyProgressState()

    fun initializeTarget(
        state: StudyProgressState,
        plan: StudyProgressPlan?,
    ): StudyProgressState {
        if (state.targetCount > 0 || plan == null) {
            return state
        }
        val target = if (plan.remainingCount > 0) {
            plan.remainingCount
        } else {
            plan.targetCount
        }
        return state.copy(targetCount = target.coerceAtLeast(0))
    }

    fun setTargetCount(
        state: StudyProgressState,
        targetCount: Int,
    ): StudyProgressState = state.copy(targetCount = targetCount.coerceAtLeast(0))

    fun includePendingTask(
        state: StudyProgressState,
        key: String?,
    ): StudyProgressUpdate {
        val safeKey = key.safeKey()
        if (
            safeKey.isEmpty() ||
            state.seenTaskKeys.contains(safeKey) ||
            state.completedTaskKeys.contains(safeKey)
        ) {
            return StudyProgressUpdate(state = state, accepted = false)
        }
        return StudyProgressUpdate(
            state = state.copy(
                seenTaskKeys = state.seenTaskKeys + safeKey,
                targetCount = state.targetCount + 1,
            ),
            accepted = true,
        )
    }

    fun registerTaskShown(
        state: StudyProgressState,
        key: String?,
    ): StudyProgressState {
        val safeKey = key.safeKey()
        if (safeKey.isEmpty()) {
            return state
        }
        return state.copy(
            seenTaskKeys = state.seenTaskKeys + safeKey,
            targetCount = if (state.targetCount <= 0) 1 else state.targetCount,
        )
    }

    fun markTaskCompleted(
        state: StudyProgressState,
        key: String?,
    ): StudyProgressState {
        val safeKey = key.safeKey()
        if (safeKey.isEmpty()) {
            return state
        }
        val shown = registerTaskShown(state, safeKey)
        if (shown.completedTaskKeys.contains(safeKey)) {
            return shown
        }
        val completed = shown.completedTaskKeys + safeKey
        return shown.copy(
            completedTaskKeys = completed,
            targetCount = maxOf(shown.targetCount, completed.size),
        )
    }

    fun recordReviewOutcome(
        state: StudyProgressState,
        outcome: StudyReviewProgressOutcome,
    ): StudyProgressState {
        val kanji = outcome.kanji.safeKanji()
        if (kanji.isEmpty()) {
            return state
        }
        return if (outcome.movedForward) {
            state.copy(
                movedForwardKanji = state.movedForwardKanji + kanji,
                missedKanji = state.missedKanji - kanji,
            )
        } else {
            state.copy(missedKanji = state.missedKanji + kanji)
        }
    }

    fun recordRepairOutcome(
        state: StudyProgressState,
        kanji: String?,
        passed: Boolean,
    ): StudyProgressState {
        val safeKanji = kanji.safeKanji()
        if (safeKanji.isEmpty()) {
            return state
        }
        return if (passed) {
            state.copy(
                movedForwardKanji = state.movedForwardKanji + safeKanji,
                missedKanji = state.missedKanji - safeKanji,
            )
        } else if (!state.movedForwardKanji.contains(safeKanji)) {
            state.copy(missedKanji = state.missedKanji + safeKanji)
        } else {
            state
        }
    }

    fun snapshot(
        state: StudyProgressState,
        activeTask: Boolean = false,
        continueAllKanjiSession: Boolean = false,
    ): StudyProgressSnapshot {
        val completed = state.completedCount
        var visibleTarget = state.targetCount
        if (activeTask && visibleTarget <= completed && continueAllKanjiSession) {
            visibleTarget = completed + 1
        }
        if (activeTask) {
            visibleTarget = maxOf(1, visibleTarget)
        }
        val visibleCompleted = completed.coerceIn(0, visibleTarget.coerceAtLeast(0))
        val fraction = if (visibleTarget <= 0) {
            0f
        } else {
            (completed / visibleTarget.toFloat()).coerceIn(0f, 1f)
        }
        return StudyProgressSnapshot(
            completedCount = completed,
            targetCount = state.targetCount,
            visibleCompletedCount = visibleCompleted,
            visibleTargetCount = visibleTarget,
            remainingCount = maxOf(0, state.targetCount - completed),
            fraction = fraction,
            atHardCap = atHardCap(state, continueAllKanjiSession),
            movedForwardCount = state.movedForwardKanji.size,
            missedCount = state.missedKanji.size,
        )
    }

    fun atHardCap(
        state: StudyProgressState,
        continueAllKanjiSession: Boolean,
    ): Boolean = !continueAllKanjiSession &&
        state.targetCount > 0 &&
        state.completedCount >= state.targetCount

    fun sessionTaskKey(
        taskType: String?,
        kanji: String?,
        token: String?,
    ): String {
        val safeTaskType = taskType.safeKey()
        val safeKanji = kanji.safeKanji()
        val safeToken = token.safeKey()
        if (safeTaskType.isEmpty() || safeKanji.isEmpty() || safeToken.isEmpty()) {
            return ""
        }
        return "session:$safeTaskType:$safeKanji:$safeToken"
    }

    fun similarRepairProgressKey(id: Long?): String =
        id?.takeIf { it > 0L }?.let { "repair:$it" }.orEmpty()

    fun similarRepairStudyTaskKey(
        id: Long?,
        activeToken: String?,
    ): String {
        val progressKey = similarRepairProgressKey(id)
        val safeToken = activeToken.safeKey()
        if (progressKey.isEmpty() || safeToken.isEmpty()) {
            return ""
        }
        return "$progressKey:$safeToken"
    }

    private fun String?.safeKey(): String = this?.trim().orEmpty()

    private fun String?.safeKanji(): String = this?.trim().orEmpty()
}

data class StudyProgressState(
    val targetCount: Int = 0,
    val seenTaskKeys: Set<String> = emptySet(),
    val completedTaskKeys: Set<String> = emptySet(),
    val movedForwardKanji: Set<String> = emptySet(),
    val missedKanji: Set<String> = emptySet(),
) {
    init {
        require(targetCount >= 0) { "targetCount must not be negative" }
    }

    val completedCount: Int
        get() = completedTaskKeys.size
}

data class StudyProgressPlan(
    val targetCount: Int,
    val remainingCount: Int,
)

data class StudyProgressUpdate(
    val state: StudyProgressState,
    val accepted: Boolean,
)

data class StudyReviewProgressOutcome(
    val kanji: String?,
    val rating: StudyRating,
    val writingLevelBefore: Int,
    val writingLevelAfter: Int,
    val realPassStreakBefore: Int,
    val realPassStreakAfter: Int,
) {
    val movedForward: Boolean
        get() = rating != StudyRating.AGAIN ||
            writingLevelAfter > writingLevelBefore ||
            realPassStreakAfter > realPassStreakBefore
}

data class StudyProgressSnapshot(
    val completedCount: Int,
    val targetCount: Int,
    val visibleCompletedCount: Int,
    val visibleTargetCount: Int,
    val remainingCount: Int,
    val fraction: Float,
    val atHardCap: Boolean,
    val movedForwardCount: Int,
    val missedCount: Int,
) {
    val isDone: Boolean
        get() = targetCount > 0 && completedCount >= targetCount
}
