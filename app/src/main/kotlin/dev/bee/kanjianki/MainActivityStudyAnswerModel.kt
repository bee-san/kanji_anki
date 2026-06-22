package dev.bee.kanjianki

internal data class StudyAnswerPanelModel(
    val title: String,
    val glyph: String,
    val glyphSizeSp: Int,
    val lines: List<StudyAnswerLineModel>,
    val helperText: String?,
    val stateKey: String = "",
    val kanjiDetails: StudyAnswerKanjiDetailsModel? = null,
)

data class StudyAnswerLineModel(
    val text: String,
    /** Legacy palette ARGB; resolve with [kaniColor] at render time. */
    val color: Int,
    val sizeSp: Int,
    val bold: Boolean,
)
