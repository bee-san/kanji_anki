package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppLifecycleState
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopAppLifecycleTest {
    @Test
    fun startsBackgroundedSoNothingAssumesTheUserIsWatching() {
        assertEquals(AppLifecycleState.BACKGROUND, DesktopAppLifecycle().currentState())
    }

    @Test
    fun focusAndBlurMoveBetweenForegroundAndBackground() {
        val lifecycle = DesktopAppLifecycle()
        val observed = ArrayList<AppLifecycleState>()
        lifecycle.observe(observed::add)

        lifecycle.onWindowFocused()
        lifecycle.onWindowBackgrounded()
        lifecycle.onWindowFocused()

        assertEquals(
            listOf(
                // The immediate delivery on subscribe, then each transition.
                AppLifecycleState.BACKGROUND,
                AppLifecycleState.FOREGROUND,
                AppLifecycleState.BACKGROUND,
                AppLifecycleState.FOREGROUND,
            ),
            observed,
        )
    }

    @Test
    fun anObserverLearnsTheCurrentStateWithoutWaitingForATransition() {
        // On desktop the next transition may be hours away, so a subscriber that
        // only saw future events would act on a stale assumption until then.
        val lifecycle = DesktopAppLifecycle(initialState = AppLifecycleState.FOREGROUND)
        val observed = ArrayList<AppLifecycleState>()

        lifecycle.observe(observed::add)

        assertEquals(listOf(AppLifecycleState.FOREGROUND), observed)
    }

    @Test
    fun repeatingTheCurrentStateNotifiesNobody() {
        // Window systems repeat focus events; a reminder observer that re-evaluated
        // on each one would post duplicates.
        val lifecycle = DesktopAppLifecycle()
        val observed = ArrayList<AppLifecycleState>()
        lifecycle.observe(observed::add)

        lifecycle.onWindowBackgrounded()
        lifecycle.onWindowFocused()
        lifecycle.onWindowFocused()

        assertEquals(
            listOf(AppLifecycleState.BACKGROUND, AppLifecycleState.FOREGROUND),
            observed,
        )
    }

    @Test
    fun stoppingIsTerminalSoNoLateFocusEventRearmsAClosingApp() {
        val lifecycle = DesktopAppLifecycle()
        val observed = ArrayList<AppLifecycleState>()
        lifecycle.observe(observed::add)

        lifecycle.onStopping()
        lifecycle.onWindowFocused()
        lifecycle.onWindowBackgrounded()

        assertEquals(AppLifecycleState.STOPPING, lifecycle.currentState())
        assertEquals(
            listOf(AppLifecycleState.BACKGROUND, AppLifecycleState.STOPPING),
            observed,
        )
    }

    @Test
    fun closingASubscriptionStopsDeliveryToItAlone() {
        val lifecycle = DesktopAppLifecycle()
        val kept = ArrayList<AppLifecycleState>()
        val dropped = ArrayList<AppLifecycleState>()
        lifecycle.observe(kept::add)
        val subscription = lifecycle.observe(dropped::add)

        subscription.close()
        lifecycle.onWindowFocused()

        assertEquals(
            listOf(AppLifecycleState.BACKGROUND, AppLifecycleState.FOREGROUND),
            kept,
        )
        assertEquals(listOf(AppLifecycleState.BACKGROUND), dropped)
    }

    @Test
    fun anObserverMayReadLifecycleStateFromInsideItsOwnCallback() {
        // Notifying under the instance lock would deadlock a callback that touches
        // the lifecycle again -- which a tray or reminder observer plausibly does.
        val lifecycle = DesktopAppLifecycle()
        val seen = ArrayList<AppLifecycleState>()
        lifecycle.observe { seen.add(lifecycle.currentState()) }

        lifecycle.onWindowFocused()

        assertEquals(
            listOf(AppLifecycleState.BACKGROUND, AppLifecycleState.FOREGROUND),
            seen,
        )
    }
}
