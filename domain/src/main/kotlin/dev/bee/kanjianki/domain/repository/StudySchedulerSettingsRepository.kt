package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadPolicy
import dev.bee.kanjianki.domain.scheduler.StudyLadderSettings
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeedSettings

data class StudySchedulerSettings(
    val activeQueueCap: Int = DEFAULT_ACTIVE_QUEUE_CAP,
    val newPerDay: Int = DEFAULT_NEW_PER_DAY,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
    val workloadPolicy: AdaptiveWorkloadPolicy = AdaptiveWorkloadPolicy.fromSettings(
        AdaptiveStudyPlanner.DEFAULT_WORKLOAD_PERCENT,
        AdaptiveStudyPlanner.DEFAULT_WORKLOAD_MODE,
        AdaptiveStudyPlanner.DEFAULT_MAX_ITEMS,
    ),
) {
    init {
        require(activeQueueCap >= 0) { "activeQueueCap must not be negative" }
        require(newPerDay >= 0) { "newPerDay must not be negative" }
    }

    fun queueSeedSettings(importSettings: ImportSettings): StudyQueueSeedSettings =
        StudyQueueSeedSettings(
            activeQueueCap = activeQueueCap,
            newPerDay = newPerDay,
            matureSupportThreshold = importSettings.matureSupportThreshold,
            newCardSortMode = importSettings.newCardSortMode,
        )

    companion object {
        const val DEFAULT_ACTIVE_QUEUE_CAP = 24
        const val DEFAULT_NEW_PER_DAY = 3
    }
}

interface StudySchedulerSettingsRepository {
    suspend fun get(): StudySchedulerSettings

    suspend fun save(
        settings: StudySchedulerSettings,
        updatedAtMillis: Long,
    )
}
