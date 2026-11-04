package dev.bee.kanjianki.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniTab
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.ui.KaniThemeId
import dev.bee.kanjianki.ui.contrastRatio
import dev.bee.kanjianki.ui.resolvePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Renders the shell to actual pixels at the configurations Goal 193 enumerates.
 *
 * Phone, tablet, 1280x800, 1440x900, high-DPI, dark, light, and large-font — each
 * one rasterized, asserted on, and written out to `build/reports/shell-raster/`.
 * See [rasterizeShell] for why this is desktop-only and why it is not a
 * golden-image diff.
 *
 * The division of labour with `ShellRenderAssertions` is deliberate. Those
 * assertions run on both hosts and check structure: which node exists, what it is
 * announced as, what it dispatches. These check what survives only as pixels —
 * that the palette actually reached the surface, that a dark theme is genuinely
 * dark, that the rail occupies width rather than merely existing in the semantics
 * tree, and that doubling the display scale changes the pixel count without
 * changing a single dp of layout. A semantics test passes happily on a shell that
 * renders every theme identically; these do not.
 */
@OptIn(ExperimentalTestApi::class)
class ShellRasterTest {
    /**
     * Every configuration Goal 193 names, rendered and captured.
     *
     * One test rather than eight because the assertion is the same for all of them
     * — the raster has the requested size and contains more than a flat fill —
     * and because the captures are most useful reviewed as a set. A failure names
     * the config, so a single one going wrong is still identifiable.
     */
    @Test
    fun everyGoalConfigurationRastersAtItsRequestedSizeWithRealContent() {
        for (config in GOAL_CONFIGS) {
            rasterizeShell(config = config, state = rasterState()) {
                val image = captureShellRoot(config)

                assertEquals(config.pixelWidth, image.width, "$config raster width")
                assertEquals(config.pixelHeight, image.height, "$config raster height")
                // A blank or single-fill surface is the failure mode a size
                // assertion alone cannot catch: a shell that threw during layout
                // still produces a correctly sized background.
                val colors = image.distinctColorCount()
                assertTrue(
                    colors > MINIMUM_DISTINCT_COLORS,
                    "$config rendered $colors distinct colors, a flat fill rather than a shell",
                )
            }
        }
    }

    @Test
    fun theChosenThemesBackgroundIsTheColorActuallyPainted() {
        // Every theme, because a palette is wired field by field and only the ones
        // a test actually renders are known to be reachable.
        for (theme in KaniThemeId.entries) {
            val config = RasterConfig(window = ShellWindow.DESKTOP_SMALL, theme = theme)
            rasterizeShell(config = config, state = ShellState()) {
                val painted = captureShellRoot(config).shellBackgroundColor()

                assertTrue(
                    painted.matchesWithin8Bit(config.palette.bg),
                    "$theme painted $painted where its palette says ${config.palette.bg}",
                )
            }
        }
    }

    @Test
    fun studyPaintsItsOwnBackgroundAndEveryOtherTabPaintsTheOrdinaryOne() {
        val config = RasterConfig(window = ShellWindow.DESKTOP_SMALL)
        val palette = config.palette
        // Only worth asserting because the two differ in this palette; if they
        // ever stopped differing the test would be vacuous rather than wrong.
        assertNotEquals(palette.bg, palette.studyBg, "girlypop bg and studyBg")

        for (destination in listOf(KaniDestination.Home, KaniDestination.Stats)) {
            rasterizeShell(config = config, state = ShellState(backStack = listOf(destination))) {
                val painted = captureShellRoot(config).shellBackgroundColor()
                assertTrue(
                    painted.matchesWithin8Bit(palette.bg),
                    "${destination.route} painted $painted, not the ordinary background",
                )
            }
        }

        val study = ShellState(backStack = listOf(KaniDestination.Study))
        rasterizeShell(config = config, state = study) {
            val painted = captureShellRoot(config).shellBackgroundColor()
            assertTrue(
                painted.matchesWithin8Bit(palette.studyBg),
                "study painted $painted, not its own studyBg",
            )
        }
    }

