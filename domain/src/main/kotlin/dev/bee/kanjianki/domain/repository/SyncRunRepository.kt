package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.sync.SyncRun
import kotlinx.coroutines.flow.Flow

interface SyncRunRepository {
    fun observeLatest(): Flow<SyncRun?>

    suspend fun get(id: SyncRunId): SyncRun?

    suspend fun latest(): SyncRun?

    suspend fun insert(syncRun: SyncRun): SyncRunId

    suspend fun update(syncRun: SyncRun)
}
