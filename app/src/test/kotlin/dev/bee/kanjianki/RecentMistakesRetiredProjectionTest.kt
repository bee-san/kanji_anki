package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.data.STATS_RECENT_MISTAKE_LIMIT
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecentMistakesRetiredProjectionTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private var now = System.currentTimeMillis()

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
    fun recentMistakesQueryProjectsHistoryOnlyWhileTargetIsCurrentlyRepairEligible() {
        seedWeakMistake()
        assertEquals(listOf(KANJI), store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).map { it.kanji })

        transitionTo(StudyLadderRules.STATE_RETIRED, matureSupportCount = 2)
        assertTrue(store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).isEmpty())

        transitionTo(StudyLadderRules.STATE_LEARNING, matureSupportCount = 1)
        assertEquals(listOf(KANJI), store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).map { it.kanji })

        transitionTo(StudyLadderRules.STATE_RETIRED, matureSupportCount = 2)
        assertTrue(store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).isEmpty())
        assertEquals(1, reviewCount())

        val timeline = store.timelineForKanji(KANJI)
        assertEquals(1, timeline.events.count { it.eventType == "review_failed" })
        assertEquals(2, timeline.events.count { it.eventType == StudyLadderRules.STATE_RETIRED })
        assertEquals(1, timeline.events.count { it.eventType == "reopened" })
    }

    @Test
    fun recentMistakesQueryExcludesRetiredCurrentFamilyWhenAStaleFamilyRemainsActive() {
        val currentRow = dashboardRow(matureSupportCount = 2)
        val occurredAt = nextTime()
        val syncId = saveDashboardRow(currentRow, occurredAt)
        val currentRetired = studyItem(
            state = StudyLadderRules.STATE_RETIRED,
            signature = StudyQueueSeeder.answerSignature(currentRow),
        )
        val staleActive = studyItem(
            state = StudyLadderRules.STATE_REVIEW,
            signature = StudyQueueSeeder.answerSignature(
                dashboardRow(matureSupportCount = 1, expression = "古橋"),
            ),
        )
        store.replaceStudyItems(listOf(currentRetired, staleActive), syncId, occurredAt, settings)
        saveAgainReview()

        assertTrue(store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).isEmpty())
    }

    @Test
    fun recentMistakesQueryKeepsHistoryForAnUnseededEligibleDashboardRow() {
        val occurredAt = nextTime()
        saveDashboardRow(dashboardRow(matureSupportCount = 1), occurredAt)
        saveAgainReview()

        assertEquals(listOf(KANJI), store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).map { it.kanji })
    }

    @Test
    fun recentMistakesRouteDropsInvalidatedCachedMistakeImmediatelyAfterRetirement() {
        seedWeakMistake()
        store.recomputeStatsSnapshotSynchronously(nextTime())
        assertEquals(listOf(KANJI), routeData().mistakes.map { it.kanji })

        transitionTo(StudyLadderRules.STATE_RETIRED, matureSupportCount = 2)

        assertTrue(routeData().mistakes.isEmpty())
    }

    @Test
    fun recentMistakesRouteDropsPersistedStaleMistakeAfterRestart() {
        seedWeakMistake()
        store.recomputeStatsSnapshotSynchronously(nextTime())
        transitionTo(StudyLadderRules.STATE_RETIRED, matureSupportCount = 2)

        store.close()
        store = LocalStore(context)

        assertTrue(routeData().mistakes.isEmpty())
        assertEquals(1, reviewCount())
        assertEquals(
            StudyLadderRules.STATE_RETIRED,
            store.timelineForKanji(KANJI).currentStudyItem?.state,
        )
    }

    @Test
    fun recentMistakesRouteRestoresHistoricalMistakeImmediatelyAfterReopen() {
        seedWeakMistake()
        transitionTo(StudyLadderRules.STATE_RETIRED, matureSupportCount = 2)
        store.recomputeStatsSnapshotSynchronously(nextTime())
        assertTrue(routeData().mistakes.isEmpty())

        transitionTo(StudyLadderRules.STATE_LEARNING, matureSupportCount = 1)

        assertEquals(listOf(KANJI), routeData().mistakes.map { it.kanji })
        assertEquals(1, reviewCount())
    }

    @Test
    fun recentMistakesRouteRestoresHistoricalMistakeImmediatelyAfterLocalUnsuspend() {
        seedWeakMistake()
        store.setKanjiLocallySuspended(KANJI, true, nextTime())
        store.recomputeStatsSnapshotSynchronously(nextTime())
        assertTrue(routeData().mistakes.isEmpty())

        store.setKanjiLocallySuspended(KANJI, false, nextTime())

        assertEquals(listOf(KANJI), routeData().mistakes.map { it.kanji })
        assertEquals(1, reviewCount())
    }

    private fun seedWeakMistake() {
        transitionTo(StudyLadderRules.STATE_REVIEW, matureSupportCount = 1)
        saveAgainReview()
    }

    private fun saveAgainReview() {
        store.saveReview(
            RecordsSchedulerModels.ReviewRequest(
                KANJI,
                "again-token",
                "again",
                false,
                false,
                false,
                0,
            ),
            "again",
            nextTime(),
        )
    }

    private fun transitionTo(state: String, matureSupportCount: Int) {
        val occurredAt = nextTime()
        val syncId = saveDashboardRow(dashboardRow(matureSupportCount), occurredAt)
        val current = store.studyItemsForKanji(listOf(KANJI)).singleOrNull() ?: studyItem()
        val transitioned = current.copyBuilder()
            .state(state)
            .activeToken(if (state == StudyLadderRules.STATE_RETIRED) "" else "token-$state")
            .build()
        store.replaceStudyItems(
            listOf(transitioned),
            syncId,
            occurredAt,
            settings,
        )
    }

    private fun saveDashboardRow(
        row: RecordsImportModels.DashboardRow,
        occurredAt: Long,
    ): Long {
        return store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            listOf(row),
            settings,
            occurredAt - 1L,
            occurredAt,
            null,
        )
    }

    private fun routeData(): RecentMistakesRouteData {
        return recentMistakesRouteData(
            object : RecentMistakesRouteDataSource {
                override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
                    return store.cachedStatsSnapshotOrNull()
                }

                override fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
                    return store.recentMistakes(limit)
                }

                override fun studyItemsForKanji(
                    kanji: Collection<String>,
                ): List<RecordsStudyModels.StudyItem> {
                    return store.studyItemsForKanji(kanji)
                }

                override fun activeDashboardRowsByKanji(): Map<String, RecordsImportModels.DashboardRow> {
                    return store.activeDashboardRowsByKanji()
                }
            },
        )
    }

    private fun reviewCount(): Int {
        return store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM review_log WHERE kanji=?",
            arrayOf(KANJI),
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    private fun dashboardRow(
        matureSupportCount: Int,
        expression: String = "橋箱",
    ): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            KANJI,
            1,
            "bridge",
            "はし",
            "deck:Kiku $KANJI",
            10,
            "weak_card",
            "Bridge box",
            1,
            0,
            matureSupportCount,
            listOf(
                RecordsImportModels.Example(
                    "active",
                    1L,
                    10L,
                    expression,
                    "はしばこ",
                    "bridge box",
                    "橋箱を見た。",
                    matureSupportCount > 0,
                    1,
                    21,
                    2,
                    4.0,
                    5.0,
                    0.9,
                ),
            ),
        )
    }

    private fun studyItem(
        state: String = StudyLadderRules.STATE_REVIEW,
        signature: String = "",
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            KANJI,
            state,
            now,
            1.0,
            5.0,
            1,
            1,
            0,
            0,
            "token-review",
            now,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .answerSignature(signature)
            .build()
    }

    private fun nextTime(): Long {
        now += 1_000L
        return now
    }

    private companion object {
        const val KANJI = "橋"
    }
}
