package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.sync.SyncRunDao
import dev.bee.kanjianki.data.sync.SyncRunEntity
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSyncRunRepositoryTest {
    @Test
    fun hasSuccessfulSyncSinceMatchesLegacyAutoSyncReadBoundary() = runBlocking {
        val dao = FakeSyncRunDao(
            syncRun(status = "config_error", finishedAt = 5_000L),
            syncRun(status = "success", finishedAt = 1_000L),
            syncRun(status = "success", finishedAt = null),
        )
        val repository = RoomSyncRunRepository(dao)

        assertTrue(repository.hasSuccessfulSyncSince(500L))
        assertFalse(repository.hasSuccessfulSyncSince(1_500L))
        assertFalse(RoomSyncRunRepository(FakeSyncRunDao(syncRun(status = "success", finishedAt = null)))
            .hasSuccessfulSyncSince(0L))
    }

    private class FakeSyncRunDao(
        private vararg val initial: SyncRunEntity,
    ) : SyncRunDao {
        private val rows = initial.toMutableList()

        override fun observeLatest(): Flow<SyncRunEntity?> = emptyFlow()

        override suspend fun get(id: Long): SyncRunEntity? = rows.firstOrNull { it.id == id }

        override suspend fun latest(): SyncRunEntity? = rows.lastOrNull()

        override suspend fun hasSuccessfulSyncSince(finishedAtMillis: Long): Boolean =
            rows.any { it.status == "success" && it.finishedAt != null && it.finishedAt >= finishedAtMillis }

        override suspend fun insert(syncRun: SyncRunEntity): Long {
            val id = (rows.size + 1).toLong()
            rows += syncRun.copy(id = id)
            return id
        }

        override suspend fun update(syncRun: SyncRunEntity) {
            rows.replaceAll { if (it.id == syncRun.id) syncRun else it }
        }
    }

    private companion object {
        fun syncRun(
            status: String,
            finishedAt: Long?,
        ): SyncRunEntity =
            SyncRun(
                id = SyncRunId((finishedAt ?: 0L).coerceAtLeast(1L)),
                startedAt = 0L,
                finishedAt = finishedAt,
                status = SyncRunStatus.fromWireName(status),
                activeNotesCount = 0,
                activeCardsCount = 0,
                suspendedCardsArchivedCount = 0,
                suspendedKanjiImportedCount = 0,
                deletedNotesCount = 0,
                deletedCardsCount = 0,
                errorCode = null,
                errorMessage = null,
                removalMessage = null,
            ).toEntity()
    }
}
