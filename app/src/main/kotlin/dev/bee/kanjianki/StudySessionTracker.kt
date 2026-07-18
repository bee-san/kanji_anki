package dev.bee.kanjianki

import android.os.SystemClock
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.ReviewTaskTiming

/**
 * Session-scoped study progress and the active-task timer. Mutated on the io
 * thread (task completion / review outcomes) and read + mutated on the main
 * thread (pause/resume on lifecycle, the badge, the done-screen breakdown).
 * All state access holds [lock] so concurrent pause vs completeActiveTask can
 * not corrupt the active-task timer and reads never see torn state. The
 * delegated [progressTracker] is independently synchronized; the extra lock
 * here guards this class's own fields (activeTask, planned-key sets).
 */
internal class StudySessionTracker(
    private val onChanged: () -> Unit = {},
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) {
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

    fun snapshot(): Snapshot = synchronized(lock) {
        snapshotLocked()
    }

    private fun snapshotLocked(): Snapshot {
        val progress = progressTracker.snapshot()
        return Snapshot(
            targetCount = progress.targetCount,
            completedCount = progress.completedCount,
            movedForwardCount = progress.movedForwardCount,
            missedCount = progress.missedCount,
            activeTask = activeTask != null,
        )
    }

    fun copyForStaging(): StudySessionTracker {
        val state = synchronized(lock) { stateLocked() }
        return StudySessionTracker(
            elapsedRealtime = elapsedRealtime,
        ).also { staged ->
            synchronized(staged.lock) { staged.restoreStateLocked(state) }
        }
    }

    fun replaceStateFrom(staged: StudySessionTracker) {
        val state = synchronized(staged.lock) { staged.stateLocked() }
        synchronized(lock) { restoreStateLocked(state) }
        onChanged()
    }

    private fun stateLocked(): State = State(
        progressTracker = progressTracker.copyForStaging(),
        plannedSessionTaskKeys = ArrayList(plannedSessionTaskKeys),
        completedPlannedSessionTaskKeys = HashSet(completedPlannedSessionTaskKeys),
        activeTask = activeTask?.copy(),
    )

    private fun restoreStateLocked(state: State) {
        progressTracker.replaceStateFrom(state.progressTracker)
        plannedSessionTaskKeys.clear()
        plannedSessionTaskKeys.addAll(state.plannedSessionTaskKeys)
        completedPlannedSessionTaskKeys.clear()
        completedPlannedSessionTaskKeys.addAll(state.completedPlannedSessionTaskKeys)
        activeTask = state.activeTask?.copy()
    }

    fun resetProgress() {
        synchronized(lock) {
            resetProgressLocked()
        }
        onChanged()
    }

    internal fun resetProgressWithoutNotification(): Snapshot = synchronized(lock) {
        resetProgressLocked()
        snapshotLocked()
    }

    private fun resetProgressLocked() {
        progressTracker.resetProgress()
        plannedSessionTaskKeys.clear()
        completedPlannedSessionTaskKeys.clear()
        activeTask = null
    }

    /**
     * Reconciles the live progress target with the work that can still appear
     * in this run. [pendingRepeatTaskKeys] contains only the next persisted
     * learning/relearning occurrence for each same-session card; later steps
     * are added on a later reconciliation if and when they are scheduled.
     */
    fun initializeSessionPlan(
        taskKeys: List<String>?,
        pendingRepeatTaskKeys: List<String>? = null,
    ) {
        synchronized(lock) {
            val normalized = normalizeSessionTaskKeys(taskKeys)
            val normalizedRepeats = normalizeSessionTaskKeys(pendingRepeatTaskKeys)
            val reconciled = reconcileSessionTaskKeys(normalized)
            plannedSessionTaskKeys.clear()
            plannedSessionTaskKeys.addAll(reconciled)
            progressTracker.setTargetCount(
                progressTracker.completedCount() +
                    pendingPlannedSessionTaskKeysLocked().size +
                    normalizedRepeats.size,
            )
        }
        onChanged()
    }

    private fun normalizeSessionTaskKeys(taskKeys: List<String>?): List<String> {
        val normalized = LinkedHashSet<String>()
        for (key in taskKeys.orEmpty()) {
            if (key.isNotEmpty()) {
                normalized.add(key)
            }
        }
        return normalized.toList()
    }

    private fun reconcileSessionTaskKeys(normalized: List<String>): List<String> {
        val currentTaskKeys = normalized.toHashSet()
        val reconciled = ArrayList<String>()
        retainCompletedAndCurrentTaskKeys(currentTaskKeys, reconciled)
        appendNewPendingTaskKeys(normalized, reconciled)
        return reconciled
    }

    private fun retainCompletedAndCurrentTaskKeys(
        currentTaskKeys: Set<String>,
        reconciled: MutableList<String>,
    ) {
        for (key in plannedSessionTaskKeys) {
            if (isCompletedPlannedSessionTaskKeyLocked(key) || currentTaskKeys.contains(key)) {
                reconciled.add(key)
            }
        }
    }

    private fun appendNewPendingTaskKeys(
        normalized: List<String>,
        reconciled: MutableList<String>,
    ) {
        for (key in normalized) {
            if (!isCompletedPlannedSessionTaskKeyLocked(key) && !reconciled.contains(key)) {
                reconciled.add(key)
            }
        }
    }

    fun nextPlannedSessionTaskKey(): String = synchronized(lock) {
        nextPlannedSessionTaskKeyLocked()
    }

    private fun nextPlannedSessionTaskKeyLocked(): String {
        for (key in plannedSessionTaskKeys) {
            if (!isCompletedPlannedSessionTaskKeyLocked(key)) {
                return key
            }
        }
        return ""
    }

    fun pendingPlannedSessionTaskKeys(): List<String> = synchronized(lock) {
        pendingPlannedSessionTaskKeysLocked()
    }

    private fun pendingPlannedSessionTaskKeysLocked(): List<String> {
        val out = ArrayList<String>()
        for (key in plannedSessionTaskKeys) {
            if (!isCompletedPlannedSessionTaskKeyLocked(key)) {
                out.add(key)
            }
        }
        return out
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
            val taskType = if (AdaptiveStudyItemPolicy.isAdaptive(item)) {
                AdaptiveStudyItemPolicy.taskTypeFor(
                    item,
                    RecordsBase.StudyLadderSettings.defaults(),
                )
            } else {
                item.rung.wireName()
            }
            val key = plannedSessionTaskKey(taskType, item.kanji)
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

    private fun isLearningRepeatPhase(phase: RecordsBase.SchedulerPhase): Boolean {
        return phase == RecordsBase.SchedulerPhase.NEW_LEARNING ||
            phase == RecordsBase.SchedulerPhase.RELEARNING
    }

    private fun isCompletedPlannedSessionTaskKeyLocked(key: String): Boolean {
        return isCompletedPlannedSessionTask(key, key.substringAfter(':', ""))
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
        onChanged()
    }

    fun setTargetCount(targetCount: Int) {
        synchronized(lock) {
            val minimumTarget = maxOf(progressTracker.completedCount(), progressTracker.targetCount())
            require(targetCount >= minimumTarget) {
                "A normal target update cannot lower the accepted target"
            }
            progressTracker.setTargetCount(targetCount)
        }
        onChanged()
    }

    internal fun reconcileTargetCountWithoutNotification(targetCount: Int): Snapshot = synchronized(lock) {
        require(targetCount >= progressTracker.completedCount()) {
            "A reconciled target cannot be below completed progress"
        }
        progressTracker.setTargetCount(targetCount)
        snapshotLocked()
    }

    fun includePendingTask(key: String?): Boolean {
        val included = progressTracker.includePendingTask(key)
        if (included) onChanged()
        return included
    }

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
        onChanged()
    }

    fun markTaskCompleted(key: String?) {
        synchronized(lock) {
            progressTracker.markTaskCompleted(key)
        }
        onChanged()
    }

    fun hasActiveTask(): Boolean = synchronized(lock) { activeTask != null }

    fun startActiveTask(
        key: String?,
        kanji: String?,
        taskType: String?,
        startedAt: Long,
        resumeImmediately: Boolean,
    ) {
        val changed = synchronized(lock) {
            if (key.isNullOrEmpty() || activeTask?.taskKey == key) {
                false
            } else {
                activeTask = ActiveStudyTask(key, kanji, taskType, startedAt)
                if (resumeImmediately) {
                    activeTask?.resume(elapsedRealtime())
                }
                true
            }
        }
        if (changed) onChanged()
    }

    fun completeActiveTask(
        store: LocalStore,
        key: String?,
        outcome: String?,
        answeredAt: Long,
        countProgress: Boolean,
    ) {
        val prepared = prepareActiveTask(key, outcome, answeredAt, countProgress) ?: return
        val timing = prepared.timing
        store.recordStudyTaskAnswered(
            timing.taskKey,
            timing.kanji,
            timing.taskType,
            timing.startedAtMillis,
            timing.answeredAtMillis,
            timing.activeElapsedMillis,
            timing.outcome,
        )
        commitPreparedTask(prepared)
    }

    /**
     * Freezes the active timer without mutating progress. Review persistence
     * can include [PreparedTaskCompletion.timing] in its transaction, then call
     * [commitPreparedTask] only after an APPLIED commit (or [rollbackPreparedTask]
     * for STALE/failure). No SQLite work is performed while [lock] is held.
     */
    fun prepareActiveTask(
        key: String?,
        outcome: String?,
        answeredAt: Long,
        countProgress: Boolean,
    ): PreparedTaskCompletion? = synchronized(lock) {
        val task = activeTask
        if (task == null || key == null || key != task.taskKey) {
            return@synchronized null
        }
        task.pause(elapsedRealtime())
        PreparedTaskCompletion(
            task,
            ReviewTaskTiming(
                taskKey = task.taskKey,
                kanji = task.kanji,
                taskType = task.taskType,
                startedAtMillis = task.startedAtMillis,
                answeredAtMillis = answeredAt,
                activeElapsedMillis = task.activeElapsedMillis,
                outcome = outcome ?: "",
            ),
            countProgress,
        )
    }

    fun commitPreparedTask(prepared: PreparedTaskCompletion?) {
        val changed = synchronized(lock) {
            if (prepared == null || activeTask !== prepared.task) {
                false
            } else {
                if (prepared.countProgress) {
                    progressTracker.markTaskCompleted(prepared.task.taskKey)
                    val plannedKey = plannedSessionTaskKey(prepared.task.taskType, prepared.task.kanji)
                    if (plannedKey.isNotEmpty()) {
                        completedPlannedSessionTaskKeys.add(plannedKey)
                    }
                }
                activeTask = null
                true
            }
        }
        if (changed) onChanged()
    }

    fun rollbackPreparedTask(prepared: PreparedTaskCompletion?) = synchronized(lock) {
        if (prepared == null || activeTask !== prepared.task) {
            return@synchronized
        }
        // The same card remains visible and retryable after a stale/failed
        // commit, so continue measuring from the rollback point.
        prepared.task.resume(elapsedRealtime())
    }

    fun recordReviewOutcome(
        kanji: String?,
        appliedRating: String?,
        before: RecordsStudyModels.StudyItem?,
        after: RecordsStudyModels.StudyItem?,
    ) {
        progressTracker.recordReviewOutcome(kanji, appliedRating, before, after)
        onChanged()
    }

    fun recordRepairOutcome(kanji: String?, passed: Boolean) {
        progressTracker.recordRepairOutcome(kanji, passed)
        onChanged()
    }

    fun pauseActiveTask() = synchronized(lock) {
        activeTask?.pause(elapsedRealtime())
        Unit
    }

    fun resumeActiveTask() = synchronized(lock) {
        activeTask?.resume(elapsedRealtime())
        Unit
    }

    fun abandonActiveTask() {
        val changed = synchronized(lock) {
            val hadActiveTask = activeTask != null
            activeTask = null
            hadActiveTask
        }
        if (changed) onChanged()
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

        fun copy(): ActiveStudyTask = ActiveStudyTask(taskKey, kanji, taskType, startedAtMillis).also {
            it.activeElapsedMillis = activeElapsedMillis
            it.visibleSinceElapsedMillis = visibleSinceElapsedMillis
        }
    }

    private data class State(
        val progressTracker: StudySessionProgressTracker,
        val plannedSessionTaskKeys: List<String>,
        val completedPlannedSessionTaskKeys: Set<String>,
        val activeTask: ActiveStudyTask?,
    )

    data class Snapshot(
        val targetCount: Int,
        val completedCount: Int,
        val movedForwardCount: Int,
        val missedCount: Int,
        val activeTask: Boolean,
    )

    class PreparedTaskCompletion internal constructor(
        internal val task: ActiveStudyTask,
        @JvmField val timing: ReviewTaskTiming,
        internal val countProgress: Boolean,
    )

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
