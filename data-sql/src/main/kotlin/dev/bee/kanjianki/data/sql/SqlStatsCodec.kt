package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.KaniJson
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LadderCompletionForecastPolicy
import dev.bee.kanjianki.core.RecordsBase
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
 * Dependency-free serialization of the analytics [StatsSnapshot] for the
 * `stats_screen_cache` row, backed by [KaniJson]. `outcome_json` holds every
 * derived metric; `impact_report_json` holds the impact report; the version,
 * generated-at, and cache-format-version live in their own columns.
 *
 * This is self-contained within `:data-sql`: the round trip is exact, but the
 * byte layout is not required to match the legacy Android `org.json` cache,
 * since the conformance contract is the repository return value, not the
 * on-disk JSON.
 */
internal object SqlStatsCodec {
    fun outcomeJson(snapshot: StatsSnapshot): String =
        KaniJson.encode(
            linkedMapOf(
                "cacheFormatVersion" to snapshot.cacheFormatVersion,
                "timeZoneId" to snapshot.timeZoneId(),
                "outcomeStats" to outcomeStats(snapshot.outcomeStats),
                "studyImpactStats" to studyImpact(snapshot.studyImpactStats),
                "recentMistakes" to snapshot.recentMistakes.map(::recentMistake),
                "studyStreak" to studyStreak(snapshot.studyStreak),
                "studyTaskTimeStats" to studyTaskTime(snapshot.studyTaskTimeStats),
                "reviewDaySummaries" to snapshot.reviewDaySummaries.map(::reviewDay),
                "kanjiRepairEvidence" to snapshot.kanjiRepairEvidence.map(::repairEvidence),
                "taskTypeDaySummaries" to snapshot.taskTypeDaySummaries.map(::taskTypeDay),
                "cumulativeKanjiPracticed" to snapshot.cumulativeKanjiPracticed.map(::cumulative),
                "wrongPickCounts" to wrongPickCounts(snapshot.wrongPickCounts),
                "confusionMeanings" to snapshot.confusionMeanings,
                "ladderForecast" to snapshot.ladderForecast?.let(::forecast),
            ),
        )

    fun impactReportJson(report: KanjiImpactAnalyzer.Report): String =
        KaniJson.encode(
            linkedMapOf(
                "helpedCount" to report.helpedCount,
                "notHelpingCount" to report.notHelpingCount,
                "needsMoreCardsCount" to report.needsMoreCardsCount,
                "rows" to report.rows.map(::impactRow),
            ),
        )

    fun decode(
        outcomeJson: String,
        impactJson: String,
        generatedAtMillis: Long,
        sourceVersion: Long,
    ): StatsSnapshot? {
        val root = KaniJson.decode(outcomeJson) ?: return null
        val impact = KaniJson.decode(impactJson) ?: return null
        return StatsSnapshot(
            outcomeStats = outcomeStatsFrom(root.obj("outcomeStats")),
            impactReport = impactReportFrom(impact),
            generatedAtMillis = generatedAtMillis,
            sourceVersion = sourceVersion,
            studyImpactStats = studyImpactFrom(root.obj("studyImpactStats")),
            recentMistakes = root.arr("recentMistakes").map { recentMistakeFrom(it.asObj()) },
            studyStreak = studyStreakFrom(root.obj("studyStreak")),
            studyTaskTimeStats = studyTaskTimeFrom(root.obj("studyTaskTimeStats")),
            cacheFormatVersion = root.int("cacheFormatVersion"),
            reviewDaySummaries = root.arr("reviewDaySummaries").map { reviewDayFrom(it.asObj()) },
            kanjiRepairEvidence = root.arr("kanjiRepairEvidence").map { repairEvidenceFrom(it.asObj()) },
            taskTypeDaySummaries = root.arr("taskTypeDaySummaries").map { taskTypeDayFrom(it.asObj()) },
            cumulativeKanjiPracticed = root.arr("cumulativeKanjiPracticed").map { cumulativeFrom(it.asObj()) },
            wrongPickCounts = wrongPickCountsFrom(root.obj("wrongPickCounts")),
            confusionMeanings = root.obj("confusionMeanings").mapValues { it.value?.toString().orEmpty() },
            ladderForecast = root.obj("ladderForecast").takeIf { it.isNotEmpty() }?.let(::forecastFrom),
        )
    }

