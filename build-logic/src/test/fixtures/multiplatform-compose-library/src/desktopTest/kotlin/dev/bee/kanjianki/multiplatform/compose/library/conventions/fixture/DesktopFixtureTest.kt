package dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopFixtureTest {
    @Test
    fun desktopTestSurfaceCanUseDesktopMain() {
        assertEquals("Kani shared UI foundation", desktopFoundationLabel())
        assertEquals("Kani shared UI foundation", commonTestFoundationLabel())
    }
}
