package dev.bee.kanjianki.data.fakes

import dev.bee.kanjianki.core.AnkiKanjiInventory
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.ManualKanjiSource
import dev.bee.kanjianki.core.ManualKanjiSourceRemovalResult
import dev.bee.kanjianki.core.ManualKanjiSourceWriteResult
import dev.bee.kanjianki.core.MissingKanjiExportReceipt
import dev.bee.kanjianki.core.MissingKanjiInventoryState
import dev.bee.kanjianki.core.MissingKanjiPreferences
import dev.bee.kanjianki.core.MissingKanjiScanRecord
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AddManualKanjiSourcesCommand
import dev.bee.kanjianki.data.CommitFsrsFitCommand
import dev.bee.kanjianki.data.DeactivateManualKanjiSourcesCommand
import dev.bee.kanjianki.data.MissingKanjiRepository
import dev.bee.kanjianki.data.PublishMissingKanjiInventoryCommand
import dev.bee.kanjianki.data.RecordMissingKanjiScanCommand
import dev.bee.kanjianki.data.RemoveManualKanjiSourcesCommand
import dev.bee.kanjianki.data.FinishLegacyRepairCommand
import dev.bee.kanjianki.data.HomeKanjiDetailSnapshot
import dev.bee.kanjianki.data.HomeGameDataSnapshot
import dev.bee.kanjianki.data.HomeNewCardSortPreviewSnapshot
import dev.bee.kanjianki.data.HomeRepository
import dev.bee.kanjianki.data.HomeSnapshot
import dev.bee.kanjianki.data.RecordRepairedWriteBackCommand
import dev.bee.kanjianki.data.RecordSyncFailureCommand
import dev.bee.kanjianki.data.ReviewCommitCommand
import dev.bee.kanjianki.data.ReviewCommitResult
import dev.bee.kanjianki.data.ReviewTaskTiming
import dev.bee.kanjianki.data.ReviewTokenQuery
import dev.bee.kanjianki.data.ReviewTokenStatus
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SetLocalSuspensionCommand
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.SkipLegacyRepairCommand
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StatsRepository
import dev.bee.kanjianki.data.StatsSnapshot
import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.data.StudyChoiceDataSnapshot
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.StudyQueueWriteCommand
import dev.bee.kanjianki.data.StudyRecoveryQuery
import dev.bee.kanjianki.data.StudyRecoveryStatus
import dev.bee.kanjianki.data.StudyRepository
import dev.bee.kanjianki.data.SyncPublicationCommand
import dev.bee.kanjianki.data.SyncPublicationResult
import dev.bee.kanjianki.data.SyncRepository

class FakeHomeRepository : HomeRepository {
    var loadHomeHandler: suspend (Long) -> StoreResult<HomeSnapshot> = unconfigured1("loadHome")
    var searchHandler:
        suspend (String, Boolean) -> StoreResult<List<RecordsImportModels.KanjiInventoryItem>> =
        unconfigured2("searchInventory")
    var studySearchHandler:
        suspend (String, Boolean, Boolean) -> StoreResult<List<RecordsImportModels.KanjiInventoryItem>> =
        { _, _, _ -> StoreResult.ok(emptyList()) }
    var detailHandler: suspend (String, Long) -> StoreResult<HomeKanjiDetailSnapshot> =
        unconfigured2("loadKanjiDetail")
    var gameDataResult: StoreResult<HomeGameDataSnapshot> = StoreResult.ok(
        HomeGameDataSnapshot(emptyList(), emptyList(), emptyList()),
    )
    var newCardSortPreviewResult: StoreResult<HomeNewCardSortPreviewSnapshot> = StoreResult.ok(
        HomeNewCardSortPreviewSnapshot(emptyList(), emptyList(), 0L),
    )
    var newCardSortPreviewVersionResult: StoreResult<Long> = StoreResult.ok(0L)
    var downgradeNoticeResult: StoreResult<Int?> = StoreResult.ok(null)
    var saveMnemonicHandler: suspend (SaveMnemonicCommand) -> StoreResult<Unit> =
        { StoreResult.ok(Unit) }
    var suspensionHandler: suspend (SetLocalSuspensionCommand) -> StoreResult<Unit> =
        { StoreResult.ok(Unit) }

    val mnemonicCommands = mutableListOf<SaveMnemonicCommand>()
    val suspensionCommands = mutableListOf<SetLocalSuspensionCommand>()

    override suspend fun loadHome(nowMillis: Long): StoreResult<HomeSnapshot> =
        loadHomeHandler(nowMillis)

