package dev.bee.kanjianki.core

import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackupPolicy {
    const val DB_NAME: String = "kanji_anki_simple.db"
    const val BACKUP_DIR: String = "backups"

    /** Most-recent backups kept unconditionally (roughly one per day). */
    const val KEEP_DAILY: Int = 7

    /** Additional older backups kept, thinned to one per calendar week. */
    const val KEEP_WEEKLY: Int = 4

    private const val BACKUP_PREFIX = "kanji_anki_simple_"

    /** Legacy uncompressed backup suffix; still matched so old files are pruned. */
    private const val BACKUP_SUFFIX = ".db"

    /** Current backups are gzip-compressed (SQLite databases compress ~4-10x). */
    private const val COMPRESSED_SUFFIX = ".db.gz"

    private const val TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"
    private const val WEEK_MILLIS = 7L * 86_400_000L

    @JvmStatic
    fun backupDir(filesDir: File): File = File(filesDir, BACKUP_DIR)

    @JvmStatic
    fun backupFile(filesDir: File, nowMillis: Long): File {
        return File(backupDir(filesDir), BACKUP_PREFIX + timestamp(nowMillis) + COMPRESSED_SUFFIX)
    }

    @JvmStatic
    fun timestamp(nowMillis: Long): String {
        return SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(Date(nowMillis))
    }

    /**
     * Tiered retention: keep the newest [KEEP_DAILY] backups, then keep one backup
     * (the newest) per calendar week for up to [KEEP_WEEKLY] additional weeks, and
     * prune everything else. Bounds storage to roughly [KEEP_DAILY] + [KEEP_WEEKLY]
     * files instead of a flat 31 daily copies of a growing database.
     */
    @JvmStatic
    fun oldBackupsToPrune(backupDir: File): List<File> {
        val files = matchingBackups(backupDir) ?: return emptyList()
        if (files.size <= KEEP_DAILY) {
            return emptyList()
        }
        // Newest first by timestamp; entries without a parseable timestamp sort last
        // (treated as oldest) so they are pruned first.
        val sorted = files.sortedByDescending { parseTimestampMillis(it) ?: Long.MIN_VALUE }
        val keep = LinkedHashSet<File>()
        val keptWeeks = LinkedHashSet<Long>()
        for ((index, file) in sorted.withIndex()) {
            if (index < KEEP_DAILY) {
                keep.add(file)
                continue
            }
            if (keptWeeks.size >= KEEP_WEEKLY) {
                continue
            }
            val millis = parseTimestampMillis(file) ?: continue
            val week = millis / WEEK_MILLIS
            if (keptWeeks.add(week)) {
                keep.add(file)
            }
        }
        return sorted.filter { !keep.contains(it) }
    }

    @JvmStatic
    fun sanitizedDiagnosticLine(action: String, error: Throwable): String {
        val type = error.javaClass.simpleName.ifBlank { "Error" }
        return "$action Diagnostic: $type"
    }

    private fun parseTimestampMillis(file: File): Long? {
        val name = file.name
        if (!name.startsWith(BACKUP_PREFIX)) {
            return null
        }
        val stamp = when {
            name.endsWith(COMPRESSED_SUFFIX) ->
                name.substring(BACKUP_PREFIX.length, name.length - COMPRESSED_SUFFIX.length)
            name.endsWith(BACKUP_SUFFIX) ->
                name.substring(BACKUP_PREFIX.length, name.length - BACKUP_SUFFIX.length)
            else -> return null
        }
        val format = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US)
        format.isLenient = false
        return try {
            format.parse(stamp)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private fun matchingBackups(backupDir: File): Array<File>? {
        return backupDir.listFiles { _, name ->
            name.startsWith(BACKUP_PREFIX) && (name.endsWith(BACKUP_SUFFIX) || name.endsWith(COMPRESSED_SUFFIX))
        }
    }
}
