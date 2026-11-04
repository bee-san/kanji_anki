package dev.bee.kanjianki.application

import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.HomeGameDataSnapshot
import dev.bee.kanjianki.data.HomeKanjiDetailSnapshot
import dev.bee.kanjianki.data.HomeNewCardSortPreviewSnapshot
import dev.bee.kanjianki.data.HomeRepository
import dev.bee.kanjianki.data.HomeSnapshot
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SetLocalSuspensionCommand
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.StudyQueueWriteCommand
import dev.bee.kanjianki.data.StudyRepository
import dev.bee.kanjianki.data.SyncRepository

data class HomeRouteSnapshot(
    val home: HomeSnapshot,
    val study: StudyQueueSnapshot,
    val settings: SettingsSnapshot,
    val repairedWriteBackProposal: RepairedWriteBackPolicy.Proposal?,
)

/**
 * Portable Home/Browse orchestration. Repository failures are normalized here
 * so hosts can render one retry state without depending on storage details.
 */
class HomeUseCases(
    private val homeRepository: HomeRepository,
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
) {
    suspend fun loadRoute(nowMillis: Long): HomeRouteSnapshot {
        val home = homeRepository.loadHome(nowMillis).valueOrThrow("load home")
        val study = studyRepository.loadQueue(nowMillis).valueOrThrow("load home study queue")
        val settings = settingsRepository.load().valueOrThrow("load home settings")
        val proposal = if (settings.tagRepairedCards) {
            syncRepository.repairedWriteBackPreview(settings.sync.matureSupportThreshold)
                .valueOrThrow("load repaired write-back preview")
        } else {
            null
        }
        return HomeRouteSnapshot(home, study, settings, proposal)
    }

    suspend fun searchStudyInventory(
        query: String,
        onlySimilarKanji: Boolean,
        includeLocallySuspended: Boolean,
    ): List<RecordsImportModels.KanjiInventoryItem> =
        homeRepository.searchStudyInventory(
            query,
            onlySimilarKanji,
            includeLocallySuspended,
        ).valueOrThrow("search study inventory")

    suspend fun loadKanjiDetail(
        kanji: String,
        nowMillis: Long,
    ): HomeKanjiDetailSnapshot =
        homeRepository.loadKanjiDetail(kanji, nowMillis)
            .valueOrThrow("load kanji detail")

    suspend fun loadGameData(): HomeGameDataSnapshot =
        homeRepository.loadGameData().valueOrThrow("load game data")

    suspend fun loadHome(nowMillis: Long): HomeSnapshot =
        homeRepository.loadHome(nowMillis).valueOrThrow("load home")

    suspend fun loadNewCardSortPreviewData(): HomeNewCardSortPreviewSnapshot =
        homeRepository.loadNewCardSortPreviewData()
            .valueOrThrow("load new-card sort preview")

    suspend fun loadNewCardSortPreviewVersion(): Long =
        homeRepository.loadNewCardSortPreviewVersion()
            .valueOrThrow("load new-card sort preview version")

    suspend fun loadSettings(): SettingsSnapshot =
        settingsRepository.load().valueOrThrow("load settings")

    suspend fun loadStudyQueue(nowMillis: Long): StudyQueueSnapshot =
        studyRepository.loadQueue(nowMillis).valueOrThrow("load study queue")

    suspend fun annotateCapabilities(
        items: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> =
        studyRepository.annotateCapabilities(items).valueOrThrow("annotate study capabilities")

    suspend fun replaceStudyQueue(
        items: List<RecordsStudyModels.StudyItem>,
        baseline: List<RecordsStudyModels.StudyItem>,
    ) {
        studyRepository.replaceQueue(StudyQueueWriteCommand(items, baseline))
            .valueOrThrow("replace study queue")
    }

    suspend fun consumeDowngradeNotice(): Int? =
        homeRepository.consumeDowngradeNotice().valueOrThrow("consume downgrade notice")

    suspend fun saveMnemonic(command: SaveMnemonicCommand) {
        homeRepository.saveMnemonic(command).valueOrThrow("save mnemonic")
    }

    suspend fun setLocalSuspension(command: SetLocalSuspensionCommand) {
        homeRepository.setLocalSuspension(command).valueOrThrow("set local suspension")
    }

    suspend fun dismissRepairedHandoff() {
        syncRepository.dismissRepairedHandoff().valueOrThrow("dismiss repaired handoff")
    }
}
