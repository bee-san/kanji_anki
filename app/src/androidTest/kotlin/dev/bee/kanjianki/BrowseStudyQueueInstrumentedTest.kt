package dev.bee.kanjianki

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.testing.DeviceRisk
import dev.bee.kanjianki.testing.DeviceSmoke
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@DeviceSmoke
@DeviceRisk
class BrowseStudyQueueInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.browse_queue_no_anki")
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(false)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        seedSuspendedStudyItem()
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
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    @Test
    fun suspendedScopeSurvivesDetailBackAndReactivation() {
        val backToBrowseTag = homeFullWidthHomeButtonTestTag(HomeTextCopy.backToBrowseKanjiLabel())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Stroke-order rendering is outside this regression and can contend with
                // cold asset parsing on older device jobs.
                activity.strokeGuides = emptyMap()
                activity.renderBrowseKanji("裂")
            }
            waitForText("No local kanji found")
            composeRule.onAllNodesWithTag(browseKanjiRowTestTag("裂")).assertCountEquals(0)

            composeRule.onNodeWithTag(browseShowSuspendedTestTag())
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            waitForTag(browseKanjiRowTestTag("裂"))
            composeRule.onNodeWithTag(browseKanjiRowTestTag("裂"))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()

            waitForTag(backToBrowseTag)
            assertDetailScope(scenario)

            composeRule.onNodeWithTag(backToBrowseTag)
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            waitForTag(browseKanjiRowTestTag("裂"))
            composeRule.onNodeWithTag(browseKanjiRowTestTag("裂"))
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText("SUSPENDED").assertIsDisplayed()
            scenario.onActivity { activity ->
                val route = requireNotNull(activity.currentHomeRouteRestoration)
                assertEquals(HomeRouteRestoration.Destination.BROWSE, route.destination)
                assertEquals("裂", route.query)
                assertTrue(route.showSuspended)
            }

            composeRule.onNodeWithTag(browseKanjiRowTestTag("裂")).performClick()
            waitForText("Unsuspend locally")
            composeRule.onNodeWithText("Unsuspend locally").performClick()
            waitForText("Suspend locally")
            scenario.onActivity { activity ->
                assertFalse(activity.store.isKanjiLocallySuspended("裂"))
                assertTrue(activity.currentHomeRouteRestoration?.showSuspended == true)
            }
        }
    }

    private fun assertDetailScope(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val route = requireNotNull(activity.currentHomeRouteRestoration)
            assertEquals(HomeRouteRestoration.Destination.DETAIL, route.destination)
            assertEquals("裂", route.query)
            assertEquals("裂", route.kanji)
            assertTrue(route.showSuspended)
        }
    }

    private fun seedSuspendedStudyItem() {
        val row = RecordsImportModels.DashboardRow(
            "裂",
            1_000,
            "split",
            "レツ",
            "裂",
            10,
            "browse_queue",
            "browse queue",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                    listOf(TestRecords.kikuNote(1L, "裂語", "レツ", "split", "裂を見た。")),
                    listOf(TestRecords.kikuCard(10L, 1L).build()),
                ),
                emptyList(),
                listOf(row),
                RecordsSyncModels.Settings.kikuDefaults(),
                LocalStoreBase.SyncTiming(1_000L, 2_000L),
                null,
                null,
            )
            store.saveStudyItem(
                RecordsStudyModels.StudyItem(
                    "裂",
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
                    0L,
                    false,
                    "",
                    0L,
                    0,
                    "browse-queue-signature",
                    "browse-queue-token",
                    0L,
                ).withRung(RecordsBase.LadderRung.KANJI_MEANING)
            )
            store.setKanjiLocallySuspended("裂", true, 1_000L)
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text, substring = true))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onAllNodes(hasText(text, substring = true)).onFirst()
            .performScrollTo()
            .assertIsDisplayed()
    }

    private companion object {
        private const val DATABASE_NAME = "kanji_anki_simple.db"
        private const val UI_TIMEOUT_MILLIS = 30_000L
    }
}
