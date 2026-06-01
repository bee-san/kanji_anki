package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.HashSet
import java.util.TimeZone

class DatabaseBackupPolicyTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun backupFileUsesStableDirectoryPrefixAndTimestamp() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val filesDir = temp.newFolder("files")
            val now = 1_778_832_000_000L

            assertEquals(File(filesDir, "backups"), DatabaseBackupPolicy.backupDir(filesDir))
            assertEquals("20260515_080000", DatabaseBackupPolicy.timestamp(now))
            assertEquals(
                File(File(filesDir, "backups"), "kanji_anki_simple_20260515_080000.db"),
                DatabaseBackupPolicy.backupFile(filesDir, now),
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun oldBackupsToPruneKeepsNewestThirtyOneMatchingDatabaseFiles() {
        val dir = temp.newFolder("backups")
        for (i in 1..35) {
            assertTrue(File(dir, String.format("kanji_anki_simple_20260515_%06d.db", i)).createNewFile())
        }
        assertTrue(File(dir, "notes.txt").createNewFile())
        assertTrue(File(dir, "kanji_anki_simple_20260515_999999.tmp").createNewFile())

        val names = HashSet<String>()
        for (file in DatabaseBackupPolicy.oldBackupsToPrune(dir)) {
            names.add(file.name)
        }

        assertEquals(4, names.size)
        assertTrue(names.contains("kanji_anki_simple_20260515_000001.db"))
        assertTrue(names.contains("kanji_anki_simple_20260515_000004.db"))
    }

    @Test
    fun oldBackupsToPruneHandlesMissingDirectoryAndShortLists() {
        assertTrue(DatabaseBackupPolicy.oldBackupsToPrune(File(temp.root, "missing")).isEmpty())

        val dir = temp.newFolder("short")
        assertTrue(File(dir, "kanji_anki_simple_20260515_000001.db").createNewFile())
        assertTrue(DatabaseBackupPolicy.oldBackupsToPrune(dir).isEmpty())
    }

    @Test
    fun diagnosticLineDoesNotExposePathsOrExceptionMessages() {
        val error = IOExceptionWithPath(
            "open failed: /data/user/0/dev.bee.kanjianki/databases/kanji_anki_simple.db",
        )

        assertEquals(
            "Database backup failed. Diagnostic: IOExceptionWithPath",
            DatabaseBackupPolicy.sanitizedDiagnosticLine("Database backup failed.", error),
        )
    }

    private class IOExceptionWithPath(message: String) : IOException(message)
}
