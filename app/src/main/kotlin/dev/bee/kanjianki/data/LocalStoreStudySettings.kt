package dev.bee.kanjianki.data

import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.sync.SyncSettings
import dev.bee.kanjianki.theme.KaniThemeChoice
import dev.bee.kanjianki.theme.KaniThemeChoiceRepository
import dev.bee.kanjianki.updatecore.AutoUpdateStatusPolicy

internal class LocalStoreStudySettings(private val store: LocalStoreStudy) {
    private val newCardSortSettings = NewCardSortSettingsRepository(store.settingsRepository())
    private val themeChoiceSettings = KaniThemeChoiceRepository(store.settingsRepository())

    fun getIntSetting(key: String, fallback: Int): Int = store.settingsRepository().getInt(key, fallback)

    fun getLongSetting(key: String, fallback: Long): Long = store.settingsRepository().getLong(key, fallback)

    fun getStringSetting(key: String, fallback: String): String = store.settingsRepository().getString(key, fallback) ?: fallback

    fun getDoubleSetting(key: String, fallback: Double): Double = store.settingsRepository().getDouble(key, fallback)

    fun putIntSetting(key: String, value: Int) {
        store.settingsRepository().putInt(key, value)
        markStatsDirtyIfNeeded(key)
    }

    fun putLongSetting(key: String, value: Long) {
        store.settingsRepository().putLong(key, value)
    }

    fun putStringSetting(key: String, value: String?) {
        store.settingsRepository().putString(key, value)
    }

    fun putDoubleSetting(key: String, value: Double) {
        store.settingsRepository().putDouble(key, value)
    }

    fun adaptiveLoadWorkPercent(): Int {
        return AdaptiveLoadPlanner.snapWorkloadPercent(
            getIntSetting(
                AdaptiveLoadPlanner.SETTING_KEY,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT
            )
        )
    }

    fun saveAdaptiveLoadWorkPercent(percent: Int) {
        putIntSetting(AdaptiveLoadPlanner.SETTING_KEY, AdaptiveLoadPlanner.snapWorkloadPercent(percent))
    }

    fun studyAheadMinutes(): Int {
        return SettingsInputRules.normalizeStudyAheadMinutes(
            getIntSetting(
                LocalStoreBase.SETTING_STUDY_AHEAD_MINUTES,
                SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES
            )
        )
    }

    fun saveStudyAheadMinutes(minutes: Int) {
        putIntSetting(
            LocalStoreBase.SETTING_STUDY_AHEAD_MINUTES,
            SettingsInputRules.normalizeStudyAheadMinutes(minutes)
        )
    }

    fun studyLadderSettings(): RecordsBase.StudyLadderSettings {
        return RecordsBase.StudyLadderSettings.fromStored(
            getStringSetting(KEY_STUDY_LADDER_ORDER, ""),
            getStringSetting(KEY_STUDY_LADDER_ENABLED, "")
        )
    }

    fun saveStudyLadderSettings(settings: RecordsBase.StudyLadderSettings?) {
        val normalized = settings ?: RecordsBase.StudyLadderSettings.defaults()
        inTransaction {
            putStringSetting(KEY_STUDY_LADDER_ORDER, normalized.orderText())
            putStringSetting(KEY_STUDY_LADDER_ENABLED, normalized.enabledText())
            markStatsDirty()
        }
    }

    fun adaptiveLoadMaxItems(): Int {
        return AdaptiveLoadPlanner.normalizeMaxItems(
            getIntSetting(
                "adaptive_load_max_items",
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS
            )
        )
    }

    fun saveAdaptiveLoadMaxItems(maxItems: Int) {
        putIntSetting("adaptive_load_max_items", AdaptiveLoadPlanner.normalizeMaxItems(maxItems))
    }

    fun adaptiveLoadMode(): String {
        return AdaptiveLoadPlanner.normalizeWorkloadMode(
            getStringSetting(
                AdaptiveLoadPlanner.MODE_SETTING_KEY,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE
            )
        )
    }

    fun saveAdaptiveLoadMode(mode: String?) {
        putStringSetting(AdaptiveLoadPlanner.MODE_SETTING_KEY, AdaptiveLoadPlanner.normalizeWorkloadMode(mode))
    }

    fun saveNewCardSortMode(mode: String?) = newCardSortSettings.saveMode(mode)

    fun appThemeChoice(): KaniThemeChoice = themeChoiceSettings.currentChoice()

