package dev.bee.kanjianki.data

import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SyncSettings

internal class SqliteSettingsRepository(
    private val store: LocalStore,
) : SettingsRepository {
    override suspend fun load() = safeStoreCall {
        SettingsSnapshot(
            sync = SyncSettings.fromStore(store),
            tagRepairedCards = SyncSettings.tagRepairedCards(store),
            adaptiveWorkload = AdaptiveWorkloadSnapshot(
                store.adaptiveLoadWorkPercent(),
                store.adaptiveLoadMaxItems(),
                store.adaptiveLoadMode(),
            ),
            studyAheadMinutes = store.studyAheadMinutes(),
            studyLadder = store.studyLadderSettings(),
            schedulerParameters = store.schedulerParameters(),
            schedulerFsrsWeights = store.schedulerFsrsWeights()?.toList(),
            learningSteps = store.learningStepSettings(),
            themeChoice = store.appThemeChoice(),
            reminder = store.reminderSettings().toRepositorySnapshot(),
            reminderAntiSpam = store.reminderAntiSpamSettings().toRepositorySnapshot(),
            autoSync = store.autoSyncSettings().toRepositorySnapshot(),
            autoUpdate = store.autoUpdateStatus().toRepositorySnapshot(),
            debugLogEnabled = store.debugLogEnabled(),
            fsrsPersonalizationEnabled = store.fsrsPersonalizationEnabled(),
            fsrsFitSummaryJson = store.fsrsFitSummaryJson(),
            updateCheckFailedAtMillis = store.updateCheckFailedAt(),
            installPermissionPromptShown = store.installPermissionPromptShown(),
            installPermissionPromptLastVersion = store.installPermissionPromptLastVersion(),
        )
    }

    override suspend fun save(command: SettingsSaveCommand) = safeStoreCall {
        when (command) {
            is SettingsSaveCommand.Sync -> saveSync(command.settings, command.tagRepairedCards)
            is SettingsSaveCommand.AdaptiveWorkload -> saveAtomically {
                store.saveAdaptiveLoadWorkPercent(command.value.workPercent)
                store.saveAdaptiveLoadMaxItems(command.value.maxItems)
                store.saveAdaptiveLoadMode(command.value.mode)
            }
            is SettingsSaveCommand.StudyAhead -> store.saveStudyAheadMinutes(command.minutes)
            is SettingsSaveCommand.StudyLadder -> store.saveStudyLadderSettings(command.value)
            is SettingsSaveCommand.NewCardSort -> {
                store.saveNewCardSortMode(command.mode)
                Unit
            }
            is SettingsSaveCommand.Theme -> {
                store.saveAppThemeChoice(command.choice)
                Unit
            }
            is SettingsSaveCommand.Reminder -> store.saveReminderSettings(command.value.toStoreModel())
            is SettingsSaveCommand.ReminderAntiSpam ->
                store.saveReminderAntiSpamSettings(command.value.toStoreModel())
            is SettingsSaveCommand.ReminderPosted -> store.recordReminderPosted(
                command.postedAtMillis,
                command.family,
                command.signature,
                command.dailyTimeOverride,
            )
            is SettingsSaveCommand.ReminderDismissed ->
                store.recordReminderDismissed(command.dismissedAtMillis, command.family)
            is SettingsSaveCommand.AutoSync -> store.saveAutoSyncSettings(command.value.toStoreModel())
            is SettingsSaveCommand.AutoSyncEnabled -> store.setAutoSyncEnabled(command.enabled)
            is SettingsSaveCommand.AutoSyncScheduled -> store.markAutoSyncScheduled(command.nextRunAtMillis)
            is SettingsSaveCommand.AutoSyncAttempt ->
                store.recordAutoSyncAttempt(command.attemptedAtMillis, command.success)
            is SettingsSaveCommand.AutoUpdateEnabled -> store.saveAutoUpdateEnabled(command.enabled)
            is SettingsSaveCommand.AutoUpdateResult -> store.recordAutoUpdateResult(
                command.checkedAtMillis,
                command.result,
                command.version,
                command.pendingApkName,
                command.pendingMessage,
            )
            is SettingsSaveCommand.ClearPendingAutoUpdate -> store.clearPendingAutoUpdate(command.result)
            is SettingsSaveCommand.UpdateCheckFailed -> store.recordUpdateCheckFailed(command.failedAtMillis)
            SettingsSaveCommand.ClearUpdateCheckFailed -> store.clearUpdateCheckFailed()
            is SettingsSaveCommand.InstallPermissionPrompted ->
                store.recordInstallPermissionPrompted(command.version)
            is SettingsSaveCommand.DebugLogEnabled -> store.saveDebugLogEnabled(command.enabled)
            is SettingsSaveCommand.SchedulerParameters -> store.saveSchedulerParameters(command.value)
            is SettingsSaveCommand.SchedulerFsrsWeights ->
                store.saveSchedulerFsrsWeights(command.weights?.toDoubleArray())
            is SettingsSaveCommand.FsrsPersonalizationEnabled ->
                store.saveFsrsPersonalizationEnabled(command.enabled)
            is SettingsSaveCommand.FsrsFitSummary -> store.saveFsrsFitSummaryJson(command.summaryJson)
            SettingsSaveCommand.ResetFsrsPersonalization -> store.resetFsrsPersonalization()
            is SettingsSaveCommand.LearningSteps -> store.saveLearningStepSettings(command.value)
        }
    }

    override suspend fun commitFsrsFit(command: CommitFsrsFitCommand) = safeStoreCall {
        store.commitFsrsFitOutcome(
            command.weightsToAdopt?.toDoubleArray(),
            command.summaryJson,
            command.disabledSummaryJson,
            command.preserveExistingWeights,
        )
    }

    private fun saveSync(settings: RecordsSyncModels.Settings, tagRepairedCards: Boolean) {
        saveAtomically {
            store.putStringSetting(SyncSettings.NOTE_TYPE_SETTING_KEY, settings.modelName)
            store.putStringSetting(SyncSettings.EXPRESSION_FIELD_SETTING_KEY, settings.expressionField)
            store.putStringSetting(SyncSettings.READING_FIELD_SETTING_KEY, settings.readingField)
            store.putStringSetting(SyncSettings.MEANING_FIELD_SETTING_KEY, settings.meaningField)
            store.putStringSetting(SyncSettings.SENTENCE_FIELD_SETTING_KEY, settings.sentenceField)
            store.putStringSetting(SyncSettings.FREQUENCY_FIELD_SETTING_KEY, settings.frequencyField)
            store.putStringSetting(SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY, settings.frequencySortField)
            store.putIntSetting(SUSPENDED_RANK_MIN_KEY, settings.suspendedRankMin)
            store.putIntSetting(SUSPENDED_RANK_MAX_KEY, settings.suspendedRankMax)
            store.putIntSetting(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY, settings.writingTriggerMissDays)
            store.putIntSetting(
                SyncSettings.RECOGNITION_PROMOTION_PASSES_SETTING_KEY,
                settings.recognitionPromotionPasses,
            )
            store.putIntSetting(
                SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
                settings.realDueReviewsToMove,
            )
            store.putIntSetting(
                SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
                settings.ladderPromotionIntervalDays,
            )
            store.putIntSetting(
                SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
                settings.ladderDemotionFailStreak,
            )
            store.putIntSetting(
                SyncSettings.LADDER_PROMOTION_MIN_PASSES_SETTING_KEY,
                settings.ladderPromotionMinPasses,
            )
            store.putIntSetting(
                SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY,
                settings.importActiveCards.toSettingInt(),
            )
            store.putIntSetting(
                SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY,
                settings.importSuspendedCards.toSettingInt(),
            )
            store.putIntSetting(
                SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY,
                settings.importTaggedCards.toSettingInt(),
            )
            store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, settings.importTagsText())
            store.putIntSetting(
                SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY,
                settings.importWeakCards.toSettingInt(),
            )
            store.putDoubleSetting(
                SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY,
                settings.importWeakFsrsDifficultyThreshold,
            )
            store.putIntSetting(
                SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY,
                settings.importWeakLapsesThreshold,
            )
            store.putIntSetting(
                SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY,
                settings.importMinMatchingCardsPerKanji,
            )
            store.putIntSetting(
                SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY,
                settings.importBrowserQueryCards.toSettingInt(),
            )
            store.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, settings.importBrowserQuery)
            store.putStringSetting(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, settings.newCardSortMode)
            store.putIntSetting(SyncSettings.NEW_PER_DAY_SETTING_KEY, settings.newPerDay)
            store.putIntSetting(SyncSettings.ACTIVE_QUEUE_CAP_SETTING_KEY, settings.activeQueueCap)
            store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, tagRepairedCards.toSettingInt())
        }
    }

    private fun saveAtomically(block: () -> Unit) {
        store.writableDatabase.transaction {
            block()
        }
        store.settingsStore().invalidate()
    }

    private fun Boolean.toSettingInt(): Int = if (this) 1 else 0

    private companion object {
        const val SUSPENDED_RANK_MIN_KEY = "suspended_rank_min"
        const val SUSPENDED_RANK_MAX_KEY = "suspended_rank_max"
    }
}
