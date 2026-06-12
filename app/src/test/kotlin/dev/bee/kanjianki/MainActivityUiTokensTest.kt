package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color as ComposeColor
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityUiTokensTest {
    @Test
    fun canonicalAccentTokensAndReadableTextColorsStayInSync() {
        assertEquals(ComposeColor(MainActivityUiSupport.TEAL), LightKaniColors.teal)
        assertEquals(
            ComposeColor(MainActivityUiSupport.INK),
            KaniUiTokens.readableTextColor(ComposeColor(MainActivityUiSupport.CORAL))
        )
        assertEquals(
            ComposeColor(MainActivityUiSupport.INK),
            KaniUiTokens.readableTextColor(ComposeColor(MainActivityUiSupport.TEAL))
        )
        assertEquals(
            ComposeColor.White,
            KaniUiTokens.readableTextColor(ComposeColor(MainActivityUiSupport.BLUE))
        )
    }

    @Test
    fun legacyPaletteIntsResolveToThemeTokens() {
        assertEquals(LightKaniColors.primary, LightKaniColors.fromLegacy(MainActivityUiSupport.STUDY_PINK_DARK))
        assertEquals(LightKaniColors.ink, LightKaniColors.fromLegacy(MainActivityUiSupport.INK))
        assertEquals(DarkKaniColors.primary, DarkKaniColors.fromLegacy(MainActivityUiSupport.STUDY_PINK_DARK))
        // Unknown ints pass through unchanged.
        assertEquals(ComposeColor(0xFF123456), DarkKaniColors.fromLegacy(0xFF123456.toInt()))
    }
}
