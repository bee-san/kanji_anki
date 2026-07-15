package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.StudyReviewButtonCopy
import dev.bee.kanjianki.core.StudyTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyFlowComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun flashcardActionBarShowsRevealButtonWhenNotRevealed() {
        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = false,
                onReveal = {},
                onFail = {},
                onPass = {},
            )
        }
        composeRule.onNodeWithText(StudyReviewButtonCopy.revealLabel()).assertIsDisplayed()
    }

    @Test
    fun flashcardActionBarShowsPassAndFailWhenRevealed() {
        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = {},
                onPass = {},
            )
        }
        composeRule.onNodeWithText(StudyReviewButtonCopy.againLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(StudyReviewButtonCopy.goodLabel()).assertIsDisplayed()
    }

    @Test
    fun flashcardPassButtonInvokesCallback() {
        var passed = false
        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = {},
                onPass = { passed = true },
            )
        }
        composeRule.onNodeWithText(StudyReviewButtonCopy.goodLabel()).performClick()
        assertTrue(passed)
    }

    @Test
    fun flashcardFailButtonInvokesCallback() {
        var failed = false
        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = { failed = true },
                onPass = {},
            )
        }
        composeRule.onNodeWithText(StudyReviewButtonCopy.againLabel()).performClick()
        assertTrue(failed)
    }

    @Test
    fun doneScreenRendersTitleAndSummary() {
        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = "Focus",
                    title = "Session complete",
                    headline = null,
                    body = "You reviewed 10 cards",
                    summaryLines = listOf("5 correct", "3 incorrect"),
                    showDoneActions = true,
                    availableStudyMoreNewCards = 0,
                    showBackHome = true,
                    backHomePrimary = false,
                    onStudyMore = Runnable {},
                    onContinueAll = Runnable {},
                    onBackHome = Runnable {},
                ),
            )
        }
        composeRule.onNodeWithText("Session complete").assertIsDisplayed()
        composeRule.onNodeWithText("You reviewed 10 cards").assertIsDisplayed()
        composeRule.onNodeWithText("5 correct").assertIsDisplayed()
    }

    @Test
    fun doneScreenShowsBackHomeButton() {
        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = "Focus",
                    title = "Done!",
                    headline = null,
                    body = "Good work",
                    summaryLines = emptyList(),
                    showDoneActions = true,
                    availableStudyMoreNewCards = 0,
                    showBackHome = true,
                    backHomePrimary = false,
                    onStudyMore = Runnable {},
                    onContinueAll = Runnable {},
                    onBackHome = Runnable {},
                ),
            )
        }
        composeRule.onNodeWithText(StudyTextCopy.backHomeLabel()).assertIsDisplayed()
    }

    @Test
    fun doneScreenShowsStudyMoreWhenNewCardsAvailable() {
        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = "Focus",
                    title = "Done!",
                    headline = null,
                    body = "Good work",
                    summaryLines = emptyList(),
                    showDoneActions = true,
                    availableStudyMoreNewCards = 5,
                    showBackHome = true,
                    backHomePrimary = false,
                    onStudyMore = Runnable {},
                    onContinueAll = Runnable {},
                    onBackHome = Runnable {},
                ),
            )
        }
        composeRule.onNodeWithText(StudyTextCopy.studyMoreNewCardsLabel()).assertIsDisplayed()
    }

    @Test
    fun doneScreenBackHomeCallbackWorks() {
        var backHome = false
        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = "Focus",
                    title = "Done!",
                    headline = null,
                    body = "Good work",
                    summaryLines = emptyList(),
                    showDoneActions = true,
                    availableStudyMoreNewCards = 0,
                    showBackHome = true,
                    backHomePrimary = false,
                    onStudyMore = Runnable {},
                    onContinueAll = Runnable {},
                    onBackHome = Runnable { backHome = true },
                ),
            )
        }
        composeRule.onNodeWithText(StudyTextCopy.backHomeLabel()).performClick()
        assertTrue(backHome)
    }
}
