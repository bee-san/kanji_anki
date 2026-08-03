package dev.bee.kanjianki.missing

import kotlin.test.Test

/** Runs the shared Missing Kanji render assertions on the desktop JVM; the Android twin runs the identical list. */
class MissingKanjiDesktopRenderTest {
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
