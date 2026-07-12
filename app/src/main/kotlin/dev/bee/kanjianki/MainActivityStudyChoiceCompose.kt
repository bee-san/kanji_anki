@file:JvmName("MainActivityStudyChoiceCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.StudyTextCopy

internal val StudyChoicePlum: Color @Composable get() = KaniTheme.colors.plum
internal val StudyChoiceButtonFill: Color @Composable get() = KaniTheme.colors.studyBg
internal val StudyChoiceBorder: Color @Composable get() = KaniTheme.colors.border
internal val StudyChoiceCorrectFill: Color @Composable get() = KaniTheme.colors.teal
internal val StudyChoiceIncorrectFill: Color @Composable get() = KaniTheme.colors.coral
internal const val SIMILAR_KANJI_DETAILS_TOGGLE_TAG = "similar-kanji-details-toggle"
private val StudyCardFill: Color @Composable get() = KaniTheme.colors.surface
private val StudyPanelFill: Color @Composable get() = KaniTheme.colors.panel
private val StudyPinkDark: Color @Composable get() = KaniTheme.colors.primary
private val StudyMuted: Color @Composable get() = KaniTheme.colors.muted

internal fun studyChoiceFeedbackContentColor(background: Color): Color =
    KaniUiTokens.readableTextColor(background)

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

/**
 * Selection state for the similar-kanji grid. Only a wrong pick is recorded
 * here: it freezes the grid with red/green feedback until the user taps
 * Continue. Correct picks submit immediately and never set state.
 */
class SimilarChoiceSessionState(selectedChoice: String? = null) {
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
internal fun rememberSimilarChoiceSessionState(model: SimilarChoiceSessionModel): SimilarChoiceSessionState {
    return remember(
        model.question,
        model.gridModel.choices,
        model.gridModel.correctChoice,
    ) { SimilarChoiceSessionState() }
}

@Composable
internal fun rememberMeaningChoiceSessionState(model: MeaningChoiceSessionModel): MeaningChoiceSessionState {
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
    detailsExpandedByDefault: Boolean = showInlineChoices,
    onExploreDifferences: Runnable? = null,
    state: SimilarChoiceSessionState = rememberSimilarChoiceSessionState(model),
) {
    var detailsExpanded by rememberSaveable(model.question, showInlineChoices, detailsExpandedByDefault) {
        mutableStateOf(detailsExpandedByDefault)
    }
    StudyChoiceSessionSurface(
        modeLabel = model.modeLabel,
        modifier = modifier,
    ) {
        SimilarChoiceInsetPanel(
            model = model,
            state = state,
            showChoices = showInlineChoices,
            detailsExpanded = detailsExpanded,
            onToggleDetails = { detailsExpanded = !detailsExpanded },
            onExploreDifferences = onExploreDifferences,
        )
    }
}

@Composable
internal fun SimilarKanjiDifferenceScreen(model: SimilarKanjiDifferenceModel, modifier: Modifier = Modifier) {
    StudyChoiceSessionSurface(
        modeLabel = model.modeLabel,
        title = model.title,
        taskLabel = model.correctLabel,
        body = model.body,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            shape = KaniUiTokens.StudyShapeMedium,
            color = StudyPanelFill,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudyChoiceText(model.correctKanji, sizeSp = KaniUiTokens.StudyQuestionTextSizeSp, color = StudyChoicePlum, bold = true)
                SimilarKanjiExplanationPanel(model.explanationLines)
                StudyChoiceText(model.choicesTitle, sizeSp = KaniUiTokens.StudyActionTextSizeSp, color = StudyPinkDark, bold = true)
                model.choices.forEach { choice ->
                    SimilarKanjiDifferenceChoiceRow(choice)
                }
                StudySecondaryActionButton(
                    label = StudyTextCopy.backToStudyLabel(),
                    onClick = { model.onBack.run() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SimilarKanjiDifferenceChoiceRow(choice: SimilarKanjiDifferenceChoiceModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = KaniUiTokens.StudyShapeMedium,
        color = StudyCardFill,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            StudyChoiceText(choice.label, sizeSp = KaniUiTokens.StudyActionTextSizeSp, color = StudyChoicePlum, bold = true)
            if (choice.onOpenBrowse != null) {
                StudySecondaryActionButton(
                    label = StudyTextCopy.openInBrowseLabel(),
                    onClick = { choice.onOpenBrowse.run() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    minHeight = 52.dp,
                )
            }
        }
    }
}

@Composable
internal fun MeaningChoiceSessionCard(
    model: MeaningChoiceSessionModel,
    state: MeaningChoiceSessionState = rememberMeaningChoiceSessionState(model),
    showInlineResultAction: Boolean = true,
    onBrowseAction: Runnable? = null,
) {
    val selectedChoice = state.selectedChoice
    val answered = state.answered
    StudyChoiceSessionSurface(
        modeLabel = model.modeLabel,
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
            onBrowseAction = onBrowseAction,
        )
}
}

@Composable
private fun StudyChoiceSessionSurface(
    modeLabel: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    taskLabel: String? = null,
    body: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = KaniUiTokens.StudyShapeLarge,
        color = StudyCardFill,
        border = BorderStroke(1.dp, StudyChoiceBorder),
        shadowElevation = KaniUiTokens.StudyElevation,
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            StudyModeChip(modeLabel, Modifier.padding(bottom = 14.dp))
            title?.let { StudyChoiceText(it, sizeSp = KaniUiTokens.StudyQuestionTextSizeSp, color = StudyChoicePlum, bold = true) }
            taskLabel?.let { StudyChoiceText(it, sizeSp = KaniUiTokens.StudyBodyTextSizeSp, color = StudyPinkDark, bold = true) }
            body?.let { StudyChoiceText(it, sizeSp = KaniUiTokens.StudyBodyTextSizeSp, color = StudyMuted, bold = false) }
            content()
        }
    }
}

@Composable
private fun SimilarChoiceInsetPanel(
    model: SimilarChoiceSessionModel,
    state: SimilarChoiceSessionState,
    showChoices: Boolean,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onExploreDifferences: Runnable? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        shape = KaniUiTokens.StudyShapeMedium,
        color = StudyPanelFill,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StudyChoiceText(model.question, sizeSp = KaniUiTokens.StudyQuestionTextSizeSp, color = StudyChoicePlum, bold = true)
            if (detailsExpanded) {
                SimilarKanjiDetailsToggleRow(expanded = true, onToggleDetails = onToggleDetails)
                SimilarKanjiExplanationPanel(model.explanationLines)
            } else {
                SimilarKanjiCollapsedSummaryRow(
                    summaryLine = model.explanationLines.firstOrNull(),
                    onToggleDetails = onToggleDetails,
                )
            }
            if (onExploreDifferences != null) {
                StudySecondaryActionButton(
                    label = StudyTextCopy.exploreDifferencesLabel(),
                    onClick = { onExploreDifferences.run() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp)
                )
            }
            if (showChoices) {
                SimilarChoiceGrid(model.gridModel, state)
                SimilarChoiceWrongAnswerBar(model, state)
            }
        }
    }
}

