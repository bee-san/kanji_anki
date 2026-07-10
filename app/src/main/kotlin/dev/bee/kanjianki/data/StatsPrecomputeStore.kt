package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.ConfusionPairMiner
import dev.bee.kanjianki.core.LadderCompletionForecastPolicy
import dev.bee.kanjianki.sync.SyncSettings

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
        fun kanjiRepairEvidence(db: SQLiteDatabase): List<StudyStatsStore.KanjiRepairEvidence> = emptyList()
        fun wrongPickCounts(db: SQLiteDatabase, nowMillis: Long): Map<String, Map<String, Int>> = emptyMap()
        fun ladderForecast(db: SQLiteDatabase, nowMillis: Long): LadderCompletionForecastPolicy.Forecast? = null
    }

    fun refresh(
        db: SQLiteDatabase = store.writableDatabase,
        generatedAtMillis: Long = System.currentTimeMillis(),
    ): StatsCacheStore.Snapshot {
        val sourceVersion = cacheStore.currentSourceVersion(db)
        val statsStore = StudyStatsStore(store, db)
        val outcomeStats = computations.outcomeStats(db)
        val impactReport = computations.impactReport(db)
        val studyImpactStats = computations.studyImpactStats(db)
        val recentMistakes = computations.recentMistakes(db, STATS_RECENT_MISTAKE_LIMIT)
        val queries = StudyStatsQueries(store, db)
        val wrongPickCounts = computations.wrongPickCounts(db, generatedAtMillis)
        val snapshot = StatsCacheStore.Snapshot(
            outcomeStats = outcomeStats,
            impactReport = impactReport,
            generatedAtMillis = generatedAtMillis,
            sourceVersion = sourceVersion,
            studyImpactStats = studyImpactStats,
            recentMistakes = recentMistakes,
            studyStreak = statsStore.studyStreak(generatedAtMillis),
            studyTaskTimeStats = statsStore.studyTaskTimeStats(generatedAtMillis),
            cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            reviewDaySummaries = queries.reviewDaySummaries(generatedAtMillis, STATS_REVIEW_DAY_SUMMARY_LIMIT),
            kanjiRepairEvidence = computations.kanjiRepairEvidence(db),
            taskTypeDaySummaries = queries.taskTypeDaySummaries(generatedAtMillis, STATS_REVIEW_DAY_SUMMARY_LIMIT),
            cumulativeKanjiPracticed = queries.cumulativeKanjiPracticed(),
            wrongPickCounts = wrongPickCounts,
            confusionMeanings = queries.confusionMeanings(wrongPickCounts),
            ladderForecast = computations.ladderForecast(db, generatedAtMillis),
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

        override fun kanjiRepairEvidence(db: SQLiteDatabase): List<StudyStatsStore.KanjiRepairEvidence> {
            return StudyStatsStore(store, db).kanjiRepairEvidence()
        }

        override fun wrongPickCounts(db: SQLiteDatabase, nowMillis: Long): Map<String, Map<String, Int>> {
            return store.choiceWrongPickCounts(nowMillis)
        }

        override fun ladderForecast(db: SQLiteDatabase, nowMillis: Long): LadderCompletionForecastPolicy.Forecast {
            return LadderCompletionForecastPolicy.forecast(
                rows = store.dashboardRows(),
                startingItems = store.studyItems(),
                settings = SyncSettings.fromStore(store),
                parameters = store.schedulerParameters(),
                learningSettings = store.learningStepSettings(),
                ladder = store.studyLadderSettings(),
                nowMillis = nowMillis,
                weights = store.schedulerFsrsWeights(),
            )
        }
    }
}
