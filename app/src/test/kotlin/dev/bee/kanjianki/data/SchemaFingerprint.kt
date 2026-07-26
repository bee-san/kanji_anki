package dev.bee.kanjianki.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.testing.GoldenFixtureResources
import java.util.Locale

/**
 * Semantic SQLite schema fingerprint used by the Goal 165 conversion fixtures.
 *
 * SQLite preserves the spelling of historical CREATE statements in
 * sqlite_master, so harmless whitespace and `IF NOT EXISTS` differences are
 * normalized. Column, index, and foreign-key pragmas remain authoritative and
 * are emitted separately. Internal object names are excluded; automatic
 * indexes are identified by origin and indexed columns instead of SQLite's
 * generated name.
 */
internal object SchemaFingerprint {
    data class Snapshot(
        val canonical: String,
        val sha256: String,
        val structuralCanonical: String,
        val structuralSha256: String,
        val userVersion: Int,
        val tableNames: List<String>,
        val indexNames: List<String>,
        val triggerNames: List<String>,
        val settingsRowCount: Int,
        val statsSourceVersion: Long?,
        val statsCacheFormatVersion: Int,
    )

    fun capture(db: SQLiteDatabase): Snapshot {
        val schemaRows = schemaRows(db)
        val tables = schemaRows.filter { it.type == "table" }.map { it.name }
        val explicitIndexes = schemaRows.filter { it.type == "index" }.map { it.name }
        val triggers = schemaRows.filter { it.type == "trigger" }.map { it.name }
        val canonical = buildString {
            appendLine("fingerprint_format=1")
            appendLine("user_version=${db.version}")
            for (row in schemaRows) {
                append("schema|")
                    .append(row.type)
                    .append('|')
                    .append(row.name)
                    .append('|')
                    .append(row.table)
                    .append('|')
                    .appendLine(normalizeSql(row.sql))
            }
            for (table in tables) {
                appendTableDetails(db, table)
            }
            appendLine("seed|settings_rows=${scalarLong(db, "SELECT COUNT(*) FROM settings")}")
            appendLine(
                "seed|stats_source_version=" +
                    (statsSourceVersion(db)?.toString() ?: "<missing>"),
            )
            appendLine("contract|stats_cache_format_version=$STATS_CACHE_FORMAT_VERSION")
        }
        val structuralCanonical = structuralCanonical(canonical)
        return Snapshot(
            canonical = canonical,
            sha256 = GoldenFixtureResources.sha256(canonical),
            structuralCanonical = structuralCanonical,
            structuralSha256 = GoldenFixtureResources.sha256(structuralCanonical),
            userVersion = db.version,
            tableNames = tables,
            indexNames = explicitIndexes,
            triggerNames = triggers,
            settingsRowCount = scalarLong(db, "SELECT COUNT(*) FROM settings").toInt(),
            statsSourceVersion = statsSourceVersion(db),
            statsCacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
        )
    }

    private fun StringBuilder.appendTableDetails(db: SQLiteDatabase, table: String) {
        db.rawQuery("PRAGMA table_xinfo(${quoteSqlString(table)})", null).use { cursor ->
            while (cursor.moveToNext()) {
                append("column|")
                    .append(table)
                    .append('|')
                    .append(cursor.int("cid"))
                    .append('|')
                    .append(cursor.string("name"))
                    .append('|')
                    .append(cursor.string("type").uppercase(Locale.ROOT))
                    .append("|not_null=")
                    .append(cursor.int("notnull"))
                    .append("|default=")
                    .append(normalizeDefault(cursor.nullableString("dflt_value")))
                    .append("|pk=")
                    .append(cursor.int("pk"))
                    .append("|hidden=")
                    .appendLine(cursor.optionalInt("hidden", 0))
            }
        }

        val indexes = ArrayList<IndexRow>()
        db.rawQuery("PRAGMA index_list(${quoteSqlString(table)})", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.string("name")
                val columns = indexColumns(db, name)
                val origin = cursor.optionalString("origin", "c")
                val stableName = if (name.startsWith("sqlite_autoindex_")) {
                    "auto:$origin:${columns.filter { it.key }.joinToString(",") { it.name ?: "<rowid>" }}"
                } else {
                    name
                }
                indexes += IndexRow(
                    stableName,
                    cursor.int("unique"),
                    origin,
                    cursor.optionalInt("partial", 0),
                    columns,
                )
            }
        }
        for (index in indexes.sortedBy { it.stableName }) {
            append("index|")
                .append(table)
                .append('|')
                .append(index.stableName)
                .append("|unique=")
                .append(index.unique)
                .append("|origin=")
                .append(index.origin)
                .append("|partial=")
                .appendLine(index.partial)
            for (column in index.columns) {
                append("index_column|")
                    .append(table)
                    .append('|')
                    .append(index.stableName)
                    .append('|')
                    .append(column.sequence)
                    .append("|cid=")
                    .append(column.cid)
                    .append("|name=")
                    .append(column.name ?: "<rowid>")
                    .append("|desc=")
                    .append(column.descending)
                    .append("|collation=")
                    .append(column.collation)
                    .append("|key=")
                    .appendLine(if (column.key) 1 else 0)
            }
        }

