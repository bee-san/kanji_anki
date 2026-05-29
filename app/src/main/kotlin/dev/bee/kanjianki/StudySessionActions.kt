package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels

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
        return scheduler.nextSessionForTaskKeys(
            items,
            rows,
            nowMillis,
            studyAheadMillis,
            allowedKanji,
            settings,
            ladder,
            tracker.pendingPlannedSessionTaskKeys(),
        )
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
