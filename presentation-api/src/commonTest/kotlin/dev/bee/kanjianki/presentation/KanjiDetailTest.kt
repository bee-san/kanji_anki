package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KanjiDetailTest {
    @Test
    fun aCopySearchButtonCarriesItsSearchAndItsOwnConfirmation() {
        // Derived rather than passed, for the reason FocusCard.action is: the Android
        // model took a `Runnable` each call site built, and one built with the wrong
        // text would compile and copy the wrong thing.
        val button = CopySearchButton(
            label = UiText.Literal("Copy search"),
            copiedLabel = UiText.Literal("Copied."),
            search = "deck:current tag:kani",
        )

        assertEquals(
            KaniAction.RequestCopy(
                text = "deck:current tag:kani",
                confirmation = UiText.Literal("Copied."),
            ),
            button.action,
        )
    }

    @Test
    fun copyingNothingIsNotAButton() {
        assertFailsWith<IllegalArgumentException> {
            CopySearchButton(
                label = UiText.Literal("Copy"),
                copiedLabel = UiText.Literal("Copied."),
                search = "",
            )
        }
    }

    @Test
    fun aMnemonicEditorSavesTheTypedNoteKeyedOnItsKanji() {
        val editor = MnemonicEditor(
            kanji = "脱",
            title = UiText.Literal("My mnemonic"),
            fieldLabel = UiText.Literal("Mnemonic note"),
            helper = UiText.Literal("A picture that sticks."),
            saveLabel = UiText.Literal("Save"),
        )

        assertEquals(
            KaniAction.SaveMnemonic(kanji = "脱", note = "snake escaping"),
            editor.saveAction("snake escaping"),
        )
        // An empty save is a clear, not a rejected action: the host distinguishes them.
        assertEquals(
            KaniAction.SaveMnemonic(kanji = "脱", note = ""),
            editor.saveAction(""),
        )
    }

    @Test
    fun aMnemonicEditorAboutNoKanjiCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> {
            MnemonicEditor(
                kanji = " ",
                title = UiText.EMPTY,
                fieldLabel = UiText.EMPTY,
                helper = UiText.EMPTY,
                saveLabel = UiText.EMPTY,
            )
        }
    }

    @Test
    fun aNeighbourOpensItsOwnDetailKeepingTheBrowseContext() {
        val row = NeighborRow(kanji = "説", meaning = UiText.Literal("explain"))

        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Detail(kanji = "説", fromBrowse = true)),
            row.action,
        )
    }

    @Test
    fun aNeighbourRowAboutNoKanjiCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> { NeighborRow(kanji = "") }
    }

    @Test
    fun aDetailAboutNoKanjiCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> {
            KanjiDetail(
                kanji = " ",
                identity = identity(),
                reason = panel(),
                actions = actions(),
                mnemonic = mnemonic(),
                timeline = timeline(),
            )
        }
    }

    @Test
    fun aDetailHoldsEveryOptionalPartAndDefaultsTheRestToAbsent() {
        val detail = KanjiDetail(
            kanji = "脱",
            identity = identity(),
            reason = panel(),
            actions = actions(),
            mnemonic = mnemonic(),
            timeline = timeline(),
        )

        // The optional panels are genuinely absent by default rather than empty
        // placeholders, so the surface can skip them instead of rendering blank cards.
        assertNull(detail.strokeOrder)
        assertNull(detail.neighbors)
        assertNull(detail.localInventory)
        assertNull(detail.missing)
        assertEquals(emptyList(), detail.examples)
    }

    @Test
    fun theValueTypesCarryTheDataTheSurfaceLaysOut() {
        // A construction test for the plain carriers, so a field renamed out from
        // under the surface fails here rather than in a windowed render test.
        val stroke = StrokeOrderDiagram(
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
            ),
        )
        assertEquals(1, stroke.panels.single().strokeNumber)
        val path = stroke.panels.single().strokes.single()
        assertEquals(true, path.highlighted)
        assertEquals(StrokePoint(1f, 1f), path.points.last())

        val timeline = RecoveryTimeline(
            title = UiText.Literal("Recovery timeline"),
            status = UiText.Literal("On track"),
            statusAccent = DetailAccent.POSITIVE,
            support = UiText.Literal("2 of 2 mature"),
            events = listOf(TimelineEvent(title = UiText.Literal("Imported"))),
        )
        assertEquals(DetailAccent.POSITIVE, timeline.statusAccent)
        assertEquals(DetailAccent.INFO, timeline.events.single().accent)

        val example = ExampleCard(
            source = UiText.Literal("Active"),
            expression = UiText.Literal("脱出"),
        )
        assertEquals(DetailAccent.POSITIVE, example.accent)

        val neighbors = NeighborPanel(
            title = UiText.Literal("Confused with"),
            rows = listOf(NeighborRow(kanji = "説")),
        )
        assertEquals("説", neighbors.rows.single().kanji)

        val identity = DetailIdentity(
            title = UiText.Literal("take off"),
            reading = UiText.Literal("だつ"),
            badges = listOf(DetailBadge(UiText.Literal("STUCK"))),
        )
        assertEquals(DetailAccent.WARNING, identity.badges.single().accent)

        val missing = DetailMissing(
            title = UiText.Literal("Kanji not found"),
            body = UiText.Literal("No local record found."),
        )
        assertEquals(UiText.Literal("Kanji not found"), missing.title)

        val actions = DetailActions(
            review = KaniActionButton(
                label = UiText.Literal("Review now"),
                action = KaniAction.Navigation.Open(KaniDestination.Study),
            ),
            suspend = KaniActionButton(
                label = UiText.Literal("Suspend"),
                action = KaniAction.SaveMnemonic(kanji = "脱", note = ""),
            ),
        )
        assertNull(actions.copySearch)
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Study),
            actions.review?.action,
        )

        val band = DetailPanel(
            title = UiText.Literal("Why this kanji"),
            lines = listOf(UiText.Literal("Weak in Anki")),
            accent = DetailAccent.INFO,
            emphasized = true,
        )
        assertEquals(DetailAccent.INFO, band.accent)
    }

    private fun identity() = DetailIdentity(title = UiText.Literal("take off"))

    private fun panel() = DetailPanel(
        title = UiText.EMPTY,
        lines = emptyList(),
        accent = DetailAccent.INFO,
        emphasized = true,
    )

    private fun actions() = DetailActions(
        suspend = KaniActionButton(
            label = UiText.Literal("Suspend"),
            action = KaniAction.Navigation.Back,
        ),
    )

    private fun mnemonic() = MnemonicEditor(
        kanji = "脱",
        title = UiText.EMPTY,
        fieldLabel = UiText.EMPTY,
        helper = UiText.EMPTY,
        saveLabel = UiText.EMPTY,
    )

    private fun timeline() = RecoveryTimeline(
        title = UiText.EMPTY,
        status = UiText.EMPTY,
        statusAccent = DetailAccent.INFO,
        support = UiText.EMPTY,
    )
}
