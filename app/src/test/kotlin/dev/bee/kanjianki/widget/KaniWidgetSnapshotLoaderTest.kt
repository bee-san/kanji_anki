package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
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
class KaniWidgetSnapshotLoaderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun missingDatabaseReturnsNotSetUpWithoutCreatingDatabase() {
        val databaseFile = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        assertFalse(databaseFile.exists())

        val snapshot = KaniWidgetSnapshotLoader.load(context, NOW)

        assertEquals(KaniWidgetState.NOT_SET_UP, snapshot.state)
        assertFalse(databaseFile.exists())
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

        val snapshot = KaniWidgetSnapshotLoader.load(context, NOW)

        assertEquals(1, expectedDueCount)
        assertEquals(KaniWidgetState.DUE_NOW, snapshot.state)
        assertEquals(expectedDueCount, snapshot.dueCount)
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

    private fun studyItem(kanji: String, dueAtMillis: Long): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            "review",
            dueAtMillis,
            1.0,
            5.0,
            1,
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
