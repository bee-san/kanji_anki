package dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidDeviceFixtureTest {
    @Test
    fun deviceTestSurfaceCanUseAndroidMainAndCommonTest() {
        assertEquals("Kani shared UI foundation", androidFoundationLabel())
        assertEquals("Kani shared UI foundation", commonTestFoundationLabel())
    }
}
