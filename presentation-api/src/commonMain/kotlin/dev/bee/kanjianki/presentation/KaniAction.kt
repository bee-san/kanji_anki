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

    /**
     * Asking the collection provider for something.
     *
     * These are the only actions in this file whose meaning differs by host, and
     * they are here rather than in a host module because the *screen* that raises
     * them is shared: onboarding shows one button, and which of these it dispatches
     * is decided by [OnboardingPlan.primaryAction] from portable state. What each
     * one then does — a runtime permission dialog on Android, opening Anki and
     * waiting for the AnkiConnect prompt on desktop — is the host's business.
     *
     * Note what is absent: there is no `Provider.Tag`, `Provider.Suspend`, or
     * anything else that would write scheduling state. Kani's supported write
     * surface is note tags plus the additive Missing Kanji flow, and the tag write
     * rides along inside a user-confirmed [ConfirmSync] rather than being
     * independently dispatchable.
     */
    sealed interface Provider : KaniAction {
        /**
         * Make a collection reachable.
         *
         * Android sends the user to install AnkiDroid; desktop starts or focuses
         * Anki. Both may legitimately do nothing but show instructions, because
         * neither host can install or launch another app unilaterally.
         */
        data object Connect : Provider

        /** Ask for read access: a runtime permission on Android, an AnkiConnect prompt on desktop. */
        data object Authorize : Provider

        /**
         * The user asked to sync.
         *
         * Deliberately *not* the thing that starts a sync. It asks for the
         * confirmation, and [ConfirmSync] is what starts one. Splitting them is what
         * keeps the repaired-note tag write-back manual-confirm-only: there is no
         * action a background runner could dispatch that both skips the dialog and
         * performs the write.
         */
        data object RequestSync : Provider

        /** The user answered the confirmation. This is the only action that starts a sync. */
        data object ConfirmSync : Provider

        /**
         * Stop the sync in progress.
         *
         * Cancellation is cooperative and leaves whatever already committed
         * committed: sync writes in batches and a cancelled run is a shorter run,
         * not a rolled-back one. Nothing needs undoing because a partial import is
         * a valid state that the next sync completes.
         */
        data object CancelSync : Provider
    }

    /**
     * Choosing which kanji Kani practises.
     *
     * Kani-side queue state, not a collection write. Marking a kanji unstudied does
     * not suspend anything in Anki and does not touch scheduling state — the same
     * boundary [Provider] draws, restated here because Browse's checkbox sits directly
     * beside a `SUSPENDED` chip that *is* collection state, and the two are easy to
     * conflate from the screen alone.
     */
    sealed interface Browse : KaniAction {
        /** Mark or unmark one kanji for study. */
        data class SetStudied(val kanji: String, val studied: Boolean) : Browse {
            init {
                require(kanji.isNotBlank()) { "selecting nothing is not a user intent" }
            }
        }

        /**
         * Mark or unmark every row the current query returned.
         *
         * Scoped to the visible result set rather than the whole inventory, which is
         * what Android's "Select all" did. A control that silently enrolled every kanji
         * in the collection would be a very different button wearing the same label.
         */
        data class SetAllStudied(val studied: Boolean) : Browse
    }

    /**
     * The user asked to copy something to the clipboard.
     *
     * An action rather than a screen reaching for a clipboard API, because the
     * reducer owns the effect queue and this becomes a
     * [KaniEffect.CopyToClipboard] there. That keeps the confirmation and the write
     * in one place: a screen that copied directly would have to remember to show its
     * own toast, and half of them would not.
     *
     * [text] is a plain `String` because it is data being moved, not copy being
     * displayed — an Anki search, a diagnostic dump — and resolving it through a
     * resource table would be wrong. [confirmation] is displayed, so it is a
     * [UiText].
     */
    data class RequestCopy(
        val text: String,
        val confirmation: UiText = UiText.EMPTY,
    ) : KaniAction {
        init {
            require(text.isNotEmpty()) { "copying nothing is not a user intent" }
        }
    }

    /**
     * The user saved a mnemonic note for a kanji.
     *
     * Kani-side content, not a collection write: the note lives in Kani's own
     * store, the same boundary [Browse] and [Provider] draw. It is its own action
     * rather than a [RequestCopy]-style effect because it changes persisted state
     * the detail screen then re-reads — the reducer reloads the route the way it
     * does for a [Browse] toggle, so the saved note is on screen from the store
     * rather than from the field it was typed into.
     *
     * [note] is already trimmed by the editor; an empty note is a clear, which the
     * host distinguishes when confirming.
     */
    data class SaveMnemonic(val kanji: String, val note: String) : KaniAction {
        init {
            require(kanji.isNotBlank()) { "a mnemonic is about a kanji" }
        }
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
