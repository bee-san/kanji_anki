package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine

internal fun gamesChoiceButtonTestTag(label: String): String = "games-choice-button-$label"

@Composable
fun GamesScoreStrip(model: GamesScoreStripModel) {
    val scoreDescription = model.scoreDescription
    val modifier = Modifier
        .fillMaxWidth()
        .padding(top = 3.dp, bottom = 8.dp)
        .then(
            if (scoreDescription != null) {
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = scoreDescription
                }
            } else {
                Modifier
            }
        )
    val cards = listOf(
        Triple(model.roundLabel, model.roundValue, GamesBlue),
        Triple(model.scoreLabel, model.scoreValue, GamesCoral),
        Triple(model.streakLabel, model.streakValue, GamesTeal),
    )
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { (label, value, accent) ->
                GamesScoreCard(label, value, accent, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { (label, value, accent) ->
                GamesScoreCard(label, value, accent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GamesScoreCard(
    label: String,
    value: String,
    accentColor: ComposeColor,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = GamesScoreShape,
        color = GamesWhite,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                ),
                color = accentColor,
                maxLines = 1,
            )
            Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                ),
                color = GamesInk,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun GamesQuestionCard(
    question: KanjiGameEngine.GameQuestion,
    onChoiceSelected: (String) -> Unit
) {
    val accent = gameModeColor(question.mode)
    val useKanjiTypography = KanjiGameCopy.choiceUsesKanjiTypography(question)
    val promptDescription = KanjiGameCopy.questionPrompt(question)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = promptDescription
            },
        shape = GamesScoreShape,
        color = GamesWhite,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = GamesPillShape,
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.34f))
            ) {
                Text(
                    text = KanjiGameCopy.modeLabel(question.mode),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
            }

            Text(
                text = KanjiGameCopy.questionPrompt(question),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = KanjiGameCopy.promptTextSizeSp(question).sp,
                    lineHeight = KanjiGameCopy.promptTextSizeSp(question).sp,
                    fontWeight = FontWeight.Bold
                ).copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    fontFamily = GamesKanjiFontFamily
                ),
                color = GamesInk
            )

            Text(
                text = KanjiGameCopy.questionPromptDetail(question),
                style = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Normal
                ).copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                color = GamesMuted
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.choices.forEach { choice ->
                    GameChoiceButton(
                        question = question,
                        choice = choice,
                        useKanjiTypography = useKanjiTypography,
                        onChoiceSelected = onChoiceSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun GameChoiceButton(
    question: KanjiGameEngine.GameQuestion,
    choice: String,
    useKanjiTypography: Boolean,
    onChoiceSelected: (String) -> Unit
) {
    val label = KanjiGameCopy.choiceLabel(question, choice).orEmpty()
    val fontFamily = if (useKanjiTypography) GamesKanjiFontFamily else FontFamily.Default
    OutlinedButton(
        onClick = {
            withButtonTrace("games-choice-$label") {
                onChoiceSelected(choice)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (useKanjiTypography) 74.dp else 56.dp)
            .testTag(gamesChoiceButtonTestTag(label))
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = GamesChoiceShape,
        border = BorderStroke(1.dp, GamesButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = GamesWhite,
            contentColor = GamesInk
        )
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = TextStyle(
                fontSize = KanjiGameCopy.choiceTextSizeSp(question).sp,
                lineHeight = KanjiGameCopy.choiceTextSizeSp(question).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            ).copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            color = if (useKanjiTypography) GamesInk else GamesStudyPlum
        )
    }
}

@Composable
internal fun gameModeColor(mode: KanjiGameEngine.GameMode): ComposeColor {
    return gameModeColor(KaniTheme.colors, mode)
}

internal fun gameModeColor(colors: KaniColors, mode: KanjiGameEngine.GameMode): ComposeColor {
    return when (mode) {
        KanjiGameEngine.GameMode.MEANING_POP -> colors.coral
        KanjiGameEngine.GameMode.READING_RUSH -> colors.teal
        KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> colors.blue
        KanjiGameEngine.GameMode.MISS_SWEEP -> colors.coral
    }
}
