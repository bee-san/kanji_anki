@file:JvmName("MainActivityStudyChoiceCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val StudyChoicePlum = Color(MainActivityUiSupport.STUDY_PLUM)
internal val StudyChoiceButtonFill = Color(MainActivityUiSupport.STUDY_BG)
internal val StudyChoiceBorder = Color(MainActivityUiSupport.STUDY_BORDER)
private val StudyCardFill = Color(MainActivityUiSupport.STUDY_CARD)
private val StudyPanelFill = Color(MainActivityUiSupport.STUDY_PANEL)
private val StudyPillFill = Color(MainActivityUiSupport.STUDY_PILL)
private val StudyPinkDark = Color(MainActivityUiSupport.STUDY_PINK_DARK)
private val StudyMuted = Color(MainActivityUiSupport.STUDY_MUTED)

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
fun SimilarChoiceSessionCard(model: SimilarChoiceSessionModel, modifier: Modifier = Modifier) {
    StudyChoiceSessionSurface(
        modeLabel = model.modeLabel,
        title = model.title,
        taskLabel = model.taskLabel,
        body = model.body,
        reasonLine = model.reasonLine,
        modifier = modifier,
    ) {
        SimilarChoiceInsetPanel(model)
    }
}

@Composable
fun MeaningChoiceSessionCard(model: MeaningChoiceSessionModel) {
    var selectedChoice by remember { mutableStateOf<String?>(null) }
    val answered = selectedChoice != null
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
                    selectedChoice = glyph
                    if (model.resultResolver == null) {
                        model.onChoice.onChoice(glyph)
                    }
                }
            },
            selectedChoice = selectedChoice,
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
    modifier: Modifier = Modifier,
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
private fun SimilarChoiceInsetPanel(model: SimilarChoiceSessionModel) {
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
            SimilarChoiceGrid(model.gridModel)
        }
    }
}

@Composable
private fun MeaningChoiceInsetPanel(
    model: MeaningChoiceSessionModel,
    answered: Boolean,
    onAnswered: (String) -> Unit,
    selectedChoice: String?,
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
            KanjiChoiceGrid(
                choices = model.choices,
                balanceLastRow = false,
                enabled = !answered,
                onChoice = onAnswered
            )
            if (answered) {
                StudyAnswerPanel(
                    model = model.answerPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
                val result = selectedChoice?.let { model.resultResolver?.resultForChoice(it) }
                if (result != null) {
                    MeaningChoiceResultActionBar(
                        status = result.status,
                        statusColor = result.statusColor,
                        onNext = { model.onChoice.onChoice(selectedChoice) },
                    )
                }
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
