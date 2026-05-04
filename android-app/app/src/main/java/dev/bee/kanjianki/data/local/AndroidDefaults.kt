package dev.bee.kanjianki.data.local

import dev.bee.kanjianki.domain.DashboardSnapshot
import dev.bee.kanjianki.domain.DashboardSummarySnapshot
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.domain.SettingsSnapshot
import dev.bee.kanjianki.domain.SourceCounts
import dev.bee.kanjianki.domain.StudyOverviewSnapshot

internal object AndroidDefaults {
    private const val DEFAULT_ANKICONNECT_URL = "http://127.0.0.1:8765"
    private val DEFAULT_MODEL_NAMES = listOf("Kiku")
    private const val DEFAULT_EXPRESSION_FIELD = "Expression"
    private const val DEFAULT_READING_FIELD = "Reading"
    private const val DEFAULT_MEANING_FIELD = "Meaning"
    private const val DEFAULT_MATURE_DAYS = 21
    private const val DEFAULT_KANJI_SUPPORT_THRESHOLD = 3
    private const val DEFAULT_JITEN_CACHE_TTL_HOURS = 24
    private const val DEFAULT_JITEN_REQUEST_TIMEOUT_SECONDS = 20
    private const val DEFAULT_POLLING_ENABLED = false
    private const val DEFAULT_POLLING_INTERVAL_SECONDS = 15 * 60

    fun settings(): SettingsSnapshot =
        SettingsSnapshot(
            ankiConnectUrl = DEFAULT_ANKICONNECT_URL,
            noteModels = DEFAULT_MODEL_NAMES,
            expressionField = DEFAULT_EXPRESSION_FIELD,
            readingField = DEFAULT_READING_FIELD,
            meaningField = DEFAULT_MEANING_FIELD,
            matureDays = DEFAULT_MATURE_DAYS,
            kanjiSupportThreshold = DEFAULT_KANJI_SUPPORT_THRESHOLD,
            jitenCacheTtlHours = DEFAULT_JITEN_CACHE_TTL_HOURS,
            jitenRequestTimeoutSeconds = DEFAULT_JITEN_REQUEST_TIMEOUT_SECONDS,
            pollingEnabled = DEFAULT_POLLING_ENABLED,
            pollingIntervalSeconds = DEFAULT_POLLING_INTERVAL_SECONDS,
        )

    fun emptyDashboard(
        settings: SettingsSnapshot,
        warnings: List<String> = emptyList(),
        sourceCounts: SourceCounts = SourceCounts(0, 0),
    ): DashboardSnapshot =
        DashboardSnapshot(
            summary = DashboardSummarySnapshot(
                totalKanjiCount = 0,
                unknownKanjiCount = 0,
                averageKanjiRank = null,
                matureSupportThreshold = settings.kanjiSupportThreshold,
                rankedKanjiCount = 0,
            ),
            rows = emptyList(),
            problemSeedCount = 0,
            warnings = warnings.distinct(),
            sourceCounts = sourceCounts,
        )

    fun emptyOverview(): StudyOverviewSnapshot =
        StudyOverviewSnapshot(
            dueCount = 0,
            newCount = 0,
            activeQueueCount = 0,
            inactiveCount = 0,
            currentProblemSeedCount = 0,
            nextDueAt = null,
            queuePreview = emptyList(),
        )

    fun emptySeedRefresh(): SeedRefreshSnapshot =
        SeedRefreshSnapshot(
            introducedCount = 0,
            updatedCount = 0,
            reactivatedCount = 0,
            inactivatedCount = 0,
            currentProblemSeedCount = 0,
        )
}
