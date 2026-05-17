package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.importing.NoteTypeMapping
import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadPolicy
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeedSettings
import dev.bee.kanjianki.domain.sync.SyncAdaptivePlanContext
import dev.bee.kanjianki.domain.sync.SyncStudyQueueSeedContext

object LegacySyncMappers {
    @JvmStatic
    fun toImportSettings(settings: RecordsSyncModels.Settings?): ImportSettings {
        val safeSettings = settings ?: RecordsSyncModels.Settings.kikuDefaults()
        return ImportSettings(
            noteMapping = NoteTypeMapping(
                noteTypeName = safeSettings.modelName,
                templateName = safeSettings.templateName,
                expressionField = safeSettings.expressionField,
                readingField = safeSettings.readingField,
                meaningField = safeSettings.meaningField,
                sentenceField = safeSettings.sentenceField,
                frequencyField = safeSettings.frequencyField,
                frequencySortField = safeSettings.frequencySortField,
            ),
            matureDays = safeSettings.matureDays,
            matureSupportThreshold = safeSettings.matureSupportThreshold,
            importActiveCards = safeSettings.importActiveCards,
            importSuspendedCards = safeSettings.importSuspendedCards,
            importTaggedCards = safeSettings.importTaggedCardsEnabled(),
            importTags = safeSettings.importTags,
            importWeakCards = safeSettings.importWeakCards,
            importWeakFsrsDifficultyThreshold = safeSettings.importWeakFsrsDifficultyThreshold,
            importWeakLapsesThreshold = safeSettings.importWeakLapsesThreshold,
            importMinMatchingCardsPerKanji = safeSettings.importMinMatchingCardsPerKanji,
            importBrowserQueryCards = safeSettings.importBrowserQueryCards,
            importBrowserQuery = safeSettings.normalizedBrowserQuery(),
            suspendedRankMin = safeSettings.suspendedRankMin,
            suspendedRankMax = safeSettings.suspendedRankMax,
            newCardSortMode = NewCardSortMode.fromWireName(safeSettings.newCardSortMode),
        )
    }

    @JvmStatic
    fun toQueueSeedSettings(settings: RecordsSyncModels.Settings?): StudyQueueSeedSettings {
        val safeSettings = settings ?: RecordsSyncModels.Settings.kikuDefaults()
        return StudyQueueSeedSettings(
            activeQueueCap = safeSettings.activeQueueCap,
            newPerDay = safeSettings.newPerDay,
            matureSupportThreshold = safeSettings.matureSupportThreshold,
            newCardSortMode = NewCardSortMode.fromWireName(safeSettings.newCardSortMode),
        )
    }

    @JvmStatic
    fun toQueueSeedContext(
        settings: RecordsSyncModels.Settings?,
        ladderSettings: RecordsBase.StudyLadderSettings?,
        locallySuspendedKanji: Set<String>,
        startOfDayMillis: Long,
        recentStats: RecordsSchedulerModels.ReviewStats?,
        currentStreakDays: Int,
        studiedToday: Set<String>,
        workloadPercent: Int,
        workloadMode: String?,
        maxItems: Int,
    ): SyncStudyQueueSeedContext {
        val safeSettings = settings ?: RecordsSyncModels.Settings.kikuDefaults()
        return SyncStudyQueueSeedContext(
            settings = toQueueSeedSettings(safeSettings),
            startOfDayMillis = startOfDayMillis,
            ladderSettings = LegacyStudyMappers.toDomain(safeSettings, ladderSettings),
            locallySuspendedKanji = locallySuspendedKanji,
            adaptiveContext = SyncAdaptivePlanContext(
                recentStats = toAdaptiveReviewStats(recentStats),
                currentStreakDays = currentStreakDays,
                studiedToday = studiedToday,
                workloadPolicy = AdaptiveWorkloadPolicy.fromSettings(
                    workloadPercent,
                    workloadMode ?: AdaptiveStudyPlanner.DEFAULT_WORKLOAD_MODE,
                    maxItems,
                ),
            ),
        )
    }

    private fun toAdaptiveReviewStats(
        stats: RecordsSchedulerModels.ReviewStats?,
    ): AdaptiveReviewStats {
        val safeStats = stats ?: RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0)
        return AdaptiveReviewStats(
            total = safeStats.total,
            again = safeStats.again,
            hard = safeStats.hard,
            good = safeStats.good,
            easy = safeStats.easy,
            writingRequired = safeStats.writingRequired,
            writingFailed = safeStats.writingFailed,
        )
    }
}
