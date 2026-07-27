package dev.bee.kanjianki.sync

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider
import dev.bee.kanjianki.core.SyncSettings
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.time.AppClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val DATABASE_NAME = "kanji_anki_simple.db"

@RunWith(AndroidJUnit4::class)
class AutoSyncRunnerInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        store = LocalStore(context)
        resetProvider()
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
        if (::context.isInitialized) {
            context.deleteDatabase(DATABASE_NAME)
            resetProvider()
        }
    }

    @Test
    fun disabledAutoSyncSkipsWithoutReadingProvider() {
        val now = localDayStart(fixedNow()) + 60_000L
        val result = createAutoSyncRunner(
            context,
            store,
            AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
            AppClock { now },
        ).run()

        assertFalse(result.ran)
        assertFalse(result.retryable)
        assertEquals("Daily sync is off.", result.message)
        assertEquals(0, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        val auto = store.autoSyncSettings()
        assertEquals(0L, auto.lastAttemptAt)
        assertEquals(0L, auto.lastSuccessAt)
        assertFalse(store.hasSuccessfulSyncSince(0L))
    }

    @Test
    fun dueAutoSyncRunsManualEngineAndRecordsAttempt() {
        val now = localDayStart(fixedNow()) + 60_000L
        store.saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L))

        val result = createAutoSyncRunner(
            context,
            store,
            AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
            AppClock { now },
        ).run()

        assertTrue(result.ran)
        assertTrue(result.success)
        assertFalse(result.retryable)
        val sync = store.latestSync()!!
        assertEquals("success", sync.status)
        assertEquals(now, sync.finishedAt)
        val auto = store.autoSyncSettings()
        assertEquals(now, auto.lastAttemptAt)
        assertEquals(now, auto.lastSuccessAt)
        assertTrue(store.hasSuccessfulSyncSince(localDayStart(now)))
        assertEquals(1, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
    }

    @Test
    fun autoSyncSkipsWhenSuccessfulSyncAlreadyHappenedToday() {
        val now = localDayStart(fixedNow()) + 2L * 60L * 60L * 1000L
        store.saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L))
        assertTrue(
            createManualSyncEngine(
                context,
                store,
                AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
                SyncSettings.fromStore(store),
                SyncProgress.NONE,
                AppClock { now },
            ).run().success,
        )
        resetProvider()

        val result = createAutoSyncRunner(
            context,
            store,
            AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY),
            AppClock { now },
        ).run()

        assertFalse(result.ran)
        assertFalse(result.retryable)
        assertEquals("AnkiDroid already synced today.", result.message)
        assertEquals(0, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        val auto = store.autoSyncSettings()
        assertEquals(0L, auto.lastAttemptAt)
        assertEquals(0L, auto.lastSuccessAt)
        assertEquals("success", store.latestSync()!!.status)
    }

    @Test
    fun autoSyncProviderReadinessFailureRecordsSyncFailure() {
        val now = localDayStart(fixedNow()) + 60_000L
        store.saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L))

        val result = createAutoSyncRunner(
            context,
            store,
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.missing_auto_sync_provider"),
            AppClock { now },
        ).run()

        assertTrue(result.ran)
        assertFalse(result.success)
        assertFalse(result.retryable)
        val auto = store.autoSyncSettings()
        assertEquals(now, auto.lastAttemptAt)
        assertEquals(0L, auto.lastSuccessAt)
        val sync = store.latestSync()!!
        assertEquals("config_error", sync.status)
        assertEquals(now, sync.finishedAt)
        assertTrue(sync.errorMessage.contains("AnkiDroid"))
    }

    @Test
    fun autoSyncManualEngineFailureRecordsFailedAttempt() {
        val now = localDayStart(fixedNow()) + 60_000L
        store.saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L))

        val result = createAutoSyncRunner(
            context,
            store,
            RetryableGateway(),
            AppClock { now },
        ).run()

        assertTrue(result.ran)
        assertFalse(result.success)
        assertTrue(result.retryable)
        assertEquals("AnkiDroid returned no configured note cursor.", result.message)
        val auto = store.autoSyncSettings()
        assertEquals(now, auto.lastAttemptAt)
        assertEquals(0L, auto.lastSuccessAt)
        val sync = store.latestSync()!!
        assertEquals("retryable_error", sync.status)
        assertEquals(now, sync.finishedAt)
        assertEquals("AnkiDroid returned no configured note cursor.", sync.errorMessage)
    }

    @Test
    fun autoSyncManualEngineSkippedDoesNotRecordAttempt() {
        val now = localDayStart(fixedNow()) + 60_000L
        store.saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L))
        val blockingGateway = BlockingGateway(
            snapshot(),
            AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done"),
        )
        val firstResult = AtomicReference<ManualSyncEngine.SyncResult?>(null)
        val threadFailure = AtomicReference<Throwable?>(null)
        val firstSync = Thread {
            try {
                firstResult.set(
                    createManualSyncEngine(
                        context,
                        store,
                        blockingGateway,
                        SyncSettings.fromStore(store),
                        SyncProgress.NONE,
                        AppClock { now },
                    ).run(),
                )
            } catch (error: Throwable) {
                threadFailure.set(error)
            }
        }

        firstSync.start()
        assertTrue(blockingGateway.awaitStarted())

        val result = createAutoSyncRunner(
            context,
            store,
            RetryableGateway(),
            AppClock { now },
        ).run()

        assertFalse(result.ran)
        assertFalse(result.success)
        assertTrue(result.retryable)
        assertEquals("Sync already running.", result.message)
        val auto = store.autoSyncSettings()
        assertEquals(0L, auto.lastAttemptAt)
        assertEquals(0L, auto.lastSuccessAt)

        blockingGateway.release()
        firstSync.join(10_000L)
        assertFalse(firstSync.isAlive)
        assertNull(threadFailure.get())
        assertTrue(firstResult.get()!!.success)
    }

    @Test
    fun schedulerMovesPastTimesToTomorrow() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.JANUARY, 12, 18, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val now = calendar.timeInMillis

        val futureToday = LocalStoreBase.AutoSyncSettings(true, true, 19, 0, 0L, 0L, 0L)
        assertEquals(now + 60L * 60L * 1000L, AutoSyncScheduler.nextTriggerMillis(futureToday, now))
        assertEquals(now + 25L * 60L * 60L * 1000L, AutoSyncScheduler.nextTriggerMillis(futureToday, now, true))

        val pastToday = LocalStoreBase.AutoSyncSettings(true, true, 17, 0, 0L, 0L, 0L)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 17)
        assertEquals(calendar.timeInMillis, AutoSyncScheduler.nextTriggerMillis(pastToday, now))
    }

    private fun localDayStart(millis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun fixedNow(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.MAY, 15, 12, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun resetProvider() {
        context.contentResolver.call(providerUri(), "reset", null, null)
    }

    private fun providerInt(method: String): Int {
        val result = context.contentResolver.call(providerUri(), method, null, null)
        return result?.getInt("value", -1) ?: -1
    }

    private fun providerUri(): Uri {
        return Uri.parse("content://${FakeAnkiDroidProvider.AUTHORITY}")
    }

    private fun snapshot(): RecordsSyncModels.CollectionSnapshot {
        val fields = linkedMapOf(
            "Expression" to "確認",
            "ExpressionReading" to "かくにん",
            "MainDefinition" to "confirmation",
            "Sentence" to "確認した。",
            "Frequency" to "1000",
            "FreqSort" to "1000",
        )
        val note = RecordsSyncModels.Note(1L, "Kiku", fields, emptyList())
        val card = RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 30, 12, 0, false)
        return RecordsSyncModels.CollectionSnapshot(listOf(note), listOf(card))
    }

    private class RetryableGateway : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            throw AnkiDroidGateway.SyncFailure.retryable("AnkiDroid returned no configured note cursor.")
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "")
        }
    }

    private class BlockingGateway(
        private val snapshot: RecordsSyncModels.CollectionSnapshot,
        private val removal: AnkiDroidGateway.RemovalSummary,
    ) : CollectionGateway {
        private val started = CountDownLatch(1)
        private val release = CountDownLatch(1)

        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            started.countDown()
            try {
                if (!release.await(10L, TimeUnit.SECONDS)) {
                    throw AnkiDroidGateway.SyncFailure.retryable("Timed out waiting for test release.")
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw AnkiDroidGateway.SyncFailure.retryable("Interrupted while waiting for test release.", error)
            }
            return snapshot
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return removal
        }

        fun awaitStarted(): Boolean {
            return started.await(10L, TimeUnit.SECONDS)
        }

        fun release() {
            release.countDown()
        }
    }
}
