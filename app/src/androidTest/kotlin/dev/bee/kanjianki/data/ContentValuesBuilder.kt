package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

class ContentValuesBuilder private constructor(
    private val db: SQLiteDatabase,
    private val table: String,
) {
    private val values = ContentValues()

    fun put(key: String, value: String): ContentValuesBuilder {
        values.put(key, value)
        return this
    }

    fun put(key: String, value: Int): ContentValuesBuilder {
        values.put(key, value)
        return this
    }

    fun put(key: String, value: Long): ContentValuesBuilder {
        values.put(key, value)
        return this
    }

    fun put(key: String, value: Double): ContentValuesBuilder {
        values.put(key, value)
        return this
    }

    fun commit() {
        db.insertOrThrow(table, null, values)
    }

    companion object {
        @JvmStatic
        fun insert(db: SQLiteDatabase, table: String): ContentValuesBuilder {
            return ContentValuesBuilder(db, table)
        }
    }
}
