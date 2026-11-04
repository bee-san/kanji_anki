package dev.bee.kanjianki.hostpresentation

/**
 * What a host does when the UI throws: keep the window, or let the process die.
 *
 * A desktop window has no equivalent of Android's process restart. If a Compose
 * composition throws and nothing catches it, the AWT event thread unwinds, the window
 * disappears, and the user is left with no window, no message, and — if the throw
 * happened mid-review — no idea whether their answer was saved. The point of a boundary
 * is not to hide bugs; it is to make the failure *legible* and recoverable, because a
 * vanished window is the least informative outcome available.
 *
 * The decision is a pure function of the failure so it can be tested without a display,
 * which matters: a crash handler that itself throws is a bug you find at the worst
 * possible moment. Rendering the outcome is the host's job.
 *
 * Two failures are deliberately *not* contained:
 *
 * - [Error] and its subclasses. An `OutOfMemoryError` or `StackOverflowError` means the
 *   JVM cannot be relied on to run the recovery path, and a `LinkageError` means the
 *   code on disk does not match what is running. Catching those and drawing a friendly
 *   screen produces an app that appears to work while being unable to save anything.
 * - Cancellation. A coroutine cancellation is normal control flow — a route left, a
 *   sync abandoned — and reporting it as a crash would show the user a fault where
 *   they simply navigated away.
 */
object CrashBoundaryPolicy {
    /** What the host should do with a failure that escaped the UI. */
    enum class Action {
        /** Show the recovery screen; the window stays open. */
        SHOW_RECOVERY,

        /** Re-throw: the JVM is not in a state where recovery can be trusted. */
        RETHROW,

        /** Ignore: normal control flow, not a failure. */
        IGNORE,
    }

    /**
     * A contained failure, described without leaking what the user was working on.
     *
     * The type name only, never `message`. An exception message routinely carries the
     * thing that failed: a file path (so a home directory, so a username), a SQL
     * fragment, a kanji or sentence from the user's own collection. This text is bound
     * for a screen the user may screenshot into a bug report, so it says what broke,
     * not what they were studying. [DatabaseBackupPolicy.sanitizedDiagnosticLine] takes
     * the same position for the same reason.
     */
    data class Report(
        val typeName: String,
        val recoverable: Boolean,
    ) {
        /** A single line safe to display or log. */
        val summary: String get() = "Kani hit an unexpected error ($typeName)."
    }

    /**
     * Decides how to treat [failure].
     *
     * [isCancellation] is injected rather than tested against `CancellationException`
     * here, because this module is plain JVM and must not depend on kotlinx-coroutines
     * to answer a question the caller already knows the answer to.
     */
    fun decide(
        failure: Throwable,
        isCancellation: (Throwable) -> Boolean = { false },
    ): Action = when {
        isCancellation(failure) -> Action.IGNORE
        failure is Error -> Action.RETHROW
        else -> Action.SHOW_RECOVERY
    }

    /**
     * Describes [failure] for display, with the type name sanitized.
     *
     * An anonymous or synthetic class has a blank `simpleName`, and a boundary that
     * rendered "Kani hit an unexpected error ()." would look broken at exactly the
     * moment the user needs to trust it — so a blank name falls back to the honest
     * generic. Lambdas throwing is a common enough shape for this to matter.
     */
    fun report(failure: Throwable): Report = Report(
        typeName = failure.javaClass.simpleName.ifBlank { failure.javaClass.name.ifBlank { "Error" } },
        recoverable = failure !is Error,
    )

    /**
     * Runs [block], returning null when a contained failure was reported through
     * [onFailure] instead.
     *
     * The order is load-bearing: [decide] runs *before* [onFailure], so an `Error`
     * propagates without the boundary first trying to render a recovery screen inside a
     * JVM that may be out of memory. A failure inside [onFailure] itself is allowed to
     * propagate rather than be swallowed — a reporting path that silently fails leaves
     * the window blank with no trace of why, which is the outcome this exists to avoid.
     */
    fun <T> guard(
        onFailure: (Report) -> Unit,
        isCancellation: (Throwable) -> Boolean = { false },
        block: () -> T,
    ): T? = try {
        block()
    } catch (failure: Throwable) {
        when (decide(failure, isCancellation)) {
            Action.IGNORE -> null
            Action.RETHROW -> throw failure
            Action.SHOW_RECOVERY -> {
                onFailure(report(failure))
                null
            }
        }
    }
}
