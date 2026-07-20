package dev.bee.kanjianki.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.theme.resolvePalette
import kotlin.math.max
import kotlin.math.min
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
            assertEquals(
                choice.name,
                if (contrastRatio(day.primary, day.bg) >= 4.5) day.primary else day.ink,
                palette.primaryText.day,
            )
            assertEquals(
                choice.name,
                if (contrastRatio(night.primary, night.bg) >= 4.5) night.primary else night.ink,
                palette.primaryText.night,
            )
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

    @Test
    fun activityHeatUsesFourDistinctSemanticRolesInEveryTheme() {
        for (choice in KaniThemeChoice.entries) {
            val palette = KaniWidgetPalette.forChoice(choice)
            val roles = ActivityIntensity.entries.map(palette::activityHeat)

            assertEquals(choice.name, palette.track, roles.first())
            assertEquals(choice.name, 4, roles.map { it.day }.toSet().size)
            assertEquals(choice.name, 4, roles.map { it.night }.toSet().size)
            roles.drop(1).forEach { role ->
                assertTrue(
                    "$choice day activity cell contrast",
                    contrastRatio(role.day, palette.background.day) >= 3.0,
                )
                assertTrue(
                    "$choice night activity cell contrast",
                    contrastRatio(role.night, palette.background.night) >= 3.0,
                )
            }
        }
    }

    @Test
    fun todayOutlineKeepsNonTextContrastAgainstTheWidgetBackground() {
        for (choice in KaniThemeChoice.entries) {
            val palette = KaniWidgetPalette.forChoice(choice)
            assertTrue(
                "$choice day today outline contrast",
                contrastRatio(palette.todayOutline.day, palette.background.day) >= 3.0,
            )
            assertTrue(
                "$choice night today outline contrast",
                contrastRatio(palette.todayOutline.night, palette.background.night) >= 3.0,
            )
        }
    }

    @Test
    fun widgetTextRolesKeepNormalTextContrastAgainstTheBackground() {
        for (choice in KaniThemeChoice.entries) {
            val palette = KaniWidgetPalette.forChoice(choice)
            listOf(palette.ink, palette.muted, palette.primaryText).forEach { role ->
                assertTrue(
                    "$choice day text contrast",
                    contrastRatio(role.day, palette.background.day) >= 4.5,
                )
                assertTrue(
                    "$choice night text contrast",
                    contrastRatio(role.night, palette.background.night) >= 4.5,
                )
            }
        }
    }

    @Test
    fun filledActionLabelsKeepNormalTextContrastAgainstPrimary() {
        for (choice in KaniThemeChoice.entries) {
            val palette = KaniWidgetPalette.forChoice(choice)
            assertTrue(
                "$choice day filled-action contrast",
                contrastRatio(palette.onPrimary.day, palette.primary.day) >= 4.5,
            )
            assertTrue(
                "$choice night filled-action contrast",
                contrastRatio(palette.onPrimary.night, palette.primary.night) >= 4.5,
            )
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val foregroundLuminance = foreground.luminance().toDouble()
        val backgroundLuminance = background.luminance().toDouble()
        return (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
    }
}
