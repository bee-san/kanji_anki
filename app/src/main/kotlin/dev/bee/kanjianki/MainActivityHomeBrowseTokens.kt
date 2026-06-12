package dev.bee.kanjianki

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val BrowseInk: ComposeColor @Composable get() = KaniTheme.colors.ink
internal val BrowseMuted: ComposeColor @Composable get() = KaniTheme.colors.muted
internal val BrowseTeal: ComposeColor @Composable get() = KaniTheme.colors.teal
internal val BrowseCoral: ComposeColor @Composable get() = KaniTheme.colors.coral
internal val BrowseGold: ComposeColor @Composable get() = KaniTheme.colors.gold
internal val BrowseWhite: ComposeColor @Composable get() = KaniTheme.colors.surface
internal val BrowseBlush: ComposeColor @Composable get() = KaniTheme.colors.pill
internal val BrowsePanelShape = RoundedCornerShape(18.dp)
internal val BrowseCardShape = RoundedCornerShape(8.dp)

@Composable
internal fun browseSoftenedColor(color: ComposeColor): ComposeColor {
    val colors = KaniTheme.colors
    if (colors.isDark) {
        return color.copy(alpha = 0.16f)
    }
    return when (color) {
        colors.coral -> ComposeColor(0xFFFFEBF3)
        colors.teal -> ComposeColor(0xFFE6FAFB)
        colors.gold -> ComposeColor(0xFFFFF7DC)
        colors.blue, ComposeColor(0xFFC9B9FF) -> ComposeColor(0xFFF2EEFF)
        else -> ComposeColor(0xFFF8EEF5)
    }
}

internal fun browseNoFontPaddingStyle(sizeSp: Int): TextStyle {
    return TextStyle(
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Bold,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
}
