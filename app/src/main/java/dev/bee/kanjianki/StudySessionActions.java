package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;

final class StudySessionActions {
    private StudySessionActions() {
    }

    static String activateStudySession(
            RecordsSchedulerModels.StudySession session,
            long nowMillis,
            StudyItemWriter writer,
            TaskRegistrar registrar,
            ActiveTaskStarter starter
    ) {
        writer.saveStudyItem(session.item);
        String taskKey = StudySessionTracker.sessionTaskKey(session);
        registrar.registerStudyTaskShown(taskKey);
        starter.startActiveStudyTask(taskKey, session.item.kanji, session.taskType, nowMillis);
        return taskKey;
    }

    interface StudyItemWriter {
        void saveStudyItem(RecordsStudyModels.StudyItem item);
    }

    interface TaskRegistrar {
        void registerStudyTaskShown(String taskKey);
    }

    interface ActiveTaskStarter {
        void startActiveStudyTask(String taskKey, String kanji, String taskType, long nowMillis);
    }
}
