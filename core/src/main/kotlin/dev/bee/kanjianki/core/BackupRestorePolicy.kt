package dev.bee.kanjianki.core

import java.util.Locale

/** Fail-closed validation decisions and user copy for staged database restore. */
object BackupRestorePolicy {
    enum class CopyId {
        READY,
        TRUNCATED_GZIP,
        BACKUP_TOO_LARGE,
        INSUFFICIENT_STORAGE,
        BAD_SQLITE_MAGIC,
        NEWER_DATABASE_VERSION,
        MISSING_SETTINGS_TABLE,
        QUICK_CHECK_FAILED,
    }

    data class ValidationFacts(
        val gzipReadable: Boolean = true,
        val sqliteMagicPresent: Boolean = true,
        val userVersion: Int = 0,
        val settingsTablePresent: Boolean = true,
        val quickCheckOk: Boolean = true,
    )

    data class ValidationResult(
        val copyId: CopyId,
        val accepted: Boolean,
        val message: String,
    )

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun validate(facts: ValidationFacts, supportedVersion: Int): ValidationResult {
        return when {
            !facts.gzipReadable -> rejected(CopyId.TRUNCATED_GZIP)
            !facts.sqliteMagicPresent -> rejected(CopyId.BAD_SQLITE_MAGIC)
            facts.userVersion > supportedVersion -> rejected(CopyId.NEWER_DATABASE_VERSION)
            !facts.quickCheckOk -> rejected(CopyId.QUICK_CHECK_FAILED)
            !facts.settingsTablePresent -> rejected(CopyId.MISSING_SETTINGS_TABLE)
            else -> ValidationResult(CopyId.READY, true, readyMessage())
        }
    }

    @JvmStatic
    fun rejection(copyId: CopyId): ValidationResult {
        require(copyId != CopyId.READY) { "READY is not a rejection" }
        return rejected(copyId)
    }

    @JvmStatic
    fun restoreAllowed(syncRunning: Boolean): Boolean = !syncRunning

    @JvmStatic
    fun panelBlockedBySyncMessage(): String = localizedText(
        "Wait for the current AnkiDroid sync to finish before restoring.",
        "現在のAnkiDroid同期が終わってから復元してください。",
    )

    @JvmStatic
    fun confirmTitle(): String = localizedText("Restore this backup?", "このバックアップを復元しますか？")

    @JvmStatic
    fun confirmMessage(): String = localizedText(
        "Restore replaces all current data on this device. Kani will close and apply the backup on next launch.",
        "復元すると、この端末の現在のデータがすべて置き換わります。Kaniは終了し、次回起動時にバックアップを適用します。",
    )

    @JvmStatic
    fun confirmLabel(): String = localizedText("Restore and close Kani", "復元してKaniを終了")

    @JvmStatic
    fun cancelLabel(): String = localizedText("Cancel", "キャンセル")

    @JvmStatic
    fun stagingFailedMessage(): String = localizedText(
        "Could not stage the backup. Your current data was not changed.",
        "バックアップを準備できませんでした。現在のデータは変更されていません。",
    )

    private fun rejected(copyId: CopyId): ValidationResult {
        val message = when (copyId) {
            CopyId.TRUNCATED_GZIP, CopyId.QUICK_CHECK_FAILED -> localizedText(
                "File is corrupted.",
                "ファイルが破損しています。",
            )
            CopyId.BACKUP_TOO_LARGE -> localizedText(
                "Backup is too large to restore.",
                "バックアップが大きすぎるため復元できません。",
            )
            CopyId.INSUFFICIENT_STORAGE -> localizedText(
                "Not enough free space to restore this backup.",
                "このバックアップを復元するための空き容量が不足しています。",
            )
            CopyId.BAD_SQLITE_MAGIC, CopyId.MISSING_SETTINGS_TABLE -> localizedText(
                "Not a Kani backup.",
                "Kaniのバックアップではありません。",
            )
            CopyId.NEWER_DATABASE_VERSION -> localizedText(
                "This backup is from a newer version of Kani.",
                "このバックアップは新しいバージョンのKaniで作成されています。",
            )
            CopyId.READY -> throw IllegalArgumentException("READY is not a rejection")
        }
        return ValidationResult(copyId, false, message)
    }

    private fun readyMessage(): String = localizedText(
        "Backup is ready to restore.",
        "バックアップを復元できます。",
    )

    private fun localizedText(english: String, japanese: String): String {
        return if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
    }
}
