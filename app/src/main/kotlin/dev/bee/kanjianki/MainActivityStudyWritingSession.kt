package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.study.WritingFeedbackCopy

internal class MainActivityStudyWritingSession(private val home: MainActivityStudy) {
    fun renderComposeWritingSession(session: RecordsSchedulerModels.StudySession) {
        lateinit var route: WritingSessionRouteModel
        home.initializeSessionProgressTarget(home.activeStudyPlan)
        val progress = home.studySessionTracker.topBarProgress(home.activeSession != null, home.continueAllKanjiSession)
        home.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            beforeContent = { route = composeWritingRouteModel(session) },
            content = {
                Column {
                    StudyTopBar(
                        completed = progress.completed,
                        target = progress.target,
                        fraction = progress.fraction,
                        onClose = home::renderHome,
                        onSettings = home::renderSettings,
                    )
                    ComposeWritingSessionCard(route)
                }
            },
            actionBar = { ComposeWritingActionBar(route) },
        )
        home.updateResultActions()
        home.refreshWritingModelStatus()
    }

    private fun composeWritingRouteModel(session: RecordsSchedulerModels.StudySession): WritingSessionRouteModel {
        resetWritingInteractionState(session)
        val route = writingRouteModel(session)
        route.actionBarState = home.buildComposeWritingActionBarState()
        return route
    }

    private fun writingRouteModel(session: RecordsSchedulerModels.StudySession): WritingSessionRouteModel {
        val targetKanji = session.item?.kanji ?: ""
        val answerPanelState = WritingAnswerPanelState(false)
        home.writingAnswerPanelState = answerPanelState
        home.studyAnswerPanel = null
        val answerPanel = home.learningPanelModel(session)

        val guide = home.strokeGuide(targetKanji)
        val status = WritingStatusState()
        home.studyStatus = status
        status.setStatus(WritingFeedbackCopy.guideLabel(home.currentHintState, guide), MainActivityUiSupport.STUDY_MUTED)
        val drawingPad = DrawingPadView(home)
        home.drawingPad = drawingPad
        drawingPad.setTarget(targetKanji)
        drawingPad.setInkEditListener(home::handleDrawingEdited)
        drawingPad.setStrokeBlockedListener(home::handleDrawingBlocked)
        drawingPad.setGuide(guide, home.currentHintState, false)
        val resultStatus = WritingResultStatusHandle()
        home.writingResultStatus = resultStatus
        resultStatus.hide()

        return WritingSessionRouteModel(
            WritingSessionCardModel(
                writingPromptHeaderModel(session),
                answerPanel,
                answerPanelState,
                "Writing",
                MainActivityUiSupport.STUDY_PLUM,
                status,
                drawingPad,
                home.studyPadHeight(),
                resultStatus
            ),
        )
    }

    private fun writingPromptHeaderModel(session: RecordsSchedulerModels.StudySession): WritingPromptHeaderModel {
        return WritingPromptHeaderModel(
            MainActivityBase.LABEL_PRACTICE,
            "Draw this kanji",
            StudyTaskCopy.labelForTask(session.taskType),
            home.studyReasonLine(session),
            writingPromptLines(session)
        )
    }

    private fun writingPromptLines(session: RecordsSchedulerModels.StudySession): List<WritingPromptLineModel> {
        val lines = mutableListOf<WritingPromptLineModel>()
        val row = session.row
        if (row == null) {
            lines.add(WritingPromptLineModel(safeText(session.prompt), 17, MainActivityUiSupport.STUDY_MUTED, false))
            return lines
        }
        if (!StudyTaskCopy.isRecallTask(session)) {
            lines.add(
                WritingPromptLineModel(
                    "Learn it from the reference, trace it, then check.",
                    15,
                    MainActivityUiSupport.STUDY_MUTED,
                    false
                )
            )
            return lines
        }
        lines.add(
            WritingPromptLineModel(
                "Prompt: " + StudyTextCopy.sessionClue(home.currentDictionaryLookup(), session),
                17,
                MainActivityUiSupport.STUDY_PLUM,
                true
            )
        )
        if (row.reading.isNotEmpty()) {
            lines.add(WritingPromptLineModel("Reading: " + row.reading, 15, MainActivityUiSupport.STUDY_MUTED, false))
        }
        lines.add(
            WritingPromptLineModel(
                "Write the kanji from this prompt. The answer stays hidden until you check.",
                15,
                MainActivityUiSupport.STUDY_MUTED,
                false
            )
        )
        return lines
    }

    private fun safeText(value: String?): String {
        return value ?: ""
    }

    fun resetWritingSession(session: RecordsSchedulerModels.StudySession) {
        resetWritingInteractionState(session)
    }

    private fun resetWritingInteractionState(session: RecordsSchedulerModels.StudySession) {
        home.activeAnalysis = null
        home.checkingWriting = false
        home.flashcardGestureBounds = null
        home.flashcardAnswerRevealed = false
        home.flashcardTouchTracking = false
        home.typingAnswerState = null
        home.hintsUsed = 0
        home.setHintState(home.initialHintState(session))
        hideStudyActionBar()
    }

    fun hideStudyActionBar() {
        home.flashcardActionBarState = null
    }

    @Composable
    private fun ComposeWritingSessionCard(route: WritingSessionRouteModel) {
        val model = remember(route) { route.cardModel }
        WritingSessionCard(
            model = model,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 14.dp),
        )
    }

    @Composable
    private fun ComposeWritingActionBar(route: WritingSessionRouteModel) {
        val state = route.actionBarState ?: return
        WritingActionsBar(state, modifier = Modifier.fillMaxWidth())
    }

    private data class WritingSessionRouteModel(
        val cardModel: WritingSessionCardModel,
        var actionBarState: WritingActionsBarState? = null,
    )
}
