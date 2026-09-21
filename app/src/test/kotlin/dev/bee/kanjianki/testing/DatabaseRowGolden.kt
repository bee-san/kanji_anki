package dev.bee.kanjianki.testing

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.util.Locale

internal object DatabaseRowGolden {
    fun capture(
        db: SQLiteDatabase,
        table: String,
        where: String? = null,
        args: Array<String>? = null,
        orderBy: String? = null,
    ): String {
        val sql = buildString {
            append("SELECT * FROM ").append(quoteIdentifier(table))
            if (!where.isNullOrBlank()) append(" WHERE ").append(where)
            if (!orderBy.isNullOrBlank()) append(" ORDER BY ").append(orderBy)
        }
        return db.rawQuery(sql, args).use { cursor ->
            buildString {
                append("table|").append(table).append("|rows=").appendLine(cursor.count)
                while (cursor.moveToNext()) {
                    append("row")
                    for (index in 0 until cursor.columnCount) {
                        append('|')
                            .append(cursor.getColumnName(index))
                            .append('=')
                            .append(encode(cursor, index))
                    }
                    appendLine()
                }
            }
        }
    }

    private fun encode(cursor: Cursor, index: Int): String {
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> "null"
            Cursor.FIELD_TYPE_INTEGER -> "i:${cursor.getLong(index)}"
            Cursor.FIELD_TYPE_FLOAT -> "f:${canonicalDouble(cursor.getDouble(index))}"
            Cursor.FIELD_TYPE_STRING -> "s:${escape(cursor.getString(index))}"
            Cursor.FIELD_TYPE_BLOB -> "b:${GoldenFixtureResources.sha256(cursor.getBlob(index))}"
            else -> error("Unknown SQLite cursor field type ${cursor.getType(index)}")
        }
    }

    private fun canonicalDouble(value: Double): String {
        return when {
            value.isNaN() -> "nan"
            value == Double.POSITIVE_INFINITY -> "+infinity"
            value == Double.NEGATIVE_INFINITY -> "-infinity"
            else -> String.format(Locale.ROOT, "%.17g", value)
        }
    }

    private fun escape(value: String): String {
        return buildString(value.length) {
            for (character in value) {
                when (character) {
                    '\\' -> append("\\\\")
                    '|' -> append("\\|")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
    }

    private fun quoteIdentifier(identifier: String): String {
        require(identifier.matches(Regex("[a-z0-9_]+"))) { "Unsafe SQLite identifier: $identifier" }
        return "\"$identifier\""
    }
}
