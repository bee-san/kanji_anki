package dev.bee.kanjianki

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.widget.ActivityWidgetSnapshotLoader
import dev.bee.kanjianki.widget.ActivityWidgetState
import dev.bee.kanjianki.widget.ActivityWidgetReceiver
import dev.bee.kanjianki.widget.FocusKanjiWidgetReceiver
import dev.bee.kanjianki.widget.FocusKanjiWidgetSnapshotLoader
import dev.bee.kanjianki.widget.FocusKanjiWidgetState
import dev.bee.kanjianki.widget.KaniWidgetReceiver
import dev.bee.kanjianki.widget.KaniWidgetRegistry
import dev.bee.kanjianki.widget.KaniWidgetState
import dev.bee.kanjianki.widget.QuickStudyWidgetReceiver
import dev.bee.kanjianki.widget.StudyWidgetSnapshotLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds sanitized target-app data for the manual widget screenshot harness. */
@RunWith(AndroidJUnit4::class)
class KaniWidgetScreenshotFixtureInstrumentedTest {
    @Test
    fun pinWidgetForScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val receiverClass = when (val provider = InstrumentationRegistry.getArguments().getString(PIN_PROVIDER_ARG)) {
            "overview" -> KaniWidgetReceiver::class.java
            "quick-study" -> QuickStudyWidgetReceiver::class.java
            "activity" -> ActivityWidgetReceiver::class.java
            "focus-kanji" -> FocusKanjiWidgetReceiver::class.java
            else -> {
                assumeNotNull("Pass -e $PIN_PROVIDER_ARG with a supported provider.", provider)
                error("Unsupported widget provider: $provider")
            }
        }
        val manager = AppWidgetManager.getInstance(context)
        assumeTrue("Launcher does not support pinned widgets.", manager.isRequestPinAppWidgetSupported)
        val device = UiDevice.getInstance(instrumentation)
        device.pressHome()
        assertTrue(
            manager.requestPinAppWidget(ComponentName(context, receiverClass), null, null),
        )
        val addButton = device.wait(
            Until.findObject(By.text("Add to home screen")),
            PIN_DIALOG_TIMEOUT_MILLIS,
        )
        assertNotNull("Launcher did not show the pin-widget confirmation dialog.", addButton)
        addButton.click()
        device.waitForIdle()
    }

    @Test
    fun seedDueFocusAndActivityFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        KaniTestDatabase.delete(context)
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
            store.saveStudyItem(studyItem("包", now + 30 * 60_000L, now - 30_000L))
            FIXTURE_DAILY_COUNTS.forEachIndexed { dayIndex, reviewCount ->
                repeat(reviewCount) { reviewIndex ->
                    store.saveReview(
                        RecordsSchedulerModels.ReviewRequest(
                            "学",
                            "widget-fixture-review-$dayIndex-$reviewIndex",
                            "good",
                            false,
                            false,
                            false,
                            0,
                        ),
                        "good",
                        now - (FIXTURE_DAILY_COUNTS.lastIndex - dayIndex) * DAY_MILLIS - 60_000L,
                    )
                }
            }
            store.putStringSetting(FIXTURE_SETTING, FIXTURE_ID)
        }
        runBlocking { KaniWidgetRegistry.DEFAULT.refreshInstalled(context) }
        // Glance publishes through a session worker after updateAll returns.
        SystemClock.sleep(WIDGET_RENDER_WAIT_MILLIS)

        val overview = StudyWidgetSnapshotLoader.load(context, now)
        assertEquals(KaniWidgetState.DUE_NOW, overview.state)
        assertEquals(7, overview.last7DayCounts.size)
        val focus = FocusKanjiWidgetSnapshotLoader.load(context, now)
        assertEquals(FocusKanjiWidgetState.READY, focus.state)
        assertEquals("学", focus.kanji)
        assertEquals("study", focus.primaryMeaning)
        assertEquals("まなぶ", focus.readings)
        val activity = ActivityWidgetSnapshotLoader.load(context, now)
        assertEquals(ActivityWidgetState.HISTORY, activity.state)
        assertEquals(87, activity.last35DayTotal)
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
        const val FIXTURE_ID = "sanitized-focus-due-history-v3"
        const val FIXTURE_SETTING = "kani_widget_screenshot_fixture_id"
        const val PIN_PROVIDER_ARG = "kaniWidgetProvider"
        private const val PIN_DIALOG_TIMEOUT_MILLIS = 10_000L
        private const val WIDGET_RENDER_WAIT_MILLIS = 2_000L
        private const val DAY_MILLIS = 86_400_000L
        private val FIXTURE_DAILY_COUNTS = listOf(
            0, 1, 0, 2, 1, 0, 3,
            2, 0, 4, 1, 0, 3, 2,
            0, 2, 5, 3, 1, 0, 4,
            3, 1, 0, 6, 2, 4, 5,
            0, 4, 7, 3, 0, 8, 10,
        )
        private val ROWS = listOf(
            row("学", "study", "まなぶ"),
            row("裂", "split", "れつ"),
            row("包", "wrap", "つつむ"),
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
