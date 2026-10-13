package dev.bee.kanjianki.backup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSpaceBudgetTest {
    @Test
    fun writesAreAllowedUntilTheReserveFloor() {
        val budget = BackupSpaceBudget(allocatableBytes = 1_000L, reserveBytes = 100L)
        assertEquals(900L, budget.remainingWritableBytes())
        assertTrue(budget.canWrite(900))
        assertFalse("cannot dip into the reserve", budget.canWrite(901))
        budget.requireWrite(500)
        budget.recordWrite(500)
        assertEquals(400L, budget.remainingWritableBytes())
        assertThrows(InsufficientBackupStorageException::class.java) { budget.requireWrite(401) }
    }

    @Test
    fun budgetBelowReserveRejectsEveryWrite() {
        val budget = BackupSpaceBudget(allocatableBytes = 50L, reserveBytes = 100L)
        assertEquals(0L, budget.remainingWritableBytes())
        assertFalse(budget.canWrite(1))
        assertThrows(InsufficientBackupStorageException::class.java) { budget.requireWrite(1) }
    }

    @Test
    fun negativeAllocatableClampsToZero() {
        val budget = BackupSpaceBudget(allocatableBytes = -5L, reserveBytes = 0L)
        assertEquals(0L, budget.remainingWritableBytes())
        budget.recordWrite(10)
        assertEquals(0L, budget.remainingWritableBytes())
    }

    @Test
    fun decompressedCapMirrorsValidatorGuard() {
        val max = BackupSpaceBudget.MAX_DECOMPRESSED_BYTES
        assertFalse(BackupSpaceBudget.exceedsDecompressedCap(alreadyWritten = 0L, nextChunkBytes = 8))
        assertFalse(BackupSpaceBudget.exceedsDecompressedCap(alreadyWritten = max - 8, nextChunkBytes = 8))
        assertTrue(BackupSpaceBudget.exceedsDecompressedCap(alreadyWritten = max - 7, nextChunkBytes = 8))
    }

    @Test
    fun storageExhaustionRecheck() {
        assertTrue(
            "below reserve is exhausted",
            BackupSpaceBudget.isStorageExhausted(allocatableBytes = 50L, reserveBytes = 100L, bytesNeeded = 1),
        )
        assertTrue(
            "no room for the needed bytes above reserve",
            BackupSpaceBudget.isStorageExhausted(allocatableBytes = 105L, reserveBytes = 100L, bytesNeeded = 10),
        )
        assertFalse(
            "enough room above reserve is not exhausted",
            BackupSpaceBudget.isStorageExhausted(allocatableBytes = 200L, reserveBytes = 100L, bytesNeeded = 10),
        )
        assertTrue(
            "negative allocatable clamps and is exhausted",
            BackupSpaceBudget.isStorageExhausted(allocatableBytes = -1L, reserveBytes = 0L, bytesNeeded = 1),
        )
    }

    @Test
    fun constantsMatchTheAndroidValidator() {
        assertEquals(512L * 1024L * 1024L, BackupSpaceBudget.MAX_DECOMPRESSED_BYTES)
        assertEquals(64L * 1024L * 1024L, BackupSpaceBudget.FREE_SPACE_RESERVE_BYTES)
    }
}
