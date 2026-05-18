package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import java.util.List;

final class HomeStudyQueueActions {
    private HomeStudyQueueActions() {
    }

    static List<RecordsStudyModels.StudyItem> studyQueue(StudyQueueRequest request) {
        List<RecordsStudyModels.StudyItem> currentItems = request.reader().studyItems();
        if (!request.persist()) {
            return currentItems;
        }
        RecordsSchedulerModels.AdaptiveLoadPlan effectivePlan = request.plan() == null
                ? request.planProvider().adaptivePlan(request.rows(), currentItems, request.nowMillis())
                : request.plan();
        List<RecordsStudyModels.StudyItem> seeded = request.seeder().seedQueue(
                request.rows(),
                currentItems,
                request.settingsProvider().settings(),
                request.nowMillis(),
                request.dayStartProvider().startOfDay(request.nowMillis()),
                effectivePlan,
                request.ladderProvider().studyLadderSettings()
        );
        List<RecordsStudyModels.StudyItem> annotated = request.writer().annotateSimilarKanjiAvailability(seeded);
        request.writer().replaceStudyItems(annotated);
        return annotated;
    }

    record StudyQueueRequest(
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            boolean persist,
            RecordsSchedulerModels.AdaptiveLoadPlan plan,
            StudyItemsReader reader,
            StudySettingsProvider settingsProvider,
            DayStartProvider dayStartProvider,
            StudyLadderProvider ladderProvider,
            AdaptivePlanProvider planProvider,
            StudyQueueSeeder seeder,
            StudyItemsWriter writer
    ) {
    }

    interface StudyItemsReader {
        List<RecordsStudyModels.StudyItem> studyItems();
    }

    interface StudySettingsProvider {
        RecordsSyncModels.Settings settings();
    }

    interface DayStartProvider {
        long startOfDay(long nowMillis);
    }

    interface StudyLadderProvider {
        RecordsBase.StudyLadderSettings studyLadderSettings();
    }

    interface AdaptivePlanProvider {
        RecordsSchedulerModels.AdaptiveLoadPlan adaptivePlan(
                List<RecordsImportModels.DashboardRow> rows,
                List<RecordsStudyModels.StudyItem> currentItems,
                long nowMillis
        );
    }

    interface StudyQueueSeeder {
        List<RecordsStudyModels.StudyItem> seedQueue(
                List<RecordsImportModels.DashboardRow> rows,
                List<RecordsStudyModels.StudyItem> currentItems,
                RecordsSyncModels.Settings settings,
                long nowMillis,
                long startOfDayMillis,
                RecordsSchedulerModels.AdaptiveLoadPlan plan,
                RecordsBase.StudyLadderSettings ladder
        );
    }

    interface StudyItemsWriter {
        List<RecordsStudyModels.StudyItem> annotateSimilarKanjiAvailability(List<RecordsStudyModels.StudyItem> items);

        void replaceStudyItems(List<RecordsStudyModels.StudyItem> items);
    }
}
