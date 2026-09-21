package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowseResultsTest {
    private val active = BrowseRow(kanji = "脱", meaning = UiText.Literal("take off"))
    private val suspended = BrowseRow(
        kanji = "橋",
        meaning = UiText.Literal("bridge"),
        suspended = true,
    )

    @Test
    fun anActiveRowIsStudiedByDefaultAndASuspendedOneIsNot() {
        // The default that makes a first sync useful: everything active enters the
        // queue, and a card the user already suspended in Anki does not come back
        // through the side door.
        assertTrue(active.studied)
        assertFalse(suspended.studied)
    }

    @Test
    fun aBrowseRowAboutNoKanjiCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> {
            BrowseRow(kanji = "", meaning = UiText.EMPTY)
        }
    }

    @Test
    fun defaultBrowseHidesSuspendedRowsBecauseItProjectsTheStudyQueue() {
        val results = BrowseResults.of(listOf(active, suspended))

        assertEquals(listOf(active), results.rows)
    }

    @Test
    fun showingSuspendedRowsIncludesThem() {
        val results = BrowseResults.of(listOf(active, suspended), showSuspended = true)

        assertEquals(listOf(active, suspended), results.rows)
    }

    @Test
    fun theSelectionSummaryCountsOnlyTheRowsOnScreen() {
        val results = BrowseResults.of(listOf(active, suspended), showSuspended = true)

        assertEquals(1, results.studiedCount)
        assertFalse(results.allStudied)
        assertFalse(results.noneStudied)
    }

    @Test
    fun anEmptyListIsNeitherAllSelectedNorPartiallySelected() {
        // `allStudied` on an empty list would otherwise be vacuously true, and a
        // "Clear all" control would offer to clear nothing.
        val empty = BrowseResults()

        assertFalse(empty.allStudied)
        assertTrue(empty.noneStudied)
    }

    @Test
    fun togglingOneRowNamesTheKanjiItIsAbout() {
        assertEquals(
            KaniAction.Browse.SetStudied(kanji = "脱", studied = false),
            active.studiedAction(studied = false),
        )
    }

    @Test
    fun selectAllIsScopedToTheVisibleResultsRatherThanTheWholeInventory() {
        val results = BrowseResults.of(listOf(active, suspended))

        assertEquals(
            KaniAction.Browse.SetAllStudied(studied = true),
            results.setAllStudied(studied = true),
        )
        assertEquals(
            KaniAction.Browse.SetAllStudied(studied = false),
            results.setAllStudied(studied = false),
        )
    }

    @Test
    fun aStudiedToggleAboutNoKanjiIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            KaniAction.Browse.SetStudied(kanji = "  ", studied = true)
        }
    }

    @Test
    fun flippingAFilterOpensTheSameSearchWithTheFlagChangedSoBackUndoesIt() {
        // Filters live in the back stack rather than in a callback, so back from a
        // filtered list returns to the unfiltered one instead of dropping the filter
        // with no way to recover the previous view.
        val results = BrowseResults.of(
            listOf(active),
            query = "bridge",
            allKanjiScope = true,
        )

        assertEquals(
            KaniAction.Navigation.Open(
                KaniDestination.Browse(
                    query = "bridge",
                    onlySimilarKanji = true,
                    allKanjiScope = true,
                ),
            ),
            results.withSimilarFilter(only = true),
        )
        assertEquals(
            KaniAction.Navigation.Open(
                KaniDestination.Browse(
                    query = "bridge",
                    allKanjiScope = true,
                    showSuspended = true,
                ),
            ),
            results.withSuspendedShown(shown = true),
        )
    }

    @Test
    fun searchingKeepsTheFiltersTheUserAlreadySet() {
        // Retyping a query must not silently reset "similar kanji only" — the user
        // narrowed the scope on purpose and did not ask to widen it again.
        val results = BrowseResults.of(
            listOf(active),
            query = "bridge",
            onlySimilarKanji = true,
            showSuspended = true,
        )

        assertEquals(
            KaniAction.Navigation.Open(
                KaniDestination.Browse(
                    query = "take off",
                    onlySimilarKanji = true,
                    showSuspended = true,
                ),
            ),
            results.search("take off"),
        )
    }

    @Test
    fun openingARowCarriesTheSearchSoBackReturnsToTheResults() {
        // The difference between "close this card" and "lose my search". Building the
        // destination from the result set is what makes losing the query impossible: a
        // row on its own does not know the query and would have to default it.
        val results = BrowseResults.of(
            listOf(active),
            query = "take off",
            onlySimilarKanji = true,
            allKanjiScope = true,
        )

        val opened = results.open(active)

        assertEquals(
            KaniAction.Navigation.Open(
                KaniDestination.Detail(
                    kanji = "脱",
                    fromBrowse = true,
                    query = "take off",
                    onlySimilarKanji = true,
                    allKanjiScope = true,
                ),
            ),
            opened,
        )
        val destination = (opened as KaniAction.Navigation.Open).destination
        assertEquals(
            KaniDestination.Browse(
                query = "take off",
                onlySimilarKanji = true,
                allKanjiScope = true,
            ),
            destination.parent,
        )
    }

    @Test
    fun aTruncatedResultSetSaysSoRatherThanLettingTheRowCountImplyIt() {
        // Passed rather than derived from a row-count threshold: the limit belongs to
        // the query layer, and duplicating the number here would let the two drift.
        assertTrue(BrowseResults.of(listOf(active), truncated = true).truncated)
        assertFalse(BrowseResults.of(listOf(active)).truncated)
    }

    @Test
    fun aHostThatCannotReportFsrsMemoryStateGetsAnExplanationRatherThanSilence() {
        // The capability's own documentation has described this since it was written,
        // and nothing rendered it — so a desktop user's first intervals differed from
        // an Android user's with nothing on screen to say why.
        assertEquals(
            listOf(HomeNotice.REDUCED_FSRS_PRECISION),
            HomeNoticePolicy.notices(PlatformCapabilities.NONE),
        )
    }

    @Test
    fun aHostThatReportsMemoryStateHasNothingToExplain() {
        assertEquals(
            emptyList(),
            HomeNoticePolicy.notices(
                PlatformCapabilities.of(PlatformCapability.PROVIDER_FSRS_MEMORY),
            ),
        )
    }
}
