package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SyncSettings
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryAdaptersTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun typedSettingsCommandsRoundTripWithoutRawKeys() = runTest {
        val repository = SqliteSettingsRepository(store)
        val syncSettings = syncSettingsWithCustomImmutableFields()

        assertTrue(
            repository.save(
                SettingsSaveCommand.AdaptiveWorkload(
                    AdaptiveWorkloadSnapshot(workPercent = 65, maxItems = 17, mode = "manual"),
                ),
            ).isOk(),
        )
        assertTrue(
            repository.save(
                SettingsSaveCommand.Sync(
                    syncSettings,
                    tagRepairedCards = true,
                ),
            ).isOk(),
        )

        val snapshot = repository.load().valueOrNull()
        assertEquals(65, snapshot?.adaptiveWorkload?.workPercent)
        assertEquals(17, snapshot?.adaptiveWorkload?.maxItems)
        assertEquals("manual", snapshot?.adaptiveWorkload?.mode)
        assertTrue(snapshot?.tagRepairedCards == true)
        assertEquals("Alternate", snapshot?.sync?.templateName)
        assertEquals(35, snapshot?.sync?.matureDays)
        assertEquals(4, snapshot?.sync?.matureSupportThreshold)
    }

    @Test
    fun featureReadAdaptersReturnStableSnapshotsFromAnEmptyStore() = runTest {
        val home = SqliteHomeRepository(store)
        val study = SqliteStudyRepository(store)
        val stats = SqliteStatsRepository(store)

        val homeSnapshot = home.loadHome(FINISHED_AT).valueOrNull()
        assertTrue(homeSnapshot?.activeRows?.isEmpty() == true)
        assertNull(homeSnapshot?.latestSync)
        assertEquals(0, homeSnapshot?.studyStreak?.currentDays)

        assertTrue(
            home.saveMnemonic(SaveMnemonicCommand("痛", "water memory", FINISHED_AT)).isOk(),
        )
        assertEquals(
            "water memory",
            home.loadKanjiDetail("痛", FINISHED_AT).valueOrNull()?.mnemonic,
        )

        val queue = study.loadQueue(FINISHED_AT).valueOrNull()
        assertTrue(queue?.studyItems?.isEmpty() == true)
        assertTrue(study.loadChoiceData("痛", FINISHED_AT).valueOrNull()?.inventory?.isEmpty() == true)

        val analytics = stats.refresh(FINISHED_AT).valueOrNull()
        assertEquals(0, analytics?.outcomeStats?.weakKanjiImproved?.improvedCount)
        assertEquals(0, analytics?.studyImpactStats?.totalReviews)
    }

    @Test
    fun typedSyncSettingsDoNotPersistNonPositiveMaturityValues() = runTest {
        val repository = SqliteSettingsRepository(store)
        val defaults = RecordsSyncModels.Settings.kikuDefaults()

        assertTrue(
            repository.save(
                SettingsSaveCommand.Sync(
                    syncSettingsWithCustomImmutableFields(
                        matureDays = 0,
                        matureSupportThreshold = -1,
                    ),
                    tagRepairedCards = false,
                ),
            ).isOk(),
        )

        assertEquals(
            defaults.matureDays,
            store.getIntSetting(SyncSettings.MATURE_DAYS_SETTING_KEY, Int.MIN_VALUE),
        )
        assertEquals(
            defaults.matureSupportThreshold,
            store.getIntSetting(SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY, Int.MIN_VALUE),
        )
        val actual = repository.load().valueOrNull()?.sync
        assertEquals(defaults.matureDays, actual?.matureDays)
        assertEquals(defaults.matureSupportThreshold, actual?.matureSupportThreshold)
    }

    @Test
    fun typedSyncSettingsUseDefaultsForMalformedStoredMaturityValues() = runTest {
        store.putStringSetting(SyncSettings.MATURE_DAYS_SETTING_KEY, "not-a-number")
        store.putStringSetting(SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY, "")
        val defaults = RecordsSyncModels.Settings.kikuDefaults()

        val actual = SqliteSettingsRepository(store).load().valueOrNull()?.sync

        assertEquals(defaults.matureDays, actual?.matureDays)
        assertEquals(defaults.matureSupportThreshold, actual?.matureSupportThreshold)
    }

    @Test
    fun studyAdapterOwnsReviewRecoveryAndCompatibilityOperations() = runTest {
        val repository = SqliteStudyRepository(store)
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val request = RecordsSchedulerModels.ReviewRequest(
            "痛",
            "repository-token",
            "good",
            false,
            true,
            false,
            0,
        )

        assertEquals(1, repository.loadItems(listOf("痛")).valueOrNull()?.size)
        assertEquals(1, repository.annotateCapabilities(listOf(before)).valueOrNull()?.size)

        val committed = repository.commitReview(
            ReviewCommitCommand(
                afterReview = before.copyBuilder().totalReviews(2).build(),
                request = request,
                appliedRating = "good",
                reviewedAtMillis = FINISHED_AT,
                beforeReview = before,
            ),
        ).valueOrNull()
        assertEquals(ReviewCommitDisposition.APPLIED, committed?.disposition)

        val tokenQuery = ReviewTokenQuery(
            request.token,
            request.kanji,
            request.taskType,
            request.answerSignature,
        )
        val tokenStatus = repository.reviewTokenStatus(tokenQuery).valueOrNull()
        assertTrue(tokenStatus?.consumed == true)
        assertTrue(tokenStatus?.matchesReview == true)

        val recovery = repository.recoveryStatus(
            StudyRecoveryQuery(
                review = tokenQuery,
                repairId = 999L,
                repairAttemptsBefore = 0,
                repairPassed = false,
            ),
        ).valueOrNull()
        assertTrue(recovery?.token?.consumed == true)
        assertFalse(recovery?.legacyRepairFinished ?: true)

        val applied = committed?.item ?: throw AssertionError("review was not applied")
        assertTrue(
            repository.undoLastReview(
                AppliedReviewSnapshot(request.token, before, applied),
            ).valueOrNull() == true,
        )

        assertTrue(repository.replaceQueue(StudyQueueWriteCommand(emptyList(), emptyList())).isOk())
        assertNull(repository.loadDueSimilarChoice("痛", FINISHED_AT).valueOrNull())
        assertTrue(repository.loadDueLegacyWritingRepairs(FINISHED_AT).valueOrNull()?.isEmpty() == true)
        assertTrue(repository.saveLegacyWritingRepair(writingRepair()).isOk())
        assertFalse(
            repository.finishLegacyWritingRepair(
                FinishLegacyRepairCommand(999L, "repair-token", true, FINISHED_AT),
            ).valueOrNull() ?: true,
        )
        assertFalse(
            repository.skipLegacyWritingRepair(
                SkipLegacyRepairCommand(999L, "repair-token", FINISHED_AT),
            ).valueOrNull() ?: true,
        )
        assertEquals("", repository.loadMnemonic("痛").valueOrNull())
    }

    @Test
    fun homeAdapterOwnsSearchAndLocalSuspensionOperations() = runTest {
        val repository = SqliteHomeRepository(store)

        assertTrue(repository.searchInventory("痛", false).valueOrNull()?.isEmpty() == true)
        assertTrue(
            repository.setLocalSuspension(
                SetLocalSuspensionCommand(listOf("痛", "弱"), true, FINISHED_AT),
            ).isOk(),
        )
        assertEquals("痛", repository.loadKanjiDetail("痛", FINISHED_AT).valueOrNull()?.kanji)
    }

    @Test
    fun typedSettingsAdapterDispatchesEveryCommandFamily() = runTest {
        val repository = SqliteSettingsRepository(store)
        val initial = repository.load().valueOrNull()
            ?: throw AssertionError("settings snapshot was not loaded")
        val commands = listOf(
            SettingsSaveCommand.StudyAhead(15),
            SettingsSaveCommand.StudyLadder(initial.studyLadder),
            SettingsSaveCommand.NewCardSort(initial.sync.newCardSortMode),
            SettingsSaveCommand.Theme(initial.themeChoice),
            SettingsSaveCommand.Reminder(ReminderSettingsSnapshot(true, 8, 30)),
            SettingsSaveCommand.ReminderAntiSpam(initial.reminderAntiSpam),
            SettingsSaveCommand.ReminderPosted(FINISHED_AT, "due", "signature", true),
            SettingsSaveCommand.ReminderDismissed(FINISHED_AT, "due"),
            SettingsSaveCommand.AutoSync(initial.autoSync),
            SettingsSaveCommand.AutoSyncEnabled(true),
            SettingsSaveCommand.AutoSyncScheduled(FINISHED_AT),
            SettingsSaveCommand.AutoSyncAttempt(FINISHED_AT, true),
            SettingsSaveCommand.AutoUpdateEnabled(true),
            SettingsSaveCommand.AutoUpdateResult(
                FINISHED_AT,
                "downloaded",
                "v9.9.9",
                "update.apk",
                "ready",
            ),
            SettingsSaveCommand.ClearPendingAutoUpdate("installed"),
            SettingsSaveCommand.UpdateCheckFailed(FINISHED_AT),
            SettingsSaveCommand.ClearUpdateCheckFailed,
            SettingsSaveCommand.InstallPermissionPrompted("v9.9.9"),
            SettingsSaveCommand.DebugLogEnabled(true),
            SettingsSaveCommand.SchedulerParameters(initial.schedulerParameters),
            SettingsSaveCommand.SchedulerFsrsWeights(null),
            SettingsSaveCommand.FsrsPersonalizationEnabled(true),
            SettingsSaveCommand.FsrsFitSummary("{\"status\":\"tested\"}"),
            SettingsSaveCommand.ResetFsrsPersonalization,
            SettingsSaveCommand.LearningSteps(initial.learningSteps),
        )

        commands.forEach { command ->
            assertTrue("failed to save $command", repository.save(command).isOk())
        }
        assertTrue(
            repository.commitFsrsFit(
                CommitFsrsFitCommand(
                    weightsToAdopt = null,
                    summaryJson = "{\"status\":\"not_adopted\"}",
                    disabledSummaryJson = null,
                    preserveExistingWeights = true,
                ),
            ).isOk(),
        )

        val actual = repository.load().valueOrNull()
        assertTrue(actual?.debugLogEnabled == true)
        assertEquals(15, actual?.studyAheadMinutes)
        assertEquals("v9.9.9", actual?.installPermissionPromptLastVersion)
    }

    @Test
    fun syncPublicationInvokesPlannerOnceAndFinalizesOneSuccessfulRun() = runTest {
        val repository = SqliteSyncRepository(store)
        var plannerCalls = 0
        val plan = emptyAdaptivePlan()

        val result = repository.publish(
            emptyPublicationCommand {
                plannerCalls += 1
                SyncQueuePlan(emptyList(), plan)
            },
        )

        assertTrue(result.isOk())
        assertEquals(1, plannerCalls)
        assertEquals(plan, result.valueOrNull()?.adaptiveLoadPlan)
        assertTrue(store.hasSuccessfulSyncSince(FINISHED_AT))
    }

    @Test
    fun plannerFailureRollsBackStagedMirrorAndPendingHistory() = runTest {
        val repository = SqliteSyncRepository(store)

        try {
            repository.publish(
                emptyPublicationCommand {
                    throw IllegalArgumentException("queue planning failed")
                },
            )
            throw AssertionError("planner failure should propagate")
        } catch (_: IllegalArgumentException) {
            // A non-storage planning error propagates after the outer transaction rolls back.
        }

        assertNull(store.latestSync())
        assertTrue(store.studyItems().isEmpty())
        assertFalse(store.hasPersistedCollectionMirror())
    }

    @Test
    fun syncAdapterOwnsFailureWriteBackAndHandoffOperations() = runTest {
        val repository = SqliteSyncRepository(store)

        val state = repository.loadStoredState().valueOrNull()
        assertFalse(state?.hasCollectionMirror ?: true)
        assertTrue(
            repository.recordFailure(
                RecordSyncFailureCommand(
                    startedAtMillis = STARTED_AT,
                    finishedAtMillis = FINISHED_AT,
                    status = "provider_error",
                    errorCode = "provider_unavailable",
                    errorMessage = "provider unavailable",
                ),
            ).isOk(),
        )
        assertTrue(repository.updateRemovalMessage(1L, "nothing removed").isOk())

        val snapshot = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())
        val proposal = repository.repairedWriteBackProposal(snapshot, 2).valueOrNull()
            ?: throw AssertionError("write-back proposal was not loaded")
        assertTrue(proposal.isEmpty())
        assertTrue(repository.repairedWriteBackPreview(2).valueOrNull()?.isEmpty() == true)
        assertTrue(
            repository.recordRepairedWriteBack(
                RecordRepairedWriteBackCommand(
                    proposal = proposal,
                    taggedNoteIds = emptySet(),
                    occurredAtMillis = FINISHED_AT,
                    syncId = 1L,
                ),
            ).valueOrNull()?.isEmpty() == true,
        )
        assertTrue(repository.loadRepairedHandoff().valueOrNull()?.isEmpty() == true)
        assertTrue(repository.dismissRepairedHandoff().isOk())

        val latest = SqliteHomeRepository(store).loadHome(FINISHED_AT).valueOrNull()?.latestSync
        assertEquals("provider_error", latest?.status)
        assertEquals("provider unavailable", latest?.errorMessage)
    }

    private fun emptyPublicationCommand(planner: SyncQueuePlanner): SyncPublicationCommand =
        SyncPublicationCommand(
            snapshot = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            imports = emptyList(),
            auditImports = emptyList(),
            rows = emptyList(),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
            timing = SyncTimingSnapshot(STARTED_AT, FINISHED_AT),
            removalMessage = null,
            similarIndex = null,
            dictionary = null,
            queuePlanner = planner,
        )

    private fun emptyAdaptivePlan(): RecordsSchedulerModels.AdaptiveLoadPlan =
        RecordsSchedulerModels.AdaptiveLoadPlan(
            100,
            0,
            0,
            emptyList(),
            0,
            true,
            "all",
        )

    private fun syncSettingsWithCustomImmutableFields(
        matureDays: Int = 35,
        matureSupportThreshold: Int = 4,
    ): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            defaults.modelName,
            "Alternate",
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            matureDays,
            matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
            defaults.importActiveCards,
            defaults.importSuspendedCards,
            defaults.importTaggedCards,
            defaults.importTags,
            defaults.importWeakCards,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            defaults.importMinMatchingCardsPerKanji,
            defaults.importBrowserQueryCards,
            defaults.importBrowserQuery,
            defaults.newCardSortMode,
            defaults.ladderPromotionIntervalDays,
            defaults.ladderDemotionFailStreak,
            defaults.ladderPromotionMinPasses,
        )
    }

    private fun studyItem(kanji: String, totalReviews: Int): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            "review",
            STARTED_AT,
            1.0,
            2.0,
            totalReviews,
            0,
            0,
            0,
            "",
            STARTED_AT,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()

    private fun writingRepair(): RecordsImportModels.SimilarKanjiWritingRepair =
        RecordsImportModels.SimilarKanjiWritingRepair(
            999L,
            "痛",
            "痒",
            "痛|痒",
            "痒",
            "pain",
            "pending",
            STARTED_AT,
            "repair-token",
            0,
            STARTED_AT,
            STARTED_AT,
            0L,
        )

    private companion object {
        const val STARTED_AT = 1_000L
        const val FINISHED_AT = 2_000L
    }
}
