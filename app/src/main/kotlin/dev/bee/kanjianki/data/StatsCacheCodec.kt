package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecordsBase
import org.json.JSONArray
import org.json.JSONObject

object StatsCacheCodec {
    @JvmStatic
    internal fun outcomeToJson(
        stats: StudyStatsStore.KaniOutcomeStats?,
        studyImpactStats: StudyStatsStore.StudyImpactStats? = null,
        recentMistakes: List<StudyStatsStore.RecentMistake>? = null,
        studyStreak: StudyStatsStore.StudyStreak? = null,
        studyTaskTimeStats: StudyStatsStore.StudyTaskTimeStats? = null,
        reviewDaySummaries: List<StatsCacheStore.ReviewDaySummarySnapshot>? = null,
        kanjiRepairEvidence: List<StudyStatsStore.KanjiRepairEvidence>? = null,
    ): String {
        val safe = stats ?: StudyStatsStore.KaniOutcomeStats.empty()
        val root = JSONObject()
            .put("weakKanjiImproved", weakKanjiImprovedToJson(safe.weakKanjiImproved))
            .put("matureSupportGained", matureSupportGainedToJson(safe.matureSupportGained))
            .put("ladderHealth", ladderHealthToJson(safe.ladderHealth))
        val hasExtras = studyImpactStats != null || recentMistakes != null || studyStreak != null || studyTaskTimeStats != null || reviewDaySummaries != null || kanjiRepairEvidence != null
        if (hasExtras) {
            root.put("cacheFormatVersion", STATS_CACHE_FORMAT_VERSION)
            root.put("studyImpactStats", studyImpactStatsToJson(studyImpactStats ?: StudyStatsStore.StudyImpactStats(0, 0, 0, 0, 0, 0)))
            root.put("recentMistakes", recentMistakesToJson(recentMistakes ?: emptyList()))
            root.put("studyStreak", studyStreakToJson(studyStreak ?: StudyStatsStore.StudyStreak(0, 0, false, 0, 0L)))
            root.put("studyTaskTimeStats", studyTaskTimeStatsToJson(studyTaskTimeStats ?: StudyStatsStore.StudyTaskTimeStats(0L, 0L, 0)))
            root.put("reviewDaySummaries", reviewDaySummariesToJson(reviewDaySummaries ?: emptyList()))
            root.put("kanjiRepairEvidence", kanjiRepairEvidenceToJson(kanjiRepairEvidence ?: emptyList()))
        } else {
            root.put("cacheFormatVersion", 1)
        }
        return root.toString()
    }

    @JvmStatic
    fun outcomeFromJson(json: String?): StudyStatsStore.KaniOutcomeStats {
        return try {
            val root = JSONObject(json ?: return StudyStatsStore.KaniOutcomeStats.empty())
            StudyStatsStore.KaniOutcomeStats(
                weakKanjiImprovedFromJson(root.optJSONObject("weakKanjiImproved")),
                matureSupportGainedFromJson(root.optJSONObject("matureSupportGained")),
                ladderHealthFromJson(root.optJSONObject("ladderHealth"))
            )
        } catch (_: Exception) {
            StudyStatsStore.KaniOutcomeStats.empty()
        }
    }

    @JvmStatic
    fun impactReportToJson(report: KanjiImpactAnalyzer.Report?): String {
        val safe = report ?: emptyImpactReport()
        return JSONObject()
            .put("helpedCount", safe.helpedCount)
            .put("notHelpingCount", safe.notHelpingCount)
            .put("needsMoreCardsCount", safe.needsMoreCardsCount)
            .put(
                "rows",
                JSONArray().also { array ->
                    safe.rows.forEach { row -> array.put(impactRowToJson(row)) }
                }
            )
            .toString()
    }

    @JvmStatic
    fun impactReportFromJson(json: String?): KanjiImpactAnalyzer.Report {
        return try {
            val root = JSONObject(json ?: return emptyImpactReport())
            KanjiImpactAnalyzer.Report(
                root.optInt("helpedCount", 0),
                root.optInt("notHelpingCount", 0),
                root.optInt("needsMoreCardsCount", 0),
                impactRowsFromJson(root.optJSONArray("rows"))
            )
        } catch (_: Exception) {
            emptyImpactReport()
        }
    }

