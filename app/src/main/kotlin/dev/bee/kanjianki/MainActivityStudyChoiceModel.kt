package dev.bee.kanjianki

fun interface KanjiChoiceHandler {
    fun onChoice(glyph: String)
}

data class SimilarChoiceGridModel(
    val choices: List<String>,
    val balanceLastRow: Boolean,
    val onChoice: KanjiChoiceHandler,
)

data class SimilarChoiceSessionModel(
    val modeLabel: String,
    val title: String,
    val taskLabel: String,
    val body: String,
    val reasonLine: String,
    val question: String,
    val gridModel: SimilarChoiceGridModel,
)

data class MeaningChoiceSessionModel(
    val modeLabel: String,
    val title: String,
    val taskLabel: String,
    val body: String,
    val reasonLine: String,
    val question: String,
    val choices: List<String>,
    val answerPanel: StudyAnswerPanelModel,
    val onChoice: KanjiChoiceHandler,
)
