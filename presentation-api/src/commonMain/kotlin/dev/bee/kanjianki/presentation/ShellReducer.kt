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

        /**
         * A copy request becomes a queued effect rather than a direct clipboard write.
         *
         * Translating it here is what pairs the write with its confirmation: a screen
         * that reached for the clipboard itself would have to remember to show a
         * toast, and half of them would not. The queue also means the write survives
         * a recomposition between the tap and the host handling it.
         */
        is KaniAction.RequestCopy -> state.copy(
            effects = state.effects.enqueue(
                KaniEffect.CopyToClipboard(
                    text = action.text,
                    confirmation = action.confirmation,
                ),
            ),
        )

        /**
         * Provider actions do not touch shell state.
         *
         * They are host work — a permission dialog, launching Anki, starting a sync
         * — and none of it changes the back stack or the selected tab. Listing them
         * here rather than adding an `else` branch keeps this `when` exhaustive, so
         * a future action that *does* need shell handling fails to compile until
         * someone decides what it means.
         */
        KaniAction.Provider.Connect,
        KaniAction.Provider.Authorize,
        KaniAction.Provider.RequestSync,
        KaniAction.Provider.ConfirmSync,
        KaniAction.Provider.CancelSync,
        KaniAction.Consume.Failure,
        KaniAction.Retry,
        KaniAction.Lifecycle.Entered,
        KaniAction.Lifecycle.Exited,
        KaniAction.Lifecycle.Refresh,
        -> state

        /**
         * Study selection is route content, not shell state.
         *
         * Which kanji Kani practises changes the Browse list the user is looking at
         * and the study badge count, and both are recomputed from `:application`'s
         * data. A shell that adjusted the badge itself would be guessing at a number
         * the next load would overwrite.
         *
         * A mnemonic save is the same shape of change one route down — persisted
         * content the detail screen re-reads — so it too leaves the shell alone.
         */
        is KaniAction.Browse -> state
        is KaniAction.SaveMnemonic -> state

        /**
         * Study grading is route content, not shell state.
         *
         * A grade advances the session and moves the study badge count, both
         * recomputed from `:application`'s snapshot on the next load. The shell must
         * not guess at either: which card is next is the scheduler's call, and a badge
         * the shell adjusted itself would be overwritten by the reload the route asks
         * for. Listing the cases keeps the `when` exhaustive.
         */
        is KaniAction.Study -> state

        /**
         * Game play is route content, not shell state.
         *
         * A game answer scores in the engine and the next round is re-derived from it,
         * exactly like a study grade — the shell's stack and tabs are untouched.
         */
        is KaniAction.Game -> state
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

        /**
         * A route-level copy queues on the route rather than on the shell.
         *
         * Because the route is what the user is looking at: `Lifecycle.Exited` clears
         * this queue, so a copy confirmation for a screen they navigated away from is
         * dropped instead of surfacing over the next one.
         */
        is KaniAction.RequestCopy -> state.copy(
            effects = state.effects.enqueue(
                KaniEffect.CopyToClipboard(
                    text = action.text,
                    confirmation = action.confirmation,
                ),
            ),
        ) to null

        /**
         * A provider action does not itself change the route's load state.
         *
         * A sync that finishes will reload the route, but through the host calling
         * the content port again — not through the dispatch that asked for it.
         * Reloading here would clear the screen the moment the user pressed Sync,
         * before anything had actually changed.
         */
        is KaniAction.Provider -> state to null

        is KaniAction.Navigation -> state to null

        /**
         * A study-selection change reloads the route, keeping the list on screen.
         *
         * Unlike a provider action, this one has already happened by the time the
         * route hears about it, and it changes what the current query returns — a
         * kanji marked unstudied leaves the study queue, so the summary above the list
         * is now wrong. [RouteState.loading] keeps the visible rows while the reload
         * runs, so ticking a checkbox does not blank the list under the user's finger.
         */
        is KaniAction.Browse -> state.loading() to RouteIntent.Load

        /**
         * Saving a mnemonic reloads the route, keeping the detail on screen.
         *
         * By the time the route hears this the note is written, and the detail's
         * "stuck" helper text and the saved note both come from the store — so a
         * reload is how the screen reflects what was just persisted.
         * [RouteState.loading] keeps the visible detail while it runs, so saving does
         * not blank the card the user is reading.
         */
        is KaniAction.SaveMnemonic -> state.loading() to RouteIntent.Load

        /**
         * A study grade, continue, or undo reloads the route, keeping the card up.
         *
         * Each has already changed the session by the time the route hears it — a
         * grade committed a review, Continue advanced the gate, Undo reversed the last
         * card — and the next card comes from re-reading `:application`'s snapshot.
         * [RouteState.loading] keeps the answered card visible while the reload runs,
         * so grading does not blank the screen between cards.
         *
         * [KaniAction.Study.Reveal] is the exception: it turns over a self-graded
         * card's answer, which the surface already holds, so it changes nothing the
         * host must reload and is left as-is.
         */
        KaniAction.Study.Reveal -> state to null
        is KaniAction.Study -> state.loading() to RouteIntent.Load

        /**
         * A game action reloads the route from the advanced engine state.
         *
         * Start, Answer, and Continue each change what the games screen shows — a new
         * round, a graded result, the next question — and the host re-derives it from
         * the engine. [RouteState.loading] keeps the current screen up while it does.
         */
        is KaniAction.Game -> state.loading() to RouteIntent.Load
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
