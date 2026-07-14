@file:JvmName("MainActivityStudyChoiceGridCompose")

package dev.bee.kanjianki

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.bee.kanjianki.core.HomeTextCopy
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.StudyTextCopy

internal val SimilarChoiceCellHorizontalPadding = 4.dp
internal val SimilarChoiceCellTopPadding = 8.dp
internal val SimilarChoiceButtonHeight = 82.dp

@Composable
fun SimilarChoiceGrid(model: SimilarChoiceGridModel, state: SimilarChoiceSessionState? = null) {
    val correctChoice = model.correctChoice
    val selectedChoice = state?.selectedChoice
    val answered = correctChoice != null && selectedChoice != null
    KanjiChoiceGrid(
        choices = model.choices,
        balanceLastRow = model.balanceLastRow,
        enabled = !answered,
        onChoice = { glyph ->
            when {
                correctChoice == null || state == null -> model.onChoice.onChoice(glyph)
                else -> {
                    if (model.onChoice.onChoice(glyph)) {
                        state.select(glyph)
                    }
                }
            }
        },
        feedbackForChoice = { glyph -> feedbackForSimilarChoice(glyph, selectedChoice, correctChoice) },
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
    val choiceRows = remember(choices) {
        choices.chunked(2)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        choiceRows.forEachIndexed { rowIndex, rowChoices ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowChoices.forEachIndexed { columnIndex, glyph ->
                    val cellModifier = if (rowChoices.size == 1 && !balanceLastRow) {
                        Modifier.fillMaxWidth().choiceCellSpacing()
                    } else {
                        Modifier.weight(1f).choiceCellSpacing()
                    }
                    SimilarChoiceButton(
                        glyph = glyph,
                        positionDescription = HomeTextCopy.choicePositionDescription(
                            index = rowIndex * 2 + columnIndex,
                            total = choices.size,
                        ),
                        enabled = enabled,
                        feedback = feedbackForChoice(glyph),
                        onClick = { onChoice(glyph) },
                        modifier = cellModifier
                    )
                }
                if (balanceLastRow && rowChoices.size == 1) {
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
    positionDescription: String,
    enabled: Boolean,
    feedback: KanjiChoiceFeedback?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedbackColor = feedback?.choiceFeedbackColor()
    val targetContainerColor = feedbackColor ?: StudyChoiceButtonFill
    val contentColor = if (feedback == null) {
        StudyChoicePlum
    } else {
        studyChoiceFeedbackContentColor(targetContainerColor)
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        label = "study-choice-fill"
    )
    val borderColor by animateColorAsState(
        targetValue = feedbackColor ?: StudyChoiceBorder,
        label = "study-choice-border"
    )
    ChoiceFeedbackHaptics(feedback)
    OutlinedButton(
        enabled = enabled,
        onClick = { withButtonTrace("study-choice-$glyph") { onClick() } },
        modifier = modifier
            .heightIn(min = SimilarChoiceButtonHeight)
            .testTag(similarChoiceTestTag(glyph))
            .semantics {
                role = Role.Button
                contentDescription = "$glyph, $positionDescription"
            }
            .choiceFeedbackSemantics(feedback),
        shape = KaniUiTokens.StudyShapeMedium,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        )
    ) {
        Text(
            text = choiceButtonText(glyph, feedback),
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            fontSize = KaniUiTokens.StudyQuestionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

/**
 * Adds a check or cross mark next to the kanji so answer feedback does not
 * rely on color alone.
 */
internal fun choiceButtonText(glyph: String, feedback: KanjiChoiceFeedback?): String {
    return when (feedback) {
        KanjiChoiceFeedback.CORRECT -> "$glyph ✓"
        KanjiChoiceFeedback.INCORRECT -> "$glyph ✕"
        null -> glyph
    }
}

@Composable
private fun ChoiceFeedbackHaptics(feedback: KanjiChoiceFeedback?) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(feedback) {
        when (feedback) {
            KanjiChoiceFeedback.CORRECT -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            KanjiChoiceFeedback.INCORRECT -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            null -> Unit
        }
    }
}

@Composable
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
            KanjiChoiceFeedback.CORRECT -> StudyTextCopy.choiceCorrectStateDescription()
            KanjiChoiceFeedback.INCORRECT -> StudyTextCopy.choiceIncorrectStateDescription()
        }
    }
}

internal fun similarChoiceTestTag(glyph: String): String {
    return "similar-choice-$glyph"
}
