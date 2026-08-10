package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.OnboardingPolicy
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.SyncConfirmCopy
import dev.bee.kanjianki.presentation.SyncOutcome
import dev.bee.kanjianki.presentation.UiText

/**
 * What a host's sync engine looks like from the shared graph's side.
 *
 * A port rather than a direct `:sync-engine` dependency, and deliberately so:
 * constructing `PlatformNeutralSyncEngine` needs asset readers, a queue-planner factory,
 * and post-commit effects that are all host-shaped — Android reads assets from an
 * `AssetManager` and re-arms an `AlarmManager`; desktop reads files and has no widget to
 * refresh. So the *composition* stays in each host while the *sequencing* — confirm, run,
 * cancel, report — lives here once, which is the part the two hosts must not disagree
 * about.
 *
 * [run] blocks for the length of a whole sync. It is called on whatever background thread
 * the host schedules, never on the UI thread.
 */
fun interface HostSyncEngine {
    /**
     * Runs one sync to completion, reporting progress, and returns what it did.
     *
     * [progress] is called from the engine's own thread, so an implementation must not
     * assume a UI dispatcher; [HostSyncDriver] is what hops it back. It is valid only for
     * the duration of this call — an implementation that retains it and reports later is
     * ignored rather than trusted, because a progress bar must not reappear over an idle
     * screen.
     *
     * [cancelled] is polled cooperatively rather than the thread being interrupted:
     * the provider reads and SQLite writes in the middle of a sync ignore interruption,
     * so a checked flag is the only cancellation that actually stops one.
     */
    fun run(progress: (SyncRunProgress) -> Unit, cancelled: () -> Boolean): SyncRunResult
}

/**
 * How far a running sync has got, as the portable projection a shell can show.
 *
 * A fraction rather than the engine's `(stage, scanned, total)` triple, because that
 * triple names `:sync-engine` types the shared graph cannot see and the only thing a
 * shell does with it is draw a bar. [label] is already-resolved copy: the host owns the
 * wording, since the stage names come from its own resources.
 */
data class SyncRunProgress(
    val label: UiText = UiText.EMPTY,
    /** Completion in `0.0..1.0`, or null while the total is still unknown. */
    val fraction: Float? = null,
) {
    init {
        require(fraction == null || fraction in 0f..1f) {
            "sync progress fraction must be within 0..1, was $fraction"
        }
    }
}

/**
 * What a finished sync did, as the shared graph sees it.
 *
 * [Skipped] is its own case rather than a [Failed]: "another run already holds the lock"
 * and "you cancelled it" are not things the user did wrong, and collapsing them into a
 * failure is exactly how they would end up rendered as an error with a retry button.
 */
sealed interface SyncRunResult {
    data class Succeeded(val importedKanji: Int) : SyncRunResult {
        init {
            require(importedKanji >= 0) { "imported kanji cannot be negative, was $importedKanji" }
        }
    }

    data class Failed(val failure: PresentationFailure) : SyncRunResult

    /** Another run held the lock, or the user cancelled this one. */
    data class Skipped(val message: UiText = UiText.EMPTY) : SyncRunResult
}

/**
 * Sequences the three [KaniAction.Provider] sync actions for either host.
 *
 * Sync was the last piece of product behavior still reachable only through Android's
 * `MainActivity` chain. The shared graph already modelled all of it —
 * [OnboardingPolicy.syncConfirmation] produces the confirm effect, `ShellReducer` routes
 * the three actions, the shared shell renders the dialog — but no host *drove* it:
 * `AndroidShellHost.driveProvider` matched `RequestSync`/`ConfirmSync`/`CancelSync` and
 * did nothing, and desktop had no branch at all. So a thin-host-only app could not sync,
 * and deleting the chain would have shipped one. This is the driver both hosts share, so
 * that neither can quietly disagree about the invariant below.
 *
 * **A sync is never started without the user answering the dialog.** [request] only
 * builds a confirmation; [confirm] is the only thing that starts an engine. The split is
 * load-bearing past politeness: CLAUDE.md requires the repaired-note tag write-back to be
 * manual-confirm-only with the proposal count visible, and the automatic sync runner is
 * not authorized to perform it at all. That count reaches the user through the
 * [SyncConfirmCopy] body, which is why the copy is supplied per-request rather than
 * captured once in the constructor — a captured copy would go stale and understate the
 * number of notes about to be tagged.
 *
 * Single-flight: a second [request] or [confirm] while a run is in flight is dropped, not
 * queued. Two concurrent syncs would race on the same provider and the same local write
 * transaction, and the engine's own lock would reject the second anyway — never asking
 * twice is better than surfacing that rejection to the user. Note that a [cancel] does
 * *not* end the run early for this purpose: the engine still holds the provider and the
 * database until it reaches a safe point, so [isSyncing] stays true until it reports back.
 * Reporting idle sooner would only let the user start a sync the engine then rejects.
 *
 * Threading: every method here, and every callback it invokes, runs on the host's UI
 * dispatcher. Only [HostSyncEngine.run] executes elsewhere; its progress and its result
 * are hopped back through [post]. That is what lets [progress] and [isSyncing] be plain
 * fields a shell can read during composition without synchronization.
 */
