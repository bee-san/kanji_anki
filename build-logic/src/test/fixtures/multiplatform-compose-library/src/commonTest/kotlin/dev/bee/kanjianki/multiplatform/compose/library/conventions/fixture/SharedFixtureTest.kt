package dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture

import kotlin.test.Test
import kotlin.test.assertEquals

fun commonTestFoundationLabel(): String = platformNeutralLabel()

class SharedFixtureTest {
    @Test
    fun exposesPlatformNeutralLabel() {
        assertEquals("Kani shared UI foundation", platformNeutralLabel())
    }
}