    fun saveAppThemeChoice(choice: KaniThemeChoice?): KaniThemeChoice = themeChoiceSettings.saveChoice(choice)

    fun reminderSettings(): LocalStoreBase.ReminderSettings {
        return LocalStoreBase.ReminderSettings(
            getIntSetting("reminder_enabled", 0) == 1,
            getIntSetting("reminder_hour", TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR),
            getIntSetting("reminder_minute", TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE)
        ).normalized()
    }

    fun saveReminderSettings(settings: LocalStoreBase.ReminderSettings) {
        val normalized = settings.normalized()
        inTransaction {
            putIntSetting("reminder_enabled", if (normalized.enabled) 1 else 0)
            putIntSetting("reminder_hour", normalized.hour)
            putIntSetting("reminder_minute", normalized.minute)
        }
    }

    fun reviewReminderNotificationsToday(nowMillis: Long): Int {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        val storedDayStart = getLongSetting(KEY_REVIEW_REMINDER_DAY_START, 0L)
        if (storedDayStart != todayStart) {
            return 0
        }
        return getIntSetting(KEY_REVIEW_REMINDER_COUNT, 0).coerceAtLeast(0)
    }

    fun recordReviewReminderNotificationShown(nowMillis: Long) {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        val count = reviewReminderNotificationsToday(nowMillis) + 1
        inTransaction {
            putLongSetting(KEY_REVIEW_REMINDER_DAY_START, todayStart)
            putIntSetting(KEY_REVIEW_REMINDER_COUNT, count)
        }
    }

    fun clearReviewReminderNotifications(nowMillis: Long) {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        inTransaction {
            putLongSetting(KEY_REVIEW_REMINDER_DAY_START, todayStart)
            putIntSetting(KEY_REVIEW_REMINDER_COUNT, 0)
        }
    }

