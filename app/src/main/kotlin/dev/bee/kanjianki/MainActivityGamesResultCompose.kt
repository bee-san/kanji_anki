package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor
import dev.bee.kanjianki.core.KanjiGameCopy

internal fun gamesResultPrimaryButtonTestTag(label: String): String = "games-result-primary-button-$label"

internal fun gamesResultGamesButtonTestTag(): String = "games-result-games-button"

@Composable
fun GamesUnavailableCard(model: GamesUnavailableModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GamesPanelShape,
        color = KaniTheme.colors.gold.copy(alpha = if (KaniTheme.colors.isDark) 0.18f else 0.28f),
        border = BorderStroke(1.dp, KaniTheme.colors.gold)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = model.title,
                color = GamesInk,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = GamesInk,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun GamesResultCard(model: GamesResultModel) {
    val accent = kaniColor(model.titleColor)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GamesPanelShape,
        color = GamesWhite,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            model.finalScore?.let { line ->
                Text(
                    text = line,
                    color = GamesInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            model.accuracy?.let { line ->
                Text(
                    text = line,
                    color = GamesMuted,
                    fontSize = 15.sp
                )
            }
            model.answer?.let { line ->
                Text(
                    text = line,
                    color = GamesInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            model.selectedAnswer?.let { line ->
                Text(
                    text = line,
                    color = GamesMuted,
                    fontSize = 16.sp
                )
            }
            model.explanation?.let { line ->
                Text(
                    text = line,
                    color = GamesMuted,
                    fontSize = 15.sp
                )
            }
            Button(
                onClick = {
                    withButtonTrace("games-result-primary") {
                        model.onPrimary.run()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .testTag(gamesResultPrimaryButtonTestTag(model.primaryLabel)),
                shape = GamesButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = kaniColor(model.primaryColor),
                    contentColor = GamesWhite
                )
            ) {
                Text(
                    text = model.primaryLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = {
                    withButtonTrace("games-result-open") {
                        model.onGames.run()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag(gamesResultGamesButtonTestTag()),
                shape = GamesButtonShape,
                border = BorderStroke(1.dp, GamesButtonBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = GamesWhite,
                    contentColor = GamesInk
                )
            ) {
                Text(
                    text = KanjiGameCopy.gamesLabel(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
