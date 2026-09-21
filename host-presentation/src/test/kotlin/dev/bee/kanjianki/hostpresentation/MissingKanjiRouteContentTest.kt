package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiReport
import dev.bee.kanjianki.presentation.MissingKanjiContent
import dev.bee.kanjianki.presentation.MissingKanjiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The render states a host hands the loader, and the screens they must produce.
 *
 * The loader's own `load` needs a live use-case graph, so this covers the part that
 * carries the decisions: which render maps to which screen, and that the states stay
 * distinguishable. A route that rendered the wrong branch would show a user a finished
 * report while a scan was running, or an invitation to scan when scanning cannot work.
 */
class MissingKanjiRouteContentTest {
    private fun report(missing: List<String>) = MissingKanjiReport(
        range = MissingKanjiFrequencyRange(1, 2_500, false),
        missing = missing.map { MissingKanjiCandidate(literal = it, jitenRank = 10) },
        uniqueObservedKanjiCount = 900,
        uniqueDictionaryKanjiCount = 13_108,
        eligibleDictionaryKanjiCount = 2_500,
        eligibleRankedKanjiCount = 2_500,
        eligibleUnrankedKanjiCount = 0,
        presentEligibleKanjiCount = 900,
        invalidObservedValueCount = 0,
        invalidDictionaryCandidateCount = 0,
    )

    @Test
    fun anIdleRenderWithNoDictionarySaysSoRatherThanInvitingAScan() {
        val screen = DesktopMissingKanjiModel.firstRun(
            MissingKanjiProvider.READY,
            dictionaryAvailable = MissingKanjiRender.Idle(dictionaryAvailable = false)
                .dictionaryAvailable,
        )

        assertEquals(
            MissingKanjiContent.Error(DesktopMissingKanjiModel.DICTIONARY_UNAVAILABLE),
            screen.content,
        )
    }

    @Test
    fun eachRenderStateIsADistinctScreen() {
        val idle = DesktopMissingKanjiModel.firstRun(MissingKanjiProvider.READY, true)
        val scanning = DesktopMissingKanjiModel.scanning(
            MissingKanjiProvider.READY,
            notesScanned = 10,
            uniqueKanji = 3,
            skippedNotes = 0,
            cancelling = false,
        )
        val failed = DesktopMissingKanjiModel.failed(MissingKanjiProvider.READY, "read_failed")
        val ready = DesktopMissingKanjiModel.report(
            MissingKanjiProvider.READY,
            report(listOf("脱")),
        )

        // Four distinct branches. Collapsing any pair loses a distinction the user needs:
        // "not scanned" vs "scanning" vs "broken" vs "here is the answer".
        val contents = listOf(idle, scanning, failed, ready).map { it.content::class }
        assertEquals(contents.size, contents.toSet().size)
    }

    @Test
    fun aScanningRenderCarriesItsProgressIntoTheScreen() {
        val render = MissingKanjiRender.Scanning(
            notesScanned = 1_234,
            uniqueKanji = 456,
            skippedNotes = 7,
            cancelling = true,
        )

        val screen = DesktopMissingKanjiModel.scanning(
            provider = MissingKanjiProvider.READY,
            notesScanned = render.notesScanned,
            uniqueKanji = render.uniqueKanji,
            skippedNotes = render.skippedNotes,
            cancelling = render.cancelling,
        )

        val content = screen.content as MissingKanjiContent.Scanning
        assertEquals(1_234, content.notesScanned)
        assertEquals(456, content.uniqueKanji)
        assertEquals(7, content.skippedNotes)
        assertTrue(content.cancelling)
    }

    @Test
    fun aReadyRenderCarriesEveryFieldTheReportScreenNeeds() {
        val render = MissingKanjiRender.Ready(
            report = report(listOf("脱", "出")),
            admittedKanji = setOf("出"),
            canCreateAnkiNotes = true,
            defaultDeckName = "Kani",
            staleReason = "collection_changed",
            operationInProgress = false,
        )

        val screen = DesktopMissingKanjiModel.report(
            provider = MissingKanjiProvider.READY,
            report = render.report,
            admittedKanji = render.admittedKanji,
            canCreateAnkiNotes = render.canCreateAnkiNotes,
            defaultDeckName = render.defaultDeckName,
            staleReason = render.staleReason,
            operationInProgress = render.operationInProgress,
        )

        val content = screen.content as MissingKanjiContent.Report
        assertEquals(2, content.rows.size)
        assertNotNull(content.staleLine)
        assertTrue(content.rows.single { it.literal == "出" }.inKani)
        assertTrue(screen.destinations.createAnkiEnabled)
        assertEquals("Kani", screen.destinations.defaultDeckName)
    }

    @Test
    fun aFailedRenderRefusesABlankCode() {
        // A blank code would resolve to no copy at all, leaving the user an empty error
        // panel — the least actionable outcome available.
        val rejected = runCatching { MissingKanjiRender.Failed("") }
        assertTrue(rejected.isFailure)
        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun theRouteContentSlotIsNullOffRoute() {
        // Null off-route rather than an empty screen: a route load for Home must not
        // resurrect a report the user navigated away from.
        val content = KaniRouteContent(
            providerMessage = "",
            studyItemCount = 0,
            dueCount = 0,
            themeChoice = dev.bee.kanjianki.core.KaniThemeChoice.SYSTEM,
        )

        assertNull(content.missingKanji)
    }

    @Test
    fun theRouteContentCarriesTheScreenWhenOnRoute() {
        val screen = DesktopMissingKanjiModel.report(
            MissingKanjiProvider.READY,
            report(listOf("脱")),
        )
        val content = KaniRouteContent(
            providerMessage = "",
            studyItemCount = 0,
            dueCount = 0,
            themeChoice = dev.bee.kanjianki.core.KaniThemeChoice.SYSTEM,
            missingKanji = screen,
        )

        assertEquals(screen, content.missingKanji)
    }
}