    fun timeZoneIdOf(outcomeJson: String): String =
        KaniJson.decode(outcomeJson)?.string("timeZoneId").orEmpty()

    // --- encoders ---------------------------------------------------------

    private fun outcomeStats(stats: KaniOutcomeSnapshot): Map<String, Any?> =
        linkedMapOf(
            "weakKanjiImproved" to linkedMapOf(
                "improvedCount" to stats.weakKanjiImproved.improvedCount,
                "averageBeforeWeakness" to stats.weakKanjiImproved.averageBeforeWeakness,
                "averageAfterWeakness" to stats.weakKanjiImproved.averageAfterWeakness,
                "examples" to stats.weakKanjiImproved.examples.map {
                    linkedMapOf<String, Any?>(
                        "kanji" to it.kanji,
                        "beforeWeakness" to it.beforeWeakness,
                        "afterWeakness" to it.afterWeakness,
                    )
                },
            ),
            "matureSupportGained" to linkedMapOf(
                "gainedSupportCount" to stats.matureSupportGained.gainedSupportCount,
                "matureSupportGained" to stats.matureSupportGained.matureSupportGained,
                "firstSupportCount" to stats.matureSupportGained.firstSupportCount,
                "examples" to stats.matureSupportGained.examples.map {
                    linkedMapOf<String, Any?>(
                        "kanji" to it.kanji,
                        "beforeMatureSupport" to it.beforeMatureSupport,
                        "afterMatureSupport" to it.afterMatureSupport,
                    )
                },
            ),
            "ladderHealth" to linkedMapOf(
                "rungCounts" to stats.ladderHealth.rungCounts.mapKeys { it.key.wireName() },
                "totalActiveItems" to stats.ladderHealth.totalActiveItems,
                "realDueReviewsToMove" to stats.ladderHealth.realDueReviewsToMove,
                "ladderPromotionIntervalDays" to stats.ladderHealth.ladderPromotionIntervalDays,
                "ladderDemotionFailStreak" to stats.ladderHealth.ladderDemotionFailStreak,
                "promotionReadyCount" to stats.ladderHealth.promotionReadyCount,
                "demotionRiskCount" to stats.ladderHealth.demotionRiskCount,
                "demotionReadyCount" to stats.ladderHealth.demotionReadyCount,
                "stuckCount" to stats.ladderHealth.stuckCount,
            ),
            "adaptiveHealth" to linkedMapOf(
                "coreCounts" to stats.adaptiveHealth.coreCounts.mapKeys { it.key.wireName() },
                "activeRepairsByTask" to stats.adaptiveHealth.activeRepairsByTask,
                "activeRepairsByFailure" to stats.adaptiveHealth.activeRepairsByFailure.mapKeys { it.key.wireName() },
                "totalAdaptiveItems" to stats.adaptiveHealth.totalAdaptiveItems,
                "contextualCompleteCount" to stats.adaptiveHealth.contextualCompleteCount,
                "activeRepairCount" to stats.adaptiveHealth.activeRepairCount,
                "revalidationPendingCount" to stats.adaptiveHealth.revalidationPendingCount,
                "recentCoreMissCount" to stats.adaptiveHealth.recentCoreMissCount,
                "escalationRiskCount" to stats.adaptiveHealth.escalationRiskCount,
                "stuckRepairCount" to stats.adaptiveHealth.stuckRepairCount,
                "malformedStateCount" to stats.adaptiveHealth.malformedStateCount,
            ),
        )

    private fun studyImpact(s: StudyImpactSnapshot) = linkedMapOf<String, Any?>(
        "totalReviews" to s.totalReviews,
        "distinctReviewedKanji" to s.distinctReviewedKanji,
        "writingRequired" to s.writingRequired,
        "writingPassed" to s.writingPassed,
        "writingFailed" to s.writingFailed,
        "manualOverrides" to s.manualOverrides,
    )

    private fun recentMistake(m: RecentMistakeSnapshot) = linkedMapOf<String, Any?>(
        "kanji" to m.kanji, "rating" to m.rating, "reviewedAtMillis" to m.reviewedAtMillis,
    )

    private fun studyStreak(s: StudyStreakSnapshot) = linkedMapOf<String, Any?>(
        "currentDays" to s.currentDays,
        "bestDays" to s.bestDays,
        "studiedToday" to s.studiedToday,
        "reviewsToday" to s.reviewsToday,
        "lastStudyAtMillis" to s.lastStudyAtMillis,
    )

