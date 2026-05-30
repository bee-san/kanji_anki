package dev.bee.kanjianki

fun interface KanjiChoiceHandler {
    fun onChoice(glyph: String)
}

fun interface MeaningChoiceResultResolver {
    fun resultForChoice(glyph: String): MeaningChoiceResultModel
}

data class MeaningChoiceResultModel(
    val status: String,
    val statusColor: Int,
    val actionLabel: String,
    val correctChoice: String? = null,
    val selectedChoiceCorrect: Boolean? = null,
)

enum class KanjiChoiceFeedback {
    CORRECT,
    INCORRECT,
}

internal fun feedbackForMeaningChoice(
    glyph: String,
    selectedChoice: String?,
    result: MeaningChoiceResultModel?,
): KanjiChoiceFeedback? {
    if (selectedChoice == null || result == null) {
        return null
    }
    if (result.correctChoice == glyph) {
        return KanjiChoiceFeedback.CORRECT
    }
    return if (glyph == selectedChoice && result.selectedChoiceCorrect == false) {
        KanjiChoiceFeedback.INCORRECT
    } else {
        null
    }
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
    val resultResolver: MeaningChoiceResultResolver? = null,
)
