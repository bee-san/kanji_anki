package dev.bee.kanjianki.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the shared Settings render assertions on the Android host target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsAndroidRenderTest {
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
}
