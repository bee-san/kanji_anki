package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyCollectionLookup
import java.util.Locale
import kotlin.system.measureNanoTime
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreActiveDashboardRowsByKanjiCacheTest {
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
    fun cachesRepeatedActiveDashboardRowsByKanjiLookupsAndInvalidatesOnDashboardAndSuspensionChanges() {
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            dashboardRows(120),
            RecordsSyncModels.Settings.kikuDefaults(),
            1_000L,
            2_000L,
            null,
        )

        val rows = store.activeDashboardRows()
        val expected = StudyCollectionLookup.dashboardRowsByKanji(rows)
        val cachedSnapshot = store.activeDashboardRowsByKanji()
        assertEquals(expected, cachedSnapshot)
        assertEquals(rows.map { it.kanji }, cachedSnapshot.keys.toList())
        assertSame(cachedSnapshot, store.activeDashboardRowsByKanji())

        val baselineIterations = 5_000
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                StudyCollectionLookup.dashboardRowsByKanji(rows)
            }
        }

        val hitNanos = measureNanoTime {
            repeat(hitIterations) {
                assertSame(cachedSnapshot, store.activeDashboardRowsByKanji())
            }
        }

        store.setKanjiLocallySuspended("字0", true, 3_000L)
        val refreshedAfterSuspension = store.activeDashboardRowsByKanji()
        assertNotSame(cachedSnapshot, refreshedAfterSuspension)
        assertTrue(refreshedAfterSuspension.size < cachedSnapshot.size)

        store.clearDashboardRowsCache()
        val refreshedAfterClear = store.activeDashboardRowsByKanji()
        assertNotSame(refreshedAfterSuspension, refreshedAfterClear)
        assertEquals(refreshedAfterSuspension.keys.toList(), refreshedAfterClear.keys.toList())

        println(
            String.format(
                Locale.ROOT,
                "active-dashboard-rows-by-kanji baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
                baselineNanos / 1_000_000.0,
                baselineNanos / baselineIterations.toDouble() / 1_000.0,
                hitNanos / 1_000_000.0,
                hitNanos / hitIterations.toDouble() / 1_000.0,
            ),
        )
    }

    private fun dashboardRows(count: Int): List<RecordsImportModels.DashboardRow> {
        return List(count) { index ->
            RecordsImportModels.DashboardRow(
                "字$index",
                if (index % 3 == 0) null else index + 1,
                "meaning $index",
                "reading $index",
                "browser $index",
                index % 13,
                "reason-${index % 4}",
                "reason text $index",
                1,
                0,
                0,
                emptyList<RecordsImportModels.Example>(),
            )
        }
    }
}
