package dev.bee.kanjianki.presentation

/**
 * A single kanji's detail screen, as portable data both hosts render.
 *
 * The Android host built this as `BrowseDetailScreenModel`: a hero, an identity
 * block, an optional stroke-order diagram, a reason band, optional neighbours and
 * local-inventory panels, a mnemonic editor, an action row, a recovery timeline,
 * and example cards. This is the same shape with the host specifics removed — packed
 * ARGB ints become [DetailAccent], `Runnable`s become [KaniAction]s, and every
 * displayable string is a [UiText] the host's resolver turns into words.
 *
 * The text itself is still computed by the shared `:core` copy (`HomeTextCopy`,
 * `TimelineCopy`), exactly as Home is: a host maps its profile snapshot to this and
 * the surface only lays it out. What Android did *around* the model — clipboard
 * writes, toasts, `renderStudyForKanji` — is dispatched as [KaniAction] here so the
 * shell owns it once for both hosts.
 *
 * [missing] is the one either/or: a kanji with no local record at all is a short
 * "not found" card rather than an empty detail, and carrying it here rather than as a
 * separate route keeps the detail destination single.
 */
data class KanjiDetail(
    val kanji: String,
    val identity: DetailIdentity,
    val reason: DetailPanel,
    val actions: DetailActions,
    val mnemonic: MnemonicEditor,
    val timeline: RecoveryTimeline,
    val strokeOrder: StrokeOrderDiagram? = null,
    val neighbors: NeighborPanel? = null,
    val localInventory: DetailPanel? = null,
    val examples: List<ExampleCard> = emptyList(),
    val missing: DetailMissing? = null,
) {
    init {
        require(kanji.isNotBlank()) { "a detail is about a kanji" }
    }
}

/**
 * A colour role, not a colour.
 *
 * The three the detail panels actually use, named by meaning. Android reached for
 * `BLUE`/`TEAL`/`CORAL` from a fixed palette; naming the role instead lets the active
 * theme choose the hue, which is why `RESTING` on Android's legacy pink is not one of
 * these — a detail panel never used it.
 */
enum class DetailAccent {
    /** Informational: the reason band, the neutral timeline status. */
    INFO,

    /** Positive: earned evidence, a healthy timeline. */
    POSITIVE,

    /** Attention: a suspended or stuck card, a warning on the timeline. */
    WARNING,
}

/** A short, filled or bordered panel of lines under a title. */
data class DetailPanel(
    val title: UiText,
    val lines: List<UiText>,
    val accent: DetailAccent,
    /** A filled band reads as a headline; a bordered card reads as a footnote. */
    val emphasized: Boolean,
)

/** The name, reading, and state chips at the top of the card. */
data class DetailIdentity(
    val title: UiText,
    val reading: UiText = UiText.EMPTY,
    val badges: List<DetailBadge> = emptyList(),
)

/** One state chip — suspended, stuck — carrying its own accent. */
data class DetailBadge(
    val label: UiText,
    val accent: DetailAccent = DetailAccent.WARNING,
)

/**
 * The buttons under the identity block.
 *
 * Each is nullable because each is conditional: review only for an active,
 * non-retired card; copy only when there is an Anki search to copy. [suspend] is the
 * one that is always offered, so it is not nullable.
 */
data class DetailActions(
    val review: KaniActionButton? = null,
    val copySearch: CopySearchButton? = null,
    val suspend: KaniActionButton,
)

/** A labelled button that dispatches one action. */
data class KaniActionButton(
    val label: UiText,
    val action: KaniAction,
)

/**
 * The "copy Anki search" button.
 *
 * Distinct from [KaniActionButton] because it has a second, transient label — the
 * "Copied." confirmation the surface shows after the tap — and it dispatches a
 * [KaniAction.RequestCopy] carrying the search text rather than a caller-chosen
 * action.
 */
