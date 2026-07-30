package dev.bee.kanjianki.data.sql

internal class MigrationRow(
    private val row: SqlRow,
) {
    private val indexes: Map<String, Int> =
        buildMap(row.columnCount) {
            for (index in 0 until row.columnCount) {
                put(row.columnName(index).lowercase(), index)
            }
        }

    fun text(column: String): String {
        val index = index(column) ?: return ""
        return if (row.isNull(index)) "" else row.text(index)
    }

    fun long(column: String): Long {
        val index = index(column) ?: return 0L
        return if (row.isNull(index)) 0L else row.long(index)
    }

    fun int(column: String): Int = long(column).toInt()

    fun nullableInt(column: String): Int? {
        val index = index(column) ?: return null
        return if (row.isNull(index)) null else row.long(index).toInt()
    }

    fun nullableLong(column: String): Long? {
        val index = index(column) ?: return null
        return if (row.isNull(index)) null else row.long(index)
    }

    fun nullableDouble(column: String): Double? {
        val index = index(column) ?: return null
        return if (row.isNull(index)) null else row.double(index)
    }

    fun textAtFirstColumn(): String =
        if (row.isNull(0)) "" else row.text(0)

    fun longAtFirstColumn(): Long =
        if (row.isNull(0)) 0L else row.long(0)

    fun doubleAtFirstColumn(): Double =
        if (row.isNull(0)) 0.0 else row.double(0)

    fun nullableDoubleAtFirstColumn(): Double? =
        if (row.isNull(0)) null else row.double(0)

    private fun index(column: String): Int? = indexes[column.lowercase()]
}

internal fun SqlSession.forEachMigrationRow(
    sql: String,
    bind: SqlStatement.() -> Unit = {},
    consume: (MigrationRow) -> Unit,
) {
    val statement = prepare(sql)
    try {
        statement.bind()
        val rows = statement.query()
        try {
            while (rows.next()) {
                consume(MigrationRow(rows.row))
            }
        } finally {
            rows.close()
        }
    } finally {
        statement.close()
    }
}

internal fun SqlSession.firstMigrationRow(
    sql: String,
    bind: SqlStatement.() -> Unit = {},
): MigrationRow? {
    val statement = prepare(sql)
    try {
        statement.bind()
        val rows = statement.query()
        try {
            return if (rows.next()) MigrationRowSnapshot.capture(rows.row) else null
        } finally {
            rows.close()
        }
    } finally {
        statement.close()
    }
}

internal fun SqlSession.executeMigration(
    sql: String,
    bind: SqlStatement.() -> Unit = {},
) {
    val statement = prepare(sql)
    try {
        statement.bind()
        statement.execute()
    } finally {
        statement.close()
    }
}

internal fun SqlStatement.bindNullableLong(index: Int, value: Long?) {
    if (value == null) {
        bindNull(index)
    } else {
        bindLong(index, value)
    }
}

internal fun SqlStatement.bindNullableDouble(index: Int, value: Double?) {
    if (value == null) {
        bindNull(index)
    } else {
        bindDouble(index, value)
    }
}

private object MigrationRowSnapshot {
    fun capture(row: SqlRow): MigrationRow {
        val values = ArrayList<SnapshotValue>(row.columnCount)
        for (index in 0 until row.columnCount) {
            values += when (row.valueType(index)) {
                SqlValueType.NULL -> SnapshotValue(row.columnName(index), null)
                SqlValueType.INTEGER -> SnapshotValue(row.columnName(index), row.long(index))
                SqlValueType.REAL -> SnapshotValue(row.columnName(index), row.double(index))
                SqlValueType.TEXT -> SnapshotValue(row.columnName(index), row.text(index))
                SqlValueType.BLOB -> SnapshotValue(row.columnName(index), row.blob(index))
            }
        }
        return MigrationRow(SnapshotRow(values))
    }
}

private data class SnapshotValue(
    val name: String,
    val value: Any?,
)

private class SnapshotRow(
    private val values: List<SnapshotValue>,
) : SqlRow {
    override val columnCount: Int
        get() = values.size

    override fun columnName(index: Int): String = values[index].name

    override fun valueType(index: Int): SqlValueType =
        when (values[index].value) {
            null -> SqlValueType.NULL
            is Long -> SqlValueType.INTEGER
            is Double -> SqlValueType.REAL
            is String -> SqlValueType.TEXT
            is ByteArray -> SqlValueType.BLOB
            else -> error("Unsupported migration snapshot value")
        }

    override fun isNull(index: Int): Boolean = values[index].value == null

    override fun text(index: Int): String = values[index].value as String

    override fun long(index: Int): Long = values[index].value as Long

    override fun double(index: Int): Double = values[index].value as Double

    override fun blob(index: Int): ByteArray = (values[index].value as ByteArray).copyOf()
}
