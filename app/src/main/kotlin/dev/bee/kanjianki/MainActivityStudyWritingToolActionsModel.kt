package dev.bee.kanjianki

data class WritingToolActionsModel(
    val undoEnabled: Boolean,
    val hintText: String,
    val hintVisible: Boolean,
    val onErase: Runnable,
    val onUndo: Runnable,
    val onHint: Runnable,
) {
    companion object {
        fun initial(): WritingToolActionsModel {
            return WritingToolActionsModel(
                undoEnabled = false,
                hintText = "Hint",
                hintVisible = false,
                onErase = Runnable {},
                onUndo = Runnable {},
                onHint = Runnable {}
            )
        }
    }
}
