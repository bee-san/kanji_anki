package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.SyncConfirmCopy
import dev.bee.kanjianki.presentation.SyncOutcome
import dev.bee.kanjianki.presentation.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins the sequencing both hosts share, and the one invariant neither may relax: an
 * engine only ever starts after the user answered the dialog.
 *
 * The driver is deliberately dispatcher-agnostic, so these tests drive it with a manual
 * queue rather than a real executor: the interesting cases are all about *ordering* — a
 * cancelled run reporting after a resync started, a second confirm arriving mid-run — and
 * a real thread pool would make them timing-dependent instead of asserted.
 */
class HostSyncDriverTest {
    private val copy = SyncConfirmCopy(
        title = UiText.Literal("Sync cards"),
        body = UiText.Literal("Import from AnkiDroid. 12 notes will be tagged kani_repaired."),
        confirmLabel = UiText.Literal("Sync"),
        dismissLabel = UiText.Literal("Cancel"),
    )

    @Test
    fun requestingASyncOnlyAsksAndStartsNothing() {
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        val engine = RecordingEngine()

        val confirm = driver.request(copy)
        queue.drain()

        // The invariant, asserted at its narrowest point: a request produced a dialog and
        // no engine work at all -- not "no result yet", but never invoked.
        assertEquals(0, engine.runs)
        assertFalse(driver.isSyncing)
        assertEquals(KaniAction.Provider.ConfirmSync, confirm?.confirm)
        assertEquals(copy.title, confirm?.title)
        assertEquals(copy.body, confirm?.body)
    }

    @Test
    fun theConfirmationIsNotStyledAsDestructive() {
        // The write surface behind it is additive, idempotent note tags. A destructive
        // style would dress a routine import as a warning and train the user past it.
        val driver = HostSyncDriver(launch = {}, post = {})

        assertEquals(false, driver.request(copy)?.isDestructive)
    }

    @Test
    fun theRepairedTagCountReachesTheDialogTheUserAnswers() {
        // CLAUDE.md requires the repaired-note tag write-back to be manual-confirm-only
        // with the proposal count visible. Visible *in the dialog*: a count shown only on
        // the screen behind it is disclosed, not consented to.
        val driver = HostSyncDriver(launch = {}, post = {})

        val body = driver.request(copy)?.body
        assertTrue("<$body> states the count", (body as? UiText.Literal)?.text?.contains("12") == true)
    }

    @Test
    fun confirmingRunsTheEngineAndReportsWhatItDid() {
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        val engine = RecordingEngine(result = SyncRunResult.Succeeded(importedKanji = 42))
        var completed: SyncRunResult? = null

        driver.confirm(engine) { completed = it }
        assertTrue("syncing while the engine runs", driver.isSyncing)
        queue.drain()

        assertEquals(1, engine.runs)
        assertEquals(SyncRunResult.Succeeded(importedKanji = 42), completed)
        assertFalse("idle once it reported", driver.isSyncing)
        assertNull("progress cleared", driver.progress)
    }

    @Test
    fun aSecondConfirmDuringARunIsDroppedRatherThanQueued() {
        // Two concurrent syncs would race on the same provider and the same write
        // transaction. Dropping is right; queueing would just run the second one late,
        // which is a sync the user asked for once and got twice.
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        val engine = RecordingEngine()
        var completions = 0

        driver.confirm(engine) { completions++ }
        driver.confirm(engine) { completions++ }
        queue.drain()

        assertEquals("one engine run", 1, engine.runs)
        assertEquals("one completion", 1, completions)
    }

    @Test
    fun requestingWhileARunIsInFlightAsksNothing() {
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)

        driver.confirm(RecordingEngine()) {}

