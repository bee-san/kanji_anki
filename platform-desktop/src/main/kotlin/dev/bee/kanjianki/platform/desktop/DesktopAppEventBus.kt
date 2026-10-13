package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppEvent
import dev.bee.kanjianki.platform.AppEventBus
import dev.bee.kanjianki.platform.PlatformSubscription
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop's [AppEventBus]: an in-process fan-out for committed-work events.
 *
 * This is the mechanism behind the "refresh after committed sync, study, and reminder
 * evaluation; do not poll the database" rule. Android has the same rule and satisfies it
 * with a system broadcast; a desktop app is one process, so the bus is just a list of
 * observers — but the *ordering* guarantee is what makes it a contract rather than a
 * convenience: an event is published only after the work it describes has committed, so
 * an observer that reloads on `STUDY_COMMITTED` cannot read a half-written review.
 *
 * Three properties, each load-bearing:
 *
 * - **A failing observer never stops the others, and never fails the publisher.** A
 *   publish happens at the end of a committed transaction. If a stats-cache refresh
 *   throws and that propagated, it would surface as a *sync failure* to the user — a
 *   committed sync reported as broken is worse than a stale panel.
 * - **Re-entrant publishing is allowed.** An observer handling `SYNC_COMMITTED` may
 *   legitimately publish `SETTINGS_CHANGED`, and `CopyOnWriteArrayList` iterates a
 *   snapshot so that neither deadlocks nor skips observers.
 * - **Unsubscribing is idempotent and safe mid-dispatch.** A route that closes its
 *   subscription while an event is being delivered must not remove someone else's, and
 *   must not throw on a second close — subscriptions are closed from teardown paths that
 *   run more than once.
 *
 * Deliberately not a `SharedFlow`: `:platform-desktop` has a single reviewed edge, to
 * `:platform-contracts`, and adding kotlinx-coroutines to it to hold a list of callbacks
 * would widen that for no gain. The contract is a callback, so the implementation is one.
 */
class DesktopAppEventBus(
    private val onObserverFailure: (AppEvent, Throwable) -> Unit = { _, _ -> },
) : AppEventBus {
    private val observers = CopyOnWriteArrayList<(AppEvent) -> Unit>()

    override fun publish(event: AppEvent) {
        // A snapshot iteration: an observer that subscribes or unsubscribes during
        // dispatch affects the next publish, not this one. Delivering to a
        // just-registered observer mid-dispatch would hand it an event describing work
        // that committed before it existed.
        observers.forEach { observer ->
            try {
                observer(event)
            } catch (failure: Throwable) {
                // Reported, not swallowed silently and not rethrown. The publisher is a
                // committed transaction; one broken listener must not make it look failed.
                reportSafely(event, failure)
            }
        }
    }

    override fun observe(observer: (AppEvent) -> Unit): PlatformSubscription {
        observers.add(observer)
        val closed = AtomicBoolean(false)
        return PlatformSubscription {
            // Guarded so a second close cannot remove an *identical* observer belonging
            // to someone else — two routes can legitimately register equal lambdas, and
            // `remove` deletes the first match.
            if (closed.compareAndSet(false, true)) {
                observers.remove(observer)
            }
        }
    }

    /** The number of live observers. For tests, to prove a close actually detached. */
    val observerCount: Int get() = observers.size

    private fun reportSafely(event: AppEvent, failure: Throwable) {
        // The reporter is host-supplied, so it can throw too. A failure here has nowhere
        // left to go: rethrowing would defeat the isolation this method exists to provide.
        runCatching { onObserverFailure(event, failure) }
    }
}
