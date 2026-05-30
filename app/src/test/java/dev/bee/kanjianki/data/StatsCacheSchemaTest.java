package dev.bee.kanjianki.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StatsCacheSchemaTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(LocalStoreSchema.DB_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME);
    }

    @Test
    public void dbVersionIsTwentyTwo() {
        assertEquals(22, LocalStoreSchema.DB_VERSION);
    }

    @Test
    public void createInitialTablesCreatesStatsCacheTables() {
        LocalStore store = new LocalStore(context);

        SQLiteDatabase db = store.getWritableDatabase();

        assertStatsCacheTablesExist(db);
        assertEquals(1L, statsSourceVersion(db));
        store.close();
    }

    @Test
    public void migrationToTwentyTwoCreatesStatsCacheTables() {
        LocalStore store = new LocalStore(context);
        SQLiteDatabase db = SQLiteDatabase.create(null);

        store.onUpgrade(db, 21, 22);

        assertStatsCacheTablesExist(db);
        assertEquals(1L, statsSourceVersion(db));
        db.close();
        store.close();
    }

    private static void assertStatsCacheTablesExist(SQLiteDatabase db) {
        assertTrue(tableExists(db, "stats_cache_state"));
        assertTrue(tableExists(db, "stats_screen_cache"));
    }

    private static boolean tableExists(SQLiteDatabase db, String tableName) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName}
        )) {
            return cursor.moveToFirst();
        }
    }

    private static long statsSourceVersion(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery(
                "SELECT value FROM stats_cache_state WHERE key='stats_source_version'",
                null
        )) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }
}
