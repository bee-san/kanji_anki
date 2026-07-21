package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.SimpleTimeZone
import java.util.TimeZone

class DatabaseBackupPolicyTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun backupFileUsesStableDirectoryPrefixCompressedSuffixAndTimestamp() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val filesDir = temp.newFolder("files")
            val now = 1_778_832_000_000L

            assertEquals(File(filesDir, "backups"), DatabaseBackupPolicy.backupDir(filesDir))
            assertEquals("20260515_080000", DatabaseBackupPolicy.timestamp(now))
            assertEquals(
                File(File(filesDir, "backups"), "kanji_anki_simple_20260515_080000.db.gz"),
                DatabaseBackupPolicy.backupFile(filesDir, now),
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun oldBackupsToPruneKeepsSevenDailyPlusFourWeeklyCompressedBackups() {
        val dir = temp.newFolder("backups")
        // 40 daily backups across March-April 2026.
        val stamps = ArrayList<String>()
        for (day in 1..31) {
            stamps.add(String.format("202603%02d_120000", day))
        }
        for (day in 1..9) {
            stamps.add(String.format("202604%02d_120000", day))
        }
        for (stamp in stamps) {
            assertTrue(File(dir, "kanji_anki_simple_$stamp.db.gz").createNewFile())
        }
        assertTrue(File(dir, "notes.txt").createNewFile())

        val prunedNames = DatabaseBackupPolicy.oldBackupsToPrune(dir).map { it.name }.toSet()
        val keptNames = stamps
            .map { "kanji_anki_simple_$it.db.gz" }
            .filter { !prunedNames.contains(it) }

        // 7 daily + up to 4 weekly.
        assertTrue("kept ${keptNames.size}", keptNames.size in 8..11)
        // Newest 7 are always kept.
        assertTrue(keptNames.contains("kanji_anki_simple_20260409_120000.db.gz"))
        assertTrue(keptNames.contains("kanji_anki_simple_20260403_120000.db.gz"))
        // Oldest is pruned.
        assertTrue(prunedNames.contains("kanji_anki_simple_20260301_120000.db.gz"))
        // Non-backup files are never pruned.
        assertFalse(prunedNames.contains("notes.txt"))
    }

    @Test
    fun oldBackupsToPrunePrunesLegacyUncompressedBackupsFirst() {
        val dir = temp.newFolder("backups")
        for (day in 1..10) {
            assertTrue(File(dir, String.format("kanji_anki_simple_202603%02d_120000.db", day)).createNewFile())
        }

        val pruned = DatabaseBackupPolicy.oldBackupsToPrune(dir).map { it.name }.toSet()
        // Legacy .db files are matched and subject to tiered pruning.
        assertTrue(pruned.isNotEmpty())
        assertTrue(pruned.contains("kanji_anki_simple_20260302_120000.db"))
        assertFalse(pruned.contains("kanji_anki_simple_20260301_120000.db"))
        assertFalse(pruned.contains("kanji_anki_simple_20260310_120000.db"))
    }

    @Test
    fun oldBackupsToPruneUsesIsoCalendarWeeksInsteadOfEpochBuckets() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val dir = temp.newFolder("iso-weeks")
            val stamps = listOf(
                "20260115_120000",
                "20260114_120000",
                "20260113_120000",
                "20260112_120000",
                "20260111_120000",
                "20260110_120000",
                "20260109_120000",
                "20260108_120000",
                "20260105_120000",
            )
            for (stamp in stamps) {
                assertTrue(File(dir, "kanji_anki_simple_$stamp.db.gz").createNewFile())
            }

            val pruned = DatabaseBackupPolicy.oldBackupsToPrune(dir).map { it.name }.toSet()

            assertFalse(pruned.contains("kanji_anki_simple_20260108_120000.db.gz"))
            assertTrue(pruned.contains("kanji_anki_simple_20260105_120000.db.gz"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun oldBackupsToPruneSupportsUnknownCustomDefaultTimeZoneIds() {
        withDefaultTimeZone(SimpleTimeZone(0, "Kani/Unknown")) {
            assertTrue(DatabaseBackupPolicy.oldBackupsToPrune(createWeekBoundaryBackups("unknown-zone")).isEmpty())
        }
    }

    @Test
    fun oldBackupsToPruneUsesCustomDefaultRulesInsteadOfKnownIdRules() {
        withDefaultTimeZone(SimpleTimeZone(0, "Asia/Tokyo")) {
            assertTrue(DatabaseBackupPolicy.oldBackupsToPrune(createWeekBoundaryBackups("misleading-zone")).isEmpty())
        }
    }

    @Test
    fun oldBackupsToPruneIgnoresNonBackupAndUnparseableNames() {
        val dir = temp.newFolder("mixed")
        // 8 valid daily backups so pruning engages (> KEEP_DAILY).
        for (day in 1..8) {
            assertTrue(File(dir, String.format("kanji_anki_simple_202603%02d_120000.db.gz", day)).createNewFile())
        }
        // A matching prefix/suffix but an unparseable timestamp (parse returns null).
        assertTrue(File(dir, "kanji_anki_simple_not-a-timestamp.db.gz").createNewFile())
        assertTrue(File(dir, "kanji_anki_simple_20260301_120000junk.db.gz").createNewFile())
        // A file that does not start with the backup prefix (filtered out).
        assertTrue(File(dir, "unrelated_20260301_120000.db.gz").createNewFile())
        // Prefixed but neither .db nor .db.gz suffix: matched by neither branch, so
        // parseTimestampMillis returns null via the else path (and matchingBackups
        // filters it out of candidates anyway).
        assertTrue(File(dir, "kanji_anki_simple_20260301_120000.tmp").createNewFile())

        val pruned = DatabaseBackupPolicy.oldBackupsToPrune(dir).map { it.name }.toSet()

        // The unparseable-timestamp backup sorts oldest and is pruned first.
        assertTrue(pruned.contains("kanji_anki_simple_not-a-timestamp.db.gz"))
        assertTrue(pruned.contains("kanji_anki_simple_20260301_120000junk.db.gz"))
        // The non-prefixed file is never a backup candidate.
        assertFalse(pruned.contains("unrelated_20260301_120000.db.gz"))
    }

    @Test
    fun oldBackupsToPruneRejectsVariableWidthDateAndTimeFields() {
        val dir = temp.newFolder("variable-width")
        for (day in 1..7) {
            assertTrue(File(dir, String.format("kanji_anki_simple_202601%02d_120000.db.gz", day)).createNewFile())
        }
        val shortDate = "kanji_anki_simple_2026031_120000.db.gz"
        val shortTime = "kanji_anki_simple_20260301_12000.db.gz"
        assertTrue(File(dir, shortDate).createNewFile())
        assertTrue(File(dir, shortTime).createNewFile())

        val pruned = DatabaseBackupPolicy.oldBackupsToPrune(dir).map { it.name }.toSet()

        assertTrue(pruned.contains(shortDate))
        assertTrue(pruned.contains(shortTime))
    }

    @Test
    fun oldBackupsToPruneHandlesMissingDirectoryAndShortLists() {
        assertTrue(DatabaseBackupPolicy.oldBackupsToPrune(File(temp.root, "missing")).isEmpty())

        val dir = temp.newFolder("short")
        for (day in 1..7) {
            assertTrue(File(dir, String.format("kanji_anki_simple_202603%02d_120000.db.gz", day)).createNewFile())
        }
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

    private fun createWeekBoundaryBackups(directoryName: String): File {
        val dir = temp.newFolder(directoryName)
        val stamps = listOf(
            "20260112_120000",
            "20260111_120000",
            "20260110_120000",
            "20260109_120000",
            "20260108_120000",
            "20260107_120000",
            "20260106_120000",
            "20260105_003000",
            "20260104_200000",
        )
        for (stamp in stamps) {
            assertTrue(File(dir, "kanji_anki_simple_$stamp.db.gz").createNewFile())
        }
        return dir
    }

    private fun withDefaultTimeZone(zone: TimeZone, body: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(zone)
            body()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private class IOExceptionWithPath(message: String) : IOException(message)
}