    override suspend fun searchInventory(
        query: String,
        onlySimilarKanji: Boolean,
    ): StoreResult<List<RecordsImportModels.KanjiInventoryItem>> =
        searchHandler(query, onlySimilarKanji)

    override suspend fun searchStudyInventory(
        query: String,
        onlySimilarKanji: Boolean,
        includeLocallySuspended: Boolean,
    ): StoreResult<List<RecordsImportModels.KanjiInventoryItem>> =
        studySearchHandler(query, onlySimilarKanji, includeLocallySuspended)

    override suspend fun loadKanjiDetail(
        kanji: String,
        nowMillis: Long,
    ): StoreResult<HomeKanjiDetailSnapshot> = detailHandler(kanji, nowMillis)

    override suspend fun loadGameData(): StoreResult<HomeGameDataSnapshot> = gameDataResult

    override suspend fun loadNewCardSortPreviewData(): StoreResult<HomeNewCardSortPreviewSnapshot> =
        newCardSortPreviewResult

    override suspend fun loadNewCardSortPreviewVersion(): StoreResult<Long> =
        newCardSortPreviewVersionResult

    override suspend fun consumeDowngradeNotice(): StoreResult<Int?> = downgradeNoticeResult

    override suspend fun saveMnemonic(command: SaveMnemonicCommand): StoreResult<Unit> {
        mnemonicCommands += command
        return saveMnemonicHandler(command)
    }

    override suspend fun setLocalSuspension(command: SetLocalSuspensionCommand): StoreResult<Unit> {
        suspensionCommands += command
        return suspensionHandler(command)
    }
}

class FakeStudyRepository : StudyRepository {
    var loadQueueHandler: suspend (Long) -> StoreResult<StudyQueueSnapshot> = unconfigured1("loadQueue")
    var loadAllItemsHandler: suspend () -> StoreResult<List<RecordsStudyModels.StudyItem>> =
        unconfigured0("loadAllItems")
    var loadItemsHandler:
        suspend (Collection<String>) -> StoreResult<List<RecordsStudyModels.StudyItem>> =
        unconfigured1("loadItems")
    var replaceQueueHandler: suspend (StudyQueueWriteCommand) -> StoreResult<Unit> =
        { StoreResult.ok(Unit) }
    var annotateHandler:
        suspend (List<RecordsStudyModels.StudyItem>) -> StoreResult<List<RecordsStudyModels.StudyItem>> =
        { StoreResult.ok(it) }
    var saveItemHandler: suspend (RecordsStudyModels.StudyItem) -> StoreResult<Unit> =
        { StoreResult.ok(Unit) }
    var taskTimingHandler: suspend (ReviewTaskTiming) -> StoreResult<Boolean> =
        { StoreResult.ok(true) }
    var commitReviewHandler: suspend (ReviewCommitCommand) -> StoreResult<ReviewCommitResult> =
        unconfigured1("commitReview")
    var undoHandler: suspend (AppliedReviewSnapshot) -> StoreResult<Boolean> =
        unconfigured1("undoLastReview")
    var queueVersionHandler: suspend () -> StoreResult<Long?> = { StoreResult.ok(null) }
    var tokenHandler: suspend (ReviewTokenQuery) -> StoreResult<ReviewTokenStatus> =
        unconfigured1("reviewTokenStatus")
    var recoveryHandler: suspend (StudyRecoveryQuery) -> StoreResult<StudyRecoveryStatus> =
        unconfigured1("recoveryStatus")
    var choiceHandler: suspend (String, Long) -> StoreResult<StudyChoiceDataSnapshot> =
        unconfigured2("loadChoiceData")
    var dueChoiceHandler:
        suspend (String, Long) -> StoreResult<RecordsImportModels.SimilarKanjiChoiceCard?> =
        { _, _ -> StoreResult.ok(null) }
    var dueRepairsHandler:
        suspend (Long) -> StoreResult<List<RecordsImportModels.SimilarKanjiWritingRepair>> =
        { StoreResult.ok(emptyList()) }
    var saveRepairHandler:
        suspend (RecordsImportModels.SimilarKanjiWritingRepair) -> StoreResult<Unit> =
        { StoreResult.ok(Unit) }
    var finishRepairHandler: suspend (FinishLegacyRepairCommand) -> StoreResult<Boolean> =
        { StoreResult.ok(true) }
    var skipRepairHandler: suspend (SkipLegacyRepairCommand) -> StoreResult<Boolean> =
        { StoreResult.ok(true) }
    var mnemonicHandler: suspend (String) -> StoreResult<String> = { StoreResult.ok("") }
    var saveMnemonicHandler: suspend (SaveMnemonicCommand) -> StoreResult<Unit> =
        { StoreResult.ok(Unit) }

