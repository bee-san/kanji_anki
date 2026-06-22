package dev.bee.kanjianki

import dev.bee.kanjianki.core.study.WritingFeedbackCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.StudyWritingCopy

data class WritingPrimaryActionsModel(
    val checkText: String,
    val checkVisible: Boolean,
    val checkEnabled: Boolean,
    val downloadText: String,
    val downloadVisible: Boolean,
    val nextText: String,
    val nextVisible: Boolean,
    val onCheck: Runnable,
    val onDownload: Runnable,
    val onNext: Runnable,
    val skipText: String = StudyWritingCopy.skipLabel(),
    val skipVisible: Boolean = false,
    val skipEnabled: Boolean = true,
    val onSkip: Runnable = Runnable {},
) {
    companion object {
        fun initial(): WritingPrimaryActionsModel {
            return WritingPrimaryActionsModel(
                checkText = WritingFeedbackCopy.checkWritingButtonText(false, false),
                checkVisible = true,
                checkEnabled = true,
                downloadText = WritingFeedbackCopy.downloadCheckerLabel(),
                downloadVisible = true,
                nextText = StudyTextCopy.passLabel(),
                nextVisible = false,
                onCheck = Runnable {},
                onDownload = Runnable {},
                onNext = Runnable {},
                skipText = StudyWritingCopy.skipLabel(),
                skipVisible = false,
                skipEnabled = true,
                onSkip = Runnable {},
            )
        }
    }
}
