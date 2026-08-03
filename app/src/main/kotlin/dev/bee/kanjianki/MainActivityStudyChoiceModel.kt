package dev.bee.kanjianki

import dev.bee.kanjianki.core.SimilarKanjiExplanation
import java.util.Locale

private const val JAPANESE_LANGUAGE = "ja"

fun interface KanjiChoiceHandler {
    fun onChoice(glyph: String): Boolean
}

fun interface MeaningChoiceResultResolver {
    fun resultForChoice(glyph: String): MeaningChoiceResultModel
}

data class MeaningChoiceResultModel(
    val status: String,
    val statusColor: Int,
    val actionTone: StudyActionTone,
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

/**
 * Feedback colors for the similar-kanji grid after a wrong pick: the pressed
 * choice turns red and the correct choice turns green. No feedback before an
 * answer or when the grid has no known correct choice (legacy immediate mode).
 */
internal fun feedbackForSimilarChoice(
    glyph: String,
    selectedChoice: String?,
    correctChoice: String?,
): KanjiChoiceFeedback? {
    if (selectedChoice == null || correctChoice == null) {
        return null
    }
    if (glyph == correctChoice) {
        return KanjiChoiceFeedback.CORRECT
    }
    return if (glyph == selectedChoice) {
        KanjiChoiceFeedback.INCORRECT
    } else {
        null
    }
}

data class SimilarChoiceGridModel(
    val choices: List<String>,
    val balanceLastRow: Boolean,
    val onChoice: KanjiChoiceHandler,
    /**
     * When set, every pick shows red/green feedback and freezes the grid after
     * [onChoice] grades it. Navigation remains a separate explicit Continue action.
     * When null (legacy callers/tests), taps fire [onChoice] without feedback state.
     */
    val correctChoice: String? = null,
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

internal data class SimilarChoiceSessionModel(
    val modeLabel: String,
    val question: String,
    val gridModel: SimilarChoiceGridModel,
    val explanationLines: List<SimilarKanjiExplanationLineModel> = emptyList(),
    val feedbackState: StudyAnswerFeedbackState? = null,
    val onContinue: Runnable = Runnable {},
    val continueAction: StudyContinueAction? = null,
    val mnemonic: StudyAnswerMnemonicModel? = null,
)

internal data class SimilarKanjiDifferenceChoiceModel(
    val kanji: String,
    val label: String,
    val onOpenBrowse: Runnable? = null,
)

internal data class SimilarKanjiDifferenceModel(
    val modeLabel: String,
    val title: String,
    val body: String,
    val correctLabel: String,
    val correctKanji: String,
    val choicesTitle: String,
    val choices: List<SimilarKanjiDifferenceChoiceModel>,
    val explanationLines: List<SimilarKanjiExplanationLineModel>,
    val onBack: Runnable,
)

internal data class MeaningChoiceSessionModel(
    val modeLabel: String,
    val question: String,
    val choices: List<String>,
    val answerPanel: StudyAnswerPanelModel,
    val onChoice: KanjiChoiceHandler,
    val resultResolver: MeaningChoiceResultResolver? = null,
    val feedbackState: StudyAnswerFeedbackState? = null,
    val onContinue: Runnable = Runnable {},
    val continueAction: StudyContinueAction? = null,
)
