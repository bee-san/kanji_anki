package dev.bee.kanjianki.home

import kotlin.test.Test

/**
 * Runs the shared Home render assertions on the desktop JVM.
 *
 * This is the host where onboarding is new, so these are the first proof that the
 * cards, the note-type configuration, and this module's own resource lookups work
 * with no Android runtime underneath. Its Android twin runs the same list under
 * Robolectric, and the two lists are deliberately identical.
 */
class HomeDesktopRenderTest {
    @Test
    fun everyOnboardingStepShowsItsBodyAndItsOneButton() {
        assertEveryOnboardingStepShowsItsBodyAndItsOneButton()
    }

    @Test
    fun thePrimaryButtonDispatchesWhateverTheSharedPolicyDecided() {
        assertThePrimaryButtonDispatchesWhateverTheSharedPolicyDecided()
    }

    @Test
    fun aDisabledPrimaryButtonStaysVisibleAndDispatchesNothing() {
        assertADisabledPrimaryButtonStaysVisibleAndDispatchesNothing()
    }

    @Test
    fun theHostSentenceReachesTheScreenAndTheSharedOneIsUsedOtherwise() {
        assertTheHostSentenceReachesTheScreenAndTheSharedOneIsUsedOtherwise()
    }

    @Test
    fun theRepairedTaggingLineAppearsOnTheCardOnlyWhereItCanHappen() {
        assertTheRepairedTaggingLineAppearsOnTheCardOnlyWhereItCanHappen()
    }

    @Test
    fun providerStatusIsAnnouncedAsOneLabelledPair() {
        assertProviderStatusIsAnnouncedAsOneLabelledPair()
    }

    @Test
    fun syncProgressOffersStopAndDispatchesCancelWithNoConfirmation() {
        assertSyncProgressOffersStopAndDispatchesCancelWithNoConfirmation()
    }

    @Test
    fun theRepairedHandoffCopiesTheSearchRatherThanUnsuspending() {
        assertTheRepairedHandoffCopiesTheSearchRatherThanUnsuspending()
    }

    @Test
    fun theRepairedHandoffIsAbsentWhenThereIsNothingTagged() {
        assertTheRepairedHandoffIsAbsentWhenThereIsNothingTagged()
    }

    @Test
    fun theNoteTypePickerOffersEveryNoteTypeAndMarksTheUnusableOnes() {
        assertTheNoteTypePickerOffersEveryNoteTypeAndMarksTheUnusableOnes()
    }

    @Test
    fun selectingANoteTypeReportsItAndAnUnusableOneIsRefused() {
        assertSelectingANoteTypeReportsItAndAnUnusableOneIsRefused()
    }

    @Test
    fun theSelectedNoteTypeIsAnnouncedAsSelected() {
        assertTheSelectedNoteTypeIsAnnouncedAsSelected()
    }

    @Test
    fun everyFieldRoleIsShownWithItsRequirementAndItsField() {
        assertEveryFieldRoleIsShownWithItsRequirementAndItsField()
    }

    @Test
    fun anUnmappedRoleSaysSoRatherThanRenderingAGap() {
        assertAnUnmappedRoleSaysSoRatherThanRenderingAGap()
    }

    @Test
    fun aFieldRenamedInAnkiIsReportedPerRole() {
        assertAFieldRenamedInAnkiIsReportedPerRole()
    }

    @Test
    fun aMissingRequiredRoleIsReportedAheadOfAStaleOne() {
        assertAMissingRequiredRoleIsReportedAheadOfAStaleOne()
    }

    @Test
    fun theShippedResourcesResolveOnThisHost() {
        assertTheShippedResourcesResolveOnThisHost()
    }

    @Test
    fun theFailureRecoveryCardShowsTheProvidersOwnWords() {
        assertTheFailureRecoveryCardShowsTheProvidersOwnWords()
    }

    @Test
    fun anEnabledPrimaryButtonIsReachableAtThePhoneWidth() {
        assertAnEnabledPrimaryButtonIsReachableAtThePhoneWidth()
    }

    @Test
    fun theTestTagsAreDistinctSoAssertionsCannotCollide() {
        assertTheTestTagsAreDistinctSoAssertionsCannotCollide()
    }
}
