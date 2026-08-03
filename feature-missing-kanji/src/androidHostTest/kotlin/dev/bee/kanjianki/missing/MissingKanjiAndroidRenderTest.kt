package dev.bee.kanjianki.missing

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the shared Missing Kanji render assertions on the Android host target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MissingKanjiAndroidRenderTest {
    @Test
    fun theFirstRunOffersTheOnePrimaryScan() {
        assertTheFirstRunOffersTheOnePrimaryScan()
    }

    @Test
    fun scanningShowsProgressAndCancels() {
        assertScanningShowsProgressAndCancels()
    }

    @Test
    fun anErrorStateRendersItsFailureCode() {
        assertAnErrorStateRendersItsFailureCode()
    }

    @Test
    fun selectingRowsEnablesTheDestinationsAndDispatchesTheSet() {
        assertSelectingRowsEnablesTheDestinationsAndDispatchesTheSet()
    }

    @Test
    fun selectAllThenExportCsvDispatchesEverySelectableRow() {
        assertSelectAllThenExportCsvDispatchesEverySelectableRow()
    }

    @Test
    fun createAnkiIsAbsentWhenTheCapabilityIsOff() {
        assertCreateAnkiIsAbsentWhenTheCapabilityIsOff()
    }

    @Test
    fun anAdmittedRowOffersRemovalRatherThanSelection() {
        assertAnAdmittedRowOffersRemovalRatherThanSelection()
    }

    @Test
    fun theOperationResultDialogDismisses() {
        assertTheOperationResultDialogDismisses()
    }

    @Test
    fun providerMissingAndPermissionStatesRender() {
        assertProviderMissingAndPermissionStatesRender()
    }

    @Test
    fun theShippedMissingResourcesResolveOnThisHost() {
        assertTheShippedMissingResourcesResolveOnThisHost()
    }

    @Test
    fun theMissingTestTagsAreDistinct() {
        assertTheMissingTestTagsAreDistinct()
    }
}
