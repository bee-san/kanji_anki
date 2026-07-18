package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.theme.KaniThemeChoice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyWidgetSnapshotLoaderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        context.getDatabasePath(LocalStoreSchema.DB_NAME).deleteRecursively()
    }

    @Test
    fun missingDatabaseReturnsNotSetUpWithoutCreatingDatabase() {
        val databaseFile = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        assertFalse(databaseFile.exists())

        val snapshot = StudyWidgetSnapshotLoader.load(context, NOW)

        assertEquals(KaniWidgetState.NOT_SET_UP, snapshot.state)
        assertFalse(databaseFile.exists())
        assertEquals(KaniThemeChoice.GIRLYPOP, snapshot.themeChoice)
    }

    @Test
    fun corruptDatabaseReturnsErrorWithoutThrowing() {
        val databaseFile = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeText("not sqlite")

        val snapshot = StudyWidgetSnapshotLoader.load(context, NOW)

        assertEquals(KaniWidgetState.ERROR, snapshot.state)
    }

    @Test
    fun storedThemeChoiceIsLoadedIntoSnapshot() {
        LocalStore(context).use { store ->
            store.putStringSetting(KaniThemeChoice.SETTING_KEY, KaniThemeChoice.DARK.storageKey)
        }

        val snapshot = StudyWidgetSnapshotLoader.load(context, NOW)

        assertEquals(KaniThemeChoice.DARK, snapshot.themeChoice)
    }

    @Test
    fun missingOrUnknownThemeSettingFallsBackToDefault() {
        LocalStore(context).use { store ->
            store.putStringSetting(KaniThemeChoice.SETTING_KEY, "no_such_theme")
        }

        val snapshot = StudyWidgetSnapshotLoader.load(context, NOW)

        assertEquals(KaniThemeChoice.GIRLYPOP, snapshot.themeChoice)
    }

    @Test
    fun dueCountMatchesReminderEligibilityForSeededStore() {
        var expectedDueCount = -1
        LocalStore(context).use { store ->
            store.saveRows(store.writableDatabase, listOf(dashboardRow("裂")), NOW)
            store.saveStudyItem(studyItem("裂", NOW - 1L))
            store.saveStudyItem(studyItem("包", NOW - 1L)) // Not on dashboard: ineligible.
            expectedDueCount = ReminderEligibilityPolicy.eligibleReminderItems(
                store.studyItems(),
                store.activeDashboardRows(),
                store.studyLadderSettings(),
            ).count { it.dueAtMillis <= NOW }
        }

        val snapshot = StudyWidgetSnapshotLoader.load(context, NOW)

        assertEquals(1, expectedDueCount)
        assertEquals(KaniWidgetState.DUE_NOW, snapshot.state)
        assertEquals(expectedDueCount, snapshot.dueCount)
    }

    @Test
    fun futureReviewBecomesDueWhenClockReachesDueTime() {
        val dueAt = NOW + 60_000L
        LocalStore(context).use { store ->
            store.saveRows(store.writableDatabase, listOf(dashboardRow("岩")), NOW)
            store.saveStudyItem(studyItem("岩", dueAt))
        }

        val beforeDue = StudyWidgetSnapshotLoader.load(context, dueAt - 1L)
        val atDue = StudyWidgetSnapshotLoader.load(context, dueAt)

        assertEquals(KaniWidgetState.NOTHING_DUE, beforeDue.state)
        assertEquals(0, beforeDue.dueCount)
        assertEquals(KaniWidgetState.DUE_NOW, atDue.state)
        assertEquals(1, atDue.dueCount)
    }

    @Test
    fun snapshotCarriesNewWorkAndDueLaterCluster() {
        val dueLaterAt = NOW + 30 * 60 * 1000L
        LocalStore(context).use { store ->
            store.saveRows(
                store.writableDatabase,
                listOf(dashboardRow("裂"), dashboardRow("包"), dashboardRow("岩")),
                NOW,
            )
            store.saveStudyItem(studyItem("裂", NOW - 1L)) // Reviewed, due now.
            store.saveStudyItem(studyItem("包", NOW - 1L, totalReviews = 0)) // New, due now.
            store.saveStudyItem(studyItem("岩", dueLaterAt)) // Due later today.
        }

        val snapshot = StudyWidgetSnapshotLoader.load(context, NOW)

        assertEquals(KaniWidgetState.DUE_NOW, snapshot.state)
        assertEquals(2, snapshot.dueCount)
        assertEquals(1, snapshot.newDueCount)
        assertEquals(1, snapshot.dueLaterCount)
        assertEquals(dueLaterAt, snapshot.dueLaterByMillis)
    }

    /**
     * Parity pin (widget expansion plan, secondary item 1): the Today home
     * card and the widget both consume [DailyStudyPlanPolicy]. This mirrors
     * the home card's request construction (MainActivityStudyPlanProvider
     * .dailyStudyPlan) against the widget snapshot for the same store, so the
     * two surfaces cannot silently disagree about due-later times. The widget
     * additionally filters items through ReminderEligibilityPolicy (D-S6);
     * this seed keeps every item eligible so the item sets coincide.
     */
    @Test
    fun widgetDueLaterFieldsMatchTodayCardPlanForSameStore() {
        val dueLaterAt = NOW + 30 * 60 * 1000L
        LocalStore(context).use { store ->
            store.saveRows(
                store.writableDatabase,
                listOf(dashboardRow("裂"), dashboardRow("岩")),
                NOW,
            )
            store.saveStudyItem(studyItem("裂", NOW - 1L))
            store.saveStudyItem(studyItem("岩", dueLaterAt))
        }

        val snapshot = StudyWidgetSnapshotLoader.load(context, NOW)

        LocalStore(context).use { store ->
            val rows = store.activeDashboardRows()
            val homeItems = store.studyItemsForKanji(rows.map { it.kanji })
            val streak = store.studyStreak(NOW)
            val homePlan = DailyStudyPlanPolicy.plan(
                DailyStudyPlanRequest(
                    nowMillis = NOW,
                    dueAtMillis = homeItems.map { it.dueAtMillis },
                    studiedToday = streak.studiedToday,
                    streak = StudyStreakPolicy.Streak(
                        currentDays = streak.currentDays,
                        bestDays = streak.bestDays,
                        studiedToday = streak.studiedToday,
                        reviewsToday = streak.reviewsToday,
                        lastStudyAtMillis = streak.lastStudyAtMillis,
                    ),
                    newProblemKanjiAvailable = if (rows.isEmpty()) 0 else homeItems.count { it.totalReviews == 0 },
                    lastSuccessfulSyncAtMillis = store.latestSuccessfulSyncFinishedAt(),
                ),
            )

            assertEquals(homePlan.dueLookahead.clusterSize, snapshot.dueLaterCount)
            assertEquals(homePlan.dueLookahead.clusterEndAtMillis, snapshot.dueLaterByMillis)
            assertEquals(homePlan.nextUsefulReminderAtMillis, snapshot.nextUsefulAtMillis)
            assertEquals(homePlan.dueNow, snapshot.dueCount)
        }
    }

    private fun dashboardRow(kanji: String) = RecordsImportModels.DashboardRow(
        kanji,
        100,
        "split",
        "れつ",
        kanji,
        0,
        "",
        "",
        0,
        0,
        0,
        emptyList<RecordsImportModels.Example>(),
    )

    private fun studyItem(
        kanji: String,
        dueAtMillis: Long,
        totalReviews: Int = 1,
    ): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            "review",
            dueAtMillis,
            1.0,
            5.0,
            totalReviews,
            0,
            0,
            0,
            "token-$kanji",
            NOW - 10_000L,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()

    companion object {
        private const val NOW = 1_800_000_000_000L
    }
}