    private fun studyTaskTime(s: StudyTaskTimeSnapshot) = linkedMapOf<String, Any?>(
        "todayMillis" to s.todayMillis,
        "lastSevenDaysMillis" to s.lastSevenDaysMillis,
        "answeredTasks" to s.answeredTasks,
    )

    private fun reviewDay(s: ReviewDaySummarySnapshot) = linkedMapOf<String, Any?>(
        "dayStartMillis" to s.dayStartMillis,
        "total" to s.total,
        "again" to s.again,
        "hard" to s.hard,
        "good" to s.good,
        "easy" to s.easy,
        "writingRequired" to s.writingRequired,
        "writingFailed" to s.writingFailed,
    )

    private fun taskTypeDay(s: TaskTypeDaySummarySnapshot) = linkedMapOf<String, Any?>(
        "dayStartMillis" to s.dayStartMillis, "taskType" to s.taskType, "correct" to s.correct, "total" to s.total,
    )

    private fun cumulative(s: CumulativeKanjiSnapshot) = linkedMapOf<String, Any?>(
        "dayStartMillis" to s.dayStartMillis, "cumulativeCount" to s.cumulativeCount,
    )

    private fun repairEvidence(e: KanjiRepairEvidenceSnapshot) = linkedMapOf<String, Any?>(
        "kanji" to e.kanji,
        "status" to e.status.name,
        "reason" to e.reason,
        "explanation" to e.explanation,
        "beforeWeakness" to e.beforeWeakness,
        "afterWeakness" to e.afterWeakness,
        "beforeMatureSupport" to e.beforeMatureSupport,
        "afterMatureSupport" to e.afterMatureSupport,
        "kaniReviews" to e.kaniReviews,
        "writingFailures" to e.writingFailures,
        "lastMistakeAtMillis" to e.lastMistakeAtMillis,
        "lastSyncAtMillis" to e.lastSyncAtMillis,
        "confidence" to e.confidence,
        "confidenceReason" to e.confidenceReason,
    )

    private fun wrongPickCounts(counts: Map<String, Map<String, Int>>): Map<String, Any?> =
        counts.mapValues { (_, inner) -> inner.mapValues { it.value } }

    private fun forecast(f: LadderCompletionForecastPolicy.Forecast) = linkedMapOf<String, Any?>(
        "totalItems" to f.totalItems,
        "burnDown" to f.burnDown.map {
            linkedMapOf<String, Any?>(
                "monthStartMillis" to it.monthStartMillis,
                "completedItems" to it.completedItems,
                "remainingItems" to it.remainingItems,
            )
        },
        "projectedCompletionMonthMillis" to f.projectedCompletionMonthMillis,
        "beyondHorizon" to f.beyondHorizon,
        "alreadyAtCeiling" to f.alreadyAtCeiling,
        "alreadyParked" to f.alreadyParked,
        "alreadyRetired" to f.alreadyRetired,
        "assumptionCopyIds" to f.assumptionCopyIds,
    )

    private fun impactRow(row: KanjiImpactAnalyzer.Row) = linkedMapOf<String, Any?>(
        "kanji" to row.kanji,
        "bucket" to row.bucket,
        "baselineDifficulty" to row.baselineDifficulty,
        "currentDifficulty" to row.currentDifficulty,
        "baselineRetention" to row.baselineRetention,
        "currentRetention" to row.currentRetention,
        "baselineMatureCards" to row.baselineMatureCards,
        "currentMatureCards" to row.currentMatureCards,
        "sameCardCount" to row.sameCardCount,
        "newCardCount" to row.newCardCount,
        "currentCardCount" to row.currentCardCount,
        "reviewCount" to row.reviewCount,
        "advice" to row.advice,
    )

    // --- decoders ---------------------------------------------------------

