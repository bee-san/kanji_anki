package dev.bee.kanjianki.domain.model.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncModelsTest {
    @Test
    fun syncStatusWireNamesMatchCurrentDatabaseValues() {
        assertEquals(SyncRunStatus.SUCCESS, SyncRunStatus.fromWireName("success"))
        assertEquals(SyncRunStatus.CONFIG_ERROR, SyncRunStatus.fromWireName("config_error"))
        assertEquals(SyncRunStatus.RETRYABLE_ERROR, SyncRunStatus.fromWireName("retryable_error"))
    }

    @Test
    fun syncErrorWireNamesMatchCurrentFailureValues() {
        assertEquals(SyncErrorCode.PERMANENT, SyncErrorCode.fromWireName("permanent"))
        assertEquals(SyncErrorCode.RETRYABLE, SyncErrorCode.fromWireName("retryable"))
        assertEquals(SyncErrorCode.UNEXPECTED, SyncErrorCode.fromWireName("unexpected"))
        assertEquals(
            SyncErrorCode.PERMANENT_PERMISSION,
            SyncErrorCode.fromWireName("permanent_permission"),
        )
        assertEquals(
            SyncErrorCode.PERMANENT_CONFIGURATION,
            SyncErrorCode.fromWireName("permanent_configuration"),
        )
    }

    @Test
    fun syncRunRejectsNegativeCounters() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncRun(
                id = null,
                startedAt = 10,
                finishedAt = 9,
                status = SyncRunStatus.SUCCESS,
                activeNotesCount = 0,
                activeCardsCount = 0,
                suspendedCardsArchivedCount = 0,
                suspendedKanjiImportedCount = 0,
                deletedNotesCount = 0,
                deletedCardsCount = 0,
                errorCode = null,
                errorMessage = null,
                removalMessage = null,
            )
        }
    }
}
