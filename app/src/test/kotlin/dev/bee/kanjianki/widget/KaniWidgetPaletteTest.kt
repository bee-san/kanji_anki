package dev.bee.kanjianki.widget

import dev.bee.kanjianki.theme.KaniThemeChoice
import dev.bee.kanjianki.theme.resolvePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniWidgetPaletteTest {

    @Test
    fun everyThemeChoiceMapsRolesFromItsResolvedPalettes() {
        for (choice in KaniThemeChoice.entries) {
            val palette = KaniWidgetPalette.forChoice(choice)
            val day = choice.resolvePalette(isSystemInDarkTheme = false)
            val night = choice.resolvePalette(isSystemInDarkTheme = true)

            assertEquals(choice.name, day.bg, palette.background.day)
            assertEquals(choice.name, night.bg, palette.background.night)
            assertEquals(choice.name, day.primary, palette.primary.day)
            assertEquals(choice.name, night.primary, palette.primary.night)
            assertEquals(choice.name, day.ink, palette.ink.day)
            assertEquals(choice.name, night.ink, palette.ink.night)
            assertEquals(choice.name, day.muted, palette.muted.day)
            assertEquals(choice.name, night.muted, palette.muted.night)
            assertEquals(choice.name, day.track, palette.track.day)
            assertEquals(choice.name, night.track, palette.track.night)
        }
    }

    @Test
    fun fixedThemeChoicesIgnoreSystemDarkMode() {
        for (choice in KaniThemeChoice.entries.filter { it != KaniThemeChoice.SYSTEM }) {
            val palette = KaniWidgetPalette.forChoice(choice)
            assertEquals(choice.name, palette.background.day, palette.background.night)
            assertEquals(choice.name, palette.primary.day, palette.primary.night)
            assertEquals(choice.name, palette.ink.day, palette.ink.night)
            assertEquals(choice.name, palette.muted.day, palette.muted.night)
            assertEquals(choice.name, palette.track.day, palette.track.night)
        }
    }

    @Test
    fun systemChoiceResolvesDifferentDayAndNightPalettes() {
        val palette = KaniWidgetPalette.forChoice(KaniThemeChoice.SYSTEM)
        assertNotEquals(palette.background.day, palette.background.night)
        assertNotEquals(palette.ink.day, palette.ink.night)
        assertEquals(
            KaniThemeChoice.LIGHT.resolvePalette(isSystemInDarkTheme = false).bg,
            palette.background.day,
        )
        assertEquals(
            KaniThemeChoice.DARK.resolvePalette(isSystemInDarkTheme = true).bg,
            palette.background.night,
        )
    }

    @Test
    fun withAlphaScalesBothDayAndNightColors() {
        val role = KaniWidgetPalette.forChoice(KaniThemeChoice.SYSTEM).primary
        val faded = role.withAlpha(0.4f)
        assertEquals(0.4f, faded.day.alpha, 0.0001f)
        assertEquals(0.4f, faded.night.alpha, 0.0001f)
        assertEquals(role.day.red, faded.day.red, 0.0001f)
        assertEquals(role.night.red, faded.night.red, 0.0001f)
    }

    @Test
    fun darkThemeChoiceUsesDarkPaletteForBothModes() {
        val palette = KaniWidgetPalette.forChoice(KaniThemeChoice.DARK)
        val dark = KaniThemeChoice.DARK.resolvePalette(isSystemInDarkTheme = false)
        assertTrue(dark.isDark)
        assertEquals(dark.bg, palette.background.day)
        assertEquals(dark.bg, palette.background.night)
    }
}
