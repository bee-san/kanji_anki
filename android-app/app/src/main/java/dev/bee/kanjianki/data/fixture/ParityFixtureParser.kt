package dev.bee.kanjianki.data.fixture

import android.content.Context
import dev.bee.kanjianki.data.ankidroid.AnkiDroidCardSnapshot
import dev.bee.kanjianki.data.ankidroid.AnkiDroidCollectionSnapshot
import dev.bee.kanjianki.data.ankidroid.AnkiDroidNoteSnapshot
import dev.bee.kanjianki.domain.DashboardRowSnapshot
import dev.bee.kanjianki.domain.DashboardSnapshot
import dev.bee.kanjianki.domain.DashboardSummarySnapshot
import dev.bee.kanjianki.domain.HandwritingPolicySnapshot
import dev.bee.kanjianki.domain.HealthSnapshot
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.domain.LatestSyncSnapshot
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.domain.SettingsSnapshot
import dev.bee.kanjianki.domain.SourceCounts
import dev.bee.kanjianki.domain.StudyOverviewSnapshot
import dev.bee.kanjianki.domain.StudyQueuePreviewSnapshot
import dev.bee.kanjianki.domain.StudyReviewSnapshot
import dev.bee.kanjianki.domain.StudySessionSnapshot
import org.json.JSONArray
import org.json.JSONObject

