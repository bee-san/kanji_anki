package dev.bee.kanjianki.host

import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import dev.bee.kanjianki.SourceBindingRecoveryUi
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.syncapi.SourceBindingReason
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * The live AnkiDroid release gate, on the thin host.
 *
 * This is the port of `MainActivityInstrumentedTest`'s
 * `testManualSyncButtonWorksAgainstLiveAnkiDroid` — the class#method CLAUDE.md names as the
 * gate that must pass before a release touching sync or provider behaviour. It had to move
 * before the `MainActivity*` chain can be deleted, because it launched that activity
 * directly and drove its views; deleting the chain with the gate still pointed at it would
 * remove the project's only live-provider validation.
 *
 * Everything is driven through [UiDevice] rather than through activity-typed helpers, which
 * is what makes it host-agnostic: the taps find text on screen by package, and every
 * assertion that matters reads the committed database rather than the view tree. The old
 * version already worked this way for the hard parts (the first-bind confirmation is a
 * `UiDevice` scroll-and-tap); the activity type only appeared in the scenario and the text
 * waits, so those are the only things this had to re-express.
 *
 * Opt-in: without `-e kanjiLiveAnkiDroid true` this assumes out, exactly as before, because
 * it needs a real AnkiDroid install and a real collection. Run it as CLAUDE.md documents,
 * naming this class instead of the old one.
 */
class KaniHostLiveSyncInstrumentedTest {
    @Test
    fun theSyncButtonOnTheThinHostImportsFromLiveAnkiDroid() {
        Assume.assumeTrue("Live AnkiDroid fixture is opt-in.", liveEnabled())

        ActivityScenario.launch(KaniHostActivity::class.java).use {
            tapSyncEntryPoint()
            tapDeviceText(HomeTextCopy.syncDialogPositiveLabel())
            confirmFirstCollectionBinding()

            val startedAt = System.currentTimeMillis()
            val status = waitForLiveSyncImport(startedAt)

            assertEquals("success", status.status)
            assertTrue(status.finishedAt >= startedAt)

            // Store-level, not view-level: the gate is about what the sync committed, and a
            // rendered row can be right while the write that produced it was not.
            LocalStore(context()).use { store ->
                assertFalse("a live sync should produce dashboard rows", store.dashboardRows().isEmpty())
                assertFalse("a live sync should produce study items", store.studyItems().isEmpty())
                // Goal 81 live gate: the reading-usage table is rebuilt from the real
                // collection and the reading-aware flags are annotated onto real items.
                assertTrue(
                    "kanji_reading_usage should be non-empty after a real sync",
                    store.kanjiReadingUsageRowCount() > 0,
                )
                val items = store.studyItems()
                assertTrue(
                    "at least one study item should carry hasKanjiReading",
                    items.any { it.hasKanjiReading },
                )
                // The deterministic CI fixture carries only the 橋・箸・端 homophone set, so
                // it is checked by name; a real collection is checked in aggregate.
                val deterministicFixture = isDeterministicFixture()
                val readingKanjiItems = if (deterministicFixture) {
                    items.filter { it.kanji in setOf("橋", "箸", "端") }
                } else {
                    items
                }
                assertTrue(
                    if (deterministicFixture) {
                        "the 橋・箸・端 fixture should produce a study item with hasReadingKanji"
                    } else {
                        "at least one real-collection study item should carry hasReadingKanji"
                    },
                    readingKanjiItems.any { it.hasReadingKanji },
                )
                assertTrue(
                    "at least one study item should carry hasSentenceReading",
                    items.any { it.hasSentenceReading },
                )
            }

            // Last, and on the device rather than the store: the user has to be *told* the
            // sync finished, and a committed run the UI never reported is still a bug.
            waitForDeviceText(HomeTextCopy.syncCompleteTitle(), UI_STEP_TIMEOUT_MILLIS)
        }
    }

    /**
     * Taps whichever sync affordance Home is showing.
     *
     * Two labels because Home shows the provider-specific one once a source is known and the
     * generic one before that; which appears depends on the emulator's state, so the test
     * accepts either rather than assuming a fixture.
     */
    private fun tapSyncEntryPoint() {
        waitForDeviceText("Sync", UI_STEP_TIMEOUT_MILLIS)
        if (tapDeviceTextIfPresent(HomeTextCopy.syncAnkiDroidLabel())) return
        tapDeviceText("Sync")
    }

