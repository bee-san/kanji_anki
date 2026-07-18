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
    fun japaneseLocaleLocalizesSyncAndBackfilledTimelineEvents() {
        withLocale(Locale.JAPAN) {
            val settings = RecordsSyncModels.Settings.kikuDefaults()
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                listOf(suspendedImport("字0")),
                listOf(dashboardRow(0, matureSupportCount = 1)),
                settings,
                1_000L,
                2_000L,
                null,
            )
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                listOf(suspendedImport("字0")),
                listOf(dashboardRow(0, matureSupportCount = 3)),
                settings,
                3_000L,
                4_000L,
                null,
            )
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                listOf(suspendedImport("字0")),
                listOf(dashboardRow(0, matureSupportCount = 2)),
                settings,
                5_000L,
                6_000L,
                null,
            )

            val syncEvents = store.timelineForKanji("字0").events
            assertTimelineEvent(
                syncEvents,
                "suspended_imported",
                "保留中のAnkiからインポート",
                "KaniはAnkiDroidの保留カードからこの漢字を復旧しました。",
            )
            assertTimelineEvent(
                syncEvents,
                LocalStoreBase.TIMELINE_FIRST_SEEN,
                "Kaniが見守り開始",
                "この漢字はローカルAnkiDroidの証拠からKaniに入りました。",
            )
            assertTimelineEvent(
                syncEvents,
                "weak_support_seen",
                "弱いサポートを検出",
                "Ankiの証拠はまだ修復が必要: 成熟サポート 1 / 目標 2。",
            )
            assertTimelineEvent(
                syncEvents,
                "support_improved",
                "Ankiサポートが改善",
                "成熟サポートが1から3に増えました。",
            )
            assertTimelineEvent(
                syncEvents,
                "support_dropped",
                "Ankiサポートが低下",
                "成熟サポートが3から2に減りました。",
            )

            store.replaceStudyItems(listOf(studyItem("字0", 7_000L, LocalStoreBase.STATE_RETIRED)))
            store.saveReview(
                RecordsSchedulerModels.ReviewRequest("字0", "review-token", "good", false, false, false, 0),
                "good",
                8_000L,
            )
            val db = store.writableDatabase
            db.delete(LocalStoreBase.TABLE_KANJI_TIMELINE_EVENTS, null, null)
            store.clearTimelineCache()
            store.backfillTimelineEvents(db)
            store.clearTimelineCache()

            val backfilledEvents = store.timelineForKanji("字0").events
            assertTimelineEvent(
                backfilledEvents,
                LocalStoreBase.STATE_RETIRED,
                "Ankiの支えで修了",
                "成熟したAnkiの支えが目標に到達: 成熟サポート 2 / 目標 2。",
            )
            assertTimelineEvent(
                backfilledEvents,
                "review_passed",
                "復習成功",
                "思い出し復習は「良い」と評価されました。",
            )
        }
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

    @Test
    fun signatureReshuffleKeepsRetirementOnTheExistingKanjiTimeline() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val syncId = store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            listOf(dashboardRow(0)),
            settings,
            500L,
            900L,
            null,
        )
        store.replaceStudyItems(listOf(studyItem("字0", 1_000L, signature = "字0|old|reading|meaning")))

        store.replaceStudyItems(
            listOf(
                studyItem(
                    "字0",
                    1_000L,
                    state = LocalStoreBase.STATE_RETIRED,
                    signature = "字0|new|reading|meaning",
                ),
            ),
            syncId = syncId,
            occurredAt = 2_000L,
            settings = settings,
        )

        val events = store.timelineForKanji("字0").events
        assertTrue(
            "items=${store.studyItems().map { it.state to it.answerSignature }} events=${events.map { it.eventType }}",
            events.any { it.eventType == LocalStoreBase.STATE_RETIRED },
        )
    }

    private fun dashboardRows(count: Int): List<RecordsImportModels.DashboardRow> {
        return List(count) { index -> dashboardRow(index) }
    }

    private fun dashboardRow(index: Int, matureSupportCount: Int = 0): RecordsImportModels.DashboardRow {
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
            matureSupportCount,
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

    private fun suspendedImport(kanji: String): RecordsImportModels.SuspendedImport {
        return RecordsImportModels.SuspendedImport(
            kanji,
            null,
            false,
            0,
            listOf(RecordsImportModels.SuspendedSource(kanji, 1L, 10L, "expr-0", "read-0", "meaning-0", "sentence-0")),
        )
    }

    private fun assertTimelineEvent(
        events: List<RecordsImportModels.KanjiTimelineEvent>,
        eventType: String,
        title: String,
        detail: String,
    ) {
        val event = events.first { it.eventType == eventType }
        assertEquals(title, event.title)
        assertEquals(detail, event.detail)
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun studyItem(
        kanji: String,
        dueAtMillis: Long,
        state: String = "review",
        signature: String = "",
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            state,
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
            .answerSignature(signature)
            .build()
    }
}
