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
import dev.bee.kanjianki.data.StudyQueueSnapshot
import kotlinx.coroutines.runBlocking
import dev.bee.kanjianki.widget.KaniWidgetUpdater

internal class MainActivityStudyDoneActions(private val home: MainActivityStudy) {
    private val retained: StudyDoneViewModel
        get() = home.studyDoneViewModel

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
                true,
                true,
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
        retained.install(plan, model, reason)
        renderCurrentStudyDone()
    }

    private fun renderCurrentStudyDone() {
        val presentation = retained.presentation ?: return
        home.activeStudyPlan = retained.renderedPlan
        val routeSnapshot = home.studySessionViewModel.acceptedRouteSnapshot()
        if (!routeSnapshot.isComplete) return
        home.renderComposeStudyRoute(routeSnapshot) {
            StudyDoneScreen(
                model = presentation.toScreenModel(
                    studyMoreDialog = studyMoreDialogModel(),
                    onStudyMore = Runnable {
                        showStudyMoreNewCardsDialog(presentation.availableStudyMoreNewCards)
                    },
                    onContinueAll = Runnable(::continueAllKanji),
                    onBackHome = Runnable(::backHome),
                ),
                modifier = Modifier.padding(top = 10.dp),
                routeSnapshot = routeSnapshot,
            )
        }
    }

    private fun rerenderStudyDone() {
        renderCurrentStudyDone()
    }

    private fun studyDoneScreenModel(
        title: String,
        headline: String?,
        body: String,
        summaryLines: List<String>,
        showDoneActions: Boolean,
        showBackHome: Boolean,
        backHomePrimary: Boolean,
        offerStudyMoreNewCards: Boolean = false,
    ): StudyDoneScreenModel {
        val available = if (showDoneActions || offerStudyMoreNewCards) {
            availableStudyMoreNewCards()
        } else {
            0
        }
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
        clearRetainedStudyDone()
        home.renderStudy()
    }

    private fun backHome() {
        clearRetainedStudyDone()
        home.clearStudyModeOverrides()
        home.renderHome()
    }

    fun availableStudyMoreNewCards(): Int {
        return withUiTrace("kani.study.more-new-cards.available") {
            var loadedQueue: StudyQueueSnapshot? = null
            fun queue(): StudyQueueSnapshot {
                loadedQueue?.let { return it }
                return runBlocking {
                    home.studyUseCases.loadQueue(System.currentTimeMillis())
                }.also { loadedQueue = it }
            }
            val availability = resolveStudyMoreNewCardsAvailability(
                retained.cachedStudyMoreSnapshot,
                retained.cachedStudyMoreAvailability,
                loadRows = { queue().availableRows },
                loadExisting = { kanji ->
                    runBlocking { home.studyUseCases.loadItems(kanji) }
                },
                countAvailable = { loadData ->
                    val now = System.currentTimeMillis()
                    val snapshot = queue()
                    BridgeScheduler.withWeights(
                        snapshot.schedulerFsrsWeights?.toDoubleArray(),
                    ).countExtraNewCardsAvailable(
                        loadData.rows,
                        loadData.existing,
                        snapshot.syncSettings,
                        now,
                        home.startOfDay(now),
                        snapshot.studyLadder,
                    )
                },
            )
            if (availability == null) {
                clearStudyMoreNewCardsSnapshot()
                return@withUiTrace 0
            }
            if (retained.cachedStudyMoreSnapshot == null) {
                retained.cachedStudyMoreSnapshot = StudyMoreNewCardsSnapshot(
                    availability.loadData.rows,
                    availability.loadData.existing,
                )
            }
            if (retained.cachedStudyMoreAvailability == null) {
                retained.cachedStudyMoreAvailability = availability.availableCount
            }
            availability.availableCount
        }
    }

    fun studyMoreNewCardsSnapshot(): StudyMoreNewCardsSnapshot? {
        return retained.cachedStudyMoreSnapshot
    }

    fun clearStudyMoreNewCardsSnapshot() {
        retained.clearStudyMoreCache()
    }

    fun showStudyMoreNewCardsDialog(availableAtOpen: Int) {
        val defaultCount = StudyMoreNewCardsPolicy.defaultRequestCount(availableAtOpen)
        retained.showDialog(defaultCount)
        rerenderStudyDone()
    }

    private fun studyMoreDialogModel(): StudyMoreNewCardsDialogModel? {
        val initialCount = retained.dialogInitialCount ?: return null
        return StudyMoreNewCardsDialogModel(
            title = StudyTextCopy.studyMoreNewCardsLabel(),
            message = StudyTextCopy.studyMoreNewCardsDialogMessage(),
            inputLabel = StudyTextCopy.newCardsLabel(),
            initialCount = initialCount,
            requestText = retained.dialogRequestText ?: initialCount.toString(),
            onRequestTextChanged = retained::updateDialogRequestText,
            confirmLabel = StudyTextCopy.studyLabel(),
            cancelLabel = StudyTextCopy.cancelLabel(),
            onConfirm = ::applyStudyMoreNewCardsRequest,
            onDismiss = Runnable {
                retained.hideDialog()
                clearStudyMoreNewCardsSnapshot()
                rerenderStudyDone()
            }
        )
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
            retained.hideDialog()
        }
        return started
    }

    fun hasRetainedStudyDone(): Boolean = retained.presentation != null

    fun restoreRetainedStudyDone(): Boolean {
        val reason = retained.completionReason
        if (!hasRetainedStudyDone() || reason == null) {
            return false
        }
        if (
            !home.studySessionViewModel.acceptedRouteSnapshot().isComplete &&
            !home.studySessionViewModel.restoreTerminalPresentation(reason)
        ) {
            clearRetainedStudyDone()
            return false
        }
        renderCurrentStudyDone()
        return true
    }

    fun clearRetainedStudyDone() {
        retained.clear()
    }
}
