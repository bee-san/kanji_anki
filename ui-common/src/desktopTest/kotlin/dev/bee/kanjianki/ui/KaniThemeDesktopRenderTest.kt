package dev.bee.kanjianki.ui

import kotlin.test.Test

/**
 * Runs the shared theme render assertions on the desktop JVM.
 *
 * Nothing here is desktop-specific except the fact that it runs: the desktop test
 * JVM composes into a Skia surface with no extra plumbing, so the class body is
 * only a list of calls into the shared assertions. Its Android twin does the same
 * under Robolectric.
 */
class KaniThemeDesktopRenderTest {
    @Test
    fun theMaterialSchemeIsDerivedFromThePaletteForEveryTheme() {
        assertMaterialSchemeIsDerivedFromThePaletteForEveryTheme()
    }

    @Test
    fun anExplicitPaletteBypassesChoiceResolutionEntirely() {
        assertAnExplicitPaletteBypassesChoiceResolutionEntirely()
    }

    @Test
    fun theDefaultThemeIsWhatAFreshInstallShows() {
        assertTheDefaultThemeIsWhatAFreshInstallShows()
    }

    @Test
    fun typographyAndShapesAreAppliedRatherThanLeftAtMaterialDefaults() {
        assertTypographyAndShapesAreAppliedRatherThanLeftAtMaterialDefaults()
    }

    @Test
    fun thePaletteLocalHasAUsableValueWithNoThemeAboveIt() {
        assertThePaletteLocalHasAUsableValueWithNoThemeAboveIt()
    }
}
