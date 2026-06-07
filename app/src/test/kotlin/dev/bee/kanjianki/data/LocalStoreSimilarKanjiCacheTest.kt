package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader
import java.util.Locale
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreSimilarKanjiCacheTest {
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
    fun cachesRepeatedSimilarNeighborLookupsAndInvalidatesOnPairRebuilds() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val initialIndex = SimilarKanjiIndex.parseTsv(StringReader("拉\t提\tfixture\n拉\t謎\tfixture\n"))
        val initialRows = listOf(dashboardRow("拉"), dashboardRow("提"), dashboardRow("謎"), dashboardRow("烈"))
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList<RecordsImportModels.SuspendedImport>(),
            initialRows,
            settings,
            LocalStoreBase.SyncTiming(1_000L, 2_000L),
            null,
            initialIndex,
        )

        val warmup = store.kanjiWithSimilarNeighbors(store.readableDatabase)
        assertEquals(setOf("拉", "提", "謎"), warmup)

        val baselineIterations = 25
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                store.clearSimilarKanjiNeighborsCache()
                store.kanjiWithSimilarNeighbors(store.readableDatabase)
            }
        }

        val cachedSnapshot = store.kanjiWithSimilarNeighbors(store.readableDatabase)
        assertSame(cachedSnapshot, store.kanjiWithSimilarNeighbors(store.readableDatabase))

        val hitNanos = measureNanoTime {
            repeat(hitIterations) {
                assertSame(cachedSnapshot, store.kanjiWithSimilarNeighbors(store.readableDatabase))
            }
        }

        val rebuiltIndex = SimilarKanjiIndex.parseTsv(StringReader("拉\t烈\tfixture\n"))
        store.rebuildSimilarKanjiPairs(rebuiltIndex, 2_500L)

        val refreshedSnapshot = store.kanjiWithSimilarNeighbors(store.readableDatabase)
        assertNotSame(cachedSnapshot, refreshedSnapshot)
        assertTrue(refreshedSnapshot.contains("烈"))

        println(
            String.format(
                Locale.ROOT,
                "similar-kanji-neighbors baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
                baselineNanos / 1_000_000.0,
                baselineNanos / baselineIterations.toDouble() / 1_000.0,
                hitNanos / 1_000_000.0,
                hitNanos / hitIterations.toDouble() / 1_000.0,
            ),
        )
    }

    private fun dashboardRow(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            "meaning-$kanji",
            "reading-$kanji",
            "browser-$kanji",
            1,
            "reason",
            "reason text",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }
}