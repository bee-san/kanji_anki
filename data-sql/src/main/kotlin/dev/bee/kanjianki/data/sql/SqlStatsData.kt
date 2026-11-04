package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.AdaptiveStudyHealthPolicy
import dev.bee.kanjianki.core.KaniOutcomePolicy
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LadderCompletionForecastPolicy
import dev.bee.kanjianki.core.LadderHealthPolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecentMistakePolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyProjectionEligibilityPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import dev.bee.kanjianki.core.SyncSettings
import dev.bee.kanjianki.data.AdaptiveHealthSnapshot
import dev.bee.kanjianki.data.CumulativeKanjiSnapshot
import dev.bee.kanjianki.data.KaniOutcomeSnapshot
import dev.bee.kanjianki.data.KanjiImprovementSnapshot
import dev.bee.kanjianki.data.KanjiRepairEvidenceSnapshot
import dev.bee.kanjianki.data.KanjiSupportGainSnapshot
import dev.bee.kanjianki.data.LadderHealthSnapshot
import dev.bee.kanjianki.data.MatureSupportGainedSnapshot
import dev.bee.kanjianki.data.RecentMistakeSnapshot
import dev.bee.kanjianki.data.ReviewDaySummarySnapshot
import dev.bee.kanjianki.data.StatsSnapshot
import dev.bee.kanjianki.data.StudyImpactSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import dev.bee.kanjianki.data.StudyTaskTimeSnapshot
import dev.bee.kanjianki.data.TaskTypeDaySummarySnapshot
import dev.bee.kanjianki.data.WeakKanjiImprovedSnapshot

/**
 * Driver-neutral analytics computation. Runs the read-only SQL the app's
 * StudyStatsQueries / KanjiImpactReportStore ran, feeds `:core` policies, and
 * maps the result straight into the data-api [StatsSnapshot]. Reuses
 * [SqlRepairEvidenceReader] (shared with sync) for repair evidence and
 * [SqlHomeData] / [SqlStudyData] for dashboard, inventory, and study reads.
 */
