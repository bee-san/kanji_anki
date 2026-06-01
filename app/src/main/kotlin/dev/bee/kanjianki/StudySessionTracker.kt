package dev.bee.kanjianki

import android.os.SystemClock
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import dev.bee.kanjianki.data.LocalStore

internal class StudySessionTracker {
    private var activeTask: ActiveStudyTask? = null
    private val progressTracker = StudySessionProgressTracker()
    private val plannedSessionTaskKeys = ArrayList<String>()
    private val completedPlannedSessionTaskKeys = HashSet<String>()

    fun completedCount(): Int = progressTracker.completedCount()

    fun targetCount(): Int = progressTracker.targetCount()

    fun movedForwardCount(): Int = progressTracker.movedForwardCount()

    fun missedCount(): Int = progressTracker.missedCount()

    fun resetProgress() {
        progressTracker.resetProgress()
        plannedSessionTaskKeys.clear()
        completedPlannedSessionTaskKeys.clear()
    }

    fun initializeSessionPlan(taskKeys: List<String>?) {
        if (taskKeys.isNullOrEmpty() || hasPendingPlannedSessionTask()) {
            return
        }
        plannedSessionTaskKeys.clear()
        completedPlannedSessionTaskKeys.clear()
        for (key in taskKeys) {
            if (key.isNotEmpty() && !plannedSessionTaskKeys.contains(key)) {
                plannedSessionTaskKeys.add(key)
            }
        }
    }

    fun nextPlannedSessionTaskKey(): String {
        for (key in plannedSessionTaskKeys) {
            if (!completedPlannedSessionTaskKeys.contains(key)) {
                return key
            }
        }
        return ""
    }

    fun pendingPlannedSessionTaskKeys(): List<String> {
        val out = ArrayList<String>()
        for (key in plannedSessionTaskKeys) {
            if (!completedPlannedSessionTaskKeys.contains(key)) {
                out.add(key)
            }
        }
        return out
    }

    fun dueCompletedLearningRepeatTaskKeys(
        items: List<RecordsStudyModels.StudyItem>?,
        nowMillis: Long,
    ): List<String> {
        if (items.isNullOrEmpty() || completedPlannedSessionTaskKeys.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<String>()
        for (item in items) {
            if (item.dueAtMillis > nowMillis || !isLearningRepeatPhase(item.phase)) {
                continue
            }
            val key = plannedSessionTaskKey(item.rung.wireName(), item.kanji)
            if (isCompletedPlannedSessionTask(key, item.kanji) && !out.contains(key)) {
                out.add(key)
            }
        }
        return out
    }

    fun markPlannedSessionTaskCompleted(taskType: String?, kanji: String?) {
        val key = plannedSessionTaskKey(taskType, kanji)
        if (key.isNotEmpty()) {
            completedPlannedSessionTaskKeys.add(key)
        }
    }

    private fun hasPendingPlannedSessionTask(): Boolean {
        return nextPlannedSessionTaskKey().isNotEmpty()
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

    fun hasActiveTask(): Boolean = activeTask != null

    fun startActiveTask(
        key: String?,
        kanji: String?,
        taskType: String?,
        startedAt: Long,
        resumeImmediately: Boolean,
    ) {
        if (key.isNullOrEmpty()) {
            return
        }
        if (activeTask?.taskKey == key) {
            return
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
    ) {
        val task = activeTask
        if (task == null || key == null || key != task.taskKey) {
            return
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

    fun pauseActiveTask() {
        activeTask?.pause(SystemClock.elapsedRealtime())
    }

    fun resumeActiveTask() {
        activeTask?.resume(SystemClock.elapsedRealtime())
    }

    fun abandonActiveTask() {
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