        db.rawQuery("PRAGMA foreign_key_list(${quoteSqlString(table)})", null).use { cursor ->
            val rows = ArrayList<String>()
            while (cursor.moveToNext()) {
                rows += buildString {
                    append(cursor.int("id"))
                    append('|')
                    append(cursor.int("seq"))
                    append("|table=")
                    append(cursor.string("table"))
                    append("|from=")
                    append(cursor.string("from"))
                    append("|to=")
                    append(cursor.nullableString("to") ?: "<implicit>")
                    append("|on_update=")
                    append(cursor.string("on_update"))
                    append("|on_delete=")
                    append(cursor.string("on_delete"))
                    append("|match=")
                    append(cursor.string("match"))
                }
            }
            for (row in rows.sorted()) {
                append("foreign_key|").append(table).append('|').appendLine(row)
            }
        }
    }

    private fun indexColumns(db: SQLiteDatabase, index: String): List<IndexColumn> {
        val columns = ArrayList<IndexColumn>()
        db.rawQuery("PRAGMA index_xinfo(${quoteSqlString(index)})", null).use { cursor ->
            while (cursor.moveToNext()) {
                columns += IndexColumn(
                    sequence = cursor.int("seqno"),
                    cid = cursor.int("cid"),
                    name = cursor.nullableString("name"),
                    descending = cursor.optionalInt("desc", 0),
                    collation = cursor.optionalString("coll", "BINARY"),
                    key = cursor.optionalInt("key", 1) != 0,
                )
            }
        }
        return columns.sortedBy { it.sequence }
    }

    private fun schemaRows(db: SQLiteDatabase): List<SchemaRow> {
        val rows = ArrayList<SchemaRow>()
        db.rawQuery(
            "SELECT type, name, tbl_name, sql FROM sqlite_master " +
                "WHERE name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' AND sql IS NOT NULL " +
                "ORDER BY type, name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += SchemaRow(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                )
            }
        }
        return rows
    }

    private fun statsSourceVersion(db: SQLiteDatabase): Long? {
        db.rawQuery(
            "SELECT value FROM stats_cache_state WHERE key=?",
            arrayOf(LocalStoreBase.STATS_CACHE_SOURCE_VERSION_KEY),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun scalarLong(db: SQLiteDatabase, sql: String): Long {
        db.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst()) { "Scalar query returned no row: $sql" }
            return cursor.getLong(0)
        }
    }

    internal fun normalizeSql(sql: String): String {
        return normalizeSqlSyntax(sql.trim())
            .replace(Regex("\\bif not exists\\s+"), "")
    }

    private fun normalizeDefault(value: String?): String {
        if (value == null) return "<none>"
        return normalizeSqlSyntax(value.trim())
    }

    /**
     * Lowercases SQLite's case-insensitive syntax and normalizes spacing while
     * retaining the exact content of single-quoted default/check literals.
     */
    private fun normalizeSqlSyntax(value: String): String {
        val normalized = StringBuilder(value.length)
        var singleQuoted = false
        var pendingSpace = false
        var index = 0
        while (index < value.length) {
            val character = value[index]
            val next = value.getOrNull(index + 1)
            if (singleQuoted) {
                normalized.append(character)
                if (character == '\'' && next == '\'') {
                    normalized.append(next)
                    index += 2
                    continue
                }
                if (character == '\'') singleQuoted = false
                index += 1
                continue
            }
            if (character == '\'') {
                appendPendingSpace(normalized, pendingSpace, character)
                pendingSpace = false
                normalized.append(character)
                singleQuoted = true
            } else if (character.isWhitespace()) {
                pendingSpace = normalized.isNotEmpty()
            } else {
                if (character in SQL_PUNCTUATION) {
                    while (normalized.lastOrNull() == ' ') normalized.setLength(normalized.length - 1)
                } else {
                    appendPendingSpace(normalized, pendingSpace, character)
                }
                pendingSpace = false
                normalized.append(character.lowercaseChar())
            }
            index += 1
        }
        return normalized.toString()
    }

    private fun appendPendingSpace(
        output: StringBuilder,
        pending: Boolean,
        next: Char,
    ) {
        if (!pending || output.isEmpty()) return
        if (output.last() !in SQL_PUNCTUATION && next !in SQL_PUNCTUATION) {
            output.append(' ')
        }
    }

    private fun quoteSqlString(value: String): String = "'" + value.replace("'", "''") + "'"

    private fun structuralCanonical(exact: String): String {
        val lines = exact.lineSequence()
            .filter(String::isNotEmpty)
            .mapNotNull { line ->
                when {
                    line.startsWith("schema|table|") -> null
                    line.startsWith("column|") -> {
                        line.replaceFirst(Regex("^(column\\|[^|]+)\\|[0-9]+\\|"), "$1|")
                    }
                    line.startsWith("index_column|") -> {
                        line.replace(Regex("\\|cid=-?[0-9]+"), "")
                    }
                    else -> line
                }
            }
            .sorted()
            .toList()
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

    private fun Cursor.nullableString(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.optionalInt(column: String, fallback: Int): Int {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) fallback else getInt(index)
    }

    private fun Cursor.optionalString(column: String, fallback: String): String {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) fallback else getString(index)
    }

    private data class SchemaRow(
        val type: String,
        val name: String,
        val table: String,
        val sql: String,
    )

    private data class IndexRow(
        val stableName: String,
        val unique: Int,
        val origin: String,
        val partial: Int,
        val columns: List<IndexColumn>,
    )

    private data class IndexColumn(
        val sequence: Int,
        val cid: Int,
        val name: String?,
        val descending: Int,
        val collation: String,
        val key: Boolean,
    )

    private val SQL_PUNCTUATION = setOf('(', ')', ',', '=')
}

