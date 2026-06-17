package dev.bee.kanjianki

import dev.bee.kanjianki.core.SimilarKanjiExplanation
import java.util.Locale

private const val JAPANESE_LANGUAGE = "ja"

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
        out.add(SimilarKanjiExplanationLineModel(localizedText("Compare shapes", "見比べ"), similarPairValue(explanation), true))
    }
    addJoinedLine(out, localizedText("Seen in", "使用例"), explanation.failedSourceWords, false)
    addJoinedLine(out, localizedText("Meaning hint", "意味のヒント"), explanation.meaningClues, false)
    addJoinedLine(out, localizedText("Reading hint", "読みのヒント"), explanation.readingClues, false)
    addJoinedLine(out, localizedText("Shared part", "共通部"), explanation.sharedComponents, false)
    addJoinedLine(out, localizedText("Different part", "違い"), explanation.differingComponents, false)
    out.add(SimilarKanjiExplanationLineModel(localizedText("Shape hint", "形のヒント"), explanation.watchThisPart, true))
    return out
}

private fun similarPairValue(explanation: SimilarKanjiExplanation): String {
    val separator = if (isJapaneseLocale()) "・" else " / "
    val confused = explanation.confusedWith.joinToString(separator)
    if (explanation.targetKanji.isEmpty()) {
        return confused
    }
    return if (isJapaneseLocale()) {
        "${explanation.targetKanji}と$confused"
    } else {
        "${explanation.targetKanji} vs $confused"
    }
}

private fun localizedText(english: String, japanese: String): String = if (isJapaneseLocale()) japanese else english

private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

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

internal data class MeaningChoiceSessionModel(
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
