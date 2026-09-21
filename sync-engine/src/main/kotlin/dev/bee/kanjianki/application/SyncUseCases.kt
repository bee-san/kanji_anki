package dev.bee.kanjianki.application

import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.RecordRepairedWriteBackCommand
import dev.bee.kanjianki.data.RecordSyncFailureCommand
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.StudyRepository
import dev.bee.kanjianki.data.SyncPublicationCommand
import dev.bee.kanjianki.data.SyncPublicationResult
import dev.bee.kanjianki.data.SyncRepository

/** Portable repository boundary owned by the shared sync engine. */
class SyncUseCases(
    private val syncRepository: SyncRepository,
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun loadSettings(): SettingsSnapshot =
        settingsRepository.load().valueOrThrow("load sync settings")

    suspend fun loadStoredState(): StoredSyncState =
        syncRepository.loadStoredState().valueOrThrow("load stored sync state")

    suspend fun publish(command: SyncPublicationCommand): SyncPublicationResult =
        syncRepository.publish(command).valueOrThrow("publish sync")

    suspend fun recordFailure(command: RecordSyncFailureCommand) {
        syncRepository.recordFailure(command).valueOrThrow("record sync failure")
    }

    suspend fun updateRemovalMessage(syncId: Long, message: String?) {
        syncRepository.updateRemovalMessage(syncId, message)
            .valueOrThrow("update sync removal message")
    }

    suspend fun repairedWriteBackProposal(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        matureSupportThreshold: Int,
    ): RepairedWriteBackPolicy.Proposal =
        syncRepository.repairedWriteBackProposal(snapshot, matureSupportThreshold)
            .valueOrThrow("load repaired write-back proposal")

    suspend fun recordRepairedWriteBack(command: RecordRepairedWriteBackCommand): List<String> =
        syncRepository.recordRepairedWriteBack(command)
            .valueOrThrow("record repaired write-back")

    suspend fun loadCommittedStudyQueue(nowMillis: Long): StudyQueueSnapshot =
        studyRepository.loadQueue(nowMillis).valueOrThrow("load committed study queue")

    suspend fun loadCommittedStudyItems(kanji: Collection<String>): List<RecordsStudyModels.StudyItem> =
        studyRepository.loadItems(kanji).valueOrThrow("load committed study items")

    suspend fun annotateCapabilities(
        items: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> =
        studyRepository.annotateCapabilities(items)
            .valueOrThrow("annotate committed study capabilities")
}
