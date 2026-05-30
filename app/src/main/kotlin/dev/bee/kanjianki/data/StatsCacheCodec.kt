package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsBase
import org.json.JSONArray
import org.json.JSONObject

object StatsCacheCodec {
    @JvmStatic
    fun outcomeToJson(stats: StudyStatsStore.KaniOutcomeStats?): String {
        val safe = stats ?: StudyStatsStore.KaniOutcomeStats.empty()
        return JSONObject()
            .put("weakKanjiImproved", weakKanjiImprovedToJson(safe.weakKanjiImproved))
            .put("matureSupportGained", matureSupportGainedToJson(safe.matureSupportGained))
            .put("ladderHealth", ladderHealthToJson(safe.ladderHealth))
            .toString()
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
            json.optInt("demotionReadyCount", 0)
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
}
