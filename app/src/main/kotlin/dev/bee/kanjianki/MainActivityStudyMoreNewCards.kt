package dev.bee.kanjianki

import android.widget.EditText
import android.widget.Toast
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy

internal class MainActivityStudyMoreNewCards(private val study: MainActivityStudy) {
    fun availableStudyMoreNewCards(): Int {
        return study.doneActions.availableStudyMoreNewCards()
    }

    fun showStudyMoreNewCardsDialog(availableAtOpen: Int) {
        study.doneActions.showStudyMoreNewCardsDialog(availableAtOpen)
    }

    fun applyStudyMoreNewCardsRequest(countInput: EditText): Boolean {
        return study.doneActions.applyStudyMoreNewCardsRequest(countInput)
    }

    fun requestedStudyMoreNewCards(countInput: EditText): Int {
        val decision = StudyMoreNewCardsPolicy.requestedCount(countInput.text.toString())
        if (!decision.accepted()) {
            Toast.makeText(study, decision.message(), Toast.LENGTH_SHORT).show()
            return -1
        }
        return decision.requestedCount()
    }

    fun startStudyMoreNewCards(requestedCount: Int): Boolean {
        val rows = study.store.activeDashboardRows()
        if (rows.isEmpty()) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.NO_NEW_CARDS_AVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return false
        }
        val now = System.currentTimeMillis()
        val result = BridgeScheduler().seedExtraNewCards(
            rows,
            study.store.studyItems(),
            study.settings(),
            now,
            study.startOfDay(now),
            requestedCount,
            study.studyLadderSettings()
        )
        if (!result.admittedAny()) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.NO_NEW_CARDS_AVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return false
        }
        val admission = StudyMoreNewCardActions.applyAdmission(
            result,
            MainActivityStudyMoreNewCardWriter(study),
            study.studyMoreNewCardKanji,
            study::resetStudyRunProgress,
            study.studySessionTracker::setTargetCount
        )
        study.continueAllKanjiSession = false
        if (admission.admittedCount < requestedCount) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.partialAvailabilityMessage(admission.admittedCount), Toast.LENGTH_SHORT).show()
        }
        study.renderStudy()
        return true
    }
}
