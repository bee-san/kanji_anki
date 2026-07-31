package dev.bee.kanjianki.presentation

/**
 * Something the user did, as a value.
 *
 * The Android shell currently passes `() -> Unit` callbacks (`KaniNavActions`
 * holds four of them) and, further down, `Runnable`s. A lambda cannot be
 * compared, logged, replayed, or asserted on, so a test can only check that
 * *something* was invoked. An action can be put in a list and compared, which is
 * what lets a fake host drive a route in a common test.
 *
 * Actions are deliberately shallow: they say what happened, not what to do about
 * it. Deciding what a `RateCurrentTask` means is `:application`'s job — the
 * authoritative Study snapshot lives there, and a reducer that decided ratings
 * would be a second scheduler.
 */
sealed interface KaniAction {
    /** Navigation, shared by every route. */
    sealed interface Navigation : KaniAction {
        data class Open(val destination: KaniDestination) : Navigation

        /** The top-level tab bar or rail. Re-selecting the current tab is a no-op the reducer resolves. */
        data class SelectTab(val tab: KaniTab) : Navigation

        /** System back, the back affordance, or a desktop window's back binding. */
        data object Back : Navigation
    }

    /** Screen-lifecycle actions the host raises, not the user. */
    sealed interface Lifecycle : KaniAction {
        /** The route became visible and should load if it has not. */
        data object Entered : Lifecycle

        /** The route stopped being visible. In-flight work may be abandoned. */
        data object Exited : Lifecycle

        /** An explicit user refresh, which keeps existing content on screen. */
        data object Refresh : Lifecycle
    }

    /**
     * Acknowledging something the host already showed.
     *
     * A one-shot effect stays queued until the host says it landed, so an effect
     * cannot be lost to a recomposition or replayed on a process restart. These
     * are the acknowledgements.
     */
    sealed interface Consume : KaniAction {
        /** The effect with this id was delivered and must not be delivered twice. */
        data class Effect(val id: Long) : Consume

        /** The visible failure was dismissed; clear it without retrying. */
        data object Failure : Consume
    }

    /** Retry the work that produced the currently visible failure. */
    data object Retry : KaniAction
}

/**
 * Where a screen sends its actions.
 *
 * A `fun interface` rather than a `Flow` so a common test can dispatch
 * synchronously and assert on the resulting state with no dispatcher, no clock,
 * and no coroutine scope.
 */
fun interface ActionDispatcher {
    fun dispatch(action: KaniAction)
}
