package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.After
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
class LocalStoreTimelineCacheTest {
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
    fun cachesRepeatedTimelineLookupsAndInvalidatesOnTimelineAndCacheChanges() {
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            dashboardRows(120),
            RecordsSyncModels.Settings.kikuDefaults(),
            1_000L,
            2_000L,
            null,
        )
        store.replaceStudyItems(listOf(studyItem("字0", 3_000L)))
        store.saveReview(
            RecordsSchedulerModels.ReviewRequest("字0", "seed-token", "good", true, true, false, 0),
            "good",
            4_000L,
        )

        val query = "字0"
        val warmup = store.timelineForKanji(query)
        assertTrue(warmup.events.isNotEmpty())

        val baselineIterations = 25
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                store.clearTimelineCache()
                store.timelineForKanji(query)
            }
        }

        val cachedSnapshot = store.timelineForKanji(query)
        assertSame(cachedSnapshot, store.timelineForKanji(query))

        val hitNanos = measureNanoTime {
            repeat(hitIterations) {
                assertSame(cachedSnapshot, store.timelineForKanji(query))
            }
        }

        store.saveReview(
            RecordsSchedulerModels.ReviewRequest("字0", "follow-up-token", "again", true, false, false, 0),
            "again",
            5_000L,
        )
        val refreshedAfterReview = store.timelineForKanji(query)
        assertNotSame(cachedSnapshot, refreshedAfterReview)

        store.replaceStudyItems(listOf(studyItem("字0", 6_000L)))
        val refreshedAfterStudy = store.timelineForKanji(query)
        assertNotSame(refreshedAfterReview, refreshedAfterStudy)

        store.clearDashboardRowsCache()
        val refreshedAfterDashboardClear = store.timelineForKanji(query)
        assertNotSame(refreshedAfterStudy, refreshedAfterDashboardClear)

        store.clearKanjiInventoryAllCache()
        val refreshedAfterInventoryClear = store.timelineForKanji(query)
        assertNotSame(refreshedAfterDashboardClear, refreshedAfterInventoryClear)

        println(
            String.format(
                Locale.ROOT,
                "timeline-cache baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
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

    private fun studyItem(kanji: String, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            dueAtMillis,
            1.0,
            2.0,
            1,
            0,
            0,
            0,
            "",
            dueAtMillis,
        )
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
    }
}
