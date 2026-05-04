package dev.bee.kanjianki.data.local

import dev.bee.kanjianki.domain.DashboardRowSnapshot
import dev.bee.kanjianki.domain.DashboardSnapshot
import dev.bee.kanjianki.domain.DashboardSummarySnapshot
import dev.bee.kanjianki.domain.HandwritingPolicySnapshot
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.domain.SettingsSnapshot
import dev.bee.kanjianki.domain.SourceCounts
import dev.bee.kanjianki.domain.StudyOverviewSnapshot
import dev.bee.kanjianki.domain.StudyQueuePreviewSnapshot
import dev.bee.kanjianki.domain.StudyReviewSnapshot
import dev.bee.kanjianki.domain.StudySessionSnapshot
import org.json.JSONArray
import org.json.JSONObject

internal object RoomCacheCodec {
    fun encodeSettings(snapshot: SettingsSnapshot): String =
        JSONObject()
            .put("ankiConnectUrl", snapshot.ankiConnectUrl)
            .put("noteModels", JSONArray(snapshot.noteModels))
            .put("expressionField", snapshot.expressionField)
            .put("readingField", snapshot.readingField)
            .put("meaningField", snapshot.meaningField)
            .put("matureDays", snapshot.matureDays)
            .put("kanjiSupportThreshold", snapshot.kanjiSupportThreshold)
            .put("jitenCacheTtlHours", snapshot.jitenCacheTtlHours)
            .put("jitenRequestTimeoutSeconds", snapshot.jitenRequestTimeoutSeconds)
            .put("pollingEnabled", snapshot.pollingEnabled)
            .put("pollingIntervalSeconds", snapshot.pollingIntervalSeconds)
            .toString()

    fun decodeSettings(raw: String): SettingsSnapshot {
        val node = JSONObject(raw)
        return SettingsSnapshot(
            ankiConnectUrl = node.getString("ankiConnectUrl"),
            noteModels = node.getJSONArray("noteModels").stringList(),
            expressionField = node.getString("expressionField"),
            readingField = node.getString("readingField"),
            meaningField = node.getString("meaningField"),
            matureDays = node.getInt("matureDays"),
            kanjiSupportThreshold = node.getInt("kanjiSupportThreshold"),
            jitenCacheTtlHours = node.getInt("jitenCacheTtlHours"),
            jitenRequestTimeoutSeconds = node.getInt("jitenRequestTimeoutSeconds"),
            pollingEnabled = node.getBoolean("pollingEnabled"),
            pollingIntervalSeconds = node.getInt("pollingIntervalSeconds"),
        )
    }

    fun encodeDashboard(snapshot: DashboardSnapshot): String =
        JSONObject()
            .put("summary", encodeDashboardSummary(snapshot.summary))
            .put("rows", JSONArray(snapshot.rows.map(::encodeDashboardRow)))
            .put("problemSeedCount", snapshot.problemSeedCount)
            .put("warnings", JSONArray(snapshot.warnings))
            .put("sourceCounts", encodeSourceCounts(snapshot.sourceCounts))
            .toString()

    fun decodeDashboard(raw: String): DashboardSnapshot {
        val node = JSONObject(raw)
        return DashboardSnapshot(
            summary = decodeDashboardSummary(node.getJSONObject("summary")),
            rows = node.getJSONArray("rows").objectList(::decodeDashboardRow),
            problemSeedCount = node.getInt("problemSeedCount"),
            warnings = node.getJSONArray("warnings").stringList(),
            sourceCounts = decodeSourceCounts(node.getJSONObject("sourceCounts")),
        )
    }

    fun encodeKanjiDetail(snapshot: KanjiDetailSnapshot): String =
        JSONObject()
            .put("kanji", snapshot.kanji)
            .put("jitenRank", snapshot.jitenRank)
            .put("keyword", snapshot.keyword)
            .put("meanings", JSONArray(snapshot.meanings))
            .put("onReadings", JSONArray(snapshot.onReadings))
            .put("kunReadings", JSONArray(snapshot.kunReadings))
            .put("components", JSONArray(snapshot.components))
            .put("componentHint", snapshot.componentHint)
            .put("strokeCount", snapshot.strokeCount)
            .put("browserSearch", snapshot.browserSearch)
            .put("collectionExamples", JSONArray(snapshot.collectionExamples))
            .put("suspendedExamples", JSONArray(snapshot.suspendedExamples))
            .put("activeRecurringExamples", JSONArray(snapshot.activeRecurringExamples))
            .put("matureExamples", JSONArray(snapshot.matureExamples))
            .toString()

