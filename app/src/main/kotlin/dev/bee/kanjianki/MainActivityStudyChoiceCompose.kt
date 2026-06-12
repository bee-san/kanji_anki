@file:JvmName("MainActivityStudyChoiceCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val StudyChoicePlum: Color @Composable get() = KaniTheme.colors.plum
internal val StudyChoiceButtonFill: Color @Composable get() = KaniTheme.colors.studyBg
internal val StudyChoiceBorder: Color @Composable get() = KaniTheme.colors.border
internal val StudyChoiceCorrectFill: Color @Composable get() = KaniTheme.colors.teal
internal val StudyChoiceIncorrectFill: Color @Composable get() = KaniTheme.colors.coral
internal val StudyChoiceFeedbackContent = Color.White
private val StudyCardFill: Color @Composable get() = KaniTheme.colors.surface
private val StudyPanelFill: Color @Composable get() = KaniTheme.colors.panel
private val StudyPillFill: Color @Composable get() = KaniTheme.colors.pill
private val StudyPinkDark: Color @Composable get() = KaniTheme.colors.primary
private val StudyMuted: Color @Composable get() = KaniTheme.colors.muted

class MeaningChoiceSessionState(selectedChoice: String? = null) {
    var selectedChoice by mutableStateOf(selectedChoice)
        private set

    val answered: Boolean
        get() = selectedChoice != null

    fun select(glyph: String) {
        if (!answered) {
            selectedChoice = glyph
        }
    }
}

@Composable
fun rememberMeaningChoiceSessionState(model: MeaningChoiceSessionModel): MeaningChoiceSessionState {
    return remember(
        model.question,
        model.choices,
        model.answerPanel.glyph,
        model.answerPanel.lines,
    ) { MeaningChoiceSessionState() }
}

@Composable
fun SimilarChoiceSessionCard(
    model: SimilarChoiceSessionModel,
    modifier: Modifier = Modifier,
    showInlineChoices: Boolean = true,
) {
    StudyChoiceSessionSurface(
        modeLabel = model.modeLabel,
        title = model.title,
        taskLabel = model.taskLabel,
        body = model.body,
        modifier = modifier,
    ) {
        SimilarChoiceInsetPanel(model, showInlineChoices)
    }
}

@Composable
internal fun SimilarChoiceActionBar(model: SimilarChoiceGridModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    ) {
        SimilarChoiceGrid(model)
    }
}

@Composable
fun MeaningChoiceSessionCard(
    model: MeaningChoiceSessionModel,
    state: MeaningChoiceSessionState = rememberMeaningChoiceSessionState(model),
    showInlineResultAction: Boolean = true,
) {
    val selectedChoice = state.selectedChoice
    val answered = state.answered
    StudyChoiceSessionSurface(
        modeLabel = model.modeLabel,
        title = model.title,
        taskLabel = model.taskLabel,
        body = model.body,
        showTaskCopy = false,
    ) {
        MeaningChoiceInsetPanel(
            model = model,
            answered = answered,
            onAnswered = { glyph ->
                if (!answered) {
                    state.select(glyph)
                    if (model.resultResolver == null) {
                        model.onChoice.onChoice(glyph)
                    }
                }
            },
            selectedChoice = selectedChoice,
            showInlineResultAction = showInlineResultAction,
        )
    }
}

@Composable
private fun StudyChoiceSessionSurface(
    modeLabel: String,
    title: String,
    taskLabel: String,
    body: String,
    modifier: Modifier = Modifier,
    showTaskCopy: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = StudyCardFill,
        border = BorderStroke(1.dp, StudyChoiceBorder),
        shadowElevation = 5.dp
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            SimilarChoiceModePill(modeLabel)
            StudyChoiceText(title, sizeSp = 30, color = StudyChoicePlum, bold = true)
            if (showTaskCopy) {
                StudyChoiceText(taskLabel, sizeSp = 16, color = StudyPinkDark, bold = true)
                StudyChoiceText(body, sizeSp = 15, color = StudyMuted, bold = false)
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
        border = BorderStroke(1.dp, StudyChoiceBorder)
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
private fun SimilarChoiceInsetPanel(model: SimilarChoiceSessionModel, showChoices: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = StudyPanelFill,
        border = BorderStroke(1.dp, StudyChoiceBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StudyChoiceText(model.question, sizeSp = 22, color = StudyChoicePlum, bold = true)
            if (model.reasonLine.isNotBlank()) {
                StudyChoiceText(model.reasonLine, sizeSp = 14, color = StudyMuted, bold = false)
            }
            SimilarKanjiExplanationPanel(model.explanationLines)
            if (showChoices) {
                SimilarChoiceGrid(model.gridModel)
            }
        }
    }
}

@Composable
private fun SimilarKanjiExplanationPanel(lines: List<SimilarKanjiExplanationLineModel>) {
    if (lines.isEmpty()) {
        return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = StudyCardFill,
        border = BorderStroke(1.dp, StudyChoiceBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            lines.forEach { line ->
                StudyChoiceText(
                    text = "${line.label}: ${line.value}",
                    sizeSp = if (line.emphasized) 15 else 14,
                    color = if (line.emphasized) StudyChoicePlum else StudyMuted,
                    bold = line.emphasized,
                )
            }
        }
    }
}

@Composable
private fun MeaningChoiceInsetPanel(
    model: MeaningChoiceSessionModel,
    answered: Boolean,
    onAnswered: (String) -> Unit,
    selectedChoice: String?,
    showInlineResultAction: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = StudyPanelFill,
        border = BorderStroke(1.dp, StudyChoiceBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StudyChoiceText(model.question, sizeSp = 22, color = StudyChoicePlum, bold = true)
            val result = if (answered) {
                selectedChoice?.let { model.resultResolver?.resultForChoice(it) }
            } else {
                null
            }
            if (answered) {
                StudyAnswerPanel(
                    model = model.answerPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
                if (showInlineResultAction && result != null) {
                    MeaningChoiceResultActionBar(
                        status = result.status,
                        statusColor = result.statusColor,
                        actionLabel = result.actionLabel,
                        onNext = { model.onChoice.onChoice(selectedChoice ?: return@MeaningChoiceResultActionBar) },
                    )
                }
            }
            KanjiChoiceGrid(
                choices = model.choices,
                balanceLastRow = false,
                enabled = !answered,
                onChoice = onAnswered,
                feedbackForChoice = { glyph -> feedbackForMeaningChoice(glyph, selectedChoice, result) },
            )
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
