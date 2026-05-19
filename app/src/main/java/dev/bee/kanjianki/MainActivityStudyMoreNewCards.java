package dev.bee.kanjianki;

import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy;
import dev.bee.kanjianki.core.RecordsImportModels;

final class MainActivityStudyMoreNewCards {
    private final MainActivityStudy study;

    MainActivityStudyMoreNewCards(MainActivityStudy study) {
        this.study = study;
    }

    int availableStudyMoreNewCards() {
        return study.doneActions.availableStudyMoreNewCards();
    }

    void showStudyMoreNewCardsDialog(int availableAtOpen) {
        study.doneActions.showStudyMoreNewCardsDialog(availableAtOpen);
    }

    boolean applyStudyMoreNewCardsRequest(EditText countInput) {
        return study.doneActions.applyStudyMoreNewCardsRequest(countInput);
    }

    int requestedStudyMoreNewCards(EditText countInput) {
        StudyMoreNewCardsPolicy.RequestDecision decision = StudyMoreNewCardsPolicy.requestedCount(countInput.getText().toString());
        if (!decision.accepted()) {
            Toast.makeText(study, decision.message(), Toast.LENGTH_SHORT).show();
            return -1;
        }
        return decision.requestedCount();
    }

    boolean startStudyMoreNewCards(int requestedCount) {
        List<RecordsImportModels.DashboardRow> rows = study.store.activeDashboardRows();
        if (rows.isEmpty()) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.NO_NEW_CARDS_AVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show();
            return false;
        }
        long now = System.currentTimeMillis();
        BridgeScheduler.ExtraNewCardsResult result = new BridgeScheduler().seedExtraNewCards(
                rows,
                study.store.studyItems(),
                study.settings(),
                now,
                study.startOfDay(now),
                requestedCount,
                study.studyLadderSettings()
        );
        if (!result.admittedAny()) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.NO_NEW_CARDS_AVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show();
            return false;
        }
        StudyMoreNewCardActions.AdmissionResult admission = StudyMoreNewCardActions.applyAdmission(
                result,
                new MainActivityStudyMoreNewCardWriter(study),
                study.studyMoreNewCardKanji,
                study::resetStudyRunProgress,
                study.studySessionTracker::setTargetCount
        );
        study.continueAllKanjiSession = false;
        if (admission.admittedCount() < requestedCount) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.partialAvailabilityMessage(admission.admittedCount()), Toast.LENGTH_SHORT).show();
        }
        study.renderStudy();
        return true;
    }
}