    val queueWrites = mutableListOf<StudyQueueWriteCommand>()
    val reviewCommits = mutableListOf<ReviewCommitCommand>()
    val savedItems = mutableListOf<RecordsStudyModels.StudyItem>()
    val taskTimings = mutableListOf<ReviewTaskTiming>()
    val mnemonicCommands = mutableListOf<SaveMnemonicCommand>()

    override suspend fun loadQueue(nowMillis: Long): StoreResult<StudyQueueSnapshot> =
        loadQueueHandler(nowMillis)

    override suspend fun loadAllItems(): StoreResult<List<RecordsStudyModels.StudyItem>> =
        loadAllItemsHandler()

    override suspend fun loadItems(
        kanji: Collection<String>,
    ): StoreResult<List<RecordsStudyModels.StudyItem>> = loadItemsHandler(kanji)

    override suspend fun replaceQueue(command: StudyQueueWriteCommand): StoreResult<Unit> {
        queueWrites += command
        return replaceQueueHandler(command)
    }

    override suspend fun annotateCapabilities(
        items: List<RecordsStudyModels.StudyItem>,
    ): StoreResult<List<RecordsStudyModels.StudyItem>> = annotateHandler(items)

    override suspend fun saveItem(item: RecordsStudyModels.StudyItem): StoreResult<Unit> {
        savedItems += item
        return saveItemHandler(item)
    }

    override suspend fun recordTaskTiming(timing: ReviewTaskTiming): StoreResult<Boolean> {
        taskTimings += timing
        return taskTimingHandler(timing)
    }

    override suspend fun commitReview(command: ReviewCommitCommand): StoreResult<ReviewCommitResult> {
        reviewCommits += command
        return commitReviewHandler(command)
    }

    override suspend fun undoLastReview(snapshot: AppliedReviewSnapshot): StoreResult<Boolean> =
        undoHandler(snapshot)

    override suspend fun loadQueueVersion(): StoreResult<Long?> = queueVersionHandler()

    override suspend fun reviewTokenStatus(query: ReviewTokenQuery): StoreResult<ReviewTokenStatus> =
        tokenHandler(query)

    override suspend fun recoveryStatus(query: StudyRecoveryQuery): StoreResult<StudyRecoveryStatus> =
        recoveryHandler(query)

    override suspend fun loadChoiceData(
        kanji: String,
        nowMillis: Long,
    ): StoreResult<StudyChoiceDataSnapshot> = choiceHandler(kanji, nowMillis)

    override suspend fun loadDueSimilarChoice(
        targetKanji: String,
        nowMillis: Long,
    ): StoreResult<RecordsImportModels.SimilarKanjiChoiceCard?> =
        dueChoiceHandler(targetKanji, nowMillis)

    override suspend fun loadDueLegacyWritingRepairs(
        nowMillis: Long,
    ): StoreResult<List<RecordsImportModels.SimilarKanjiWritingRepair>> =
        dueRepairsHandler(nowMillis)

    override suspend fun saveLegacyWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
    ): StoreResult<Unit> = saveRepairHandler(repair)

    override suspend fun finishLegacyWritingRepair(
        command: FinishLegacyRepairCommand,
    ): StoreResult<Boolean> = finishRepairHandler(command)

    override suspend fun skipLegacyWritingRepair(
        command: SkipLegacyRepairCommand,
    ): StoreResult<Boolean> = skipRepairHandler(command)

    override suspend fun loadMnemonic(kanji: String): StoreResult<String> = mnemonicHandler(kanji)

    override suspend fun saveMnemonic(command: SaveMnemonicCommand): StoreResult<Unit> {
        mnemonicCommands += command
        return saveMnemonicHandler(command)
    }
}

class FakeStatsRepository : StatsRepository {
    var cachedResult: StoreResult<StatsSnapshot?> = StoreResult.ok(null)
    var latestResult: StoreResult<StatsSnapshot?> = StoreResult.ok(null)
    var refreshHandler: suspend (Long) -> StoreResult<StatsSnapshot> = unconfigured1("refresh")
    val refreshRequests = mutableListOf<Long>()

    override suspend fun loadCached(nowMillis: Long): StoreResult<StatsSnapshot?> = cachedResult

    override suspend fun loadLatest(): StoreResult<StatsSnapshot?> = latestResult

