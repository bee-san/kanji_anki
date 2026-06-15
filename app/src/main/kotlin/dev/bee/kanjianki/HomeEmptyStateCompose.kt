@file:JvmName("MainActivityHomeEmptyStateCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class HomeEmptyStateStyle {
    LegacyBand,
    Panel,
}

internal fun homeEmptyStateTestTag(title: String): String {
    return "home-empty-state-" + HOME_EMPTY_STATE_TAG_PATTERN
        .replace(title, "-")
        .trim('-')
        .lowercase()
}

@Composable
fun HomeEmptyState(
    title: String,
    body: String,
    style: HomeEmptyStateStyle = HomeEmptyStateStyle.Panel,
) {
    val colors = homeEmptyStateColors(style)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(homeEmptyStateTestTag(title)),
        shape = RoundedCornerShape(colors.cornerRadius),
        color = colors.fill,
        border = BorderStroke(1.dp, colors.stroke)
    ) {
        Column(
            modifier = Modifier.padding(colors.padding),
            verticalArrangement = Arrangement.spacedBy(colors.textSpacing)
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = colors.titleColor,
                fontSize = colors.titleSize,
                fontWeight = FontWeight.Bold,
                style = colors.titleStyle
            )
            Text(
                text = body,
                color = colors.bodyColor,
                fontSize = colors.bodySize,
                style = colors.bodyStyle
            )
        }
    }
}

private data class HomeEmptyStateColors(
    val fill: ComposeColor,
    val stroke: ComposeColor,
    val titleColor: ComposeColor,
    val bodyColor: ComposeColor,
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val cornerRadius: Dp,
    val padding: Dp,
    val textSpacing: Dp,
    val titleStyle: TextStyle,
    val bodyStyle: TextStyle,
)

@Composable
private fun homeEmptyStateColors(style: HomeEmptyStateStyle): HomeEmptyStateColors {
    return when (style) {
        HomeEmptyStateStyle.LegacyBand -> HomeEmptyStateColors(
            fill = KaniTheme.colors.gold,
            stroke = KaniTheme.colors.gold,
            titleColor = KaniTheme.colors.ink,
            bodyColor = KaniTheme.colors.ink,
            titleSize = 24.sp,
            bodySize = 16.sp,
            cornerRadius = 8.dp,
            padding = 20.dp,
            textSpacing = 0.dp,
            titleStyle = TextStyle(
                lineHeight = 25.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            bodyStyle = TextStyle(
                lineHeight = 17.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            )
        )

        HomeEmptyStateStyle.Panel -> HomeEmptyStateColors(
            fill = KaniTheme.colors.surface,
            stroke = KaniTheme.colors.borderSoft,
            titleColor = KaniTheme.colors.ink,
            bodyColor = KaniTheme.colors.greyText,
            titleSize = TextUnit.Unspecified,
            bodySize = TextUnit.Unspecified,
            cornerRadius = 18.dp,
            padding = 16.dp,
            textSpacing = 8.dp,
            titleStyle = MaterialTheme.typography.titleMedium,
            bodyStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

private val HOME_EMPTY_STATE_TAG_PATTERN = Regex("[^A-Za-z0-9]+")