    fun decodeKanjiDetail(raw: String): KanjiDetailSnapshot {
        val node = JSONObject(raw)
        return KanjiDetailSnapshot(
            kanji = node.getString("kanji"),
            jitenRank = node.optNullableDouble("jitenRank"),
            keyword = node.getString("keyword"),
            meanings = node.getJSONArray("meanings").stringList(),
            onReadings = node.getJSONArray("onReadings").stringList(),
            kunReadings = node.getJSONArray("kunReadings").stringList(),
            components = node.getJSONArray("components").stringList(),
            componentHint = node.getString("componentHint"),
            strokeCount = node.getInt("strokeCount"),
            browserSearch = node.getString("browserSearch"),
            collectionExamples = node.getJSONArray("collectionExamples").stringList(),
            suspendedExamples = node.getJSONArray("suspendedExamples").stringList(),
            activeRecurringExamples = node.getJSONArray("activeRecurringExamples").stringList(),
            matureExamples = node.getJSONArray("matureExamples").stringList(),
        )
    }

    fun encodeStudyOverview(snapshot: StudyOverviewSnapshot): String =
        JSONObject()
            .put("dueCount", snapshot.dueCount)
            .put("newCount", snapshot.newCount)
            .put("activeQueueCount", snapshot.activeQueueCount)
            .put("inactiveCount", snapshot.inactiveCount)
            .put("currentProblemSeedCount", snapshot.currentProblemSeedCount)
            .put("nextDueAt", snapshot.nextDueAt)
            .put("queuePreview", JSONArray(snapshot.queuePreview.map(::encodeQueuePreview)))
            .toString()

    fun decodeStudyOverview(raw: String): StudyOverviewSnapshot {
        val node = JSONObject(raw)
        return StudyOverviewSnapshot(
            dueCount = node.getInt("dueCount"),
            newCount = node.getInt("newCount"),
            activeQueueCount = node.getInt("activeQueueCount"),
            inactiveCount = node.getInt("inactiveCount"),
            currentProblemSeedCount = node.getInt("currentProblemSeedCount"),
            nextDueAt = node.optNullableString("nextDueAt"),
            queuePreview = node.getJSONArray("queuePreview").objectList(::decodeQueuePreview),
        )
    }

    fun encodeSeedRefresh(snapshot: SeedRefreshSnapshot): String =
        JSONObject()
            .put("introducedCount", snapshot.introducedCount)
            .put("updatedCount", snapshot.updatedCount)
            .put("reactivatedCount", snapshot.reactivatedCount)
            .put("inactivatedCount", snapshot.inactivatedCount)
            .put("currentProblemSeedCount", snapshot.currentProblemSeedCount)
            .toString()

    fun decodeSeedRefresh(raw: String): SeedRefreshSnapshot {
        val node = JSONObject(raw)
        return SeedRefreshSnapshot(
            introducedCount = node.getInt("introducedCount"),
            updatedCount = node.getInt("updatedCount"),
            reactivatedCount = node.getInt("reactivatedCount"),
            inactivatedCount = node.getInt("inactivatedCount"),
            currentProblemSeedCount = node.getInt("currentProblemSeedCount"),
        )
    }

    fun encodeStudySession(snapshot: StudySessionSnapshot): String =
        JSONObject()
            .put("kanji", snapshot.kanji)
            .put("reviewToken", snapshot.reviewToken)
            .put("promptType", snapshot.promptType)
            .put("promptLabel", snapshot.promptLabel)
            .put("taskKind", snapshot.taskKind)
            .put("schedulerPhase", snapshot.schedulerPhase)
            .put("requiresWriting", snapshot.requiresWriting)
            .put("itemStatus", snapshot.itemStatus)
            .put("reviewCount", snapshot.reviewCount)
            .put("guideLevelLabel", snapshot.guideLevelLabel)
            .put("handwritingPolicy", encodeHandwritingPolicy(snapshot.handwritingPolicy))
            .put("keyword", snapshot.keyword)
            .put("productionContext", JSONArray(snapshot.productionContext))
            .put("recognitionContext", JSONArray(snapshot.recognitionContext))
            .put("supportWords", JSONArray(snapshot.supportWords))
            .put("painExample", snapshot.painExample)
            .put("bridgeExample", snapshot.bridgeExample)
            .put("matureExample", snapshot.matureExample)
            .toString()

    fun encodeStudyReview(snapshot: StudyReviewSnapshot): String =
        JSONObject()
            .put("binaryOutcome", snapshot.binaryOutcome)
            .put("reviewedAt", snapshot.reviewedAt)
            .put("itemStatus", snapshot.itemStatus)
            .put("reviewCount", snapshot.reviewCount)
            .put("guideLevelLabel", snapshot.guideLevelLabel)
            .put("dueAt", snapshot.dueAt)
            .put("overviewDueCount", snapshot.overviewDueCount)
            .toString()

