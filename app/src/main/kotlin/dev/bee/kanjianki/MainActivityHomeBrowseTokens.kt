package dev.bee.kanjianki

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val BrowseInk = ComposeColor(0xFF2D1635)
internal val BrowseMuted = ComposeColor(0xFF6C5674)
internal val BrowseTeal = ComposeColor(0xFF00AEB5)
internal val BrowseCoral = ComposeColor(0xFFFF4C76)
internal val BrowseGold = ComposeColor(0xFFFFD640)
internal val BrowseWhite = ComposeColor(0xFFFFFFFF)
internal val BrowseBlush = ComposeColor(0xFFFFEFF6)
internal val BrowsePanelShape = RoundedCornerShape(18.dp)
internal val BrowseCardShape = RoundedCornerShape(8.dp)

internal fun browseSoftenedColor(color: ComposeColor): ComposeColor {
    return when (color) {
        BrowseCoral -> ComposeColor(0xFFFFEBF3)
        BrowseTeal -> ComposeColor(0xFFE6FAFB)
        BrowseGold -> ComposeColor(0xFFFFF7DC)
        ComposeColor(0xFF6E5CE6), ComposeColor(0xFFC9B9FF) -> ComposeColor(0xFFF2EEFF)
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
