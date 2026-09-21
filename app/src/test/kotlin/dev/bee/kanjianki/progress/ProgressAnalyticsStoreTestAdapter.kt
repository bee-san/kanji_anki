package dev.bee.kanjianki.progress

import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.toRepositorySnapshot

internal fun progressAnalyticsSnapshot(
    store: LocalStore,
    nowMillis: Long = System.currentTimeMillis(),
    scheduleRefresh: (() -> Unit)? = null,
): ProgressAnalyticsState = progressAnalyticsSnapshot(
    source = object : ProgressAnalyticsStatsSource {
        override fun cachedStatsSnapshotOrNull(nowMillis: Long) =
            StatsCacheStore(store).readFresh(nowMillis = nowMillis)?.toRepositorySnapshot()

        override fun recomputeStatsSnapshotSynchronously(nowMillis: Long) =
            store.recomputeStatsSnapshotSynchronously(nowMillis).toRepositorySnapshot()

        override fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> =
            emptyList()
    },
    nowMillis = nowMillis,
    scheduleRefresh = scheduleRefresh,
    ladderSettings = store.studyLadderSettings(),
)
