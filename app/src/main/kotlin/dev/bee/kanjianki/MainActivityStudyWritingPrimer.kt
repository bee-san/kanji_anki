package dev.bee.kanjianki

import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.core.StudyWritingCopy
import dev.bee.kanjianki.core.study.HintLevel
import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.StrokeOrderEvaluator
import dev.bee.kanjianki.core.study.WritingCompareModel
import dev.bee.kanjianki.core.study.WritingFeedbackCopy
import dev.bee.kanjianki.core.study.WritingPrimerStage

/**
 * Drives the ungraded writing primer (watch → write from memory → compare)
 * that precedes a kanji's first guided `write_kanji` attempt.
 *
 * The primer never touches [MainActivityBase.activeAnalysis] or
 * [MainActivityBase.hintsUsed] and never submits a review: when it ends, the
 * card is exactly as it would have been without it.
 */
internal class MainActivityStudyWritingPrimer(private val activity: MainActivityStudy) {
    fun active(): Boolean = activity.writingPrimerStage != null

    /** Called once the pad exists for a freshly built writing card. */
    fun begin(session: RecordsSchedulerModels.StudySession) {
        val writeKanjiCard = StudyTaskTypes.WRITE_KANJI == session.taskType &&
            session.row != null &&
            activity.activeSimilarWritingRepair == null
        if (WritingPrimerStage.shouldPrime(writeKanjiCard, firstAppearance(session), activity.currentHintState, guide())) {
            watch()
        }
    }

    fun watch() {
        val pad = activity.drawingPad ?: return
        enter(WritingPrimerStage.WATCH, clearInk = true)
        pad.startGuideAnimation()
        setStatus(StudyWritingCopy.primerWatchStatus())
        render()
    }

    fun startWriting() {
        enter(WritingPrimerStage.WRITE, clearInk = true)
        setStatus(StudyWritingCopy.primerWriteStatus())
        render()
    }

    fun compare() {
        val pad = activity.drawingPad ?: return
        val guide = guide()
        val sample = pad.writingSample()
        val model = WritingCompareModel.from(guide, sample, StrokeOrderEvaluator.evaluate(guide, sample).diagnosis)
        if (model == null) {
            setStatus(StudyWritingCopy.primerNeedsInkStatus())
            return
        }
        enter(WritingPrimerStage.COMPARE, clearInk = false)
        activity.writingCompareState?.show(model)
        setStatus(StudyWritingCopy.primerCompareStatus())
        render()
    }

    /** Leaves the primer and hands the untouched card to the graded flow. */
    fun finish() {
        val pad = activity.drawingPad
        activity.writingPrimerStage = null
        activity.writingCompareState?.hide()
        if (pad != null) {
            pad.clear()
            pad.setInputEnabled(true)
            pad.setGuide(guide(), activity.currentHintState, false)
        }
        activity.setStudyStatus(
            WritingFeedbackCopy.guideLabel(activity.currentHintState, guide()),
            MainActivityBase.MUTED,
        )
        activity.updateResultActions()
        activity.refreshWritingModelStatus()
    }

    /** New ink while comparing means the learner is trying again. */
    fun onInkEdited() {
        if (activity.writingPrimerStage == WritingPrimerStage.COMPARE) {
            enter(WritingPrimerStage.WRITE, clearInk = false)
            setStatus(StudyWritingCopy.primerWriteStatus())
        }
        render()
    }

    fun render() {
        val stage = activity.writingPrimerStage ?: return
        val pad = activity.drawingPad
        activity.writingToolActionsView?.render(
            WritingToolActionsModel(
                undoEnabled = stage.padInputEnabled() && pad != null && pad.canUndoStroke(),
                hintText = "",
                hintVisible = false,
                onErase = Runnable { if (stage.padInputEnabled()) startWriting() },
                onUndo = Runnable {
                    pad?.undoLastStroke()
                    onInkEdited()
                },
                onHint = Runnable {},
            )
        )
        activity.writingPrimaryActionsView?.render(primaryActions(stage))
        activity.writingFallbackActionsView?.render(
            WritingFallbackActionsModel(
                replayVisible = true,
                manualOverrideVisible = false,
                practiceWithGuideVisible = false,
                onReplay = Runnable { watch() },
                onManualOverride = Runnable {},
                onPracticeWithGuide = Runnable {},
            )
        )
        activity.writingAnswerPanelState?.updateVisible(stage.referenceVisible())
        activity.writingResultStatus?.hide()
    }

    private fun primaryActions(stage: WritingPrimerStage): WritingPrimaryActionsModel {
        val comparing = stage == WritingPrimerStage.COMPARE
        return WritingPrimaryActionsModel(
            checkText = when (stage) {
                WritingPrimerStage.WATCH -> StudyWritingCopy.primerWriteLabel()
                WritingPrimerStage.WRITE -> StudyWritingCopy.primerCompareLabel()
                WritingPrimerStage.COMPARE -> StudyWritingCopy.primerTryAgainLabel()
            },
            checkVisible = true,
            checkEnabled = true,
            downloadText = "",
            downloadVisible = false,
            nextText = StudyWritingCopy.primerStartLabel(),
            nextVisible = comparing,
            nextTone = StudyActionTone.PRIMARY,
            onCheck = if (stage == WritingPrimerStage.WRITE) Runnable { compare() } else Runnable { startWriting() },
            onDownload = Runnable {},
            onNext = Runnable { finish() },
            skipText = StudyWritingCopy.primerSkipLabel(),
            skipVisible = !comparing,
            skipEnabled = true,
            onSkip = Runnable { finish() },
        )
    }

    private fun enter(stage: WritingPrimerStage, clearInk: Boolean) {
        activity.writingPrimerStage = stage
        activity.writingCompareState?.hide()
        val pad = activity.drawingPad ?: return
        if (clearInk) {
            pad.clear()
        }
        pad.setInputEnabled(stage.padInputEnabled())
        // Free-recall state: nothing is drawn and nothing is blocked unless the
        // stage reveals the whole model.
        pad.setGuide(guide(), HintState(HintLevel.BLIND, 0, 0), stage.guideRevealed())
    }

    /**
     * Adaptive items always have core reviews behind a repair, so their first
     * writing appearance is a repair not yet attempted. Legacy items count
     * their own reviews.
     */
    private fun firstAppearance(session: RecordsSchedulerModels.StudySession): Boolean {
        val item = session.item ?: return false
        val route = AdaptiveStudyItemPolicy.routeState(item)
        return if (route != null) route.repairAttemptCount == 0 else item.totalReviews == 0
    }

    private fun setStatus(text: String) {
        activity.studyStatus?.setStatus(text, MainActivityBase.MUTED)
    }

    private fun guide(): StrokeGuide? = activity.activeSession?.item?.let { activity.strokeGuide(it.kanji) }
}
