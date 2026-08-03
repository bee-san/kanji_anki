package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.HomeKanjiDetailSnapshot
import dev.bee.kanjianki.presentation.DetailAccent
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop half of Goal 194's detail parity: the mapping feeds the shared surface
 * through the same `:core` copy and policies Android calls, so the surface's own
 * render tests (which run on both hosts) are what prove the two match. This checks the
 * derivation the mapping owns — the missing-record card, the review gate, the accent
 * translation, and the suspend toggle's polarity.
 */
class DesktopDetailModelTest {
    @Test
    fun aKanjiWithNoLocalRecordIsAMissingCardRatherThanAnEmptyDetail() {
        val detail = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = null, inventory = null, item = null, events = emptyList(), mnemonic = ""),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )

        assertNotNull(detail.missing)
        assertEquals(UiText.Literal(HomeTextCopy.kanjiNotFoundTitle()), detail.missing?.title)
    }

    @Test
    fun anyLocalEvidenceMakesItARealDetailRatherThanMissing() {
        // A single mnemonic is enough: the user wrote something about this kanji, so a
        // "not found" card would be throwing their note away.
        val detail = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = null, inventory = null, item = null, events = emptyList(), mnemonic = "snake"),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )

        assertNull(detail.missing)
        assertEquals("snake", detail.mnemonic.initial)
    }

    @Test
    fun anActiveCardOffersReviewAndAHistoricalOneDoesNot() {
        val active = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = row("脱"), item = review("脱"), inventory = null, events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertNotNull(active.actions.review)
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Study),
            active.actions.review?.action,
        )

        // No dashboard row means an inactive, history-only item: reviewing it is not on
        // offer, exactly as `BrowseManualReviewPolicy.shouldOfferReview` decided.
        val historical = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = null, item = null, inventory = inventory("脱"), events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertNull(historical.actions.review)
    }

    @Test
    fun aSuspendedCardOffersNeitherReviewNorTheSuspendPolarityOfAnActiveOne() {
        val suspended = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(
                row = row("脱"),
                item = review("脱"),
                inventory = null,
                events = emptyList(),
                locallySuspended = true,
            ),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )

        // Review is gated on not being suspended.
        assertNull(suspended.actions.review)
        // The suspend button unsuspends a suspended card: `studied = true` clears the
        // local suspension. The opposite polarity would retire a card the user opened
        // to bring back.
        assertEquals(
            KaniAction.Browse.SetStudied(kanji = "脱", studied = true),
            suspended.actions.suspend.action,
        )
    }

    @Test
    fun anActiveCardsSuspendButtonSuspendsRatherThanUnsuspends() {
        val active = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = row("脱"), item = review("脱"), inventory = null, events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertEquals(
            KaniAction.Browse.SetStudied(kanji = "脱", studied = false),
            active.actions.suspend.action,
        )
    }

    @Test
    fun theCopySearchButtonAppearsOnlyWhenThereIsASearchToCopy() {
        val withSearch = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = row("脱", browserSearch = "deck:current"), item = review("脱"), inventory = null, events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertEquals(
            KaniAction.RequestCopy(
                text = "deck:current",
                confirmation = UiText.Literal(HomeTextCopy.ankiSearchCopiedToast()),
            ),
            withSearch.actions.copySearch?.action,
        )

        val withoutSearch = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = row("脱", browserSearch = ""), item = review("脱"), inventory = null, events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertNull(withoutSearch.actions.copySearch)
    }

    @Test
    fun theStrokeDiagramIsAbsentUntilTheAssetsAreWired() {
        // Desktop has no KanjiVG provider until Goal 183's binaries land, so the model
        // leaves stroke order null and the surface draws the rest of the card.
        val detail = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = row("脱"), item = review("脱"), inventory = null, events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertNull(detail.strokeOrder)
    }

    @Test
    fun theNeighboursPanelIsBuiltFromTheSharedPolicyAndOpensEachNeighboursDetail() {
        val detail = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(
                row = row("脱"),
                item = review("脱"),
                inventory = null,
                events = emptyList(),
                similarPairs = listOf(pair("脱", "説")),
                inventoryList = listOf(inventory("説", meaning = "explain")),
            ),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )

        val neighbors = detail.neighbors
        assertNotNull(neighbors)
        assertEquals(listOf("説"), neighbors!!.rows.map { it.kanji })
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Detail(kanji = "説", fromBrowse = true)),
            neighbors.rows.single().action,
        )
    }

    @Test
    fun noSimilarPairsMeansNoNeighboursPanelRatherThanAnEmptyOne() {
        val detail = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = row("脱"), item = review("脱"), inventory = null, events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertNull(detail.neighbors)
    }

    @Test
    fun anExamplesAccentFollowsWhetherItsSourceIsSuspended() {
        val detail = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(
                row = row("脱", examples = listOf(example("active"), example("suspended"))),
                item = review("脱"),
                inventory = null,
                events = emptyList(),
            ),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )

        assertEquals(
            listOf(DetailAccent.POSITIVE, DetailAccent.WARNING),
            detail.examples.map { it.accent },
        )
    }

    @Test
    fun theTimelineReportsMatureSupportForAnActiveRowAndNoEvidenceOtherwise() {
        val active = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = row("脱"), item = review("脱"), inventory = null, events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertEquals(
            UiText.Literal(HomeTextCopy.matureSupportTargetText(0, 2)),
            active.timeline.support,
        )
        // An empty event list gets the "timeline appears after sync" hint rather than a
        // blank panel.
        assertEquals(UiText.Literal(HomeTextCopy.timelineEmptyText()), active.timeline.empty)

        val historical = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = null, item = null, inventory = inventory("脱"), events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertEquals(
            UiText.Literal(HomeTextCopy.noActiveEvidenceText()),
            historical.timeline.support,
        )
    }

    @Test
    fun theReasonBandIsEmphasizedAndInformationalWhicheverBranchItTakes() {
        val active = DesktopDetailModel.detail(
            kanji = "脱",
            snapshot = snapshot(row = row("脱"), item = review("脱"), inventory = null, events = emptyList()),
            matureSupportThreshold = 2,
            nowMillis = NOW,
        )
        assertTrue(active.reason.emphasized)
        assertEquals(DetailAccent.INFO, active.reason.accent)
        assertFalse(active.reason.lines.isEmpty())
    }

    private fun snapshot(
        row: RecordsImportModels.DashboardRow?,
        item: RecordsStudyModels.StudyItem?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
        events: List<RecordsImportModels.KanjiTimelineEvent>,
        mnemonic: String = "",
        locallySuspended: Boolean = false,
        similarPairs: List<RecordsImportModels.SimilarKanjiPair> = emptyList(),
        inventoryList: List<RecordsImportModels.KanjiInventoryItem> = emptyList(),
    ) = HomeKanjiDetailSnapshot(
        kanji = "脱",
        dashboardRow = row,
        inventoryItem = inventory,
        timeline = RecordsStudyModels.KanjiRecoveryTimeline(inventory, row, item, events),
        mnemonic = mnemonic,
        similarPairs = similarPairs,
        wrongPickCounts = emptyMap(),
        inventory = inventoryList,
        locallySuspended = locallySuspended,
    )

    private fun row(
        kanji: String,
        browserSearch: String = "deck:current",
        examples: List<RecordsImportModels.Example> = emptyList(),
    ) = RecordsImportModels.DashboardRow(
        kanji,
        900,
        "take off",
        "だつ",
        browserSearch,
        50,
        "reason",
        "weak in Anki",
        1,
        1,
        0,
        examples,
    )

    private fun review(kanji: String) =
        RecordsStudyModels.StudyItem(kanji, "review", NOW, 1.0, 5.0, 1, 0, 0, 1, null, 0L)

    private fun inventory(kanji: String, meaning: String = "take off") =
        RecordsImportModels.KanjiInventoryItem(kanji, meaning, "だつ", "deck:current", 2, 3, false, NOW)

    private fun pair(a: String, b: String) =
        RecordsImportModels.SimilarKanjiPair(a, b, "fixture", 0L, 0L)

    private fun example(sourceType: String) =
        RecordsImportModels.Example(sourceType, 1L, 1L, "脱出", "だっしゅつ", "escape", "脱出する", false, 0)

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