    @Test
    fun aDarkThemeRastersDarkAndALightThemeRastersLight() {
        // The property a per-color assertion misses: a palette can be wired
        // correctly field by field and still render inverted if the shell reads
        // the wrong one. Luminance cannot be inverted quietly.
        val dark = RasterConfig(window = ShellWindow.DESKTOP_SMALL, theme = KaniThemeId.DARK)
        val light = RasterConfig(window = ShellWindow.DESKTOP_SMALL, theme = KaniThemeId.LIGHT)

        var darkLuminance = 1f
        var lightLuminance = 0f
        rasterizeShell(config = dark, state = rasterState()) {
            darkLuminance = captureShellRoot(dark).shellBackgroundColor().luminance()
        }
        rasterizeShell(config = light, state = rasterState()) {
            lightLuminance = captureShellRoot(light).shellBackgroundColor().luminance()
        }

        assertTrue(darkLuminance < LUMINANCE_DARK_CEILING, "dark background luminance $darkLuminance")
        assertTrue(lightLuminance > LUMINANCE_LIGHT_FLOOR, "light background luminance $lightLuminance")
    }

    @Test
    fun theSystemThemeFollowsTheHostsDarkSignalAllTheWayToThePixels() {
        // `SYSTEM` is the one theme whose palette is not a function of the choice
        // alone, so it is the one that can silently ignore the host signal.
        for (hostIsDark in listOf(false, true)) {
            val config = RasterConfig(
                window = ShellWindow.DESKTOP_SMALL,
                theme = KaniThemeId.SYSTEM,
                isSystemInDarkTheme = hostIsDark,
            )
            rasterizeShell(config = config, state = rasterState()) {
                val painted = captureShellRoot(config).shellBackgroundColor()
                val expected = KaniThemeId.SYSTEM.resolvePalette(hostIsDark).bg
                assertTrue(
                    painted.matchesWithin8Bit(expected),
                    "system theme with hostIsDark=$hostIsDark painted $painted, not $expected",
                )
            }
        }
    }

    @Test
    fun readableTextKeepsItsContrastAgainstWhatIsActuallyBehindIt() {
        // Read from the raster rather than from the declared palette pair, because
        // the question is what the user sees: a surface drawn over the background
        // with any opacity would change the effective contrast without changing
        // either declared color.
        for (theme in KaniThemeId.entries) {
            val config = RasterConfig(window = ShellWindow.DESKTOP_SMALL, theme = theme)
            rasterizeShell(config = config, state = rasterState()) {
                val painted = captureShellRoot(config).shellBackgroundColor()
                val ratio = contrastRatio(config.palette.ink, painted)
                assertTrue(
                    ratio >= WCAG_AA_CONTRAST,
                    "$theme ink over its painted background is only $ratio:1",
                )
            }
        }
    }

    @Test
    fun theRailOccupiesRealPixelsAndTheBarOccupiesRealRows() {
        // The semantics tests establish which surface is chosen at each width.
        // This establishes that the chosen one was actually drawn with size — a
        // rail measured to zero width is present, addressable, and invisible.
        val wide = RasterConfig(window = ShellWindow.DESKTOP_LARGE)
        rasterizeShell(config = wide, state = rasterState()) {
            val rail = onNodeWithTag(SHELL_NAV_RAIL_TEST_TAG)
            rail.assertIsDisplayed()
            val bounds = rail.getBoundsInRoot()
            val width = bounds.right - bounds.left
            assertTrue(width > MINIMUM_RAIL_WIDTH, "the rail is only $width wide")
            val colors = rail.captureAndWrite("rail-${wide.label}").distinctColorCount()
            assertTrue(
                colors > MINIMUM_DISTINCT_COLORS,
                "the rail rastered $colors colors, so its tabs did not draw",
            )
        }

        val narrow = RasterConfig(window = ShellWindow.PHONE)
        rasterizeShell(config = narrow, state = rasterState()) {
            val bar = onNodeWithTag(SHELL_BOTTOM_NAV_TEST_TAG)
            bar.assertIsDisplayed()
            val bounds = bar.getBoundsInRoot()
            val height = bounds.bottom - bounds.top
            assertTrue(height > MINIMUM_BAR_HEIGHT, "the bottom bar is only $height tall")
            val colors = bar.captureAndWrite("bar-${narrow.label}").distinctColorCount()
            assertTrue(
                colors > MINIMUM_DISTINCT_COLORS,
                "the bottom bar rastered $colors colors, so its tabs did not draw",
            )
        }
    }

