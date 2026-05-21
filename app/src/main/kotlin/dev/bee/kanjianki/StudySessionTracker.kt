package dev.bee.kanjianki

import android.os.SystemClock
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import dev.bee.kanjianki.data.LocalStore

internal class StudySessionTracker {
    private var activeTask: ActiveStudyTask? = null
    private val progressTracker = StudySessionProgressTracker()

    fun completedCount(): Int = progressTracker.completedCount()

    fun targetCount(): Int = progressTracker.targetCount()

    fun movedForwardCount(): Int = progressTracker.movedForwardCount()

    fun missedCount(): Int = progressTracker.missedCount()

    fun resetProgress() {
        progressTracker.resetProgress()
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
