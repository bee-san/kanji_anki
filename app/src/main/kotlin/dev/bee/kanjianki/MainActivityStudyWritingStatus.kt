package dev.bee.kanjianki

import dev.bee.kanjianki.core.study.WritingFeedbackCopy
import dev.bee.kanjianki.study.WritingRecognizer

internal class MainActivityStudyWritingStatus(private val activity: MainActivityStudy) {
    fun refreshWritingModelStatus() {
        activity.writingModelStatusKnown = false
        activity.writingModelDownloaded = false
        activity.updateResultActions()
        val token = activity.activeSession?.token
        val recognizer = activity.currentWritingRecognizer()
        if (recognizer == null) {
            updateWritingModelAvailability(false)
            activity.setStudyStatus(
                WritingFeedbackCopy.unavailableModelStatusMessage(
                    WritingFeedbackCopy.guideLabel(
                        activity.currentHintState,
                        activity.activeSession?.item?.let { activity.strokeGuide(it.kanji) }
                    )
                ),
                MainActivityBase.CORAL
            )
            activity.updateResultActions()
            return
        }
        recognizer.modelStatus().whenComplete { status, error ->
            activity.postToMainIfActive {
                if (token == null || !activity.isActiveToken(token)) {
                    return@postToMainIfActive
                }
                updateWritingModelAvailability(error == null && status != null && status.downloaded)
                activity.updateResultActions()
                if (activity.activeAnalysis != null || activity.checkingWriting) {
                    return@postToMainIfActive
                }
                setWritingModelStatusMessage(status, error)
            }
        }
    }

    fun setWritingModelStatusMessage(status: WritingRecognizer.ModelStatus?, error: Throwable?) {
        val prefix = guidePrefix()
        if (error != null || status == null) {
            activity.setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, status != null, false, error != null), MainActivityBase.CORAL)
            return
        }
        if (!status.downloaded) {
            activity.setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, true, false, false), MainActivityBase.CORAL)
            return
        }
        activity.setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, true, true, false), MainActivityBase.MUTED)
    }

    fun downloadWritingModel() {
        val token = activity.activeSession?.token
        val recognizer = activity.currentWritingRecognizer()
        if (recognizer == null) {
            activity.setStudyStatus(WritingFeedbackCopy.unavailableModelStatusMessage(guidePrefix()), MainActivityBase.CORAL)
            return
        }
        activity.setStudyStatus(WritingFeedbackCopy.checkerDownloadStatus(guidePrefix()), MainActivityBase.MUTED)
        recognizer.downloadModel().whenComplete { _, error ->
            activity.postToMainIfActive {
                if (token != null && !activity.isActiveToken(token)) {
                    return@postToMainIfActive
                }
                val prefix = guidePrefix()
                if (error != null) {
                    updateWritingModelAvailability(false)
                    activity.setStudyStatus(
                        WritingFeedbackCopy.checkerDownloadFailedStatus(prefix, error.message),
                        MainActivityBase.CORAL
                    )
                } else {
                    updateWritingModelAvailability(true)
                    activity.setStudyStatus(
                        WritingFeedbackCopy.modelStatusMessage(prefix, true, true, false),
                        MainActivityBase.TEAL
                    )
                }
                activity.updateResultActions()
            }
        }
    }

    fun updateWritingModelAvailability(downloaded: Boolean) {
        activity.writingModelStatusKnown = true
        activity.writingModelDownloaded = downloaded
    }

    private fun guidePrefix(): String {
        return WritingFeedbackCopy.guideLabel(
            activity.currentHintState,
            activity.activeSession?.item?.let { activity.strokeGuide(it.kanji) }
        )
    }
}
