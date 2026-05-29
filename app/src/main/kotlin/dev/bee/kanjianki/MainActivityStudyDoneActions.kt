package dev.bee.kanjianki

import android.widget.EditText
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy
import dev.bee.kanjianki.core.StudyTextCopy

internal class MainActivityStudyDoneActions(private val home: MainActivityStudy) {
    private var renderedPlan: RecordsSchedulerModels.AdaptiveLoadPlan? = null
    private var renderedScreenModel: StudyDoneScreenModel? = null
    private var studyMoreDialog: StudyMoreNewCardsDialogModel? = null

    fun renderNoStudySession(seededPlan: RecordsSchedulerModels.AdaptiveLoadPlan) {
        if (!home.continueAllKanjiSession && seededPlan.focusComplete()) {
            renderFocusDone(seededPlan)
            return
        }
        renderStudyDone(
            seededPlan,
            studyDoneScreenModel(
                "Nothing due now",
                "All caught up",
                "Your active kanji are resting. Sync again if Anki has created new problem candidates, or come back when the next review is due.",
                emptyList(),
                false,
                true,
                true
            )
        )
    }

    fun renderFocusDone(plan: RecordsSchedulerModels.AdaptiveLoadPlan) {
        val summaryLines = mutableListOf<String>()
        summaryLines.add(StudyTextCopy.adaptiveFocusDoneSummary(plan.target))
        if (plan.status.isNotEmpty()) {
            summaryLines.add(plan.status)
        }
        renderStudyDone(
            plan,
            studyDoneScreenModel(
                StudyTextCopy.studyDoneTitle(),
                null,
                StudyTextCopy.adaptiveFocusDoneBody(),
                summaryLines,
                true,
                false,
                false
            )
        )
    }

    fun renderStudyRunDone(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        val summaryLines = mutableListOf<String>()
        summaryLines.add(StudyTextCopy.movedForwardSummary(home.studySessionTracker.movedForwardCount()))
        summaryLines.add(StudyTextCopy.missedSummary(home.studySessionTracker.missedCount()))
        summaryLines.add(StudyTextCopy.completedTaskSummary(home.studySessionTracker.completedCount()))
        if (plan != null && plan.status.isNotEmpty()) {
            summaryLines.add(plan.status)
        }
        renderStudyDone(
            plan,
            studyDoneScreenModel(
                StudyTextCopy.studyDoneTitle(),
                null,
                StudyTextCopy.studyRunDoneBody(),
                summaryLines,
                true,
                false,
                false
            )
        )
    }

    fun renderEmptyStudyQueue() {
        renderStudyDone(
            home.activeStudyPlan,
            studyDoneScreenModel(
                "Study practice",
                "Nothing to study yet",
                "Sync from AnkiDroid first. Study opens once the app finds problem kanji to repair.",
                emptyList(),
                false,
                false,
                false
            )
        )
    }

    fun renderStudyForKanjiNotAvailable() {
        renderStudyDone(
            home.activeStudyPlan,
            studyDoneScreenModel(
                "Study practice",
                "Kanji not available",
                "This row may have changed after sync.",
                emptyList(),
                false,
                false,
                false
            )
        )
    }

    private fun renderStudyDone(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        model: StudyDoneScreenModel,
    ) {
        renderedPlan = plan
        renderedScreenModel = model
        renderCurrentStudyDone(plan, model)
    }

    private fun renderCurrentStudyDone(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        model: StudyDoneScreenModel,
    ) {
        home.activeStudyPlan = plan
        home.renderComposeStudyRoute {
            StudyDoneScreen(
                model = model.copy(studyMoreDialog = studyMoreDialog),
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }

    private fun rerenderStudyDone() {
        val model = renderedScreenModel ?: return
        renderCurrentStudyDone(renderedPlan, model)
    }

    private fun studyDoneScreenModel(
        title: String,
        headline: String?,
        body: String,
        summaryLines: List<String>,
        showDoneActions: Boolean,
        showBackHome: Boolean,
        backHomePrimary: Boolean,
    ): StudyDoneScreenModel {
        val available = if (showDoneActions) availableStudyMoreNewCards() else 0
        return StudyDoneScreenModel(
            MainActivityBase.LABEL_PRACTICE,
            title,
            headline,
            body,
            summaryLines,
            showDoneActions,
            available,
            showBackHome,
            backHomePrimary,
            Runnable { showStudyMoreNewCardsDialog(available) },
            Runnable { continueAllKanji() },
            Runnable { backHome() }
        )
    }

    private fun continueAllKanji() {
        home.studyMoreNewCardKanji.clear()
        home.continueAllKanjiSession = true
        home.renderStudy()
    }

    private fun backHome() {
        home.clearStudyModeOverrides()
        home.renderHome()
    }

    fun availableStudyMoreNewCards(): Int {
        val rows = home.store.activeDashboardRows()
        if (rows.isEmpty()) {
            return 0
        }
        val now = System.currentTimeMillis()
        val result = BridgeScheduler().seedExtraNewCards(
            rows,
            home.store.studyItems(),
            home.settings(),
            now,
            home.startOfDay(now),
            Int.MAX_VALUE,
            home.studyLadderSettings()
        )
        return result.availableCount
    }

    fun showStudyMoreNewCardsDialog(availableAtOpen: Int) {
        val defaultCount = StudyMoreNewCardsPolicy.defaultRequestCount(availableAtOpen)
        studyMoreDialog = StudyMoreNewCardsDialogModel(
            title = "Study more new cards",
            message = "How many extra new cards do you want to study now?",
            inputLabel = MainActivityBase.LABEL_NEW_CARDS,
            initialCount = defaultCount,
            confirmLabel = MainActivityBase.LABEL_STUDY,
            cancelLabel = "Cancel",
            onConfirm = ::applyStudyMoreNewCardsRequest,
            onDismiss = Runnable {
                studyMoreDialog = null
                rerenderStudyDone()
            }
        )
        rerenderStudyDone()
    }

    fun applyStudyMoreNewCardsRequest(countInput: EditText): Boolean {
        val requested = home.requestedStudyMoreNewCards(countInput)
        return requested > 0 && home.startStudyMoreNewCards(requested)
    }

    fun applyStudyMoreNewCardsRequest(requestText: String): Boolean {
        val requested = home.requestedStudyMoreNewCards(requestText)
        if (requested <= 0) {
            return false
        }
        val started = home.startStudyMoreNewCards(requested)
        if (started) {
            studyMoreDialog = null
        }
        return started
    }
}
