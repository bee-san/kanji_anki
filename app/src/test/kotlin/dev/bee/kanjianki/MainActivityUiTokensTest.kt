package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color as ComposeColor
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityUiTokensTest {
    @Test
    fun canonicalAccentTokensAndReadableTextColorsStayInSync() {
        assertEquals(ComposeColor(MainActivityUiSupport.TEAL), KaniUiTokens.Teal)
        assertEquals(
            KaniUiTokens.Ink,
            KaniUiTokens.readableTextColor(ComposeColor(MainActivityUiSupport.CORAL))
        )
        assertEquals(
            KaniUiTokens.Ink,
            KaniUiTokens.readableTextColor(ComposeColor(MainActivityUiSupport.TEAL))
        )
        assertEquals(
            KaniUiTokens.White,
            KaniUiTokens.readableTextColor(ComposeColor(MainActivityUiSupport.BLUE))
        )
    }
}
