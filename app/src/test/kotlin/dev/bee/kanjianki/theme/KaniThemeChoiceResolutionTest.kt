package dev.bee.kanjianki.theme

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import dev.bee.kanjianki.AutumnKaniColors
import dev.bee.kanjianki.DarkKaniColors
import dev.bee.kanjianki.ForestMossKaniColors
import dev.bee.kanjianki.GirlypopKaniColors
import dev.bee.kanjianki.GrapeSodaKaniColors
import dev.bee.kanjianki.MatchaMilkKaniColors
import dev.bee.kanjianki.MidnightArcadeKaniColors
import dev.bee.kanjianki.NeutralLightKaniColors
import dev.bee.kanjianki.OceanStudyKaniColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class KaniThemeChoiceResolutionTest {
    @Test
    fun followSystemThemeUsesTheInjectedDarkModeBoolean() {
        assertEquals(false, KaniThemeChoice.GIRLYPOP.resolveDarkTheme(isSystemInDarkTheme = false))
        assertEquals(false, KaniThemeChoice.LIGHT.resolveDarkTheme(isSystemInDarkTheme = true))
        assertEquals(true, KaniThemeChoice.DARK.resolveDarkTheme(isSystemInDarkTheme = false))
        assertEquals(false, KaniThemeChoice.SYSTEM.resolveDarkTheme(isSystemInDarkTheme = false))
        assertEquals(true, KaniThemeChoice.SYSTEM.resolveDarkTheme(isSystemInDarkTheme = true))
        assertEquals(false, KaniThemeChoice.AUTUMN.resolveDarkTheme(isSystemInDarkTheme = true))
        assertEquals(false, KaniThemeChoice.MATCHA_MILK.resolveDarkTheme(isSystemInDarkTheme = true))
        assertEquals(false, KaniThemeChoice.OCEAN_STUDY.resolveDarkTheme(isSystemInDarkTheme = true))
        assertEquals(true, KaniThemeChoice.MIDNIGHT_ARCADE.resolveDarkTheme(isSystemInDarkTheme = false))
        assertEquals(false, KaniThemeChoice.GRAPE_SODA.resolveDarkTheme(isSystemInDarkTheme = true))
        assertEquals(false, KaniThemeChoice.FOREST_MOSS.resolveDarkTheme(isSystemInDarkTheme = false))
    }

    @Test
    fun themeChoicesResolveExpectedPalettes() {
        assertEquals(GirlypopKaniColors.bg, KaniThemeChoice.GIRLYPOP.resolvePalette(false).bg)
        assertEquals(NeutralLightKaniColors.bg, KaniThemeChoice.LIGHT.resolvePalette(false).bg)
        assertEquals(DarkKaniColors.bg, KaniThemeChoice.DARK.resolvePalette(false).bg)
        assertEquals(NeutralLightKaniColors.bg, KaniThemeChoice.SYSTEM.resolvePalette(false).bg)
        assertEquals(DarkKaniColors.bg, KaniThemeChoice.SYSTEM.resolvePalette(true).bg)
        assertEquals(AutumnKaniColors.bg, KaniThemeChoice.AUTUMN.resolvePalette(false).bg)
        assertEquals(MatchaMilkKaniColors.bg, KaniThemeChoice.MATCHA_MILK.resolvePalette(false).bg)
        assertEquals(OceanStudyKaniColors.bg, KaniThemeChoice.OCEAN_STUDY.resolvePalette(false).bg)
        assertEquals(MidnightArcadeKaniColors.bg, KaniThemeChoice.MIDNIGHT_ARCADE.resolvePalette(false).bg)
        assertEquals(GrapeSodaKaniColors.bg, KaniThemeChoice.GRAPE_SODA.resolvePalette(false).bg)
        assertEquals(ForestMossKaniColors.bg, KaniThemeChoice.FOREST_MOSS.resolvePalette(false).bg)
        assertTrue(GirlypopKaniColors.bg != NeutralLightKaniColors.bg)
    }

    @Test
    fun systemBarsFollowResolvedThemePalette() {
        val darkSystemBars = KaniThemeChoice.SYSTEM.resolveSystemBars(true)
        val lightSystemBars = KaniThemeChoice.SYSTEM.resolveSystemBars(false)

        assertEquals(DarkKaniColors.bg, darkSystemBars.backgroundColor)
        assertEquals(NeutralLightKaniColors.bg, lightSystemBars.backgroundColor)
        assertTrue(!darkSystemBars.appearanceLightStatusBars)
        assertTrue(!darkSystemBars.appearanceLightNavigationBars)
        assertTrue(lightSystemBars.appearanceLightStatusBars)
        assertTrue(lightSystemBars.appearanceLightNavigationBars)
    }

    @Test
    fun palettesKeepReadableTextOnKeySurfaces() {
        listOf(
            GirlypopKaniColors,
            NeutralLightKaniColors,
            DarkKaniColors,
            AutumnKaniColors,
            MatchaMilkKaniColors,
            OceanStudyKaniColors,
            MidnightArcadeKaniColors,
            GrapeSodaKaniColors,
            ForestMossKaniColors,
        ).forEach { colors ->
            assertContrastAtLeast(
                foreground = colors.ink,
                background = colors.surface,
                minimum = 4.5,
                label = "ink on surface for ${colors.bg}",
            )
            assertContrastAtLeast(
                foreground = colors.onPrimary,
                background = colors.primary,
                minimum = 4.5,
                label = "onPrimary on primary for ${colors.bg}",
            )
        }
    }

    private fun assertContrastAtLeast(
        foreground: ComposeColor,
        background: ComposeColor,
        minimum: Double,
        label: String,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("Expected $label contrast >= $minimum, was ${"%.2f".format(ratio)}", ratio >= minimum)
    }

    private fun contrastRatio(foreground: ComposeColor, background: ComposeColor): Double {
        val foregroundLuminance = foreground.luminance().toDouble()
        val backgroundLuminance = background.luminance().toDouble()
        val lighter = max(foregroundLuminance, backgroundLuminance)
        val darker = min(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
