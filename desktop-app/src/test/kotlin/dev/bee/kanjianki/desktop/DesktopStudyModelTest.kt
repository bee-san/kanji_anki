package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.StudyAnswerFeedbackPhase
import dev.bee.kanjianki.StudyAnswerFeedbackSnapshot
import dev.bee.kanjianki.StudyAnswerOutcome
import dev.bee.kanjianki.StudyRouteSnapshot
import dev.bee.kanjianki.StudySessionPhase
import dev.bee.kanjianki.StudySessionProgressUiState
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.presentation.StudyCard
import dev.bee.kanjianki.presentation.StudyFeedbackPhase
import dev.bee.kanjianki.presentation.StudyOutcome
import dev.bee.kanjianki.presentation.StudySessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop half of Goal 195's Study parity: the mapping feeds the shared surface
 * through the same `:core` copy Android calls, so the surface's own render tests
 * (which run on both hosts) prove the two match. This checks the derivation the
 * mapping owns — which card variant a task type becomes, the grade ratings, the
 * feedback translation, and the loading/done/empty state.
 */
class DesktopStudyModelTest {
    @Test
    fun aRecognitionTaskBecomesASelfGradedFlashcardGradingGoodAndAgain() {
        val model = DesktopStudyModel.session(
            session(StudyTaskTypes.KANJI_MEANING),
            route(StudySessionPhase.ACTIVE),
            undoable = false,
        )

        val card = model.card
        assertTrue("expected a flashcard, was $card", card is StudyCard.Flashcard)
        card as StudyCard.Flashcard
        assertEquals("good", card.pass.rating)
        assertEquals("again", card.fail.rating)
        assertEquals("脱", card.subject)
    }

    @Test
    fun aTypingTaskBecomesATypedCardWithOneSubmit() {
        val meaning = DesktopStudyModel.session(session(StudyTaskTypes.TYPE_MEANING), route(StudySessionPhase.ACTIVE), false)
        assertTrue(meaning.card is StudyCard.Typed)
        assertEquals("good", (meaning.card as StudyCard.Typed).submit.rating)

        val reading = DesktopStudyModel.session(session(StudyTaskTypes.TYPE_READING), route(StudySessionPhase.ACTIVE), false)
        assertTrue(reading.card is StudyCard.Typed)
    }

    @Test
    fun aSentenceReadingTaskEmphasizesItsSmallSentenceFront() {
        val model = DesktopStudyModel.session(session(StudyTaskTypes.SENTENCE_READING), route(StudySessionPhase.ACTIVE), false)
        val card = model.card
        assertTrue(card is StudyCard.Flashcard)
        assertTrue("the sentence front is small", (card as StudyCard.Flashcard).emphasizeSubjectInPrompt)
    }

    @Test
    fun aWritingTaskBecomesAWritingCardWithOnlyPassAndFail() {
        val model = DesktopStudyModel.session(session(StudyTaskTypes.WRITE_KANJI, writing = true), route(StudySessionPhase.ACTIVE), false)
        val card = model.card
        assertTrue(card is StudyCard.Writing)
        card as StudyCard.Writing
        assertEquals("good", card.pass.rating)
        assertEquals("again", card.fail.rating)
        // No Save hard from a plain mapping — that is the evaluator's CLOSE verdict,
        // which the ink surface supplies in Goal 196.
        assertNull(card.saveHard)
    }

    @Test
    fun aWordReadingCardRevealsAReadingRatherThanAMeaning() {
        val model = DesktopStudyModel.session(session(StudyTaskTypes.WORD_READING), route(StudySessionPhase.ACTIVE), false)
        val card = model.card
        assertTrue(card is StudyCard.Flashcard)
        // The reading task asks for the reading; the answer is the reading, not the
        // meaning. The example row carries "だつ", which is what a word-reading card
        // reveals.
        assertEquals("だつ", (card as StudyCard.Flashcard).answer.literalText())
    }

    @Test
    fun aDrainedQueueIsEmptyAndALoadingRouteIsLoading() {
        assertEquals(
            StudySessionState.LOADING,
            DesktopStudyModel.session(null, route(StudySessionPhase.LOADING), false).state,
        )
        assertEquals(
            StudySessionState.EMPTY,
            DesktopStudyModel.session(null, route(StudySessionPhase.IDLE), false).state,
        )
    }

    @Test
    fun aCompletedRouteIsDone() {
        // The completion arithmetic is StudyRouteSnapshot's: complete phase, target
        // met, no active task, and feedback past submitting.
        val complete = StudyRouteSnapshot(
            phase = StudySessionPhase.COMPLETE,
            progress = StudySessionProgressUiState(targetCount = 3, completedCount = 3, activeTask = false),
        )
        assertEquals(StudySessionState.DONE, DesktopStudyModel.session(null, complete, false).state)
    }

    @Test
    fun feedbackTranslatesPhaseAndOutcomeOneForOne() {
        val model = DesktopStudyModel.session(
            session(StudyTaskTypes.KANJI_MEANING),
            route(
                StudySessionPhase.FEEDBACK,
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = "token",
                    phase = StudyAnswerFeedbackPhase.APPLIED,
                    outcome = StudyAnswerOutcome.INCORRECT,
                    selectedAnswer = "説",
                ),
            ),
            undoable = false,
        )

        assertEquals(StudyFeedbackPhase.APPLIED, model.feedback.phase)
        assertEquals(StudyOutcome.INCORRECT, model.feedback.outcome)
        assertEquals("説", model.feedback.selected)
    }

    @Test
    fun theProgressCarriesTheSnapshotCountsThroughToTheDisplayedTarget() {
        val model = DesktopStudyModel.session(
            session(StudyTaskTypes.KANJI_MEANING),
            route(
                StudySessionPhase.ACTIVE,
                progress = StudySessionProgressUiState(targetCount = 5, completedCount = 5, activeTask = true),
            ),
            undoable = false,
        )
        assertEquals(5, model.progress.completed)
        // One past the target while a learn-ahead card is still active.
        assertEquals(6, model.progress.displayedTarget)
    }

    @Test
    fun undoIsCarriedFromTheHostReport() {
        assertTrue(
            DesktopStudyModel.session(session(StudyTaskTypes.KANJI_MEANING), route(StudySessionPhase.ACTIVE), undoable = true).undoable,
        )
    }

    private fun session(taskType: String, writing: Boolean = false) =
        RecordsSchedulerModels.StudySession(
            item("脱"),
            row("脱"),
            "token",
            taskType,
            writing,
            "take off",
        )

    private fun item(kanji: String) =
        RecordsStudyModels.StudyItem(kanji, "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L)

    private fun row(kanji: String) = RecordsImportModels.DashboardRow(
        kanji,
        900,
        "take off",
        "だつ",
        "deck:current",
        50,
        "reason",
        "reason text",
        1,
        1,
        0,
        listOf(
            RecordsImportModels.Example("active", 1L, 1L, "脱出", "だつ", "escape", "脱出する", false, 0),
        ),
    )

    private fun route(
        phase: StudySessionPhase,
        feedback: StudyAnswerFeedbackSnapshot? = null,
        progress: StudySessionProgressUiState = StudySessionProgressUiState(targetCount = 3, completedCount = 0, activeTask = true),
    ) = StudyRouteSnapshot(phase = phase, feedback = feedback, progress = progress)

    private fun dev.bee.kanjianki.presentation.UiText.literalText(): String =
        (this as dev.bee.kanjianki.presentation.UiText.Literal).text
}
