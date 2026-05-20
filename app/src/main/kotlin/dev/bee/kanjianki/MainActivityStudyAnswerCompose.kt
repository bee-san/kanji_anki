@file:JvmName("MainActivityStudyAnswerCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTaskCopy

private val StudyAnswerPlum = Color(MainActivityUiSupport.STUDY_PLUM)
private val StudyAnswerMuted = Color(MainActivityUiSupport.MUTED)
private val StudyAnswerPink = Color(MainActivityUiSupport.STUDY_PINK_DARK)
private val StudyAnswerPanelFill = Color(MainActivityUiSupport.STUDY_PANEL)
private val StudyAnswerBorder = Color(MainActivityUiSupport.STUDY_BORDER)

data class StudyAnswerPanelModel(
    val title: String,
    val glyph: String,
    val glyphSizeSp: Int,
    val lines: List<StudyAnswerLineModel>,
    val helperText: String?,
)

data class StudyAnswerLineModel(
    val text: String,
    val color: Color,
    val sizeSp: Int,
    val bold: Boolean,
)

internal fun flashcardAnswerPanelView(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession
): View {
    return studyAnswerPanelView(activity, answerPanelModel(activity, session, "Answer", 76, null))
}

internal fun learningPanelView(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession
): View {
    return studyAnswerPanelView(activity, answerPanelModel(activity, session, "Reference", 72, "Trace it below, then check."))
}

private fun studyAnswerPanelView(activity: MainActivityStudy, model: StudyAnswerPanelModel): View {
    return ComposeView(activity).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, activity.dp(12), 0, activity.dp(10))
        }
        setContent {
            MaterialTheme {
                StudyAnswerPanel(model)
            }
        }
    }
}

private fun answerPanelModel(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession,
    title: String,
    glyphSizeSp: Int,
    helperText: String?,
): StudyAnswerPanelModel {
    val lines = if (session.row != null) {
        StudyCueTexts.answerLines(
            activity.currentDictionaryLookup(),
            session,
            activity.exampleForSession(session),
            StudyTaskCopy.isWordReadingTask(session)
        ).mapIndexed { index, line ->
            StudyAnswerLineModel(
                text = line,
                color = if (line.startsWith("Reading:")) StudyAnswerPink else StudyAnswerPlum,
                sizeSp = if (index == 0) 17 else 15,
                bold = true
            )
        }
    } else {
        listOf(
            StudyAnswerLineModel(
                text = session.prompt,
                color = StudyAnswerMuted,
                sizeSp = 15,
                bold = false
            )
        )
    }
    return StudyAnswerPanelModel(
        title = title,
        glyph = session.item.kanji,
        glyphSizeSp = glyphSizeSp,
        lines = lines,
        helperText = helperText
    )
}

@Composable
fun StudyAnswerPanel(model: StudyAnswerPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = StudyAnswerPanelFill,
        border = BorderStroke(1.dp, StudyAnswerBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = model.title,
                color = StudyAnswerPlum,
                style = studyAnswerTextStyle(19),
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(118.dp)
                        .height(108.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = model.glyph,
                        color = StudyAnswerPlum,
                        style = studyAnswerTextStyle(model.glyphSizeSp),
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    model.lines.forEach { line ->
                        Text(
                            text = line.text,
                            color = line.color,
                            style = studyAnswerTextStyle(line.sizeSp),
                            fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            model.helperText?.let { helper ->
                Text(
                    text = helper,
                    color = StudyAnswerMuted,
                    style = studyAnswerTextStyle(13)
                )
            }
        }
    }
}

private fun studyAnswerTextStyle(sizeSp: Int): TextStyle {
    val size = sizeSp.sp
    return TextStyle(
        fontSize = size,
        lineHeight = size * 1.05f
    )
}