data class CopySearchButton(
    val label: UiText,
    val copiedLabel: UiText,
    val search: String,
) {
    init {
        require(search.isNotEmpty()) { "a copy button needs something to copy" }
    }

    /** The clipboard request, carrying its own confirmation. */
    val action: KaniAction
        get() = KaniAction.RequestCopy(text = search, confirmation = copiedLabel)
}

/**
 * The mnemonic note field and its save button.
 *
 * [initial] seeds the editor; the surface holds the edited text as local state and
 * only [saveAction] commits it, keyed on the kanji. The save is
 * [KaniAction.SaveMnemonic] rather than a callback so the write is a value the shell
 * dispatches and a test can assert on — the same reason every other action here is.
 */
data class MnemonicEditor(
    val kanji: String,
    val title: UiText,
    val fieldLabel: UiText,
    val helper: UiText,
    val saveLabel: UiText,
    val initial: String = "",
) {
    init {
        require(kanji.isNotBlank()) { "a mnemonic is about a kanji" }
    }

    /** The save action for [note], which the editor has already trimmed. */
    fun saveAction(note: String): KaniAction = KaniAction.SaveMnemonic(kanji = kanji, note = note)
}

/** The "confused with" panel: visually similar kanji the user mixes up. */
data class NeighborPanel(
    val title: UiText,
    val rows: List<NeighborRow>,
)

/** One neighbour, opening that kanji's own detail when tapped. */
data class NeighborRow(
    val kanji: String,
    val meaning: UiText = UiText.EMPTY,
    val evidence: UiText = UiText.EMPTY,
) {
    init {
        require(kanji.isNotBlank()) { "a neighbour row is about a kanji" }
    }

    /** Opening the neighbour keeps the browse context, the same as any detail open. */
    val action: KaniAction
        get() = KaniAction.Navigation.Open(KaniDestination.Detail(kanji = kanji, fromBrowse = true))
}

/**
 * The stroke-order diagram: one cell per stroke, each cumulative.
 *
 * Null on the [KanjiDetail] when no guide is available — which on desktop is every
 * kanji until the Goal 183 KanjiVG asset binaries are wired, so the surface must
 * render the rest of the card without it rather than treating its absence as an
 * error.
 */
data class StrokeOrderDiagram(
    val title: UiText,
    val panels: List<StrokePanel>,
    val overflow: UiText = UiText.EMPTY,
)

/** One stroke-order cell: the strokes drawn so far, and this stroke's start dot. */
data class StrokePanel(
    val strokeNumber: Int,
    val strokes: List<StrokePath>,
    val startX: Float? = null,
    val startY: Float? = null,
)

/** One stroke as normalized (0..1) points, highlighted when it is the newest. */
data class StrokePath(
    val points: List<StrokePoint>,
    val highlighted: Boolean,
)

/** A normalized point in a stroke cell, both coordinates in 0..1. */
data class StrokePoint(val x: Float, val y: Float)

/** The recovery timeline: a status line and the events that led to it. */
data class RecoveryTimeline(
    val title: UiText,
    val status: UiText,
    val statusAccent: DetailAccent,
    val support: UiText,
    val events: List<TimelineEvent> = emptyList(),
    val empty: UiText = UiText.EMPTY,
)

/** One timeline entry. */
data class TimelineEvent(
    val date: UiText = UiText.EMPTY,
    val title: UiText = UiText.EMPTY,
    val detail: UiText = UiText.EMPTY,
    val source: UiText = UiText.EMPTY,
    val accent: DetailAccent = DetailAccent.INFO,
)

/** One example sentence card. */
data class ExampleCard(
    val source: UiText,
    val expression: UiText,
    val sentence: UiText = UiText.EMPTY,
    val meaning: UiText = UiText.EMPTY,
    /** Coral for a suspended source, teal otherwise — the collection's own state. */
    val accent: DetailAccent = DetailAccent.POSITIVE,
)

/** The "not found" card shown when a kanji has no local record at all. */
data class DetailMissing(
    val title: UiText,
    val body: UiText,
)
