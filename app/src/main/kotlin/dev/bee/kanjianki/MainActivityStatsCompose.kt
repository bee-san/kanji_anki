@file:JvmName("MainActivityStatsCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
internal const val STATS_INK_COLOR = 0xFF2D1635.toInt()
internal const val STATS_MUTED_COLOR = 0xFF6C5674.toInt()
internal const val STATS_CORAL_COLOR = 0xFFFF4C76.toInt()
internal const val STATS_TEAL_COLOR = 0xFF00AEB5.toInt()
internal const val STATS_GOLD_COLOR = 0xFFFFD640.toInt()
internal const val STATS_BLUE_COLOR = 0xFF6E5CE6.toInt()
internal const val STATS_VERDICT_WORKING_FILL = 0xFFEEFCFA.toInt()
internal const val STATS_VERDICT_LADDER_FILL = 0xFFFFFAE2.toInt()
internal const val STATS_VERDICT_IDLE_FILL = 0xFFF6F6F8.toInt()
internal const val STATS_WHITE_COLOR = 0xFFFFFFFF.toInt()

@Composable
fun StatsRouteScreen(
    model: StatsScreenModel,
    onHome: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeFullWidthHomeButton(
            label = HomeTextCopy.homeLabel(),
            onClick = onHome
        )
        StatsScreen(model = model)
    }
}

@Composable
fun StatsScreen(model: StatsScreenModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 380.dp
        val titleSpacing = 7.dp
        val introSpacing = 10.dp
        val sectionSpacing = 14.dp
        val bottomPadding = if (compact) 24.dp else 32.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = model.title,
                style = statsTextStyle(sizeSp = 34, bold = true, compact = compact),
                color = ComposeColor(STATS_INK_COLOR)
            )
            Spacer(modifier = Modifier.height(titleSpacing))
            StatsCard(model.verdict, compact = compact)
            Spacer(modifier = Modifier.height(titleSpacing))
            Text(
                text = model.intro,
                style = statsTextStyle(sizeSp = 16, bold = false, compact = compact),
                color = ComposeColor(STATS_MUTED_COLOR)
            )
            Spacer(modifier = Modifier.height(introSpacing))
            Column(verticalArrangement = Arrangement.spacedBy(sectionSpacing)) {
                model.sections.forEach { card ->
                    StatsCard(card, compact = compact)
                }
            }
        }
    }
}

@Composable
private fun StatsCard(model: StatsCardModel, compact: Boolean = false) {
    if (model.emptyState) {
        HomeEmptyState(
            title = model.title,
            body = model.body.orEmpty()
        )
        return
    }
    val cardPadding = 14.dp
    val lineSpacing = 4.dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.PanelShape,
        color = ComposeColor(model.fillColor),
        border = BorderStroke(1.dp, ComposeColor(model.strokeColor))
    ) {
        Column(
            modifier = Modifier.padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(lineSpacing)
        ) {
            Text(
                text = model.title,
                style = statsTextStyle(sizeSp = model.titleSizeSp, bold = true, compact = compact),
                color = ComposeColor(model.titleColor)
            )
            model.summary?.let { summary ->
                Text(
                    text = summary,
                    style = statsTextStyle(sizeSp = model.summarySizeSp, bold = true, compact = compact),
                    color = ComposeColor(model.summaryColor)
                )
            }
            model.body?.let { body ->
                Text(
                    text = body,
                    style = statsTextStyle(sizeSp = model.bodySizeSp, bold = false, compact = compact),
                    color = ComposeColor(model.bodyColor)
                )
            }
            model.lines.forEach { line ->
                Text(
                    text = line.text,
                    style = statsTextStyle(sizeSp = line.sizeSp, bold = line.bold, compact = compact),
                    color = ComposeColor(line.color)
                )
            }
        }
    }
}

private fun statsTextStyle(sizeSp: Int, bold: Boolean, compact: Boolean): TextStyle {
    val scale = if (compact) 0.94f else 1f
    return TextStyle(
        fontSize = (sizeSp * scale).sp,
        lineHeight = (sizeSp * scale).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}
