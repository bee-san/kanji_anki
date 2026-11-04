package dev.bee.kanjianki.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The theme's job is to make one stored choice render the same on both hosts.
 *
 * These tests are therefore about the mapping — choice to palette, palette to
 * Material scheme — and not about how any particular color looks. Editing a
 * palette literal should not fail them; letting a *mapping* go stale should.
 *
 * The assertions that need a live composition live in
 * [KaniThemeRenderAssertions.kt] and run from each host's own render test class,
 * because composing needs host-specific JUnit plumbing.
 */
class KaniThemeTest {
    @Test
    fun everyThemeResolvesToAPaletteAndNoTwoThemesShareOne() {
        val resolved = KaniThemeId.entries.associateWith { it.resolvePalette(false) }
        assertEquals(KaniThemeId.entries.size, resolved.size)

        // SYSTEM is the one theme whose palette is another theme's: in light mode
        // it *is* LIGHT. That is the point of it, so it is excluded here rather
        // than given a near-duplicate palette of its own.
        val distinct = resolved.filterKeys { it != KaniThemeId.SYSTEM }
        assertEquals(
            distinct.size,
            distinct.values.distinct().size,
            "each theme must be visually distinguishable",
        )
    }

    @Test
    fun onlyTheSystemThemeChangesWithTheHostsDarkModeSignal() {
        for (theme in KaniThemeId.entries) {
            val light = theme.resolvePalette(isSystemInDarkTheme = false)
            val dark = theme.resolvePalette(isSystemInDarkTheme = true)
            if (theme == KaniThemeId.SYSTEM) {
                assertNotEquals(light, dark)
                assertEquals(NeutralLightKaniColors, light)
                assertEquals(DarkKaniColors, dark)
            } else {
                assertEquals(light, dark, "$theme must not depend on the host signal")
            }
        }
    }

    @Test
    fun theDarkFlagAgreesWithThePaletteItCameFrom() {
        for (theme in KaniThemeId.entries) {
            for (hostIsDark in listOf(false, true)) {
                assertEquals(
                    theme.resolvePalette(hostIsDark).isDark,
                    theme.resolveDarkTheme(hostIsDark),
                    "$theme dark flag must be derived from its palette",
                )
            }
        }
    }

    @Test
    fun exactlyTwoShippedThemesAreDarkAndSystemCanReachOne() {
        val darkThemes = KaniThemeId.entries.filter { it.resolveDarkTheme(false) }
        assertEquals(listOf(KaniThemeId.DARK, KaniThemeId.MIDNIGHT_ARCADE), darkThemes)
        assertTrue(KaniThemeId.SYSTEM.resolveDarkTheme(isSystemInDarkTheme = true))
    }

    @Test
    fun aStoredChoiceRoundTripsAndAnUnknownOneFallsBackToTheShippedDefault() {
        for (theme in KaniThemeId.entries) {
            assertEquals(theme, KaniThemeId.fromStorageKey(theme.storageKey))
        }
        // Absent, blank, and misspelled all mean "the user has not chosen", so all
        // of them must land on the theme a fresh install shows.
        for (unknown in listOf(null, "", "  ", "girlypop ", "dispaly", "GIRLYPOP")) {
            assertEquals(
                KaniThemeId.GIRLYPOP,
                KaniThemeId.fromStorageKey(unknown),
                "unrecognized key <$unknown> must fall back, not fail",
            )
        }
    }

    @Test
    fun storageKeysAreStableLowercaseIdentifiersAndUnique() {
        val keys = KaniThemeId.entries.map { it.storageKey }
        assertEquals(keys.size, keys.distinct().size)
        for (key in keys) {
            assertTrue(
                Regex("[a-z][a-z_]*").matches(key),
                "$key must stay a stable lowercase storage identifier",
            )
        }
    }

}
