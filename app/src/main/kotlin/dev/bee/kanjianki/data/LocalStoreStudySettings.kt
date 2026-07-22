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

    fun reminderAntiSpamSettings(): LocalStoreBase.ReminderAntiSpamSettings {
        return LocalStoreBase.ReminderAntiSpamSettings(
            getIntSetting(KEY_REMINDER_QUIET_START_MINUTE, ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE),
            getIntSetting(KEY_REMINDER_QUIET_END_MINUTE, ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE),
            getIntSetting(KEY_REMINDER_MAX_PER_DAY, ReminderAntiSpamPolicy.DEFAULT_MAX_PER_DAY),
        ).normalized()
    }

    fun saveReminderAntiSpamSettings(settings: LocalStoreBase.ReminderAntiSpamSettings) {
        val normalized = settings.normalized()
        inTransaction {
            putIntSetting(KEY_REMINDER_QUIET_START_MINUTE, normalized.quietStartMinuteOfDay)
            putIntSetting(KEY_REMINDER_QUIET_END_MINUTE, normalized.quietEndMinuteOfDay)
            putIntSetting(KEY_REMINDER_MAX_PER_DAY, normalized.maxRemindersPerDay)
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
        val count = saturatingIncrement(reviewReminderNotificationsToday(nowMillis))
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

    /**
     * Anti-spam throttle + decision state, read as one snapshot. Per-day counters
     * and dismissed families reset implicitly at local-day rollover (same pattern
     * as the review-reminder counter): a stored day-start that no longer matches
     * today reads as a fresh day.
     */
    fun reminderThrottleState(nowMillis: Long): LocalStoreBase.ReminderThrottleState {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        val storedDayStart = getLongSetting(KEY_REMINDER_STATE_DAY_START, 0L)
        val sameDay = storedDayStart == todayStart
        return LocalStoreBase.ReminderThrottleState(
            getLongSetting(KEY_REMINDER_LAST_POSTED_AT, 0L),
            getStringSetting(KEY_REMINDER_LAST_POSTED_SIGNATURE, ""),
            if (sameDay) getIntSetting(KEY_REMINDER_DUE_SHOWN, 0).coerceAtLeast(0) else 0,
            if (sameDay) getIntSetting(KEY_REMINDER_STREAK_SHOWN, 0).coerceAtLeast(0) else 0,
            if (sameDay) getIntSetting(KEY_REMINDER_SYNC_SHOWN, 0).coerceAtLeast(0) else 0,
            if (sameDay) getStringSetting(KEY_REMINDER_DISMISSED_FAMILIES, "") else "",
            if (sameDay) getIntSetting(KEY_REMINDER_DAILY_OVERRIDE_USED, 0) == 1 else false,
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
        inTransaction {
            resetReminderStateIfNewDay(todayStart)
            putLongSetting(KEY_REMINDER_LAST_POSTED_AT, nowMillis)
            putStringSetting(KEY_REMINDER_LAST_POSTED_SIGNATURE, signature ?: "")
            incrementFamilyCounter(family)
            if (dailyTimeOverride) {
                putIntSetting(KEY_REMINDER_DAILY_OVERRIDE_USED, 1)
            }
        }
    }

    /**
     * Updates throttle state for a user-requested snooze replay without consuming
     * another per-day reminder slot. The original post already owns that budget.
     */
    fun recordReminderReposted(nowMillis: Long, signature: String?) {
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        inTransaction {
            resetReminderStateIfNewDay(todayStart)
            putLongSetting(KEY_REMINDER_LAST_POSTED_AT, nowMillis)
            putStringSetting(KEY_REMINDER_LAST_POSTED_SIGNATURE, signature ?: "")
        }
    }

    /** Records a swipe-dismissal of [family] for the rest of the local day. */
    fun recordReminderDismissed(nowMillis: Long, family: String?) {
        val normalized = family?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return
        }
        val todayStart = LocalDayPolicy.localDayStart(nowMillis)
        inTransaction {
            resetReminderStateIfNewDay(todayStart)
            val current = getStringSetting(KEY_REMINDER_DISMISSED_FAMILIES, "")
            val families = current.split(',').filter { it.isNotBlank() }.toMutableSet()
            if (families.add(normalized)) {
                putStringSetting(KEY_REMINDER_DISMISSED_FAMILIES, families.joinToString(","))
            }
        }
    }

    private fun resetReminderStateIfNewDay(todayStart: Long) {
        if (getLongSetting(KEY_REMINDER_STATE_DAY_START, 0L) == todayStart) {
            return
        }
        putLongSetting(KEY_REMINDER_STATE_DAY_START, todayStart)
        putIntSetting(KEY_REMINDER_DUE_SHOWN, 0)
        putIntSetting(KEY_REMINDER_STREAK_SHOWN, 0)
        putIntSetting(KEY_REMINDER_SYNC_SHOWN, 0)
        putStringSetting(KEY_REMINDER_DISMISSED_FAMILIES, "")
        putIntSetting(KEY_REMINDER_DAILY_OVERRIDE_USED, 0)
    }

    private fun incrementFamilyCounter(family: String?) {
        val key = when (family?.trim()?.uppercase()) {
            "STREAK" -> KEY_REMINDER_STREAK_SHOWN
            "SYNC" -> KEY_REMINDER_SYNC_SHOWN
            else -> KEY_REMINDER_DUE_SHOWN
        }
        putIntSetting(key, saturatingIncrement(getIntSetting(key, 0)))
    }

    private fun saturatingIncrement(value: Int): Int = value.coerceIn(0, Int.MAX_VALUE - 1) + 1

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

    fun debugLogEnabled(): Boolean {
        return getIntSetting(LocalStoreBase.KEY_DEBUG_LOG_ENABLED, 0) == 1
    }

    fun saveDebugLogEnabled(enabled: Boolean) {
        putIntSetting(LocalStoreBase.KEY_DEBUG_LOG_ENABLED, if (enabled) 1 else 0)
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

    fun recordUpdateCheckFailed(atMillis: Long) {
        putLongSetting(LocalStoreBase.KEY_UPDATE_CHECK_FAILED_AT, atMillis)
    }

    fun clearUpdateCheckFailed() {
        putLongSetting(LocalStoreBase.KEY_UPDATE_CHECK_FAILED_AT, 0L)
    }

    fun updateCheckFailedAt(): Long {
        return getLongSetting(LocalStoreBase.KEY_UPDATE_CHECK_FAILED_AT, 0L)
    }

    fun installPermissionPromptShown(): Boolean {
        return getIntSetting(LocalStoreBase.KEY_UPDATE_PERMISSION_PROMPT_SHOWN, 0) == 1
    }

    fun installPermissionPromptLastVersion(): String {
        return getStringSetting(LocalStoreBase.KEY_UPDATE_PERMISSION_PROMPT_LAST_VERSION, "")
    }

    fun recordInstallPermissionPrompted(version: String?) {
        inTransaction {
            putIntSetting(LocalStoreBase.KEY_UPDATE_PERMISSION_PROMPT_SHOWN, 1)
            putStringSetting(
                LocalStoreBase.KEY_UPDATE_PERMISSION_PROMPT_LAST_VERSION,
                AutoUpdateStatusPolicy.text(version)
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

    private companion object {
        const val TAG = "LocalStoreStudySettings"
        const val KEY_STUDY_LADDER_ORDER = "study_ladder_order"
        const val KEY_STUDY_LADDER_ENABLED = "study_ladder_enabled"
        const val KEY_ADAPTIVE_REPAIR_ORDER = "adaptive_repair_order"
        const val KEY_ADAPTIVE_REPAIR_ENABLED = "adaptive_repair_enabled"
        const val KEY_REVIEW_REMINDER_DAY_START = "review_reminder_day_start"
        const val KEY_REVIEW_REMINDER_COUNT = "review_reminder_count"
        const val KEY_REMINDER_LAST_POSTED_AT = "reminder_last_posted_at"
        const val KEY_REMINDER_LAST_POSTED_SIGNATURE = "reminder_last_posted_signature"
        const val KEY_REMINDER_STATE_DAY_START = "reminder_state_day_start"
        const val KEY_REMINDER_DUE_SHOWN = "reminder_due_shown_today"
        const val KEY_REMINDER_STREAK_SHOWN = "reminder_streak_shown_today"
        const val KEY_REMINDER_SYNC_SHOWN = "reminder_sync_shown_today"
        const val KEY_REMINDER_DISMISSED_FAMILIES = "reminder_dismissed_families_today"
        const val KEY_REMINDER_DAILY_OVERRIDE_USED = "reminder_daily_override_used_today"
        const val KEY_REMINDER_QUIET_START_MINUTE = "reminder_quiet_start_minute"
        const val KEY_REMINDER_QUIET_END_MINUTE = "reminder_quiet_end_minute"
        const val KEY_REMINDER_MAX_PER_DAY = "reminder_max_per_day"
        val STATS_SETTING_KEYS = setOf(
            SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY,
            SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
            SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
            SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
        )
    }
}
