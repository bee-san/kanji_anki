package dev.bee.kanjianki

data class WritingPromptHeaderModel(
    val modeLabel: String,
    val title: String,
    val detailLines: List<WritingPromptLineModel>,
)

data class WritingPromptLineModel(
    val text: String,
    val sizeSp: Int,
    val color: Int,
    val bold: Boolean,
)
