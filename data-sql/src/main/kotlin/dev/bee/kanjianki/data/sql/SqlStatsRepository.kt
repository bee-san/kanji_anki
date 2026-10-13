package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.data.StatsRepository
import dev.bee.kanjianki.data.StatsSnapshot
import java.util.TimeZone

/**
 * Driver-neutral analytics repository. Reads/writes the `stats_screen_cache`
 * row (format 11) via [SqlStatsCodec] and recomputes through [SqlStatsData].
 * Freshness matches the legacy `StatsCacheStore`: same source version, cache
 * format, time zone, and local day. Android production stays on LocalStore
 * until Goal 184.
 */
class SqlStatsRepository(
    private val database: SqlDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : StatsRepository {
    override suspend fun loadCached(nowMillis: Long) = safeSqlStoreCall {
        database.readSnapshot {
            val cached = readLatest() ?: return@readSnapshot null
            if (isFresh(cached, nowMillis)) cached.snapshot else null
        }
    }

    override suspend fun loadLatest() = safeSqlStoreCall {
        database.readSnapshot { readLatest()?.snapshot }
    }

    override suspend fun refresh(nowMillis: Long) = safeSqlStoreCall {
        database.write {
            val sourceVersion = currentSourceVersion()
            val snapshot = SqlStatsData(this).compute(nowMillis, sourceVersion)
            writeCache(snapshot)
            snapshot
        }
    }

    private fun SqlReadScope.readLatest(): CachedSnapshot? =
        queryOneOrNull(
            "SELECT source_version, generated_at, outcome_json, impact_report_json FROM stats_screen_cache WHERE id = 1",
        ) { row ->
            val outcomeJson = row.text(2)
            val snapshot = SqlStatsCodec.decode(outcomeJson, row.text(3), row.long(1), row.long(0))
            snapshot?.let { CachedSnapshot(it, SqlStatsCodec.timeZoneIdOf(outcomeJson)) }
        }

    private fun SqlReadScope.isFresh(cached: CachedSnapshot, nowMillis: Long): Boolean =
        cached.snapshot.sourceVersion == currentSourceVersion() &&
            cached.snapshot.cacheFormatVersion == SqlStatsData.STATS_CACHE_FORMAT_VERSION &&
            cached.timeZoneId == TimeZone.getDefault().id &&
            LocalDayPolicy.sameLocalDay(cached.snapshot.generatedAtMillis, nowMillis)

    private fun SqlTransactionScope.writeCache(snapshot: StatsSnapshot) {
        executeBound(
            """
            INSERT INTO stats_screen_cache(id, source_version, generated_at, cache_format_version, outcome_json, impact_report_json)
            VALUES (1, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                source_version = excluded.source_version,
                generated_at = excluded.generated_at,
                cache_format_version = excluded.cache_format_version,
                outcome_json = excluded.outcome_json,
                impact_report_json = excluded.impact_report_json
            """.trimIndent(),
        ) {
            bindLong(1, snapshot.sourceVersion)
            bindLong(2, snapshot.generatedAtMillis)
            bindLong(3, snapshot.cacheFormatVersion.toLong())
            bindText(4, SqlStatsCodec.outcomeJson(snapshot))
            bindText(5, SqlStatsCodec.impactReportJson(snapshot.impactReport))
        }
    }

    private fun SqlSession.currentSourceVersion(): Long =
        queryOneOrNull(
            "SELECT value FROM stats_cache_state WHERE key = ? LIMIT 1",
            bind = { bindText(1, STATS_SOURCE_VERSION_KEY) },
        ) { row -> row.long(0) } ?: 1L

    private data class CachedSnapshot(
        val snapshot: StatsSnapshot,
        val timeZoneId: String,
    )

    private companion object {
        const val STATS_SOURCE_VERSION_KEY = "stats_source_version"
    }
}
