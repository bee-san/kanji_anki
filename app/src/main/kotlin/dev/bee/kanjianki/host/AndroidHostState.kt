package dev.bee.kanjianki.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchRequest

/**
 * The state the Android host owns across a composition, in both directions.
 *
 * The activity has two jobs the composition cannot do for it — persist the visible
 * destination when the process is about to die, and deliver a warm-launch intent that
 * arrives after `onCreate` — and each needs a value to cross the boundary. Holding both
 * here rather than in the scaffold keeps the composable declarative and keeps this a
 * plain object a JVM test can drive without a window.
 */
internal class AndroidHostState(
    /** The launch this activity was created for, from its `onCreate` intent. */
    val initialLaunch: KaniLaunchRequest? = null,
    /** The destination restored from saved instance state, or null to start at Home. */
    val restored: KaniDestination? = null,
) {
    /**
     * A warm-launch intent awaiting navigation, newest last.
     *
     * Compose state, not a plain field, and load-bearingly so: `onNewIntent` runs after
     * the composition exists, so the only way a new intent reaches the screen is for a
     * composable reading this to be invalidated by the write. A plain `var` here compiles
     * and silently never navigates — the exact failure mode that hid a broken Study
     * feedback gate behind a passing test suite.
     *
     * Carries a [PendingLaunch.sequence] because a repeat of the *same* request must
     * still navigate: tapping the Study notification, walking back to Home, and tapping
     * it again produces two equal [KaniLaunchRequest]s, and a consumer keyed on the
     * request alone would treat the second as already handled and stay on Home.
     */
    var pendingLaunch: PendingLaunch? by mutableStateOf(null)
        private set

    /**
     * How many times the host has asked the visible route to reload, newest value last.
     *
     * Compose state for the same reason [pendingLaunch] is: a permission result arrives
     * long after the composition was built. A counter rather than a boolean because two
     * grants in a row are two reloads, and rather than an action queue because the only
     * thing a host result can ask for is "re-read what you are showing" — the route
     * decides what that means.
     */
    var refreshRequest: Long by mutableStateOf(0L)
        private set

    /**
     * The destination currently on screen, published by the scaffold for saved state.
     *
     * Deliberately *not* Compose state: nothing composes on it. It is written during
     * composition and read in `onSaveInstanceState`, both on the main thread, and making
     * it observable would invalidate the very composable that writes it.
     */
    var current: KaniDestination? = null

    private var sequence: Long = 0L

    /**
     * Records a warm-launch intent, superseding any restored destination.
     *
     * A genuine intent is newer than the marker `onCreate` restored, which is why
     * `MainActivityBase.onNewIntent` clears its three restore fields before handling one.
     * Here the restored destination was already consumed when the shell was constructed,
     * so superseding it is just a matter of navigating.
     *
     * An unrecognized intent — [request] null — is ignored rather than treated as a
     * request to go Home: `KaniLaunchCodec` yields null for an ordinary launch and for a
     * malformed one alike, and neither should move a user off the screen they are on.
     */
    fun warmLaunch(request: KaniLaunchRequest?) {
        val target = request ?: return
        sequence += 1
        pendingLaunch = PendingLaunch(sequence, target)
    }

    /** Clears [pendingLaunch] if [launch] is still the newest one, so a race cannot drop a newer intent. */
    fun consume(launch: PendingLaunch) {
        if (pendingLaunch?.sequence == launch.sequence) {
            pendingLaunch = null
        }
    }

    /**
     * Asks the visible route to reload, for a host result the composition cannot see.
     *
     * A granted AnkiDroid permission changes what the provider probe reports and so what
     * every route renders; the old host handled this by re-rendering Home or the Import &
     * sync screen by name. Bumping a counter instead means the reload follows whatever the
     * user is actually on, and a route the shared graph adds later needs no new branch.
     */
    fun requestRefresh() {
        refreshRequest += 1
    }

    /** A warm-launch request plus the monotonic sequence that makes a repeat distinct. */
    internal data class PendingLaunch(val sequence: Long, val request: KaniLaunchRequest)
}
