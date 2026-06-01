package dev.bee.kanjianki

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityGamesInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kanji_anki_simple.db")
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
        context.deleteDatabase("kanji_anki_simple.db")
    }

    @Test
    fun homeGamesButtonOpensPracticeOnlyHub() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.renderGames()

                assertTrue(containsText(activity.findViewById(android.R.id.content), "Home"))
                assertNotNull(findComposeView(activity.findViewById(android.R.id.content)))
            }
        }
    }

    @Test
    fun gameRoundEndsAfterTenAnswersWithoutSrsReview() {
        seedGameRows()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP)

                for (answer in 0 until 10) {
                    assertTrue(performFirstAnswerClick(activity.findViewById(android.R.id.content)))
                    if (answer < 9) {
                        assertTrue(performClickWithText(activity.findViewById(android.R.id.content), LABEL_NEXT))
                    }
                }

                assertTrue(containsText(activity.findViewById(android.R.id.content), "Round complete"))
                assertTrue(containsText(activity.findViewById(android.R.id.content), "Final score:"))
                assertTrue(containsText(activity.findViewById(android.R.id.content), LABEL_NEW_ROUND))
                assertFalse(containsText(activity.findViewById(android.R.id.content), LABEL_NEXT))
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

    private fun containsText(view: View, expected: String): Boolean {
        if (view is TextView) {
            val text = view.text.toString()
            if (text.contains(expected)) {
                return true
            }
        }
        if (view is androidx.compose.ui.platform.ComposeView && containsAccessibilityText(view.createAccessibilityNodeInfo(), expected)) {
            return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsText(view.getChildAt(i), expected)) {
                    return true
                }
            }
        }
        return false
    }

    private fun findClickable(view: View, expected: String): View? {
        if (view.isClickable && containsText(view, expected)) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findClickable(view.getChildAt(i), expected)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun findComposeView(view: View): View? {
        if (view is androidx.compose.ui.platform.ComposeView) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findComposeView(view.getChildAt(i))
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun containsAccessibilityText(node: AccessibilityNodeInfo?, expected: String): Boolean {
        if (node == null) {
            return false
        }
        val text = node.text?.toString()
        if (text != null && text.contains(expected)) {
            return true
        }
        val description = node.contentDescription?.toString()
        if (description != null && description.contains(expected)) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && containsAccessibilityText(child, expected)) {
                return true
            }
        }
        return false
    }

    private fun performFirstAnswerClick(root: View): Boolean = performFirstAnswerClick(root.createAccessibilityNodeInfo())

    private fun performFirstAnswerClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            return false
        }
        val text = nodeText(node)
        if (node.isClickable
            && text.isNotEmpty()
            && text != LABEL_NEXT
            && text != LABEL_GAMES
            && !text.startsWith("$LABEL_GAMES ")
            && text != LABEL_NEW_ROUND
        ) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (performFirstAnswerClick(child)) {
                return true
            }
        }
        return false
    }

    private fun performClickWithText(root: View, label: String): Boolean =
        performClickWithText(root.createAccessibilityNodeInfo(), label)

    private fun performClickWithText(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) {
            return false
        }
        if (node.isClickable && label == nodeText(node)) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (performClickWithText(child, label)) {
                return true
            }
        }
        return false
    }

    private fun nodeText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()
        if (text != null) {
            return text
        }
        val description = node.contentDescription?.toString()
        if (description != null) {
            return description
        }
        return ""
    }

    companion object {
        private const val LABEL_GAMES = "Games"
        private const val LABEL_NEXT = "Next"
        private const val LABEL_NEW_ROUND = "New round"
    }
}