internal class SqlStatsData(
    private val session: SqlReadScope,
) {
    private val home = SqlHomeData(session)
    private val study = SqlStudyData(session)

    fun compute(generatedAtMillis: Long, sourceVersion: Long): StatsSnapshot {
        val settings = SqlSettingsRepository.readSnapshot(session).sync
        val wrongPickCounts = home.wrongPickCounts(generatedAtMillis)
        return StatsSnapshot(
            outcomeStats = outcomeStats(settings.ladderPromotionIntervalDays, settings.ladderDemotionFailStreak),
            impactReport = impactReport(),
            generatedAtMillis = generatedAtMillis,
            sourceVersion = sourceVersion,
            studyImpactStats = studyImpactStats(),
            recentMistakes = recentMistakes(settings, generatedAtMillis),
            studyStreak = studyStreak(generatedAtMillis),
            studyTaskTimeStats = studyTaskTimeStats(generatedAtMillis),
            cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            reviewDaySummaries = reviewDaySummaries(generatedAtMillis, STATS_REVIEW_DAY_SUMMARY_LIMIT),
            kanjiRepairEvidence = kanjiRepairEvidence(),
            taskTypeDaySummaries = taskTypeDaySummaries(generatedAtMillis, STATS_REVIEW_DAY_SUMMARY_LIMIT),
            cumulativeKanjiPracticed = cumulativeKanjiPracticed(),
            wrongPickCounts = wrongPickCounts,
            confusionMeanings = confusionMeanings(wrongPickCounts),
            ladderForecast = ladderForecast(generatedAtMillis),
        )
    }

    // --- outcome / ladder / adaptive health -------------------------------

    private fun outcomeStats(promotionDays: Int, failStreak: Int): KaniOutcomeSnapshot {
        val outcome = KaniOutcomePolicy.summarize(outcomeEvidence(), ladderMetric(promotionDays, failStreak))
        return KaniOutcomeSnapshot(
            weakKanjiImproved = WeakKanjiImprovedSnapshot(
                outcome.weakKanjiImproved().improvedCount(),
                outcome.weakKanjiImproved().averageBeforeWeakness(),
                outcome.weakKanjiImproved().averageAfterWeakness(),
                outcome.weakKanjiImproved().examples().map {
                    KanjiImprovementSnapshot(it.kanji(), it.beforeWeakness(), it.afterWeakness())
                },
            ),
            matureSupportGained = MatureSupportGainedSnapshot(
                outcome.matureSupportGained().gainedSupportCount(),
                outcome.matureSupportGained().matureSupportGained(),
                outcome.matureSupportGained().firstSupportCount(),
                outcome.matureSupportGained().examples().map {
                    KanjiSupportGainSnapshot(it.kanji(), it.beforeMatureSupport(), it.afterMatureSupport())
                },
            ),
            ladderHealth = ladderHealthSnapshot(outcome.ladderHealth()),
            adaptiveHealth = adaptiveHealthSnapshot(failStreak),
        )
    }

    private fun ladderMetric(promotionDays: Int, failStreak: Int): LadderHealthPolicy.Metric =
        LadderHealthPolicy.summarize(ladderItems(), promotionDays, failStreak)

    private fun ladderHealthSnapshot(metric: LadderHealthPolicy.Metric): LadderHealthSnapshot =
        LadderHealthSnapshot(
            rungCounts = metric.rungCounts(),
            totalActiveItems = metric.totalActiveItems(),
            realDueReviewsToMove = metric.ladderDemotionFailStreak(),
            ladderPromotionIntervalDays = metric.ladderPromotionIntervalDays(),
            ladderDemotionFailStreak = metric.ladderDemotionFailStreak(),
            promotionReadyCount = metric.promotionReadyCount(),
            demotionRiskCount = metric.demotionRiskCount(),
            demotionReadyCount = metric.demotionReadyCount(),
            stuckCount = metric.stuckCount(),
        )

    private fun adaptiveHealthSnapshot(escalationThreshold: Int): AdaptiveHealthSnapshot {
        val metric = AdaptiveStudyHealthPolicy.summarize(adaptiveItems(), escalationThreshold)
        return AdaptiveHealthSnapshot(
            coreCounts = metric.coreCounts,
            activeRepairsByTask = metric.activeRepairsByTask,
            activeRepairsByFailure = metric.activeRepairsByFailure,
            totalAdaptiveItems = metric.totalAdaptiveItems,
            contextualCompleteCount = metric.contextualCompleteCount,
            activeRepairCount = metric.activeRepairCount,
            revalidationPendingCount = metric.revalidationPendingCount,
            recentCoreMissCount = metric.recentCoreMissCount,
            escalationRiskCount = metric.escalationRiskCount,
            stuckRepairCount = metric.stuckRepairCount,
            malformedStateCount = metric.malformedStateCount,
        )
    }

    private fun outcomeEvidence(): List<KaniOutcomePolicy.OutcomeEvidence> =
        session.queryList(
            """
            SELECT rw.kanji,
                (SELECT s.weakness_score FROM sync_kanji_snapshots s WHERE s.kanji = rw.kanji AND s.finished_at < rw.first_reviewed_at AND $SUCCESSFUL_SNAPSHOT ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS before_weakness,
                (SELECT s.mature_support_count FROM sync_kanji_snapshots s WHERE s.kanji = rw.kanji AND s.finished_at < rw.first_reviewed_at AND $SUCCESSFUL_SNAPSHOT ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS before_support,
                (SELECT s.weakness_score FROM sync_kanji_snapshots s WHERE s.kanji = rw.kanji AND s.finished_at > rw.last_reviewed_at AND $SUCCESSFUL_SNAPSHOT ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS after_weakness,
                (SELECT s.mature_support_count FROM sync_kanji_snapshots s WHERE s.kanji = rw.kanji AND s.finished_at > rw.last_reviewed_at AND $SUCCESSFUL_SNAPSHOT ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS after_support
            FROM (SELECT kanji, MIN(reviewed_at) AS first_reviewed_at, MAX(reviewed_at) AS last_reviewed_at
                  FROM review_log WHERE kanji <> '' GROUP BY kanji) rw
            """.trimIndent(),
        ) { row ->
            KaniOutcomePolicy.OutcomeEvidence(
                row.text(0),
                outcomeSnapshot(row, 1, 2),
                outcomeSnapshot(row, 3, 4),
            )
        }

    private fun outcomeSnapshot(row: SqlRow, weaknessIndex: Int, supportIndex: Int): KaniOutcomePolicy.OutcomeSnapshot? {
        if (row.isNull(weaknessIndex) || row.isNull(supportIndex)) return null
        return KaniOutcomePolicy.OutcomeSnapshot(row.long(weaknessIndex).toInt(), row.long(supportIndex).toInt())
    }

    private fun ladderItems(): List<LadderHealthPolicy.ItemEvidence> {
        val withSimilar = kanjiWithSimilarNeighbors()
        return session.queryList(
            """
            SELECT kanji, state, rung, phase, real_pass_streak, real_again_streak, mature_interval_days
            FROM study_items WHERE state <> ?
            """.trimIndent(),
            bind = { bindText(1, STATE_RETIRED) },
        ) { row ->
            val values = NamedSqlRow(row)
            LadderHealthPolicy.ItemEvidence(
                values.text("state"),
                RecordsBase.LadderRung.fromWireName(values.text("rung")),
                RecordsBase.SchedulerPhase.fromWireName(values.text("phase")),
                values.int("real_pass_streak"),
                values.int("real_again_streak"),
                values.int("mature_interval_days"),
                values.text("kanji") in withSimilar,
            )
        }
    }

    private fun adaptiveItems(): List<AdaptiveStudyHealthPolicy.ItemEvidence> =
        session.queryList(
            """
            SELECT state, phase, routing_version, adaptive_route_state_json, word_reading_memory
            FROM study_items WHERE state <> ? AND routing_version >= 2
            """.trimIndent(),
            bind = { bindText(1, STATE_RETIRED) },
        ) { row ->
            val values = NamedSqlRow(row)
            val wordReadingMemory = RecordsStudyModels.TaskMemory.decode(
                values.text("word_reading_memory"),
                RecordsStudyModels.TaskMemory.initial(),
            )
            AdaptiveStudyHealthPolicy.ItemEvidence(
                state = values.text("state"),
                phase = RecordsBase.SchedulerPhase.fromWireName(values.text("phase")),
                routingVersion = values.int("routing_version"),
                adaptiveRouteStateJson = values.text("adaptive_route_state_json"),
                contextualReadingConsecutivePasses = wordReadingMemory.consecutivePasses,
            )
        }

    private fun kanjiWithSimilarNeighbors(): Set<String> =
        session.queryList(
            """
            SELECT kanji_a FROM similar_kanji_pairs
            WHERE kanji_a IN (SELECT kanji FROM kanji_inventory) AND kanji_b IN (SELECT kanji FROM kanji_inventory)
            UNION
            SELECT kanji_b FROM similar_kanji_pairs
            WHERE kanji_a IN (SELECT kanji FROM kanji_inventory) AND kanji_b IN (SELECT kanji FROM kanji_inventory)
            """.trimIndent(),
        ) { row -> row.text(0) }.toSet()

    // --- study impact / streak / task timing ------------------------------

    private fun studyImpactStats(): StudyImpactSnapshot =
        session.queryOneOrNull(
            """
            SELECT
                COUNT(*) AS total_reviews,
                COUNT(DISTINCT kanji) AS distinct_kanji,
                COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required,
                COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=1 THEN 1 ELSE 0 END), 0) AS writing_passed,
                COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed,
                COALESCE(SUM(CASE WHEN manual_override=1 THEN 1 ELSE 0 END), 0) AS manual_overrides
            FROM review_log
            """.trimIndent(),
        ) { row ->
            StudyImpactSnapshot(
                row.long(0).toInt(),
                row.long(1).toInt(),
                row.long(2).toInt(),
                row.long(3).toInt(),
                row.long(4).toInt(),
                row.long(5).toInt(),
            )
        } ?: StudyImpactSnapshot(0, 0, 0, 0, 0, 0)

    private fun studyStreak(nowMillis: Long): StudyStreakSnapshot {
        val today = LocalDayPolicy.localDayStart(nowMillis)
        val rows = session.queryList(
            """
            SELECT review_day_start, COUNT(*) AS review_count, MAX(reviewed_at) AS last_reviewed_at
            FROM review_log WHERE review_day_start > 0
            GROUP BY review_day_start ORDER BY review_day_start DESC
            """.trimIndent(),
        ) { row -> Triple(row.long(0), row.long(1).toInt(), row.long(2)) }
        val streak = StudyStreakPolicy.summarize(
            rows.map { it.first },
            today,
            rows.firstOrNull { it.first == today }?.second ?: 0,
            rows.firstOrNull()?.third ?: 0L,
        )
        return StudyStreakSnapshot(
            streak.currentDays,
            streak.bestDays,
            streak.studiedToday,
            streak.reviewsToday,
            streak.lastStudyAtMillis,
        )
    }

    private fun studyTaskTimeStats(nowMillis: Long): StudyTaskTimeSnapshot {
        val window = StudyTaskTimingPolicy.windowFor(nowMillis)
        return session.queryOneOrNull(
            """
            SELECT
                COALESCE(SUM(CASE WHEN answered_at >= ? THEN active_elapsed_ms ELSE 0 END), 0) AS today_elapsed,
                COALESCE(SUM(active_elapsed_ms), 0) AS week_elapsed,
                COUNT(*) AS week_tasks
            FROM study_task_log WHERE answered_at >= ? AND answered_at < ?
            """.trimIndent(),
            bind = {
                bindLong(1, window.todayStartMillis)
                bindLong(2, window.sevenDayStartMillis)
                bindLong(3, window.tomorrowStartMillis)
            },
        ) { row -> StudyTaskTimeSnapshot(row.long(0), row.long(1), row.long(2).toInt()) }
            ?: StudyTaskTimeSnapshot(0, 0, 0)
    }

    // --- recent mistakes / day summaries / cumulative ---------------------

    private fun recentMistakes(
        settings: dev.bee.kanjianki.core.RecordsSyncModels.Settings,
        nowMillis: Long,
    ): List<RecentMistakeSnapshot> {
        val rows = home.activeDashboardRows()
        if (rows.isEmpty()) return emptyList()
        val items = study.studyItemsForKanji(rows.map { it.kanji })
        val evidenceStatus = SqlRepairEvidenceReader(session).inputs()
            .map(KanjiRepairEvidencePolicy::summarize)
            .associate { it.kanji() to it.status() }
        val eligible = StudyProjectionEligibilityPolicy.eligibleDashboardKanji(rows, items, settings, evidenceStatus)
        if (eligible.isEmpty()) return emptyList()
        val boundedLimit = RecentMistakePolicy.boundedLimit(STATS_RECENT_MISTAKE_LIMIT)
        val ratings = RecentMistakePolicy.mistakeRatings()
        val candidates = ArrayList<Triple<Long, RecentMistakeSnapshot, Long>>()
        eligible.toList().chunked(RECENT_MISTAKE_BATCH).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            session.queryList(
                """
                SELECT id, kanji, rating, reviewed_at FROM review_log
                WHERE rating IN (?, ?) AND kanji IN ($placeholders)
                ORDER BY reviewed_at DESC, id DESC LIMIT $boundedLimit
                """.trimIndent(),
                bind = {
                    bindText(1, ratings[0])
                    bindText(2, ratings[1])
                    chunk.forEachIndexed { index, value -> bindText(index + 3, value) }
                },
            ) { row ->
                Triple(row.long(0), RecentMistakeSnapshot(row.text(1), row.text(2), row.long(3)), row.long(3))
            }.let(candidates::addAll)
        }
        return candidates
            .sortedWith(compareByDescending<Triple<Long, RecentMistakeSnapshot, Long>> { it.third }.thenByDescending { it.first })
            .take(boundedLimit)
            .map { it.second }
    }

    private fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummarySnapshot> {
        if (days <= 0) return emptyList()
        val startDay = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), -(days - 1))
        val endExclusive = LocalDayPolicy.nextLocalDayStart(nowMillis)
        val byDay = session.queryList(
            """
            SELECT review_day_start, COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN rating='again' THEN 1 ELSE 0 END), 0) AS again_count,
                COALESCE(SUM(CASE WHEN rating='hard' THEN 1 ELSE 0 END), 0) AS hard_count,
                COALESCE(SUM(CASE WHEN rating='easy' THEN 1 ELSE 0 END), 0) AS easy_count,
                COALESCE(SUM(CASE WHEN rating NOT IN ('again','hard','easy') THEN 1 ELSE 0 END), 0) AS good_count,
                COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count,
                COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count
            FROM review_log WHERE review_day_start >= ? AND review_day_start < ?
            GROUP BY review_day_start ORDER BY review_day_start ASC
            """.trimIndent(),
            bind = {
                bindLong(1, startDay)
                bindLong(2, endExclusive)
            },
        ) { row ->
            val values = NamedSqlRow(row)
            values.long("review_day_start") to ReviewDaySummarySnapshot(
                dayStartMillis = values.long("review_day_start"),
                total = values.int("total"),
                again = values.int("again_count"),
                hard = values.int("hard_count"),
                good = values.int("good_count"),
                easy = values.int("easy_count"),
                writingRequired = values.int("writing_required_count"),
                writingFailed = values.int("writing_failed_count"),
            )
        }.toMap()
        return (0 until days).map { index ->
            val dayStart = LocalDayPolicy.moveLocalDays(startDay, index)
            byDay[dayStart] ?: ReviewDaySummarySnapshot(dayStart, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    private fun taskTypeDaySummaries(nowMillis: Long, days: Int): List<TaskTypeDaySummarySnapshot> {
        if (days <= 0) return emptyList()
        val startDay = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), -(days - 1))
        val endExclusive = LocalDayPolicy.nextLocalDayStart(nowMillis)
        return session.queryList(
            """
            SELECT review_day_start, task_type, COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN rating <> 'again' THEN 1 ELSE 0 END), 0) AS correct
            FROM review_log WHERE review_day_start >= ? AND review_day_start < ?
            GROUP BY review_day_start, task_type ORDER BY review_day_start ASC, task_type ASC
            """.trimIndent(),
            bind = {
                bindLong(1, startDay)
                bindLong(2, endExclusive)
            },
        ) { row ->
            TaskTypeDaySummarySnapshot(row.long(0), row.text(1), row.long(3).toInt(), row.long(2).toInt())
        }
    }

    private fun cumulativeKanjiPracticed(): List<CumulativeKanjiSnapshot> {
        var cumulative = 0
        return session.queryList(
            """
            SELECT first_day, COUNT(*) FROM (
                SELECT kanji, MIN(review_day_start) AS first_day FROM review_log WHERE kanji <> '' GROUP BY kanji
            ) GROUP BY first_day ORDER BY first_day ASC
            """.trimIndent(),
        ) { row -> row.long(0) to row.long(1).toInt() }
            .map { (day, count) ->
                cumulative += count
                CumulativeKanjiSnapshot(day, cumulative)
            }
    }

    private fun confusionMeanings(counts: Map<String, Map<String, Int>>): Map<String, String> {
        val glyphs = linkedSetOf<String>()
        counts.forEach { (target, selected) ->
            glyphs += target
            glyphs += selected.keys
        }
        if (glyphs.isEmpty()) return emptyMap()
        val placeholders = glyphs.joinToString(",") { "?" }
        return session.queryList(
            "SELECT kanji, primary_meaning FROM kanji_inventory WHERE kanji IN ($placeholders) ORDER BY kanji",
            bind = { glyphs.forEachIndexed { index, value -> bindText(index + 1, value) } },
        ) { row -> row.text(0) to row.text(1) }.toMap()
    }

    // --- repair evidence / impact / forecast ------------------------------

    private fun kanjiRepairEvidence(): List<KanjiRepairEvidenceSnapshot> =
        SqlRepairEvidenceReader(session).inputs()
            .map(KanjiRepairEvidencePolicy::summarize)
            .map { evidence ->
                KanjiRepairEvidenceSnapshot(
                    kanji = evidence.kanji(),
                    status = evidence.status(),
                    reason = evidence.reason(),
                    explanation = evidence.explanation(),
                    beforeWeakness = evidence.beforeWeakness(),
                    afterWeakness = evidence.afterWeakness(),
                    beforeMatureSupport = evidence.beforeMatureSupport(),
                    afterMatureSupport = evidence.afterMatureSupport(),
                    kaniReviews = evidence.kaniReviews(),
                    writingFailures = evidence.writingFailures(),
                    lastMistakeAtMillis = evidence.lastMistakeAtMillis(),
                    lastSyncAtMillis = evidence.lastSyncAtMillis(),
                    confidence = evidence.confidence(),
                    confidenceReason = evidence.confidenceReason(),
                )
            }

    private fun impactReport(): KanjiImpactAnalyzer.Report =
        SqlKanjiImpactReport(session).report()

    private fun ladderForecast(nowMillis: Long): LadderCompletionForecastPolicy.Forecast {
        val settingsSnapshot = SqlSettingsRepository.readSnapshot(session)
        return LadderCompletionForecastPolicy.forecast(
            rows = home.activeDashboardRows(),
            startingItems = study.studyItemsForKanji(allStudyItemKanji()),
            settings = settingsSnapshot.sync,
            parameters = settingsSnapshot.schedulerParameters,
            learningSettings = settingsSnapshot.learningSteps,
            ladder = settingsSnapshot.studyLadder,
            nowMillis = nowMillis,
            weights = settingsSnapshot.schedulerFsrsWeights?.toDoubleArray(),
        )
    }

    private fun allStudyItemKanji(): List<String> =
        session.queryList("SELECT DISTINCT kanji FROM study_items") { row -> row.text(0) }

    internal companion object {
        const val STATS_CACHE_FORMAT_VERSION = 11
        const val STATS_REVIEW_DAY_SUMMARY_LIMIT = 366
        const val STATS_RECENT_MISTAKE_LIMIT = 12
        const val RECENT_MISTAKE_BATCH = 900
        const val STATE_RETIRED = "retired"
        const val SUCCESSFUL_SNAPSHOT = "s.sync_id IN (SELECT id FROM sync_runs WHERE status = 'success')"
    }
}
