package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.sync.SyncRunDao
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.repository.SyncRunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSyncRunRepository(
    private val dao: SyncRunDao,
) : SyncRunRepository {
    override fun observeLatest(): Flow<SyncRun?> = dao.observeLatest().map { it?.toDomain() }

    override suspend fun get(id: SyncRunId): SyncRun? = dao.get(id.value)?.toDomain()

    override suspend fun latest(): SyncRun? = dao.latest()?.toDomain()

    override suspend fun insert(syncRun: SyncRun): SyncRunId =
        SyncRunId(dao.insert(syncRun.copy(id = null).toEntity()))

    override suspend fun update(syncRun: SyncRun) {
        require(syncRun.id != null) { "syncRun id is required for update" }
        dao.update(syncRun.toEntity())
    }
}
