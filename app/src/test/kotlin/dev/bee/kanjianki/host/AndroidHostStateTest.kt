package dev.bee.kanjianki.host

import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The host↔composition bridge, driven without a window.
 *
 * Every case here is one the on-device gate is bad at reaching: a repeated warm-launch
 * intent, a stale consume racing a newer one, an unrecognized intent. They are cheap to
 * get wrong in a way that fails silently — the composition simply never navigates — so
 * they are pinned on the JVM where the sequencing is observable.
 */
class AndroidHostStateTest {
    @Test
    fun aFreshHostHasNothingPending() {
        val state = AndroidHostState()

        assertNull(state.pendingLaunch)
        assertNull(state.current)
        assertNull(state.restored)
        assertNull(state.initialLaunch)
        // Zero means "no refresh has been asked for", which the scaffold must not treat as
        // a request -- `Lifecycle.Entered` already loads the route it composes.
        assertEquals(0L, state.refreshRequest)
    }

    @Test
    fun aWarmLaunchBecomesPending() {
        val state = AndroidHostState()
        val request = KaniLaunchCodec.request(KaniLaunchCodec.Target.STUDY, null)

        state.warmLaunch(request)

        assertEquals(request, state.pendingLaunch?.request)
    }

    @Test
    fun theSameIntentTwiceIsTwoDistinctPendingLaunches() {
        // The bug this guards: tapping the Study notification, walking back to Home, and
        // tapping it again produces two *equal* KaniLaunchRequests. A consumer keyed on the
        // request alone treats the second as already handled and the user stays on Home.
        val state = AndroidHostState()
        val request = KaniLaunchCodec.request(KaniLaunchCodec.Target.STUDY, null)

        state.warmLaunch(request)
        val first = requireNotNull(state.pendingLaunch)
        state.consume(first)
        state.warmLaunch(request)
        val second = requireNotNull(state.pendingLaunch)

        assertEquals(first.request, second.request)
        assertNotEquals(first.sequence, second.sequence)
    }

    @Test
    fun consumingAStaleLaunchDoesNotDropANewerOne() {
        // The race: the composition is mid-navigation for intent A when intent B arrives.
        // Clearing unconditionally would discard B and lose the newer tap entirely.
        val state = AndroidHostState()

        state.warmLaunch(KaniLaunchCodec.request(KaniLaunchCodec.Target.STUDY, null))
        val stale = requireNotNull(state.pendingLaunch)
        state.warmLaunch(KaniLaunchCodec.request(KaniLaunchCodec.Target.STATS, null))
        val newer = requireNotNull(state.pendingLaunch)
        state.consume(stale)

        assertEquals(newer, state.pendingLaunch)

        state.consume(newer)
        assertNull(state.pendingLaunch)
    }

    @Test
    fun anUnrecognizedIntentIsIgnoredRatherThanSendingTheUserHome() {
        // KaniLaunchCodec yields null for an ordinary launch and for a malformed one alike.
        // Neither should move a user off the screen they are on.
        val state = AndroidHostState()
        state.warmLaunch(KaniLaunchCodec.request(KaniLaunchCodec.Target.STATS, null))
        val existing = state.pendingLaunch

        state.warmLaunch(null)

        assertEquals(existing, state.pendingLaunch)
    }

    @Test
    fun eachRefreshRequestIsDistinct() {
        // Two grants in a row are two reloads. A boolean would collapse them, and the
        // scaffold keys a LaunchedEffect on the value -- so it has to change every time.
        val state = AndroidHostState()

        state.requestRefresh()
        val first = state.refreshRequest
        state.requestRefresh()

        assertEquals(1L, first)
        assertNotEquals(first, state.refreshRequest)
    }

    @Test
    fun theRestoredAndLaunchDestinationsAreCarriedVerbatim() {
        // Both are read once, in onCreate, and handed to the shell's constructor; the
        // precedence between them (launch wins) is ShellReducer's, not this class's.
        val launch = KaniLaunchCodec.request(KaniLaunchCodec.Target.STUDY, null)
        val restored = KaniDestination.Browse(query = "water")
        val state = AndroidHostState(initialLaunch = launch, restored = restored)

        assertEquals(launch, state.initialLaunch)
        assertEquals(restored, state.restored)
    }

    @Test
    fun theCurrentDestinationIsWhateverTheScaffoldLastPublished() {
        val state = AndroidHostState()

        state.current = KaniDestination.Stats
        state.current = KaniDestination.Games

        // Read by `onSaveInstanceState`, which runs outside composition: whatever was on
        // screen at the moment the process was killed is what gets saved.
        assertEquals(KaniDestination.Games, state.current)
    }
}
