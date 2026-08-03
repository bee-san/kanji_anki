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
     * Grading, revealing, and advancing a study card.
     *
     * The user-facing intents of a study session. Like [Browse], these are Kani-side —
     * a grade is a review in Kani's own scheduler, never a write to the collection.
     * What a [Grade]'s rating *means* — which interval, which rung move — is
     * `:application`'s to decide; the reducer only records that the card was graded and
     * lets the host perform the review, exactly as it does for a provider action. A
     * reducer that scheduled from a rating would be a second scheduler.
     *
     * The guarded desktop controls dispatch these, and the reducer's idempotence is
     * what makes a key-repeat or a double-click safe: a second [Grade] on an
     * already-answered card is dropped by the session's own `acceptsGrade` gate before
     * it reaches here.
     */
    sealed interface Study : KaniAction {
        /**
         * Submit a grade for the visible card.
         *
         * [rating] is a scheduler wire name (`good`/`again`/`hard`/`easy`). The UI's
         * Pass/Fail map to `good`/`again` at the boundary that built the action, not
         * here — the reducer treats the string as opaque.
         */
        data class Grade(val rating: String) : Study {
            init {
                require(rating.isNotBlank()) { "a grade needs a rating" }
            }
        }

        /** Reveal a self-graded card's answer before grading it. */
        data object Reveal : Study

        /** Advance past the one-card feedback gate to the next card. */
        data object Continue : Study

        /** Reverse the last committed card, where the host reports one it can undo. */
        data object Undo : Study
    }

    /**
     * Playing a kanji game.
     *
     * Kani-side, like [Study]: a game answer scores in the engine, never touches the
     * collection or the scheduler. The reducer records the action and lets the host
     * advance the engine, exactly as it does for a study grade — deciding what a game
     * answer means is the engine's, not the reducer's.
     */
    sealed interface Game : KaniAction {
        /** Start a game mode by its id. */
        data class Start(val modeId: String) : Game {
            init {
                require(modeId.isNotBlank()) { "a game mode needs an id" }
            }
        }

        /** Answer the current round with a chosen value. */
        data class Answer(val answer: String) : Game {
            init {
                require(answer.isNotBlank()) { "answering nothing is not a user intent" }
            }
        }

        /** Advance past the result to the next round or back to the menu. */
        data object Continue : Game
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

    /**
     * The Missing Kanji flow: scan, filter, and the batch destinations.
     *
     * [ScanIntent] is the one primary button whose meaning the host decides (scan,
     * scan again, grant permission, install, retry). [AddToKani] and [Remove] are
     * Kani-side queue edits; [CreateAnkiNotes] is the one capability-gated provider
     * write — additive notes in Kani's own model/deck, per CLAUDE.md — and [ExportCsv]
     * is the always-available fallback that the host turns into a file-picker save.
     * None of these writes a user's existing notes or scheduling state.
     */
    sealed interface MissingKanji : KaniAction {
        /** The primary button; what it does is [MissingKanjiScreen.primaryAction]'s host meaning. */
        data object ScanIntent : MissingKanji

        /** Cancel a scan in progress. */
        data object CancelScan : MissingKanji

        /** Dismiss the operation-result dialog. */
        data object DismissResult : MissingKanji

        /** Admit the selected kanji into Kani's queue (local). */
        data class AddToKani(val literals: Set<String>) : MissingKanji

        /** Remove one admitted kanji from Kani's queue (local). */
        data class Remove(val literal: String) : MissingKanji {
            init {
                require(literal.isNotBlank()) { "removing nothing is not a user intent" }
            }
        }

        /** Create additive Anki notes for the selected kanji in the named deck. */
        data class CreateAnkiNotes(val literals: Set<String>, val deckName: String) : MissingKanji

        /** Export the selected kanji as CSV through the host's file picker. */
        data class ExportCsv(val literals: Set<String>) : MissingKanji
    }

    /**
     * Editing a setting.
     *
     * A small, stable vocabulary rather than one action per preference, because
     * Settings is the app's largest surface (~40 Android panels) and is ported one
     * section at a time — a shared enum would force this file to grow with every
     * section, and a screen that dispatched a typed setting would pull the settings
     * schema into `:presentation-api`. Each carries a stable [key]/[id] the host maps
     * to the concrete `Settings` field; validation, bounds, and persistence stay in
     * `:core`/`:application`, the same boundary [Provider] and [Study] draw.
     *
     * Note what is absent: nothing here writes the collection. Every settings edit is
     * Kani-side device state.
     */
    sealed interface Settings : KaniAction {
        /** Flip a boolean setting identified by [key]. */
        data class SetToggle(val key: String, val enabled: Boolean) : Settings {
            init {
                require(key.isNotBlank()) { "a toggle needs a key" }
            }
        }

        /** Choose one [optionId] of the multi-value setting identified by [key]. */
        data class SetChoice(val key: String, val optionId: String) : Settings {
            init {
                require(key.isNotBlank()) { "a choice needs a key" }
                require(optionId.isNotBlank()) { "a choice needs an option" }
            }
        }

        /**
         * A named settings command the host resolves.
         *
         * Reset the ladder, recompute stats, export a backup, stage a restore, export
         * diagnostics: the actions that are not a simple field edit. [id] is the stable
         * command name; whether it needs a picker, a confirmation, or a restart is the
         * host's business, decided from [SettingsControl] and its own capabilities.
         */
        data class Command(val id: String) : Settings {
            init {
                require(id.isNotBlank()) { "a command needs an id" }
            }
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
