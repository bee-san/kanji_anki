package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

internal class ContentValuesBuilder private constructor(
    private val db: SQLiteDatabase,
    private val table: String,
) {
    private val values = ContentValues()

    fun put(key: String, value: String): ContentValuesBuilder = apply {
        values.put(key, value)
    }

    fun put(key: String, value: Int): ContentValuesBuilder = apply {
        values.put(key, value)
    }

    fun put(key: String, value: Long): ContentValuesBuilder = apply {
        values.put(key, value)
    }

    fun put(key: String, value: Double): ContentValuesBuilder = apply {
        values.put(key, value)
    }

    fun commit() {
        db.insertOrThrow(table, null, values)
    }

    companion object {
        @JvmStatic
        fun insert(db: SQLiteDatabase, table: String): ContentValuesBuilder =
            ContentValuesBuilder(db, table)
    }
}
