package dev.bee.kanjianki;

import java.util.List;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.StudySessionFocusPolicy;

final class MainActivityStudyScreen {
    private final MainActivityStudy study;

    MainActivityStudyScreen(MainActivityStudy study) {
        this.study = study;
    }

    void renderStudy() {
        study.base(MainActivityBase.NAV_STUDY);
        List<RecordsImportModels.DashboardRow> rows = study.store.activeDashboardRows();
        long now = System.currentTimeMillis();
        RecordsBase.StudyLadderSettings ladder = study.studyLadderSettings();
        study.activeStudyPlan = rows.isEmpty() ? null : study.studyPlanForMode(rows, study.store.studyItems(), now);
        if (renderPendingRepairOrDone(study.activeStudyPlan, now, ladder)) {
            return;
        }
        if (rows.isEmpty()) {
            renderEmptyStudyQueue();
            return;
        }
        List<RecordsStudyModels.StudyItem> beforeSeed = study.store.studyItems();
        RecordsSchedulerModels.AdaptiveLoadPlan plan = study.studyPlanForMode(rows, beforeSeed, now);
        List<RecordsStudyModels.StudyItem> seeded = study.studyQueue(rows, now, true, plan);
        RecordsSchedulerModels.AdaptiveLoadPlan seededPlan = study.studyPlanForMode(rows, seeded, now);
        study.activeStudyPlan = seededPlan;
        if (renderPendingRepairOrDone(seededPlan, now, ladder)) {
            return;
        }
        study.activeSession = new BridgeScheduler().nextSession(
                seeded,
                rows,
                now,
                study.studyAheadMillis(),
                StudySessionFocusPolicy.allowedKanji(seededPlan, study.continueAllKanjiSession),
                study.settings(),
                study.studyLadderSettings()
        );
        study.activeSimilarWritingRepair = null;
        if (study.activeSession == null) {
            renderNoStudySession(seededPlan);
            return;
        }
        StudySessionActions.activateStudySession(
                study.activeSession,
                now,
                study.store::saveStudyItem,
                study::registerStudyTaskShown,
                study::startActiveStudyTask
        );
        study.renderSession(study.activeSession);
    }

    boolean renderPendingRepairOrDone(
            RecordsSchedulerModels.AdaptiveLoadPlan plan,
            long now,
            RecordsBase.StudyLadderSettings ladder
    ) {
        study.initializeSessionProgressTarget(plan);
        if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            for (RecordsImportModels.SimilarKanjiWritingRepair repair : study.store.dueSimilarWritingRepairs(now)) {
                study.studySessionTracker.includePendingTask(study.similarRepairProgressKey(repair));
            }
            RecordsImportModels.SimilarKanjiWritingRepair repair = study.store.nextDueSimilarWritingRepair(now);
            if (repair != null) {
                study.renderSimilarWritingRepair(repair, plan, now);
                return true;
            }
        }
        if (study.studySessionTracker.atHardCap(study.continueAllKanjiSession)) {
            study.doneActions.renderStudyRunDone(plan);
            return true;
        }
        return false;
    }

    void renderEmptyStudyQueue() {
        study.doneActions.renderEmptyStudyQueue();
    }

    void renderNoStudySession(RecordsSchedulerModels.AdaptiveLoadPlan seededPlan) {
        study.doneActions.renderNoStudySession(seededPlan);
    }

    void renderFocusDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        study.doneActions.renderFocusDone(plan);
    }

    void renderStudyRunDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        study.doneActions.renderStudyRunDone(plan);
    }

    void startFocusedStudy() {
        study.clearStudyModeOverrides();
        study.resetStudyRunProgress();
        renderStudy();
    }

    void renderStudyForKanji(String kanji) {
        study.targetedLaunch.renderStudyForKanji(kanji);
    }

    void renderStudyForKanjiNotAvailable() {
        study.doneActions.renderStudyForKanjiNotAvailable();
    }
}
