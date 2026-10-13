package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.data.desktop.DesktopWindowBoundsPolicy
import dev.bee.kanjianki.shell.DESKTOP_MINIMUM_WINDOW_HEIGHT
import dev.bee.kanjianki.shell.DESKTOP_MINIMUM_WINDOW_WIDTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [DesktopWindowBoundsPolicy]'s minimum size to the shared shell's, which it
 * duplicates as plain `Int`s.
 *
 * The duplication is not avoidable where the policy lives: `:data-desktop` has no
 * Compose dependency, so it cannot name a `Dp`. It is also not safe to leave
 * unpinned, and the drift is silent in a specific way — lowering the shell's
 * minimum would leave the policy refusing to persist window sizes the shell now
 * supports, and raising it would let the policy restore a window the shell has no
 * usable layout for. Neither fails to compile and neither looks wrong at the call
 * site.
 *
 * This test lives in `:desktop-app` because that is the only module that depends on
 * both, so it is also the only place the two values can be compared at all.
 */
class DesktopWindowBoundsPolicyMinimumTest {
    @Test
    fun policyMinimumMatchesTheSharedShellMinimum() {
        assertEquals(
            DESKTOP_MINIMUM_WINDOW_WIDTH.value.toInt(),
            DesktopWindowBoundsPolicy.MIN_WIDTH,
        )
        assertEquals(
            DESKTOP_MINIMUM_WINDOW_HEIGHT.value.toInt(),
            DesktopWindowBoundsPolicy.MIN_HEIGHT,
        )
    }

    @Test
    fun thePinnedMinimumIsTheDocumented480By600() {
        // Stated literally as well as relatively: if both sides were changed together
        // the comparison above would still pass, and 480x600 is the size the shell's
        // own layout coverage is written against.
        assertEquals(480, DesktopWindowBoundsPolicy.MIN_WIDTH)
        assertEquals(600, DesktopWindowBoundsPolicy.MIN_HEIGHT)
    }

    @Test
    fun theFirstRunSizeIsTheOneTheWindowUsedBeforeItWasRestorable() {
        // 1280x800 was `DesktopFoundationWindow`'s hardcoded initial size. The window
        // now takes its first-run size from the policy instead of keeping its own copy,
        // so this pins first-run placement against drifting while restore logic is
        // edited -- it is not a second source of truth.
        assertEquals(1280, DesktopWindowBoundsPolicy.DEFAULT_WIDTH)
        assertEquals(800, DesktopWindowBoundsPolicy.DEFAULT_HEIGHT)
    }

    @Test
    fun theDefaultSizeIsNotSmallerThanTheMinimumItMustSatisfy() {
        assertTrue(DesktopWindowBoundsPolicy.DEFAULT_WIDTH >= DesktopWindowBoundsPolicy.MIN_WIDTH)
        assertTrue(DesktopWindowBoundsPolicy.DEFAULT_HEIGHT >= DesktopWindowBoundsPolicy.MIN_HEIGHT)
    }
}