class ParityFixtureParser private constructor(
    private val root: JSONObject,
) {
    companion object {
        fun fromAsset(context: Context, assetName: String = "oracle-v1.json"): ParityFixtureParser {
            val payload = context.assets.open(assetName).bufferedReader().use { it.readText() }
            return ParityFixtureParser(JSONObject(payload))
        }
    }

    fun health(): HealthSnapshot {
        val node = root.getJSONObject("health")
        return HealthSnapshot(
            version = node.getString("version"),
            databasePath = node.getString("databasePath"),
            webAppPath = node.getString("webAppPath"),
            sourceCounts = parseSourceCounts(node.getJSONObject("sourceCounts")),
            latestSync = node.optJSONObject("latestSync")?.let(::parseLatestSync),
        )
    }

    fun settings(): SettingsSnapshot = parseSettings(root.getJSONObject("settings"))

    fun dashboard(): DashboardSnapshot = parseDashboard(root.getJSONObject("dashboard"))

    fun detail(kanji: String): KanjiDetailSnapshot {
        val node = root.optJSONObject("kanjiDetails")
            ?.optJSONObject(kanji)
            ?: root.getJSONObject("kanjiDetail")
        return KanjiDetailSnapshot(
            kanji = node.getString("kanji"),
            jitenRank = node.optNumber("jitenRank"),
            keyword = node.getJSONObject("relatedVocabulary").optJSONArray("collectionExamples")
                ?.optString(0)
                ?.takeIf { it.isNotBlank() }
                ?: node.getString("kanji"),
            meanings = node.getJSONObject("meanings").stringList("en"),
            onReadings = node.getJSONObject("readings").stringList("on"),
            kunReadings = node.getJSONObject("readings").stringList("kun"),
            components = node.getJSONObject("structure").stringList("kanjiVgElements"),
            componentHint = node.getJSONObject("structure").getString("componentHint"),
            strokeCount = node.getJSONObject("writing").getInt("strokeCount"),
            browserSearch = node.getString("browserSearch"),
            collectionExamples = node.getJSONObject("collection").stringList("collectionExpressions"),
            suspendedExamples = node.getJSONObject("collection").stringList("suspendedExpressions"),
            activeRecurringExamples = node.getJSONObject("collection").stringList("activeRecurringExpressions"),
            matureExamples = node.getJSONObject("collection").stringList("matureSupportingExpressions"),
        )
    }

    fun sourceSnapshot(settings: SettingsSnapshot): AnkiDroidCollectionSnapshot {
        val node = root.getJSONObject("sourceSnapshot")
        val allowedModels = settings.noteModels.toSet()
        val notes = node.getJSONArray("notes")
            .mapObjects(::parseSourceNote)
            .filter { allowedModels.isEmpty() || it.modelName in allowedModels }
        val allowedNoteIds = notes.map(AnkiDroidNoteSnapshot::noteId).toSet()
        val cards = node.getJSONArray("cards")
            .mapObjects(::parseSourceCard)
            .filter { it.noteId in allowedNoteIds }
        return AnkiDroidCollectionSnapshot(
            notes = notes,
            cards = cards,
        )
    }

    fun baselineOverview(): StudyOverviewSnapshot =
        parseOverview(root.getJSONObject("studyBaseline").getJSONObject("overviewAfterRefresh"))

    fun baselineRefresh(): SeedRefreshSnapshot =
        parseRefresh(root.getJSONObject("studyBaseline").getJSONObject("refreshSeeds"))

    fun happyPathNewSession(): StudySessionSnapshot =
        parseSession(root.getJSONObject("studyScenarios").getJSONObject("happyPath").getJSONObject("sessionNew"))

    fun happyPathMixedSession(): StudySessionSnapshot =
        parseSession(root.getJSONObject("studyScenarios").getJSONObject("happyPath").getJSONObject("sessionMixed"))

    fun happyPathReviewSession(): StudySessionSnapshot =
        parseSession(root.getJSONObject("studyScenarios").getJSONObject("happyPath").getJSONObject("sessionReview"))

    fun happyPathFirstReview(): StudyReviewSnapshot =
        parseReview(
            root.getJSONObject("studyScenarios")
                .getJSONObject("happyPath")
                .getJSONObject("firstReview")
                .getJSONObject("response"),
        )

    fun happyPathSecondReview(): StudyReviewSnapshot =
        parseReview(
            root.getJSONObject("studyScenarios")
                .getJSONObject("happyPath")
                .getJSONObject("secondReview")
                .getJSONObject("response"),
        )

    fun happyPathThirdReview(): StudyReviewSnapshot =
        parseReview(
            root.getJSONObject("studyScenarios")
                .getJSONObject("happyPath")
                .getJSONObject("thirdReview")
                .getJSONObject("response"),
        )

    fun enforcementInvalidReviewError(): String =
        root.getJSONObject("studyScenarios")
            .getJSONObject("handwritingEnforcement")
            .getJSONObject("invalidReview")
            .getString("error")

    fun enforcementRetryReview(): StudyReviewSnapshot =
        parseReview(
            root.getJSONObject("studyScenarios")
                .getJSONObject("handwritingEnforcement")
                .getJSONObject("retryReview")
                .getJSONObject("response"),
        )

    private fun parseSettings(node: JSONObject): SettingsSnapshot =
        SettingsSnapshot(
            ankiConnectUrl = node.getString("ankiConnectUrl"),
            noteModels = node.stringList("noteModels"),
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

    private fun parseLatestSync(node: JSONObject): LatestSyncSnapshot =
        LatestSyncSnapshot(
            source = node.getString("source"),
            status = node.getString("status"),
            startedAt = node.getString("startedAt"),
            finishedAt = node.optNullableString("finishedAt"),
            noteCount = node.getInt("noteCount"),
            cardCount = node.getInt("cardCount"),
            errorMessage = node.optNullableString("errorMessage"),
        )

    private fun parseDashboard(node: JSONObject): DashboardSnapshot =
        DashboardSnapshot(
            summary = parseDashboardSummary(node.getJSONObject("summary")),
            rows = node.getJSONArray("rows").mapObjects(::parseDashboardRow),
            problemSeedCount = node.getInt("problemSeedCount"),
            warnings = node.stringList("warnings"),
            sourceCounts = parseSourceCounts(node.getJSONObject("sourceCounts")),
        )

    private fun parseDashboardSummary(node: JSONObject): DashboardSummarySnapshot =
        DashboardSummarySnapshot(
            totalKanjiCount = node.getInt("totalKanjiCount"),
            unknownKanjiCount = node.getInt("unknownKanjiCount"),
            averageKanjiRank = node.optNumber("averageKanjiRank"),
            matureSupportThreshold = node.getInt("matureSupportThreshold"),
            rankedKanjiCount = node.getInt("rankedKanjiCount"),
        )

    private fun parseDashboardRow(node: JSONObject): DashboardRowSnapshot =
        DashboardRowSnapshot(
            kanji = node.getString("kanji"),
            jitenRank = node.optNumber("jitenRank"),
            collectionExpressionCount = node.getInt("collectionExpressionCount"),
            suspendedExpressionCount = node.getInt("suspendedExpressionCount"),
            activeRecurringExpressionCount = node.getInt("activeRecurringExpressionCount"),
            matureSupportCount = node.getInt("matureSupportCount"),
            supportDeficit = node.getInt("supportDeficit"),
            isUnknown = node.getBoolean("isUnknown"),
            browserSearch = node.getString("browserSearch"),
        )

    private fun parseOverview(node: JSONObject): StudyOverviewSnapshot =
        StudyOverviewSnapshot(
            dueCount = node.getInt("dueCount"),
            newCount = node.getInt("newCount"),
            activeQueueCount = node.getInt("activeQueueCount"),
            inactiveCount = node.getInt("inactiveCount"),
            currentProblemSeedCount = node.getInt("currentProblemSeedCount"),
            nextDueAt = node.optNullableString("nextDueAt"),
            queuePreview = node.getJSONArray("queuePreview").mapObjects(::parseQueuePreview),
        )

    private fun parseQueuePreview(node: JSONObject): StudyQueuePreviewSnapshot =
        StudyQueuePreviewSnapshot(
            kanji = node.getString("kanji"),
            itemStatus = node.getString("itemStatus"),
            dueAt = node.optNullableString("dueAt"),
            dueNow = node.getBoolean("dueNow"),
            guideLevelLabel = node.getString("guideLevelLabel"),
            supportDeficit = node.getInt("supportDeficit"),
            suspendedExpressionCount = node.getInt("suspendedExpressionCount"),
        )

    private fun parseRefresh(node: JSONObject): SeedRefreshSnapshot =
        SeedRefreshSnapshot(
            introducedCount = node.getInt("introducedCount"),
            updatedCount = node.getInt("updatedCount"),
            reactivatedCount = node.getInt("reactivatedCount"),
            inactivatedCount = node.getInt("inactivatedCount"),
            currentProblemSeedCount = node.getInt("currentProblemSeedCount"),
        )

    private fun parseSession(node: JSONObject): StudySessionSnapshot {
        val session = node.getJSONObject("session")
        val production = session.getJSONObject("prompts").getJSONObject("production")
        val recognition = session.getJSONObject("prompts").getJSONObject("recognition")
        val answer = session.getJSONObject("answer")
        val policy = session.getJSONObject("handwritingPolicy")
        return StudySessionSnapshot(
            kanji = session.getString("kanji"),
            reviewToken = session.getString("reviewToken"),
            promptType = session.getString("promptType"),
            promptLabel = session.getString("promptLabel"),
            taskKind = session.getString("taskKind"),
            schedulerPhase = session.getString("schedulerPhase"),
            requiresWriting = session.getBoolean("requiresWriting"),
            itemStatus = session.getString("itemStatus"),
            reviewCount = session.getInt("reviewCount"),
            guideLevelLabel = session.getString("guideLevelLabel"),
            handwritingPolicy = HandwritingPolicySnapshot(
                required = policy.getBoolean("required"),
                guideMode = policy.getString("guideMode"),
                guideLevelLabel = policy.getString("guideLevelLabel"),
                guidedEvaluationAvailable = policy.getBoolean("guidedEvaluationAvailable"),
                manualOnlyWithoutGeometry = policy.getBoolean("manualOnlyWithoutGeometry"),
                allowedRatingsOnFailure = policy.stringList("allowedRatingsOnFailure"),
            ),
            keyword = answer.getString("keyword"),
            productionContext = production.stringList("context"),
            recognitionContext = recognition.stringList("context"),
            supportWords = answer.stringList("supportWords"),
            painExample = answer.optNullableString("painExample"),
            bridgeExample = answer.optNullableString("bridgeExample"),
            matureExample = answer.optNullableString("matureExample"),
        )
    }

    private fun parseReview(node: JSONObject): StudyReviewSnapshot {
        val item = node.getJSONObject("item")
        val overview = node.getJSONObject("overview")
        return StudyReviewSnapshot(
            binaryOutcome = node.getString("binaryOutcome"),
            reviewedAt = node.getString("reviewedAt"),
            itemStatus = item.getString("itemStatus"),
            reviewCount = item.getInt("reviewCount"),
            guideLevelLabel = item.getString("guideLevelLabel"),
            dueAt = item.optNullableString("dueAt"),
            overviewDueCount = overview.getInt("dueCount"),
        )
    }

    private fun parseSourceNote(node: JSONObject): AnkiDroidNoteSnapshot =
        AnkiDroidNoteSnapshot(
            noteId = node.getLong("note_id"),
            modelName = node.getString("model_name"),
            expression = node.getString("expression"),
            reading = node.getString("reading"),
            meaning = node.getString("meaning"),
            fields = node.getJSONObject("fields").stringMap(),
            tags = node.getJSONArray("tags").stringList(),
        )

    private fun parseSourceCard(node: JSONObject): AnkiDroidCardSnapshot =
        AnkiDroidCardSnapshot(
            cardId = node.getLong("card_id"),
            noteId = node.getLong("note_id"),
            deckName = node.getString("deck_name"),
            intervalDays = node.getInt("interval_days"),
            modifiedTs = node.optLong("modified_ts"),
            dueValue = node.getInt("due"),
            cardOrd = node.getInt("card_ord"),
            queueValue = node.getInt("queue"),
            cardType = node.getInt("card_type"),
            reps = node.getInt("reps"),
            lapses = node.getInt("lapses"),
            isSuspended = node.getBoolean("is_suspended"),
            isActive = node.getBoolean("is_active"),
            isMature = node.getBoolean("is_mature"),
        )

    private fun parseSourceCounts(node: JSONObject): SourceCounts =
        SourceCounts(
            noteCount = node.getInt("noteCount"),
            cardCount = node.getInt("cardCount"),
        )
}

private fun JSONObject.stringList(key: String): List<String> {
    val array = getJSONArray(key)
    return List(array.length()) { index -> array.getString(index) }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else getString(key)

private fun JSONObject.optNumber(key: String): Double? =
    if (isNull(key) || !has(key)) null else getDouble(key)

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }

private fun JSONArray.stringList(): List<String> =
    List(length()) { index -> getString(index) }

private fun JSONObject.stringMap(): Map<String, String> =
    keys().asSequence().associateWith { key -> getString(key) }
