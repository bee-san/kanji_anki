package dev.bee.kanjianki

import dev.bee.kanjianki.core.SimilarKanjiExplanation

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

data class SimilarKanjiExplanationLineModel(
    val label: String,
    val value: String,
    val emphasized: Boolean = false,
)

internal fun similarKanjiExplanationLines(explanation: SimilarKanjiExplanation): List<SimilarKanjiExplanationLineModel> {
    val out = ArrayList<SimilarKanjiExplanationLineModel>()
    if (explanation.confusedWith.isNotEmpty()) {
        val pairValue = if (explanation.targetKanji.isNotEmpty()) {
            "${explanation.targetKanji} vs ${explanation.confusedWith.joinToString(" / ")}"
        } else {
            explanation.confusedWith.joinToString(" / ")
        }
        out.add(SimilarKanjiExplanationLineModel("Pair", pairValue, true))
    }
    addJoinedLine(out, "Source words", explanation.failedSourceWords, false)
    addJoinedLine(out, "Meaning clues", explanation.meaningClues, false)
    addJoinedLine(out, "Reading clues", explanation.readingClues, false)
    addJoinedLine(out, "Shared components", explanation.sharedComponents, false)
    addJoinedLine(out, "Different components", explanation.differingComponents, false)
    out.add(SimilarKanjiExplanationLineModel("Watch", explanation.watchThisPart, true))
    return out
}

private fun addJoinedLine(
    out: MutableList<SimilarKanjiExplanationLineModel>,
    label: String,
    values: List<String>,
    emphasized: Boolean,
) {
    val cleanValues = values.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleanValues.isNotEmpty()) {
        out.add(SimilarKanjiExplanationLineModel(label, cleanValues.joinToString(" • "), emphasized))
    }
}

data class SimilarChoiceSessionModel(
    val modeLabel: String,
    val title: String,
    val taskLabel: String,
    val body: String,
    val reasonLine: String,
    val question: String,
    val gridModel: SimilarChoiceGridModel,
    val explanationLines: List<SimilarKanjiExplanationLineModel> = emptyList(),
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
