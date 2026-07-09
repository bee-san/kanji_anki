package dev.bee.kanjianki

import android.os.SystemClock
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import dev.bee.kanjianki.data.LocalStore

/**
 * Session-scoped study progress and the active-task timer. Mutated on the io
 * thread (task completion / review outcomes) and read + mutated on the main
 * thread (pause/resume on lifecycle, the badge, the done-screen breakdown).
 * All state access holds [lock] so concurrent pause vs completeActiveTask can
 * not corrupt the active-task timer and reads never see torn state. The
 * delegated [progressTracker] is independently synchronized; the extra lock
 * here guards this class's own fields (activeTask, planned-key sets).
 */
internal class StudySessionTracker {
    private val lock = Any()
    private var activeTask: ActiveStudyTask? = null
    private val progressTracker = StudySessionProgressTracker()
    private val plannedSessionTaskKeys = ArrayList<String>()
    private val completedPlannedSessionTaskKeys = HashSet<String>()

    fun completedCount(): Int = progressTracker.completedCount()

    fun completedTaskBreakdown(): StudySessionProgressTracker.CompletedTaskBreakdown =
        progressTracker.completedTaskBreakdown()

    fun targetCount(): Int = progressTracker.targetCount()

    fun movedForwardCount(): Int = progressTracker.movedForwardCount()

    fun missedCount(): Int = progressTracker.missedCount()

    fun resetProgress() = synchronized(lock) {
        progressTracker.resetProgress()
        plannedSessionTaskKeys.clear()
        completedPlannedSessionTaskKeys.clear()
    }

    fun initializeSessionPlan(taskKeys: List<String>?) = synchronized(lock) {
        if (taskKeys.isNullOrEmpty() || hasPendingPlannedSessionTaskLocked()) {
            return@synchronized
        }
        plannedSessionTaskKeys.clear()
        completedPlannedSessionTaskKeys.clear()
        for (key in taskKeys) {
            if (key.isNotEmpty() && !plannedSessionTaskKeys.contains(key)) {
                plannedSessionTaskKeys.add(key)
            }
        }
    }

    fun nextPlannedSessionTaskKey(): String = synchronized(lock) {
        nextPlannedSessionTaskKeyLocked()
    }

    private fun nextPlannedSessionTaskKeyLocked(): String {
        for (key in plannedSessionTaskKeys) {
            if (!completedPlannedSessionTaskKeys.contains(key)) {
                return key
            }
        }
        return ""
    }

    fun pendingPlannedSessionTaskKeys(): List<String> = synchronized(lock) {
        val out = ArrayList<String>()
        for (key in plannedSessionTaskKeys) {
            if (!completedPlannedSessionTaskKeys.contains(key)) {
                out.add(key)
            }
        }
        out
    }

    /**
     * Task keys for this session's own already-completed learning-step repeats
     * whose next step is due at or before [horizonMillis]. Passing a widened
     * horizon (`now + learnAhead`) surfaces repeats due a few minutes out so
     * the run can keep serving them (PS1 learn-ahead) instead of ending; the
     * default call site passes `now` for the "due right now" set. Results are
     * ordered earliest-due first so the run serves the soonest repeat.
     */
    fun dueCompletedLearningRepeatTaskKeys(
        items: List<RecordsStudyModels.StudyItem>?,
        horizonMillis: Long,
    ): List<String> = synchronized(lock) {
        if (items.isNullOrEmpty() || completedPlannedSessionTaskKeys.isEmpty()) {
            return@synchronized emptyList()
        }
        val dueByKey = LinkedHashMap<String, Long>()
        for (item in items) {
            if (item.dueAtMillis > horizonMillis || !isLearningRepeatPhase(item.phase)) {
                continue
            }
            val key = plannedSessionTaskKey(item.rung.wireName(), item.kanji)
            if (isCompletedPlannedSessionTask(key, item.kanji) && !dueByKey.containsKey(key)) {
                dueByKey[key] = item.dueAtMillis
            }
        }
        dueByKey.entries.sortedBy { it.value }.map { it.key }
    }

    fun markPlannedSessionTaskCompleted(taskType: String?, kanji: String?) = synchronized(lock) {
        val key = plannedSessionTaskKey(taskType, kanji)
        if (key.isNotEmpty()) {
            completedPlannedSessionTaskKeys.add(key)
        }
    }

    private fun hasPendingPlannedSessionTaskLocked(): Boolean {
        return nextPlannedSessionTaskKeyLocked().isNotEmpty()
    }

    private fun isLearningRepeatPhase(phase: RecordsBase.SchedulerPhase): Boolean {
        return phase == RecordsBase.SchedulerPhase.NEW_LEARNING ||
            phase == RecordsBase.SchedulerPhase.RELEARNING
    }

    private fun isCompletedPlannedSessionTask(key: String, kanji: String): Boolean {
        if (completedPlannedSessionTaskKeys.contains(key)) {
            return true
        }
        return completedPlannedSessionTaskKeys.any { completedKey ->
            completedKey.substringAfter(':', "") == kanji
        }
    }

