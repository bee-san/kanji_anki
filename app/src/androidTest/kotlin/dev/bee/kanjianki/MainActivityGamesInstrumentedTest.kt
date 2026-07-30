package dev.bee.kanjianki

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityGamesInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        KaniTestDatabase.delete(context)
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.games_no_anki")
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        KaniTestDatabase.delete(context)
    }

    @Test
    fun homeGamesButtonOpensPracticeOnlyHub() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.renderGames()
            }
            assertTextVisible("Home")
            assertTextVisible("Games")
        }
    }

    @Test
    fun gameRoundEndsAfterTenAnswersWithoutSrsReview() {
        seedGameRows()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP)
            }

            for (answer in 0 until 10) {
                clickTag(gamesChoiceButtonTestTag(PRIMARY_GAME_ANSWER_LABEL))
                if (answer < 9) {
                    clickTag(gamesResultPrimaryButtonTestTag(LABEL_NEXT))
                }
            }

            assertTextVisible("Round complete")
            assertTextContainingVisible("Score:")
            assertTextVisible(LABEL_NEW_ROUND)
            composeRule.onAllNodes(hasTestTag(gamesResultPrimaryButtonTestTag(LABEL_NEXT)), useUnmergedTree = true)
                .assertCountEquals(0)

            scenario.onActivity { activity ->
                assertEquals(0, activity.store.reviewStatsSince(0L).total)
            }
        }
    }

    private fun seedGameRows() {
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                emptyList(),
                listOf(
                    dashboardRow("裂", "split", "れつ"),
                    dashboardRow("提", "present", "てい"),
                    dashboardRow("語", "language", "ご")
                ),
                RecordsSyncModels.Settings.kikuDefaults(),
                1L,
                2L,
                null
            )
        }
    }

    private fun dashboardRow(kanji: String, meaning: String, reading: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            100,
            meaning,
            reading,
            kanji,
            5,
            "game_fixture",
            "game fixture",
            1,
            0,
            0,
            listOf(RecordsImportModels.Example("active", 1L, 1L, "${kanji}語", reading, meaning, "", false, 1))
        )

    private fun assertTextVisible(text: String) {
        waitForText(text)
        composeRule.onAllNodes(hasText(text)).onFirst().assertIsDisplayed()
    }

    private fun assertTextContainingVisible(text: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(text, substring = true)).assertIsDisplayed()
    }

    private fun clickTag(tag: String) {
        waitForTag(tag)
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        try {
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: AssertionError) {
            throw AssertionError("Timed out waiting for Compose test tag $tag\n" + composeTree(), error)
        }
    }

    private fun composeTree(): String = composeRule.onRoot(useUnmergedTree = true).printToString()

    companion object {
        private const val LABEL_NEXT = "Next"
        private const val LABEL_NEW_ROUND = "New round"
        private const val PRIMARY_GAME_ANSWER_LABEL = "split"
        private const val UI_TIMEOUT_MILLIS = 5_000L
    }
}
