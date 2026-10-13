package dev.bee.kanjianki.missing

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.MissingKanjiContent
import dev.bee.kanjianki.presentation.MissingKanjiDestinations
import dev.bee.kanjianki.presentation.MissingKanjiOperationResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Missing Kanji render assertions, run on both hosts. */
@OptIn(ExperimentalTestApi::class)
internal fun assertTheFirstRunOffersTheOnePrimaryScan() {
    val recorded = mutableListOf<KaniAction>()
    renderMissing(
        content = { MissingKanjiScreenView(stateScreen(MissingKanjiContent.FirstRun), missingCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(MISSING_PRIMARY_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.MissingKanji.ScanIntent), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertScanningShowsProgressAndCancels() {
    val recorded = mutableListOf<KaniAction>()
    renderMissing(
        content = {
            MissingKanjiScreenView(
                stateScreen(MissingKanjiContent.Scanning(notesScanned = 120, uniqueKanji = 44, skippedNotes = 2, cancelling = false)),
                missingCopy(),
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(MISSING_SCANNING_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(MISSING_CANCEL_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.MissingKanji.CancelScan), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnErrorStateRendersItsFailureCode() {
    renderMissing(
        content = { MissingKanjiScreenView(stateScreen(MissingKanjiContent.Error("scan_read_failed")), missingCopy(), dispatch = {}) },
    ) {
        onNodeWithTag(MISSING_ERROR_TEST_TAG).assertIsDisplayed()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertSelectingRowsEnablesTheDestinationsAndDispatchesTheSet() {
    val recorded = mutableListOf<KaniAction>()
    renderMissing(
        content = { MissingKanjiScreenView(reportScreen(), missingCopy(), dispatch = { recorded += it }) },
    ) {
        // Nothing selected → add is disabled.
        onNodeWithTag(MISSING_ADD_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(missingRowSelectTestTag("脱")).performScrollTo().performClick()
        onNodeWithTag(MISSING_ADD_TEST_TAG).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.MissingKanji.AddToKani(literals = setOf("脱"))),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertSelectAllThenExportCsvDispatchesEverySelectableRow() {
    val recorded = mutableListOf<KaniAction>()
    renderMissing(
        content = { MissingKanjiScreenView(reportScreen(), missingCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(MISSING_SELECT_ALL_TEST_TAG).performScrollTo().performClick()
        onNodeWithTag(MISSING_EXPORT_CSV_TEST_TAG).performScrollTo().performClick()
        // Select all covers every report row; the CSV export carries them as a set.
        assertEquals(1, recorded.size)
        val export = recorded.single()
        assertTrue(export is KaniAction.MissingKanji.ExportCsv)
        assertEquals(setOf("脱", "説", "税"), export.literals)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertCreateAnkiIsAbsentWhenTheCapabilityIsOff() {
    // Direct Anki creation is capability-gated; CSV export is the always-available
    // fallback, so a provider that cannot accept notes shows export but not create.
    renderMissing(
        content = {
            MissingKanjiScreenView(
                reportScreen(
                    destinations = MissingKanjiDestinations(
                        addToKaniEnabled = true,
                        createAnkiEnabled = false,
                        csvExportEnabled = true,
                    ),
                ),
                missingCopy(),
                dispatch = {},
            )
        },
    ) {
        onNodeWithTag(MISSING_CREATE_ANKI_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(MISSING_EXPORT_CSV_TEST_TAG).assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnAdmittedRowOffersRemovalRatherThanSelection() {
    val recorded = mutableListOf<KaniAction>()
    renderMissing(
        content = { MissingKanjiScreenView(reportScreen(), missingCopy(), dispatch = { recorded += it }) },
    ) {
        // 税 is already in Kani, so it has no select checkbox; its row offers remove.
        onNodeWithTag(missingRowSelectTestTag("税")).assertDoesNotExist()
        onNodeWithTag(missingRowTestTag("税")).assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheOperationResultDialogDismisses() {
    val recorded = mutableListOf<KaniAction>()
    val screen = reportScreen().copy(
        operationResult = MissingKanjiOperationResult.CsvExported(
            title = "Exported",
            lines = listOf("12 kanji written to missing-kanji.csv"),
        ),
    )
    renderMissing(
        content = { MissingKanjiScreenView(screen, missingCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(MISSING_RESULT_TEST_TAG).assertExists()
        onNodeWithTag(MISSING_RESULT_DISMISS_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.MissingKanji.DismissResult), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertProviderMissingAndPermissionStatesRender() {
    renderMissing(
        content = { MissingKanjiScreenView(stateScreen(MissingKanjiContent.ProviderMissing, label = "Install"), missingCopy(), dispatch = {}) },
    ) {
        onNodeWithTag(MISSING_SCREEN_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(MISSING_PRIMARY_TEST_TAG).assertExists()
    }
    renderMissing(
        content = { MissingKanjiScreenView(stateScreen(MissingKanjiContent.PermissionRequired, label = "Grant"), missingCopy(), dispatch = {}) },
    ) {
        onNodeWithTag(MISSING_PRIMARY_TEST_TAG).assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheShippedMissingResourcesResolveOnThisHost() {
    var text = ""
    renderMissing(
        content = {
            val copy = rememberMissingKanjiCopy()
            text = copy.firstRunTitle + copy.addToKani + copy.exportCsv + copy.selection(3)
        },
    ) {
        assertTrue(text.isNotBlank() && "3" in text && "%" !in text, "shipped strings resolve: $text")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertALargeReportRendersAndSelectsEveryRow() {
    // The report can hold hundreds of rows; select-all then export must carry every
    // one, and the surface must render the large list without error.
    val rows = (1..200).map {
        dev.bee.kanjianki.presentation.MissingKanjiRow(
            literal = LARGE_KANJI[it % LARGE_KANJI.size] + it.toString(),
            meaning = "meaning $it",
            reading = "reading $it",
        )
    }
    val recorded = mutableListOf<KaniAction>()
    renderMissing(
        content = {
            MissingKanjiScreenView(
                reportScreen().copy(
                    content = MissingKanjiContent.Report(
                        summaryLine = "Scanned 9000 notes",
                        missingCountLine = "200 missing kanji",
                        rows = rows,
                    ),
                ),
                missingCopy(),
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(MISSING_SELECT_ALL_TEST_TAG).performScrollTo().performClick()
        onNodeWithTag(MISSING_EXPORT_CSV_TEST_TAG).performScrollTo().performClick()
        val export = recorded.single()
        assertTrue(export is KaniAction.MissingKanji.ExportCsv)
        assertEquals(200, export.literals.size)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnEmptyReportRendersNoDestinationsButStillReports() {
    // A scan that found nothing missing: the summary shows, but with no rows the
    // destinations dispatch nothing (select-all selects an empty set).
    val recorded = mutableListOf<KaniAction>()
    renderMissing(
        content = {
            MissingKanjiScreenView(
                reportScreen().copy(
                    content = MissingKanjiContent.Report(
                        summaryLine = "Scanned 9000 notes",
                        missingCountLine = "0 missing kanji",
                        rows = emptyList(),
                    ),
                ),
                missingCopy(),
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(MISSING_REPORT_TEST_TAG).assertExists()
        onNodeWithTag(MISSING_SELECT_ALL_TEST_TAG).performScrollTo().performClick()
        // Add stays disabled with an empty selection.
        onNodeWithTag(MISSING_ADD_TEST_TAG).assertIsNotEnabled()
        assertEquals(emptyList<KaniAction>(), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAWriteInProgressDisablesTheDestinations() {
    // A partial write (export running) disables the batch buttons and narrates
    // progress, so the user cannot fire a second overlapping write.
    renderMissing(
        content = {
            MissingKanjiScreenView(
                reportScreen(
                    destinations = MissingKanjiDestinations(
                        addToKaniEnabled = true,
                        csvExportEnabled = true,
                        operationInProgress = true,
                        exportLine = "Writing 40 of 200…",
                    ),
                ),
                missingCopy(),
                dispatch = {},
            )
        },
    ) {
        onNodeWithTag(MISSING_ADD_TEST_TAG).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(MISSING_EXPORT_CSV_TEST_TAG).assertIsNotEnabled()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAFailedOperationResultRenders() {
    val screen = reportScreen().copy(
        operationResult = MissingKanjiOperationResult.Failed(
            title = "Could not create notes",
            lines = listOf("The provider rejected the write."),
        ),
    )
    renderMissing(
        content = { MissingKanjiScreenView(screen, missingCopy(), dispatch = {}) },
    ) {
        onNodeWithTag(MISSING_RESULT_TEST_TAG).assertExists()
    }
}

private val LARGE_KANJI = listOf("脱", "説", "税", "鋭", "税", "刷", "冊", "劇")

@OptIn(ExperimentalTestApi::class)
internal fun assertTheMissingTestTagsAreDistinct() {
    val tags = listOf(
        MISSING_SCREEN_TEST_TAG, MISSING_PRIMARY_TEST_TAG, MISSING_CANCEL_TEST_TAG,
        MISSING_SCANNING_TEST_TAG, MISSING_ERROR_TEST_TAG, MISSING_REPORT_TEST_TAG,
        MISSING_SELECT_ALL_TEST_TAG, MISSING_CLEAR_TEST_TAG, MISSING_ADD_TEST_TAG,
        MISSING_CREATE_ANKI_TEST_TAG, MISSING_EXPORT_CSV_TEST_TAG, MISSING_RESULT_TEST_TAG,
        MISSING_RESULT_DISMISS_TEST_TAG,
    ) + listOf("脱", "税").flatMap { listOf(missingRowTestTag(it), missingRowSelectTestTag(it)) }
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    assertEquals("kani-missing-row-脱", missingRowTestTag("脱"))
}
