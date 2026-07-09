package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import kotlin.math.max

internal object StudySessionActions {
    @JvmStatic
    fun activateStudySession(
        session: RecordsSchedulerModels.StudySession,
        nowMillis: Long,
        writer: StudyItemWriter,
        registrar: TaskRegistrar,
        starter: ActiveTaskStarter,
    ): String {
        val item = session.item ?: throw NullPointerException("session.item")
        writer.saveStudyItem(item)
        val taskKey = StudySessionTracker.sessionTaskKey(session)
        registrar.registerStudyTaskShown(taskKey)
        starter.startActiveStudyTask(taskKey, item.kanji, session.taskType, nowMillis)
        return taskKey
    }

    @JvmStatic
    fun plannedStudySession(
        scheduler: BridgeScheduler,
        tracker: StudySessionTracker,
        items: List<RecordsStudyModels.StudyItem>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings,
        ladder: RecordsBase.StudyLadderSettings,
    ): RecordsSchedulerModels.StudySession? {
        tracker.initializeSessionPlan(
            scheduler.randomizedSessionTaskKeys(
                items,
                rows,
                nowMillis,
                studyAheadMillis,
                allowedKanji,
                settings,
                ladder,
                null,
            )
        )
        // Normal pass at the user's configured study-ahead: same-session
        // learning repeats already due *now* come first (Anki gathers
        // learning/relearning ahead of new work), then the remaining planned
        // queue. This keeps ordinary queue building on the configured horizon,
        // so a fresh not-in-session card due in a few minutes is not pulled
        // forward.
        val dueNowRepeatKeys = tracker.dueCompletedLearningRepeatTaskKeys(items, nowMillis)
        val normalSession = scheduler.nextSessionForTaskKeys(
            items,
            rows,
            nowMillis,
            studyAheadMillis,
            allowedKanji,
            settings,
            ladder,
            dueLearningRepeatFirst(dueNowRepeatKeys, tracker.pendingPlannedSessionTaskKeys()),
        )
        if (normalSession != null) {
            return normalSession
        }
        // PS1 learn-ahead: nothing is due now, so the only remaining work is
        // this session's own learning-step repeats scheduled a few minutes out.
        // Keep serving the earliest of them (up to the learn-ahead horizon)
        // instead of ending the run and abandoning cards that would otherwise
        // resurface on the home screen minutes later. The widened horizon
        // applies ONLY to these already-completed repeat keys.
        val repeatHorizonMillis = max(studyAheadMillis, StudyLadderRules.LEARN_AHEAD_MILLIS)
        val aheadRepeatKeys = tracker.dueCompletedLearningRepeatTaskKeys(
            items,
            nowMillis + repeatHorizonMillis,
        )
        if (aheadRepeatKeys.isEmpty()) {
            return null
        }
        return scheduler.nextSessionForTaskKeys(
            items,
            rows,
            nowMillis,
            repeatHorizonMillis,
            allowedKanji,
            settings,
            ladder,
            aheadRepeatKeys,
        )
    }

    private fun dueLearningRepeatFirst(
        dueRepeatKeys: List<String>,
        pendingKeys: List<String>,
    ): List<String> {
        if (dueRepeatKeys.isEmpty()) {
            return pendingKeys
        }
        val out = ArrayList<String>()
        for (key in dueRepeatKeys) {
            if (key.isNotEmpty() && !out.contains(key)) {
                out.add(key)
            }
        }
        for (key in pendingKeys) {
            if (key.isNotEmpty() && !out.contains(key)) {
                out.add(key)
            }
        }
        return out
    }

    fun interface StudyItemWriter {
        fun saveStudyItem(item: RecordsStudyModels.StudyItem)
    }

    fun interface TaskRegistrar {
        fun registerStudyTaskShown(taskKey: String)
    }

    fun interface ActiveTaskStarter {
        fun startActiveStudyTask(taskKey: String, kanji: String, taskType: String, nowMillis: Long)
    }
}
