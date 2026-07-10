package dev.bee.kanjianki.core

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExportPolicyTest {
    @Test
    fun suggestsTimestampedGzipNameAndBuildsStatusLines() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            assertEquals(
                "kanji_anki_simple_20260515_080000.db.gz",
                BackupExportPolicy.suggestedFileName(1_778_832_000_000L),
            )
            assertEquals("0 automatic backups kept on this device", BackupExportPolicy.archiveCountLine(0))
            assertEquals("1 automatic backup kept on this device", BackupExportPolicy.archiveCountLine(1))
            assertEquals("Last automatic backup: not yet", BackupExportPolicy.lastBackupLine(null))
            assertEquals(
                "Last automatic backup: 2026-05-15 08:00",
                BackupExportPolicy.lastBackupLine(1_778_832_000_000L),
            )
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun reportsSizesAndDistinctFailureCopy() {
        assertEquals(
            BackupExportPolicy.Copy(BackupExportPolicy.CopyId.EXPORT_COMPLETE, "Backup exported (1.5 KB)."),
            BackupExportPolicy.exportComplete(1_536L),
        )
        assertEquals(
            BackupExportPolicy.CopyId.EXPORT_PREPARE_FAILED,
            BackupExportPolicy.exportPrepareFailed().id,
        )
        assertEquals(
            BackupExportPolicy.CopyId.EXPORT_WRITE_FAILED,
            BackupExportPolicy.exportWriteFailed().id,
        )
        assertEquals("Backup & restore", BackupExportPolicy.panelTitle())
        assertEquals("Export now", BackupExportPolicy.exportNowLabel())
        assertEquals("Restore from backup…", BackupExportPolicy.restoreFromBackupLabel())
    }

    @Test
    fun userCopyIsLocalizedInJapanese() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("バックアップと復元", BackupExportPolicy.panelTitle())
            assertEquals("この端末に自動バックアップを2件保存", BackupExportPolicy.archiveCountLine(2))
            assertEquals(
                "その場所にバックアップを保存できませんでした。現在のデータは変更されていません。",
                BackupExportPolicy.exportWriteFailed().text,
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
