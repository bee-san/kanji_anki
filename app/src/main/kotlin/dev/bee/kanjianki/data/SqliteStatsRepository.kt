package dev.bee.kanjianki.data

internal class SqliteStatsRepository(
    private val store: LocalStore,
) : StatsRepository {
    override suspend fun loadCached(nowMillis: Long) = safeStoreCall {
        StatsCacheStore(store).readFresh(nowMillis = nowMillis)?.toRepositorySnapshot()
    }

    override suspend fun loadLatest() = safeStoreCall {
        StatsCacheStore(store).readLatest()?.toRepositorySnapshot()
    }

    override suspend fun refresh(nowMillis: Long) = safeStoreCall {
        StatsPrecomputeStore(store).refresh(generatedAtMillis = nowMillis).toRepositorySnapshot()
    }
}
