package dev.bee.kanjianki.theme

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
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
    }

    @Test
    fun themeChoicesResolveExpectedPalettes() {
        assertEquals(GirlypopKaniColors.bg, KaniThemeChoice.GIRLYPOP.resolvePalette(false).bg)
        assertEquals(NeutralLightKaniColors.bg, KaniThemeChoice.LIGHT.resolvePalette(false).bg)
        assertEquals(DarkKaniColors.bg, KaniThemeChoice.DARK.resolvePalette(false).bg)
        assertEquals(NeutralLightKaniColors.bg, KaniThemeChoice.SYSTEM.resolvePalette(false).bg)
        assertEquals(DarkKaniColors.bg, KaniThemeChoice.SYSTEM.resolvePalette(true).bg)
        assertEquals(AutumnKaniColors.bg, KaniThemeChoice.AUTUMN.resolvePalette(false).bg)
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
