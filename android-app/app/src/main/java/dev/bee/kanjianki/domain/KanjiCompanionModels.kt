package dev.bee.kanjianki.domain

data class SourceCounts(
    val noteCount: Int,
    val cardCount: Int,
)

data class LatestSyncSnapshot(
    val source: String,
    val status: String,
    val startedAt: String,
    val finishedAt: String?,
    val noteCount: Int,
    val cardCount: Int,
    val errorMessage: String?,
)

data class HealthSnapshot(
    val version: String,
    val databasePath: String,
    val webAppPath: String,
    val sourceCounts: SourceCounts,
    val latestSync: LatestSyncSnapshot? = null,
)

data class SettingsSnapshot(
    val ankiConnectUrl: String,
    val noteModels: List<String>,
    val expressionField: String,
    val readingField: String,
    val meaningField: String,
    val matureDays: Int,
    val kanjiSupportThreshold: Int,
    val jitenCacheTtlHours: Int,
    val jitenRequestTimeoutSeconds: Int,
    val pollingEnabled: Boolean,
    val pollingIntervalSeconds: Int,
)

data class DashboardSummarySnapshot(
    val totalKanjiCount: Int,
    val unknownKanjiCount: Int,
    val averageKanjiRank: Double?,
    val matureSupportThreshold: Int,
    val rankedKanjiCount: Int,
)

data class DashboardRowSnapshot(
    val kanji: String,
    val jitenRank: Double?,
    val collectionExpressionCount: Int,
    val suspendedExpressionCount: Int,
    val activeRecurringExpressionCount: Int,
    val matureSupportCount: Int,
    val supportDeficit: Int,
    val isUnknown: Boolean,
    val browserSearch: String,
)

data class DashboardSnapshot(
    val summary: DashboardSummarySnapshot,
    val rows: List<DashboardRowSnapshot>,
    val problemSeedCount: Int,
    val warnings: List<String>,
    val sourceCounts: SourceCounts,
)

data class KanjiDetailSnapshot(
    val kanji: String,
    val jitenRank: Double?,
    val keyword: String,
    val meanings: List<String>,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val components: List<String>,
    val componentHint: String,
    val strokeCount: Int,
    val browserSearch: String,
    val collectionExamples: List<String>,
    val suspendedExamples: List<String>,
    val activeRecurringExamples: List<String>,
    val matureExamples: List<String>,
)

data class StudyQueuePreviewSnapshot(
    val kanji: String,
    val itemStatus: String,
    val dueAt: String?,
    val dueNow: Boolean,
    val guideLevelLabel: String,
    val supportDeficit: Int,
    val suspendedExpressionCount: Int,
)

data class StudyOverviewSnapshot(
    val dueCount: Int,
    val newCount: Int,
    val activeQueueCount: Int,
    val inactiveCount: Int,
    val currentProblemSeedCount: Int,
    val nextDueAt: String?,
    val queuePreview: List<StudyQueuePreviewSnapshot>,
)

data class SeedRefreshSnapshot(
    val introducedCount: Int,
    val updatedCount: Int,
    val reactivatedCount: Int,
    val inactivatedCount: Int,
    val currentProblemSeedCount: Int,
)

data class SyncSnapshot(
    val sourceCounts: SourceCounts,
    val dashboard: DashboardSnapshot,
)

data class HandwritingPolicySnapshot(
    val required: Boolean,
    val guideMode: String,
    val guideLevelLabel: String,
    val guidedEvaluationAvailable: Boolean,
    val manualOnlyWithoutGeometry: Boolean,
    val allowedRatingsOnFailure: List<String>,
)

data class StudySessionSnapshot(
    val kanji: String,
    val reviewToken: String,
    val promptType: String,
    val promptLabel: String,
    val taskKind: String,
    val schedulerPhase: String,
    val requiresWriting: Boolean,
    val itemStatus: String,
    val reviewCount: Int,
    val guideLevelLabel: String,
    val handwritingPolicy: HandwritingPolicySnapshot,
    val keyword: String,
    val productionContext: List<String>,
    val recognitionContext: List<String>,
    val supportWords: List<String>,
    val painExample: String?,
    val bridgeExample: String?,
    val matureExample: String?,
)

data class HandwritingResult(
    val attempted: Boolean,
    val passed: Boolean,
    val score: Double,
    val evaluationMode: String,
    val selfAssessment: String? = null,
)

data class StudyReviewRequest(
    val kanji: String,
    val reviewToken: String,
    val promptType: String,
    val rating: String,
    val hintsUsed: Int,
    val handwritingResult: HandwritingResult,
)

data class StudyReviewSnapshot(
    val binaryOutcome: String,
    val reviewedAt: String,
    val itemStatus: String,
    val reviewCount: Int,
    val guideLevelLabel: String,
    val dueAt: String?,
    val overviewDueCount: Int,
)

enum class SessionMode(val wireValue: String) {
    NEW("new"),
    MIXED("mixed"),
    REVIEW("review"),
}
