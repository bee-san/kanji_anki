package dev.bee.kanjianki.shell

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the shared shell render assertions on the Android host target.
 *
 * Robolectric is what makes this possible off-device: `runComposeUiTest` needs a
 * real Android environment (it reads `Build.FINGERPRINT` to choose an idling
 * strategy), and without the runner every render dies in that lookup.
 *
 * This class is the one that would catch a shell that only works on one host — a
 * resource that resolves under Skiko but not through Android's asset loader, or a
 * semantics tree that differs between the two. The assertion list is deliberately
 * identical to the desktop twin's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShellAndroidRenderTest {
    @Test
    fun theRouteBodyRendersInsideTheContentSlot() {
        assertTheRouteBodyRendersInsideTheContentSlot()
    }

    @Test
    fun theRouteTagTracksTheCurrentDestination() {
        assertTheRouteTagTracksTheCurrentDestination()
    }

    @Test
    fun eachWindowSizeGetsItsExpectedNavigationSurface() {
        assertEachWindowSizeGetsItsExpectedNavigationSurface()
    }

    @Test
    fun selectingATabDispatchesAndReSelectingDoesNot() {
        assertSelectingATabDispatchesAndReSelectingDoesNot()
    }

    @Test
    fun theRailDispatchesTheSameActionsAsTheBar() {
        assertTheRailDispatchesTheSameActionsAsTheBar()
    }

    @Test
    fun everyTabIsAnnouncedWithItsLabelAndSelectedState() {
        assertEveryTabIsAnnouncedWithItsLabelAndSelectedState()
    }

    @Test
    fun theStudyBadgeAppearsOnlyWhenThereIsWork() {
        assertTheStudyBadgeAppearsOnlyWhenThereIsWork()
    }

    @Test
    fun aLargeFontScaleKeepsEveryTabReachable() {
        assertALargeFontScaleKeepsEveryTabReachable()
    }

    @Test
    fun immersionHidesNavigationWithoutHidingContent() {
        assertImmersionHidesNavigationWithoutHidingContent()
    }

    @Test
    fun theShellRendersUnderEveryThemeAndBothHostDarkSignals() {
        assertTheShellRendersUnderEveryThemeAndBothHostDarkSignals()
    }

    @Test
    fun theBackAffordanceIsTheHostsDecision() {
        assertTheBackAffordanceIsTheHostsDecision()
    }

    @Test
    fun escapeGoesBackOnlyWhenThereIsSomewhereToGo() {
        assertEscapeGoesBackOnlyWhenThereIsSomewhereToGo()
    }

    @Test
    fun aConfirmEffectBlocksUntilAnsweredAndConsumesBeforeActing() {
        assertAConfirmEffectBlocksUntilAnsweredAndConsumesBeforeActing()
    }

    @Test
    fun anUnansweredConfirmIsNotConsumed() {
        assertAnUnansweredConfirmIsNotConsumed()
    }

    @Test
    fun platformEffectsReachTheHandlerAndAreAcknowledged() {
        assertPlatformEffectsReachTheHandlerAndAreAcknowledged()
    }

    @Test
    fun onlyTheHeadEffectIsDelivered() {
        assertOnlyTheHeadEffectIsDelivered()
    }

    @Test
    fun noEffectsMeansNoHandlerCallsAndNoDispatches() {
        assertNoEffectsMeansNoHandlerCallsAndNoDispatches()
    }

    @Test
    fun theNoOpEffectHandlerSwallowsEverythingWithoutFailing() {
        assertTheNoOpEffectHandlerSwallowsEverythingWithoutFailing()
    }

    @Test
    fun theRouteSurfacesRenderEveryLoadableState() {
        assertTheRouteSurfacesRenderEveryLoadableState()
    }

    @Test
    fun aRefreshKeepsThePreviousContentOnScreen() {
        assertARefreshKeepsThePreviousContentOnScreen()
    }

    @Test
    fun retryIsOfferedOnlyForRetryableFailures() {
        assertRetryIsOfferedOnlyForRetryableFailures()
    }

    @Test
    fun aFailureAlongsideContentBannersRatherThanReplaces() {
        assertAFailureAlongsideContentBannersRatherThanReplaces()
    }

    @Test
    fun anUnavailableCapabilityIsExplainedRatherThanOffered() {
        assertAnUnavailableCapabilityIsExplainedRatherThanOffered()
    }

    @Test
    fun everyShellControlIsBigEnoughToHit() {
        assertEveryShellControlIsBigEnoughToHit()
    }

    @Test
    fun bothConfirmAnswersAreIndependentlyReachable() {
        assertBothConfirmAnswersAreIndependentlyReachable()
    }
}