    @Test
    fun doublingTheDisplayScaleDoublesThePixelsAndChangesNoLayout() {
        // This is what "high-DPI" means, and it is the configuration that can
        // regress silently: a pixel size hardcoded anywhere in the shell keeps the
        // dp layout right at 1x and wrong at 2x, or the reverse.
        val oneX = RasterConfig(window = ShellWindow.DESKTOP_SMALL, scale = 1f)
        val twoX = RasterConfig(window = ShellWindow.DESKTOP_SMALL, scale = 2f)

        val railBounds = HashMap<String, DpRect>()
        for (config in listOf(oneX, twoX)) {
            rasterizeShell(config = config, state = rasterState()) {
                val image = captureShellRoot(config)
                assertEquals(config.pixelWidth, image.width, "$config raster width")
                assertEquals(config.pixelHeight, image.height, "$config raster height")
                railBounds[config.label] = onNodeWithTag(SHELL_NAV_RAIL_TEST_TAG).getBoundsInRoot()
            }
        }

        // Compared with a tolerance rather than for equality: the two densities
        // round pixel measurements back to dp differently in the last fraction,
        // and a real layout change is off by far more than a rounding step.
        assertSameGeometry(
            expected = railBounds.getValue(oneX.label),
            actual = railBounds.getValue(twoX.label),
            what = "the rail",
        )
    }

    @Test
    fun aLargeFontScaleStillRastersEveryTabInsideTheWindow() {
        // The failure this guards is specific: at 1.5x the labels stack into two
        // rows, and a bar that grew past the window would put a tab off-screen
        // while remaining "displayed" as far as a semantics query is concerned.
        val config = RasterConfig(window = ShellWindow.PHONE, fontScale = LARGE_FONT_SCALE)
        rasterizeShell(config = config, state = rasterState()) {
            captureShellRoot(config)

            val window = onNodeWithTag(SHELL_ROOT_TEST_TAG).getBoundsInRoot()
            for (tab in KaniTab.entries) {
                val bounds = onNodeWithTag(shellTabTestTag(tab)).getBoundsInRoot()
                assertTrue(
                    bounds.bottom <= window.bottom + BOUNDS_SLACK &&
                        bounds.right <= window.right + BOUNDS_SLACK,
                    "${tab.route} at $bounds is outside the window $window",
                )
            }
        }
    }

    @Test
    fun theSmallestAllowedWindowStillRastersNavigationAndContentTogether() {
        // The desktop host refuses to resize below this, so it is the one size at
        // which "the navigation fits" is a product promise rather than a
        // convenience. Content and navigation must both be on screen, not one
        // pushing the other out.
        val config = RasterConfig(window = ShellWindow.DESKTOP_MINIMUM)
        rasterizeShell(
            config = config,
            state = rasterState(),
            routeBody = {
                Text(
                    text = "body",
                    modifier = Modifier.fillMaxSize().padding(8.dp).testTag(RASTER_BODY_TAG),
                )
            },
        ) {
            captureShellRoot(config)

            val body = onNodeWithTag(RASTER_BODY_TAG)
            body.assertIsDisplayed()
            val bar = onNodeWithTag(SHELL_BOTTOM_NAV_TEST_TAG)
            bar.assertIsDisplayed()
            assertTrue(
                body.getBoundsInRoot().bottom <= bar.getBoundsInRoot().top + BOUNDS_SLACK,
                "the route body at ${body.getBoundsInRoot()} overlaps the navigation bar",
            )
        }
    }

