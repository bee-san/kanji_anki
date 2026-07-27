package dev.bee.kanjianki.data

import android.util.Log
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.FsrsPersonalization
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.ReminderAntiSpamPolicy
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SyncSettings
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.updatecore.AutoUpdateStatusPolicy

internal class LocalStoreStudySettings(private val store: LocalStoreStudy) {
    private val newCardSortSettings = NewCardSortSettingsRepository(store.settingsStore())
    private val themeChoiceSettings = KaniThemeChoiceRepository(store.settingsStore())

    fun getIntSetting(key: String, fallback: Int): Int = store.settingsStore().getInt(key, fallback)

    fun getLongSetting(key: String, fallback: Long): Long = store.settingsStore().getLong(key, fallback)

    fun getStringSetting(key: String, fallback: String): String = store.settingsStore().getString(key, fallback) ?: fallback

    fun getDoubleSetting(key: String, fallback: Double): Double = store.settingsStore().getDouble(key, fallback)

    fun putIntSetting(key: String, value: Int) {
        if (key !in STATS_SETTING_KEYS) {
            store.settingsStore().putInt(key, value)
            return
        }
        inTransaction {
            store.settingsStore().putInt(key, value)
            markStatsDirty()
        }
    }

    fun putLongSetting(key: String, value: Long) {
        store.settingsStore().putLong(key, value)
    }

    fun putStringSetting(key: String, value: String?) {
        store.settingsStore().putString(key, value)
    }

