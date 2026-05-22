package dev.bee.kanjianki.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackupPolicy {
    const val DB_NAME: String = "kanji_anki_simple.db"
    const val BACKUP_DIR: String = "backups"
    const val MAX_BACKUPS: Int = 31

    private const val BACKUP_PREFIX = "kanji_anki_simple_"
    private const val BACKUP_SUFFIX = ".db"

    @JvmStatic
    fun backupDir(filesDir: File): File = File(filesDir, BACKUP_DIR)

    @JvmStatic
    fun backupFile(filesDir: File, nowMillis: Long): File {
        return File(backupDir(filesDir), BACKUP_PREFIX + timestamp(nowMillis) + BACKUP_SUFFIX)
    }

    @JvmStatic
    fun timestamp(nowMillis: Long): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
    }

    @JvmStatic
    fun oldBackupsToPrune(backupDir: File): List<File> {
        val files = matchingBackups(backupDir)
        if (files == null || files.size <= MAX_BACKUPS) {
            return emptyList()
        }
        files.sort()
        val toDelete = files.size - MAX_BACKUPS
        val old = ArrayList<File>(toDelete)
        for (index in 0 until toDelete) {
            old.add(files[index])
        }
        return old
    }

    private fun matchingBackups(backupDir: File): Array<File>? {
        return backupDir.listFiles { _, name ->
            name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX)
        }
    }
}
