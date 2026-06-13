package dev.bee.kanjianki

import dev.bee.kanjianki.theme.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityScreenshotThemesTest {
    @Test
    fun parsesKnownThemeKeys() {
        assertEquals(KaniThemeChoice.GIRLYPOP, screenshotThemeChoiceOrNull("girlypop"))
        assertEquals(KaniThemeChoice.LIGHT, screenshotThemeChoiceOrNull("LIGHT"))
        assertEquals(KaniThemeChoice.DARK, screenshotThemeChoiceOrNull(" dark "))
        assertEquals(KaniThemeChoice.SYSTEM, screenshotThemeChoiceOrNull("system"))
        assertEquals(KaniThemeChoice.AUTUMN, screenshotThemeChoiceOrNull("AUTUMN"))
    }

    @Test
    fun returnsNullForMissingOrUnknownThemeKeys() {
        assertNull(screenshotThemeChoiceOrNull(null))
        assertNull(screenshotThemeChoiceOrNull(" "))
        assertNull(screenshotThemeChoiceOrNull("system-dark"))
        assertNull(screenshotThemeChoiceOrNull("not-a-theme"))
    }
}
