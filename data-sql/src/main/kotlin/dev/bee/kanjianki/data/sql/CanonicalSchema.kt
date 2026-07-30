package dev.bee.kanjianki.data.sql

import java.nio.charset.StandardCharsets

internal object CanonicalSchema {
    const val DATABASE_NAME: String = "kanji_anki_simple.db"
    const val VERSION: Int = 34

    private const val RESOURCE = "/dev/bee/kanjianki/data/sql/schema-v34.sql"

    val statements: List<String> by lazy {
        val stream = checkNotNull(CanonicalSchema::class.java.getResourceAsStream(RESOURCE)) {
            "Missing canonical schema resource $RESOURCE"
        }
        stream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("--") }
                .map { line ->
                    require(line.endsWith(';')) {
                        "Canonical schema statements must occupy one complete line: $line"
                    }
                    line.dropLast(1)
                }
                .toList()
        }
    }

    val creationStatements: List<String>
        get() = statements.filterNot { it.startsWith("PRAGMA user_version", ignoreCase = true) }

    fun createTable(table: String): String =
        findStatement("CREATE TABLE $table ")
            .replaceFirst("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ")

    fun createIndex(index: String): String {
        val statement = statements.firstOrNull { candidate ->
            candidate.startsWith("CREATE INDEX $index ") ||
                candidate.startsWith("CREATE UNIQUE INDEX $index ")
        } ?: error("Canonical schema has no index $index")
        return statement
            .replaceFirst("CREATE INDEX ", "CREATE INDEX IF NOT EXISTS ")
            .replaceFirst("CREATE UNIQUE INDEX ", "CREATE UNIQUE INDEX IF NOT EXISTS ")
    }

    private fun findStatement(prefix: String): String =
        statements.firstOrNull { it.startsWith(prefix) }
            ?: error("Canonical schema has no statement starting with $prefix")
}
