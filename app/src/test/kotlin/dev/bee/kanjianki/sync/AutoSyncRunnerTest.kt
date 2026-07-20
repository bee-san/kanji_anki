package dev.bee.kanjianki.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.AutoSyncSchedulePolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.time.AppClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoSyncRunnerTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
        store.saveAutoSyncSettings(
            LocalStoreBase.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L),
        )
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun explicitProviderDispositionReachesAutomaticRetryDecision() {
        val transient = runWith(
            ThrowingGateway(AnkiDroidGateway.SyncFailure.retryable("provider locked")),
        )
        assertTrue(transient.ran)
        assertFalse(transient.success)
        assertTrue(transient.retryable)

        val permanent = runWith(
            ThrowingGateway(AnkiDroidGateway.SyncFailure.permanent("bad fields")),
        )
        assertTrue(permanent.ran)
        assertFalse(permanent.success)
        assertFalse(permanent.retryable)

        val unexpected = runWith(ThrowingGateway(IllegalStateException("disk state")))
        assertTrue(unexpected.ran)
        assertFalse(unexpected.success)
        assertFalse(unexpected.retryable)
    }

    @Test
    fun duplicateRetryAfterASuccessfulSameDaySyncIsAnIdempotentSkipThatNeverTouchesTheProvider() {
        // A retry worker that survives process death can fire again after the daily
        // sync already succeeded today. It must be an idempotent no-op: no second
        // provider read, no duplicate history, and no false "ran/success" report.
        val now = 1_784_000_000_000L
        val dayStart = AutoSyncSchedulePolicy.localDayStart(now)
        // Commit a real successful sync run earlier today (atomic pending -> success).
        val startedAt = dayStart + 1_000L
        val finishedAt = dayStart + 2_000L
        val syncId = store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(startedAt, finishedAt),
            null,
            null,
            emptyList(),
            LocalStoreBase.STATUS_PENDING,
        )
        store.commitPendingSyncStudyItems(
            emptyList(),
            syncId,
            finishedAt,
            RecordsSyncModels.Settings.kikuDefaults(),
            emptyList(),
        )
        assertTrue(store.hasSuccessfulSyncSince(dayStart))

        val gateway = RecordingGateway(
            AnkiDroidGateway.SyncFailure.retryable("provider must not be touched again today"),
        )
        val result = AutoSyncRunner(context, store, gateway, AppClock { now }).run()

        assertFalse("a same-day duplicate retry must not run the engine", result.ran)
        assertFalse(result.success)
        assertFalse("an idempotent same-day skip is terminal, not retryable", result.retryable)
        assertFalse("the provider must never be read on a same-day duplicate", gateway.wasRead())
        // Successful-run-only history is unchanged by the duplicate retry.
        assertTrue(store.hasSuccessfulSyncSince(dayStart))
    }

    @Test
    @Config(sdk = [30])
    fun providerUnavailableRecordsAPermanentConfigErrorWithoutRecordingSuccess() {
        // No AnkiDroid provider is installed in the Robolectric environment, so a real
        // AnkiDroidGateway reports canSync=false. This is a local-provider condition,
        // entirely independent of Internet connectivity, and must be a permanent
        // failure that is never misreported as a successful sync.
        val now = 1_784_000_000_000L
        val dayStart = AutoSyncSchedulePolicy.localDayStart(now)
        assertFalse(store.hasSuccessfulSyncSince(dayStart))

        val gateway = AnkiDroidGateway(context)
        assertFalse(
            "test precondition: no provider installed so it cannot sync",
            gateway.status().canSync,
        )

        val result = AutoSyncRunner(context, store, gateway, AppClock { now }).run()

        assertTrue(result.ran)
        assertFalse(result.success)
        assertFalse("a missing local provider is permanent, not a retryable network failure", result.retryable)
        // Never a false success: successful-run-only history stays empty.
        assertFalse(store.hasSuccessfulSyncSince(dayStart))
        assertNull(store.latestSuccessfulSyncFinishedAt())
        // The failure is persisted as a local config error, not a network failure.
        val latest = requireNotNull(store.latestSync())
        assertEquals("config_error", latest.status)
    }

    @Test
    fun concurrentForegroundSyncBecomesRetryableDeferral() {
        val blocker = BlockingGateway()
        val threadFailure = AtomicReference<Throwable?>()
        val foreground = Thread {
            try {
                ManualSyncEngine(
                    context,
                    store,
                    blocker,
                    RecordsSyncModels.Settings.kikuDefaults(),
                ).run()
            } catch (error: Throwable) {
                threadFailure.set(error)
            }
        }
        foreground.start()
        assertTrue(blocker.awaitStarted())

        val result = runWith(
            ThrowingGateway(AnkiDroidGateway.SyncFailure.permanent("must not run")),
        )

        blocker.release()
        foreground.join(5_000L)

        assertFalse(result.ran)
        assertFalse(result.success)
        assertTrue(result.retryable)
        assertFalse(foreground.isAlive)
        assertNull(threadFailure.get())
    }

    private fun runWith(gateway: CollectionGateway): AutoSyncRunner.Result {
        val now = 1_784_000_000_000L
        return AutoSyncRunner(context, store, gateway, AppClock { now }).run()
    }

    private class ThrowingGateway(private val error: Throwable) : CollectionGateway {
        override fun readCollection(
            settings: RecordsSyncModels.Settings,
        ): RecordsSyncModels.CollectionSnapshot {
            throw error
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            throw AssertionError("not reached")
        }
    }

    /** Records whether the provider was read, to prove same-day duplicate retries skip it. */
    private class RecordingGateway(private val error: Throwable) : CollectionGateway {
        private val read = AtomicBoolean(false)

        override fun readCollection(
            settings: RecordsSyncModels.Settings,
        ): RecordsSyncModels.CollectionSnapshot {
            read.set(true)
            throw error
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            throw AssertionError("not reached")
        }

        fun wasRead(): Boolean = read.get()
    }

    private class BlockingGateway : CollectionGateway {
        private val started = CountDownLatch(1)
        private val release = CountDownLatch(1)

        override fun readCollection(
            settings: RecordsSyncModels.Settings,
        ): RecordsSyncModels.CollectionSnapshot {
            started.countDown()
            assertTrue(release.await(5L, TimeUnit.SECONDS))
            throw AnkiDroidGateway.SyncFailure.retryable("foreground test complete")
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            throw AssertionError("not reached")
        }

        fun awaitStarted(): Boolean = started.await(5L, TimeUnit.SECONDS)

        fun release() {
            release.countDown()
        }
    }
}
