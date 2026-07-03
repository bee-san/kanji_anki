package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncCommitWindowTest {
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

    private fun emptySnapshot() = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())

    @Test
    fun pendingSyncBeforeStudyItemsCommitDoesNotCountAsSuccessfulSyncSince() {
        val finished = 2_000L
        // Transaction #1 commits as pending (simulates a crash before study items commit).
        store.saveSuccessfulSync(
            emptySnapshot(),
            emptyList(),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(1_000L, finished),
            null,
            null,
            emptyList(),
            LocalStoreBase.STATUS_PENDING,
        )

        // Auto-sync gating must treat the lingering pending run as not-yet-successful,
        // so it retries instead of skipping for the rest of the day.
        assertFalse(store.hasSuccessfulSyncSince(finished))
    }

    @Test
    fun markSyncSucceededPromotesPendingRunToSuccess() {
        val finished = 2_000L
        val syncId = store.saveSuccessfulSync(
            emptySnapshot(),
            emptyList(),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(1_000L, finished),
            null,
            null,
            emptyList(),
            LocalStoreBase.STATUS_PENDING,
        )
        assertFalse(store.hasSuccessfulSyncSince(finished))

        // Study items committed in transaction #2; promote the run.
        store.markSyncSucceeded(syncId)

        assertTrue(store.hasSuccessfulSyncSince(finished))
    }
}
