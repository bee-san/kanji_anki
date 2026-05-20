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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.viewinterop.AndroidView

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
    val answerPanel: View,
    val onChoice: KanjiChoiceHandler,
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

internal fun meaningKanjiSessionView(activity: MainActivityStudy, model: MeaningChoiceSessionModel): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                MeaningChoiceSessionCard(model)
            }
        }
    }
}

@Composable
fun SimilarChoiceSessionCard(model: SimilarChoiceSessionModel) {
    StudyChoiceSessionSurface(
        modeLabel = model.modeLabel,
        title = model.title,
        taskLabel = model.taskLabel,
        body = model.body,
        reasonLine = model.reasonLine
    ) {
        SimilarChoiceInsetPanel(model)
    }
}

@Composable
fun MeaningChoiceSessionCard(model: MeaningChoiceSessionModel) {
    var answered by remember { mutableStateOf(false) }
    StudyChoiceSessionSurface(
        modeLabel = model.modeLabel,
        title = model.title,
        taskLabel = model.taskLabel,
        body = model.body,
        reasonLine = model.reasonLine
    ) {
        MeaningChoiceInsetPanel(
            model = model,
            answered = answered,
            onAnswered = { glyph ->
                if (!answered) {
                    answered = true
                    model.answerPanel.visibility = View.VISIBLE
                    model.onChoice.onChoice(glyph)
                }
            }
        )
    }
}

@Composable
private fun StudyChoiceSessionSurface(
    modeLabel: String,
    title: String,
    taskLabel: String,
    body: String,
    reasonLine: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = StudyCardFill,
        border = BorderStroke(1.dp, StudyBorder),
        shadowElevation = 5.dp
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            SimilarChoiceModePill(modeLabel)
            StudyChoiceText(title, sizeSp = 30, color = StudyPlum, bold = true)
            StudyChoiceText(taskLabel, sizeSp = 16, color = StudyPinkDark, bold = true)
            StudyChoiceText(body, sizeSp = 15, color = StudyMuted, bold = false)
            if (reasonLine.isNotEmpty()) {
                StudyChoiceText(reasonLine, sizeSp = 14, color = StudyMuted, bold = false)
            }
            content()
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
private fun MeaningChoiceInsetPanel(
    model: MeaningChoiceSessionModel,
    answered: Boolean,
    onAnswered: (String) -> Unit,
) {
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
            KanjiChoiceGrid(
                choices = model.choices,
                balanceLastRow = false,
                enabled = !answered,
                onChoice = onAnswered
            )
            if (answered) {
                AndroidView(
                    factory = { model.answerPanel },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
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
    KanjiChoiceGrid(
        choices = model.choices,
        balanceLastRow = model.balanceLastRow,
        enabled = true,
        onChoice = { glyph -> model.onChoice.onChoice(glyph) }
    )
}

@Composable
private fun KanjiChoiceGrid(
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
