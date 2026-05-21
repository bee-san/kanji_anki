package dev.bee.kanjianki

import android.view.View
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.study.WritingFeedbackCopy

internal class MainActivityStudyWritingSession(private val home: MainActivityStudy) {
    fun renderWritingSession(session: RecordsSchedulerModels.StudySession) {
        resetWritingSession(session)

        val answerPanelState = WritingAnswerPanelState(false)
        home.writingAnswerPanelState = answerPanelState
        home.studyAnswerPanel = null
        val answerPanel = home.learningPanelModel(session)

        val guide = home.strokeGuide(session.item.kanji)
        val status = WritingStatusState()
        home.studyStatus = status
        status.setStatus(WritingFeedbackCopy.guideLabel(home.currentHintState, guide), MainActivityUiSupport.STUDY_MUTED)
        val drawingPad = DrawingPadView(home)
        home.drawingPad = drawingPad
        drawingPad.setTarget(session.item.kanji)
        drawingPad.setInkEditListener(home::handleDrawingEdited)
        drawingPad.setStrokeBlockedListener(home::handleDrawingBlocked)
        drawingPad.setGuide(guide, home.currentHintState, false)
        val resultStatus = WritingResultStatusHandle()
        home.writingResultStatus = resultStatus
        resultStatus.hide()
        home.content.addView(
            writingSessionCardView(
                home,
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
                )
            )
        )

        home.buildStudyActionBar()
        home.updateResultActions()
        home.refreshWritingModelStatus()
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
        home.prepareStudyContent(home.activeStudyPlan, false)
        home.activeAnalysis = null
        home.checkingWriting = false
        home.flashcardGestureArea = null
        home.flashcardAnswerRevealed = false
        home.flashcardTouchTracking = false
        home.typingAnswerState = null
        home.hintsUsed = 0
        home.setHintState(home.initialHintState(session))
    }

    fun hideStudyActionBar() {
        val studyActionBar = home.studyActionBar
        if (studyActionBar != null) {
            studyActionBar.removeAllViews()
            studyActionBar.visibility = View.GONE
        }
    }

    fun renderSimilarWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        now: Long,
    ) {
        val active = StudyRepairActions.activateSimilarWritingRepair(repair, now, home.store::saveSimilarWritingRepair)
        val activeRepair = active.repair
        home.activeSimilarWritingRepair = activeRepair
        val item = BridgeScheduler().newTargetedStudyItem(activeRepair.repairKanji, now, home.studyLadderSettings())
        val session = RecordsSchedulerModels.StudySession(
            item.withToken(active.token),
            null,
            active.token,
            MainActivityBase.TASK_REPAIR_WRITING,
            true,
            StudyTextCopy.similarRepairPrompt(activeRepair)
        )
        home.activeSession = session
        home.activeStudyPlan = plan
        home.registerStudyTaskShown(active.progressKey)
        home.startActiveStudyTask(active.studyTaskKey, activeRepair.repairKanji, MainActivityBase.TASK_REPAIR_WRITING, now)
        renderWritingSession(session)
    }
}
