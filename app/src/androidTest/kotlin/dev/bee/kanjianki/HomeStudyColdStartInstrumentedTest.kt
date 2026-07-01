package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeStudySeedInstrumentedTest {
    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context.deleteDatabase(DB_NAME)
        seedRows()
    }

    @Test
    fun seedHomeStudyFixture() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val studyLabel = HomeTextCopy.studyNowLabel()
            val packageSelector = By.pkg(PACKAGE_NAME)
            val found = device.wait(Until.findObject(packageSelector.text(studyLabel)), 20_000L)
                ?: device.wait(Until.findObject(packageSelector.textContains(studyLabel)), 20_000L)
            assertNotNull("Seeded home screen did not expose the Study CTA", found)
        }
    }

    private fun seedRows() {
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
            "home-study-benchmark",
            "home study benchmark",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )

    companion object {
        private const val PACKAGE_NAME = "dev.bee.kanjianki"
        private const val DB_NAME = "kanji_anki_simple.db"
    }
}