class HostSyncDriver(
    /**
     * Starts [block] off the calling thread.
     *
     * Host-supplied rather than a coroutine scope owned here, because Android runs this
     * on the container's io `Executor` while desktop uses a `CoroutineScope` on
     * `Dispatchers.IO`. A test passes a manual queue and the whole driver becomes
     * deterministic.
     */
    private val launch: (block: () -> Unit) -> Unit,
    /**
     * Runs [block] on the UI dispatcher.
     *
     * Separate from [launch] because the engine's thread is where progress is produced
     * and the UI thread is where it is read. Without this hop, [progress] would be a field
     * written by one thread and read by another mid-composition.
     */
    private val post: (block: () -> Unit) -> Unit,
) {
    /**
     * True from [confirm] until the run reports back. Feeds `HomeDashboard.syncing`.
     *
     * UI-dispatcher-confined, so a plain field: [confirm] and [complete] are the only
     * writers and both run there.
     */
    var isSyncing: Boolean = false
        private set

    /** How far the in-flight sync has got, or null when none is running. */
    var progress: SyncRunProgress? = null
        private set

    /**
     * Set by [cancel] on the UI dispatcher, polled by the engine on its own thread.
     *
     * Volatile because those are two different threads and the whole point is that the
     * engine sees the write promptly. Reset by [confirm], which is safe because a new run
     * can only start after the previous one reported — see the single-flight note above.
     */
    @Volatile
    private var cancelled: Boolean = false

    /**
     * The confirmation to enqueue for a requested sync, or null if one is already running.
     *
     * Null means "do nothing" rather than "something went wrong": a user pressing Sync
     * twice is ordinary, and a second dialog over a running sync could only be answered
     * with a no-op.
     */
    fun request(copy: SyncConfirmCopy): KaniEffect.Confirm? {
        if (isSyncing) return null
        return OnboardingPolicy.syncConfirmation(copy)
    }

    /**
     * Starts [engine], the user having confirmed.
     *
     * Only reached by dispatching `KaniAction.Provider.ConfirmSync`, and
     * [OnboardingPolicy.syncConfirmation] is that action's only producer — so no path
     * that skipped the dialog can start an engine.
     *
     * [onComplete] runs on the UI dispatcher. A parameter rather than a constructor field
     * because what happens next is host state this driver has no business holding:
     * reloading the visible route, re-arming reminders, refreshing a widget.
     */
    fun confirm(engine: HostSyncEngine, onComplete: (SyncRunResult) -> Unit) {
        if (isSyncing) return
        isSyncing = true
        cancelled = false
        progress = SyncRunProgress()
        launch {
            val result = try {
                engine.run(
                    progress = { update -> post { publish(update) } },
                    cancelled = { cancelled },
                )
            } catch (failure: Throwable) {
                // A throwing engine is a failed sync, not a dead host: this runs on a
                // background thread, so letting it propagate would take the host's
                // executor down with it and leave isSyncing stuck true -- an app that can
                // never sync again until it is restarted. The user-facing message stays
                // generic and the throwable's own text goes to `diagnostic`, which is
                // documented as logs-only, because a provider stack trace is not copy and
                // can name the user's collection path.
                SyncRunResult.Failed(
                    PresentationFailure(
                        kind = PresentationFailure.Kind.UNKNOWN,
                        message = UiText.Literal("Kani could not finish syncing."),
                        diagnostic = failure.toString(),
                    ),
                )
            }
            post { complete(result, onComplete) }
        }
    }

    /**
     * Asks the running sync to stop, if there is one.
     *
     * Cooperative, so this returns immediately and the run ends at its next safe point;
     * the result still arrives through [confirm]'s `onComplete`. Nothing needs rolling
     * back, because the engine commits once at the end — a cancelled run leaves the
     * previous collection state exactly as it was.
     */
    fun cancel() {
        if (isSyncing) cancelled = true
    }

    /**
     * Publishes [update], unless no sync is running.
     *
     * The guard is about the port, not about ordering: [HostSyncEngine.run] hands out a
     * lambda, and an adapter that retains it — say one wired to a listener that outlives
     * the run — would otherwise put a progress bar back on an idle screen.
     */
    private fun publish(update: SyncRunProgress) {
        if (!isSyncing) return
        progress = update
    }

    /** Records a finished run, ignoring a duplicate report so [onComplete] fires once. */
    private fun complete(result: SyncRunResult, onComplete: (SyncRunResult) -> Unit) {
        if (!isSyncing) return
        isSyncing = false
        cancelled = false
        progress = null
        onComplete(result)
    }

    companion object {
        /**
         * [result] as the [SyncOutcome] the onboarding step reads, or null for no change.
         *
         * A skipped run maps to null rather than to an outcome: nothing happened, so the
         * caller keeps whatever the last real sync reported instead of overwriting a
         * genuine success with a blank.
         */
        fun outcomeOf(result: SyncRunResult): SyncOutcome? = when (result) {
            is SyncRunResult.Succeeded -> SyncOutcome.Succeeded(result.importedKanji)
            is SyncRunResult.Failed -> SyncOutcome.Failed(result.failure)
            is SyncRunResult.Skipped -> null
        }
    }
}
