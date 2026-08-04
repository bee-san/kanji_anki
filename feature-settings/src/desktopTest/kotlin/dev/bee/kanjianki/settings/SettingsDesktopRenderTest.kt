package dev.bee.kanjianki.settings

import kotlin.test.Test

/**
 * Runs the shared Settings render assertions on the desktop JVM.
 *
 * The first proof the category menu, the control vocabulary, and this module's own
 * resource lookups work with no Android runtime underneath. Its Android twin runs the
 * same list under Robolectric, and the two lists are deliberately identical.
 */
class SettingsDesktopRenderTest {
    @Test
    fun eachCategoryOpensItsSection() {
        assertEachCategoryOpensItsSection()
    }

    @Test
    fun aCategoryCarriesItsCapabilityNotice() {
        assertACategoryCarriesItsCapabilityNotice()
    }

    @Test
    fun aTogglePassesItsNewValue() {
        assertATogglePassesItsNewValue()
    }

    @Test
    fun aDisabledToggleStaysVisibleAndDispatchesNothing() {
        assertADisabledToggleStaysVisibleAndDispatchesNothing()
    }

    @Test
    fun aChoiceDispatchesTheOptionItsButtonCarries() {
        assertAChoiceDispatchesTheOptionItsButtonCarries()
    }

    @Test
    fun aStepperStepsWithinItsBounds() {
        assertAStepperStepsWithinItsBounds()
    }

    @Test
    fun aDestructiveButtonDispatchesItsCommand() {
        assertADestructiveButtonDispatchesItsCommand()
    }

    @Test
    fun aPlainButtonDispatchesItsCommand() {
        assertAPlainButtonDispatchesItsCommand()
    }

    @Test
    fun anInfoRowShowsItsLabelAndValueWithNoAction() {
        assertAnInfoRowShowsItsLabelAndValueWithNoAction()
    }

    @Test
    fun aKeybindingRowReadsOutTheKeysItsCommandHolds() {
        assertAKeybindingRowReadsOutTheKeysItsCommandHolds()
    }

    @Test
    fun bindingAndUnbindingDispatchTheKeyTheyWereShownOn() {
        assertBindingAndUnbindingDispatchTheKeyTheyWereShownOn()
    }

    @Test
    fun anUnavailableKeyStatesItsReasonAndDispatchesNothing() {
        assertAnUnavailableKeyStatesItsReasonAndDispatchesNothing()
    }

    @Test
    fun anUnportedSectionNamesItselfRatherThanBlank() {
        assertAnUnportedSectionNamesItselfRatherThanBlank()
    }

    @Test
    fun theShippedSettingsResourcesResolveOnThisHost() {
        assertTheShippedSettingsResourcesResolveOnThisHost()
    }

    @Test
    fun theSettingsTestTagsAreDistinct() {
        assertTheSettingsTestTagsAreDistinct()
    }

    @Test
    fun everySettingsControlIsBigEnoughToHit() {
        assertEverySettingsControlIsBigEnoughToHit()
    }

    @Test
    fun everyKeybindingChipIsBigEnoughToHit() {
        assertEveryKeybindingChipIsBigEnoughToHit()
    }
}
