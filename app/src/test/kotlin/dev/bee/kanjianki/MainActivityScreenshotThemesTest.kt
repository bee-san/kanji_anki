package dev.bee.kanjianki

import dev.bee.kanjianki.core.KaniThemeChoice
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
        assertEquals(KaniThemeChoice.MATCHA_MILK, screenshotThemeChoiceOrNull("matcha_milk"))
        assertEquals(KaniThemeChoice.OCEAN_STUDY, screenshotThemeChoiceOrNull("OCEAN_STUDY"))
        assertEquals(KaniThemeChoice.MIDNIGHT_ARCADE, screenshotThemeChoiceOrNull(" midnight_arcade "))
        assertEquals(KaniThemeChoice.GRAPE_SODA, screenshotThemeChoiceOrNull("grape_soda"))
        assertEquals(KaniThemeChoice.FOREST_MOSS, screenshotThemeChoiceOrNull("FOREST_MOSS"))
    }

    @Test
    fun returnsNullForMissingOrUnknownThemeKeys() {
        assertNull(screenshotThemeChoiceOrNull(null))
        assertNull(screenshotThemeChoiceOrNull(" "))
        assertNull(screenshotThemeChoiceOrNull("system-dark"))
        assertNull(screenshotThemeChoiceOrNull("not-a-theme"))
    }
}