    /**
     * Answers the first-bind confirmation the sync raises on a never-bound collection.
     *
     * Kani will not import from a source the user has not acknowledged, so the first live
     * sync always stops here. The button can be below the fold — the recovery panel explains
     * itself at length first — so this scrolls toward it rather than failing on a control
     * that is present but off screen.
     */
    private fun confirmFirstCollectionBinding() {
        waitForFirstBindingRequirement(FIRST_BIND_TIMEOUT_MILLIS)
        val label = SourceBindingRecoveryUi.firstBindLabel()
        val headline = SourceBindingRecoveryUi.presentation(
            SourceBindingReason.FIRST_BIND_REQUIRED,
            evidence = null,
            safeStorageAvailable = false,
        ).headline
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val deadline = SystemClock.uptimeMillis() + UI_STEP_TIMEOUT_MILLIS
        var sawRecovery = false
        var button: UiObject2? = null
        while (SystemClock.uptimeMillis() < deadline && button == null) {
            button = findDeviceText(device, label)
            if (button != null) break
            sawRecovery = sawRecovery || findDeviceText(device, headline) != null
            if (sawRecovery) {
                val scrollable = device.findObjects(By.pkg(appPackage()).scrollable(true)).firstOrNull()
                if (scrollable != null) {
                    scrollable.scroll(Direction.DOWN, 0.8f)
                } else {
                    device.swipe(
                        device.displayWidth / 2,
                        device.displayHeight * 3 / 4,
                        device.displayWidth / 2,
                        device.displayHeight / 4,
                        10,
                    )
                }
            }
            SystemClock.sleep(500L)
        }
        assertNotNull("Missing first-bind confirmation: $label", button)
        button!!.click()
    }

