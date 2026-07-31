package dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopFixtureTest {
    @Test
    fun desktopTestSurfaceCanUseDesktopMain() {
        assertEquals("Kani shared UI foundation", desktopFoundationLabel())
        assertEquals("Kani shared UI foundation", commonTestFoundationLabel())
    }

    @Test
    fun desktopTestSurfaceCanComposeSharedUi() {
        // Proves the convention puts Skiko's native runtime on the desktop test
        // classpath. Without it this dies in a static initializer, not an
        // assertion, so compiling the module is not evidence it can render.
        assertEquals("Kani shared UI foundation", renderedFoundationLabel())
    }
}