    override suspend fun refresh(nowMillis: Long): StoreResult<StatsSnapshot> {
        refreshRequests += nowMillis
        return refreshHandler(nowMillis)
    }
}

class FakeSettingsRepository : SettingsRepository {
    var loadHandler: suspend () -> StoreResult<SettingsSnapshot> = unconfigured0("load")
    var saveHandler: suspend (SettingsSaveCommand) -> StoreResult<Unit> = { StoreResult.ok(Unit) }
    var fitHandler: suspend (CommitFsrsFitCommand) -> StoreResult<Boolean> = { StoreResult.ok(false) }
    val saveCommands = mutableListOf<SettingsSaveCommand>()
    val fitCommands = mutableListOf<CommitFsrsFitCommand>()

    override suspend fun load(): StoreResult<SettingsSnapshot> = loadHandler()

    override suspend fun save(command: SettingsSaveCommand): StoreResult<Unit> {
        saveCommands += command
        return saveHandler(command)
    }

    override suspend fun commitFsrsFit(command: CommitFsrsFitCommand): StoreResult<Boolean> {
        fitCommands += command
        return fitHandler(command)
    }
}

class FakeSyncRepository : SyncRepository {
    var storedStateHandler: suspend () -> StoreResult<StoredSyncState> = unconfigured0("loadStoredState")
    var publishHandler: suspend (SyncPublicationCommand) -> StoreResult<SyncPublicationResult> =
        unconfigured1("publish")
    var failureHandler: suspend (RecordSyncFailureCommand) -> StoreResult<Unit> =
        { StoreResult.ok(Unit) }
    var removalHandler: suspend (Long, String?) -> StoreResult<Unit> =
        { _, _ -> StoreResult.ok(Unit) }
    var proposalHandler:
        suspend (RecordsSyncModels.CollectionSnapshot, Int) ->
            StoreResult<RepairedWriteBackPolicy.Proposal> =
        unconfigured2("repairedWriteBackProposal")
    var previewHandler:
        suspend (Int) -> StoreResult<RepairedWriteBackPolicy.Proposal> =
        unconfigured1("repairedWriteBackPreview")
    var writeBackHandler: suspend (RecordRepairedWriteBackCommand) -> StoreResult<List<String>> =
        { StoreResult.ok(emptyList()) }
    var handoffResult: StoreResult<List<String>> = StoreResult.ok(emptyList())
    var dismissResult: StoreResult<Unit> = StoreResult.ok(Unit)

    val publications = mutableListOf<SyncPublicationCommand>()
    val failures = mutableListOf<RecordSyncFailureCommand>()

    override suspend fun loadStoredState(): StoreResult<StoredSyncState> = storedStateHandler()

    override suspend fun publish(command: SyncPublicationCommand): StoreResult<SyncPublicationResult> {
        publications += command
        return publishHandler(command)
    }

    override suspend fun recordFailure(command: RecordSyncFailureCommand): StoreResult<Unit> {
        failures += command
        return failureHandler(command)
    }

    override suspend fun updateRemovalMessage(syncId: Long, message: String?): StoreResult<Unit> =
        removalHandler(syncId, message)

    override suspend fun repairedWriteBackProposal(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        matureSupportThreshold: Int,
    ) = proposalHandler(snapshot, matureSupportThreshold)

    override suspend fun repairedWriteBackPreview(matureSupportThreshold: Int) =
        previewHandler(matureSupportThreshold)

    override suspend fun recordRepairedWriteBack(
        command: RecordRepairedWriteBackCommand,
    ): StoreResult<List<String>> = writeBackHandler(command)

    override suspend fun loadRepairedHandoff(): StoreResult<List<String>> = handoffResult

    override suspend fun dismissRepairedHandoff(): StoreResult<Unit> = dismissResult
}

