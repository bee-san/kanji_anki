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
import dev.bee.kanjianki.widget.KaniWidgetUpdater

internal class MainActivityStudyDoneActions(private val home: MainActivityStudy) {
    private var renderedPlan: RecordsSchedulerModels.AdaptiveLoadPlan? = null
    private var renderedScreenModel: StudyDoneScreenModel? = null
    private var studyMoreDialog: StudyMoreNewCardsDialogModel? = null
    private var cachedStudyMoreNewCardsSnapshot: StudyMoreNewCardsSnapshot? = null
    private var cachedStudyMoreNewCardsAvailability: Int? = null

    internal data class StudyMoreNewCardsSnapshot(
        val rows: List<RecordsImportModels.DashboardRow>,
        val existing: List<RecordsStudyModels.StudyItem>,
    )

    fun renderNoStudySession(
        seededPlan: RecordsSchedulerModels.AdaptiveLoadPlan,
        expectedRoute: StudyRouteSnapshot,
    ) {
        if (!home.continueAllKanjiSession && seededPlan.focusComplete()) {
            renderFocusDone(seededPlan, expectedRoute)
            return
        }
        renderStudyDone(
            seededPlan,
            studyDoneScreenModel(
                StudyTextCopy.nothingDueTitle(),
                StudyTextCopy.allCaughtUpHeadline(),
                StudyTextCopy.allCaughtUpBody(),
                emptyList(),
                false,
                true,
                true
            ),
            StudyRouteCompletionReason.NO_SESSION,
            expectedRoute,
        )
    }

    fun renderFocusDone(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan,
        expectedRoute: StudyRouteSnapshot,
    ) {
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
            ),
            StudyRouteCompletionReason.FOCUS_COMPLETE,
            expectedRoute,
        )
    }

    fun renderStudyRunDone(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        expectedRoute: StudyRouteSnapshot,
    ) {
        val summaryLines = mutableListOf<String>()
        summaryLines.add(StudyTextCopy.movedForwardSummary(home.studySessionTracker.movedForwardCount()))
        summaryLines.add(StudyTextCopy.missedSummary(home.studySessionTracker.missedCount()))
        summaryLines.add(StudyTextCopy.completedTaskBreakdownSummary(home.studySessionTracker.completedTaskBreakdown()))
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
            ),
            StudyRouteCompletionReason.HARD_CAP,
            expectedRoute,
        )
    }

    fun renderEmptyStudyQueue(
        expectedRoute: StudyRouteSnapshot,
    ) {
        renderStudyDone(
            home.activeStudyPlan,
            studyDoneScreenModel(
                StudyTextCopy.studyPracticeTitle(),
                StudyTextCopy.nothingToStudyHeadline(),
                StudyTextCopy.syncAnkiDroidFirstBody(),
                emptyList(),
                false,
                false,
                false
            ),
            StudyRouteCompletionReason.NO_SESSION,
            expectedRoute,
        )
    }

    fun renderStudyForKanjiNotAvailable(
        expectedRoute: StudyRouteSnapshot,
    ) {
        renderStudyDone(
            home.activeStudyPlan,
            studyDoneScreenModel(
                StudyTextCopy.studyPracticeTitle(),
                StudyTextCopy.kanjiNotAvailableHeadline(),
                StudyTextCopy.kanjiChangedAfterSyncBody(),
                emptyList(),
                false,
                false,
                false
            ),
            StudyRouteCompletionReason.NO_SESSION,
            expectedRoute,
        )
    }

    private fun renderStudyDone(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        model: StudyDoneScreenModel,
        reason: StudyRouteCompletionReason,
        expectedRoute: StudyRouteSnapshot,
    ) {
        val terminalRoute = if (
            reason == StudyRouteCompletionReason.NO_SESSION ||
            reason == StudyRouteCompletionReason.FOCUS_COMPLETE
        ) {
            home.acceptTerminalSessionAbsence(expectedRoute) ?: return
        } else {
            expectedRoute
        }
        val completionEvidence = home.studySessionViewModel.acceptCompletionEvidence(
            reason,
            terminalRoute.sessionGeneration,
            terminalRoute.version,
            terminalRoute.sessionToken,
        ) ?: return
        if (
            !home.studySessionViewModel.completeRoute(
                reason,
                completionEvidence.sessionGeneration,
                completionEvidence.version,
                completionEvidence.sessionToken,
            )
        ) {
            return
        }
        KaniWidgetUpdater.requestUpdate(home)
        renderedPlan = plan
        renderedScreenModel = model
        renderCurrentStudyDone(plan, model)
    }

    private fun renderCurrentStudyDone(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        model: StudyDoneScreenModel,
    ) {
        home.activeStudyPlan = plan
        val routeSnapshot = home.studySessionViewModel.acceptedRouteSnapshot()
        if (!routeSnapshot.isComplete) return
        home.renderComposeStudyRoute(routeSnapshot) {
            StudyDoneScreen(
                model = model.copy(studyMoreDialog = studyMoreDialog),
                modifier = Modifier.padding(top = 10.dp),
                routeSnapshot = routeSnapshot,
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
            StudyTextCopy.practiceLabel(),
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
            val availability = resolveStudyMoreNewCardsAvailability(
                cachedStudyMoreNewCardsSnapshot,
                cachedStudyMoreNewCardsAvailability,
                loadRows = { home.store.activeDashboardRows() },
                loadExisting = { kanji -> home.store.studyItemsForKanji(kanji) },
                countAvailable = { loadData ->
                    val now = System.currentTimeMillis()
                    BridgeScheduler.withWeights(home.store.schedulerFsrsWeights()).countExtraNewCardsAvailable(
                        loadData.rows,
                        loadData.existing,
                        home.settings(),
                        now,
                        home.startOfDay(now),
                        home.studyLadderSettings(),
                    )
                },
            )
            if (availability == null) {
                clearStudyMoreNewCardsSnapshot()
                return@withUiTrace 0
            }
            if (cachedStudyMoreNewCardsSnapshot == null) {
                cachedStudyMoreNewCardsSnapshot = StudyMoreNewCardsSnapshot(
                    availability.loadData.rows,
                    availability.loadData.existing,
                )
            }
            if (cachedStudyMoreNewCardsAvailability == null) {
                cachedStudyMoreNewCardsAvailability = availability.availableCount
            }
            availability.availableCount
        }
    }

    fun studyMoreNewCardsSnapshot(): StudyMoreNewCardsSnapshot? {
        return cachedStudyMoreNewCardsSnapshot
    }

    fun clearStudyMoreNewCardsSnapshot() {
        cachedStudyMoreNewCardsSnapshot = null
        cachedStudyMoreNewCardsAvailability = null
    }

    fun showStudyMoreNewCardsDialog(availableAtOpen: Int) {
        val defaultCount = StudyMoreNewCardsPolicy.defaultRequestCount(availableAtOpen)
        studyMoreDialog = StudyMoreNewCardsDialogModel(
            title = StudyTextCopy.studyMoreNewCardsLabel(),
            message = StudyTextCopy.studyMoreNewCardsDialogMessage(),
            inputLabel = StudyTextCopy.newCardsLabel(),
            initialCount = defaultCount,
            confirmLabel = StudyTextCopy.studyLabel(),
            cancelLabel = StudyTextCopy.cancelLabel(),
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