    private fun outcomeStatsFrom(json: Map<String, Any?>): KaniOutcomeSnapshot {
        val weak = json.obj("weakKanjiImproved")
        val support = json.obj("matureSupportGained")
        val ladder = json.obj("ladderHealth")
        val adaptive = json.obj("adaptiveHealth")
        return KaniOutcomeSnapshot(
            weakKanjiImproved = WeakKanjiImprovedSnapshot(
                weak.int("improvedCount"),
                weak.dbl("averageBeforeWeakness"),
                weak.dbl("averageAfterWeakness"),
                weak.arr("examples").map {
                    val o = it.asObj()
                    KanjiImprovementSnapshot(o.string("kanji"), o.dbl("beforeWeakness"), o.dbl("afterWeakness"))
                },
            ),
            matureSupportGained = MatureSupportGainedSnapshot(
                support.int("gainedSupportCount"),
                support.int("matureSupportGained"),
                support.int("firstSupportCount"),
                support.arr("examples").map {
                    val o = it.asObj()
                    KanjiSupportGainSnapshot(o.string("kanji"), o.int("beforeMatureSupport"), o.int("afterMatureSupport"))
                },
            ),
            ladderHealth = LadderHealthSnapshot(
                rungCounts = ladder.obj("rungCounts").entries.associate { (key, value) ->
                    RecordsBase.LadderRung.fromWireName(key) to (value as? Long)?.toInt().orZero()
                },
                totalActiveItems = ladder.int("totalActiveItems"),
                realDueReviewsToMove = ladder.int("realDueReviewsToMove"),
                ladderPromotionIntervalDays = ladder.int("ladderPromotionIntervalDays"),
                ladderDemotionFailStreak = ladder.int("ladderDemotionFailStreak"),
                promotionReadyCount = ladder.int("promotionReadyCount"),
                demotionRiskCount = ladder.int("demotionRiskCount"),
                demotionReadyCount = ladder.int("demotionReadyCount"),
                stuckCount = ladder.int("stuckCount"),
            ),
            adaptiveHealth = AdaptiveHealthSnapshot(
                coreCounts = adaptive.obj("coreCounts").mapNotNull { (key, value) ->
                    CoreSkill.fromWireName(key)?.let { it to (value as? Long)?.toInt().orZero() }
                }.toMap(),
                activeRepairsByTask = adaptive.obj("activeRepairsByTask").mapValues { (it.value as? Long)?.toInt().orZero() },
                activeRepairsByFailure = adaptive.obj("activeRepairsByFailure").mapNotNull { (key, value) ->
                    FailureKind.fromWireName(key)?.let { it to (value as? Long)?.toInt().orZero() }
                }.toMap(),
                totalAdaptiveItems = adaptive.int("totalAdaptiveItems"),
                contextualCompleteCount = adaptive.int("contextualCompleteCount"),
                activeRepairCount = adaptive.int("activeRepairCount"),
                revalidationPendingCount = adaptive.int("revalidationPendingCount"),
                recentCoreMissCount = adaptive.int("recentCoreMissCount"),
                escalationRiskCount = adaptive.int("escalationRiskCount"),
                stuckRepairCount = adaptive.int("stuckRepairCount"),
                malformedStateCount = adaptive.int("malformedStateCount"),
            ),
        )
    }

    private fun studyImpactFrom(o: Map<String, Any?>) = StudyImpactSnapshot(
        o.int("totalReviews"), o.int("distinctReviewedKanji"), o.int("writingRequired"),
        o.int("writingPassed"), o.int("writingFailed"), o.int("manualOverrides"),
    )

    private fun recentMistakeFrom(o: Map<String, Any?>) =
        RecentMistakeSnapshot(o.string("kanji"), o.string("rating"), o.lng("reviewedAtMillis"))

    private fun studyStreakFrom(o: Map<String, Any?>) = StudyStreakSnapshot(
        o.int("currentDays"), o.int("bestDays"), o.bool("studiedToday"), o.int("reviewsToday"), o.lng("lastStudyAtMillis"),
    )

    private fun studyTaskTimeFrom(o: Map<String, Any?>) =
        StudyTaskTimeSnapshot(o.lng("todayMillis"), o.lng("lastSevenDaysMillis"), o.int("answeredTasks"))

    private fun reviewDayFrom(o: Map<String, Any?>) = ReviewDaySummarySnapshot(
        o.lng("dayStartMillis"), o.int("total"), o.int("again"), o.int("hard"),
        o.int("good"), o.int("easy"), o.int("writingRequired"), o.int("writingFailed"),
    )

    private fun taskTypeDayFrom(o: Map<String, Any?>) =
        TaskTypeDaySummarySnapshot(o.lng("dayStartMillis"), o.string("taskType"), o.int("correct"), o.int("total"))

    private fun cumulativeFrom(o: Map<String, Any?>) =
        CumulativeKanjiSnapshot(o.lng("dayStartMillis"), o.int("cumulativeCount"))

