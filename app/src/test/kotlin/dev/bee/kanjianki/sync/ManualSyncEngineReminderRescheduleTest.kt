package dev.bee.kanjianki.sync

import android.content.Context
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
class ManualSyncEngineReminderRescheduleTest {
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
    fun successfulSyncRearmsReminder() {
        val engine = ManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
        var rearms = 0
        engine.reminderRescheduler = Runnable { rearms++ }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(1, rearms)
    }

    @Test
    fun failedSyncDoesNotRearmReminder() {
        val engine = ManualSyncEngine(
            context,
            store,
            ThrowingGateway(AnkiDroidGateway.SyncFailure.retryable("try later")),
            RecordsSyncModels.Settings.kikuDefaults(),
        )
        var rearms = 0
        engine.reminderRescheduler = Runnable { rearms++ }

        val result = engine.run()

        assertFalse(result.success)
        assertEquals(0, rearms)
    }

    private class EmptyGateway : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            return RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "")
        }
    }

    private class ThrowingGateway(private val failure: Exception) : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            throw failure
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            throw AssertionError("not used in this test")
        }
    }
}
