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

    static RepairCompletion completeSimilarWritingRepair(
            RecordsImportModels.SimilarKanjiWritingRepair repair,
            String rating,
            long nowMillis,
            SimilarWritingRepairFinisher finisher,
            RepairOutcomeRecorder recorder,
            RepairTaskMarker marker
    ) {
        boolean passed = !MainActivityBase.RATING_AGAIN.equals(rating);
        boolean saved = finisher.finishSimilarWritingRepair(repair.id, repair.activeToken, passed, nowMillis);
        if (saved) {
            recorder.recordRepairOutcome(repair.repairKanji, passed);
        }
        if (saved && passed) {
            marker.markStudyTaskCompleted(StudySessionTracker.similarRepairProgressKey(repair));
        }
        return new RepairCompletion(saved, passed);
    }

    interface SimilarWritingRepairWriter {
        void saveSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair repair);
    }

    interface SimilarWritingRepairFinisher {
        boolean finishSimilarWritingRepair(long repairId, String activeToken, boolean passed, long nowMillis);
    }

    interface RepairOutcomeRecorder {
        void recordRepairOutcome(String kanji, boolean passed);
    }

    interface RepairTaskMarker {
        void markStudyTaskCompleted(String taskKey);
    }

    record ActiveRepair(
            RecordsImportModels.SimilarKanjiWritingRepair repair,
            String token,
            String progressKey,
            String studyTaskKey
    ) {
    }

    record RepairCompletion(boolean saved, boolean passed) {
    }
}
