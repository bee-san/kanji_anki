package dev.bee.kanjianki.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRunRepositoryTest {
    @Test
    fun successfulSyncMapsInsertCountsAndMessages() {
        val storage = FakeSyncRunStorage()
        val repository = SyncRunRepository(storage)
        val activeIndex = LocalStoreBase.ActiveCardIndex(
            setOf(10L, 11L, 12L),
            setOf(20L, 21L),
            2,
        )

        val id = repository.insertSuccessfulSync(
            LocalStoreBase.SyncRunInsert(
                startedAt = 100L,
                finishedAt = 200L,
                status = LocalStoreBase.STATUS_SUCCESS,
                activeIndex = activeIndex,
                archivedSuspendedCardCount = 4,
                importCount = 5,
                errorCode = null,
                errorMessage = null,
                removalMessage = "Removed stale cards",
                deletedNotes = 6,
                deletedCards = 7,
            ),
        )

        assertEquals(1L, id)
        val record = storage.inserted.single()
        assertEquals(100L, record.startedAt)
        assertEquals(200L, record.finishedAt)
        assertEquals(LocalStoreBase.STATUS_SUCCESS, record.status)
        assertEquals(3, record.activeNotesCount)
        assertEquals(2, record.activeCardsCount)
        assertEquals(4, record.archivedSuspendedCardCount)
        assertEquals(5, record.importedSuspendedKanjiCount)
        assertEquals(6, record.deletedNotesCount)
        assertEquals(7, record.deletedCardsCount)
        assertEquals(null, record.errorCode)
        assertEquals(null, record.errorMessage)
        assertEquals("Removed stale cards", record.removalMessage)
    }

    @Test
    fun failedSyncWritesZeroCountsAndErrorDetails() {
        val storage = FakeSyncRunStorage()
        val repository = SyncRunRepository(storage)

        repository.saveFailedSync(
            startedAt = 300L,
            finishedAt = 400L,
            status = "failed",
            errorCode = "provider_missing",
            errorMessage = "AnkiDroid is unavailable",
        )

        val record = storage.inserted.single()
        assertEquals(300L, record.startedAt)
        assertEquals(400L, record.finishedAt)
        assertEquals("failed", record.status)
        assertEquals(0, record.activeNotesCount)
        assertEquals(0, record.activeCardsCount)
        assertEquals(0, record.archivedSuspendedCardCount)
        assertEquals(0, record.importedSuspendedKanjiCount)
        assertEquals(0, record.deletedNotesCount)
        assertEquals(0, record.deletedCardsCount)
        assertEquals("provider_missing", record.errorCode)
        assertEquals("AnkiDroid is unavailable", record.errorMessage)
        assertEquals("", record.removalMessage)
    }

    @Test
    fun removalMessageUpdateNormalizesNullToEmptyString() {
        val storage = FakeSyncRunStorage()
        val repository = SyncRunRepository(storage)

        repository.updateSyncRemovalMessage(42L, null)

        assertEquals(Pair(42L, ""), storage.updatedRemovalMessages.single())
    }

    @Test
    fun markSyncSucceededFlipsStatusToSuccess() {
        val storage = FakeSyncRunStorage()
        val repository = SyncRunRepository(storage)

        repository.markSyncSucceeded(7L)

        assertEquals(Pair(7L, LocalStoreBase.STATUS_SUCCESS), storage.updatedStatuses.single())
    }

    private class FakeSyncRunStorage : SyncRunStorage {
        val inserted = mutableListOf<SyncRunRecord>()
        val updatedRemovalMessages = mutableListOf<Pair<Long, String>>()
        val updatedStatuses = mutableListOf<Pair<Long, String>>()

        override fun insert(record: SyncRunRecord): Long {
            inserted += record
            return inserted.size.toLong()
        }

        override fun updateRemovalMessage(syncId: Long, message: String) {
            updatedRemovalMessages += syncId to message
        }

        override fun updateStatus(syncId: Long, status: String) {
            updatedStatuses += syncId to status
        }
    }
}
