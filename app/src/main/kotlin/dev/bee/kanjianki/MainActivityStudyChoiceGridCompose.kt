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
import androidx.compose.ui.platform.testTag
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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        choices.chunked(2).forEach { rowChoices ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowChoices.forEach { glyph ->
                    SimilarChoiceButton(
                        glyph = glyph,
                        enabled = enabled,
                        onClick = { onChoice(glyph) },
                        modifier = Modifier
                            .weight(1f)
                            .choiceCellSpacing()
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
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .height(SimilarChoiceButtonHeight)
            .testTag(similarChoiceTestTag(glyph)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudyChoiceBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = StudyChoiceButtonFill,
            contentColor = StudyChoicePlum
        )
    ) {
        Text(
            text = glyph,
            modifier = Modifier.fillMaxWidth(),
            color = StudyChoicePlum,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

internal fun similarChoiceTestTag(glyph: String): String {
    return "similar-choice-$glyph"
}
