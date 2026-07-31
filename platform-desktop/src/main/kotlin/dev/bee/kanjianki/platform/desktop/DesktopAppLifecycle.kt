package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppLifecycle
import dev.bee.kanjianki.platform.AppLifecycleState
import dev.bee.kanjianki.platform.PlatformSubscription
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Desktop's [AppLifecycle]: the window host reports focus and visibility changes,
 * and [DesktopShutdownCoordinator] reports the one-way move to
 * [AppLifecycleState.STOPPING].
 *
 * Android gets these transitions from the framework. On desktop the mapping is a
 * choice, and it is this one: a window that is open but unfocused or minimized is
 * [AppLifecycleState.BACKGROUND], not stopping. Kani uses BACKGROUND to mean "the
 * user is not looking at this, so a reminder is worth posting and a periodic
 * refresh can be deferred" — treating an unfocused window as still-foreground
 * would silence every reminder for a user who leaves Kani open behind their
 * browser, which is most users on a desktop.
 *
 * [AppLifecycleState.STOPPING] is terminal. Once entered, a later focus event
 * cannot move the app back: shutdown has already begun releasing the profile
 * lock and closing the database, and an observer that re-armed a background task
 * against a closing profile is exactly the kind of shutdown-race this class
 * exists to prevent.
 */
class DesktopAppLifecycle(
    initialState: AppLifecycleState = AppLifecycleState.BACKGROUND,
) : AppLifecycle {
    /**
     * One registration, wrapped so it is removable by identity.
     *
     * `CopyOnWriteArrayList.remove` compares with `equals`, and two distinct
     * observers can compare equal — a method reference on a collection is the easy
     * case, since Kotlin's adapted function references compare their receivers,
     * and two collections holding the same elements are equal. Removing by equality
     * would then close the wrong subscription and silently stop delivering to a
     * live subscriber. A subscription handle identifies *one* registration, so the
     * wrapper gives every registration its own identity.
     */
    private class Registration(val observer: (AppLifecycleState) -> Unit)

    private val observers = CopyOnWriteArrayList<Registration>()

    @Volatile
    private var state: AppLifecycleState = initialState

    override fun currentState(): AppLifecycleState = state

    /**
     * Registers [observer] and immediately delivers the current state.
     *
     * The immediate delivery is what lets a subscriber be correct without racing:
     * a tray or reminder observer registered while the window is already
     * backgrounded would otherwise wait for the next transition, which on desktop
     * might not come for hours.
     */
    override fun observe(observer: (AppLifecycleState) -> Unit): PlatformSubscription {
        val registration = Registration(observer)
        observers.add(registration)
        observer(state)
        return PlatformSubscription {
            observers.removeAll { it === registration }
        }
    }

    /** Reports that the window gained focus. Ignored once stopping. */
    fun onWindowFocused() {
        moveTo(AppLifecycleState.FOREGROUND)
    }

    /** Reports that the window lost focus, was minimized, or was hidden to tray. */
    fun onWindowBackgrounded() {
        moveTo(AppLifecycleState.BACKGROUND)
    }

    /** Reports the start of shutdown. Terminal; further transitions are ignored. */
    fun onStopping() {
        moveTo(AppLifecycleState.STOPPING)
    }

    private fun moveTo(next: AppLifecycleState) {
        val notify = synchronized(this) {
            if (state == AppLifecycleState.STOPPING || state == next) {
                false
            } else {
                state = next
                true
            }
        }
        // Observers are notified outside the lock: a reminder or widget observer
        // may itself read lifecycle state, and holding the lock across an
        // arbitrary callback is how a UI thread deadlocks against a worker.
        if (notify) {
            observers.forEach { registration -> registration.observer(next) }
        }
    }
}
