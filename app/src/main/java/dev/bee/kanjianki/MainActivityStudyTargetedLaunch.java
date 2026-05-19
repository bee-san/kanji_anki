package dev.bee.kanjianki;

import java.util.List;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;

final class MainActivityStudyTargetedLaunch {
    private final MainActivityStudy home;

    MainActivityStudyTargetedLaunch(MainActivityStudy home) {
        this.home = home;
    }

    void renderStudyForKanji(String kanji) {
        home.clearStudyModeOverrides();
        home.resetStudyRunProgress();
        home.base(MainActivityBase.NAV_STUDY);
        home.activeSimilarWritingRepair = null;
        List<RecordsImportModels.DashboardRow> rows = home.store.activeDashboardRows();
        long now = System.currentTimeMillis();
        home.activeStudyPlan = rows.isEmpty() ? null : home.adaptivePlan(rows, home.store.studyItems(), now);
        RecordsImportModels.DashboardRow row = home.findRow(rows, kanji);
        if (row == null) {
            home.renderStudyForKanjiNotAvailable();
            return;
        }
        List<RecordsStudyModels.StudyItem> seeded = home.studyQueue(rows, now, true, home.activeStudyPlan);
        home.activeStudyPlan = home.adaptivePlan(rows, seeded, now);
        home.activeSession = new BridgeScheduler().targetedSession(
                seeded,
                row,
                now,
                home.studyLadderSettings()
        );
        StudySessionActions.activateStudySession(
                home.activeSession,
                now,
                home.store::saveStudyItem,
                home::registerStudyTaskShown,
                home::startActiveStudyTask
        );
        home.renderSession(home.activeSession);
    }
}
