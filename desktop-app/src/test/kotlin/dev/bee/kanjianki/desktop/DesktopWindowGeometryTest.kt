package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.data.desktop.DesktopWindowBoundsPolicy
import dev.bee.kanjianki.data.desktop.DesktopWindowBoundsPolicy.ScreenRect
import dev.bee.kanjianki.data.desktop.DesktopWindowBoundsPolicy.WindowBounds
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.desktop.DesktopDeviceSettingsStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The store bridge around [DesktopWindowBoundsPolicy].
 *
 * Run against the real [DesktopDeviceSettingsStore] over a temporary file rather
 * than a fake, because the thing worth proving is that geometry survives a
 * round trip through the store the desktop host actually uses — a fake that
 * accepts whatever it is handed would pass even if the keys were wrong.
 */
class DesktopWindowGeometryTest {
    private lateinit var directory: Path
    private lateinit var settings: DesktopDeviceSettingsStore

    private val singleScreen = listOf(ScreenRect(0, 0, 1920, 1080))

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("kani-window-geometry")
        settings = DesktopDeviceSettingsStore.open(
            directory.resolve(DesktopDeviceSettingsStore.FILE_NAME),
        )
    }

    @After
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun anEmptyStoreReadsAsNothingStoredRatherThanAsZeroes() {
        // Zeroes would be a valid-looking placement at the top-left corner, so the
        // first launch would open there instead of centred.
        val stored = DesktopWindowGeometry.storedWindow(settings)

        assertNull(stored.x)
        assertNull(stored.y)
        assertNull(stored.width)
        assertNull(stored.height)
        assertFalse(stored.maximized)
    }

    @Test
    fun approvedGeometrySurvivesARoundTripThroughTheRealStore() {
        val bounds = WindowBounds(x = 120, y = 80, width = 1000, height = 700)

        assertTrue(DesktopWindowGeometry.persist(settings, bounds, false, singleScreen))
        val stored = DesktopWindowGeometry.storedWindow(settings)

        assertEquals(120, stored.x)
        assertEquals(80, stored.y)
        assertEquals(1000, stored.width)
        assertEquals(700, stored.height)
        assertFalse(stored.maximized)
    }

    @Test
    fun aPersistedPlacementReopensExactlyWhereItWasLeft() {
        // The property that matters to a user: close the window, open it again, and
        // it is where you left it. Asserted through both halves together rather than
        // on the stored keys, because either half alone can be right while the pair
        // disagrees.
        val bounds = WindowBounds(x = 300, y = 200, width = 1100, height = 750)
        DesktopWindowGeometry.persist(settings, bounds, false, singleScreen)

        val placement = DesktopWindowBoundsPolicy.restore(
            stored = DesktopWindowGeometry.storedWindow(settings),
            screens = singleScreen,
        )

        assertEquals(bounds, placement.bounds)
        assertFalse(placement.maximized)
    }

    @Test
    fun theMaximizedFlagSurvivesARoundTrip() {
        DesktopWindowGeometry.persist(
            settings,
            WindowBounds(0, 0, 1920, 1080),
            maximized = true,
            screens = singleScreen,
        )

        assertTrue(DesktopWindowGeometry.storedWindow(settings).maximized)
        assertTrue(
            DesktopWindowBoundsPolicy.restore(
                stored = DesktopWindowGeometry.storedWindow(settings),
                screens = singleScreen,
            ).maximized,
        )
    }

    @Test
    fun rejectedGeometryLeavesTheLastGoodPlacementStored() {
        // The whole reason validation happens before the write. A session that ended
        // in a degenerate state is exactly when the previous placement is worth
        // keeping, so a refused capture must not clear or overwrite it.
        val good = WindowBounds(x = 150, y = 100, width = 1024, height = 768)
        assertTrue(DesktopWindowGeometry.persist(settings, good, false, singleScreen))

        val degenerate = WindowBounds(x = 150, y = 100, width = 4, height = 3)
        assertFalse(DesktopWindowGeometry.persist(settings, degenerate, false, singleScreen))

        val stored = DesktopWindowGeometry.storedWindow(settings)
        assertEquals(150, stored.x)
        assertEquals(1024, stored.width)
        assertEquals(768, stored.height)
    }

    @Test
    fun geometryEntirelyOffEveryScreenIsRefused() {
        val offscreen = WindowBounds(x = 5000, y = 4000, width = 1000, height = 700)

        assertFalse(DesktopWindowGeometry.persist(settings, offscreen, false, singleScreen))
        assertNull(DesktopWindowGeometry.storedWindow(settings).x)
    }

    @Test
    fun aWindowLeftOnAMonitorThatIsGoneReopensOnTheRemainingOne() {
        // The failure this exists for, end to end through the store: geometry saved
        // while a second monitor was attached, restored after it was unplugged.
        val twoScreens = listOf(ScreenRect(0, 0, 1920, 1080), ScreenRect(1920, 0, 1920, 1080))
        val onSecond = WindowBounds(x = 2200, y = 150, width = 1000, height = 700)
        assertTrue(DesktopWindowGeometry.persist(settings, onSecond, false, twoScreens))

        val placement = DesktopWindowBoundsPolicy.restore(
            stored = DesktopWindowGeometry.storedWindow(settings),
            screens = singleScreen,
        )

        assertTrue(
            "expected the window back on the remaining screen, was ${placement.bounds}",
            placement.bounds.x in 0..920,
        )
        assertEquals(1000, placement.bounds.width)
        assertEquals(700, placement.bounds.height)
    }

    @Test
    fun enumeratingScreensNeverThrowsEvenWithNoDisplay() {
        // This runs headless in CI, so the empty list is the case exercised here.
        // Either answer is acceptable; throwing is not, because it would fail startup
        // on a host whose displays cannot be enumerated.
        val screens = DesktopWindowGeometry.attachedScreens()

        assertTrue(screens.all { it.width > 0 && it.height > 0 })
    }

    @Test
    fun aToolkitWithNoAnswerIsNotTreatedAsAMeasurement() {
        // A closing window is exactly when `WindowState` may report NaN or an
        // unspecified position. `NaN.toInt()` is 0 in Kotlin, so without this filter
        // the app would persist (0, 0) -- a placement that passes every validity check
        // the policy makes and silently moves the next launch to the corner.
        assertNull(
            DesktopWindowGeometry.reportedGeometry(
                x = Float.NaN,
                y = Float.NaN,
                width = 1000f,
                height = 700f,
                positionSpecified = true,
            ),
        )
        assertNull(
            DesktopWindowGeometry.reportedGeometry(
                x = 10f,
                y = 20f,
                width = Float.POSITIVE_INFINITY,
                height = 700f,
                positionSpecified = true,
            ),
        )
        assertNull(
            DesktopWindowGeometry.reportedGeometry(
                x = 10f,
                y = 20f,
                width = 1000f,
                height = 700f,
                positionSpecified = false,
            ),
        )
    }

    @Test
    fun theNanCornerCaseWouldOtherwiseHavePersistedTheOrigin() {
        // Names the mechanism explicitly, so the filter above cannot be removed as
        // redundant on the grounds that the policy validates anyway: it does not
        // reject (0, 0), because that is a legitimate placement.
        assertEquals(0, Float.NaN.toInt())
        assertTrue(
            DesktopWindowGeometry.persist(
                settings,
                WindowBounds(0, 0, 1000, 700),
                maximized = false,
                screens = singleScreen,
            ),
        )
    }

    @Test
    fun aUsableReportIsConvertedRatherThanRejected() {
        assertEquals(
            WindowBounds(x = 12, y = 34, width = 1000, height = 700),
            DesktopWindowGeometry.reportedGeometry(
                x = 12.7f,
                y = 34.2f,
                width = 1000.9f,
                height = 700.4f,
                positionSpecified = true,
            ),
        )
    }

    @Test
    fun everyPersistedKeyIsOneOfTheDeclaredWindowKeys() {
        // Guards against a typo'd key silently writing geometry nobody reads back.
        DesktopWindowGeometry.persist(
            settings,
            WindowBounds(10, 20, 1000, 700),
            maximized = true,
            screens = singleScreen,
        )
        val snapshot = settings.snapshot()

        assertTrue(snapshot.contains(DeviceSettingKeys.windowX))
        assertTrue(snapshot.contains(DeviceSettingKeys.windowY))
        assertTrue(snapshot.contains(DeviceSettingKeys.windowWidth))
        assertTrue(snapshot.contains(DeviceSettingKeys.windowHeight))
        assertTrue(snapshot.contains(DeviceSettingKeys.windowMaximized))
    }
}
