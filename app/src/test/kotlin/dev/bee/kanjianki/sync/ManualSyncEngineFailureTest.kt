package dev.bee.kanjianki.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManualSyncEngineFailureTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
        db = store.writableDatabase
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    private fun engine(gateway: CollectionGateway): ManualSyncEngine {
        return ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults())
    }

    private fun latestSyncRun(): Pair<String, String?> {
        db.query("sync_runs", arrayOf("status", "error_code"), null, null, null, null, "id DESC", "1").use {
            assertTrue(it.moveToFirst())
            return it.getString(0) to (if (it.isNull(1)) null else it.getString(1))
        }
    }

    private fun syncRunCount(): Int {
        db.rawQuery("SELECT COUNT(*) FROM sync_runs", null).use {
            it.moveToFirst()
            return it.getInt(0)
        }
    }

    @Test
    fun permanentSyncFailureIsClassifiedAsConfigError() {
        val result = engine(ThrowingGateway(AnkiDroidGateway.SyncFailure.permanent("bad config"))).run()

        assertFalse(result.success)
        assertFalse(result.retryable)
        assertEquals("config_error" to "permanent", latestSyncRun())
    }

    @Test
    fun retryableSyncFailureIsClassifiedAsRetryableError() {
        val result = engine(ThrowingGateway(AnkiDroidGateway.SyncFailure.retryable("try later"))).run()

        assertFalse(result.success)
        assertTrue(result.retryable)
        assertEquals("retryable_error" to "retryable", latestSyncRun())
    }

    @Test
    fun unexpectedExceptionIsClassifiedAsUnexpectedRetryableError() {
        val result = engine(ThrowingGateway(IllegalStateException("boom"))).run()

        assertFalse(result.success)
        assertFalse(result.retryable)
        assertEquals("retryable_error" to "unexpected", latestSyncRun())
    }

    @Test
    fun errorsPropagateAndAreNotPersistedAsSyncFailures() {
        try {
            engine(ThrowingGateway(OutOfMemoryError("heap"))).run()
            throw AssertionError("Error must propagate, not be swallowed as a sync failure")
        } catch (expected: OutOfMemoryError) {
            assertEquals("heap", expected.message)
        }
        assertEquals(0, syncRunCount())
    }

    private class ThrowingGateway(private val error: Throwable) : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            throw error
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            throw AssertionError("not reached")
        }
    }
}
