package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels

internal object StudySessionActions {
    @JvmStatic
    fun activateStudySession(
        session: RecordsSchedulerModels.StudySession,
        nowMillis: Long,
        writer: StudyItemWriter,
        registrar: TaskRegistrar,
        starter: ActiveTaskStarter,
    ): String {
        writer.saveStudyItem(session.item)
        val taskKey = StudySessionTracker.sessionTaskKey(session)
        registrar.registerStudyTaskShown(taskKey)
        starter.startActiveStudyTask(taskKey, session.item.kanji, session.taskType, nowMillis)
        return taskKey
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
