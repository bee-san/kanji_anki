package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsImportModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks in that the batched example loader used by [LocalStoreInventory.dashboardRows] returns the
 * same examples, per-kanji order, and 8-example cap as the original one-query-per-kanji path. The
 * batch replaced an N+1 query that cost ~240ms on cold-boot study loads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreDashboardExamplesBatchTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun batchedExamplesMatchPerKanjiReadsWithOrderingAndCap() {
        val rows = listOf(
            dashboardRow("見", exampleCount = 10),
            dashboardRow("書", exampleCount = 3),
            dashboardRow("読", exampleCount = 0),
        )
        val db = store.writableDatabase
        store.saveRows(db, rows, 1_000L)

        val batched = store.examplesForKanjiBatch(db, listOf("見", "書", "読", "未使用"))

        for (kanji in listOf("見", "書", "読")) {
            val perKanji = store.examplesForKanji(db, kanji)
            val fromBatch = batched[kanji] ?: emptyList()
            assertEquals(
                "batched example count mismatch for $kanji",
                perKanji.size,
                fromBatch.size,
            )
            assertEquals(
                "batched example order mismatch for $kanji",
                perKanji.map { it.expression },
                fromBatch.map { it.expression },
            )
        }

        // 8-example cap preserved even though 見 was saved with 10 examples.
        assertEquals(8, batched["見"]?.size)
        // Missing kanji simply have no entry.
        assertEquals(null, batched["未使用"])
    }

    private fun dashboardRow(kanji: String, exampleCount: Int): RecordsImportModels.DashboardRow {
        val examples = List(exampleCount) { index ->
            RecordsImportModels.Example(
                if (index % 2 == 0) "active" else "suspended",
                (index + 1).toLong(),
                (100 + index).toLong(),
                "$kanji$index",
                "reading$index",
                "meaning$index",
                "sentence$index",
                false,
                0,
                index,
                index,
                null,
                null,
                null,
            )
        }
        return RecordsImportModels.DashboardRow(
            kanji,
            null,
            "meaning $kanji",
            "reading $kanji",
            "browser $kanji",
            5,
            "reason",
            "reason text",
            exampleCount,
            0,
            0,
            examples,
        )
    }
}
