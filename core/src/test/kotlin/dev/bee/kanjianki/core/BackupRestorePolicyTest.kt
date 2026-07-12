package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestorePolicyTest {
    @Test
    fun validationMatrixRejectsFailClosedWithDistinctCopyIds() {
        assertRejected(
            BackupRestorePolicy.ValidationFacts(gzipReadable = false),
            BackupRestorePolicy.CopyId.TRUNCATED_GZIP,
            "File is corrupted.",
        )
        assertRejected(
            BackupRestorePolicy.ValidationFacts(sqliteMagicPresent = false),
            BackupRestorePolicy.CopyId.BAD_SQLITE_MAGIC,
            "Not a Kani backup.",
        )
        assertRejected(
            BackupRestorePolicy.ValidationFacts(userVersion = 30),
            BackupRestorePolicy.CopyId.NEWER_DATABASE_VERSION,
            "This backup is from a newer version of Kani.",
        )
        assertRejected(
            BackupRestorePolicy.ValidationFacts(settingsTablePresent = false),
            BackupRestorePolicy.CopyId.MISSING_SETTINGS_TABLE,
            "Not a Kani backup.",
        )
        assertRejected(
            BackupRestorePolicy.ValidationFacts(quickCheckOk = false),
            BackupRestorePolicy.CopyId.QUICK_CHECK_FAILED,
            "File is corrupted.",
        )

        val ids = listOf(
            BackupRestorePolicy.CopyId.TRUNCATED_GZIP,
            BackupRestorePolicy.CopyId.BACKUP_TOO_LARGE,
            BackupRestorePolicy.CopyId.INSUFFICIENT_STORAGE,
            BackupRestorePolicy.CopyId.BAD_SQLITE_MAGIC,
            BackupRestorePolicy.CopyId.NEWER_DATABASE_VERSION,
            BackupRestorePolicy.CopyId.MISSING_SETTINGS_TABLE,
            BackupRestorePolicy.CopyId.QUICK_CHECK_FAILED,
            BackupRestorePolicy.CopyId.READY,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            "Backup is too large to restore.",
            BackupRestorePolicy.rejection(BackupRestorePolicy.CopyId.BACKUP_TOO_LARGE).message,
        )
        assertEquals(
            "Not enough free space to restore this backup.",
            BackupRestorePolicy.rejection(BackupRestorePolicy.CopyId.INSUFFICIENT_STORAGE).message,
        )
    }

    @Test
    fun happyPathAcceptsOlderOrCurrentSchema() {
        val older = BackupRestorePolicy.validate(
            BackupRestorePolicy.ValidationFacts(userVersion = 12),
            supportedVersion = 29,
        )
        val current = BackupRestorePolicy.validate(
            BackupRestorePolicy.ValidationFacts(userVersion = 29),
            supportedVersion = 29,
        )

        assertTrue(older.accepted)
        assertEquals(BackupRestorePolicy.CopyId.READY, older.copyId)
        assertEquals("Backup is ready to restore.", older.message)
        assertTrue(current.accepted)
    }

    @Test
    fun runningSyncBlocksRestore() {
        assertFalse(BackupRestorePolicy.restoreAllowed(syncRunning = true))
        assertTrue(BackupRestorePolicy.restoreAllowed(syncRunning = false))
        assertEquals(
            "Wait for the current AnkiDroid sync to finish before restoring.",
            BackupRestorePolicy.panelBlockedBySyncMessage(),
        )
    }

    @Test
    fun restoreCopyIsLocalizedInJapanese() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("このバックアップを復元しますか？", BackupRestorePolicy.confirmTitle())
            assertEquals(
                "このバックアップは新しいバージョンのKaniで作成されています。",
                BackupRestorePolicy.rejection(BackupRestorePolicy.CopyId.NEWER_DATABASE_VERSION).message,
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    private fun assertRejected(
        facts: BackupRestorePolicy.ValidationFacts,
        expectedId: BackupRestorePolicy.CopyId,
        expectedMessage: String,
    ) {
        val result = BackupRestorePolicy.validate(facts, supportedVersion = 29)
        assertFalse(result.accepted)
        assertEquals(expectedId, result.copyId)
        assertEquals(expectedMessage, result.message)
    }
}
