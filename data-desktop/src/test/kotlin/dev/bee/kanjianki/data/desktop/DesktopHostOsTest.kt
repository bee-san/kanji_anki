package dev.bee.kanjianki.data.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopHostOsTest {
    @Test
    fun recognizesTheVendorStringsEachPlatformActuallyReports() {
        // The real values, not invented ones: "Mac OS X" is what every JVM
        // through 17 reports and "macOS" is what newer ones do, so matching only
        // one of the two would put a Mac profile under the Linux layout.
        assertEquals(DesktopStorageLayout.Os.WINDOWS, DesktopHostOs.of("Windows 11"))
        assertEquals(DesktopStorageLayout.Os.WINDOWS, DesktopHostOs.of("Windows Server 2022"))
        assertEquals(DesktopStorageLayout.Os.MACOS, DesktopHostOs.of("Mac OS X"))
        assertEquals(DesktopStorageLayout.Os.MACOS, DesktopHostOs.of("macOS"))
        assertEquals(DesktopStorageLayout.Os.MACOS, DesktopHostOs.of("Darwin"))
        assertEquals(DesktopStorageLayout.Os.LINUX, DesktopHostOs.of("Linux"))
    }

    @Test
    fun anUnknownOrAbsentOsNameFallsBackToTheXdgLayout() {
        // An unrecognized Unix gets XDG, which is right there and wrong nowhere
        // else; the absent/blank cases exist because `os.name` is a system
        // property and a stripped JVM may not set it.
        assertEquals(DesktopStorageLayout.Os.LINUX, DesktopHostOs.of("FreeBSD"))
        assertEquals(DesktopStorageLayout.Os.LINUX, DesktopHostOs.of("SunOS"))
        assertEquals(DesktopStorageLayout.Os.LINUX, DesktopHostOs.of(null))
        assertEquals(DesktopStorageLayout.Os.LINUX, DesktopHostOs.of("   "))
    }

    @Test
    fun theRunningHostResolvesToOneOfTheThreeLayouts() {
        // Whatever this test runs on, `current()` must answer; the assertion is
        // that it consults `os.name` rather than that CI is Linux.
        assertEquals(
            DesktopHostOs.of(System.getProperty("os.name")),
            DesktopHostOs.current(),
        )
    }
}
