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
import dev.bee.kanjianki.core.SyncSettings
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.data.STATS_RECENT_MISTAKE_LIMIT
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.toRepositorySnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun recentMistakesQueryExcludesMatureUnseededCurrentRow() {
        val occurredAt = nextTime()
        saveDashboardRow(dashboardRow(matureSupportCount = 2), occurredAt)
        saveAgainReview()

        assertTrue(store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).isEmpty())
        assertTrue(routeData().mistakes.isEmpty())
        assertEquals(1, reviewCount())
    }

    @Test
    fun recentMistakesQueryExcludesMatureCurrentRowWithOnlyStaleActiveFamily() {
        val currentRow = dashboardRow(matureSupportCount = 2)
        val occurredAt = nextTime()
        val syncId = saveDashboardRow(currentRow, occurredAt)
        val staleActive = studyItem(
            state = StudyLadderRules.STATE_REVIEW,
            signature = StudyQueueSeeder.answerSignature(
                dashboardRow(matureSupportCount = 1, expression = "古橋"),
            ),
        )
        store.replaceStudyItems(listOf(staleActive), syncId, occurredAt, settings)
        saveAgainReview()

        assertTrue(store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).isEmpty())
        assertTrue(routeData().mistakes.isEmpty())
        assertEquals(1, reviewCount())
    }

    @Test
    fun recentMistakesQueryExcludesMatureRetiredFamilyWithActiveLegacyDuplicate() {
        val currentRow = dashboardRow(matureSupportCount = 2)
        val occurredAt = nextTime()
        val syncId = saveDashboardRow(currentRow, occurredAt)
        val currentRetired = studyItem(
            state = StudyLadderRules.STATE_RETIRED,
            signature = StudyQueueSeeder.answerSignature(currentRow),
        )
        val legacyActive = studyItem(
            state = StudyLadderRules.STATE_REVIEW,
            signature = "",
        )
        store.replaceStudyItems(listOf(currentRetired, legacyActive), syncId, occurredAt, settings)
        saveAgainReview()

        assertTrue(store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).isEmpty())
        assertTrue(routeData().mistakes.isEmpty())
        assertEquals(1, reviewCount())
    }

    @Test
    fun recentMistakesProjectionRestoresRetiredFamilyAfterSupportDrops() {
        val matureRow = dashboardRow(matureSupportCount = 2)
        val retiredAt = nextTime()
        val retiredSyncId = saveDashboardRow(matureRow, retiredAt)
        val retired = studyItem(
            state = StudyLadderRules.STATE_RETIRED,
            signature = StudyQueueSeeder.answerSignature(matureRow),
        )
        store.replaceStudyItems(listOf(retired), retiredSyncId, retiredAt, settings)
        saveAgainReview()
        assertTrue(store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).isEmpty())

        saveDashboardRow(dashboardRow(matureSupportCount = 1), nextTime())

        assertEquals(listOf(KANJI), store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).map { it.kanji })
        assertEquals(listOf(KANJI), routeData().mistakes.map { it.kanji })
        assertEquals(1, reviewCount())
    }

    @Test
    fun recentMistakesProjectionRestoresCachedHistoryAfterThresholdIncreaseAndRestart() {
        val matureRow = dashboardRow(matureSupportCount = 2)
        val retiredAt = nextTime()
        val retiredSyncId = saveDashboardRow(matureRow, retiredAt)
        val retired = studyItem(
            state = StudyLadderRules.STATE_RETIRED,
            signature = StudyQueueSeeder.answerSignature(matureRow),
        )
        store.replaceStudyItems(listOf(retired), retiredSyncId, retiredAt, settings)
        saveAgainReview()
        store.recomputeStatsSnapshotSynchronously(nextTime())
        assertTrue(routeData().mistakes.isEmpty())

        store.putIntSetting(SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY, 3)
        assertEquals(3, store.getIntSetting(SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY, -1))
        store.close()
        store = LocalStore(context)

        assertEquals(listOf(KANJI), store.recentMistakes(STATS_RECENT_MISTAKE_LIMIT).map { it.kanji })
        assertEquals(listOf(KANJI), routeData().mistakes.map { it.kanji })
        assertEquals(1, reviewCount())
    }

    @Test
    fun matureSupportThresholdWritesRollBackWhenStatsInvalidationFails() {
        val thresholdKey = SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY
        store.putIntSetting(thresholdKey, 2)
        val sourceVersion = StatsCacheStore(store).currentSourceVersion()
        store.writableDatabase.execSQL(
            """
            CREATE TEMP TRIGGER abort_stats_source_version_update
            BEFORE UPDATE OF value ON stats_cache_state
            BEGIN
                SELECT RAISE(ABORT, 'forced stats invalidation failure');
            END
            """.trimIndent(),
        )

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            store.putIntSetting(thresholdKey, 3)
        }
        assertEquals(2, store.getIntSetting(thresholdKey, -1))
        store.close()
        store = LocalStore(context)

        assertEquals(2, store.getIntSetting(thresholdKey, -1))
        assertEquals(sourceVersion, StatsCacheStore(store).currentSourceVersion())
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

    @Test
    fun recentMistakesRouteRefreshesDashboardFamilyAfterAnotherStoreSyncs() {
        val oldRow = dashboardRow(matureSupportCount = 1, expression = "古橋")
        val oldSyncAt = nextTime()
        val oldSyncId = saveDashboardRow(oldRow, oldSyncAt)
        store.replaceStudyItems(
            listOf(
                studyItem(
                    state = StudyLadderRules.STATE_REVIEW,
                    signature = StudyQueueSeeder.answerSignature(oldRow),
                ),
            ),
            oldSyncId,
            oldSyncAt,
            settings,
        )
        saveAgainReview()
        assertEquals(listOf(KANJI), routeData().mistakes.map { it.kanji })

        LocalStore(context).use { syncStore ->
            val currentRow = dashboardRow(matureSupportCount = 2, expression = "新橋")
            val newSyncAt = nextTime()
            val newSyncId = saveDashboardRow(currentRow, newSyncAt, syncStore)
            syncStore.replaceStudyItems(
                listOf(
                    studyItem(
                        state = StudyLadderRules.STATE_RETIRED,
                        signature = StudyQueueSeeder.answerSignature(currentRow),
                    ),
                    studyItem(
                        state = StudyLadderRules.STATE_REVIEW,
                        signature = StudyQueueSeeder.answerSignature(oldRow),
                    ),
                ),
                newSyncId,
                newSyncAt,
                settings,
            )
        }

        assertTrue(routeData().mistakes.isEmpty())
        LocalStore(context).use { coldStore ->
            assertTrue(routeData(coldStore).mistakes.isEmpty())
        }
        assertEquals(1, reviewCount())
    }

    @Test
    fun suspensionWritesRollBackWhenStatsInvalidationFails() {
        val batchKanji = "誤"
        store.setKanjiLocallySuspendedForKanji(listOf(KANJI, batchKanji), true, nextTime())
        val cacheStore = StatsCacheStore(store)
        val sourceVersion = cacheStore.currentSourceVersion()
        store.writableDatabase.execSQL(
            """
            CREATE TEMP TRIGGER abort_stats_source_version_update
            BEFORE UPDATE OF value ON stats_cache_state
            BEGIN
                SELECT RAISE(ABORT, 'forced stats invalidation failure');
            END
            """.trimIndent(),
        )

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            store.setKanjiLocallySuspended(KANJI, false, nextTime())
        }
        assertTrue(store.isKanjiLocallySuspended(KANJI))
        assertEquals(sourceVersion, cacheStore.currentSourceVersion())

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            store.setKanjiLocallySuspendedForKanji(listOf(batchKanji), false, nextTime())
        }
        assertTrue(store.isKanjiLocallySuspended(batchKanji))
        assertEquals(sourceVersion, cacheStore.currentSourceVersion())
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
        targetStore: LocalStore = store,
    ): Long {
        return targetStore.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            listOf(row),
            settings,
            occurredAt - 1L,
            occurredAt,
            null,
        )
    }

    private fun routeData(targetStore: LocalStore = store): RecentMistakesRouteData {
        val snapshot = targetStore.cachedStatsSnapshotOrNull()
            ?: targetStore.recomputeStatsSnapshotSynchronously(now)
        val rows = targetStore.activeDashboardRows()
        return recentMistakesRouteData(
            snapshot.toRepositorySnapshot(),
            rows,
            targetStore.studyItemsForKanji(rows.map { it.kanji }),
            SyncSettings.fromStore(targetStore),
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
