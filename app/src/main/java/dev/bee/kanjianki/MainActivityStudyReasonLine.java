package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudyTextCopy;

final class MainActivityStudyReasonLine {
    private final MainActivityStudy home;

    MainActivityStudyReasonLine(MainActivityStudy home) {
        this.home = home;
    }

    String studyReasonLine(RecordsSchedulerModels.StudySession session) {
        return StudyTextCopy.studyReasonLine(
                home.activeSimilarWritingRepair != null,
                session,
                home.settings().matureSupportThreshold,
                System.currentTimeMillis()
        );
    }

}
