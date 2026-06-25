package dev.bee.kanjianki

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.StudyReviewButtonCopy
import dev.bee.kanjianki.data.LocalStoreBase
import java.io.StringReader
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStudyRouteSmokeInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kanji_anki_simple.db")
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.study_route_no_anki")
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        context.deleteDatabase("kanji_anki_simple.db")
    }

    @Test
    fun flashcardAndWritingRoutesRenderProductionComposeScreens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val row = row("裂", "split", "レツ")
            scenario.onActivity { activity ->
                val flashcard = session(
                    row,
                    "flashcard-smoke",
                    BridgeScheduler.TASK_KANJI_MEANING,
                    false
                )
                activity.activeStudyPlan = plan("裂")
                activity.activeSession = flashcard
                activity.cancelPendingHomeRouteLoads()
                activity.startActiveStudyTask(
                    activity.sessionTaskKey(flashcard),
                    "裂",
                    flashcard.taskType,
                    System.currentTimeMillis()
                )
                activity.renderSession(flashcard)
            }

            assertVisible("Recognise")
            assertVisible("Name this kanji")
            clickVisible("Reveal")
            assertVisible(StudyReviewButtonCopy.againLabel())
            assertVisible(StudyReviewButtonCopy.goodLabel())
            scenario.onActivity { activity -> assertTrue(activity.flashcardAnswerRevealed) }

            scenario.onActivity { activity ->
                val writing = session(
                    row,
                    "writing-smoke",
                    BridgeScheduler.TASK_WRITE_KANJI,
                    true
                )
                activity.activeStudyPlan = plan("裂")
                activity.activeSession = writing
                activity.cancelPendingHomeRouteLoads()
                activity.startActiveStudyTask(
                    activity.sessionTaskKey(writing),
                    "裂",
                    writing.taskType,
                    System.currentTimeMillis()
                )
                activity.renderSession(writing)
            }

            assertVisible(MainActivityBase.LABEL_PRACTICE)
            assertVisible("Draw this kanji")
            assertVisible("Writing")
            assertVisible("Check")
            scenario.onActivity { activity ->
                assertNotNull(activity.drawingPad)
                assertNotNull(activity.studyStatus)
                assertFalse(activity.flashcardAnswerRevealed)
            }

            scenario.onActivity { activity ->
                seedSimilarChoiceRows(activity)
                val similarChoice = session(
                    row,
                    "choice-smoke",
                    BridgeScheduler.TASK_SIMILAR_KANJI,
                    false
                )
                activity.activeStudyPlan = plan("裂")
                activity.activeSession = similarChoice
                activity.cancelPendingHomeRouteLoads()
                activity.startActiveStudyTask(
                    activity.sessionTaskKey(similarChoice),
                    "裂",
                    similarChoice.taskType,
                    System.currentTimeMillis()
                )
                activity.renderSession(similarChoice)
            }

            assertVisible("Choose the kanji")
            assertVisible("Recognise")
            assertVisibleAfterScroll("Which kanji means Split, rend?")
            assertVisible("裂")
            assertVisible("列")

            scenario.onActivity { activity ->
                seedMeaningChoiceRows(activity)
                val meaningChoice = session(
                    row,
                    "meaning-choice-smoke",
                    BridgeScheduler.TASK_MEANING_KANJI,
                    false
                )
                activity.activeStudyPlan = plan("裂")
                activity.activeSession = meaningChoice
                activity.cancelPendingHomeRouteLoads()
                activity.startActiveStudyTask(
                    activity.sessionTaskKey(meaningChoice),
                    "裂",
                    meaningChoice.taskType,
                    System.currentTimeMillis()
                )
                activity.renderSession(meaningChoice)
            }

            assertVisible("Recall")
            assertVisible("Choose the kanji")
            assertVisibleAfterScroll("Which kanji means Split?")
            assertVisible("裂")
            assertVisible("烈")
        }
    }

    private fun plan(kanji: String): RecordsSchedulerModels.AdaptiveLoadPlan =
        RecordsSchedulerModels.AdaptiveLoadPlan(
            20,
            1,
            1,
            listOf(kanji),
            0,
            false,
            "One left"
        )

    private fun session(
        row: RecordsImportModels.DashboardRow,
        token: String,
        taskType: String,
        writingRequired: Boolean,
    ): RecordsSchedulerModels.StudySession {
        val item = RecordsStudyModels.StudyItem(
            row.kanji,
            "review",
            0L,
            1.0,
            5.0,
            1,
            0,
            0,
            1,
            0,
            0,
            0,
            false,
            "",
            0L,
            0,
            "sig-$token",
            token,
            0L
        )
        return RecordsSchedulerModels.StudySession(item, row, token, taskType, writingRequired, row.primaryMeaning)
    }

    private fun row(kanji: String, meaning: String, reading: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            1000,
            meaning,
            reading,
            kanji,
            10,
            "reason",
            "reason text",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>()
        )

    private fun seedSimilarChoiceRows(activity: MainActivity) {
        val rows = listOf(
            row("裂", "split", "レツ"),
            row("列", "row", "レツ")
        )
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                TestRecords.kikuNote(1L, "裂語", "レツ", "split", "裂を見た。"),
                TestRecords.kikuNote(2L, "列語", "レツ", "row", "列を見た。")
            ),
            listOf(
                TestRecords.kikuCard(10L, 1L).build(),
                TestRecords.kikuCard(20L, 2L).build()
            )
        )
        try {
            activity.store.saveSuccessfulSync(
                snapshot,
                emptyList(),
                rows,
                RecordsSyncModels.Settings.kikuDefaults(),
                LocalStoreBase.SyncTiming(3000L, 4000L),
                null,
                null
            )
            activity.store.rebuildSimilarKanjiPairs(
                SimilarKanjiIndex.parseTsv(StringReader("裂\t列\n")),
                System.currentTimeMillis()
            )
        } catch (error: Exception) {
            throw AssertionError(error)
        }
    }

    private fun seedMeaningChoiceRows(activity: MainActivity) {
        val rows = listOf(
            row("裂", "split", "レツ"),
            row("列", "row", "レツ"),
            row("烈", "ardent", "レツ"),
            row("劣", "inferior", "レツ")
        )
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                TestRecords.kikuNote(1L, "裂語", "レツ", "split", "裂を見た。"),
                TestRecords.kikuNote(2L, "列語", "レツ", "row", "列を見た。"),
                TestRecords.kikuNote(3L, "烈語", "レツ", "ardent", "烈を見た。"),
                TestRecords.kikuNote(4L, "劣語", "レツ", "inferior", "劣を見た。")
            ),
            listOf(
                TestRecords.kikuCard(10L, 1L).build(),
                TestRecords.kikuCard(20L, 2L).build(),
                TestRecords.kikuCard(30L, 3L).build(),
                TestRecords.kikuCard(40L, 4L).build()
            )
        )
        try {
            activity.store.saveSuccessfulSync(
                snapshot,
                emptyList(),
                rows,
                RecordsSyncModels.Settings.kikuDefaults(),
                LocalStoreBase.SyncTiming(3000L, 4000L),
                null,
                null
            )
        } catch (error: Exception) {
            throw AssertionError(error)
        }
    }

    private fun assertVisible(text: String) {
        val object2 = waitForText(text)
        assertNotNull("Missing visible text: $text", object2)
    }

    private fun assertVisibleAfterScroll(text: String) {
        waitForText(text)?.let { return }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(3) {
            val centerX = device.displayWidth / 2
            val startY = (device.displayHeight * 0.62f).toInt()
            val endY = (device.displayHeight * 0.28f).toInt()
            device.swipe(centerX, startY, centerX, endY, 12)
            device.waitForIdle(500L)
            waitForText(text)?.let { return }
        }
        assertNotNull("Missing visible text after scroll: $text", waitForText(text))
    }

    private fun clickVisible(text: String) {
        val object2 = waitForText(text)
        assertNotNull("Missing clickable text: $text", object2)
        var clickable = object2
        while (clickable != null && !clickable.isClickable) {
            clickable = clickable.parent
        }
        val clickableNode = requireNotNull(clickable) {
            "Visible text is not inside a clickable node: $text"
        }
        clickableNode.click()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2_000L)
    }

    private fun waitForText(text: String): UiObject2? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val pkg = instrumentation.targetContext.packageName
        val deadline = SystemClock.uptimeMillis() + TEXT_WAIT_TIMEOUT_MS

        instrumentation.waitForIdleSync()
        device.wait(Until.hasObject(By.pkg(pkg)), 2_000L)

        while (SystemClock.uptimeMillis() < deadline) {
            device.waitForIdle(500L)
            device.findObject(By.pkg(pkg).text(text))?.let { return it }
            device.findObject(By.pkg(pkg).textContains(text))?.let { return it }
            Thread.sleep(100L)
        }
        return null
    }

    private companion object {
        private const val TEXT_WAIT_TIMEOUT_MS = 8_000L
    }
}