        assertNull("no second dialog over a running sync", driver.request(copy))
        queue.drain()
        assertEquals("and asking works again once idle", KaniAction.Provider.ConfirmSync, driver.request(copy)?.confirm)
    }

    @Test
    fun progressIsPublishedWhileTheRunIsInFlight() {
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        val engine = HostSyncEngine { progress, _ ->
            progress(SyncRunProgress(UiText.Literal("Reading notes"), fraction = 0.5f))
            SyncRunResult.Succeeded(importedKanji = 1)
        }
        val seen = mutableListOf<SyncRunProgress?>()

        driver.confirm(engine) {}
        assertEquals("starts at zero-knowledge progress", SyncRunProgress(), driver.progress)
        queue.drain { seen += driver.progress }

        assertTrue(
            "the engine's update was published: $seen",
            seen.contains(SyncRunProgress(UiText.Literal("Reading notes"), fraction = 0.5f)),
        )
        assertNull("and cleared at the end", driver.progress)
    }

    @Test
    fun cancellingIsVisibleToTheEngineAndStillReportsBack() {
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        var observed: Boolean? = null
        val engine = HostSyncEngine { _, cancelled ->
            observed = cancelled()
            SyncRunResult.Skipped(UiText.Literal("Cancelled"))
        }
        var completed: SyncRunResult? = null

        driver.confirm(engine) { completed = it }
        driver.cancel()
        queue.drain()

        assertEquals("the engine polled the flag and saw it set", true, observed)
        assertEquals(SyncRunResult.Skipped(UiText.Literal("Cancelled")), completed)
        assertFalse(driver.isSyncing)
    }

    @Test
    fun cancellingWhenNothingRunsDoesNothing() {
        val driver = HostSyncDriver(launch = {}, post = {})

        driver.cancel()

        assertFalse(driver.isSyncing)
    }

    @Test
    fun aCancelledRunStaysSyncingUntilTheEngineActuallyStops() {
        // Cancellation is cooperative, so the engine still holds the provider and the write
        // transaction after the flag is set. Reporting idle at cancel time would let the
        // user start a sync the engine then rejects -- a self-inflicted error dialog.
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        val engine = RecordingEngine(result = SyncRunResult.Skipped())

        driver.confirm(engine) {}
        driver.cancel()

        assertTrue("still syncing right after the cancel", driver.isSyncing)
        assertNull("and no second sync can start", driver.request(copy))
        queue.drain()
        assertFalse("idle only once the engine reported", driver.isSyncing)
    }

    @Test
    fun aRetainedProgressCallbackCannotPutABarBackOnAnIdleScreen() {
        // The port hands the engine a lambda. An adapter that wires it to a listener
        // outliving the run -- easy to do, since the real engine's listener is a field --
        // would otherwise report progress after the screen went idle.
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        val leaky = LeakyEngine()

        driver.confirm(leaky) {}
        queue.drain()
        assertFalse("the run is over", driver.isSyncing)

        leaky.reportLate(SyncRunProgress(UiText.Literal("Stale"), fraction = 0.9f))
        queue.drain()

        assertNull("no progress over an idle screen", driver.progress)
    }

    @Test
    fun aCancelledFlagDoesNotLeakIntoTheNextRun() {
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        var observed: Boolean? = null

        driver.confirm(RecordingEngine(result = SyncRunResult.Skipped())) {}
        driver.cancel()
        queue.drain()
        driver.confirm(
            HostSyncEngine { _, cancelled ->
                observed = cancelled()
                SyncRunResult.Succeeded(importedKanji = 1)
            },
        ) {}
        queue.drain()

        assertEquals("the fresh run starts uncancelled", false, observed)
    }

    @Test
    fun aThrowingEngineIsAFailedSyncAndTheDriverStaysUsable() {
        // A background throw that escaped would take the host's executor with it and leave
        // `running` set forever: an app that can never sync again until it is restarted.
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        var completed: SyncRunResult? = null

        driver.confirm(HostSyncEngine { _, _ -> throw IllegalStateException("provider died") }) {
            completed = it
        }
        queue.drain()

        val failure = (completed as? SyncRunResult.Failed)?.failure
        assertEquals(PresentationFailure.Kind.UNKNOWN, failure?.kind)
        assertFalse("driver is idle again", driver.isSyncing)

        // And a following sync works, which is the property the catch exists for.
        val engine = RecordingEngine(result = SyncRunResult.Succeeded(importedKanji = 3))
        driver.confirm(engine) { completed = it }
        queue.drain()
        assertEquals(SyncRunResult.Succeeded(importedKanji = 3), completed)
    }

    @Test
    fun aThrowingEnginesDetailIsDiagnosticOnlyAndNeverTheMessage() {
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        var completed: SyncRunResult? = null

        driver.confirm(
            HostSyncEngine { _, _ -> throw IllegalStateException("/home/someone/collection.anki2 locked") },
        ) { completed = it }
        queue.drain()

        val failure = (completed as? SyncRunResult.Failed)?.failure
        val message = (failure?.message as? UiText.Literal)?.text.orEmpty()
        assertFalse("the path is not in the user's copy: <$message>", message.contains("/home/someone"))
        assertTrue("but it is kept for logs", failure?.diagnostic?.contains("collection.anki2") == true)
    }

    @Test
    fun aSkippedRunLeavesTheLastRealOutcomeAlone() {
        // Null, not an outcome: nothing happened. Mapping it to anything else would let a
        // cancelled retry overwrite a genuine "imported 900 kanji" with a blank.
        assertNull(HostSyncDriver.outcomeOf(SyncRunResult.Skipped()))
        assertEquals(
            SyncOutcome.Succeeded(importedKanji = 900),
            HostSyncDriver.outcomeOf(SyncRunResult.Succeeded(importedKanji = 900)),
        )
        val failure = PresentationFailure(kind = PresentationFailure.Kind.PROVIDER_UNAVAILABLE)
        assertEquals(SyncOutcome.Failed(failure), HostSyncDriver.outcomeOf(SyncRunResult.Failed(failure)))
    }

    @Test
    fun aNegativeImportCountIsRejectedAtItsSource() {
        // Same guard SyncOutcome.Succeeded carries, kept here so a bad count fails where
        // the engine produced it rather than three hops later in an onboarding step.
        try {
            SyncRunResult.Succeeded(importedKanji = -1)
            fail("expected a negative import count to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("negative"))
        }
    }

    @Test
    fun anOutOfRangeProgressFractionIsRejected() {
        for (bad in listOf(-0.01f, 1.01f, 42f)) {
            try {
                SyncRunProgress(fraction = bad)
                fail("expected $bad to be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("0..1"))
            }
        }
        // Null stays legal: "total not yet known" is a real state, not an error.
        assertNull(SyncRunProgress(fraction = null).fraction)
        assertEquals(0f, SyncRunProgress(fraction = 0f).fraction)
        assertEquals(1f, SyncRunProgress(fraction = 1f).fraction)
    }

    @Test
    fun theCompletionCallbackIsTheHostsAndTheDriverHoldsNoHostState() {
        // Each confirm carries its own callback, so a host can reload a different route
        // per sync without the driver knowing any route names.
        val queue = ManualQueue()
        val driver = HostSyncDriver(launch = queue::enqueue, post = queue::enqueue)
        val calls = mutableListOf<String>()

        driver.confirm(RecordingEngine()) { calls += "first" }
        queue.drain()
        driver.confirm(RecordingEngine()) { calls += "second" }
        queue.drain()

        assertEquals(listOf("first", "second"), calls)
    }

    /** A queue standing in for both dispatchers, so ordering is asserted, not raced. */
    private class ManualQueue {
        private val pending = ArrayDeque<() -> Unit>()

        fun enqueue(block: () -> Unit) {
            pending += block
        }

        /** Runs everything queued, including work queued while draining. */
        fun drain(afterEach: () -> Unit = {}) {
            while (pending.isNotEmpty()) {
                pending.removeFirst().invoke()
                afterEach()
            }
        }
    }

    private class RecordingEngine(
        private val result: SyncRunResult = SyncRunResult.Succeeded(importedKanji = 0),
    ) : HostSyncEngine {
        var runs: Int = 0
            private set

        override fun run(
            progress: (SyncRunProgress) -> Unit,
            cancelled: () -> Boolean,
        ): SyncRunResult {
            runs++
            return result
        }
    }

    /**
     * An engine that retains the progress callback past its run, the way a real adapter
     * wired to a long-lived listener field would.
     */
    private class LeakyEngine : HostSyncEngine {
        private var reporter: ((SyncRunProgress) -> Unit)? = null

        override fun run(
            progress: (SyncRunProgress) -> Unit,
            cancelled: () -> Boolean,
        ): SyncRunResult {
            reporter = progress
            return SyncRunResult.Succeeded(importedKanji = 0)
        }

        fun reportLate(update: SyncRunProgress) {
            reporter?.invoke(update)
        }
    }
}
