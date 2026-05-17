package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.settings.SettingEntity
import dev.bee.kanjianki.data.settings.SettingsDao
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.repository.StudySchedulerSettings
import dev.bee.kanjianki.domain.repository.StudySchedulerSettingsRepository
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadMode
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadPolicy
import dev.bee.kanjianki.domain.scheduler.StudyLadderSettings

class RoomStudySchedulerSettingsRepository(
    private val settings: SettingsDao,
) : StudySchedulerSettingsRepository {
    override suspend fun get(): StudySchedulerSettings {
        val values = settings.getAll(ALL_KEYS).associate { it.key to it.value }
        return StudySchedulerSettings(
            activeQueueCap = values.int(KEY_ACTIVE_QUEUE_CAP, StudySchedulerSettings.DEFAULT_ACTIVE_QUEUE_CAP)
                .coerceAtLeast(0),
            newPerDay = values.int(KEY_NEW_PER_DAY, StudySchedulerSettings.DEFAULT_NEW_PER_DAY).coerceAtLeast(0),
            ladderSettings = values.ladderSettings(),
            workloadPolicy = AdaptiveWorkloadPolicy.fromSettings(
                values.int(KEY_ADAPTIVE_WORKLOAD_PERCENT, AdaptiveStudyPlanner.DEFAULT_WORKLOAD_PERCENT),
                values.string(KEY_ADAPTIVE_WORKLOAD_MODE, AdaptiveStudyPlanner.DEFAULT_WORKLOAD_MODE),
                values.int(KEY_ADAPTIVE_MAX_ITEMS, AdaptiveStudyPlanner.DEFAULT_MAX_ITEMS),
            ),
        )
    }

    override suspend fun save(
        settings: StudySchedulerSettings,
        updatedAtMillis: Long,
    ) {
        val enabledRungs = settings.ladderSettings.orderedRungs
            .filter { it in settings.ladderSettings.enabledRungs }
        this.settings.upsertAll(
            listOf(
                KEY_ACTIVE_QUEUE_CAP to settings.activeQueueCap.toString(),
                KEY_NEW_PER_DAY to settings.newPerDay.toString(),
                KEY_STUDY_LADDER_ORDER to settings.ladderSettings.orderedRungs.joinWireNames(),
                KEY_STUDY_LADDER_ENABLED to enabledRungs.joinWireNames(),
                KEY_LADDER_PROMOTION_INTERVAL_DAYS to settings.ladderSettings.promotionIntervalDays.toString(),
                KEY_LADDER_DEMOTION_FAIL_STREAK to settings.ladderSettings.demotionFailStreak.toString(),
                KEY_ADAPTIVE_WORKLOAD_PERCENT to settings.workloadPolicy.workloadPercent.toString(),
                KEY_ADAPTIVE_WORKLOAD_MODE to settings.workloadPolicy.mode.settingValue,
                KEY_ADAPTIVE_MAX_ITEMS to settings.workloadPolicy.maxItems.toString(),
            ).map { (key, value) ->
                SettingEntity(
                    key = key,
                    value = value,
                    updatedAt = updatedAtMillis,
                )
            },
        )
    }

    private fun Map<String, String>.ladderSettings(): StudyLadderSettings =
        runCatching {
            StudyLadderSettings(
                orderedRungs = completeOrder(rungs(KEY_STUDY_LADDER_ORDER)),
                enabledRungs = enabledRungs(KEY_STUDY_LADDER_ENABLED),
                promotionIntervalDays = int(KEY_LADDER_PROMOTION_INTERVAL_DAYS, StudyLadderSettings.defaults.promotionIntervalDays),
                demotionFailStreak = int(KEY_LADDER_DEMOTION_FAIL_STREAK, StudyLadderSettings.defaults.demotionFailStreak),
            )
        }.getOrDefault(StudyLadderSettings.defaults)

    private fun Map<String, String>.enabledRungs(key: String): Set<StudyRung> {
        val parsed = rungs(key)
        return if (parsed.isEmpty()) {
            StudyRung.defaultEnabled
        } else {
            parsed.toSet()
        }
    }

    private fun Map<String, String>.rungs(key: String): List<StudyRung> =
        string(key, "")
            .trim()
            .split(Regex("[,\\s]+"))
            .filter(String::isNotBlank)
            .mapNotNull { wireName ->
                runCatching { StudyRung.fromWireName(wireName) }.getOrNull()
            }
            .distinct()

    private fun completeOrder(requested: List<StudyRung>): List<StudyRung> =
        if (requested.isEmpty()) {
            StudyRung.defaultOrder
        } else {
            requested + StudyRung.defaultOrder.filterNot { it in requested }
        }

    private fun List<StudyRung>.joinWireNames(): String =
        joinToString(",") { it.wireName }

    private fun Map<String, String>.string(
        key: String,
        default: String,
    ): String = get(key) ?: default

    private fun Map<String, String>.int(
        key: String,
        default: Int,
    ): Int = get(key)?.toIntOrNull() ?: default

    private companion object {
        private const val KEY_ACTIVE_QUEUE_CAP = "study.queue.active_cap"
        private const val KEY_NEW_PER_DAY = "study.queue.new_per_day"
        private const val KEY_STUDY_LADDER_ORDER = "study_ladder_order"
        private const val KEY_STUDY_LADDER_ENABLED = "study_ladder_enabled"
        private const val KEY_LADDER_PROMOTION_INTERVAL_DAYS = "ladder_promotion_interval_days"
        private const val KEY_LADDER_DEMOTION_FAIL_STREAK = "ladder_demotion_fail_streak"
        private const val KEY_ADAPTIVE_WORKLOAD_PERCENT = AdaptiveStudyPlanner.SETTING_KEY
        private const val KEY_ADAPTIVE_WORKLOAD_MODE = AdaptiveStudyPlanner.MODE_SETTING_KEY
        private const val KEY_ADAPTIVE_MAX_ITEMS = AdaptiveStudyPlanner.MAX_ITEMS_SETTING_KEY
        private val ALL_KEYS = listOf(
            KEY_ACTIVE_QUEUE_CAP,
            KEY_NEW_PER_DAY,
            KEY_STUDY_LADDER_ORDER,
            KEY_STUDY_LADDER_ENABLED,
            KEY_LADDER_PROMOTION_INTERVAL_DAYS,
            KEY_LADDER_DEMOTION_FAIL_STREAK,
            KEY_ADAPTIVE_WORKLOAD_PERCENT,
            KEY_ADAPTIVE_WORKLOAD_MODE,
            KEY_ADAPTIVE_MAX_ITEMS,
        )
    }
}
