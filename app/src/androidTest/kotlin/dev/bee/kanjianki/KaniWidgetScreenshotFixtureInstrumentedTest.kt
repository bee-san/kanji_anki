package dev.bee.kanjianki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.widget.ActivityWidgetSnapshotLoader
import dev.bee.kanjianki.widget.ActivityWidgetState
import dev.bee.kanjianki.widget.FocusKanjiWidgetSnapshotLoader
import dev.bee.kanjianki.widget.FocusKanjiWidgetState
import dev.bee.kanjianki.widget.KaniWidgetState
import dev.bee.kanjianki.widget.StudyWidgetSnapshotLoader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds sanitized target-app data for the manual widget screenshot harness. */
@RunWith(AndroidJUnit4::class)
class KaniWidgetScreenshotFixtureInstrumentedTest {
    @Test
    fun seedDueFocusAndActivityFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                emptyList(),
                ROWS,
                RecordsSyncModels.Settings.kikuDefaults(),
                now - 10_000L,
                now - 9_000L,
                null,
            )
            store.saveStudyItem(studyItem("学", now - 1_000L, now - 30_000L))
            store.saveStudyItem(studyItem("裂", now - 500L, now - 30_000L))
            store.saveStudyItem(studyItem("包", now + DAY_MILLIS, now - 30_000L))
            repeat(5) { dayOffset ->
                store.saveReview(
                    RecordsSchedulerModels.ReviewRequest(
                        "学",
                        "widget-fixture-review-$dayOffset",
                        "good",
                        false,
                        false,
                        false,
                        0,
                    ),
                    "good",
                    now - dayOffset * DAY_MILLIS - 60_000L,
                )
            }
            store.putStringSetting(FIXTURE_SETTING, FIXTURE_ID)
        }

        assertEquals(KaniWidgetState.DUE_NOW, StudyWidgetSnapshotLoader.load(context, now).state)
        val focus = FocusKanjiWidgetSnapshotLoader.load(context, now)
        assertEquals(FocusKanjiWidgetState.READY, focus.state)
        assertEquals("学", focus.kanji)
        assertEquals(ActivityWidgetState.HISTORY, ActivityWidgetSnapshotLoader.load(context, now).state)
    }

    private fun studyItem(
        kanji: String,
        dueAtMillis: Long,
        createdAtMillis: Long,
    ): RecordsStudyModels.StudyItem = RecordsStudyModels.StudyItem(
        kanji,
        "review",
        dueAtMillis,
        3.0,
        5.0,
        4,
        0,
        2,
        0,
        "widget-fixture-item-$kanji",
        createdAtMillis,
    ).copyBuilder()
        .rung(RecordsBase.LadderRung.KANJI_MEANING)
        .phase(RecordsBase.SchedulerPhase.REVIEW)
        .build()

    companion object {
        const val FIXTURE_ID = "sanitized-focus-due-history-v1"
        const val FIXTURE_SETTING = "kani_widget_screenshot_fixture_id"
        private const val DAY_MILLIS = 86_400_000L
        private val ROWS = listOf(
            row("学", "learn", "がく"),
            row("裂", "", "れつ"),
            row("包", "", "ほう"),
        )

        private fun row(
            kanji: String,
            meaning: String,
            readings: String,
        ) = RecordsImportModels.DashboardRow(
            kanji,
            100,
            meaning,
            readings,
            kanji,
            0,
            "",
            "",
            0,
            1,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }
}
