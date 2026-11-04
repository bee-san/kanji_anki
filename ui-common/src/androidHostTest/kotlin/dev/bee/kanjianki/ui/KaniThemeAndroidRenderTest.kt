package dev.bee.kanjianki.ui

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the shared theme render assertions on the Android host target.
 *
 * Robolectric is what makes this possible off-device: `runComposeUiTest` needs a
 * real Android environment (it reads `Build.FINGERPRINT` to decide its idling
 * strategy), and without the runner every render dies in that lookup. The
 * assertions themselves are the same ones the desktop twin runs, so a theme
 * mapping that works on one host and not the other fails here rather than in a
 * screenshot diff later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniThemeAndroidRenderTest {
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
