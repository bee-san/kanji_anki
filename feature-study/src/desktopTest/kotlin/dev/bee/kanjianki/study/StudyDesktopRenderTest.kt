package dev.bee.kanjianki.study

import kotlin.test.Test

/**
 * Runs the shared Study render assertions on the desktop JVM.
 *
 * This is the host where the whole study session is new, so these are the first proof
 * that the card surfaces, the double-commit guard, and this module's own resource
 * lookups work with no Android runtime underneath. Its Android twin runs the same list
 * under Robolectric, and the two lists are deliberately identical.
 */
class StudyDesktopRenderTest {
    @Test
    fun loadingShowsASpinnerAndNoCard() {
        assertLoadingShowsASpinnerAndNoCard()
    }

    @Test
    fun theDoneScreenOffersOnlyAWayHome() {
        assertTheDoneScreenOffersOnlyAWayHome()
    }

    @Test
    fun theEmptyScreenNamesItsOwnCause() {
        assertTheEmptyScreenNamesItsOwnCause()
    }

    @Test
    fun anErrorStateDrawsNothingLeavingTheShellsBannerToShow() {
        assertAnErrorStateDrawsNothingLeavingTheShellsBannerToShow()
    }

    @Test
    fun theProgressLineCountsTowardTheDisplayedTarget() {
        assertTheProgressLineCountsTowardTheDisplayedTarget()
    }

    @Test
    fun aFlashcardRevealsBeforeItGradesAndGradesGoodOnPass() {
        assertAFlashcardRevealsBeforeItGradesAndGradesGoodOnPass()
    }

    @Test
    fun passMapsToGoodAndFailToAgain() {
        assertPassMapsToGoodAndFailToAgain()
    }

    @Test
    fun anAnsweredCardCannotBeGradedTwice() {
        assertAnAnsweredCardCannotBeGradedTwice()
    }

    @Test
    fun aTypedCardSubmitsWhatTheHostWillGrade() {
        assertATypedCardSubmitsWhatTheHostWillGrade()
    }

    @Test
    fun pickingAChoiceIsGradingIt() {
        assertPickingAChoiceIsGradingIt()
    }

    @Test
    fun aWritingCardOffersOnlyPassAndFail() {
        assertAWritingCardOffersOnlyPassAndFail()
    }

    @Test
    fun aCloseWritingAttemptOffersSaveHardInsteadOfPass() {
        assertACloseWritingAttemptOffersSaveHardInsteadOfPass()
    }

    @Test
    fun undoIsOfferedOnlyWhenTheSessionReportsAReversibleCard() {
        assertUndoIsOfferedOnlyWhenTheSessionReportsAReversibleCard()
    }

    @Test
    fun theWrongPickAndCorrectChoiceAreMarkedAfterAnAnswer() {
        assertTheWrongPickAndCorrectChoiceAreMarkedAfterAnAnswer()
    }

    @Test
    fun theShippedStudyResourcesResolveOnThisHost() {
        assertTheShippedStudyResourcesResolveOnThisHost()
    }

    @Test
    fun theStudyTestTagsAreDistinctSoAssertionsCannotCollide() {
        assertTheStudyTestTagsAreDistinctSoAssertionsCannotCollide()
    }

    @Test
    fun keyboardShortcutsGradeAndAdvanceThroughTheSharedModifier() {
        assertKeyboardShortcutsGradeAndAdvanceThroughTheSharedModifier()
    }

    @Test
    fun typingAKeyIntoTheAnswerFieldDoesNotGradeTheCard() {
        assertTypingAKeyIntoTheAnswerFieldDoesNotGradeTheCard()
    }

    @Test
    fun eachControlAnnouncesTheKeyThatInvokesItWhereTheHostHasOne() {
        assertEachControlAnnouncesTheKeyThatInvokesItWhereTheHostHasOne()
    }

    @Test
    fun anAnnouncedKeyIsNeverOneAFocusedFieldWouldSwallow() {
        assertAnAnnouncedKeyIsNeverOneAFocusedFieldWouldSwallow()
    }

    @Test
    fun eachChoiceAnnouncesItsOwnPositionsDigit() {
        assertEachChoiceAnnouncesItsOwnPositionsDigit()
    }

    @Test
    fun aKeyAndAClickDispatchTheSameActionExactlyOnce() {
        assertAKeyAndAClickDispatchTheSameActionExactlyOnce()
    }

    @Test
    fun draggingCapturesANormalizedStroke() {
        assertDraggingCapturesANormalizedStroke()
    }

    @Test
    fun undoDropsTheLastStrokeAndClearWipesTheCanvas() {
        assertUndoDropsTheLastStrokeAndClearWipesTheCanvas()
    }

    @Test
    fun theCanvasRendersACommittedStrokeAndAGuideWithoutError() {
        assertTheCanvasRendersACommittedStrokeAndAGuideWithoutError()
    }

    @Test
    fun theInkTestTagsAreDistinct() {
        assertTheInkTestTagsAreDistinct()
    }
}
