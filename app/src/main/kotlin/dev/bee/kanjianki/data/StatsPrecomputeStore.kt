package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.KanjiImpactAnalyzer

internal class StatsPrecomputeStore(
    private val store: LocalStore,
    private val computations: Computations = DirectComputations(store),
    private val cacheStore: StatsCacheStore = StatsCacheStore(store),
) {
    interface Computations {
        fun outcomeStats(db: SQLiteDatabase): StudyStatsStore.KaniOutcomeStats
        fun impactReport(db: SQLiteDatabase): KanjiImpactAnalyzer.Report
        fun studyImpactStats(db: SQLiteDatabase): StudyStatsStore.StudyImpactStats = StudyStatsStore.StudyImpactStats(0, 0, 0, 0, 0, 0)
        fun recentMistakes(db: SQLiteDatabase, limit: Int): List<StudyStatsStore.RecentMistake> = emptyList()
    }

    fun refresh(
        db: SQLiteDatabase = store.writableDatabase,
        generatedAtMillis: Long = System.currentTimeMillis(),
    ): StatsCacheStore.Snapshot {
        val sourceVersion = cacheStore.currentSourceVersion(db)
        val outcomeStats = computations.outcomeStats(db)
        val impactReport = computations.impactReport(db)
        val studyImpactStats = computations.studyImpactStats(db)
        val recentMistakes = computations.recentMistakes(db, STATS_RECENT_MISTAKE_LIMIT)
        val snapshot = StatsCacheStore.Snapshot(
            outcomeStats,
            impactReport,
            generatedAtMillis,
            sourceVersion,
            studyImpactStats,
            recentMistakes,
            STATS_CACHE_FORMAT_VERSION,
        )
        cacheStore.write(db, snapshot)
        return snapshot
    }

    private class DirectComputations(private val store: LocalStore) : Computations {
        override fun outcomeStats(db: SQLiteDatabase): StudyStatsStore.KaniOutcomeStats {
            return StudyStatsStore(store, db).kaniOutcomeStats()
        }

        override fun impactReport(db: SQLiteDatabase): KanjiImpactAnalyzer.Report {
            return KanjiImpactReportStore(store).report(db)
        }

        override fun studyImpactStats(db: SQLiteDatabase): StudyStatsStore.StudyImpactStats {
            return StudyStatsStore(store, db).studyImpactStats()
        }

        override fun recentMistakes(db: SQLiteDatabase, limit: Int): List<StudyStatsStore.RecentMistake> {
            return StudyStatsStore(store, db).recentMistakes(limit)
        }
    }
}
