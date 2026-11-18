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
import dev.bee.kanjianki.core.StudyWritingCopy
import dev.bee.kanjianki.core.StudyWritingPromptPolicy
import dev.bee.kanjianki.core.study.WritingFeedbackCopy

internal class MainActivityStudyWritingSession(private val home: MainActivityStudy) {
    fun renderComposeWritingSession(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ) {
        renderComposeWritingRoute { composeWritingRouteModel(session, mnemonic) }
    }

    private fun renderComposeWritingRoute(routeProvider: () -> WritingSessionRouteModel) {
        lateinit var preparedRoute: PreparedStudyRoute<WritingSessionRouteModel>
        home.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            studySessionActive = true,
            beforeContent = {
                preparedRoute = prepareAcceptedStudyRoute(
                    routeProvider,
                    home.studySessionViewModel::acceptedRouteSnapshot,
                )
            },
            content = {
                Column {
                    StudyTopBar(
                        routeSnapshot = preparedRoute.routeSnapshot,
                        onClose = home::renderHome,
                        onSettings = home::renderSettings,
                    )
                    ComposeWritingSessionCard(preparedRoute.model)
                }
            },
            actionBar = { ComposeWritingActionBar(preparedRoute.model) },
        )
        home.updateResultActions()
        home.refreshWritingModelStatus()
    }

    private fun composeWritingRouteModel(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel?,
    ): WritingSessionRouteModel {
        resetWritingInteractionState(session)
        val feedback = home.prepareStudyAnswerFeedback(session.token)
        val route = writingRouteModel(session, mnemonic, feedback)
        route.actionBarState = home.buildComposeWritingActionBarState()
        return route
    }

    private fun writingRouteModel(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel?,
        feedback: StudyAnswerFeedbackState,
    ): WritingSessionRouteModel {
        val targetKanji = session.item?.kanji ?: ""
        val answerPanelState = WritingAnswerPanelState(home.studyAnswerFeedbackState?.feedbackVisible == true)
        home.writingAnswerPanelState = answerPanelState
        home.studyAnswerPanel = null
        val answerPanel = home.learningPanelModel(session, mnemonic)

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
        val compare = WritingCompareState()
        home.writingCompareState = compare
        home.beginWritingPrimer(session)

        return WritingSessionRouteModel(
            WritingSessionCardModel(
                writingPromptHeaderModel(session),
                answerPanel,
                answerPanelState,
                status,
                drawingPad,
                home.studyPadHeight(),
                resultStatus,
                compare,
            ),
            session.token,
            home.studyContinueAction(feedback) { home.continueAfterStudyAnswer() },
        )
    }

    private fun writingPromptHeaderModel(session: RecordsSchedulerModels.StudySession): WritingPromptHeaderModel {
        val prompt = recallPrompt(session)
        return WritingPromptHeaderModel(
            StudyTaskCopy.studyModeLabel(session),
            if (prompt != null && prompt.hasBlank()) StudyWritingCopy.clozeTitle() else StudyWritingCopy.title(),
            writingPromptLines(session, prompt)
        )
    }

    /** Null only for the legacy row-less writing repair, which shows its stored prompt. */
    private fun recallPrompt(session: RecordsSchedulerModels.StudySession): StudyWritingPromptPolicy.Prompt? {
        if (session.row == null) {
            return null
        }
        return StudyWritingPromptPolicy.build(
            session,
            StudyTextCopy.sessionClue(home.currentDictionaryLookup(), session),
        )
    }

    private fun writingPromptLines(
        session: RecordsSchedulerModels.StudySession,
        prompt: StudyWritingPromptPolicy.Prompt?,
    ): List<WritingPromptLineModel> {
        if (prompt == null) {
            return listOf(
                WritingPromptLineModel(
                    safeText(session.prompt),
                    KaniUiTokens.StudyActionTextSizeSp,
                    MainActivityUiSupport.STUDY_MUTED,
                    false,
                )
            )
        }
        val texts = when (prompt.kind) {
            StudyWritingPromptPolicy.Kind.SENTENCE -> listOf(
                prompt.sentence,
                StudyWritingCopy.wordCueLine(prompt.word, prompt.reading, prompt.meaning),
            )
            StudyWritingPromptPolicy.Kind.WORD -> listOf(
                prompt.word,
                StudyWritingCopy.readingMeaningLine(prompt.reading, prompt.meaning),
            )
            StudyWritingPromptPolicy.Kind.MEANING -> listOf(
                StudyWritingCopy.recallPromptLine(prompt.meaning),
                if (prompt.reading.isEmpty()) "" else StudyWritingCopy.readingLine(prompt.reading),
            )
        }
        return texts.filter { it.isNotEmpty() }.mapIndexed { index, text ->
            if (index == 0) {
                WritingPromptLineModel(text, KaniUiTokens.StudyActionTextSizeSp, MainActivityUiSupport.STUDY_PLUM, true)
            } else {
                WritingPromptLineModel(text, KaniUiTokens.StudyBodyTextSizeSp, MainActivityUiSupport.STUDY_MUTED, false)
            }
        }
    }

    private fun safeText(value: String?): String {
        return value ?: ""
    }

    fun resetWritingSession(session: RecordsSchedulerModels.StudySession) {
        resetWritingInteractionState(session)
    }

    private fun resetWritingInteractionState(session: RecordsSchedulerModels.StudySession) {
        MainActivityStudyInteractionReset.resetWriting(home, session)
    }

    fun hideStudyActionBar() {
        home.flashcardActionBarState = null
    }

    @Composable
    private fun ComposeWritingSessionCard(route: WritingSessionRouteModel) {
        val model = remember(route) { route.cardModel }
        val browseAction = route.cardModel.answerPanel.glyph.takeIf { it.isNotBlank() }?.let { glyph ->
            Runnable {
                if (home.matchesMountedStudyRoute(route.sessionToken, null)) {
                    home.renderDetail(
                        glyph,
                        false,
                        null,
                        Runnable {
                            if (home.matchesMountedStudyRoute(route.sessionToken, null)) {
                                renderComposeWritingRoute { route }
                            }
                        },
                    )
                }
            }
        }
        WritingSessionCard(
            model = model,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 14.dp),
            onBrowseAction = browseAction,
        )
    }

    @Composable
    private fun ComposeWritingActionBar(route: WritingSessionRouteModel) {
        val state = route.actionBarState ?: return
        val undoMessage = home.studyUndoState.undoMessageOrNull()
        Column {
            StudyUndoSlot(
                undoMessage = undoMessage,
                onUndo = {
                    if (home.matchesMountedStudyRoute(route.sessionToken, null)) {
                        home.undoLastRating()
                    }
                },
            )
            val feedback = route.continueAction.feedbackState
            if (feedback.feedbackVisible) {
                val correct = feedback.outcome == StudyAnswerOutcome.CORRECT
                MeaningChoiceResultActionBar(
                    status = if (correct) StudyTextCopy.answerCorrectFeedback() else StudyTextCopy.answerIncorrectFeedback(),
                    statusColor = if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                    actionTone = if (correct) StudyActionTone.PASS else StudyActionTone.FAIL,
                    continueEnabled = feedback.continueEnabled,
                    continueAction = route.continueAction,
                    onNext = {},
                )
            } else {
                WritingActionsBar(state, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    private data class WritingSessionRouteModel(
        val cardModel: WritingSessionCardModel,
        val sessionToken: String,
        val continueAction: StudyContinueAction,
        var actionBarState: WritingActionsBarState? = null,
    )
}