internal object SchemaGoldenVerifier {
    fun assertEquivalent(
        expected: SchemaFingerprint.Snapshot,
        actual: SchemaFingerprint.Snapshot,
        label: String,
    ) {
        if (expected.canonical == actual.canonical) return
        val expectedLines = expected.canonical.lines()
        val actualLines = actual.canonical.lines()
        val differingIndex = (0 until maxOf(expectedLines.size, actualLines.size))
            .firstOrNull { index -> expectedLines.getOrNull(index) != actualLines.getOrNull(index) }
        val detail = if (differingIndex == null) {
            "canonical text differs"
        } else {
            "first difference at line ${differingIndex + 1}: " +
                "expected <${expectedLines.getOrNull(differingIndex) ?: "<missing>"}> " +
                "but was <${actualLines.getOrNull(differingIndex) ?: "<missing>"}>"
        }
        throw AssertionError(
            "$label schema fingerprint mismatch: $detail; " +
                "expected sha256=${expected.sha256}, actual sha256=${actual.sha256}",
        )
    }

    fun assertDigest(expectedSha256: String, actual: SchemaFingerprint.Snapshot, label: String) {
        if (expectedSha256 == actual.sha256) return
        throw AssertionError(
            "$label schema digest mismatch: expected $expectedSha256 but was ${actual.sha256}",
        )
    }

    fun assertStructurallyEquivalent(
        expected: SchemaFingerprint.Snapshot,
        actual: SchemaFingerprint.Snapshot,
        label: String,
    ) {
        if (expected.structuralCanonical == actual.structuralCanonical) return
        val expectedLines = expected.structuralCanonical.lines()
        val actualLines = actual.structuralCanonical.lines()
        val differingIndex = (0 until maxOf(expectedLines.size, actualLines.size))
            .firstOrNull { index -> expectedLines.getOrNull(index) != actualLines.getOrNull(index) }
        val detail = if (differingIndex == null) {
            "structural text differs"
        } else {
            "first difference at line ${differingIndex + 1}: " +
                "expected <${expectedLines.getOrNull(differingIndex) ?: "<missing>"}> " +
                "but was <${actualLines.getOrNull(differingIndex) ?: "<missing>"}>"
        }
        throw AssertionError(
            "$label structural schema mismatch: $detail; " +
                "expected sha256=${expected.structuralSha256}, " +
                "actual sha256=${actual.structuralSha256}",
        )
    }
}
