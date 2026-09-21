package dev.bee.kanjianki.application

import dev.bee.kanjianki.data.StatsRepository
import dev.bee.kanjianki.data.StatsSnapshot

/** Portable cache policy for Stats and Home analytics projections. */
class StatsUseCases(
    private val repository: StatsRepository,
) {
    suspend fun loadForDisplay(nowMillis: Long): StatsSnapshot {
        val cached = repository.loadCached(nowMillis).valueOrThrow("load cached stats")
        return cached ?: repository.refresh(nowMillis).valueOrThrow("refresh stats")
    }

    suspend fun isFresh(nowMillis: Long): Boolean =
        repository.loadCached(nowMillis).valueOrThrow("check stats freshness") != null

    suspend fun refresh(nowMillis: Long): StatsSnapshot =
        repository.refresh(nowMillis).valueOrThrow("refresh stats")
}
