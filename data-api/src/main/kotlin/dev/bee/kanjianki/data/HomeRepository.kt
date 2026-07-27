package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels

/** Persistence capabilities owned by Home and its browse/detail flows. */
interface HomeRepository {
    suspend fun loadHome(nowMillis: Long): StoreResult<HomeSnapshot>

    suspend fun searchInventory(
        query: String,
        onlySimilarKanji: Boolean = false,
    ): StoreResult<List<RecordsImportModels.KanjiInventoryItem>>

    suspend fun searchStudyInventory(
        query: String,
        onlySimilarKanji: Boolean,
        includeLocallySuspended: Boolean,
    ): StoreResult<List<RecordsImportModels.KanjiInventoryItem>>

    suspend fun loadKanjiDetail(
        kanji: String,
        nowMillis: Long,
    ): StoreResult<HomeKanjiDetailSnapshot>

    suspend fun loadGameData(): StoreResult<HomeGameDataSnapshot>

    suspend fun consumeDowngradeNotice(): StoreResult<Int?>

    suspend fun saveMnemonic(command: SaveMnemonicCommand): StoreResult<Unit>

    suspend fun setLocalSuspension(command: SetLocalSuspensionCommand): StoreResult<Unit>
}

data class HomeSnapshot(
    val activeRows: List<RecordsImportModels.DashboardRow>,
    val studyItems: List<RecordsStudyModels.StudyItem>,
    val locallySuspendedKanji: Set<String>,
    val latestSync: SyncStatusSnapshot?,
    val latestSuccessfulSyncAtMillis: Long?,
    val studyStreak: StudyStreakSnapshot,
    val dueLegacyWritingRepairs: List<RecordsImportModels.SimilarKanjiWritingRepair>,
    val repairedHandoffKanji: List<String>,
    val consecutiveFailedSyncs: Int,
)

data class HomeKanjiDetailSnapshot(
    val kanji: String,
    val dashboardRow: RecordsImportModels.DashboardRow?,
    val inventoryItem: RecordsImportModels.KanjiInventoryItem?,
    val timeline: RecordsStudyModels.KanjiRecoveryTimeline,
    val mnemonic: String,
    val similarPairs: List<RecordsImportModels.SimilarKanjiPair>,
    val wrongPickCounts: Map<String, Map<String, Int>>,
    val inventory: List<RecordsImportModels.KanjiInventoryItem>,
    val locallySuspended: Boolean,
)

data class HomeGameDataSnapshot(
    val activeRows: List<RecordsImportModels.DashboardRow>,
    val inventory: List<RecordsImportModels.KanjiInventoryItem>,
    val similarPairs: List<RecordsImportModels.SimilarKanjiPair>,
)

data class SaveMnemonicCommand(
    val kanji: String,
    val note: String,
    val updatedAtMillis: Long,
)

data class SetLocalSuspensionCommand(
    val kanji: Collection<String>,
    val suspended: Boolean,
    val updatedAtMillis: Long,
)
