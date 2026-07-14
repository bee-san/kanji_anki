package dev.bee.kanjianki.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.time.AppClock
import org.junit.After
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
