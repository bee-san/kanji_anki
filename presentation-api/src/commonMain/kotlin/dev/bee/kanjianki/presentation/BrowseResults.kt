package dev.bee.kanjianki.presentation

/**
 * One kanji row in Browse.
 *
 * [studied] is the user's own choice about whether Kani should practise this kanji,
 * which is Kani-side queue state and not something written back to the collection —
 * see CLAUDE.md's write-surface rule. [suspended] is the opposite kind of fact: it is
 * read from the collection and Kani never changes it.
 */
data class BrowseRow(
    val kanji: String,
    val meaning: UiText,
    val readings: UiText = UiText.EMPTY,
    val summary: UiText = UiText.EMPTY,
    val suspended: Boolean = false,
    val studied: Boolean = !suspended,
) {
    init {
        require(kanji.isNotBlank()) { "a browse row is about a kanji" }
    }

    /** Toggling whether Kani practises this kanji. */
    fun studiedAction(studied: Boolean): KaniAction =
        KaniAction.Browse.SetStudied(kanji = kanji, studied = studied)
}

/**
 * A Browse result set, with the query that produced it.
 *
 * The filter toggles are modelled as destinations rather than as callbacks, which is
 * the portable form of what Android already did: `onToggleSimilarFilter(query)`
 * re-rendered Browse with the flag flipped. Making that a
 * [KaniAction.Navigation.Open] means the filter state lives in the back stack, so
 * back from a filtered list returns to the unfiltered one instead of dropping the
 * filter silently.
 *
 * [truncated] is passed rather than derived from `rows.size >= 300`. The limit is the
 * query layer's, and a host that changed it would otherwise have to remember to
 * change a magic number here too.
 */
data class BrowseResults(
    val query: String = "",
    val rows: List<BrowseRow> = emptyList(),
    val onlySimilarKanji: Boolean = false,
    val allKanjiScope: Boolean = false,
    val showSuspended: Boolean = false,
    val truncated: Boolean = false,
) {
    /** How many rows the user has marked for study. */
    val studiedCount: Int
        get() = rows.count { it.studied }

    /** True when every row is marked, which is what the "select all" control reports. */
    val allStudied: Boolean
        get() = rows.isNotEmpty() && studiedCount == rows.size

    /** True when no row is marked. */
    val noneStudied: Boolean
        get() = studiedCount == 0

    /** The same list with one filter flipped, as a destination to open. */
    fun withSimilarFilter(only: Boolean): KaniAction = openBrowse(onlySimilarKanji = only)

    /** The same list with suspended rows shown or hidden. */
    fun withSuspendedShown(shown: Boolean): KaniAction = openBrowse(showSuspended = shown)

    /** Re-running the search for [query], which is what the Search button does. */
    fun search(query: String): KaniAction = openBrowse(query = query)

    /**
     * Opening one row's detail, carrying the search that found it.
     *
     * On [BrowseResults] rather than on [BrowseRow] because only the result set knows
     * the query and filters, and [KaniDestination.Detail] needs them to make back
     * return to this list — its own KDoc calls that the difference between "close this
     * card" and "lose my search". A row-level `detailDestination()` would have to
     * default them, quietly losing the search on every tap.
     */
    fun open(row: BrowseRow): KaniAction = KaniAction.Navigation.Open(
        KaniDestination.Detail(
            kanji = row.kanji,
            fromBrowse = true,
            query = query,
            onlySimilarKanji = onlySimilarKanji,
            allKanjiScope = allKanjiScope,
            showSuspended = showSuspended,
        ),
    )

    /** Marking or clearing every row currently listed. */
    fun setAllStudied(studied: Boolean): KaniAction =
        KaniAction.Browse.SetAllStudied(studied = studied)

    private fun openBrowse(
        query: String = this.query,
        onlySimilarKanji: Boolean = this.onlySimilarKanji,
        showSuspended: Boolean = this.showSuspended,
    ): KaniAction = KaniAction.Navigation.Open(
        KaniDestination.Browse(
            query = query,
            onlySimilarKanji = onlySimilarKanji,
            allKanjiScope = allKanjiScope,
            showSuspended = showSuspended,
        ),
    )

    companion object {
        /**
         * Builds a result set from every candidate row, dropping suspended ones unless
         * asked for.
         *
         * Hoisted out of the renderer because Android decided it in
         * `buildBrowseScreenData`, whose comment gives the reason: default Browse is a
         * projection of the study queue, and a suspended card is not in it. A host that
         * filtered in its own query instead would disagree with the checkbox above the
         * list about what "show suspended" means.
         */
        fun of(
            candidates: List<BrowseRow>,
            query: String = "",
            onlySimilarKanji: Boolean = false,
            allKanjiScope: Boolean = false,
            showSuspended: Boolean = false,
            truncated: Boolean = false,
        ): BrowseResults = BrowseResults(
            query = query,
            rows = candidates.filter { showSuspended || !it.suspended },
            onlySimilarKanji = onlySimilarKanji,
            allKanjiScope = allKanjiScope,
            showSuspended = showSuspended,
            truncated = truncated,
        )
    }
}

/**
 * A host limitation Home explains rather than hides.
 *
 * Only one case so far, and it is deliberately not "the capability is missing, so grey
 * the button out": this one changes how the user's early reviews are scheduled without
 * disabling anything, so the only correct response is to say so.
 */
enum class HomeNotice {
    /**
     * The provider cannot report FSRS memory state, so maturity is inferred.
     *
     * [PlatformCapability.PROVIDER_FSRS_MEMORY]'s own documentation has said this
     * since it was written — AnkiDroid's provider reports memory state and AnkiConnect
     * does not advertise it at all, so admission seeds from the card's interval
     * instead. Until now nothing rendered it, which meant a desktop user's first
     * intervals differed from an Android user's for no visible reason.
     */
    REDUCED_FSRS_PRECISION,
}

/** Decides which host limitations Home should explain, from the capabilities present. */
object HomeNoticePolicy {
    fun notices(capabilities: PlatformCapabilities): List<HomeNotice> = buildList {
        if (!capabilities.supports(PlatformCapability.PROVIDER_FSRS_MEMORY)) {
            add(HomeNotice.REDUCED_FSRS_PRECISION)
        }
    }
}
