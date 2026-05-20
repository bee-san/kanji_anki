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
import androidx.compose.material3.Surface
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
private val StudyCardFill = Color(MainActivityUiSupport.STUDY_CARD)
private val StudyPanelFill = Color(MainActivityUiSupport.STUDY_PANEL)
private val StudyPillFill = Color(MainActivityUiSupport.STUDY_PILL)
private val StudyPinkDark = Color(MainActivityUiSupport.STUDY_PINK_DARK)
private val StudyMuted = Color(MainActivityUiSupport.STUDY_MUTED)
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

data class SimilarChoiceSessionModel(
    val modeLabel: String,
    val title: String,
    val taskLabel: String,
    val body: String,
    val reasonLine: String,
    val question: String,
    val gridModel: SimilarChoiceGridModel,
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

internal fun similarKanjiSessionView(activity: MainActivityStudy, model: SimilarChoiceSessionModel): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                SimilarChoiceSessionCard(model)
            }
        }
    }
}

@Composable
fun SimilarChoiceSessionCard(model: SimilarChoiceSessionModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = StudyCardFill,
        border = BorderStroke(1.dp, StudyBorder),
        shadowElevation = 5.dp
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            SimilarChoiceModePill(model.modeLabel)
            StudyChoiceText(model.title, sizeSp = 30, color = StudyPlum, bold = true)
            StudyChoiceText(model.taskLabel, sizeSp = 16, color = StudyPinkDark, bold = true)
            StudyChoiceText(model.body, sizeSp = 15, color = StudyMuted, bold = false)
            if (model.reasonLine.isNotEmpty()) {
                StudyChoiceText(model.reasonLine, sizeSp = 14, color = StudyMuted, bold = false)
            }
            SimilarChoiceInsetPanel(model)
        }
    }
}

@Composable
private fun SimilarChoiceModePill(label: String) {
    Surface(
        modifier = Modifier.padding(bottom = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = StudyPillFill,
        border = BorderStroke(1.dp, StudyBorder)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = StudyPinkDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun SimilarChoiceInsetPanel(model: SimilarChoiceSessionModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = StudyPanelFill,
        border = BorderStroke(1.dp, StudyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StudyChoiceText(model.question, sizeSp = 22, color = StudyPlum, bold = true)
            SimilarChoiceGrid(model.gridModel)
        }
    }
}

@Composable
private fun StudyChoiceText(
    text: String,
    sizeSp: Int,
    color: Color,
    bold: Boolean,
) {
    Text(
        text = text,
        color = color,
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
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
