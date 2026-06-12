package dev.bee.kanjianki

data class StudyAnswerPanelModel(
    val title: String,
    val glyph: String,
    val glyphSizeSp: Int,
    val lines: List<StudyAnswerLineModel>,
    val helperText: String?,
)

data class StudyAnswerLineModel(
    val text: String,
    /** Legacy palette ARGB; resolve with [kaniColor] at render time. */
    val color: Int,
    val sizeSp: Int,
    val bold: Boolean,
)