    private fun plannedSessionTaskKey(taskType: String?, kanji: String?): String {
        val safeTaskType = taskType ?: ""
        val safeKanji = kanji ?: ""
        if (safeTaskType.isEmpty() || safeKanji.isEmpty()) {
            return ""
        }
        return "$safeTaskType:$safeKanji"
    }

    fun initializeTarget(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        progressTracker.initializeTarget(plan)
    }

    fun setTargetCount(targetCount: Int) {
        progressTracker.setTargetCount(targetCount)
    }

    fun includePendingTask(key: String?): Boolean = progressTracker.includePendingTask(key)

    fun atHardCap(continueAllKanjiSession: Boolean): Boolean {
        return progressTracker.atHardCap(continueAllKanjiSession)
    }

    fun topBarProgress(
        activeTask: Boolean,
        continueAllKanjiSession: Boolean,
    ): StudySessionProgressTracker.TopBarProgress {
        return progressTracker.topBarProgress(activeTask, continueAllKanjiSession)
    }

    fun registerTaskShown(key: String?) {
        progressTracker.registerTaskShown(key)
    }

    fun markTaskCompleted(key: String?) {
        progressTracker.markTaskCompleted(key)
    }

    fun hasActiveTask(): Boolean = synchronized(lock) { activeTask != null }

    fun startActiveTask(
        key: String?,
        kanji: String?,
        taskType: String?,
        startedAt: Long,
        resumeImmediately: Boolean,
    ) = synchronized(lock) {
        if (key.isNullOrEmpty()) {
            return@synchronized
        }
        if (activeTask?.taskKey == key) {
            return@synchronized
        }
        activeTask = ActiveStudyTask(key, kanji, taskType, startedAt)
        if (resumeImmediately) {
            activeTask?.resume(SystemClock.elapsedRealtime())
        }
    }

    fun completeActiveTask(
        store: LocalStore,
        key: String?,
        outcome: String?,
        answeredAt: Long,
        countProgress: Boolean,
    ) = synchronized(lock) {
        val task = activeTask
        if (task == null || key == null || key != task.taskKey) {
            return@synchronized
        }
        task.pause(SystemClock.elapsedRealtime())
        store.recordStudyTaskAnswered(
            task.taskKey,
            task.kanji,
            task.taskType,
            task.startedAtMillis,
            answeredAt,
            task.activeElapsedMillis,
            outcome,
        )
        if (countProgress) {
            markTaskCompleted(key)
            markPlannedSessionTaskCompleted(task.taskType, task.kanji)
        }
        activeTask = null
    }

    fun recordReviewOutcome(
        kanji: String?,
        appliedRating: String?,
        before: RecordsStudyModels.StudyItem?,
        after: RecordsStudyModels.StudyItem?,
    ) {
        progressTracker.recordReviewOutcome(kanji, appliedRating, before, after)
    }

    fun recordRepairOutcome(kanji: String?, passed: Boolean) {
        progressTracker.recordRepairOutcome(kanji, passed)
    }

    fun pauseActiveTask() = synchronized(lock) {
        activeTask?.pause(SystemClock.elapsedRealtime())
        Unit
    }

    fun resumeActiveTask() = synchronized(lock) {
        activeTask?.resume(SystemClock.elapsedRealtime())
        Unit
    }

    fun abandonActiveTask() = synchronized(lock) {
        activeTask = null
    }

    class ActiveStudyTask(
        taskKey: String?,
        kanji: String?,
        taskType: String?,
        startedAtMillis: Long,
    ) {
        @JvmField
        val taskKey: String = taskKey ?: ""

        @JvmField
        val kanji: String = kanji ?: ""

        @JvmField
        val taskType: String = taskType ?: ""

        @JvmField
        val startedAtMillis: Long = startedAtMillis.coerceAtLeast(0L)

        @JvmField
        var activeElapsedMillis: Long = 0L

        @JvmField
        var visibleSinceElapsedMillis: Long = 0L

        fun pause(nowElapsedMillis: Long) {
            if (visibleSinceElapsedMillis <= 0L) {
                return
            }
            activeElapsedMillis = StudyTaskTimingPolicy.elapsedAfterPause(
                activeElapsedMillis,
                visibleSinceElapsedMillis,
                nowElapsedMillis,
            )
            visibleSinceElapsedMillis = 0L
        }

        fun resume(nowElapsedMillis: Long) {
            visibleSinceElapsedMillis = StudyTaskTimingPolicy.visibleSinceAfterResume(
                visibleSinceElapsedMillis,
                nowElapsedMillis,
            )
        }
    }

    companion object {
        @JvmStatic
        fun sessionTaskKey(session: RecordsSchedulerModels.StudySession?): String {
            return StudySessionProgressTracker.sessionTaskKey(session)
        }

        @JvmStatic
        fun similarRepairProgressKey(repair: RecordsImportModels.SimilarKanjiWritingRepair?): String {
            return StudySessionProgressTracker.similarRepairProgressKey(repair)
        }

        @JvmStatic
        fun similarRepairStudyTaskKey(repair: RecordsImportModels.SimilarKanjiWritingRepair?): String {
            return StudySessionProgressTracker.similarRepairStudyTaskKey(repair)
        }
    }
}
