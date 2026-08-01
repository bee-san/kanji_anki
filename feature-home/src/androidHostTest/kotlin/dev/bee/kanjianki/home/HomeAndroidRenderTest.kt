package dev.bee.kanjianki.home

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the shared Home render assertions on the Android host target.
 *
 * Robolectric is what makes this possible off-device: `runComposeUiTest` needs a real
 * Android environment (it reads `Build.FINGERPRINT` to choose an idling strategy), and
 * without the runner every render dies in that lookup.
 *
 * This class is the one that would catch onboarding working on only one host — a
 * string or plural that resolves under Skiko but not through Android's asset loader,
 * or a semantics tree that differs between the two. The assertion list is deliberately
 * identical to the desktop twin's, which is what makes this the Android/desktop parity
 * proof Goal 194 asks for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeAndroidRenderTest {
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
