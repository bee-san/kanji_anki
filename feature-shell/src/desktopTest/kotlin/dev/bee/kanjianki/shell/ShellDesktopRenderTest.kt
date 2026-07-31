package dev.bee.kanjianki.shell

import kotlin.test.Test

/**
 * Runs the shared shell render assertions on the desktop JVM.
 *
 * This is the host where the shell is new, so these are the first proof that the
 * rail, the effect host, and the resource lookups work with no Android runtime
 * underneath. Its Android twin runs the same list under Robolectric.
 */
class ShellDesktopRenderTest {
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
}
