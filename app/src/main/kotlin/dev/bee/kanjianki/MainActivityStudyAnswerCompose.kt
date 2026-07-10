@file:JvmName("MainActivityStudyAnswerCompose")

package dev.bee.kanjianki

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyCuePolicy
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy

private val StudyAnswerPlum: Color @Composable get() = KaniTheme.colors.plum
private val StudyAnswerMuted: Color @Composable get() = KaniTheme.colors.muted
private val StudyAnswerPanelFill: Color @Composable get() = KaniTheme.colors.panel
private val StudyAnswerBorder: Color @Composable get() = KaniTheme.colors.border

internal fun flashcardAnswerPanelModel(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession
): StudyAnswerPanelModel {
    return answerPanelModel(
        activity,
        session,
        StudyTextCopy.answerLabel(),
        KaniUiTokens.StudyHeroTextSizeSp,
        null,
    )
}

internal fun meaningChoiceAnswerPanelModel(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession
): StudyAnswerPanelModel {
    return answerPanelModel(
        activity,
        session,
        StudyTextCopy.answerLabel(),
        KaniUiTokens.StudyHeroTextSizeSp,
        null,
    ) { example ->
        StudyCuePolicy.meaningChoiceAnswerLines(
            activity.currentDictionaryLookup(),
            session,
            example,
        )
    }
}

internal fun learningPanelModel(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession
): StudyAnswerPanelModel {
    return answerPanelModel(
        activity,
        session,
        StudyTextCopy.referenceLabel(),
        KaniUiTokens.StudyHeroTextSizeSp,
        StudyTextCopy.writingReferenceHelper(),
    )
}

private fun answerPanelModel(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession,
    title: String,
    glyphSizeSp: Int,
    helperText: String?,
    answerLines: ((RecordsImportModels.Example?) -> List<String>)? = null,
): StudyAnswerPanelModel {
    val lines = if (session.row != null) {
        val example = activity.exampleForSession(session)
        val textLines = answerLines?.invoke(example) ?: StudyCueTexts.answerLines(
            activity.currentDictionaryLookup(),
            session,
            example,
            StudyTaskCopy.isWordReadingTask(session) || StudyTaskCopy.isSentenceReadingTask(session)
        )
        textLines.mapIndexed { index, line ->
            StudyAnswerLineModel(
                text = line,
                color = if (StudyCuePolicy.isReadingLine(line)) {
                    MainActivityUiSupport.STUDY_PINK_DARK
                } else {
                    MainActivityUiSupport.STUDY_PLUM
                },
                sizeSp = if (index == 0) {
                    KaniUiTokens.StudyActionTextSizeSp
                } else {
                    KaniUiTokens.StudyBodyTextSizeSp
                },
                bold = true
            )
        }
    } else {
        listOf(
            StudyAnswerLineModel(
                text = session.prompt,
                color = MainActivityUiSupport.STUDY_MUTED,
                sizeSp = KaniUiTokens.StudyBodyTextSizeSp,
                bold = false
            )
        )
    }
    return StudyAnswerPanelModel(
        title = title,
        glyph = session.item?.kanji ?: "",
        glyphSizeSp = glyphSizeSp,
        lines = lines,
        helperText = helperText,
        stateKey = studyAnswerPanelStateKey(session),
        kanjiDetails = studyAnswerKanjiDetailsModel(activity, session),
    )
}

@Composable
internal fun StudyAnswerPanel(
    model: StudyAnswerPanelModel,
    modifier: Modifier = Modifier,
    onAnkiTapAction: ((StudyAnswerAnkiTapActionModel) -> Unit)? = null,
    initialExpandedSectionLabel: String? = null,
    initialUsedInAnkiShowAll: Boolean = false,
    onBrowseAction: Runnable? = null,
) {
    val panelStateKey = studyAnswerPanelStateKey(model)
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = KaniUiTokens.StudyShapeMedium,
        color = StudyAnswerPanelFill,
        border = BorderStroke(1.dp, StudyAnswerBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = model.title,
                color = StudyAnswerPlum,
                style = studyAnswerTextStyle(KaniUiTokens.StudyHeadingTextSizeSp),
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(118.dp)
                        .heightIn(min = 108.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = model.glyph,
                        color = StudyAnswerPlum,
                        style = studyAnswerTextStyle(model.glyphSizeSp),
                        fontWeight = FontWeight.Bold
                    )
                }
                StudyAnswerLines(
                    lines = model.lines,
                    modifier = Modifier.weight(1f),
                )
            }
            model.helperText?.let { helper ->
                Text(
                    text = helper,
                    color = StudyAnswerMuted,
                    style = studyAnswerTextStyle(KaniUiTokens.StudyCaptionTextSizeSp)
                )
            }
            model.kanjiDetails?.let { details ->
                Spacer(modifier = Modifier.height(12.dp))
                StudyAnswerKanjiDetailsStack(
                    details = details,
                    panelStateKey = panelStateKey,
                    onAnkiTapAction = onAnkiTapAction,
                    initialExpandedSectionLabel = initialExpandedSectionLabel,
                    initialUsedInAnkiShowAll = initialUsedInAnkiShowAll,
                    onBrowseAction = onBrowseAction,
                )
            }
        }
    }
}

/**
 * Revealed flashcard content that sits directly below the persistent kanji hero.
 * It deliberately omits the answer title, duplicate glyph, and an additional
 * panel surface so reveal keeps one visual anchor.
 */
@Composable
internal fun StudyFlashcardAnswerContent(
    model: StudyAnswerPanelModel,
    modifier: Modifier = Modifier,
    onBrowseAction: Runnable? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        StudyAnswerLines(lines = model.lines)
        model.helperText?.takeIf { it.isNotBlank() }?.let { helper ->
            Text(
                text = helper,
                color = StudyAnswerMuted,
                style = studyAnswerTextStyle(KaniUiTokens.StudyCaptionTextSizeSp),
            )
        }
        model.kanjiDetails?.let { details ->
            StudyAnswerKanjiDetailsStack(
                details = details,
                panelStateKey = studyAnswerPanelStateKey(model),
                onBrowseAction = onBrowseAction,
            )
        }
    }
}

@Composable
internal fun StudyAnswerLines(
    lines: List<StudyAnswerLineModel>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        lines.forEach { line ->
            Text(
                text = line.text,
                color = kaniColor(line.color),
                style = studyAnswerTextStyle(line.sizeSp),
                fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal,
            )
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
