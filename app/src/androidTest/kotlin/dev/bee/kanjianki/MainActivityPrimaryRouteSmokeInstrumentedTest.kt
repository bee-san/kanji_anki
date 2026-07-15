package dev.bee.kanjianki

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.testing.DeviceRisk
import dev.bee.kanjianki.testing.DeviceSmoke
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@DeviceSmoke
@DeviceRisk
class MainActivityPrimaryRouteSmokeInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kanji_anki_simple.db")
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.primary_route_no_anki")
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(false)
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
        if (::context.isInitialized) {
            context.deleteDatabase("kanji_anki_simple.db")
        }
    }

    @Test
    fun primaryRoutesRenderProductionComposeScreens() {
        seedRows()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.renderHome() }
            assertVisibleInScrollableRoute("Browse Kanji")
            assertVisibleInScrollableRoute("Games")

            scenario.onActivity { it.renderSettings() }
            assertVisibleInScrollableRoute("Import & sync")
            assertVisibleInScrollableRoute("Study settings")

            scenario.onActivity { activity -> activity.renderBrowseKanji("裂") }
            assertVisible("Browse Kanji")
            assertVisibleInScrollableRoute("split")
            assertVisibleInScrollableRoute("local source")

            scenario.onActivity { activity -> activity.renderDetail("裂", true, "裂") }
            assertVisible("Back to Browse")
            assertVisible("裂")

            scenario.onActivity { it.renderStats() }
            assertVisibleInScrollableRoute("Stats overview")
            assertVisibleInScrollableRoute("Reviews analytics")
            assertVisibleInScrollableRoute("Weakness insights")

            scenario.onActivity { it.renderGames() }
            assertVisible("Games")
            assertVisibleInScrollableRoute("Meaning Pop")

            scenario.onActivity { it.renderUpdate() }
            assertVisible("App updates")
            assertVisibleInScrollableRoute("Check for updates")
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
                LocalStoreBase.SyncTiming(1000L, 2000L),
                null,
                null,
            )
        }
    }

    private fun row(kanji: String, meaning: String, reading: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            1000,
            meaning,
            reading,
            kanji,
            10,
            "route_smoke",
            "route smoke",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )

    private fun assertVisible(text: String) {
        waitForText(text)
        composeRule.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed()
    }

    private fun assertVisibleInScrollableRoute(text: String) {
        waitForText(text)
        composeRule.onAllNodes(hasText(text, substring = true)).onFirst()
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        try {
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                composeRule.onAllNodes(hasText(text, substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: AssertionError) {
            throw AssertionError("Timed out waiting for Compose text: $text", error)
        }
    }

    private companion object {
        private const val UI_TIMEOUT_MILLIS = 30_000L
    }
}
