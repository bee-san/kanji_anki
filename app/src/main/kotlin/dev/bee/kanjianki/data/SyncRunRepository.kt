package dev.bee.kanjianki.data

internal class SyncRunRepository(
    private val storage: SyncRunStorage,
) {
    fun insertSuccessfulSync(syncRun: LocalStoreBase.SyncRunInsert): Long {
        return storage.insert(
            SyncRunRecord(
                startedAt = syncRun.startedAt(),
                finishedAt = syncRun.finishedAt(),
                status = syncRun.status(),
                activeNotesCount = syncRun.activeIndex().noteIds.size,
                activeCardsCount = syncRun.activeIndex().activeCardCount,
                archivedSuspendedCardCount = syncRun.archivedSuspendedCardCount(),
                importedSuspendedKanjiCount = syncRun.importCount(),
                deletedNotesCount = syncRun.deletedNotes(),
                deletedCardsCount = syncRun.deletedCards(),
                errorCode = syncRun.errorCode(),
                errorMessage = syncRun.errorMessage(),
                removalMessage = syncRun.removalMessage(),
            ),
        )
    }

    fun saveFailedSync(
        startedAt: Long,
        finishedAt: Long,
        status: String?,
        errorCode: String?,
        errorMessage: String?,
    ) {
        storage.insert(
            SyncRunRecord(
                startedAt = startedAt,
                finishedAt = finishedAt,
                status = status,
                activeNotesCount = 0,
                activeCardsCount = 0,
                archivedSuspendedCardCount = 0,
                importedSuspendedKanjiCount = 0,
                deletedNotesCount = 0,
                deletedCardsCount = 0,
                errorCode = errorCode,
                errorMessage = errorMessage,
                removalMessage = "",
            ),
        )
    }

    fun updateSyncRemovalMessage(syncId: Long, message: String?) {
        storage.updateRemovalMessage(syncId, message ?: "")
    }

    fun markSyncSucceeded(syncId: Long) {
        storage.updateStatus(syncId, LocalStoreBase.STATUS_SUCCESS)
    }
}