    fun autoSyncSettings(): LocalStoreBase.AutoSyncSettings {
        return LocalStoreBase.AutoSyncSettings(
            getIntSetting("auto_sync_configured", 0) == 1,
            getIntSetting("auto_sync_enabled", 0) == 1,
            getIntSetting("auto_sync_hour", TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_HOUR),
            getIntSetting("auto_sync_minute", TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_MINUTE),
            getLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_ATTEMPT_AT, 0L),
            getLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_SUCCESS_AT, 0L),
            getLongSetting(LocalStoreBase.KEY_AUTO_SYNC_NEXT_RUN_AT, 0L)
        ).normalized()
    }

    fun activateAutoSyncAfterFirstSuccess(): Boolean {
        val current = autoSyncSettings()
        if (current.configured) {
            return false
        }
        saveAutoSyncSettings(
            LocalStoreBase.AutoSyncSettings(
                true,
                true,
                current.hour,
                current.minute,
                current.lastAttemptAt,
                current.lastSuccessAt,
                current.nextRunAt
            )
        )
        return true
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        val current = autoSyncSettings()
        saveAutoSyncSettings(
            LocalStoreBase.AutoSyncSettings(
                true,
                enabled,
                current.hour,
                current.minute,
                current.lastAttemptAt,
                current.lastSuccessAt,
                current.nextRunAt
            )
        )
    }

    fun markAutoSyncScheduled(nextRunAt: Long) {
        putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_NEXT_RUN_AT, nextRunAt)
    }

    fun recordAutoSyncAttempt(attemptedAt: Long, success: Boolean) {
        inTransaction {
            putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_ATTEMPT_AT, attemptedAt)
            if (success) {
                putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_SUCCESS_AT, attemptedAt)
            }
        }
    }

    fun saveAutoSyncSettings(settings: LocalStoreBase.AutoSyncSettings) {
        val normalized = settings.normalized()
        inTransaction {
            putIntSetting("auto_sync_configured", if (normalized.configured) 1 else 0)
            putIntSetting("auto_sync_enabled", if (normalized.enabled) 1 else 0)
            putIntSetting("auto_sync_hour", normalized.hour)
            putIntSetting("auto_sync_minute", normalized.minute)
            putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_ATTEMPT_AT, normalized.lastAttemptAt)
            putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_SUCCESS_AT, normalized.lastSuccessAt)
            putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_NEXT_RUN_AT, normalized.nextRunAt)
        }
    }

    fun autoUpdateStatus(): LocalStoreBase.AutoUpdateStatus {
        return LocalStoreBase.AutoUpdateStatus(
            getIntSetting(LocalStoreBase.KEY_AUTO_UPDATE_ENABLED, 1) == 1,
            getLongSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_CHECK_AT, 0L),
            getStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_RESULT, AutoUpdateStatusPolicy.DEFAULT_LAST_RESULT),
            getStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_VERSION, ""),
            getStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_APK, ""),
            getStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_MESSAGE, "")
        )
    }

    fun saveAutoUpdateEnabled(enabled: Boolean) {
        putIntSetting(LocalStoreBase.KEY_AUTO_UPDATE_ENABLED, if (enabled) 1 else 0)
    }

    fun recordAutoUpdateResult(
        checkedAt: Long,
        result: String?,
        version: String?,
        pendingApkName: String?,
        pendingMessage: String?,
    ) {
        inTransaction {
            putLongSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_CHECK_AT, checkedAt)
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_RESULT, AutoUpdateStatusPolicy.text(result))
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_VERSION, AutoUpdateStatusPolicy.text(version))
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_APK, AutoUpdateStatusPolicy.text(pendingApkName))
            putStringSetting(
                LocalStoreBase.KEY_AUTO_UPDATE_PENDING_MESSAGE,
                AutoUpdateStatusPolicy.text(pendingMessage)
            )
        }
    }

    fun clearPendingAutoUpdate(result: String?) {
        inTransaction {
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_RESULT, AutoUpdateStatusPolicy.text(result))
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_APK, "")
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_MESSAGE, "")
        }
    }

    fun schedulerParameters(): RecordsSchedulerModels.SchedulerParameters {
        val defaults = RecordsSchedulerModels.SchedulerParameters.defaults()
        return RecordsSchedulerModels.SchedulerParameters(
            getDoubleSetting("scheduler_target_retention", defaults.targetRetention)
        ).withFrequencyRetention(
            getIntSetting(
                "scheduler_frequency_retention_enabled",
                if (defaults.frequencyRetentionEnabled) 1 else 0
            ) == 1,
            getStringSetting("scheduler_frequency_retention_ranges", defaults.frequencyRetentionRanges)
        )
    }

    fun saveSchedulerParameters(parameters: RecordsSchedulerModels.SchedulerParameters) {
        inTransaction {
            putDoubleSetting("scheduler_target_retention", parameters.targetRetention)
            putIntSetting(
                "scheduler_frequency_retention_enabled",
                if (parameters.frequencyRetentionEnabled) 1 else 0
            )
            putStringSetting("scheduler_frequency_retention_ranges", parameters.frequencyRetentionRanges)
        }
    }

    fun learningStepSettings(): RecordsSchedulerModels.LearningStepSettings {
        val defaults = RecordsSchedulerModels.LearningStepSettings.defaults()
        val newSteps = RecordsSchedulerModels.LearningStepSettings.parseSteps(
            getStringSetting("new_learning_steps_minutes", defaults.newStepsText()),
            defaults.newStepsMinutes
        )
        val reviewSteps = RecordsSchedulerModels.LearningStepSettings.parseSteps(
            getStringSetting("review_relearning_steps_minutes", defaults.reviewStepsText()),
            defaults.reviewStepsMinutes,
            true
        )
        return RecordsSchedulerModels.LearningStepSettings(newSteps, reviewSteps)
    }

    fun saveLearningStepSettings(settings: RecordsSchedulerModels.LearningStepSettings?) {
        val normalized = settings ?: RecordsSchedulerModels.LearningStepSettings.defaults()
        inTransaction {
            putStringSetting("new_learning_steps_minutes", normalized.newStepsText())
            putStringSetting("review_relearning_steps_minutes", normalized.reviewStepsText())
        }
    }

    private fun inTransaction(body: () -> Unit) {
        store.writableDatabase.transaction {
            body()
        }
    }

    private fun markStatsDirtyIfNeeded(key: String) {
        if (key in STATS_SETTING_KEYS) {
            markStatsDirty()
        }
    }

    private fun markStatsDirty() {
        StatsCacheStore(store as LocalStore).markDirty(store.writableDatabase)
    }

    private companion object {
        const val KEY_STUDY_LADDER_ORDER = "study_ladder_order"
        const val KEY_STUDY_LADDER_ENABLED = "study_ladder_enabled"
        const val KEY_REVIEW_REMINDER_DAY_START = "review_reminder_day_start"
        const val KEY_REVIEW_REMINDER_COUNT = "review_reminder_count"
        val STATS_SETTING_KEYS = setOf(
            SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
            SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
            SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
        )
    }
}
