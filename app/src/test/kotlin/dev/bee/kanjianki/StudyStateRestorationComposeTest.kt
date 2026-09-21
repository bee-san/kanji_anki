package dev.bee.kanjianki

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.bee.kanjianki.presentation.StudyCard
import dev.bee.kanjianki.presentation.StudyFeedback
import dev.bee.kanjianki.presentation.StudyGradeAction
import dev.bee.kanjianki.presentation.StudyProgress
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.StudySessionState
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.study.STUDY_REVEAL_TEST_TAG
import dev.bee.kanjianki.study.STUDY_TYPING_INPUT_TEST_TAG
import dev.bee.kanjianki.study.StudyCopy
import dev.bee.kanjianki.study.StudySessionScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a study card keeps across an activity recreation.
 *
 * Here in `:app` rather than beside the other Study render tests in `:feature-study`,
 * because [StateRestorationTester] has no multiplatform equivalent — the shared
 * `runComposeUiTest` harness cannot emulate a saved-state restore — and adding
 * `ui-test-junit4` to the KMP convention's `androidHostTest` set pulls a transitive
 * `activity-ktx` with no recorded checksum, which would mean running the three-host
 * verification-metadata bootstrap for two tests. `:app` already has the artifact and
 * already sees `:feature-study` through `:feature-shell`.
 *
 * Android-only, and not a parity gap: it is Android that recreates the activity on a
 * rotation or after killing a backgrounded process, so there is nothing here for the
 * desktop twin to prove.
 *
 * These exist because the old `MainActivity` chain persisted the typed answer to
 * `SharedPreferences` on every keystroke and flushed it at `onPause`, while the shared
 * surfaces used a plain `remember` — so the Goal 199 port silently regressed a rotation
 * from "your answer is still there" to "start typing again". A `remember` reads
 * identically to a `rememberSaveable` in every other test, which is what makes this worth
 * pinning rather than trusting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyStateRestorationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aHalfTypedAnswerSurvivesAnActivityRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            StudySessionScreen(cardSession(typedCard()), studyCopy(), LiteralResolver, dispatch = {})
        }

        composeRule.onNodeWithTag(STUDY_TYPING_INPUT_TEST_TAG).performTextInput("take of")
        restoration.emulateSavedInstanceStateRestore()

        // Rotating mid-answer must not throw the answer away.
        composeRule.onNodeWithTag(STUDY_TYPING_INPUT_TEST_TAG).assertTextContains("take of")
    }

    @Test
    fun aRevealedCardStaysFaceUpAcrossAnActivityRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            StudySessionScreen(cardSession(flashcard()), studyCopy(), LiteralResolver, dispatch = {})
        }

        composeRule.onNodeWithTag(STUDY_REVEAL_TEST_TAG).performClick()
        restoration.emulateSavedInstanceStateRestore()

        // Re-hiding an answer the user has already read invites them to grade their recall
        // of a card they just looked at. The reveal button being gone is the card being
        // face up: the surface swaps it for the grade buttons.
        composeRule.onNodeWithTag(STUDY_REVEAL_TEST_TAG).assertDoesNotExist()
    }

    private fun cardSession(card: StudyCard): StudySession = StudySession(
        state = StudySessionState.CARD,
        progress = StudyProgress(),
        card = card,
        feedback = StudyFeedback(),
    )

    private fun typedCard(): StudyCard.Typed = StudyCard.Typed(
        prompt = UiText.Literal("Type the meaning of 脱"),
        subject = "脱",
        answer = UiText.Literal("take off"),
        submit = grade("Submit", "good"),
        inputLabel = UiText.Literal("Meaning"),
    )

    private fun flashcard(): StudyCard.Flashcard = StudyCard.Flashcard(
        prompt = UiText.Literal("脱"),
        subject = "脱",
        answer = UiText.Literal("take off"),
        pass = grade("Pass", "good"),
        fail = grade("Fail", "again"),
    )

    private fun grade(label: String, rating: String) =
        StudyGradeAction(label = UiText.Literal(label), rating = rating)

    private fun studyCopy(): StudyCopy = StudyCopy(
        pass = "Pass",
        fail = "Fail",
        cont = "Continue",
        reveal = "Show answer",
        submit = "Submit",
        undo = "Undo",
        clear = "Clear",
        doneTitle = "Session complete",
        doneBody = "You finished.",
        doneHome = "Back to home",
        emptyTitle = "Nothing to study",
        emptyBody = "No card is due.",
        progressTemplate = "%1\$d of %2\$d",
    )

    /** Resolves a [UiText] to its literal; none of these assertions read resource copy. */
    private object LiteralResolver : UiTextResolver {
        override fun resolve(text: UiText): String = when (text) {
            is UiText.Literal -> text.text
            is UiText.Key, is UiText.Quantity -> ""
        }
    }
}