    private fun encodeSourceCounts(snapshot: SourceCounts): JSONObject =
        JSONObject()
            .put("noteCount", snapshot.noteCount)
            .put("cardCount", snapshot.cardCount)

    private fun decodeSourceCounts(node: JSONObject): SourceCounts =
        SourceCounts(
            noteCount = node.getInt("noteCount"),
            cardCount = node.getInt("cardCount"),
        )

    private fun encodeDashboardSummary(snapshot: DashboardSummarySnapshot): JSONObject =
        JSONObject()
            .put("totalKanjiCount", snapshot.totalKanjiCount)
            .put("unknownKanjiCount", snapshot.unknownKanjiCount)
            .put("averageKanjiRank", snapshot.averageKanjiRank)
            .put("matureSupportThreshold", snapshot.matureSupportThreshold)
            .put("rankedKanjiCount", snapshot.rankedKanjiCount)

    private fun decodeDashboardSummary(node: JSONObject): DashboardSummarySnapshot =
        DashboardSummarySnapshot(
            totalKanjiCount = node.getInt("totalKanjiCount"),
            unknownKanjiCount = node.getInt("unknownKanjiCount"),
            averageKanjiRank = node.optNullableDouble("averageKanjiRank"),
            matureSupportThreshold = node.getInt("matureSupportThreshold"),
            rankedKanjiCount = node.getInt("rankedKanjiCount"),
        )

    private fun encodeDashboardRow(snapshot: DashboardRowSnapshot): JSONObject =
        JSONObject()
            .put("kanji", snapshot.kanji)
            .put("jitenRank", snapshot.jitenRank)
            .put("collectionExpressionCount", snapshot.collectionExpressionCount)
            .put("suspendedExpressionCount", snapshot.suspendedExpressionCount)
            .put("activeRecurringExpressionCount", snapshot.activeRecurringExpressionCount)
            .put("matureSupportCount", snapshot.matureSupportCount)
            .put("supportDeficit", snapshot.supportDeficit)
            .put("isUnknown", snapshot.isUnknown)
            .put("browserSearch", snapshot.browserSearch)

    private fun decodeDashboardRow(node: JSONObject): DashboardRowSnapshot =
        DashboardRowSnapshot(
            kanji = node.getString("kanji"),
            jitenRank = node.optNullableDouble("jitenRank"),
            collectionExpressionCount = node.getInt("collectionExpressionCount"),
            suspendedExpressionCount = node.getInt("suspendedExpressionCount"),
            activeRecurringExpressionCount = node.getInt("activeRecurringExpressionCount"),
            matureSupportCount = node.getInt("matureSupportCount"),
            supportDeficit = node.getInt("supportDeficit"),
            isUnknown = node.getBoolean("isUnknown"),
            browserSearch = node.getString("browserSearch"),
        )

    private fun encodeQueuePreview(snapshot: StudyQueuePreviewSnapshot): JSONObject =
        JSONObject()
            .put("kanji", snapshot.kanji)
            .put("itemStatus", snapshot.itemStatus)
            .put("dueAt", snapshot.dueAt)
            .put("dueNow", snapshot.dueNow)
            .put("guideLevelLabel", snapshot.guideLevelLabel)
            .put("supportDeficit", snapshot.supportDeficit)
            .put("suspendedExpressionCount", snapshot.suspendedExpressionCount)

    private fun decodeQueuePreview(node: JSONObject): StudyQueuePreviewSnapshot =
        StudyQueuePreviewSnapshot(
            kanji = node.getString("kanji"),
            itemStatus = node.getString("itemStatus"),
            dueAt = node.optNullableString("dueAt"),
            dueNow = node.getBoolean("dueNow"),
            guideLevelLabel = node.getString("guideLevelLabel"),
            supportDeficit = node.getInt("supportDeficit"),
            suspendedExpressionCount = node.getInt("suspendedExpressionCount"),
        )

    private fun encodeHandwritingPolicy(snapshot: HandwritingPolicySnapshot): JSONObject =
        JSONObject()
            .put("required", snapshot.required)
            .put("guideMode", snapshot.guideMode)
            .put("guideLevelLabel", snapshot.guideLevelLabel)
            .put("guidedEvaluationAvailable", snapshot.guidedEvaluationAvailable)
            .put("manualOnlyWithoutGeometry", snapshot.manualOnlyWithoutGeometry)
            .put("allowedRatingsOnFailure", JSONArray(snapshot.allowedRatingsOnFailure))

}

private fun JSONArray.stringList(): List<String> =
    List(length()) { index -> getString(index) }

private fun <T> JSONArray.objectList(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key) || !has(key)) null else getString(key)

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (isNull(key) || !has(key)) null else getDouble(key)
