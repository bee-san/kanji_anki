@file:JvmName("MainActivityGamesCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KanjiGameCopy

@Composable
fun GamesPlayScreen(
    title: String,
    onGames: () -> Unit,
    score: GamesScoreStripModel? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = title,
            actionLabel = KanjiGameCopy.LABEL_GAMES,
            onAction = onGames
        )
        score?.let { GamesScoreStrip(it) }
        content()
    }
}

@Composable
fun GamesMenuScreen(
    model: GamesScreenModel,
    onHome: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeFullWidthHomeButton(
            label = HomeTextCopy.homeLabel(),
            onClick = onHome
        )
        GamesScreen(model)
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
            color = GamesInk,
            fontSize = 34.sp
        )
        Text(
            text = model.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = GamesMuted,
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
                        containerColor = GamesCoral,
                        contentColor = GamesWhite
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
        color = GamesWhite,
        border = BorderStroke(1.dp, GamesPanelBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GamesInk)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = GamesMuted)
        }
    }
}

@Composable
private fun GamesModeCard(model: GamesModeCardModel) {
    val accent = ComposeColor(model.accentColor)
    val availableAccent = if (model.available) accent else GamesGrey
    val fill = if (model.available) accent.copy(alpha = 0.06f) else GamesWhite
    val stroke = availableAccent.copy(alpha = 0.34f)
    val titleColor = if (model.available) GamesInk else GamesMuted
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
                        color = if (model.available) availableAccent else GamesGrey
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
                color = GamesMuted
            )
        }
    }
}
