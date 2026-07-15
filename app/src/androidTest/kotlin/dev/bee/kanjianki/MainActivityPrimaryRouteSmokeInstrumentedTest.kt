package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.testing.DeviceRisk
import dev.bee.kanjianki.testing.DeviceSmoke
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@DeviceSmoke
@DeviceRisk
class MainActivityPrimaryRouteSmokeInstrumentedTest {
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
            assertVisible("Browse Kanji")
            assertVisible("Stats")
            assertVisibleInScrollableRoute("Games")

            scenario.onActivity { it.renderSettings() }
            assertVisible(MainActivityBase.NAV_SETTINGS)
            assertVisible("Import & sync")
            assertVisible("Study settings")

            scenario.onActivity { activity -> activity.renderBrowseKanji("裂") }
            assertVisible("Browse Kanji")
            assertVisible("split")
            assertVisible("local source")

            scenario.onActivity { activity -> activity.renderDetail("裂", true, "裂") }
            assertVisible("Back to Browse")
            assertVisible("裂")

            scenario.onActivity { it.renderStats() }
            assertVisibleInScrollableRoute("Stats overview")
            assertVisibleInScrollableRoute("Reviews analytics")
            assertVisibleInScrollableRoute("Weakness insights")

            scenario.onActivity { it.renderGames() }
            assertVisible("Games")
            assertVisible("Meaning Pop")

            scenario.onActivity { it.renderUpdate() }
            assertVisible("App updates")
            assertVisible("Check for updates")
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
        val object2 = waitForText(text)
        assertNotNull("Missing visible text: $text", object2)
    }

    private fun assertVisibleInScrollableRoute(text: String) {
        val object2 = waitForTextInScrollableRoute(text)
        assertNotNull("Missing visible text after route scroll: $text", object2)
    }

    private fun waitForTextInScrollableRoute(text: String): UiObject2? {
        waitForText(text)?.let { return it }

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        scrollRouteToTop(device)
        waitForText(text, 750L)?.let { return it }

        runCatching {
            UiScrollable(UiSelector().scrollable(true))
                .setAsVerticalList()
                .scrollIntoView(UiSelector().textContains(text))
        }
        waitForText(text, 750L)?.let { return it }

        repeat(20) {
            scrollRouteDown(device)
            waitForText(text, 750L)?.let { return it }
        }

        return null
    }

    private fun scrollRouteToTop(device: UiDevice) {
        repeat(4) {
            device.swipe(routeSwipeX(device), routeSwipeTopY(device), routeSwipeX(device), routeSwipeBottomY(device), 18)
            device.waitForIdle()
        }
    }

    private fun scrollRouteDown(device: UiDevice) {
        device.swipe(routeSwipeX(device), routeSwipeBottomY(device), routeSwipeX(device), routeSwipeTopY(device), 18)
        device.waitForIdle()
    }

    private fun routeSwipeX(device: UiDevice): Int = device.displayWidth / 2

    private fun routeSwipeTopY(device: UiDevice): Int = device.displayHeight * 3 / 10

    private fun routeSwipeBottomY(device: UiDevice): Int = device.displayHeight * 7 / 10

    private fun waitForText(text: String, timeoutMs: Long = 3_000L): UiObject2? {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val exact = device.wait(Until.findObject(By.pkg(pkg).text(text)), timeoutMs)
        if (exact != null) return exact
        return device.wait(Until.findObject(By.pkg(pkg).textContains(text)), timeoutMs)
    }
}