class FakeMissingKanjiRepository : MissingKanjiRepository {
    var publishHandler: suspend (PublishMissingKanjiInventoryCommand) -> StoreResult<MissingKanjiScanRecord> =
        unconfigured1("publishInventory")
    var unsuccessfulScanHandler: suspend (RecordMissingKanjiScanCommand) -> StoreResult<MissingKanjiScanRecord> =
        unconfigured1("recordUnsuccessfulScan")
    var inventoryStateHandler: suspend () -> StoreResult<MissingKanjiInventoryState> =
        unconfigured0("inventoryState")
    var loadPreferencesHandler: suspend () -> StoreResult<MissingKanjiPreferences> =
        unconfigured0("loadPreferences")
    var savePreferencesHandler: suspend (MissingKanjiPreferences) -> StoreResult<Unit> =
        { StoreResult.ok(Unit) }
    var addSourcesHandler: suspend (AddManualKanjiSourcesCommand) -> StoreResult<ManualKanjiSourceWriteResult> =
        unconfigured1("addManualSources")
    var manualSourcesHandler: suspend (Boolean) -> StoreResult<List<ManualKanjiSource>> =
        { StoreResult.ok(emptyList()) }
    var admittedSourcesHandler: suspend () -> StoreResult<List<ManualKanjiSource>> =
        { StoreResult.ok(emptyList()) }
    var manualSourceHandler: suspend (String) -> StoreResult<ManualKanjiSource?> =
        { StoreResult.ok(null) }
    var removableLiteralsHandler: suspend () -> StoreResult<Set<String>> =
        { StoreResult.ok(emptySet()) }
    var removeSourcesHandler: suspend (RemoveManualKanjiSourcesCommand) -> StoreResult<ManualKanjiSourceRemovalResult> =
        unconfigured1("removeUnreviewedManualSources")
    var deactivateSourcesHandler: suspend (DeactivateManualKanjiSourcesCommand) -> StoreResult<Int> =
        { StoreResult.ok(0) }
    var recordReceiptsHandler: suspend (Collection<MissingKanjiExportReceipt>) -> StoreResult<Int> =
        { StoreResult.ok(0) }
    var exportReceiptsHandler: suspend (String) -> StoreResult<Map<String, MissingKanjiExportReceipt>> =
        { StoreResult.ok(emptyMap()) }

    val publishCommands = mutableListOf<PublishMissingKanjiInventoryCommand>()
    val savedPreferences = mutableListOf<MissingKanjiPreferences>()

    override suspend fun publishInventory(
        command: PublishMissingKanjiInventoryCommand,
    ): StoreResult<MissingKanjiScanRecord> {
        publishCommands += command
        return publishHandler(command)
    }

    override suspend fun recordUnsuccessfulScan(
        command: RecordMissingKanjiScanCommand,
    ): StoreResult<MissingKanjiScanRecord> = unsuccessfulScanHandler(command)

    override suspend fun inventoryState(): StoreResult<MissingKanjiInventoryState> =
        inventoryStateHandler()

    override suspend fun loadPreferences(): StoreResult<MissingKanjiPreferences> =
        loadPreferencesHandler()

    override suspend fun savePreferences(preferences: MissingKanjiPreferences): StoreResult<Unit> {
        savedPreferences += preferences
        return savePreferencesHandler(preferences)
    }

    override suspend fun addManualSources(
        command: AddManualKanjiSourcesCommand,
    ): StoreResult<ManualKanjiSourceWriteResult> = addSourcesHandler(command)

    override suspend fun manualSources(activeOnly: Boolean): StoreResult<List<ManualKanjiSource>> =
        manualSourcesHandler(activeOnly)

    override suspend fun admittedManualSources(): StoreResult<List<ManualKanjiSource>> =
        admittedSourcesHandler()

    override suspend fun manualSource(literal: String): StoreResult<ManualKanjiSource?> =
        manualSourceHandler(literal)

    override suspend fun removableManualSourceLiterals(): StoreResult<Set<String>> =
        removableLiteralsHandler()

    override suspend fun removeUnreviewedManualSources(
        command: RemoveManualKanjiSourcesCommand,
    ): StoreResult<ManualKanjiSourceRemovalResult> = removeSourcesHandler(command)

    override suspend fun deactivateManualSources(
        command: DeactivateManualKanjiSourcesCommand,
    ): StoreResult<Int> = deactivateSourcesHandler(command)

    override suspend fun recordExportReceipts(
        receipts: Collection<MissingKanjiExportReceipt>,
    ): StoreResult<Int> = recordReceiptsHandler(receipts)

    override suspend fun exportReceipts(
        destinationKey: String,
    ): StoreResult<Map<String, MissingKanjiExportReceipt>> = exportReceiptsHandler(destinationKey)
}

private fun <T> unconfigured0(name: String): suspend () -> StoreResult<T> = {
    error("Fake repository operation '$name' was not configured")
}

private fun <A, T> unconfigured1(name: String): suspend (A) -> StoreResult<T> = {
    error("Fake repository operation '$name' was not configured")
}

private fun <A, B, T> unconfigured2(name: String): suspend (A, B) -> StoreResult<T> = { _, _ ->
    error("Fake repository operation '$name' was not configured")
}
