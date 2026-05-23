package dev.bee.kanjianki

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
                checkText = "Check",
                checkVisible = true,
                checkEnabled = true,
                downloadText = "Download checker",
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
