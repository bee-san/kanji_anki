package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.desktop.DesktopWindowBoundsPolicy.ScreenRect
import dev.bee.kanjianki.data.desktop.DesktopWindowBoundsPolicy.StoredWindow
import dev.bee.kanjianki.data.desktop.DesktopWindowBoundsPolicy.WindowBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopWindowBoundsPolicyTest {
    private val primary = ScreenRect(0, 0, 1920, 1080)
    private val secondary = ScreenRect(1920, 0, 1920, 1080)

    private fun stored(
        x: Int? = null,
        y: Int? = null,
        width: Int? = null,
        height: Int? = null,
        maximized: Boolean = false,
    ) = StoredWindow(x, y, width, height, maximized)

    @Test
    fun restoreHonoursAFullyStoredReachablePosition() {
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 200, y = 150, width = 1000, height = 700, maximized = false),
            listOf(primary),
        )
        assertEquals(WindowBounds(200, 150, 1000, 700), placement.bounds)
        assertFalseMaximized(placement.maximized)
    }

    @Test
    fun restoreCarriesTheMaximizedFlagThrough() {
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 10, y = 10, width = 1000, height = 700, maximized = true),
            listOf(primary),
        )
        assertTrue(placement.maximized)
    }

    @Test
    fun restoreFallsBackToDefaultSizeCenteredWhenNothingStored() {
        val placement = DesktopWindowBoundsPolicy.restore(stored(), listOf(primary))
        assertEquals(DesktopWindowBoundsPolicy.DEFAULT_WIDTH, placement.bounds.width)
        assertEquals(DesktopWindowBoundsPolicy.DEFAULT_HEIGHT, placement.bounds.height)
        // Centred: (1920 - 1280) / 2 = 320, (1080 - 800) / 2 = 140.
        assertEquals(320, placement.bounds.x)
        assertEquals(140, placement.bounds.y)
    }

    @Test
    fun restoreRecentersWhenTheStoredMonitorIsGone() {
        // Saved on a second monitor at x=2200 that is no longer attached.
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 2200, y = 100, width = 1000, height = 700),
            listOf(primary),
        )
        // Size is kept, but the position is re-centred onto the one screen left.
        assertEquals(1000, placement.bounds.width)
        assertEquals(700, placement.bounds.height)
        assertEquals((1920 - 1000) / 2, placement.bounds.x)
        assertEquals((1080 - 700) / 2, placement.bounds.y)
    }

    @Test
    fun restoreKeepsAPositionOnASecondaryScreenWhenItIsStillAttached() {
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 2100, y = 100, width = 1000, height = 700),
            listOf(primary, secondary),
        )
        assertEquals(WindowBounds(2100, 100, 1000, 700), placement.bounds)
    }

    @Test
    fun restoreRaisesASubMinimumStoredSizeToTheMinimum() {
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 0, y = 0, width = 100, height = 100),
            listOf(primary),
        )
        assertEquals(DesktopWindowBoundsPolicy.MIN_WIDTH, placement.bounds.width)
        assertEquals(DesktopWindowBoundsPolicy.MIN_HEIGHT, placement.bounds.height)
    }

    @Test
    fun restoreCapsAnOversizedStoredWidthToTheLargestScreen() {
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 0, y = 0, width = 9000, height = 9000),
            listOf(primary),
        )
        assertEquals(1920, placement.bounds.width)
        assertEquals(1080, placement.bounds.height)
    }

    @Test
    fun restoreUsesTheLargerScreenAsTheSizeCapAcrossAllScreens() {
        val small = ScreenRect(0, 0, 800, 600)
        val large = ScreenRect(800, 0, 2560, 1440)
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 0, y = 0, width = 9000, height = 9000),
            listOf(small, large),
        )
        // Cap comes from the largest single screen, not the first one.
        assertEquals(2560, placement.bounds.width)
        assertEquals(1440, placement.bounds.height)
    }

    @Test
    fun restoreWithoutAnyScreensReturnsDefaultAtOrigin() {
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 200, y = 200, width = 1000, height = 700, maximized = true),
            emptyList(),
        )
        assertEquals(
            WindowBounds(0, 0, DesktopWindowBoundsPolicy.DEFAULT_WIDTH, DesktopWindowBoundsPolicy.DEFAULT_HEIGHT),
            placement.bounds,
        )
        // Headless resolution never claims to be maximised.
        assertFalseMaximized(placement.maximized)
    }

    @Test
    fun restoreCentersWhenOnlyOneCoordinateIsStored() {
        val placement = DesktopWindowBoundsPolicy.restore(
            stored(x = 100, y = null, width = 1000, height = 700),
            listOf(primary),
        )
        assertEquals((1920 - 1000) / 2, placement.bounds.x)
        assertEquals((1080 - 700) / 2, placement.bounds.y)
    }

    @Test
    fun restoreCentersALargeWindowThatOnlyFitsOnASmallScreen() {
        val small = ScreenRect(0, 0, 900, 700)
        val placement = DesktopWindowBoundsPolicy.restore(stored(), listOf(small))
        // Default 1280x800 is wider/taller than the screen; centring must fit it.
        assertEquals(900, placement.bounds.width)
        assertEquals(700, placement.bounds.height)
        assertEquals(0, placement.bounds.x)
        assertEquals(0, placement.bounds.y)
    }

    @Test
    fun captureAcceptsAValidReachableBounds() {
        val captured = DesktopWindowBoundsPolicy.capture(
            WindowBounds(300, 200, 1000, 700),
            maximized = false,
            screens = listOf(primary),
        )
        assertEquals(StoredWindow(300, 200, 1000, 700, false), captured)
    }

    @Test
    fun captureCarriesTheMaximizedFlag() {
        val captured = DesktopWindowBoundsPolicy.capture(
            WindowBounds(300, 200, 1000, 700),
            maximized = true,
            screens = listOf(primary),
        )
        assertTrue(captured!!.maximized)
    }

    @Test
    fun captureRefusesASubMinimumWidth() {
        assertNull(
            DesktopWindowBoundsPolicy.capture(
                WindowBounds(0, 0, DesktopWindowBoundsPolicy.MIN_WIDTH - 1, 700),
                maximized = false,
                screens = listOf(primary),
            ),
        )
    }

    @Test
    fun captureRefusesASubMinimumHeight() {
        assertNull(
            DesktopWindowBoundsPolicy.capture(
                WindowBounds(0, 0, 1000, DesktopWindowBoundsPolicy.MIN_HEIGHT - 1),
                maximized = false,
                screens = listOf(primary),
            ),
        )
    }

    @Test
    fun captureRefusesAnOffScreenPosition() {
        assertNull(
            DesktopWindowBoundsPolicy.capture(
                WindowBounds(5000, 5000, 1000, 700),
                maximized = false,
                screens = listOf(primary),
            ),
        )
    }

    @Test
    fun captureAcceptsAWindowNudgedMostlyOffTheBottomButStillGrabbable() {
        // Only 60px of height on screen, above the 48px grabbable threshold.
        val captured = DesktopWindowBoundsPolicy.capture(
            WindowBounds(200, 1020, 1000, 700),
            maximized = false,
            screens = listOf(primary),
        )
        assertEquals(WindowBounds(200, 1020, 1000, 700).x, captured!!.x)
    }

    @Test
    fun captureRefusesAWindowWithTooLittleGrabbableHeight() {
        // Only 40px on screen, below the 48px threshold: unreachable.
        assertNull(
            DesktopWindowBoundsPolicy.capture(
                WindowBounds(200, 1040, 1000, 700),
                maximized = false,
                screens = listOf(primary),
            ),
        )
    }

    @Test
    fun capturedBoundsAreThemselvesRestorableUnchanged() {
        // The invariant the two functions share: anything capture accepts, restore
        // honours verbatim. This is what makes "save only after validation" safe.
        val captured = DesktopWindowBoundsPolicy.capture(
            WindowBounds(150, 90, 1100, 720),
            maximized = false,
            screens = listOf(primary),
        )!!
        val placement = DesktopWindowBoundsPolicy.restore(captured, listOf(primary))
        assertEquals(WindowBounds(150, 90, 1100, 720), placement.bounds)
    }

    @Test
    fun screenRectRejectsANonPositiveSize() {
        assertThrows(IllegalArgumentException::class.java) {
            ScreenRect(0, 0, 0, 100)
        }
    }

    @Test
    fun screenRectExposesRightAndBottomEdges() {
        val screen = ScreenRect(100, 50, 800, 600)
        assertEquals(900, screen.right)
        assertEquals(650, screen.bottom)
    }

    private fun assertFalseMaximized(value: Boolean) {
        val maximized = value
        assertEquals(false, maximized)
    }
}
