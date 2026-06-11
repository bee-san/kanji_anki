package dev.bee.kanjianki

import dev.bee.kanjianki.core.study.WritingFeedbackCopy

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
) {
    companion object {
        fun initial(): WritingPrimaryActionsModel {
            return WritingPrimaryActionsModel(
                checkText = WritingFeedbackCopy.checkWritingButtonText(false, false),
                checkVisible = true,
                checkEnabled = true,
                downloadText = WritingFeedbackCopy.downloadCheckerLabel(),
                downloadVisible = true,
                nextText = MainActivityBase.LABEL_PASS,
                nextVisible = false,
                onCheck = Runnable {},
                onDownload = Runnable {},
                onNext = Runnable {}
            )
        }
    }
}