    @JvmStatic
    fun studyImpactStatsFromJson(json: JSONObject?): StudyStatsStore.StudyImpactStats {
        if (json == null) {
            return StudyStatsStore.StudyImpactStats(0, 0, 0, 0, 0, 0)
        }
        return StudyStatsStore.StudyImpactStats(
            json.optInt("totalReviews", 0),
            json.optInt("distinctReviewedKanji", 0),
            json.optInt("writingRequired", 0),
            json.optInt("writingPassed", 0),
            json.optInt("writingFailed", 0),
            json.optInt("manualOverrides", 0),
        )
    }

    @JvmStatic
    fun studyStreakFromJson(json: JSONObject?): StudyStatsStore.StudyStreak {
        if (json == null) {
            return StudyStatsStore.StudyStreak(0, 0, false, 0, 0L)
        }
        return StudyStatsStore.StudyStreak(
            json.optInt("currentDays", 0),
            json.optInt("bestDays", 0),
            json.optBoolean("studiedToday", false),
            json.optInt("reviewsToday", 0),
            json.optLong("lastStudyAtMillis", 0L),
        )
    }

    @JvmStatic
    fun recentMistakesFromJson(array: JSONArray?): List<StudyStatsStore.RecentMistake> {
        if (array == null) {
            return emptyList()
        }
        val out = ArrayList<StudyStatsStore.RecentMistake>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            out.add(
                StudyStatsStore.RecentMistake(
                    json.optString("kanji", ""),
                    json.optString("rating", ""),
                    json.optLong("reviewedAtMillis", 0L),
                )
            )
        }
        return out
    }

    @JvmStatic
    internal fun reviewDaySummariesFromJson(array: JSONArray?): List<StatsCacheStore.ReviewDaySummarySnapshot> {
        if (array == null) {
            return emptyList()
        }
        val out = ArrayList<StatsCacheStore.ReviewDaySummarySnapshot>()
        for (index in 0 until minOf(array.length(), STATS_REVIEW_DAY_SUMMARY_LIMIT)) {
            val json = array.optJSONObject(index) ?: continue
            out.add(
                StatsCacheStore.ReviewDaySummarySnapshot(
                    dayStartMillis = json.optLong("dayStartMillis", 0L),
                    total = json.optInt("total", 0),
                    again = json.optInt("again", 0),
                    hard = json.optInt("hard", 0),
                    good = json.optInt("good", 0),
                    easy = json.optInt("easy", 0),
                    writingRequired = json.optInt("writingRequired", 0),
                    writingFailed = json.optInt("writingFailed", 0),
                )
            )
        }
        return out
    }

    @JvmStatic
    internal fun kanjiRepairEvidenceFromJson(array: JSONArray?): List<StudyStatsStore.KanjiRepairEvidence> {
        if (array == null) {
            return emptyList()
        }
        val out = ArrayList<StudyStatsStore.KanjiRepairEvidence>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            out.add(
                StudyStatsStore.repairEvidence(
                    KanjiRepairEvidencePolicy.Evidence(
                        kanjiArg = json.optString("kanji", ""),
                        statusArg = repairEvidenceStatusFromJson(json.optString("status", "")),
                        reasonArg = json.optString("reason", ""),
                        explanationArg = json.optString("explanation", ""),
                        beforeWeaknessArg = nullableInt(json, "beforeWeakness"),
                        afterWeaknessArg = nullableInt(json, "afterWeakness"),
                        beforeMatureSupportArg = nullableInt(json, "beforeMatureSupport"),
                        afterMatureSupportArg = nullableInt(json, "afterMatureSupport"),
                        kaniReviewsArg = json.optInt("kaniReviews", 0),
                        writingFailuresArg = json.optInt("writingFailures", 0),
                        lastMistakeAtMillisArg = json.optLong("lastMistakeAtMillis", 0L),
                        lastSyncAtMillisArg = json.optLong("lastSyncAtMillis", 0L),
                        confidenceArg = json.optDouble("confidence", 0.0),
                        confidenceReasonArg = json.optString("confidenceReason", ""),
                    )
                )
            )
        }
        return out
    }

    private fun weakKanjiImprovedToJson(metric: StudyStatsStore.WeakKanjiImprovedMetric): JSONObject {
        return JSONObject()
            .put("improvedCount", metric.improvedCount)
            .put("averageBeforeWeakness", metric.averageBeforeWeakness)
            .put("averageAfterWeakness", metric.averageAfterWeakness)
            .put(
                "examples",
                JSONArray().also { array ->
                    metric.examples.forEach { example ->
                        array.put(
                            JSONObject()
                                .put("kanji", example.kanji)
                                .put("beforeWeakness", example.beforeWeakness)
                                .put("afterWeakness", example.afterWeakness)
                        )
                    }
                }
            )
    }

    private fun weakKanjiImprovedFromJson(json: JSONObject?): StudyStatsStore.WeakKanjiImprovedMetric {
        if (json == null) {
            return StudyStatsStore.WeakKanjiImprovedMetric.empty()
        }
        return StudyStatsStore.WeakKanjiImprovedMetric(
            json.optInt("improvedCount", 0),
            json.optDouble("averageBeforeWeakness", 0.0),
            json.optDouble("averageAfterWeakness", 0.0),
            kanjiImprovementList(json.optJSONArray("examples"))
        )
    }

    private fun kanjiImprovementList(array: JSONArray?): List<StudyStatsStore.KanjiImprovement> {
        if (array == null) {
            return emptyList()
        }
        val out = ArrayList<StudyStatsStore.KanjiImprovement>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            out.add(
                StudyStatsStore.KanjiImprovement(
                    json.optString("kanji", ""),
                    json.optDouble("beforeWeakness", 0.0),
                    json.optDouble("afterWeakness", 0.0)
                )
            )
        }
        return out
    }

    private fun matureSupportGainedToJson(metric: StudyStatsStore.MatureSupportGainedMetric): JSONObject {
        return JSONObject()
            .put("gainedSupportCount", metric.gainedSupportCount)
            .put("matureSupportGained", metric.matureSupportGained)
            .put("firstSupportCount", metric.firstSupportCount)
            .put(
                "examples",
                JSONArray().also { array ->
                    metric.examples.forEach { example ->
                        array.put(
                            JSONObject()
                                .put("kanji", example.kanji)
                                .put("beforeMatureSupport", example.beforeMatureSupport)
                                .put("afterMatureSupport", example.afterMatureSupport)
                        )
                    }
                }
            )
    }

    private fun studyImpactStatsToJson(stats: StudyStatsStore.StudyImpactStats): JSONObject {
        return JSONObject()
            .put("totalReviews", stats.totalReviews)
            .put("distinctReviewedKanji", stats.distinctReviewedKanji)
            .put("writingRequired", stats.writingRequired)
            .put("writingPassed", stats.writingPassed)
            .put("writingFailed", stats.writingFailed)
            .put("manualOverrides", stats.manualOverrides)
    }

    private fun studyStreakToJson(stats: StudyStatsStore.StudyStreak): JSONObject {
        return JSONObject()
            .put("currentDays", stats.currentDays)
            .put("bestDays", stats.bestDays)
            .put("studiedToday", stats.studiedToday)
            .put("reviewsToday", stats.reviewsToday)
            .put("lastStudyAtMillis", stats.lastStudyAtMillis)
    }

    @JvmStatic
    fun studyTaskTimeStatsFromJson(json: JSONObject?): StudyStatsStore.StudyTaskTimeStats {
        if (json == null) {
            return StudyStatsStore.StudyTaskTimeStats(0L, 0L, 0)
        }
        return StudyStatsStore.StudyTaskTimeStats(
            json.optLong("todayMillis", 0L),
            json.optLong("lastSevenDaysMillis", 0L),
            json.optInt("answeredTasks", 0),
        )
    }

    private fun recentMistakesToJson(mistakes: List<StudyStatsStore.RecentMistake>): JSONArray {
        return JSONArray().also { array ->
            mistakes.forEach { mistake ->
                array.put(
                    JSONObject()
                        .put("kanji", mistake.kanji)
                        .put("rating", mistake.rating)
                        .put("reviewedAtMillis", mistake.reviewedAtMillis)
                )
            }
        }
    }

    private fun reviewDaySummariesToJson(summaries: List<StatsCacheStore.ReviewDaySummarySnapshot>): JSONArray {
        return JSONArray().also { array ->
            summaries.take(STATS_REVIEW_DAY_SUMMARY_LIMIT).forEach { summary ->
                array.put(
                    JSONObject()
                        .put("dayStartMillis", summary.dayStartMillis)
                        .put("total", summary.total)
                        .put("again", summary.again)
                        .put("hard", summary.hard)
                        .put("good", summary.good)
                        .put("easy", summary.easy)
                        .put("writingRequired", summary.writingRequired)
                        .put("writingFailed", summary.writingFailed)
                )
            }
        }
    }

    private fun kanjiRepairEvidenceToJson(evidence: List<StudyStatsStore.KanjiRepairEvidence>): JSONArray {
        return JSONArray().also { array ->
            evidence.forEach { item ->
                array.put(
                    JSONObject()
                        .put("kanji", item.kanji)
                        .put("status", item.status.name)
                        .put("reason", item.reason)
                        .put("explanation", item.explanation)
                        .putNullableInt("beforeWeakness", item.beforeWeakness)
                        .putNullableInt("afterWeakness", item.afterWeakness)
                        .putNullableInt("beforeMatureSupport", item.beforeMatureSupport)
                        .putNullableInt("afterMatureSupport", item.afterMatureSupport)
                        .put("kaniReviews", item.kaniReviews)
                        .put("writingFailures", item.writingFailures)
                        .put("lastMistakeAtMillis", item.lastMistakeAtMillis)
                        .put("lastSyncAtMillis", item.lastSyncAtMillis)
                        .put("confidence", item.confidence)
                        .put("confidenceReason", item.confidenceReason)
                )
            }
        }
    }

    private fun studyTaskTimeStatsToJson(stats: StudyStatsStore.StudyTaskTimeStats): JSONObject {
        return JSONObject()
            .put("todayMillis", stats.todayMillis)
            .put("lastSevenDaysMillis", stats.lastSevenDaysMillis)
            .put("answeredTasks", stats.answeredTasks)
    }

    private fun matureSupportGainedFromJson(json: JSONObject?): StudyStatsStore.MatureSupportGainedMetric {
        if (json == null) {
            return StudyStatsStore.MatureSupportGainedMetric.empty()
        }
        return StudyStatsStore.MatureSupportGainedMetric(
            json.optInt("gainedSupportCount", 0),
            json.optInt("matureSupportGained", 0),
            json.optInt("firstSupportCount", 0),
            kanjiSupportGainList(json.optJSONArray("examples"))
        )
    }

    private fun kanjiSupportGainList(array: JSONArray?): List<StudyStatsStore.KanjiSupportGain> {
        if (array == null) {
            return emptyList()
        }
        val out = ArrayList<StudyStatsStore.KanjiSupportGain>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            out.add(
                StudyStatsStore.KanjiSupportGain(
                    json.optString("kanji", ""),
                    json.optInt("beforeMatureSupport", 0),
                    json.optInt("afterMatureSupport", 0)
                )
            )
        }
        return out
    }

    private fun ladderHealthToJson(metric: StudyStatsStore.LadderHealthMetric): JSONObject {
        val rungCounts = JSONObject()
        metric.rungCounts.forEach { (rung, count) ->
            rungCounts.put(rung.wireName(), count)
        }
        return JSONObject()
            .put("rungCounts", rungCounts)
            .put("totalActiveItems", metric.totalActiveItems)
            .put("ladderPromotionIntervalDays", metric.ladderPromotionIntervalDays)
            .put("ladderDemotionFailStreak", metric.ladderDemotionFailStreak)
            .put("promotionReadyCount", metric.promotionReadyCount)
            .put("demotionRiskCount", metric.demotionRiskCount)
            .put("demotionReadyCount", metric.demotionReadyCount)
            .put("stuckCount", metric.stuckCount)
    }

    private fun ladderHealthFromJson(json: JSONObject?): StudyStatsStore.LadderHealthMetric {
        if (json == null) {
            return StudyStatsStore.LadderHealthMetric.empty()
        }
        return StudyStatsStore.LadderHealthMetric(
            rungCountsFromJson(json.optJSONObject("rungCounts")),
            json.optInt("totalActiveItems", 0),
            json.optInt("ladderPromotionIntervalDays", 0),
            json.optInt("ladderDemotionFailStreak", 0),
            json.optInt("promotionReadyCount", 0),
            json.optInt("demotionRiskCount", 0),
            json.optInt("demotionReadyCount", 0),
            json.optInt("stuckCount", 0)
        )
    }

    private fun rungCountsFromJson(json: JSONObject?): Map<RecordsBase.LadderRung, Int> {
        if (json == null) {
            return emptyMap()
        }
        val out = LinkedHashMap<RecordsBase.LadderRung, Int>()
        val names = json.names() ?: return out
        for (index in 0 until names.length()) {
            val name = names.optString(index, "")
            out[RecordsBase.LadderRung.fromWireName(name)] = json.optInt(name, 0)
        }
        return out
    }

    private fun impactRowToJson(row: KanjiImpactAnalyzer.Row): JSONObject {
        return JSONObject()
            .put("kanji", row.kanji)
            .put("bucket", row.bucket)
            .put("baselineDifficulty", row.baselineDifficulty)
            .put("currentDifficulty", row.currentDifficulty)
            .put("baselineRetention", row.baselineRetention)
            .put("currentRetention", row.currentRetention)
            .put("baselineMatureCards", row.baselineMatureCards)
            .put("currentMatureCards", row.currentMatureCards)
            .put("sameCardCount", row.sameCardCount)
            .put("newCardCount", row.newCardCount)
            .put("currentCardCount", row.currentCardCount)
            .put("reviewCount", row.reviewCount)
            .put("advice", row.advice)
    }

    private fun impactRowsFromJson(array: JSONArray?): List<KanjiImpactAnalyzer.Row> {
        if (array == null) {
            return emptyList()
        }
        val out = ArrayList<KanjiImpactAnalyzer.Row>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            out.add(
                KanjiImpactAnalyzer.Row.create(
                    kanji = json.optString("kanji", ""),
                    bucket = json.optString("bucket", KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS),
                    baselineDifficulty = json.optDouble("baselineDifficulty", 0.0),
                    currentDifficulty = json.optDouble("currentDifficulty", 0.0),
                    baselineRetention = json.optDouble("baselineRetention", 0.0),
                    currentRetention = json.optDouble("currentRetention", 0.0),
                    baselineMatureCards = json.optInt("baselineMatureCards", 0),
                    currentMatureCards = json.optInt("currentMatureCards", 0),
                    sameCardCount = json.optInt("sameCardCount", 0),
                    newCardCount = json.optInt("newCardCount", 0),
                    currentCardCount = json.optInt("currentCardCount", 0),
                    reviewCount = json.optInt("reviewCount", 0),
                    advice = json.optString("advice", "")
                )
            )
        }
        return out
    }

    private fun repairEvidenceStatusFromJson(value: String): KanjiRepairEvidencePolicy.Status {
        return try {
            KanjiRepairEvidencePolicy.Status.valueOf(value)
        } catch (_: Exception) {
            KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE
        }
    }

    private fun nullableInt(json: JSONObject, key: String): Int? {
        return if (json.has(key) && !json.isNull(key)) json.optInt(key, 0) else null
    }

    private fun JSONObject.putNullableInt(key: String, value: Int?): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private fun emptyImpactReport(): KanjiImpactAnalyzer.Report {
        return KanjiImpactAnalyzer.Report(0, 0, 0, emptyList())
    }
}
