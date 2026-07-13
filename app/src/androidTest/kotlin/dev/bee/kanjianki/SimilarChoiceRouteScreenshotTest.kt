package dev.bee.kanjianki

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.StudyTextCopy
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

class SimilarChoiceRouteScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun capturesSimilarChoiceRouteCollapsedAndExpandedScreenshots() {
        val model = SimilarChoiceSessionModel(
            modeLabel = "Recognise",
            question = "Which kanji means split?",
            gridModel = SimilarChoiceGridModel(
                choices = listOf("裂", "列", "烈"),
                balanceLastRow = false,
                onChoice = KanjiChoiceHandler { true },
            ),
            explanationLines = listOf(
                SimilarKanjiExplanationLineModel("Compare shapes", "裂 vs 列", true),
                SimilarKanjiExplanationLineModel("Seen in", "source one • source two"),
                SimilarKanjiExplanationLineModel("Meaning hint", "split • tear • rend"),
                SimilarKanjiExplanationLineModel("Reading hint", "れつ"),
                SimilarKanjiExplanationLineModel("Shared part", "刀"),
                SimilarKanjiExplanationLineModel("Different part", "衣 vs 歹"),
                SimilarKanjiExplanationLineModel("Shape hint", "Look closely at the lower component before choosing.", true),
            ),
        )

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(640.dp)
                    .testTag(PHONE_VIEWPORT_TAG)
            ) {
                MainActivityComposeRoute(
                    model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                    navActions = KaniNavActions({}, {}, {}, {}),
                    content = {
                        SimilarChoiceSessionCard(
                            model = model,
                            showInlineChoices = true,
                            detailsExpandedByDefault = false,
                        )
                    },
                )
            }
        }

        val choiceTag = similarChoiceTestTag("裂")
        composeRule.onNodeWithText("Which kanji means split?").assertIsDisplayed()
        composeRule.onNodeWithText("Compare shapes: 裂 vs 列").assertIsDisplayed()
        composeRule.onNodeWithTag(choiceTag).assertIsDisplayed()
        composeRule.onNodeWithTag(SIMILAR_KANJI_DETAILS_TOGGLE_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText("Seen in").assertCountEquals(0)
        composeRule.onAllNodesWithText("Meaning hint").assertCountEquals(0)
        composeRule.onAllNodesWithText("Reading hint").assertCountEquals(0)
        composeRule.onAllNodesWithText("Shape hint").assertCountEquals(0)

        captureScreenshot("01-collapsed.png")

        composeRule.onNodeWithTag(SIMILAR_KANJI_DETAILS_TOGGLE_TAG).performClick()
        composeRule.onNodeWithText(StudyTextCopy.similarKanjiHideDetailsLabel()).assertIsDisplayed()
        composeRule.onNodeWithText("Seen in: source one • source two").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Meaning hint: split • tear • rend").performScrollTo().assertIsDisplayed()

        captureScreenshot("02-expanded.png")
    }

    private fun captureScreenshot(fileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshotDir = File(context.getExternalFilesDir(null), "similar-choice-route-screenshots")
        screenshotDir.mkdirs()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(screenshotDir, fileName)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private companion object {
        private const val PHONE_VIEWPORT_TAG = "similar-choice-phone-viewport"
    }
}
