package dev.bee.kanjianki.desktop.conventions.fixture

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopFixtureTest {
    @Test
    fun exposesFoundationLabel() {
        assertEquals("Kani desktop foundation", foundationLabel())
    }
}
