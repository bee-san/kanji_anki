@file:JvmName("MainActivityGamesCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine

private val Ink = ComposeColor(0xFF2D1635)
private val Muted = ComposeColor(0xFF6C5674)
private val Coral = ComposeColor(0xFFFF4C76)
private val Teal = ComposeColor(0xFF00AEB5)
private val Blue = ComposeColor(0xFF6E5CE6)
private val Grey = ComposeColor(0xFFB2B2BA)
private val White = ComposeColor(0xFFFFFFFF)
private val StudyPlum = ComposeColor(0xFF4B2552)
private val KanjiFontFamily = FontFamily(Font(R.font.kaisei_tokumin_regular))

data class GamesScreenModel(
    val title: String,
    val subtitle: String,
    val emptyTitle: String?,
    val emptyBody: String?,
    val showSyncButton: Boolean,
    val onSync: Runnable,
    val modeCards: List<GamesModeCardModel>,
)

data class GamesModeCardModel(
    val title: String,
    val label: String,
    val body: String,
    val accentColor: Int,
    val available: Boolean,
    val chipLabel: String,
    val onClick: Runnable,
)

data class GamesScoreStripModel(
    val roundLabel: String,
    val roundValue: String,
    val scoreLabel: String,
    val scoreValue: String,
    val streakLabel: String,
    val streakValue: String,
)

internal fun gamesScreenView(activity: MainActivityGames, model: GamesScreenModel): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    GamesScreen(model)
                }
            }
        }
    }
}

internal fun gamesScorePanelView(activity: MainActivityGames, model: GamesScoreStripModel): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    GamesScoreStrip(model)
                }
            }
        }
    }
}

internal fun gamesQuestionCardView(
    activity: MainActivityGames,
    question: KanjiGameEngine.GameQuestion,
    onChoiceSelected: (String) -> Unit
): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    GamesQuestionCard(
                        question = question,
                        onChoiceSelected = onChoiceSelected
                    )
                }
            }
        }
    }
}

@Composable
fun GamesScreen(model: GamesScreenModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Ink,
            fontSize = 34.sp
        )
        Text(
            text = model.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            fontSize = 16.sp
        )

        if (model.modeCards.isEmpty()) {
            GamesEmptyState(
                title = requireNotNull(model.emptyTitle),
                body = requireNotNull(model.emptyBody)
            )
            if (model.showSyncButton) {
                Button(
                    onClick = { model.onSync.run() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Coral,
                        contentColor = White
                    )
                ) {
                    Text(text = KanjiGameCopy.LABEL_SYNC_ANKIDROID)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                model.modeCards.forEach { card ->
                    GamesModeCard(card)
                }
            }
        }
    }
}

@Composable
private fun GamesEmptyState(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = White,
        border = BorderStroke(1.dp, ComposeColor(0xFFEBD6E4))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}

@Composable
private fun GamesModeCard(model: GamesModeCardModel) {
    val accent = ComposeColor(model.accentColor)
    val availableAccent = if (model.available) accent else Grey
    val fill = if (model.available) accent.copy(alpha = 0.06f) else White
    val stroke = availableAccent.copy(alpha = 0.34f)
    val titleColor = if (model.available) Ink else Muted
    val chipFill = availableAccent.copy(alpha = 0.12f)
    val chipStroke = availableAccent.copy(alpha = 0.34f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = model.available, onClick = { model.onClick.run() }),
        shape = RoundedCornerShape(18.dp),
        color = fill,
        border = BorderStroke(1.dp, stroke)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = chipFill,
                    border = BorderStroke(1.dp, chipStroke)
                ) {
                    Text(
                        text = model.chipLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (model.available) availableAccent else Grey
                    )
                }
            }

            Text(
                text = model.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = availableAccent
            )
            Text(
                text = model.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }
    }
}

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
            accentColor = Blue,
            modifier = Modifier.weight(1f)
        )
        GamesScoreCard(
            label = model.scoreLabel,
            value = model.scoreValue,
            accentColor = Coral,
            modifier = Modifier.weight(1f)
        )
        GamesScoreCard(
            label = model.streakLabel,
            value = model.streakValue,
            accentColor = Teal,
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
        shape = RoundedCornerShape(8.dp),
        color = White,
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
                color = Ink
            )
        }
    }
}

@Composable
fun GamesQuestionCard(
    question: KanjiGameEngine.GameQuestion,
    onChoiceSelected: (String) -> Unit
) {
    val accent = ComposeColor(gameModeColor(question.mode))
    val kanjiFont = KanjiFontFamily
    val useKanjiTypography = KanjiGameCopy.choiceUsesKanjiTypography(question)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        shape = RoundedCornerShape(8.dp),
        color = White,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
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
                    fontFamily = kanjiFont
                ),
                color = Ink
            )

            Text(
                text = question.promptDetail,
                style = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Normal
                ).copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                color = Muted
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
    val label = KanjiGameCopy.choiceLabel(question, choice)
    val fontFamily = if (useKanjiTypography) KanjiFontFamily else FontFamily.Default
    OutlinedButton(
        onClick = { onChoiceSelected(choice) },
        modifier = Modifier.fillMaxWidth().heightIn(min = if (useKanjiTypography) 74.dp else 56.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ComposeColor(0xFFEBD6E4)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = White,
            contentColor = Ink
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
            color = if (useKanjiTypography) Ink else StudyPlum
        )
    }
}

private fun gameModeColor(mode: KanjiGameEngine.GameMode): Int {
    return when (mode) {
        KanjiGameEngine.GameMode.MEANING_POP -> 0xFFFF4C76.toInt()
        KanjiGameEngine.GameMode.READING_RUSH -> 0xFF00AEB5.toInt()
        KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> 0xFF6E5CE6.toInt()
    }
}
