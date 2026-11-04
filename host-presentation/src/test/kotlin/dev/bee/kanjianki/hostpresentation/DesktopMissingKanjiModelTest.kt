package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiReport
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.MissingKanjiContent
import dev.bee.kanjianki.presentation.MissingKanjiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMissingKanjiModelTest {
    private fun candidate(literal: String, rank: Int? = 42) = MissingKanjiCandidate(
        literal = literal,
        meanings = listOf("to escape, to flee"),
        onReadings = listOf("ダツ"),
        jitenRank = rank,
    )

    private fun report(
        missing: List<MissingKanjiCandidate>,
        observed: Int = 1_200,
    ) = MissingKanjiReport(
        range = MissingKanjiFrequencyRange(1, 2_500, false),
        missing = missing,
        uniqueObservedKanjiCount = observed,
        uniqueDictionaryKanjiCount = 13_108,
        eligibleDictionaryKanjiCount = 2_500,
        eligibleRankedKanjiCount = 2_500,
        eligibleUnrankedKanjiCount = 0,
        presentEligibleKanjiCount = observed,
        invalidObservedValueCount = 0,
        invalidDictionaryCandidateCount = 0,
    )

    @Test
    fun aReadyProviderWithADictionaryInvitesAScan() {
        val screen = DesktopMissingKanjiModel.firstRun(
            provider = MissingKanjiProvider.READY,
            dictionaryAvailable = true,
        )

        assertEquals(MissingKanjiContent.FirstRun, screen.content)
        assertEquals(KaniAction.MissingKanji.ScanIntent, screen.primaryAction)
    }

    @Test
    fun aMissingDictionaryIsItsOwnFailureNotAnInvitationToScan() {
        val screen = DesktopMissingKanjiModel.firstRun(
            provider = MissingKanjiProvider.READY,
            dictionaryAvailable = false,
        )

        // Not FirstRun: a scan would succeed and produce an empty report, which reads as
        // "your collection is complete" when Kani never had anything to compare against.
        assertEquals(
            MissingKanjiContent.Error(DesktopMissingKanjiModel.DICTIONARY_UNAVAILABLE),
            screen.content,
        )
        // Still READY, because the provider genuinely is — the two are independent, and
        // reporting the provider as broken would send the user to fix the wrong thing.
        assertEquals(MissingKanjiProvider.READY, screen.providerAvailability)
    }

    @Test
    fun eachProviderStateRendersItsOwnBranch() {
        assertEquals(
            MissingKanjiContent.ProviderMissing,
            DesktopMissingKanjiModel.firstRun(MissingKanjiProvider.NOT_INSTALLED, true).content,
        )
        assertEquals(
            MissingKanjiContent.PermissionRequired,
            DesktopMissingKanjiModel
                .firstRun(MissingKanjiProvider.PERMISSION_REQUIRED, true)
                .content,
        )
        assertEquals(
            MissingKanjiContent.ProviderMissing,
            DesktopMissingKanjiModel.firstRun(MissingKanjiProvider.UNAVAILABLE, true).content,
        )
    }

    @Test
    fun anUnreachableProviderIsReportedEvenWithNoDictionary() {
        // Provider state is checked first: telling someone their reference assets are
        // missing when Anki is not even running would send them to fix the wrong thing.
        val screen = DesktopMissingKanjiModel.firstRun(
            provider = MissingKanjiProvider.UNAVAILABLE,
            dictionaryAvailable = false,
        )

        assertEquals(MissingKanjiContent.ProviderMissing, screen.content)
    }

    @Test
    fun aRunningScanOffersCancelAndBlocksEveryDestination() {
        val screen = DesktopMissingKanjiModel.scanning(
            provider = MissingKanjiProvider.READY,
            notesScanned = 400,
            uniqueKanji = 90,
            skippedNotes = 2,
            cancelling = false,
        )

        val content = screen.content as MissingKanjiContent.Scanning
        assertEquals(400, content.notesScanned)
        assertEquals(90, content.uniqueKanji)
        assertEquals(KaniAction.MissingKanji.CancelScan, screen.primaryAction)
        // The candidate set is still changing; a batch add now would admit kanji the
        // finished scan excludes.
        assertTrue(screen.destinations.operationInProgress)
        assertFalse(screen.destinations.addToKaniEnabled)
        assertFalse(screen.destinations.createAnkiEnabled)
        assertFalse(screen.destinations.csvExportEnabled)
    }

    @Test
    fun aProviderReportingNonsenseCountsDoesNotRenderBackwardsProgress() {
        val screen = DesktopMissingKanjiModel.scanning(
            provider = MissingKanjiProvider.READY,
            notesScanned = -5,
            uniqueKanji = -1,
            skippedNotes = -3,
            cancelling = true,
        )

        val content = screen.content as MissingKanjiContent.Scanning
        assertEquals(0, content.notesScanned)
        assertEquals(0, content.uniqueKanji)
        assertEquals(0, content.skippedNotes)
        assertTrue(content.cancelling)
    }

    @Test
    fun aFinishedReportListsRowsAndOffersTheLocalAndCsvPaths() {
        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("脱"), candidate("麿", rank = null))),
        )

        val content = screen.content as MissingKanjiContent.Report
        assertEquals(listOf("脱", "麿"), content.rows.map { it.literal })
        assertEquals("to escape, to flee", content.rows.first().meaning)
        assertEquals("ダツ", content.rows.first().reading)
        // Local admission and CSV need nothing from the provider.
        assertTrue(screen.destinations.addToKaniEnabled)
        assertTrue(screen.destinations.csvExportEnabled)
        // Note creation is capability-gated and was not granted here.
        assertFalse(screen.destinations.createAnkiEnabled)
    }

    @Test
    fun anUnrankedRowSaysUnrankedRatherThanShowingNothing() {
        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("麿", rank = null))),
        )

        // Blank reads as missing data; unranked is a real answer the documented
        // sort-last behaviour depends on.
        val row = (screen.content as MissingKanjiContent.Report).rows.single()
        assertTrue("an unranked row must say so", row.rankLine.isNotBlank())
    }

    @Test
    fun creatingAnkiNotesIsOfferedOnlyWhenTheCapabilityGatePassed() {
        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("脱"))),
            canCreateAnkiNotes = true,
            defaultDeckName = "Kani",
        )

        assertTrue(screen.destinations.createAnkiEnabled)
        assertEquals("Kani", screen.destinations.defaultDeckName)
        val action = screen.destinations.createAnkiAction(setOf("脱"))
        assertEquals(
            KaniAction.MissingKanji.CreateAnkiNotes(setOf("脱"), "Kani"),
            action,
        )
    }

    @Test
    fun anAlreadyAdmittedKanjiIsMarkedAndOffersRemovalInsteadOfAddition() {
        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("脱"), candidate("出"))),
            admittedKanji = setOf("脱"),
        )

        val rows = (screen.content as MissingKanjiContent.Report).rows
        val admitted = rows.single { it.literal == "脱" }
        assertTrue(admitted.inKani)
        assertTrue(admitted.canRemove)
        assertEquals(KaniAction.MissingKanji.Remove("脱"), admitted.removeAction)
        assertFalse(rows.single { it.literal == "出" }.inKani)
    }

    @Test
    fun aReportWhereEverythingIsAlreadyAdmittedOffersNoBatchDestination() {
        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("脱"))),
            admittedKanji = setOf("脱"),
            canCreateAnkiNotes = true,
        )

        // Nothing selectable, so every batch button is off — an enabled button that can
        // only produce a no-op is a worse answer than a disabled one.
        assertFalse(screen.destinations.addToKaniEnabled)
        assertFalse(screen.destinations.createAnkiEnabled)
        assertFalse(screen.destinations.csvExportEnabled)
    }

    @Test
    fun anEmptyReportIsAReportNotAFailure() {
        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(emptyList()),
        )

        // "Checked and found nothing" is the good outcome and must be distinguishable
        // from "could not check", which is the DICTIONARY_UNAVAILABLE case above.
        val content = screen.content as MissingKanjiContent.Report
        assertTrue(content.rows.isEmpty())
        assertTrue(content.missingCountLine.isNotBlank())
    }

    @Test
    fun aStaleReportSaysSoAndAFreshOneDoesNot() {
        val stale = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("脱"))),
            staleReason = "collection_changed",
        )
        val fresh = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("脱"))),
        )

        assertTrue((stale.content as MissingKanjiContent.Report).staleLine!!.isNotBlank())
        assertNull((fresh.content as MissingKanjiContent.Report).staleLine)
    }

    @Test
    fun aRunningWriteDisablesEveryDestinationWithoutHidingTheReport() {
        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("脱"))),
            canCreateAnkiNotes = true,
            operationInProgress = true,
        )

        // The rows stay visible: the user should see what is being written, not a blank
        // screen, and a second dispatch must not be possible while the first runs.
        assertTrue((screen.content as MissingKanjiContent.Report).rows.isNotEmpty())
        assertTrue(screen.destinations.operationInProgress)
        assertFalse(screen.destinations.addToKaniEnabled)
        assertFalse(screen.destinations.createAnkiEnabled)
        assertFalse(screen.destinations.csvExportEnabled)
    }

    @Test
    fun aFailedScanKeepsTheFailureCodeAndOffersARetry() {
        val screen = DesktopMissingKanjiModel.failed(
            provider = MissingKanjiProvider.READY,
            failureCode = "provider_read_failed",
        )

        assertEquals(MissingKanjiContent.Error("provider_read_failed"), screen.content)
        assertEquals(KaniAction.MissingKanji.ScanIntent, screen.primaryAction)
    }

    @Test
    fun theBatchActionsCarryTheSelectionTheyWereGiven() {
        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = report(listOf(candidate("脱"), candidate("出"))),
        )
        val selected = setOf("脱", "出")

        assertEquals(
            KaniAction.MissingKanji.AddToKani(selected),
            screen.destinations.addAction(selected),
        )
        assertEquals(
            KaniAction.MissingKanji.ExportCsv(selected),
            screen.destinations.exportCsvAction(selected),
        )
    }
}