    @Test
    fun theBackButtonIsDrawnWhenTheHostAsksForIt() {
        val config = RasterConfig(window = ShellWindow.DESKTOP_SMALL)
        rasterizeShell(
            config = config,
            state = rasterState(),
            backAffordance = ShellBackAffordanceMode.IN_SHELL,
        ) {
            captureShellRoot(config)

            val back = onNodeWithTag(SHELL_BACK_TEST_TAG)
            back.assertIsDisplayed()
            // Drawn, not merely laid out: the arrow is a vector resource, and a
            // resource that failed to load leaves a correctly sized empty node
            // that every semantics assertion still passes.
            val colors = back.captureAndWrite("back-${config.label}").distinctColorCount()
            assertTrue(colors > 1, "the back button rastered one color, so its icon did not draw")
        }
    }

    /** Asserts two dp rectangles describe the same layout, within rounding. */
    private fun assertSameGeometry(expected: DpRect, actual: DpRect, what: String) {
        val sides = listOf(
            "left" to (expected.left to actual.left),
            "top" to (expected.top to actual.top),
            "right" to (expected.right to actual.right),
            "bottom" to (expected.bottom to actual.bottom),
        )
        for ((side, pair) in sides) {
            val (want, got) = pair
            assertTrue(
                abs(want.value - got.value) <= BOUNDS_SLACK.value,
                "$what moved its $side edge from $want to $got",
            )
        }
    }

    private companion object {
        /**
         * The eight configurations Goal 193 lists, in one place.
         *
         * Phone and tablet carry Android's sizes so the two hosts' captures are
         * comparable by eye; the two desktop sizes are the initial window and a
         * common larger display; the 2x entry is high-DPI; dark and light are the
         * explicit palettes; and the last is the large-font phone, the only
         * configuration where the navigation changes shape.
         */
        val GOAL_CONFIGS = listOf(
            RasterConfig(window = ShellWindow.PHONE),
            RasterConfig(window = ShellWindow.TABLET),
            RasterConfig(window = ShellWindow.DESKTOP_SMALL),
            RasterConfig(window = ShellWindow.DESKTOP_LARGE),
            RasterConfig(window = ShellWindow.DESKTOP_SMALL, scale = 2f),
            RasterConfig(window = ShellWindow.DESKTOP_SMALL, theme = KaniThemeId.DARK),
            RasterConfig(window = ShellWindow.DESKTOP_SMALL, theme = KaniThemeId.LIGHT),
            RasterConfig(window = ShellWindow.PHONE, fontScale = LARGE_FONT_SCALE),
        )

        const val RASTER_BODY_TAG = "raster-body"

        /**
         * Above a flat fill by a wide margin.
         *
         * Deliberately low rather than tuned: the value that would catch a subtle
         * visual regression is also the value that breaks on a font or Skia bump,
         * and this assertion's job is to catch "nothing drew", which a threshold
         * of 8 does as well as a threshold of 500 and without the false alarms.
         */
        const val MINIMUM_DISTINCT_COLORS = 8

        val MINIMUM_RAIL_WIDTH: Dp = 40.dp
        val MINIMUM_BAR_HEIGHT: Dp = 40.dp

        /** Sub-pixel rounding tolerance when comparing dp bounds. */
        val BOUNDS_SLACK: Dp = 1.dp

        const val LUMINANCE_DARK_CEILING = 0.2f
        const val LUMINANCE_LIGHT_FLOOR = 0.5f
        const val WCAG_AA_CONTRAST = 4.5
    }
}
