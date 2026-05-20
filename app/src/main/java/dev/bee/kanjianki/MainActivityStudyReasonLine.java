package dev.bee.kanjianki;

import android.widget.LinearLayout;

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

    void addStudyReasonLine(LinearLayout card, RecordsSchedulerModels.StudySession session) {
        String reason = studyReasonLine(session);
        if (!reason.isEmpty()) {
            card.addView(home.text(reason, 14, home.STUDY_MUTED, false));
        }
    }
}
