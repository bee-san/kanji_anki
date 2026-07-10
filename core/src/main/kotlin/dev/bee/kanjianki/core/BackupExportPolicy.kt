package dev.bee.kanjianki.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** JVM-pure naming and copy policy for user-exported database backups. */
object BackupExportPolicy {
    enum class CopyId {
        EXPORT_COMPLETE,
        EXPORT_PREPARE_FAILED,
        EXPORT_WRITE_FAILED,
    }

    data class Copy(
        val id: CopyId,
        val text: String,
    )

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun suggestedFileName(nowMillis: Long): String {
        return DatabaseBackupPolicy.backupFile(File("."), nowMillis).name
    }

    @JvmStatic
    fun archiveCountLine(archiveCount: Int): String {
        val safeCount = archiveCount.coerceAtLeast(0)
        return localizedText(
            "$safeCount automatic ${if (safeCount == 1) "backup" else "backups"} kept on this device",
            "この端末に自動バックアップを${safeCount}件保存",
        )
    }

    @JvmStatic
    fun lastBackupLine(lastBackupMillis: Long?): String {
        if (lastBackupMillis == null || lastBackupMillis <= 0L) {
            return localizedText("Last automatic backup: not yet", "最終自動バックアップ: まだ")
        }
        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastBackupMillis))
        return localizedText("Last automatic backup: $formatted", "最終自動バックアップ: $formatted")
    }

    @JvmStatic
    fun exportComplete(sizeBytes: Long): Copy {
        return Copy(
            CopyId.EXPORT_COMPLETE,
            localizedText(
                "Backup exported (${formatSize(sizeBytes)}).",
                "バックアップを書き出しました（${formatSize(sizeBytes)}）。",
            ),
        )
    }

    @JvmStatic
    fun exportPrepareFailed(): Copy {
        return Copy(
            CopyId.EXPORT_PREPARE_FAILED,
            localizedText(
                "Could not create a backup. Your current data was not changed.",
                "バックアップを作成できませんでした。現在のデータは変更されていません。",
            ),
        )
    }

    @JvmStatic
    fun exportWriteFailed(): Copy {
        return Copy(
            CopyId.EXPORT_WRITE_FAILED,
            localizedText(
                "Could not save the backup to that location. Your current data was not changed.",
                "その場所にバックアップを保存できませんでした。現在のデータは変更されていません。",
            ),
        )
    }

    @JvmStatic
    fun panelTitle(): String = localizedText("Backup & restore", "バックアップと復元")

    @JvmStatic
    fun panelBody(): String = localizedText(
        "Export a copy outside Kani, or replace this device's data from a Kani backup.",
        "Kaniの外部にコピーを書き出すか、Kaniのバックアップからこの端末のデータを置き換えます。",
    )

    @JvmStatic
    fun exportNowLabel(): String = localizedText("Export now", "今すぐ書き出す")

    @JvmStatic
    fun restoreFromBackupLabel(): String = localizedText("Restore from backup…", "バックアップから復元…")

    private fun formatSize(sizeBytes: Long): String {
        val safeBytes = sizeBytes.coerceAtLeast(0L)
        if (safeBytes < 1_024L) {
            return "$safeBytes B"
        }
        if (safeBytes < 1_048_576L) {
            return String.format(Locale.ROOT, "%.1f KB", safeBytes / 1_024.0)
        }
        return String.format(Locale.ROOT, "%.1f MB", safeBytes / 1_048_576.0)
    }

    private fun localizedText(english: String, japanese: String): String {
        return if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
    }
}
