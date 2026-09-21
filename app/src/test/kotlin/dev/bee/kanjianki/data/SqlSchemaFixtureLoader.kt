package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase

/** Loads schema-only sqlite3 dumps used by the retained migration fixtures. */
internal object SqlSchemaFixtureLoader {
    fun load(db: SQLiteDatabase, sql: String) {
        val statements = splitStatements(sql)
        check(statements.isNotEmpty()) { "SQL schema fixture is empty" }
        for (statement in statements) {
            db.execSQL(statement)
        }
    }

    internal fun splitStatements(sql: String): List<String> {
        val statements = ArrayList<String>()
        val current = StringBuilder()
        var singleQuoted = false
        var doubleQuoted = false
        var backtickQuoted = false
        var bracketQuoted = false
        var lineComment = false
        var blockComment = false
        var index = 0
        while (index < sql.length) {
            val character = sql[index]
            val next = sql.getOrNull(index + 1)
            if (lineComment) {
                if (character == '\n') {
                    lineComment = false
                    current.append(character)
                }
                index += 1
                continue
            }
            if (blockComment) {
                if (character == '*' && next == '/') {
                    blockComment = false
                    index += 2
                } else {
                    index += 1
                }
                continue
            }
            if (!singleQuoted && !doubleQuoted && !backtickQuoted && !bracketQuoted) {
                if (character == '-' && next == '-') {
                    lineComment = true
                    index += 2
                    continue
                }
                if (character == '/' && next == '*') {
                    blockComment = true
                    index += 2
                    continue
                }
            }
            when {
                character == '\'' && !doubleQuoted && !backtickQuoted && !bracketQuoted -> {
                    current.append(character)
                    if (singleQuoted && next == '\'') {
                        current.append(next)
                        index += 2
                        continue
                    }
                    singleQuoted = !singleQuoted
                }
                character == '"' && !singleQuoted && !backtickQuoted && !bracketQuoted -> {
                    current.append(character)
                    if (doubleQuoted && next == '"') {
                        current.append(next)
                        index += 2
                        continue
                    }
                    doubleQuoted = !doubleQuoted
                }
                character == '`' && !singleQuoted && !doubleQuoted && !bracketQuoted -> {
                    current.append(character)
                    backtickQuoted = !backtickQuoted
                }
                character == '[' && !singleQuoted && !doubleQuoted && !backtickQuoted -> {
                    current.append(character)
                    bracketQuoted = true
                }
                character == ']' && bracketQuoted -> {
                    current.append(character)
                    bracketQuoted = false
                }
                character == ';' && !singleQuoted && !doubleQuoted && !backtickQuoted && !bracketQuoted -> {
                    val candidate = current.toString().trim()
                    if (candidate.startsWith("CREATE TRIGGER", ignoreCase = true) &&
                        !candidate.endsWith("END", ignoreCase = true)
                    ) {
                        current.append(character)
                    } else {
                        if (candidate.isNotEmpty()) statements += candidate
                        current.clear()
                    }
                }
                else -> current.append(character)
            }
            index += 1
        }
        check(!singleQuoted && !doubleQuoted && !backtickQuoted && !bracketQuoted && !blockComment) {
            "Unterminated quoted value or comment in SQL schema fixture"
        }
        val trailing = current.toString().trim()
        if (trailing.isNotEmpty()) statements += trailing
        return statements
    }
}
