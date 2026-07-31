package dev.bee.kanjianki.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest

/**
 * Composes [content] once, on every target this module builds for.
 *
 * Shared UI has to be provable without a window or an emulator, and
 * `runComposeUiTest` is what makes that true: it drives a real composition with a
 * test frame clock. Tests that only need a value that was in scope (which
 * palette, which shape) assign it to a local from inside [content]; tests about
 * what the user can see and reach use the semantics tree instead.
 *
 * The `v2` entry point is deliberate. v1 ran coroutines eagerly; v2 queues them
 * on a `StandardTestDispatcher` the way production does, so a composable that
 * only works because a launched effect happened to run synchronously fails here
 * instead of on a user's machine.
 */
@OptIn(ExperimentalTestApi::class)
fun renderOnce(content: @Composable () -> Unit) {
    runComposeUiTest {
        setContent(content)
    }
}
