package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels
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
class LocalStoreStudyItemsForKanjiCacheTest {
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
    fun cachesRepeatedKanjiSubsetLookupsAndInvalidatesOnStudyItemChanges() {
        store.replaceStudyItems(
            listOf(
                studyItem("字0", 3_000L),
                studyItem("字1", 1_000L),
                studyItem("字2", 2_000L),
            )
        )

        val query = listOf("字1", "字0", "字1")
        val warmup = store.studyItemsForKanji(query)
        assertEquals(listOf("字1", "字0"), warmup.map { it.kanji })

        val baselineIterations = 25
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                store.clearStudyItemsCache()
                store.studyItemsForKanji(query)
            }
        }

        val cachedSnapshot = store.studyItemsForKanji(listOf("字0", "字1"))
        assertSame(cachedSnapshot, store.studyItemsForKanji(listOf("字1", "字0", "字1")))

        val hitNanos = measureNanoTime {
            repeat(hitIterations) {
                assertSame(cachedSnapshot, store.studyItemsForKanji(listOf("字1", "字0")))
            }
        }

        store.saveStudyItem(studyItem("字3", 4_000L))

        val refreshedSnapshot = store.studyItemsForKanji(listOf("字0", "字1"))
        assertNotSame(cachedSnapshot, refreshedSnapshot)
        assertTrue(refreshedSnapshot.isNotEmpty())

        println(
            String.format(
                Locale.ROOT,
                "study-items-for-kanji baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
                baselineNanos / 1_000_000.0,
                baselineNanos / baselineIterations.toDouble() / 1_000.0,
                hitNanos / 1_000_000.0,
                hitNanos / hitIterations.toDouble() / 1_000.0,
            ),
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
