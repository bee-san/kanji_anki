package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreLatestSuccessfulSyncTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun returnsNullWhenNoSuccessfulSyncExists() {
        insertSync(status = "failed", finishedAt = 200L)

        assertNull(store.latestSuccessfulSyncFinishedAt())
    }

    @Test
    fun laterFailureDoesNotReplaceLastSuccessfulTimestamp() {
        insertSync(status = LocalStoreBase.STATUS_SUCCESS, finishedAt = 100L)
        insertSync(status = "failed", finishedAt = 200L)

        assertEquals(100L, store.latestSuccessfulSyncFinishedAt())
        assertEquals("failed", store.latestSync()?.status)
    }

    private fun insertSync(status: String, finishedAt: Long) {
        store.writableDatabase.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_SYNC_RUNS} (" +
                "started_at, finished_at, status, active_notes_count, active_cards_count, " +
                "suspended_cards_archived_count, suspended_kanji_imported_count, " +
                "deleted_notes_count, deleted_cards_count, error_code, error_message, removal_message" +
                ") VALUES (?, ?, ?, 0, 0, 0, 0, 0, 0, NULL, NULL, '')",
            arrayOf<Any>(finishedAt - 1L, finishedAt, status),
        )
    }
}