    fun putDoubleSetting(key: String, value: Double) {
        store.settingsStore().putDouble(key, value)
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
            getStringSetting(KEY_STUDY_LADDER_ENABLED, ""),
            store.settingsStore().getString(KEY_ADAPTIVE_REPAIR_ORDER, null),
            store.settingsStore().getString(KEY_ADAPTIVE_REPAIR_ENABLED, null),
        )
    }

    fun saveStudyLadderSettings(settings: RecordsBase.StudyLadderSettings?) {
        val normalized = settings ?: RecordsBase.StudyLadderSettings.defaults()
        inTransaction {
            putStringSetting(KEY_STUDY_LADDER_ORDER, normalized.orderText())
            putStringSetting(KEY_STUDY_LADDER_ENABLED, normalized.enabledText())
            putStringSetting(KEY_ADAPTIVE_REPAIR_ORDER, normalized.repairOrderText())
            putStringSetting(KEY_ADAPTIVE_REPAIR_ENABLED, normalized.repairEnabledText())
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
        val settings = store.deviceSettingsStore().snapshot()
        return LocalStoreBase.ReminderSettings(
            settings.value(DeviceSettingKeys.reminderEnabled, false),
            settings.value(DeviceSettingKeys.reminderHour, TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR),
            settings.value(DeviceSettingKeys.reminderMinute, TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE),
        ).normalized()
    }

    fun saveReminderSettings(settings: LocalStoreBase.ReminderSettings) {
        val normalized = settings.normalized()
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.reminderEnabled, normalized.enabled)
            put(DeviceSettingKeys.reminderHour, normalized.hour)
            put(DeviceSettingKeys.reminderMinute, normalized.minute)
        }
    }

    fun reminderAntiSpamSettings(): LocalStoreBase.ReminderAntiSpamSettings {
        val settings = store.deviceSettingsStore().snapshot()
        return LocalStoreBase.ReminderAntiSpamSettings(
            settings.value(
                DeviceSettingKeys.reminderQuietStartMinute,
                ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE,
            ),
            settings.value(
                DeviceSettingKeys.reminderQuietEndMinute,
                ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE,
            ),
            settings.value(DeviceSettingKeys.reminderMaxPerDay, ReminderAntiSpamPolicy.DEFAULT_MAX_PER_DAY),
        ).normalized()
    }

    fun saveReminderAntiSpamSettings(settings: LocalStoreBase.ReminderAntiSpamSettings) {
        val normalized = settings.normalized()
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.reminderQuietStartMinute, normalized.quietStartMinuteOfDay)
            put(DeviceSettingKeys.reminderQuietEndMinute, normalized.quietEndMinuteOfDay)
            put(DeviceSettingKeys.reminderMaxPerDay, normalized.maxRemindersPerDay)
        }
    }

    fun reviewReminderNotificationsToday(nowMillis: Long): Int {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        val settings = store.deviceSettingsStore().snapshot()
        val storedDayStart = settings.value(DeviceSettingKeys.reviewReminderDayStart, 0L)
        if (storedDayStart != todayStart) {
            return 0
        }
        return settings.value(DeviceSettingKeys.reviewReminderCount, 0).coerceAtLeast(0)
    }

    fun recordReviewReminderNotificationShown(nowMillis: Long) {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        store.deviceSettingsStore().edit {
            val previousCount = if (value(DeviceSettingKeys.reviewReminderDayStart, 0L) == todayStart) {
                value(DeviceSettingKeys.reviewReminderCount, 0)
            } else {
                0
            }
            put(DeviceSettingKeys.reviewReminderDayStart, todayStart)
            put(DeviceSettingKeys.reviewReminderCount, saturatingIncrement(previousCount))
        }
    }

    fun clearReviewReminderNotifications(nowMillis: Long) {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.reviewReminderDayStart, todayStart)
            put(DeviceSettingKeys.reviewReminderCount, 0)
        }
    }

    /**
     * Anti-spam throttle + decision state, read as one snapshot. Per-day counters
     * and dismissed families reset implicitly at local-day rollover (same pattern
     * as the review-reminder counter): a stored day-start that no longer matches
     * today reads as a fresh day.
     */
    fun reminderThrottleState(nowMillis: Long): LocalStoreBase.ReminderThrottleState {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        val settings = store.deviceSettingsStore().snapshot()
        val storedDayStart = settings.value(DeviceSettingKeys.reminderStateDayStart, 0L)
        val sameDay = storedDayStart == todayStart
        return LocalStoreBase.ReminderThrottleState(
            settings.value(DeviceSettingKeys.reminderLastPostedAt, 0L),
            settings.value(DeviceSettingKeys.reminderLastPostedSignature, ""),
            if (sameDay) {
                settings.value(DeviceSettingKeys.reminderDueShownToday, 0).coerceAtLeast(0)
            } else {
                0
            },
            if (sameDay) {
                settings.value(DeviceSettingKeys.reminderStreakShownToday, 0).coerceAtLeast(0)
            } else {
                0
            },
            if (sameDay) {
                settings.value(DeviceSettingKeys.reminderSyncShownToday, 0).coerceAtLeast(0)
            } else {
                0
            },
            if (sameDay) {
                settings.value(DeviceSettingKeys.reminderDismissedFamiliesToday, "")
            } else {
                ""
            },
            sameDay && settings.value(DeviceSettingKeys.reminderDailyOverrideUsedToday, false),
        )
    }

    /**
     * Record that a reminder of [family] was posted at [nowMillis] with the given
     * due-set [signature], incrementing that family's per-day counter. [family] is
     * the wire name from [dev.bee.kanjianki.core.ReminderFamily] ("DUE"/"STREAK"/
     * "SYNC"), or empty/null for a review-batch post (counted as DUE).
     */
    fun recordReminderPosted(nowMillis: Long, family: String?, signature: String?, dailyTimeOverride: Boolean) {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        val counterKey = reminderCounterKey(family)
        store.deviceSettingsStore().edit {
            val sameDay = value(DeviceSettingKeys.reminderStateDayStart, 0L) == todayStart
            val previousCount = if (sameDay) value(counterKey, 0) else 0
            if (!sameDay) {
                resetReminderState(todayStart)
            }
            put(DeviceSettingKeys.reminderLastPostedAt, nowMillis)
            put(DeviceSettingKeys.reminderLastPostedSignature, signature ?: "")
            put(counterKey, saturatingIncrement(previousCount))
            if (dailyTimeOverride) {
                put(DeviceSettingKeys.reminderDailyOverrideUsedToday, true)
            }
        }
    }

    /**
     * Updates throttle state for a user-requested snooze replay without consuming
     * another per-day reminder slot. The original post already owns that budget.
     */
    fun recordReminderReposted(nowMillis: Long, signature: String?) {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        store.deviceSettingsStore().edit {
            val sameDay = value(DeviceSettingKeys.reminderStateDayStart, 0L) == todayStart
            if (!sameDay) {
                resetReminderState(todayStart)
            }
            put(DeviceSettingKeys.reminderLastPostedAt, nowMillis)
            put(DeviceSettingKeys.reminderLastPostedSignature, signature ?: "")
        }
    }

    /** Records a swipe-dismissal of [family] for the rest of the local day. */
    fun recordReminderDismissed(nowMillis: Long, family: String?) {
        val normalized = family?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return
        }
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        store.deviceSettingsStore().edit {
            val sameDay = value(DeviceSettingKeys.reminderStateDayStart, 0L) == todayStart
            val current = if (sameDay) {
                value(DeviceSettingKeys.reminderDismissedFamiliesToday, "")
            } else {
                ""
            }
            val families = current.split(',').filter { it.isNotBlank() }.toMutableSet()
            if (!families.add(normalized)) {
                return@edit
            }
            if (!sameDay) {
                resetReminderState(todayStart)
            }
            put(DeviceSettingKeys.reminderDismissedFamiliesToday, families.joinToString(","))
        }
    }

    private fun DeviceSettingsEditor.resetReminderState(todayStart: Long) {
        put(DeviceSettingKeys.reminderStateDayStart, todayStart)
        put(DeviceSettingKeys.reminderDueShownToday, 0)
        put(DeviceSettingKeys.reminderStreakShownToday, 0)
        put(DeviceSettingKeys.reminderSyncShownToday, 0)
        put(DeviceSettingKeys.reminderDismissedFamiliesToday, "")
        put(DeviceSettingKeys.reminderDailyOverrideUsedToday, false)
    }

    private fun reminderCounterKey(family: String?): DeviceSettingKey<Int> =
        when (family?.trim()?.uppercase()) {
            "STREAK" -> DeviceSettingKeys.reminderStreakShownToday
            "SYNC" -> DeviceSettingKeys.reminderSyncShownToday
            else -> DeviceSettingKeys.reminderDueShownToday
        }

    private fun saturatingIncrement(value: Int): Int = value.coerceIn(0, Int.MAX_VALUE - 1) + 1

    fun autoSyncSettings(): LocalStoreBase.AutoSyncSettings {
        return autoSyncSettings(store.deviceSettingsStore().snapshot())
    }

    private fun autoSyncSettings(settings: DeviceSettingsReader): LocalStoreBase.AutoSyncSettings {
        return LocalStoreBase.AutoSyncSettings(
            settings.value(DeviceSettingKeys.autoSyncConfigured, false),
            settings.value(DeviceSettingKeys.autoSyncEnabled, false),
            settings.value(DeviceSettingKeys.autoSyncHour, TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_HOUR),
            settings.value(DeviceSettingKeys.autoSyncMinute, TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_MINUTE),
            settings.value(DeviceSettingKeys.autoSyncLastAttemptAt, 0L),
            settings.value(DeviceSettingKeys.autoSyncLastSuccessAt, 0L),
            settings.value(DeviceSettingKeys.autoSyncNextRunAt, 0L),
        ).normalized()
    }

    fun activateAutoSyncAfterFirstSuccess(): Boolean {
        var activated = false
        store.deviceSettingsStore().edit {
            val current = autoSyncSettings(this)
            if (!current.configured) {
                putAutoSyncSettings(
                    LocalStoreBase.AutoSyncSettings(
                        true,
                        true,
                        current.hour,
                        current.minute,
                        current.lastAttemptAt,
                        current.lastSuccessAt,
                        current.nextRunAt,
                    ),
                )
                activated = true
            }
        }
        return activated
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        store.deviceSettingsStore().edit {
            val current = autoSyncSettings(this)
            putAutoSyncSettings(
                LocalStoreBase.AutoSyncSettings(
                    true,
                    enabled,
                    current.hour,
                    current.minute,
                    current.lastAttemptAt,
                    current.lastSuccessAt,
                    current.nextRunAt,
                ),
            )
        }
    }

    fun markAutoSyncScheduled(nextRunAt: Long) {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.autoSyncNextRunAt, nextRunAt)
        }
    }

    fun recordAutoSyncAttempt(attemptedAt: Long, success: Boolean) {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.autoSyncLastAttemptAt, attemptedAt)
            if (success) {
                put(DeviceSettingKeys.autoSyncLastSuccessAt, attemptedAt)
            }
        }
    }

    fun saveAutoSyncSettings(settings: LocalStoreBase.AutoSyncSettings) {
        store.deviceSettingsStore().edit {
            putAutoSyncSettings(settings)
        }
    }

    private fun DeviceSettingsEditor.putAutoSyncSettings(
        settings: LocalStoreBase.AutoSyncSettings,
    ) {
        val normalized = settings.normalized()
        put(DeviceSettingKeys.autoSyncConfigured, normalized.configured)
        put(DeviceSettingKeys.autoSyncEnabled, normalized.enabled)
        put(DeviceSettingKeys.autoSyncHour, normalized.hour)
        put(DeviceSettingKeys.autoSyncMinute, normalized.minute)
        put(DeviceSettingKeys.autoSyncLastAttemptAt, normalized.lastAttemptAt)
        put(DeviceSettingKeys.autoSyncLastSuccessAt, normalized.lastSuccessAt)
        put(DeviceSettingKeys.autoSyncNextRunAt, normalized.nextRunAt)
    }

    fun autoUpdateStatus(): LocalStoreBase.AutoUpdateStatus {
        val settings = store.deviceSettingsStore().snapshot()
        return LocalStoreBase.AutoUpdateStatus(
            settings.value(DeviceSettingKeys.autoUpdateEnabled, true),
            settings.value(DeviceSettingKeys.autoUpdateLastCheckAt, 0L),
            settings.value(
                DeviceSettingKeys.autoUpdateLastResult,
                AutoUpdateStatusPolicy.DEFAULT_LAST_RESULT,
            ),
            settings.value(DeviceSettingKeys.autoUpdateLastVersion, ""),
            settings.value(DeviceSettingKeys.autoUpdatePendingPackage, ""),
            settings.value(DeviceSettingKeys.autoUpdatePendingMessage, ""),
        )
    }

    fun saveAutoUpdateEnabled(enabled: Boolean) {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.autoUpdateEnabled, enabled)
        }
    }

    fun debugLogEnabled(): Boolean {
        return store.deviceSettingsStore().read(DeviceSettingKeys.debugLogEnabled) ?: false
    }

    fun saveDebugLogEnabled(enabled: Boolean) {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.debugLogEnabled, enabled)
        }
    }

    fun recordAutoUpdateResult(
        checkedAt: Long,
        result: String?,
        version: String?,
        pendingApkName: String?,
        pendingMessage: String?,
    ) {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.autoUpdateLastCheckAt, checkedAt)
            put(DeviceSettingKeys.autoUpdateLastResult, AutoUpdateStatusPolicy.text(result))
            put(DeviceSettingKeys.autoUpdateLastVersion, AutoUpdateStatusPolicy.text(version))
            put(DeviceSettingKeys.autoUpdatePendingPackage, AutoUpdateStatusPolicy.text(pendingApkName))
            put(DeviceSettingKeys.autoUpdatePendingMessage, AutoUpdateStatusPolicy.text(pendingMessage))
        }
    }

    fun recordUpdateCheckFailed(atMillis: Long) {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.updateCheckFailedAt, atMillis)
        }
    }

    fun clearUpdateCheckFailed() {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.updateCheckFailedAt, 0L)
        }
    }

    fun updateCheckFailedAt(): Long {
        return store.deviceSettingsStore().read(DeviceSettingKeys.updateCheckFailedAt) ?: 0L
    }

    fun installPermissionPromptShown(): Boolean {
        return store.deviceSettingsStore().read(DeviceSettingKeys.updatePermissionPromptShown) ?: false
    }

    fun installPermissionPromptLastVersion(): String {
        return store.deviceSettingsStore().read(DeviceSettingKeys.updatePermissionPromptLastVersion) ?: ""
    }

    fun recordInstallPermissionPrompted(version: String?) {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.updatePermissionPromptShown, true)
            put(
                DeviceSettingKeys.updatePermissionPromptLastVersion,
                AutoUpdateStatusPolicy.text(version),
            )
        }
    }

    fun clearPendingAutoUpdate(result: String?) {
        store.deviceSettingsStore().edit {
            put(DeviceSettingKeys.autoUpdateLastResult, AutoUpdateStatusPolicy.text(result))
            put(DeviceSettingKeys.autoUpdatePendingPackage, "")
            put(DeviceSettingKeys.autoUpdatePendingMessage, "")
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

    /** Returns null for unset or malformed data so reviews always remain usable. */
    fun schedulerFsrsWeights(): DoubleArray? {
        val encoded = getStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")
        return try {
            FsrsPersonalization.decodeWeights(encoded)
        } catch (_: RuntimeException) {
            warnInvalidFsrsWeights()
            null
        }
    }

    fun saveSchedulerFsrsWeights(weights: DoubleArray?) {
        val encoded = if (weights == null) "" else FsrsPersonalization.encodeWeights(weights)
        inTransaction {
            putStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, encoded)
            markStatsDirty()
        }
    }

    /** Atomically publishes a fit summary with its corresponding live-weight decision. */
    fun commitFsrsFitOutcome(
        weightsToAdopt: DoubleArray?,
        summaryJson: String,
        disabledSummaryJson: String?,
        preserveExistingWeights: Boolean,
    ): Boolean {
        val encoded = weightsToAdopt?.let { FsrsPersonalization.encodeWeights(it) }
        var adopted = false
        inTransaction {
            val enabled = getIntSetting(FsrsPersonalization.ENABLED_SETTING_KEY, 0) == 1
            if (encoded != null && enabled) {
                putStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, encoded)
                markStatsDirty()
                adopted = true
            } else if (encoded == null && !preserveExistingWeights) {
                putStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")
                markStatsDirty()
            }
            putStringSetting(
                FsrsPersonalization.FIT_SUMMARY_SETTING_KEY,
                if (encoded != null && !enabled) disabledSummaryJson ?: summaryJson else summaryJson,
            )
        }
        return adopted
    }

    fun fsrsPersonalizationEnabled(): Boolean =
        getIntSetting(FsrsPersonalization.ENABLED_SETTING_KEY, 0) == 1

    fun saveFsrsPersonalizationEnabled(enabled: Boolean) {
        inTransaction {
            putIntSetting(FsrsPersonalization.ENABLED_SETTING_KEY, if (enabled) 1 else 0)
            if (!enabled) {
                putStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")
                markStatsDirty()
            }
        }
    }

    fun fsrsFitSummaryJson(): String =
        getStringSetting(FsrsPersonalization.FIT_SUMMARY_SETTING_KEY, "")

    fun saveFsrsFitSummaryJson(summaryJson: String?) {
        putStringSetting(FsrsPersonalization.FIT_SUMMARY_SETTING_KEY, summaryJson ?: "")
    }

    fun resetFsrsPersonalization() {
        inTransaction {
            putStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")
            putStringSetting(FsrsPersonalization.FIT_SUMMARY_SETTING_KEY, "")
            markStatsDirty()
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
        // Individual put calls invalidate eagerly, but another LocalStore can only observe these
        // values after commit. Publish one final generation after the transaction is visible so
        // no reader can retain a pre-commit bulk snapshot under the latest generation.
        store.settingsStore().invalidate()
    }

    private fun markStatsDirty() {
        StatsCacheStore(store as LocalStore).markDirty(store.writableDatabase)
    }

    private fun warnInvalidFsrsWeights() {
        try {
            // Never include the stored value: a single sanitized line is enough
            // to diagnose fallback without leaking or flooding diagnostics.
            Log.w(TAG, "Invalid scheduler_fsrs_weights; using FSRS defaults.")
        } catch (_: RuntimeException) {
            // android.util.Log is unavailable in plain local JVM tests.
        }
    }

    private fun <T : Any> DeviceSettingsReader.value(
        key: DeviceSettingKey<T>,
        fallback: T,
    ): T = read(key) ?: fallback

    private companion object {
        const val TAG = "LocalStoreStudySettings"
        const val KEY_STUDY_LADDER_ORDER = "study_ladder_order"
        const val KEY_STUDY_LADDER_ENABLED = "study_ladder_enabled"
        const val KEY_ADAPTIVE_REPAIR_ORDER = "adaptive_repair_order"
        const val KEY_ADAPTIVE_REPAIR_ENABLED = "adaptive_repair_enabled"
        val STATS_SETTING_KEYS = setOf(
            SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY,
            SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
            SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
            SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
        )
    }
}
