package dev.bee.kanjianki

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.testing.DeviceSmoke
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the process-store/database-deletion contract.
 *
 * The process container caches one [dev.bee.kanjianki.data.LocalStore] for the whole
 * instrumentation run, while tests reset state by deleting the database file. A cached
 * `SQLiteOpenHelper` keeps its connection pool across that deletion and reopens the
 * unlinked path as an empty database without rerunning `onCreate`, so every later query
 * fails with `no such table`. [KaniTestDatabase.delete] detaches the store; this test
 * fails if that detach is lost.
 */
@RunWith(AndroidJUnit4::class)
@DeviceSmoke
class ProcessStoreDatabaseResetInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        KaniTestDatabase.delete(context)
    }

    @After
    fun tearDown() {
        KaniTestDatabase.delete(context)
    }

    @Test
    fun processStoreServesFreshSchemaAfterDatabaseDeletion() {
        val store = (context.applicationContext as KaniApplication).container.localStore

        // Materialize the schema and a row through the process-cached helper.
        store.saveKanjiMnemonicNote("裂", "before deletion", 1_000L)
        assertEquals("before deletion", store.kanjiMnemonicNote("裂"))

        // Exactly what every instrumented test does between cases.
        KaniTestDatabase.delete(context)

        // The same cached instance must now serve a recreated, empty schema rather than
        // throwing `no such table: kanji_mnemonic_notes`.
        assertEquals("", store.kanjiMnemonicNote("裂"))

        // And it must still be writable, proving the helper genuinely reopened.
        store.saveKanjiMnemonicNote("裂", "after deletion", 2_000L)
        assertEquals("after deletion", store.kanjiMnemonicNote("裂"))
    }

    @Test
    fun processStoreDropsCachedProjectionsAcrossDatabaseDeletion() {
        val store = (context.applicationContext as KaniApplication).container.localStore

        store.saveKanjiMnemonicNote("裂", "cached", 1_000L)
        // Warm the dashboard/study projections so a stale cache would be observable.
        store.dashboardRows()
        store.studyItems()

        KaniTestDatabase.delete(context)

        assertEquals("", store.kanjiMnemonicNote("裂"))
        assertEquals(0, store.dashboardRows().size)
        assertEquals(0, store.studyItems().size)
    }
}
