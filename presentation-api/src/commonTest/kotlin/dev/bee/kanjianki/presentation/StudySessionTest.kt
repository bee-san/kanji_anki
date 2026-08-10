package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudySessionTest {
    @Test
    fun aSessionAcceptsAGradeOnlyWhileACardIsUnanswered() {
        val ready = session(state = StudySessionState.CARD, card = flashcard())
        assertTrue(ready.acceptsGrade)

        // The double-commit guard: once a grade is in flight or applied, a second one
        // is not accepted, so a key-repeat or double-click cannot commit twice.
        assertFalse(
            ready.copy(feedback = StudyFeedback(StudyFeedbackPhase.SUBMITTING)).acceptsGrade,
        )
        assertFalse(
            ready.copy(feedback = StudyFeedback(StudyFeedbackPhase.APPLIED)).acceptsGrade,
        )
        // No card, no grade — the done and empty states cannot be graded.
        assertFalse(session(state = StudySessionState.DONE).acceptsGrade)
        assertFalse(session(state = StudySessionState.CARD, card = null).acceptsGrade)
    }

    @Test
    fun continueIsOfferedOnlyOnceAGradeHasBeenApplied() {
        val applied = session(
            state = StudySessionState.CARD,
            card = flashcard(),
            feedback = StudyFeedback(StudyFeedbackPhase.APPLIED, StudyOutcome.CORRECT),
        )
        assertTrue(applied.acceptsContinue)
        assertFalse(session(state = StudySessionState.CARD, card = flashcard()).acceptsContinue)
    }

    @Test
    fun feedbackIsInvisibleUntilACardIsAnswered() {
        assertFalse(StudyFeedback().visible)
        assertTrue(StudyFeedback(StudyFeedbackPhase.SUBMITTING).visible)
        assertTrue(StudyFeedback(StudyFeedbackPhase.APPLIED, StudyOutcome.INCORRECT).visible)
    }

    @Test
    fun progressShowsOnePastTheTargetWhileALearnAheadCardIsStillActive() {
        // "N of N+1" so a learn-ahead repeat past the target does not read as finished.
        val ahead = StudyProgress(completed = 5, target = 5, activeCard = true)
        assertEquals(6, ahead.displayedTarget)
        assertEquals(0, ahead.remaining)

        // At rest on the target, the displayed target is the real one.
        val rest = StudyProgress(completed = 5, target = 5, activeCard = false)
        assertEquals(5, rest.displayedTarget)

        val midway = StudyProgress(completed = 2, target = 5, activeCard = true)
        assertEquals(5, midway.displayedTarget)
        assertEquals(3, midway.remaining)
    }

    @Test
    fun progressRejectsImpossibleCounts() {
        assertFailsWith<IllegalArgumentException> { StudyProgress(completed = -1, target = 0) }
        assertFailsWith<IllegalArgumentException> { StudyProgress(completed = 3, target = 2) }
    }

    @Test
    fun aGradeActionCarriesItsRatingAndDispatchesIt() {
        val pass = StudyGradeAction(label = UiText.Literal("Pass"), rating = "good")
        assertEquals(KaniAction.Study.Grade(rating = "good"), pass.action)

        val fail = StudyGradeAction(label = UiText.Literal("Fail"), rating = "again")
        assertEquals(KaniAction.Study.Grade(rating = "again"), fail.action)
    }

    @Test
    fun aGradeNeedsARating() {
        assertFailsWith<IllegalArgumentException> {
            StudyGradeAction(label = UiText.Literal("Pass"), rating = " ")
        }
        assertFailsWith<IllegalArgumentException> { KaniAction.Study.Grade(rating = "") }
    }

    @Test
    fun aFlashcardOffersPassAndFailAndKeepsItsSubject() {
        val card = flashcard()
        assertEquals("脱", card.subject)
        assertEquals("good", card.pass.rating)
        assertEquals("again", card.fail.rating)
        assertNull(card.details)
    }

    @Test
    fun aChoiceCardNeedsChoicesAndEachChoiceNeedsAValue() {
        assertFailsWith<IllegalArgumentException> {
            StudyCard.Choice(
                prompt = UiText.Literal("Which kanji?"),
                subject = "脱",
                choices = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            StudyChoice(value = " ", grade = grade("good"))
        }
    }

    @Test
    fun aWritingCardShowsSaveHardOnlyWhenTheExceptionApplies() {
        val plain = StudyCard.Writing(
            prompt = UiText.Literal("Write 脱"),
            subject = "脱",
            pass = grade("good"),
            fail = grade("again"),
        )
        assertNull(plain.saveHard)

        // The one documented exception: a CLOSE ink attempt is "Save hard", submitting
        // hard, which still counts as a pass.
        val close = plain.copy(saveHard = StudyGradeAction(UiText.Literal("Save hard"), "hard"))
        assertEquals("hard", close.saveHard?.rating)
    }

    @Test
    fun answerDetailsCarryAHeadingAndItsLines() {
        val details = StudyAnswerDetails(
            heading = UiText.Literal("だつ"),
            lines = listOf(UiText.Literal("take off"), UiText.Literal("脱出 escape")),
        )
        val card = flashcard().copy(details = details)

        assertEquals(details, card.details)
        assertEquals(2, card.details?.lines?.size)
        // The default is empty rather than absent, so a card with no details still
        // constructs one the surface can skip.
        assertEquals(UiText.EMPTY, StudyAnswerDetails().heading)
    }

    @Test
    fun aTypedCardCarriesOneSubmitTheHostGrades() {
        val card = StudyCard.Typed(
            prompt = UiText.Literal("Type the meaning"),
            subject = "脱",
            answer = UiText.Literal("take off"),
            submit = grade("good"),
        )
        // The card does not know if the input matches — the host resolves the submit to
        // good or again — so it carries a single grade action, not a pass/fail pair.
        assertEquals("脱", card.subject)
        assertEquals("good", card.submit.rating)
    }

    private fun session(
        state: StudySessionState,
        card: StudyCard? = null,
        feedback: StudyFeedback = StudyFeedback(),
    ) = StudySession(state = state, card = card, feedback = feedback)

    private fun flashcard() = StudyCard.Flashcard(
        prompt = UiText.Literal("脱"),
        subject = "脱",
        answer = UiText.Literal("take off"),
        pass = grade("good"),
        fail = grade("again"),
    )

    private fun grade(rating: String) = StudyGradeAction(label = UiText.Literal(rating), rating = rating)
}
