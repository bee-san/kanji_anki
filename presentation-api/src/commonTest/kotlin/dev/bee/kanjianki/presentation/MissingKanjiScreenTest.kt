package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MissingKanjiScreenTest {
    @Test
    fun aRowRemovesItselfKeyedOnItsKanji() {
        val row = MissingKanjiRow(literal = "脱", meaning = "take off", reading = "だつ")
        assertEquals(KaniAction.MissingKanji.Remove(literal = "脱"), row.removeAction)
    }

    @Test
    fun aRowAndARemovalAreAboutAKanji() {
        assertFailsWith<IllegalArgumentException> { MissingKanjiRow(literal = " ", meaning = "x", reading = "y") }
        assertFailsWith<IllegalArgumentException> { KaniAction.MissingKanji.Remove(literal = "") }
    }

    @Test
    fun theDestinationsDispatchTheSelectedSet() {
        val destinations = MissingKanjiDestinations(
            addToKaniEnabled = true,
            createAnkiEnabled = true,
            csvExportEnabled = true,
            defaultDeckName = "Kani::Missing Kanji",
        )
        val selected = setOf("脱", "説")

        assertEquals(KaniAction.MissingKanji.AddToKani(selected), destinations.addAction(selected))
        assertEquals(
            KaniAction.MissingKanji.CreateAnkiNotes(selected, "Kani::Missing Kanji"),
            destinations.createAnkiAction(selected),
        )
        assertEquals(KaniAction.MissingKanji.ExportCsv(selected), destinations.exportCsvAction(selected))
    }

    @Test
    fun theScreenHoldsWhicheverContentBranchItIsIn() {
        val report = MissingKanjiScreen(
            content = MissingKanjiContent.Report(
                summaryLine = "Scanned 400 notes",
                missingCountLine = "12 missing",
                staleLine = "results are a day old",
                rows = listOf(MissingKanjiRow("脱", "take off", "だつ", rankLine = "#900", inKani = true, canRemove = true)),
            ),
            providerAvailability = MissingKanjiProvider.READY,
            primaryActionLabel = "Scan again",
            primaryAction = KaniAction.MissingKanji.ScanIntent,
            operationResult = MissingKanjiOperationResult.AnkiCreated(
                title = "Created",
                lines = listOf("10 created", "2 already present"),
                csvFallbackAvailable = true,
            ),
        )
        val content = report.content
        assertEquals(true, content is MissingKanjiContent.Report)
        assertEquals("results are a day old", (content as MissingKanjiContent.Report).staleLine)
        assertEquals(true, content.rows.single().inKani)
        val result = report.operationResult
        assertEquals(true, result is MissingKanjiOperationResult.AnkiCreated)
        assertEquals(true, (result as MissingKanjiOperationResult.AnkiCreated).csvFallbackAvailable)
    }

    @Test
    fun everyContentAndResultAndProviderVariantConstructs() {
        // One touch of each remaining variant, so :presentation-api's own coverage sees
        // each class built and a renamed field fails here.
        val scanning = MissingKanjiContent.Scanning(notesScanned = 5, uniqueKanji = 3, skippedNotes = 1, cancelling = true)
        assertEquals(5, scanning.notesScanned)
        assertEquals(true, scanning.cancelling)
        assertEquals("boom", (MissingKanjiContent.Error("boom")).failureCode)
        assertEquals(MissingKanjiContent.FirstRun, MissingKanjiContent.FirstRun)
        assertEquals(MissingKanjiContent.ProviderMissing, MissingKanjiContent.ProviderMissing)
        assertEquals(MissingKanjiContent.PermissionRequired, MissingKanjiContent.PermissionRequired)

        assertEquals("Added", (MissingKanjiOperationResult.Added("Added", listOf("3 added"))).title)
        assertEquals("Removed", (MissingKanjiOperationResult.Removed("Removed", emptyList())).title)
        assertEquals("Exported", (MissingKanjiOperationResult.CsvExported("Exported", listOf("csv"))).title)
        assertEquals("Failed", (MissingKanjiOperationResult.Failed("Failed", listOf("why"))).title)

        assertEquals(4, MissingKanjiProvider.entries.size)
    }
}
