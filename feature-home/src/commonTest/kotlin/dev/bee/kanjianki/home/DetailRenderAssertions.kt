package dev.bee.kanjianki.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.bee.kanjianki.presentation.CopySearchButton
import dev.bee.kanjianki.presentation.DetailAccent
import dev.bee.kanjianki.presentation.DetailBadge
import dev.bee.kanjianki.presentation.DetailIdentity
import dev.bee.kanjianki.presentation.DetailMissing
import dev.bee.kanjianki.presentation.DetailPanel
import dev.bee.kanjianki.presentation.DetailActions
import dev.bee.kanjianki.presentation.ExampleCard
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniActionButton
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KanjiDetail
import dev.bee.kanjianki.presentation.MnemonicEditor
import dev.bee.kanjianki.presentation.NeighborPanel
import dev.bee.kanjianki.presentation.NeighborRow
import dev.bee.kanjianki.presentation.RecoveryTimeline
import dev.bee.kanjianki.presentation.StrokeOrderDiagram
import dev.bee.kanjianki.presentation.StrokePanel
import dev.bee.kanjianki.presentation.StrokePath
import dev.bee.kanjianki.presentation.StrokePoint
import dev.bee.kanjianki.presentation.TimelineEvent
import dev.bee.kanjianki.presentation.UiText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The kanji-detail render assertions, shared by both hosts exactly as Home's are.
 *
 * The detail screen was `:app`-only until Goal 194; these are the first proof that
 * the same surface draws under Skiko with no Android runtime beneath it. They assert
 * structure and which action each control dispatches — not pixels — because the
 * action is what the screen owes its host, and what the action *means* is
 * `:presentation-api`'s to decide and test.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertADetailScreenShowsEveryOptionalPanelWhenItHasThem() {
    renderHome(
        content = { KanjiDetailScreen(fullDetail(), TestUiTextResolver, dispatch = {}) },
    ) {
        for (tag in listOf(
            DETAIL_TEST_TAG,
            DETAIL_IDENTITY_TEST_TAG,
            DETAIL_STROKE_ORDER_TEST_TAG,
            DETAIL_NEIGHBORS_TEST_TAG,
            DETAIL_TIMELINE_TEST_TAG,
            DETAIL_MNEMONIC_INPUT_TEST_TAG,
            DETAIL_SUSPEND_TEST_TAG,
        )) {
            onNodeWithTag(tag).assertExists()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAMissingKanjiIsAShortCardRatherThanAnEmptyDetail() {
    val missing = fullDetail().copy(
        missing = DetailMissing(
            title = UiText.Literal("Kanji not found"),
            body = UiText.Literal("No local record found."),
        ),
    )
    renderHome(
        content = { KanjiDetailScreen(missing, TestUiTextResolver, dispatch = {}) },
    ) {
        onNodeWithTag(DETAIL_MISSING_TEST_TAG).assertIsDisplayed()
        // The panels the model still carries are not drawn: `missing` wins over
        // everything so the user sees one "not found" card, not blank panels.
        onNodeWithTag(DETAIL_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(DETAIL_IDENTITY_TEST_TAG).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnOptionalPanelIsAbsentRatherThanEmptyWhenTheModelOmitsIt() {
    // Every kanji on desktop lacks a stroke guide until the Goal 183 assets land, so
    // the screen must render without the diagram rather than treat its absence as an
    // error. The neighbours and local-inventory panels are the same story.
    val bare = fullDetail().copy(strokeOrder = null, neighbors = null, localInventory = null)
    renderHome(
        content = { KanjiDetailScreen(bare, TestUiTextResolver, dispatch = {}) },
    ) {
        onNodeWithTag(DETAIL_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(DETAIL_STROKE_ORDER_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(DETAIL_NEIGHBORS_TEST_TAG).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertSavingAMnemonicDispatchesTheTrimmedNoteForThisKanji() {
    // Only Save commits, and it trims first — the "no actions while editing text" rule
    // the Browse search box also follows. A note that is only whitespace is a clear.
    // A minimal detail keeps the editor near the top: the full card's stroke, neighbour,
    // and inventory panels would push Save below the fixed test window, where a click
    // lands on nothing.
    val recorded = mutableListOf<KaniAction>()
    renderHome(
        content = { ScrollableDetail(minimalDetail()) { recorded += it } },
    ) {
        onNodeWithTag(DETAIL_MNEMONIC_INPUT_TEST_TAG).performScrollTo().performTextInput("  snake escaping  ")
        onNodeWithTag(DETAIL_MNEMONIC_SAVE_TEST_TAG).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.SaveMnemonic(kanji = "脱", note = "snake escaping")),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertCopyingTheAnkiSearchDispatchesTheSearchAndItsConfirmation() {
    // The copy carries its own confirmation, so the toast and the write stay paired —
    // the shell turns the one RequestCopy into a clipboard effect with that text.
    val recorded = mutableListOf<KaniAction>()
    renderHome(
        content = { ScrollableDetail(minimalDetail()) { recorded += it } },
    ) {
        onNodeWithTag(DETAIL_COPY_SEARCH_TEST_TAG).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(
                KaniAction.RequestCopy(
                    text = "deck:current",
                    confirmation = UiText.Literal("Copied."),
                ),
            ),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertANeighbourOpensItsOwnDetailKeepingTheBrowseContext() {
    // No stroke order, so the neighbours panel is near the top rather than pushed past
    // the fixed test window by a grid of stroke cells.
    val recorded = mutableListOf<KaniAction>()
    renderHome(
        content = { ScrollableDetail(fullDetail().copy(strokeOrder = null)) { recorded += it } },
    ) {
        onNodeWithTag(detailNeighborTestTag("説")).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(
                KaniAction.Navigation.Open(KaniDestination.Detail(kanji = "説", fromBrowse = true)),
            ),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheReviewButtonIsAbsentWhenTheModelOffersNone() {
    // Review is conditional — no active, non-retired card, no button — and suspend is
    // always offered. A screen that showed a dead review button would be promising a
    // study session the route then refuses.
    val noReview = fullDetail().copy(
        actions = DetailActions(
            review = null,
            suspend = KaniActionButton(
                label = UiText.Literal("Study this kanji"),
                action = KaniAction.SaveMnemonic(kanji = "脱", note = ""),
            ),
        ),
    )
    renderHome(
        content = { KanjiDetailScreen(noReview, TestUiTextResolver, dispatch = {}) },
    ) {
        onNodeWithTag(DETAIL_REVIEW_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(DETAIL_SUSPEND_TEST_TAG).assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheDetailTestTagsAreDistinctSoAssertionsCannotCollide() {
    val tags = listOf(
        DETAIL_TEST_TAG,
        DETAIL_MISSING_TEST_TAG,
        DETAIL_IDENTITY_TEST_TAG,
        DETAIL_REVIEW_TEST_TAG,
        DETAIL_COPY_SEARCH_TEST_TAG,
        DETAIL_SUSPEND_TEST_TAG,
        DETAIL_MNEMONIC_INPUT_TEST_TAG,
        DETAIL_MNEMONIC_SAVE_TEST_TAG,
        DETAIL_STROKE_ORDER_TEST_TAG,
        DETAIL_NEIGHBORS_TEST_TAG,
        DETAIL_TIMELINE_TEST_TAG,
    ) + listOf(1, 2).map(::detailStrokePanelTestTag) +
        listOf("説", "脱").map(::detailNeighborTestTag)
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    assertEquals("kani-detail-stroke-1", detailStrokePanelTestTag(1))
    assertEquals("kani-detail-neighbor-説", detailNeighborTestTag("説"))
}

/**
 * The detail inside a scroll, matching how each host wraps it.
 *
 * The fixed test window is a phone height; the detail is taller, so a control below
 * the fold is only reachable through the same `verticalScroll` both hosts give the
 * route. `performScrollTo` then brings a target into view before a click — a click on
 * an off-screen node otherwise lands on nothing and records no action.
 */
@Composable
private fun ScrollableDetail(detail: KanjiDetail, dispatch: (KaniAction) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        KanjiDetailScreen(detail, TestUiTextResolver, dispatch = dispatch)
    }
}

/**
 * A detail with no optional panels, for the interaction assertions.
 *
 * The full card is tall enough that its lower controls fall past the fixed test
 * window, where a click lands on nothing. Stripping the stroke, neighbour, and
 * inventory panels keeps the editor and action row reachable without changing what
 * either dispatches.
 */
private fun minimalDetail(): KanjiDetail =
    fullDetail().copy(strokeOrder = null, neighbors = null, localInventory = null, examples = emptyList())

/** A detail with every optional panel present, for the assertions that need them all. */
private fun fullDetail(): KanjiDetail = KanjiDetail(
    kanji = "脱",
    identity = DetailIdentity(
        title = UiText.Literal("take off"),
        reading = UiText.Literal("だつ"),
        badges = listOf(DetailBadge(UiText.Literal("STUCK"), accent = DetailAccent.WARNING)),
    ),
    reason = DetailPanel(
        title = UiText.Literal("Why this kanji"),
        lines = listOf(UiText.Literal("Weak in Anki")),
        accent = DetailAccent.INFO,
        emphasized = true,
    ),
    actions = DetailActions(
        review = KaniActionButton(
            label = UiText.Literal("Review now"),
            action = KaniAction.Navigation.Open(KaniDestination.Study),
        ),
        copySearch = CopySearchButton(
            label = UiText.Literal("Copy search"),
            copiedLabel = UiText.Literal("Copied."),
            search = "deck:current",
        ),
        suspend = KaniActionButton(
            label = UiText.Literal("Suspend locally"),
            action = KaniAction.SaveMnemonic(kanji = "脱", note = ""),
        ),
    ),
    mnemonic = MnemonicEditor(
        kanji = "脱",
        title = UiText.Literal("My mnemonic"),
        fieldLabel = UiText.Literal("Mnemonic note"),
        helper = UiText.Literal("A picture that sticks."),
        saveLabel = UiText.Literal("Save"),
    ),
    timeline = RecoveryTimeline(
        title = UiText.Literal("Recovery timeline"),
        status = UiText.Literal("On track"),
        statusAccent = DetailAccent.POSITIVE,
        support = UiText.Literal("2 of 2 mature"),
        events = listOf(
            TimelineEvent(
                date = UiText.Literal("Mar 3"),
                title = UiText.Literal("Imported"),
                accent = DetailAccent.INFO,
            ),
        ),
    ),
    strokeOrder = StrokeOrderDiagram(
        title = UiText.Literal("Stroke order"),
        panels = listOf(
            StrokePanel(
                strokeNumber = 1,
                strokes = listOf(
                    StrokePath(
                        points = listOf(StrokePoint(0f, 0f), StrokePoint(1f, 1f)),
                        highlighted = true,
                    ),
                ),
                startX = 0f,
                startY = 0f,
            ),
            StrokePanel(strokeNumber = 2, strokes = emptyList()),
        ),
    ),
    neighbors = NeighborPanel(
        title = UiText.Literal("Confused with"),
        rows = listOf(
            NeighborRow(
                kanji = "説",
                meaning = UiText.Literal("explain"),
                evidence = UiText.Literal("you picked it 3 times"),
            ),
        ),
    ),
    localInventory = DetailPanel(
        title = UiText.Literal("Local records"),
        lines = listOf(UiText.Literal("2 sources, 3 examples")),
        accent = DetailAccent.POSITIVE,
        emphasized = false,
    ),
    examples = listOf(
        ExampleCard(
            source = UiText.Literal("Active"),
            expression = UiText.Literal("脱出"),
            sentence = UiText.Literal("脱出する"),
            meaning = UiText.Literal("to escape"),
            accent = DetailAccent.POSITIVE,
        ),
    ),
)
