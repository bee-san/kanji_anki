package dev.bee.kanjianki

import android.widget.EditText
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy
import dev.bee.kanjianki.core.StudyTextCopy

internal class MainActivityStudyDoneActions(private val home: MainActivityStudy) {
    private var renderedPlan: RecordsSchedulerModels.AdaptiveLoadPlan? = null
    private var renderedScreenModel: StudyDoneScreenModel? = null
    private var studyMoreDialog: StudyMoreNewCardsDialogModel? = null
    private var cachedStudyMoreNewCardsSnapshot: StudyMoreNewCardsSnapshot? = null

    internal data class StudyMoreNewCardsSnapshot(
        val rows: List<RecordsImportModels.DashboardRow>,
        val existing: List<RecordsStudyModels.StudyItem>,
    )

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
                "Your active kanji are resting. Sync if AnkiDroid has new cards, or return when reviews are due.",
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
                "Sync from AnkiDroid first to find kanji to repair.",
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
        clearStudyMoreNewCardsSnapshot()
        home.renderStudy()
    }

    private fun backHome() {
        clearStudyMoreNewCardsSnapshot()
        home.clearStudyModeOverrides()
        home.renderHome()
    }

    fun availableStudyMoreNewCards(): Int {
        return withUiTrace("kani.study.more-new-cards.available") {
            val loadData = resolveStudyMoreNewCardsLoadData(
                cachedStudyMoreNewCardsSnapshot,
                loadRows = { home.store.activeDashboardRows() },
                loadExisting = { kanji -> home.store.studyItemsForKanji(kanji) },
            )
            if (loadData == null) {
                cachedStudyMoreNewCardsSnapshot = null
                return@withUiTrace 0
            }
            if (cachedStudyMoreNewCardsSnapshot == null) {
                cachedStudyMoreNewCardsSnapshot = StudyMoreNewCardsSnapshot(loadData.rows, loadData.existing)
            }
            val now = System.currentTimeMillis()
            BridgeScheduler().countExtraNewCardsAvailable(
                loadData.rows,
                loadData.existing,
                home.settings(),
                now,
                home.startOfDay(now),
                home.studyLadderSettings(),
            )
        }
    }

    fun studyMoreNewCardsSnapshot(): StudyMoreNewCardsSnapshot? {
        return cachedStudyMoreNewCardsSnapshot
    }

    fun clearStudyMoreNewCardsSnapshot() {
        cachedStudyMoreNewCardsSnapshot = null
    }

    fun showStudyMoreNewCardsDialog(availableAtOpen: Int) {
        val defaultCount = StudyMoreNewCardsPolicy.defaultRequestCount(availableAtOpen)
        studyMoreDialog = StudyMoreNewCardsDialogModel(
            title = "Study more new cards",
            message = "How many extra new cards?",
            inputLabel = MainActivityBase.LABEL_NEW_CARDS,
            initialCount = defaultCount,
            confirmLabel = MainActivityBase.LABEL_STUDY,
            cancelLabel = "Cancel",
            onConfirm = ::applyStudyMoreNewCardsRequest,
            onDismiss = Runnable {
                studyMoreDialog = null
                clearStudyMoreNewCardsSnapshot()
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
