package dev.bee.kanjianki

import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.WritingAnalysisEngine
import dev.bee.kanjianki.core.study.WritingSample
import dev.bee.kanjianki.core.StudyWritingCopy
import dev.bee.kanjianki.study.CapturedWriting
import dev.bee.kanjianki.study.WritingRecognizer

internal class MainActivityStudyWritingCheck(private val activity: MainActivityStudy) {
    fun checkWriting() {
        val session = activity.activeSession ?: return
        if (activity.showNoInkWhenNeeded()) {
            return
        }
        if (activity.checkingWriting) {
            return
        }
        val token = session.token
        val target = session.item?.kanji ?: return
        val attempt = try {
            capturedWritingAttempt()
        } catch (error: IllegalArgumentException) {
            val analysis = WritingAnalysisEngine.noInk(activity.currentHintState.level(), activity.hintsUsed)
            activity.activeAnalysis = analysis
            activity.showAnalysis(analysis)
            return
        }
        val guide = activity.strokeGuide(target)
        activity.checkingWriting = true
        activity.updateResultActions()
        activity.setStudyStatus(StudyWritingCopy.checkingStatus(), MainActivityBase.MUTED)
        val recognizer = activity.currentWritingRecognizer()
        if (recognizer == null) {
            activity.showModelUnavailable(StudyWritingCopy.modelUnavailableStatus())
            return
        }
        recognizer.modelStatus().whenComplete { status, statusError ->
            if (statusError != null || status == null || !status.downloaded) {
                activity.main.post {
                    if (!activity.isActiveToken(token)) {
                        return@post
                    }
                    activity.writingModelDownloaded = false
                    activity.writingModelStatusKnown = true
                    activity.showModelUnavailable(StudyWritingCopy.downloadRequiredStatus())
                }
                return@whenComplete
            }
            recognizeWriting(recognizer, attempt.captured, attempt.sample, guide, target, token)
        }
    }

    fun capturedWritingAttempt(): MainActivityStudy.CapturedWritingAttempt {
        return MainActivityStudy.CapturedWritingAttempt(
            activity.drawingPad!!.capturedWriting(),
            activity.drawingPad!!.writingSample()
        )
    }

    fun recognizeWriting(
        recognizer: WritingRecognizer,
        captured: CapturedWriting,
        sample: WritingSample,
        guide: StrokeGuide?,
        target: String,
        token: String,
    ) {
        recognizer.recognize(captured).whenComplete { result, error ->
            activity.main.post {
                if (!activity.isActiveToken(token)) {
                    return@post
                }
                activity.checkingWriting = false
                val analysis = if (error != null) {
                    WritingAnalysisEngine.recognitionError(activity.currentHintState.level(), activity.hintsUsed)
                } else {
                    WritingAnalysisEngine.analyze(
                        target,
                        sample,
                        guide,
                        activity.candidates(result),
                        activity.currentHintState.level(),
                        activity.hintsUsed
                    )
                }
                activity.activeAnalysis = analysis
                activity.showAnalysis(analysis)
            }
        }
    }
}
