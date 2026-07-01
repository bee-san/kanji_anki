package dev.bee.kanjianki

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MainActivityWritingRouteSmokeInstrumentedTest {
    @Before
    fun setUp() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
        seedRows()
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
    }

    @Test
    fun writingRouteInitializesDrawingPadAndStatus() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val row = row("裂", "split", "レツ")
                val writing = session(
                    row,
                    "writing-smoke",
                    BridgeScheduler.TASK_WRITE_KANJI,
                    true
                )
                activity.activeStudyPlan = plan("裂")
                activity.activeSession = writing
                activity.startActiveStudyTask(
                    activity.sessionTaskKey(writing),
                    "裂",
                    writing.taskType,
                    System.currentTimeMillis()
                )
                activity.renderSession(writing)
            }

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.waitForIdle(10_000L)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val debugDump = File(context.getExternalFilesDir(null), "writing-route-debug.txt")
            val debugShot = File(context.getExternalFilesDir(null), "writing-route-debug.png")
            debugDump.writeText(device.executeShellCommand("uiautomator dump --compressed /sdcard/Android/data/dev.bee.kanjianki/files/writing-route-debug.xml"))
            device.executeShellCommand("screencap -p ${debugShot.absolutePath}")
            assertTrue(debugDump.exists())
            assertTrue(debugShot.exists())

            scenario.onActivity { activity ->
                assertNotNull(activity.drawingPad)
                assertNotNull(activity.studyStatus)
                assertNotNull(activity.activeSession)
            }
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

    private fun seedRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                    listOf(
                        TestRecords.kikuNote(1L, "裂語", "レツ", "split", "裂を見た。"),
                        TestRecords.kikuNote(2L, "列語", "レツ", "row", "列を見た。"),
                        TestRecords.kikuNote(3L, "語学", "ゴ", "language", "語を見た。"),
                    ),
                    listOf(
                        TestRecords.kikuCard(10L, 1L).build(),
                        TestRecords.kikuCard(20L, 2L).build(),
                        TestRecords.kikuCard(30L, 3L).build(),
                    ),
                ),
                emptyList(),
                listOf(
                    row("裂", "split", "レツ"),
                    row("列", "row", "レツ"),
                    row("語", "language", "ゴ"),
                ),
                RecordsSyncModels.Settings.kikuDefaults(),
                LocalStoreBase.SyncTiming(1_000L, 2_000L),
                null,
                null,
            )
        }
    }

    private fun row(kanji: String, meaning: String, reading: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            1_000,
            meaning,
            reading,
            kanji,
            10,
            "writing-route-smoke",
            "writing route smoke",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )

    companion object {
        private const val DB_NAME = "kanji_anki_simple.db"
    }
}
