package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Locale;

final class SettingsRepository {
    private final LocalStoreBase store;

    SettingsRepository(LocalStoreBase store) {
        this.store = store;
    }

    int getInt(String key, int fallback) {
        try (Cursor cursor = settingCursor(key)) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            return SettingValueParser.parseInt(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE), fallback);
        }
    }

    long getLong(String key, long fallback) {
        try (Cursor cursor = settingCursor(key)) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            return SettingValueParser.parseLong(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE), fallback);
        }
    }

    String getString(String key, String fallback) {
        try (Cursor cursor = settingCursor(key)) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            String value = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE);
            return value == null ? fallback : value;
        }
    }

    double getDouble(String key, double fallback) {
        try (Cursor cursor = settingCursor(key)) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            return SettingValueParser.parseDouble(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE), fallback);
        }
    }

    void putInt(String key, int value) {
        put(key, Integer.toString(value));
    }

    void putLong(String key, long value) {
        put(key, Long.toString(value));
    }

    void putString(String key, String value) {
        put(key, value == null ? "" : value);
    }

    void putDouble(String key, double value) {
        put(key, String.format(Locale.ROOT, "%.4f", value));
    }

    void put(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put(LocalStoreBase.COLUMN_VALUE, value);
        values.put(LocalStoreBase.COLUMN_UPDATED_AT, System.currentTimeMillis());
        store.getWritableDatabase().insertWithOnConflict(LocalStoreBase.TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private Cursor settingCursor(String key) {
        return store.getReadableDatabase().query(
                LocalStoreBase.TABLE_SETTINGS,
                new String[]{LocalStoreBase.COLUMN_VALUE},
                LocalStoreBase.WHERE_SETTING_KEY,
                new String[]{key},
                null,
                null,
                null,
                "1"
        );
    }
}
