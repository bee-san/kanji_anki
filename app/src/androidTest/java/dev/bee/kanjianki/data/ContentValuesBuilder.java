package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

final class ContentValuesBuilder {
    private final SQLiteDatabase db;
    private final String table;
    private final ContentValues values = new ContentValues();

    private ContentValuesBuilder(SQLiteDatabase db, String table) {
        this.db = db;
        this.table = table;
    }

    static ContentValuesBuilder insert(SQLiteDatabase db, String table) {
        return new ContentValuesBuilder(db, table);
    }

    ContentValuesBuilder put(String key, String value) {
        values.put(key, value);
        return this;
    }

    ContentValuesBuilder put(String key, int value) {
        values.put(key, value);
        return this;
    }

    ContentValuesBuilder put(String key, long value) {
        values.put(key, value);
        return this;
    }

    ContentValuesBuilder put(String key, double value) {
        values.put(key, value);
        return this;
    }

    void commit() {
        db.insertOrThrow(table, null, values);
    }
}
