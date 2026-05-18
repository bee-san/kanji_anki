package dev.bee.kanjianki;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsStudyModels;

import java.util.List;

final class StudyMoreNewCardActions {
    private StudyMoreNewCardActions() {
    }

    static AdmissionResult applyAdmission(
            BridgeScheduler.ExtraNewCardsResult result,
            StudyItemWriter writer,
            List<String> selectedKanji,
            ProgressResetter progressResetter,
            TargetCounter targetCounter
    ) {
        if (!result.admittedAny()) {
            return new AdmissionResult(false, result.admittedCount);
        }
        List<RecordsStudyModels.StudyItem> seeded = writer.annotateSimilarKanjiAvailability(result.items);
        writer.replaceStudyItems(seeded);
        selectedKanji.clear();
        selectedKanji.addAll(result.admittedKanji);
        progressResetter.resetStudyRunProgress();
        targetCounter.setTargetCount(result.admittedCount);
        return new AdmissionResult(true, result.admittedCount);
    }

    interface StudyItemWriter {
        List<RecordsStudyModels.StudyItem> annotateSimilarKanjiAvailability(List<RecordsStudyModels.StudyItem> items);

        void replaceStudyItems(List<RecordsStudyModels.StudyItem> items);
    }

    interface ProgressResetter {
        void resetStudyRunProgress();
    }

    interface TargetCounter {
        void setTargetCount(int targetCount);
    }

    record AdmissionResult(boolean admittedAny, int admittedCount) {
    }
}
