@file:JvmName("MainActivityStudyChoiceCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StudyPlum = Color(MainActivityUiSupport.STUDY_PLUM)
private val StudyButtonFill = Color(MainActivityUiSupport.STUDY_BG)
private val StudyBorder = Color(MainActivityUiSupport.STUDY_BORDER)
internal val SimilarChoiceCellHorizontalPadding = 4.dp
internal val SimilarChoiceCellTopPadding = 8.dp
internal val SimilarChoiceButtonHeight = 82.dp

fun interface SimilarChoiceHandler {
    fun onChoice(glyph: String)
}

data class SimilarChoiceGridModel(
    val choices: List<String>,
    val balanceLastRow: Boolean,
    val onChoice: SimilarChoiceHandler,
)

internal fun similarKanjiGridView(activity: MainActivityStudy, model: SimilarChoiceGridModel): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                SimilarChoiceGrid(model)
            }
        }
    }
}

@Composable
fun SimilarChoiceGrid(model: SimilarChoiceGridModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        model.choices.chunked(2).forEach { rowChoices ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowChoices.forEach { glyph ->
                    SimilarChoiceButton(
                        glyph = glyph,
                        onClick = { model.onChoice.onChoice(glyph) },
                        modifier = Modifier
                            .weight(1f)
                            .choiceCellSpacing()
                    )
                }
                if (model.balanceLastRow && rowChoices.size == 1) {
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(SimilarChoiceButtonHeight)
            .testTag(similarChoiceTestTag(glyph)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudyBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = StudyButtonFill,
            contentColor = StudyPlum
        )
    ) {
        Text(
            text = glyph,
            modifier = Modifier.fillMaxWidth(),
            color = StudyPlum,
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
