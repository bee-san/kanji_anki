package dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

fun commonTestFoundationLabel(): String = platformNeutralLabel()

class SharedFixtureTest {
    @Test
    fun exposesPlatformNeutralLabel() {
        assertEquals("Kani shared UI foundation", platformNeutralLabel())
    }
}

/**
 * Composes [SharedFixture] and reports the string it rendered.
 *
 * Called from both host test source sets rather than run as a `commonTest` case,
 * because composing needs host-specific plumbing that the convention supplies:
 * Skiko's native runtime on desktop, and ui-test-manifest's host activity plus
 * Robolectric on Android. If either wiring regresses, the corresponding host's
 * fixture test fails here instead of in a downstream module.
 */
@OptIn(ExperimentalTestApi::class)
fun renderedFoundationLabel(): String {
    var rendered = ""
    runComposeUiTest {
        setContent {
            SharedFixture()
        }
        onNodeWithText("Kani shared UI foundation").assertExists()
        rendered = "Kani shared UI foundation"
    }
    return rendered
}
