package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppLogger
import dev.bee.kanjianki.platform.error
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs Kani's desktop shutdown once, in reverse registration order, and does not
 * stop at the first failure.
 *
 * Android never needs this: the framework tears the process down and Kani's
 * durability guarantees are written to survive being killed at any instant. A
 * desktop process closes on its own terms and holds things the OS will not clean
 * up for us — an exclusive profile lock, a SQLite connection with a WAL to
 * checkpoint, session-held secrets, granted file paths. Leaving a stale lock
 * behind means the user's next launch reports "profile in use" for a process that
 * no longer exists.
 *
 * Three properties, each load-bearing:
 *
 *  - **Reverse order.** Steps are registered in startup order and run in the
 *    opposite one, because that is the only order in which a step's dependencies
 *    still exist: the profile lock is acquired before the database is opened, so it
 *    must be released after the database is closed.
 *  - **Every step runs even if an earlier one throws.** A shutdown that aborted
 *    halfway would skip exactly the steps that prevent a stale lock. Failures are
 *    collected, logged, and reported; they do not cancel the rest.
 *  - **Once only.** A user can hit the window close button while a "quit?" dialog
 *    from the tray is already shutting down. Running twice would close an already
 *    closed database, and the second failure would look like a bug in the first
 *    step rather than a double call.
 *
 * This is not a substitute for crash safety. Every persistence path Kani has must
 * still survive a `SIGKILL`; this coordinator makes the *ordinary* exit clean, so
 * the crash-recovery paths stay rare rather than routine.
 */
class DesktopShutdownCoordinator(
    private val lifecycle: DesktopAppLifecycle? = null,
    private val logger: AppLogger = AppLogger.NONE,
) {
    /** One named teardown step. The name is what a failure is reported as. */
    class Step(val name: String, val action: () -> Unit)

    /** What happened during a [shutDown] call. */
    data class Outcome(
        /** Step names that completed without throwing, in the order they ran. */
        val completed: List<String>,
        /** Step names that threw, paired with the failure. */
        val failures: List<Pair<String, Throwable>>,
        /** False when a previous call had already run shutdown. */
        val ranNow: Boolean,
    ) {
        val isClean: Boolean get() = failures.isEmpty()
    }

    private val steps = CopyOnWriteArrayList<Step>()

    @Volatile
    private var completed: Outcome? = null

    /** Whether shutdown has already run. */
    val hasShutDown: Boolean get() = completed != null

    /**
     * Registers [action] under [name], to run before every step registered before
     * it.
     *
     * Registering after shutdown has run is a programming error, not a silently
     * ignored call: it means something started a resource during teardown, and that
     * resource would leak with no other signal.
     */
    fun register(name: String, action: () -> Unit): DesktopShutdownCoordinator {
        require(name.isNotBlank()) { "shutdown step name must not be blank" }
        check(completed == null) { "cannot register '$name' after shutdown has run" }
        steps.add(Step(name, action))
        return this
    }

    /**
     * Runs every registered step in reverse order. Subsequent calls return the
     * first call's outcome with [Outcome.ranNow] false.
     */
    fun shutDown(): Outcome {
        val alreadyRun = synchronized(this) {
            completed?.let { return@synchronized it }
            // Marked before any step runs, so a step that itself triggers a close
            // (a window listener firing during teardown) re-enters and returns
            // rather than running the list a second time.
            completed = IN_PROGRESS
            null
        }
        if (alreadyRun != null) return alreadyRun.copy(ranNow = false)

        lifecycle?.onStopping()
        val completedSteps = ArrayList<String>()
        val failures = ArrayList<Pair<String, Throwable>>()
        for (step in steps.asReversed()) {
            try {
                step.action()
                completedSteps.add(step.name)
            } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                // Deliberately every Throwable: this is the last code to run, and a
                // step that throws an Error must still not prevent the profile lock
                // from being released. The failure is reported, never swallowed.
                failures.add(step.name to error)
                logger.error("Shutdown step '${step.name}' failed", error)
            }
        }
        val outcome = Outcome(
            completed = completedSteps,
            failures = failures,
            ranNow = true,
        )
        synchronized(this) { completed = outcome }
        return outcome
    }

    private companion object {
        private val IN_PROGRESS = Outcome(
            completed = emptyList(),
            failures = emptyList(),
            ranNow = true,
        )
    }
}
