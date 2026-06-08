@file:JvmName("MainActivityStudyChoiceGridCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val SimilarChoiceCellHorizontalPadding = 4.dp
internal val SimilarChoiceCellTopPadding = 8.dp
internal val SimilarChoiceButtonHeight = 82.dp

@Composable
fun SimilarChoiceGrid(model: SimilarChoiceGridModel) {
    KanjiChoiceGrid(
        choices = model.choices,
        balanceLastRow = model.balanceLastRow,
        enabled = true,
        onChoice = { glyph -> model.onChoice.onChoice(glyph) }
    )
}

@Composable
internal fun KanjiChoiceGrid(
    choices: List<String>,
    balanceLastRow: Boolean,
    enabled: Boolean,
    onChoice: (String) -> Unit,
    feedbackForChoice: (String) -> KanjiChoiceFeedback? = { null },
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        choices.forEachTwoColumnRowIndexed { _, first, second ->
            Row(modifier = Modifier.fillMaxWidth()) {
                SimilarChoiceButton(
                    glyph = first,
                    enabled = enabled,
                    feedback = feedbackForChoice(first),
                    onClick = { onChoice(first) },
                    modifier = Modifier
                        .weight(1f)
                        .choiceCellSpacing()
                )
                if (second != null) {
                    SimilarChoiceButton(
                        glyph = second,
                        enabled = enabled,
                        feedback = feedbackForChoice(second),
                        onClick = { onChoice(second) },
                        modifier = Modifier
                            .weight(1f)
                            .choiceCellSpacing()
                    )
                } else if (balanceLastRow) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .choiceCellSpacing()
                            .height(SimilarChoiceButtonHeight)
                    )
                }
            }
        }
    }
}

private fun Modifier.choiceCellSpacing(): Modifier {
    return padding(
        start = SimilarChoiceCellHorizontalPadding,
        top = SimilarChoiceCellTopPadding,
        end = SimilarChoiceCellHorizontalPadding
    )
}

@Composable
private fun SimilarChoiceButton(
    glyph: String,
    enabled: Boolean,
    feedback: KanjiChoiceFeedback?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedbackColor = feedback?.choiceFeedbackColor()
    val contentColor = if (feedback == null) StudyChoicePlum else StudyChoiceFeedbackContent
    OutlinedButton(
        enabled = enabled,
        onClick = { withButtonTrace("study-choice-$glyph") { onClick() } },
        modifier = modifier
            .height(SimilarChoiceButtonHeight)
            .testTag(similarChoiceTestTag(glyph))
            .choiceFeedbackSemantics(feedback),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, feedbackColor ?: StudyChoiceBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = feedbackColor ?: StudyChoiceButtonFill,
            contentColor = contentColor,
            disabledContainerColor = feedbackColor ?: StudyChoiceButtonFill,
            disabledContentColor = contentColor,
        )
    ) {
        Text(
            text = glyph,
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

private fun KanjiChoiceFeedback.choiceFeedbackColor(): Color {
    return when (this) {
        KanjiChoiceFeedback.CORRECT -> StudyChoiceCorrectFill
        KanjiChoiceFeedback.INCORRECT -> StudyChoiceIncorrectFill
    }
}

private fun Modifier.choiceFeedbackSemantics(feedback: KanjiChoiceFeedback?): Modifier {
    if (feedback == null) {
        return this
    }
    return semantics {
        stateDescription = when (feedback) {
            KanjiChoiceFeedback.CORRECT -> "Correct answer"
            KanjiChoiceFeedback.INCORRECT -> "Incorrect answer"
        }
    }
}

internal fun similarChoiceTestTag(glyph: String): String {
    return "similar-choice-$glyph"
}
