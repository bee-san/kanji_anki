package dev.bee.kanjianki.study

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the shared Study render assertions on the Android host target.
 *
 * Robolectric stands up the Android environment `runComposeUiTest` needs off-device.
 * The assertion list is identical to the desktop twin's, which is what makes this the
 * Android/desktop Study parity proof Goal 195 asks for.
 *
 * One deliberate exception, named here so the difference cannot be mistaken for drift:
 * `assertEachFontScaleReallyReachesTheRenderedText` is desktop-only. Robolectric's text
 * measurement ignores `Density.fontScale` — the progress line measures the same height at
 * 1x and at 2x — so that assertion would pass here without measuring anything. The
 * scaled-layout assertions it guards still run on both hosts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyAndroidRenderTest {
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
    fun everyGradeStaysReachableAcrossWindowsAndFontScales() {
        assertEveryGradeStaysReachableAcrossWindowsAndFontScales()
    }

    @Test
    fun eachNamedWindowReallyRendersAtItsOwnWidth() {
        assertEachNamedWindowReallyRendersAtItsOwnWidth()
    }

    @Test
    fun noActionShrinksBelowAUsableTargetAtAnyFontScale() {
        assertNoActionShrinksBelowAUsableTargetAtAnyFontScale()
    }

    @Test
    fun noControlOverflowsTheWindowSidewaysAtAnyScale() {
        assertNoControlOverflowsTheWindowSidewaysAtAnyScale()
    }

    @Test
    fun aLongTranslationStillRendersWholeLabelsAndSubstitutes() {
        assertALongTranslationStillRendersWholeLabelsAndSubstitutes()
    }

    @Test
    fun aLongTranslationKeepsTheDoneAndEmptyScreensWhole() {
        assertALongTranslationKeepsTheDoneAndEmptyScreensWhole()
    }

    @Test
    fun theWholeSessionIsCompletableWithoutAPointerAtEveryScale() {
        assertTheWholeSessionIsCompletableWithoutAPointerAtEveryScale()
    }

    @Test
    fun everyAnnouncementSurvivesALongTranslationAndALargeFont() {
        assertEveryAnnouncementSurvivesALongTranslationAndALargeFont()
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
    fun theInkControlsStayUsableTargetsAtEveryFontScale() {
        assertTheInkControlsStayUsableTargetsAtEveryFontScale()
    }

    @Test
    fun theInkTestTagsAreDistinct() {
        assertTheInkTestTagsAreDistinct()
    }
}