/**
 * After a wrong pick the grid freezes with red/green feedback and this bar
 * appears: it names the correct kanji and waits for an explicit Continue tap
 * before submitting the (failing) answer, instead of instantly advancing.
 */
@Composable
private fun SimilarChoiceWrongAnswerBar(
    model: SimilarChoiceSessionModel,
    state: SimilarChoiceSessionState,
) {
    val selectedChoice = state.selectedChoice ?: return
    val correctChoice = model.gridModel.correctChoice ?: return
    Column(modifier = Modifier.padding(top = 12.dp)) {
        MeaningChoiceResultActionBar(
            status = StudyTextCopy.similarKanjiWrongChoiceResult(correctChoice),
            statusColor = MainActivityBase.CORAL,
            actionTone = StudyActionTone.FAIL,
            onNext = { model.gridModel.onChoice.onChoice(selectedChoice) },
        )
    }
}

@Composable
private fun SimilarKanjiCollapsedSummaryRow(
    summaryLine: SimilarKanjiExplanationLineModel?,
    onToggleDetails: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (summaryLine != null) {
            StudyChoiceText(
                text = "${summaryLine.label}: ${summaryLine.value}",
                sizeSp = if (summaryLine.emphasized) KaniUiTokens.StudyBodyTextSizeSp else KaniUiTokens.StudyCaptionTextSizeSp,
                color = if (summaryLine.emphasized) StudyChoicePlum else StudyMuted,
                bold = summaryLine.emphasized,
                modifier = Modifier.weight(1f),
            )
        } else {
            StudyChoiceText(
                text = StudyTextCopy.similarKanjiDetailsLabel(),
                sizeSp = KaniUiTokens.StudyCaptionTextSizeSp,
                color = StudyMuted,
                bold = false,
                modifier = Modifier.weight(1f),
            )
        }
        SimilarKanjiDetailsButton(expanded = false, onToggleDetails = onToggleDetails)
    }
}

@Composable
private fun SimilarKanjiDetailsToggleRow(
    expanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        SimilarKanjiDetailsButton(expanded = expanded, onToggleDetails = onToggleDetails)
    }
}

@Composable
private fun SimilarKanjiDetailsButton(
    expanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    TextButton(
        onClick = onToggleDetails,
        modifier = Modifier.testTag(SIMILAR_KANJI_DETAILS_TOGGLE_TAG),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (expanded) StudyTextCopy.similarKanjiHideDetailsLabel() else StudyTextCopy.similarKanjiDetailsLabel(),
            color = StudyPinkDark,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
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
        shape = KaniUiTokens.StudyShapeMedium,
        color = StudyCardFill,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            lines.forEach { line ->
                StudyChoiceText(
                    text = "${line.label}: ${line.value}",
                    sizeSp = if (line.emphasized) KaniUiTokens.StudyBodyTextSizeSp else KaniUiTokens.StudyCaptionTextSizeSp,
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
    onBrowseAction: Runnable? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        shape = KaniUiTokens.StudyShapeMedium,
        color = StudyPanelFill,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StudyChoiceText(model.question, sizeSp = KaniUiTokens.StudyQuestionTextSizeSp, color = StudyChoicePlum, bold = true)
            val result = if (answered) {
                selectedChoice?.let { model.resultResolver?.resultForChoice(it) }
            } else {
                null
            }
            // The choice grid stays directly under the question so it does not jump
            // when the answer panel appears; the answer and the Next action render
            // below it, keeping Next as a stable bottom action bar.
            KanjiChoiceGrid(
                choices = model.choices,
                balanceLastRow = false,
                enabled = !answered,
                onChoice = onAnswered,
                feedbackForChoice = { glyph -> feedbackForMeaningChoice(glyph, selectedChoice, result) },
            )
            if (answered) {
                StudyAnswerPanel(
                    model = model.answerPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    onBrowseAction = onBrowseAction,
                )
                if (showInlineResultAction && result != null) {
                    MeaningChoiceResultActionBar(
                        status = result.status,
                        statusColor = result.statusColor,
                        actionTone = result.actionTone,
                        onNext = { model.onChoice.onChoice(selectedChoice ?: return@MeaningChoiceResultActionBar) },
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
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}
