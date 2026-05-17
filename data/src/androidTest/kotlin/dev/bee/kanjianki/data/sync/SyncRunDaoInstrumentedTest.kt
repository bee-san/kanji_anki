package dev.bee.kanjianki.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bee.kanjianki.data.KaniRoomDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncRunDaoInstrumentedTest {
    private lateinit var database: KaniRoomDatabase
    private lateinit var dao: SyncRunDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KaniRoomDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.syncRunDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun successfulSyncSinceUsesRoomSqlWithInclusiveFinishedAtBoundary() = runBlocking {
        dao.insert(syncRun(status = "config_error", finishedAt = 20_000L))
        dao.insert(syncRun(status = "success", finishedAt = null))

        assertFalse(dao.hasSuccessfulSyncSince(0L))

        dao.insert(syncRun(status = "success", finishedAt = 20_000L))

        assertTrue(dao.hasSuccessfulSyncSince(20_000L))
        assertFalse(dao.hasSuccessfulSyncSince(20_001L))
    }

    private companion object {
        fun syncRun(
            status: String,
            finishedAt: Long?,
        ): SyncRunEntity = SyncRunEntity(
            startedAt = 1_000L,
            finishedAt = finishedAt,
            status = status,
            activeNotesCount = 0,
            activeCardsCount = 0,
            suspendedCardsArchivedCount = 0,
            suspendedKanjiImportedCount = 0,
            deletedNotesCount = 0,
            deletedCardsCount = 0,
            errorCode = null,
            errorMessage = null,
            removalMessage = null,
        )
    }
}
