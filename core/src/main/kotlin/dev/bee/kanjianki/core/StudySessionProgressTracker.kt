package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min

class StudySessionProgressTracker {
    private var completedCount = 0
    private var targetCount = 0
    private val completedTaskKeys = HashSet<String>()
    private val seenTaskKeys = HashSet<String>()
    private val movedForwardKanji = HashSet<String>()
    private val missedKanji = HashSet<String>()

    fun completedCount(): Int = completedCount

    fun targetCount(): Int = targetCount

    fun movedForwardCount(): Int = movedForwardKanji.size

    fun missedCount(): Int = missedKanji.size

    fun resetProgress() {
        completedCount = 0
        targetCount = 0
        completedTaskKeys.clear()
        seenTaskKeys.clear()
        movedForwardKanji.clear()
        missedKanji.clear()
    }

    fun initializeTarget(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        if (targetCount <= 0 && plan != null) {
            targetCount = max(0, if (plan.remaining > 0) plan.remaining else plan.target)
        }
    }

    fun setTargetCount(targetCount: Int) {
        this.targetCount = max(0, targetCount)
    }

    fun includePendingTask(key: String?): Boolean {
        if (isEmpty(key) || seenTaskKeys.contains(key) || completedTaskKeys.contains(key)) {
            return false
        }
        seenTaskKeys.add(key!!)
        targetCount++
        return true
    }

    fun atHardCap(continueAllKanjiSession: Boolean): Boolean {
        return !continueAllKanjiSession && targetCount > 0 && completedCount >= targetCount
    }

    fun topBarProgress(activeTask: Boolean, continueAllKanjiSession: Boolean): TopBarProgress {
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
        return TopBarProgress(visibleCompleted, target, fraction)
    }

    fun registerTaskShown(key: String?) {
        if (isEmpty(key)) {
            return
        }
        seenTaskKeys.add(key!!)
        if (targetCount <= 0) {
            targetCount = 1
        }
    }

    fun markTaskCompleted(key: String?) {
        if (isEmpty(key)) {
            return
        }
        registerTaskShown(key)
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
    ) {
        val safeKanji = safeKanji(kanji)
        if (safeKanji.isEmpty()) {
            return
        }
        val moved = BridgeScheduler.RATING_AGAIN != appliedRating || locallyImproved(before, after)
        if (moved) {
            movedForwardKanji.add(safeKanji)
            missedKanji.remove(safeKanji)
        } else {
            missedKanji.add(safeKanji)
        }
    }

    fun recordRepairOutcome(kanji: String?, passed: Boolean) {
        val safeKanji = safeKanji(kanji)
        if (safeKanji.isEmpty()) {
            return
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

    companion object {
        @JvmStatic
        fun sessionTaskKey(session: RecordsSchedulerModels.StudySession?): String {
            if (session == null) {
                return ""
            }
            return "session:" + session.taskType + ":" + session.item.kanji + ":" + session.token
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
            return key == null || key.isEmpty()
        }

        private fun safeKanji(kanji: String?): String {
            return kanji?.trim() ?: ""
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
    }
}
