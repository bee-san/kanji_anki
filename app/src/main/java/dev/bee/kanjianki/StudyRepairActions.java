package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;

final class StudyRepairActions {
    private StudyRepairActions() {
    }

    static ActiveRepair activateSimilarWritingRepair(
            RecordsImportModels.SimilarKanjiWritingRepair repair,
            long nowMillis,
            SimilarWritingRepairWriter writer
    ) {
        String token = StudyTokenFactory.studyItem("repair-" + repair.id, repair.activeToken);
        RecordsImportModels.SimilarKanjiWritingRepair activeRepair = repair.withToken(token, nowMillis);
        writer.saveSimilarWritingRepair(activeRepair);
        return new ActiveRepair(
                activeRepair,
                token,
                StudySessionTracker.similarRepairProgressKey(activeRepair),
                StudySessionTracker.similarRepairStudyTaskKey(activeRepair)
        );
    }

    interface SimilarWritingRepairWriter {
        void saveSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair repair);
    }

    record ActiveRepair(
            RecordsImportModels.SimilarKanjiWritingRepair repair,
            String token,
            String progressKey,
            String studyTaskKey
    ) {
    }
}
