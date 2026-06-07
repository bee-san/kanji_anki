package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreInventorySearchCacheTest {
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
    fun cachesRepeatSearchesAndInvalidatesOnSuspensionChanges() {
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            dashboardRows(120),
            RecordsSyncModels.Settings.kikuDefaults(),
            1_000L,
            2_000L,
            null,
        )

        val query = "字"
        val warmup = store.searchKanjiInventory(query)
        assertTrue(warmup.isNotEmpty())
        store.clearKanjiInventoryAllCache()

        val baselineIterations = 25
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                store.clearKanjiInventoryAllCache()
                store.searchKanjiInventory(query)
            }
        }

        val cachedSnapshot = store.searchKanjiInventory(query)
        assertSame(cachedSnapshot, store.searchKanjiInventory(query))

        val hitNanos = measureNanoTime {
            repeat(hitIterations) {
                assertSame(cachedSnapshot, store.searchKanjiInventory(query))
            }
        }

        val allSnapshot = store.searchKanjiInventory(null)
        assertSame(allSnapshot, store.searchKanjiInventory(" "))
        assertTrue(!allSnapshot.first { it.kanji == "字0" }.suspended)

        store.setKanjiLocallySuspended("字0", true, 2_500L)

        val refreshedQuery = store.searchKanjiInventory(query)
        val refreshedAll = store.searchKanjiInventory(null)

        assertNotSame(cachedSnapshot, refreshedQuery)
        assertNotSame(allSnapshot, refreshedAll)
        assertTrue(refreshedQuery.first { it.kanji == "字0" }.suspended)
        assertTrue(refreshedAll.first { it.kanji == "字0" }.suspended)

        println(
            String.format(
                Locale.ROOT,
                "kanji-inventory-search baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
                baselineNanos / 1_000_000.0,
                baselineNanos / baselineIterations.toDouble() / 1_000.0,
                hitNanos / 1_000_000.0,
                hitNanos / hitIterations.toDouble() / 1_000.0,
            ),
        )
    }

    private fun dashboardRows(count: Int): List<RecordsImportModels.DashboardRow> {
        return List(count) { index -> dashboardRow(index) }
    }

    private fun dashboardRow(index: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            "字$index",
            if (index % 4 == 0) null else index + 1,
            "meaning $index",
            "reading $index",
            "browser $index",
            index % 13,
            "reason-${index % 3}",
            "reason text $index",
            1,
            0,
            0,
            listOf(example(index)),
        )
    }

    private fun example(index: Int): RecordsImportModels.Example {
        val difficulty = 1.0 + (index % 7)
        val retrievability = 0.1 + (index % 8) * 0.1
        return RecordsImportModels.Example(
            "active",
            index.toLong() + 1,
            index.toLong() + 10_000,
            "expr-$index",
            "read-$index",
            "meaning-$index",
            "sentence-$index",
            index % 2 == 0,
            index % 5,
            index + 1,
            (index % 6) + 1,
            2.5 + index,
            difficulty,
            retrievability,
        )
    }
}
