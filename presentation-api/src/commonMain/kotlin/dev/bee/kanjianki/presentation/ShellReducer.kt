package dev.bee.kanjianki.presentation

/**
 * Navigation and effect bookkeeping for the shell, as a pure function.
 *
 * Deliberately narrow. This reducer owns the back stack, the selected tab, and
 * the effect queue — ephemeral UI state, nothing else. It does not decide what a
 * Study rating means, which item comes next, or whether a commit succeeded: the
 * authoritative Study snapshot lives in `:application`, and a second place
 * deriving from it would be a second scheduler.
 *
 * Pure and synchronous so a fake host can drive every route in a common test
 * with no dispatcher, clock, or coroutine scope.
 */
object ShellReducer {
    fun reduce(state: ShellState, action: KaniAction): ShellState = when (action) {
        is KaniAction.Navigation.Open -> state.open(action.destination)
        is KaniAction.Navigation.SelectTab -> state.selectTab(action.tab)
        KaniAction.Navigation.Back -> state.back()
        is KaniAction.Consume.Effect -> state.copy(effects = state.effects.consume(action.id))
        KaniAction.Consume.Failure,
        KaniAction.Retry,
        KaniAction.Lifecycle.Entered,
        KaniAction.Lifecycle.Exited,
        KaniAction.Lifecycle.Refresh,
        -> state
    }

    /**
     * The state the app starts in, given how it was asked to open.
     *
     * One function so both hosts agree on the launch stack. A deep-linked
     * destination is *not* pushed onto the ordinary launch stack: it replaces it,
     * because the user did not walk Home -> Stats, they tapped a widget. Back from
     * there then falls through to `current.parent` — already how [back] behaves for
     * a one-entry stack — which is why a widget-launched Detail can still go back
     * without the launch faking a history the user never had.
     *
     * A `null` [request] means an ordinary launch, which is [restored] if the host
     * has restorable state and Home otherwise.
     *
     * When both are present the request wins. Android's startup appears to order
     * these the other way — it checks its recreated route before reading the open
     * extras — but the two never actually meet there: the extras are consumed off
     * the intent on first delivery, so a recreation finds none. Here they can meet,
     * on a desktop host whose session file outlives the process and whose tray can
     * ask for a screen. An explicit ask is newer information than a saved session,
     * so it takes precedence.
     */
    fun launch(
        request: KaniLaunchRequest?,
        restored: KaniDestination? = null,
    ): ShellState {
        val destination = request?.destination ?: restored ?: KaniDestination.Home
        return ShellState(backStack = listOf(destination))
    }

    /**
     * Gates an action on a capability, queueing an explanation when it is absent.
     *
     * Returns the action to dispatch onward, or `null` when the shell absorbed it
     * by explaining instead. Doing this in the shell rather than at each call site
     * is what stops a screen from forgetting the check and offering a dead
     * button.
     */
    fun gate(
        state: ShellState,
        capability: PlatformCapability,
        action: KaniAction,
        explanation: UiText,
    ): Pair<ShellState, KaniAction?> =
        when (val gate = state.capabilities.gate(capability, action)) {
            is CapabilityGate.Allowed -> state to gate.action
            is CapabilityGate.Unavailable -> state.copy(
                effects = state.effects.enqueue(
                    KaniEffect.ShowMessage(message = explanation),
                ),
            ) to null
        }

    private fun ShellState.open(destination: KaniDestination): ShellState = when {
        destination == current -> this

        /**
         * Revisiting a destination already on the stack unwinds to it rather than
         * pushing a duplicate. Without this, Home -> Detail -> Browse -> Detail
         * grows without bound and back walks a loop the user did not take.
         */
        destination in backStack -> copy(
            backStack = backStack.subList(0, backStack.indexOf(destination) + 1),
        )

        else -> copy(backStack = backStack + destination)
    }

    /**
     * Selecting a tab resets to that tab's root and drops the rest of the stack.
     *
     * Re-selecting the current tab from a nested screen returns to its root
     * (Detail -> Home), matching Android's behavior. Re-selecting while already at
     * the root is a no-op, which is why the bottom bar suppresses the click.
     */
    private fun ShellState.selectTab(tab: KaniTab): ShellState {
        val root = tab.root
        return if (current == root) this else copy(backStack = listOf(root))
    }

    /**
     * Back pops the stack, or falls back to the current destination's parent.
     *
     * The parent fallback matters for a deep link or a restored session, where the
     * user arrived at a nested screen with no stack behind them. At a destination
     * with no parent the state is returned unchanged: whether that closes the
     * window or leaves the app is the host's call, and the reducer must not
     * invent a screen to show.
     */
    private fun ShellState.back(): ShellState = when {
        backStack.size > 1 -> copy(backStack = backStack.dropLast(1))
        else -> current.parent?.let { copy(backStack = listOf(it)) } ?: this
    }
}

/**
 * Load-state and effect bookkeeping for one route, as a pure function.
 *
 * Same mandate as [ShellReducer], one route down: it tracks whether content is
 * loading, what failed, and which effects are outstanding. Producing the content
 * is a [RouteContentPort]'s job, and the reducer only records the result.
 */
object RouteReducer {
    /**
     * Applies an action to a route's state.
     *
     * Returns the new state plus the load [RouteIntent] the host should perform, if
     * any. Returning the intent instead of performing it keeps the reducer pure
     * and lets a test assert *that* a reload was requested without running one.
     */
    fun <T> reduce(
        state: RouteState<T>,
        action: KaniAction,
    ): Pair<RouteState<T>, RouteIntent?> = when (action) {
        /**
         * Entering an already-loaded route does not reload it.
         *
         * Returning to Home from a subscreen would otherwise reload on every
         * back press, which on a large collection is a visible stall for content
         * that is already correct. An explicit [KaniAction.Lifecycle.Refresh]
         * still reloads.
         */
        KaniAction.Lifecycle.Entered ->
            if (state.content is Loadable.Idle) {
                state.loading() to RouteIntent.Load
            } else {
                state to null
            }

        KaniAction.Lifecycle.Refresh -> state.loading() to RouteIntent.Load

        /**
         * Leaving clears the effect queue.
         *
         * An effect that was never delivered describes a screen the user is no
         * longer looking at; showing it on return is worse than dropping it.
         */
        KaniAction.Lifecycle.Exited -> state.copy(effects = state.effects.cleared()) to null

        KaniAction.Retry -> state.loading() to RouteIntent.Load

        is KaniAction.Consume.Effect -> state.consumeEffect(action.id) to null

        KaniAction.Consume.Failure -> state.dismissFailure() to null

        is KaniAction.Navigation -> state to null
    }
}

/**
 * Work the host must perform on a reducer's behalf.
 *
 * Only loading, for now. Everything else a route does goes through a
 * [RouteContentPort] call the host makes directly; this exists for the one case
 * the reducer itself has to ask for, because it is the reducer that knows the
 * content went stale.
 */
enum class RouteIntent {
    Load,
}