    private fun repairEvidenceFrom(o: Map<String, Any?>) = KanjiRepairEvidenceSnapshot(
        kanji = o.string("kanji"),
        status = KanjiRepairEvidencePolicy.Status.entries.firstOrNull { it.name == o.string("status") }
            ?: KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE,
        reason = o.string("reason"),
        explanation = o.string("explanation"),
        beforeWeakness = o.nullableInt("beforeWeakness"),
        afterWeakness = o.nullableInt("afterWeakness"),
        beforeMatureSupport = o.nullableInt("beforeMatureSupport"),
        afterMatureSupport = o.nullableInt("afterMatureSupport"),
        kaniReviews = o.int("kaniReviews"),
        writingFailures = o.int("writingFailures"),
        lastMistakeAtMillis = o.lng("lastMistakeAtMillis"),
        lastSyncAtMillis = o.lng("lastSyncAtMillis"),
        confidence = o.dbl("confidence"),
        confidenceReason = o.string("confidenceReason"),
    )

    private fun wrongPickCountsFrom(o: Map<String, Any?>): Map<String, Map<String, Int>> =
        o.mapValues { (_, inner) ->
            @Suppress("UNCHECKED_CAST")
            (inner as? Map<String, Any?>).orEmpty().mapValues { (it.value as? Long)?.toInt().orZero() }
        }

    private fun forecastFrom(o: Map<String, Any?>) = LadderCompletionForecastPolicy.Forecast(
        totalItems = o.int("totalItems"),
        burnDown = o.arr("burnDown").map {
            val p = it.asObj()
            LadderCompletionForecastPolicy.MonthPoint(p.lng("monthStartMillis"), p.int("completedItems"), p.int("remainingItems"))
        },
        projectedCompletionMonthMillis = (o["projectedCompletionMonthMillis"] as? Long),
        beyondHorizon = o.bool("beyondHorizon"),
        alreadyAtCeiling = o.int("alreadyAtCeiling"),
        alreadyParked = o.int("alreadyParked"),
        alreadyRetired = o.int("alreadyRetired"),
        assumptionCopyIds = o.arr("assumptionCopyIds").map { it.toString() },
    )

    private fun impactReportFrom(json: Map<String, Any?>): KanjiImpactAnalyzer.Report =
        KanjiImpactAnalyzer.Report(
            json.int("helpedCount"),
            json.int("notHelpingCount"),
            json.int("needsMoreCardsCount"),
            json.arr("rows").map {
                val o = it.asObj()
                KanjiImpactAnalyzer.Row.create(
                    o.string("kanji"),
                    o.string("bucket"),
                    o.dbl("baselineDifficulty"),
                    o.dbl("currentDifficulty"),
                    o.dbl("baselineRetention"),
                    o.dbl("currentRetention"),
                    o.int("baselineMatureCards"),
                    o.int("currentMatureCards"),
                    o.int("sameCardCount"),
                    o.int("newCardCount"),
                    o.int("currentCardCount"),
                    o.int("reviewCount"),
                    o.string("advice"),
                )
            },
        )

    // --- decode helpers ---------------------------------------------------

    private fun Int?.orZero(): Int = this ?: 0

    private fun StatsSnapshot.timeZoneId(): String = java.util.TimeZone.getDefault().id

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(key: String): Map<String, Any?> =
        (this[key] as? Map<String, Any?>) ?: emptyMap()

    private fun Map<String, Any?>.arr(key: String): List<Any?> =
        (this[key] as? List<Any?>) ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObj(): Map<String, Any?> = (this as? Map<String, Any?>) ?: emptyMap()

    private fun Map<String, Any?>.string(key: String): String = (this[key] as? String).orEmpty()

    private fun Map<String, Any?>.int(key: String): Int = (this[key] as? Long)?.toInt() ?: 0

    private fun Map<String, Any?>.nullableInt(key: String): Int? = (this[key] as? Long)?.toInt()

    private fun Map<String, Any?>.lng(key: String): Long = (this[key] as? Long) ?: 0L

    private fun Map<String, Any?>.dbl(key: String): Double =
        (this[key] as? Double) ?: (this[key] as? Long)?.toDouble() ?: 0.0

    private fun Map<String, Any?>.bool(key: String): Boolean = (this[key] as? Boolean) ?: false
}
