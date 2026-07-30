package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.data.StoreResult

internal suspend fun <T> safeSqlStoreCall(
    block: suspend () -> T,
): StoreResult<T> =
    try {
        StoreResult.ok(block())
    } catch (failure: SqlBusyException) {
        StoreResult.transient(failure)
    } catch (failure: SqlException) {
        StoreResult.permanent(failure)
    } catch (failure: IllegalStateException) {
        StoreResult.permanent(failure)
    }

internal fun SqlSession.executeBound(
    sql: String,
    bind: SqlStatement.() -> Unit = {},
) {
    prepare(sql).use { statement ->
        statement.bind()
        statement.execute()
    }
}

internal fun <T> SqlSession.queryList(
    sql: String,
    bind: SqlStatement.() -> Unit = {},
    map: (SqlRow) -> T,
): List<T> =
    prepare(sql).use { statement ->
        statement.bind()
        statement.query().use { rows ->
            buildList {
                while (rows.next()) {
                    add(map(rows.row))
                }
            }
        }
    }

internal fun <T> SqlSession.queryOneOrNull(
    sql: String,
    bind: SqlStatement.() -> Unit = {},
    map: (SqlRow) -> T,
): T? =
    prepare(sql).use { statement ->
        statement.bind()
        statement.query().use { rows ->
            if (rows.next()) map(rows.row) else null
        }
    }

internal fun SqlRow.textOrEmpty(index: Int): String =
    if (isNull(index)) "" else text(index)

internal fun SqlRow.longOrNull(index: Int): Long? =
    if (isNull(index)) null else long(index)

internal fun SqlRow.doubleOrNull(index: Int): Double? =
    if (isNull(index)) null else double(index)

internal class NamedSqlRow(
    private val row: SqlRow,
) {
    private val indices: Map<String, Int> =
        buildMap(row.columnCount) {
            repeat(row.columnCount) { index ->
                put(row.columnName(index), index)
            }
        }

    fun text(column: String): String =
        index(column)?.let { row.textOrEmpty(it) }.orEmpty()

    fun long(column: String): Long =
        index(column)?.takeUnless(row::isNull)?.let(row::long) ?: 0L

    fun int(column: String): Int = long(column).toInt()

    fun double(column: String): Double =
        index(column)?.takeUnless(row::isNull)?.let(row::double) ?: 0.0

    fun nullableLong(column: String): Long? =
        index(column)?.let(row::longOrNull)

    fun nullableInt(column: String): Int? =
        nullableLong(column)?.toInt()

    fun nullableDouble(column: String): Double? =
        index(column)?.let(row::doubleOrNull)

    private fun index(column: String): Int? = indices[column]
}
