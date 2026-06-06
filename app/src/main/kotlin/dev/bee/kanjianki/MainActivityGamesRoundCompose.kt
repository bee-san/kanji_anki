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
import androidx.compose.ui.platform.testTag
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GamesScoreCard(
            label = model.roundLabel,
            value = model.roundValue,
            accentColor = GamesBlue,
            modifier = Modifier.weight(1f)
        )
        GamesScoreCard(
            label = model.scoreLabel,
            value = model.scoreValue,
            accentColor = GamesCoral,
            modifier = Modifier.weight(1f)
        )
        GamesScoreCard(
            label = model.streakLabel,
            value = model.streakValue,
            accentColor = GamesTeal,
            modifier = Modifier.weight(1f)
        )
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
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = GamesInk
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
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
                    text = question.mode.label,
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
                text = question.prompt,
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
                text = question.promptDetail,
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
            .testTag(gamesChoiceButtonTestTag(label)),
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

internal fun gameModeColor(mode: KanjiGameEngine.GameMode): ComposeColor {
    return when (mode) {
        KanjiGameEngine.GameMode.MEANING_POP -> GamesCoral
        KanjiGameEngine.GameMode.READING_RUSH -> GamesTeal
        KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> GamesBlue
    }
}
