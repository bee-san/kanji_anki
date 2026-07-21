package dev.bee.kanjianki.core

import java.io.File
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        val zone = TimeZone.getDefault()
        val parsedByFile = files.associateWith { parseTimestamp(it, zone) }
        // Newest first by timestamp; entries without a parseable timestamp sort last
        // (treated as oldest) so they are pruned first.
        val sorted = files.sortedByDescending { parsedByFile[it]?.millis ?: Long.MIN_VALUE }
        val keep = LinkedHashSet<File>()
        val keptWeeks = LinkedHashSet<CalendarWeek>()
        for ((index, file) in sorted.withIndex()) {
            if (index < KEEP_DAILY) {
                keep.add(file)
                continue
            }
            if (keptWeeks.size >= KEEP_WEEKLY) {
                continue
            }
            val parsed = parsedByFile[file] ?: continue
            if (keptWeeks.add(parsed.week)) {
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

    private fun parseTimestamp(file: File, zone: TimeZone): ParsedTimestamp? {
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
        if (!hasTimestampShape(stamp)) {
            return null
        }
        val format = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US)
        format.isLenient = false
        format.timeZone = zone
        format.calendar.firstDayOfWeek = Calendar.MONDAY
        format.calendar.minimalDaysInFirstWeek = ISO_MINIMAL_DAYS_IN_FIRST_WEEK
        val position = ParsePosition(0)
        val parsed = format.parse(stamp, position) ?: return null
        if (position.index != stamp.length) {
            return null
        }
        return ParsedTimestamp(
            parsed.time,
            CalendarWeek(
                format.calendar.weekYear,
                format.calendar.get(Calendar.WEEK_OF_YEAR),
            ),
        )
    }

    private fun hasTimestampShape(stamp: String): Boolean {
        if (stamp.length != TIMESTAMP_PATTERN.length || stamp[8] != '_') {
            return false
        }
        return stamp.indices.all { index ->
            index == 8 || stamp[index] in '0'..'9'
        }
    }

    private fun matchingBackups(backupDir: File): Array<File>? {
        return backupDir.listFiles { _, name ->
            name.startsWith(BACKUP_PREFIX) && (name.endsWith(BACKUP_SUFFIX) || name.endsWith(COMPRESSED_SUFFIX))
        }
    }

    private data class ParsedTimestamp(val millis: Long, val week: CalendarWeek)
    private data class CalendarWeek(val year: Int, val week: Int)

    private const val ISO_MINIMAL_DAYS_IN_FIRST_WEEK = 4
}
