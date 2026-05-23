package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color

data class StudyAnswerPanelModel(
    val title: String,
    val glyph: String,
    val glyphSizeSp: Int,
    val lines: List<StudyAnswerLineModel>,
    val helperText: String?,
)

data class StudyAnswerLineModel(
    val text: String,
    val color: Color,
    val sizeSp: Int,
    val bold: Boolean,
)
