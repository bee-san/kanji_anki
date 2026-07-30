package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min

/**
 * Progress across a study session. Mutated on the io thread (review outcomes,
 * task shown/completed) and read on the main thread (the #507 badge,
 * completedTaskBreakdown on the done screen). Every instance method holds
 * [lock] so reads never see torn state and iteration over the key sets cannot
 * race a concurrent write (ConcurrentModificationException).
 */
class StudySessionProgressTracker {
    enum class PendingTaskAdmission(val isTracked: Boolean) {
        ADDED(true),
        EXISTING(true),
        REJECTED(false),
    }

    private val lock = Any()
    private var completedCount = 0
    private var targetCount = 0
    private val completedTaskKeys = HashSet<String>()
    private val seenTaskKeys = HashSet<String>()
    private val movedForwardKanji = HashSet<String>()
    private val missedKanji = HashSet<String>()

    fun completedCount(): Int = synchronized(lock) { completedCount }

    fun targetCount(): Int = synchronized(lock) { targetCount }

    fun movedForwardCount(): Int = synchronized(lock) { movedForwardKanji.size }

    fun missedCount(): Int = synchronized(lock) { missedKanji.size }

    /** One lock acquisition for UI consumers that need a coherent progress frame. */
    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            targetCount = targetCount,
            completedCount = completedCount,
            movedForwardCount = movedForwardKanji.size,
            missedCount = missedKanji.size,
        )
    }

    fun copyForStaging(): StudySessionProgressTracker {
        val state = synchronized(lock) { stateLocked() }
        return StudySessionProgressTracker().also { it.restoreState(state) }
    }

    fun replaceStateFrom(staged: StudySessionProgressTracker) {
        restoreState(synchronized(staged.lock) { staged.stateLocked() })
    }

    fun hasSameStateAs(other: StudySessionProgressTracker): Boolean {
        val otherState = synchronized(other.lock) { other.stateLocked() }
        return synchronized(lock) { stateLocked() == otherState }
    }

    private fun stateLocked(): State = State(
        completedCount = completedCount,
        targetCount = targetCount,
        completedTaskKeys = HashSet(completedTaskKeys),
        seenTaskKeys = HashSet(seenTaskKeys),
        movedForwardKanji = HashSet(movedForwardKanji),
        missedKanji = HashSet(missedKanji),
    )

    private fun restoreState(state: State) = synchronized(lock) {
        completedCount = state.completedCount
        targetCount = state.targetCount
        completedTaskKeys.clear()
        completedTaskKeys.addAll(state.completedTaskKeys)
        seenTaskKeys.clear()
        seenTaskKeys.addAll(state.seenTaskKeys)
        movedForwardKanji.clear()
        movedForwardKanji.addAll(state.movedForwardKanji)
        missedKanji.clear()
        missedKanji.addAll(state.missedKanji)
    }

    fun completedTaskBreakdown(): CompletedTaskBreakdown = synchronized(lock) {
        var writingChecks = 0
        var similarKanjiChoices = 0
        var similarKanjiRepairs = 0
        var wordReadingReviews = 0
        var otherReviews = 0
        for (key in completedTaskKeys) {
            val taskType = taskTypeForCompletedKey(key)
            when {
                isSimilarRepairKey(key) -> similarKanjiRepairs++
                StudyTaskTypes.SIMILAR_KANJI == taskType -> similarKanjiChoices++
                StudyTaskTypes.WORD_READING == taskType -> wordReadingReviews++
                isWritingTaskType(taskType) -> writingChecks++
                else -> otherReviews++
            }
        }
        CompletedTaskBreakdown(
            writingChecks,
            similarKanjiChoices,
            similarKanjiRepairs,
            wordReadingReviews,
            otherReviews,
        )
    }

    fun resetProgress() = synchronized(lock) {
        completedCount = 0
        targetCount = 0
        completedTaskKeys.clear()
        seenTaskKeys.clear()
        movedForwardKanji.clear()
        missedKanji.clear()
    }

    fun initializeTarget(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) = synchronized(lock) {
        if (targetCount <= 0 && plan != null) {
            targetCount = max(0, if (plan.remaining > 0) plan.remaining else plan.target)
        }
    }

    fun setTargetCount(targetCount: Int) = synchronized(lock) {
        this.targetCount = max(0, targetCount)
    }

    fun includePendingTask(key: String?): Boolean = admitPendingTask(key) == PendingTaskAdmission.ADDED

    fun admitPendingTask(key: String?): PendingTaskAdmission = synchronized(lock) {
        when {
            isEmpty(key) -> PendingTaskAdmission.REJECTED
            seenTaskKeys.contains(key) || completedTaskKeys.contains(key) -> PendingTaskAdmission.EXISTING
            targetCount == Int.MAX_VALUE -> PendingTaskAdmission.REJECTED
            else -> {
                seenTaskKeys.add(key!!)
                targetCount++
                PendingTaskAdmission.ADDED
            }
        }
    }

    fun atHardCap(continueAllKanjiSession: Boolean): Boolean = synchronized(lock) {
        !continueAllKanjiSession && targetCount > 0 && completedCount >= targetCount
    }

    fun topBarProgress(activeTask: Boolean, continueAllKanjiSession: Boolean): TopBarProgress = synchronized(lock) {
        val completed = completedCount
        var target = targetCount
        if (activeTask && target <= completed && continueAllKanjiSession) {
            target = completed + 1
        }
        if (activeTask) {
            target = max(1, target)
        }
        val visibleCompleted = max(0, min(target, completed))
        val fraction = if (target <= 0) {
            0f
        } else {
            max(0f, min(1f, completed / target.toFloat()))
        }
        TopBarProgress(visibleCompleted, target, fraction)
    }

    fun registerTaskShown(key: String?) = synchronized(lock) {
        registerTaskShownLocked(key)
    }

    private fun registerTaskShownLocked(key: String?) {
        if (isEmpty(key)) {
            return
        }
        seenTaskKeys.add(key!!)
        if (targetCount <= 0) {
            targetCount = 1
        }
    }

    fun markTaskCompleted(key: String?) = synchronized(lock) {
        if (isEmpty(key)) {
            return@synchronized
        }
        registerTaskShownLocked(key)
        if (completedTaskKeys.add(key!!)) {
            completedCount++
            targetCount = max(targetCount, completedCount)
        }
    }

    fun recordReviewOutcome(
        kanji: String?,
        appliedRating: String?,
        before: RecordsStudyModels.StudyItem?,
        after: RecordsStudyModels.StudyItem?,
    ) = synchronized(lock) {
        val safeKanji = safeKanji(kanji)
        if (safeKanji.isEmpty()) {
            return@synchronized
        }
        val moved = BridgeScheduler.RATING_AGAIN != appliedRating || locallyImproved(before, after)
        if (moved) {
            movedForwardKanji.add(safeKanji)
            missedKanji.remove(safeKanji)
        } else {
            missedKanji.add(safeKanji)
        }
    }

    fun recordRepairOutcome(kanji: String?, passed: Boolean) = synchronized(lock) {
        val safeKanji = safeKanji(kanji)
        if (safeKanji.isEmpty()) {
            return@synchronized
        }
        if (passed) {
            movedForwardKanji.add(safeKanji)
            missedKanji.remove(safeKanji)
        } else if (!movedForwardKanji.contains(safeKanji)) {
            missedKanji.add(safeKanji)
        }
    }

    class TopBarProgress(
        @JvmField val completed: Int,
        @JvmField val target: Int,
        @JvmField val fraction: Float,
    )

    data class Snapshot(
        val targetCount: Int,
        val completedCount: Int,
        val movedForwardCount: Int,
        val missedCount: Int,
    )

    private data class State(
        val completedCount: Int,
        val targetCount: Int,
        val completedTaskKeys: Set<String>,
        val seenTaskKeys: Set<String>,
        val movedForwardKanji: Set<String>,
        val missedKanji: Set<String>,
    )

    class CompletedTaskBreakdown(
        @JvmField val writingChecks: Int,
        @JvmField val similarKanjiChoices: Int,
        @JvmField val similarKanjiRepairs: Int,
        @JvmField val wordReadingReviews: Int,
        @JvmField val otherReviews: Int,
    ) {
        @JvmField
        val total: Int = writingChecks + similarKanjiChoices + similarKanjiRepairs +
            wordReadingReviews + otherReviews
    }

    companion object {
        @JvmStatic
        fun sessionTaskKey(session: RecordsSchedulerModels.StudySession?): String {
            if (session == null) {
                return ""
            }
            return "session:" + session.taskType + ":" + (session.item?.kanji ?: "") + ":" + session.token
        }

        @JvmStatic
        fun similarRepairProgressKey(repair: RecordsImportModels.SimilarKanjiWritingRepair?): String {
            return if (repair == null) "" else "repair:" + repair.id
        }

        @JvmStatic
        fun similarRepairStudyTaskKey(repair: RecordsImportModels.SimilarKanjiWritingRepair?): String {
            if (repair == null) {
                return ""
            }
            return similarRepairProgressKey(repair) + ":" + repair.activeToken
        }

        private fun isEmpty(key: String?): Boolean {
            return key.isNullOrEmpty()
        }

        private fun isSimilarRepairKey(key: String): Boolean {
            return key.startsWith("repair:")
        }

        private fun taskTypeForCompletedKey(key: String): String {
            if (!key.startsWith("session:")) {
                return ""
            }
            val taskSuffix = key.substring("session:".length)
            val separator = taskSuffix.indexOf(':')
            return if (separator < 0) taskSuffix else taskSuffix.substring(0, separator)
        }

        private fun isWritingTaskType(taskType: String): Boolean {
            return taskType == StudyTaskTypes.WRITE_KANJI ||
                taskType == StudyTaskTypes.TARGETED_WRITING ||
                taskType == StudyTaskTypes.CONTEXT_WRITING ||
                taskType == StudyTaskTypes.GUIDED_WRITING ||
                taskType == StudyTaskTypes.BLIND_WRITING ||
                taskType == StudyTaskTypes.SAMPLED_HANDWRITING ||
                taskType == StudyTaskTypes.REPAIR_WRITING
        }

        private fun safeKanji(kanji: String?): String {
            return kanji?.trimJavaWhitespace() ?: ""
        }

        private fun locallyImproved(
            before: RecordsStudyModels.StudyItem?,
            after: RecordsStudyModels.StudyItem?,
        ): Boolean {
            if (before == null || after == null) {
                return false
            }
            return after.writingLevel > before.writingLevel ||
                after.realPassStreak > before.realPassStreak
        }

        private fun String.trimJavaWhitespace(): String {
            var start = 0
            var end = length
            while (start < end && this[start].code <= ' '.code) {
                start++
            }
            while (start < end && this[end - 1].code <= ' '.code) {
                end--
            }
            return substring(start, end)
        }
    }
}