    /**
     * Waits for a committed successful run that actually populated study items.
     *
     * Both halves are required: a `success` row with no items would mean the run committed
     * nothing, which is the failure mode a status-only assertion misses.
     *
     * `SQLITE_BUSY` is swallowed rather than failed on, and that is CLAUDE.md's rule for
     * this poller: the app is committing a large collection import, so a locked database
     * means "still working", not "broken".
     */
    private fun waitForLiveSyncImport(startedAt: Long): LocalStoreBase.SyncStatus {
        val budget = importBudgetMillis()
        val deadline = SystemClock.uptimeMillis() + budget
        var lastStatus: String? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                LocalStore(context()).use { store ->
                    val status = store.latestSync()
                    lastStatus = status?.status
                    if (status != null &&
                        status.status == "success" &&
                        status.finishedAt >= startedAt &&
                        store.studyItems().isNotEmpty()
                    ) {
                        return status
                    }
                }
            } catch (_: SQLiteDatabaseLockedException) {
                // Still committing the import; not a product failure.
            }
            SystemClock.sleep(1_000L)
        }
        // Names what it last saw, because "timed out" alone cannot distinguish a sync that
        // never started from one that failed from one still committing.
        throw AssertionError(
            "Timed out after ${budget / 1000}s waiting for live sync to populate study items; " +
                "last recorded sync status was ${lastStatus ?: "none"}",
        )
    }

    /**
     * How long the import may take, bounded by the harness that will kill us.
     *
     * The four-hour figure is right for a real user collection on a developer machine, and
     * wrong inside a 75-minute CI job: the first dispatch of this gate waited past the job's
     * own `timeout-minutes`, so the run was cancelled and the log showed the test's class name
     * and nothing else — no assertion, no diagnosis. A caller can pass
     * `-e kanjiLiveImportBudgetSeconds N` to cap it below the harness limit, and the
     * deterministic CI fixture (`kanjiLiveMinimumNotes=1`) is a handful of notes that has no
     * business taking more than a few minutes, so it gets a short default automatically.
     */
    private fun importBudgetMillis(): Long {
        val args = InstrumentationRegistry.getArguments()
        args.getString("kanjiLiveImportBudgetSeconds")?.toLongOrNull()?.let {
            return TimeUnit.SECONDS.toMillis(it)
        }
        return if (isDeterministicFixture()) {
            DETERMINISTIC_IMPORT_TIMEOUT_MILLIS
        } else {
            LIVE_SYNC_TIMEOUT_MILLIS
        }
    }

    /**
     * Whether this is the small synthesized CI collection rather than a real one.
     *
     * `kanjiLiveMinimumNotes=1` is how `run_ankidroid_fixture.sh` says "deterministic
     * fixture"; a real-collection run leaves it at the 7,000 default.
     */
    private fun isDeterministicFixture(): Boolean =
        "1" == InstrumentationRegistry.getArguments().getString("kanjiLiveMinimumNotes")

    /** Waits until the sync engine has recorded that it needs a first-bind acknowledgement. */
    private fun waitForFirstBindingRequirement(timeoutMillis: Long) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                LocalStore(context()).use { store ->
                    store.readableDatabase.query(
                        LocalStoreBase.TABLE_SYNC_RUNS,
                        arrayOf(LocalStoreBase.COLUMN_STATUS, "error_code"),
                        null,
                        null,
                        null,
                        null,
                        LocalStoreBase.ORDER_ID_DESC,
                        "1",
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val errorCode = if (cursor.isNull(1)) null else cursor.getString(1)
                            if (errorCode == "source_binding_first_bind_required") return
                        }
                    }
                }
            } catch (_: SQLiteDatabaseLockedException) {
                // The engine is writing its run row.
            }
            SystemClock.sleep(500L)
        }
        throw AssertionError("Timed out waiting for the first-bind requirement")
    }

    private fun waitForDeviceText(text: String, timeoutMillis: Long) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (findDeviceText(device, text) != null) return
            SystemClock.sleep(500L)
        }
        throw AssertionError("Missing device text: $text")
    }

    private fun tapDeviceText(text: String) {
        waitForDeviceText(text, UI_STEP_TIMEOUT_MILLIS)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertNotNull("Missing tappable text: $text", findDeviceText(device, text))
        findDeviceText(device, text)!!.click()
    }

    private fun tapDeviceTextIfPresent(text: String): Boolean {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val found = findDeviceText(device, text) ?: return false
        found.click()
        return true
    }

    /**
     * Finds [text] on screen, tolerating the case a Material button applied.
     *
     * Four passes: exact, upper-cased, contained, and upper-cased-contained. A theme that
     * upper-cases labels would otherwise make every tap here miss.
     */
    private fun findDeviceText(device: UiDevice, text: String): UiObject2? {
        val pkg = appPackage()
        return device.findObjects(By.pkg(pkg).text(text)).firstOrNull()
            ?: device.findObjects(By.pkg(pkg).text(text.uppercase(Locale.ROOT))).firstOrNull()
            ?: device.findObjects(By.pkg(pkg).textContains(text)).firstOrNull()
            ?: device.findObjects(By.pkg(pkg).textContains(text.uppercase(Locale.ROOT))).firstOrNull()
    }

    private fun appPackage(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun context(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun liveEnabled(): Boolean =
        "true" == InstrumentationRegistry.getArguments().getString("kanjiLiveAnkiDroid")

    private companion object {
        /**
         * How long the *import itself* may take.
         *
         * Four hours, carried over from the old gate: a real user collection is tens of
         * thousands of notes committed in one transaction, and shortening this would turn a
         * slow-but-correct release gate into a flaky one. Only the two waits that genuinely
         * span the import use it — [waitForLiveSyncImport] and the final "Sync complete".
         */
        val LIVE_SYNC_TIMEOUT_MILLIS: Long = TimeUnit.HOURS.toMillis(4)

        /**
         * How long any single UI step may take before the test gives up and says which one.
         *
         * Separate from the import bound, and the reason is a real failure: the first dispatch
         * of this gate hung for 64 minutes and was killed by the job's own
         * `timeout-minutes: 75`, so the log showed the class name and nothing else. A wait
         * that outlives its harness cannot report anything — every UI step now fails inside
         * the job with a message naming the text it was waiting for.
         *
         * Three minutes is generous for a button appearing while a sync runs in the
         * background, and short enough that all of them together stay well inside the job.
         */
        val UI_STEP_TIMEOUT_MILLIS: Long = TimeUnit.MINUTES.toMillis(3)

        /**
         * How long to wait for the engine to record that it needs a first-bind acknowledgement.
         *
         * Its own bound because it is neither a UI step nor the import: the sync has to reach
         * the provider and write a run row first, which on a cold emulator is slower than a
         * button appearing but nothing like a full import.
         */
        val FIRST_BIND_TIMEOUT_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)

        /**
         * The import bound for the small synthesized CI collection.
         *
         * A few notes, so anything past this is stuck rather than slow — and failing here
         * leaves time inside a 75-minute job to report which step and what the last recorded
         * sync status was.
         */
        val DETERMINISTIC_IMPORT_TIMEOUT_MILLIS: Long = TimeUnit.MINUTES.toMillis(20)
    }
}
