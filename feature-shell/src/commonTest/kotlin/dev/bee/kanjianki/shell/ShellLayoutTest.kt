package dev.bee.kanjianki.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The layout decision at every configuration Goal 193 names.
 *
 * These are the sizes the screenshot tests will render at, checked here as values
 * first: a wrong placement is far cheaper to find in an assertion than in an image
 * diff, and the pure function is what makes that possible.
 */
class ShellLayoutTest {
    @Test
    fun phoneAndTabletKeepTheirAndroidPlacements() {
        // A phone in portrait and a small tablet stay on the bottom bar; a large
        // tablet in landscape crosses the breakpoint and gets the rail, which is
        // exactly what the Android shell did at 840dp.
        assertEquals(
            ShellNavigationPlacement.BOTTOM_BAR,
            resolveShellLayout(windowWidth = 411.dp).placement,
            "a phone keeps the bottom bar",
        )
        assertEquals(
            ShellNavigationPlacement.BOTTOM_BAR,
            resolveShellLayout(windowWidth = 600.dp).placement,
            "a small tablet keeps the bottom bar",
        )
        assertEquals(
            ShellNavigationPlacement.SIDE_RAIL,
            resolveShellLayout(windowWidth = 1024.dp).placement,
            "a large tablet gets the rail, as on Android",
        )
    }

    @Test
    fun theBreakpointIsInclusiveAtItsOwnValue() {
        // Exactly at the breakpoint is the rail, matching Android's `>=`. A test
        // for this exists because an off-by-one here silently changes the layout
        // of every device whose width is exactly 840dp.
        assertEquals(
            ShellNavigationPlacement.BOTTOM_BAR,
            resolveShellLayout(windowWidth = EXPANDED_WIDTH_BREAKPOINT - 1.dp).placement,
        )
        assertEquals(
            ShellNavigationPlacement.SIDE_RAIL,
            resolveShellLayout(windowWidth = EXPANDED_WIDTH_BREAKPOINT).placement,
        )
    }

    @Test
    fun everyDesktopWindowSizeGetsTheRailAndACappedMeasure() {
        // The desktop sizes from Goal 193, plus the high-DPI case. High-DPI does
        // not change the answer, which is the point: Compose reports density-
        // independent width, so a 2x display of the same physical size lays out
        // identically. Asserting it here is what keeps someone from "fixing" the
        // layout by branching on density.
        for (width in listOf(1280.dp, 1440.dp, 1920.dp)) {
            val layout = resolveShellLayout(windowWidth = width)
            assertEquals(
                ShellNavigationPlacement.SIDE_RAIL,
                layout.placement,
                "$width should use the rail",
            )
            assertEquals(
                CONTENT_MAX_WIDTH,
                layout.contentMaxWidth,
                "$width must cap the readable measure",
            )
        }
    }

    @Test
    fun theMinimumDesktopWindowStaysBelowTheBreakpoint() {
        // A user who shrinks the window to its floor must get the compact layout,
        // not a rail with no room for content beside it. This is the one coupling
        // between the window's minimum size and the layout breakpoint, so pin it.
        assertTrue(
            DESKTOP_MINIMUM_WINDOW_WIDTH < EXPANDED_WIDTH_BREAKPOINT,
            "the minimum window must fall in the compact range",
        )
        assertEquals(
            ShellNavigationPlacement.BOTTOM_BAR,
            resolveShellLayout(windowWidth = DESKTOP_MINIMUM_WINDOW_WIDTH).placement,
        )
        assertTrue(DESKTOP_MINIMUM_WINDOW_HEIGHT > 0.dp)
    }

    @Test
    fun aCompactWindowDoesNotCapItsOwnWidth() {
        // Applying a 640dp cap inside a 411dp window would be a no-op today and a
        // trap later, so the compact layout leaves the width unspecified.
        assertEquals(
            Dp.Unspecified,
            resolveShellLayout(windowWidth = 411.dp).contentMaxWidth,
        )
    }

    @Test
    fun aLargeFontScaleStacksTheBottomBarButNeverTheRail() {
        val phone = resolveShellLayout(windowWidth = 411.dp, fontScale = LARGE_FONT_SCALE)
        assertEquals(ShellNavigationPlacement.BOTTOM_BAR, phone.placement)
        assertTrue(phone.stackNavigationRows, "four labels do not fit on one row")

        assertFalse(
            resolveShellLayout(windowWidth = 411.dp, fontScale = 1.4f).stackNavigationRows,
            "below the threshold the labels still fit",
        )
        // The rail stacks its tabs vertically already, so it has room for each
        // label at any scale. Stacking there would produce a 2x2 grid in a
        // vertical strip.
        assertFalse(
            resolveShellLayout(
                windowWidth = 1280.dp,
                fontScale = LARGE_FONT_SCALE,
            ).stackNavigationRows,
        )
    }

    @Test
    fun immersionHidesNavigationAtEverySizeAndForEitherReason() {
        // The Android shell derived this from four separate booleans and a caller
        // that forgot one drew the bar over the keyboard. Both reasons must win at
        // both sizes.
        for (width in listOf(411.dp, 1440.dp)) {
            for (immersion in listOf(
                ShellImmersion(keyboardVisible = true),
                ShellImmersion(routeIsImmersive = true),
                ShellImmersion(keyboardVisible = true, routeIsImmersive = true),
            )) {
                val layout = resolveShellLayout(windowWidth = width, immersion = immersion)
                assertEquals(
                    ShellNavigationPlacement.HIDDEN,
                    layout.placement,
                    "$width with $immersion must hide navigation",
                )
                assertFalse(layout.showsNavigation)
                assertFalse(
                    layout.stackNavigationRows,
                    "hidden navigation has no rows to stack",
                )
            }
        }
    }

    @Test
    fun aVisibleLayoutReportsThatItShowsNavigation() {
        assertTrue(resolveShellLayout(windowWidth = 411.dp).showsNavigation)
        assertTrue(resolveShellLayout(windowWidth = 1440.dp).showsNavigation)
    }

    @Test
    fun contentPaddingIsTheSameAtEverySize() {
        // Not a size-dependent value today. Asserted so that if someone makes it
        // one, they do it deliberately rather than as a side effect.
        val paddings = listOf(411.dp, 600.dp, 840.dp, 1280.dp, 1440.dp)
            .map { resolveShellLayout(windowWidth = it).contentPadding }
            .distinct()
        assertEquals(1, paddings.size, "padding should not vary by width yet")
        assertTrue(paddings.single() > 0.dp)
    }
}
