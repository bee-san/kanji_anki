package dev.bee.kanjianki.presentation

/**
 * The one place a [KaniDestination] becomes durable key/value state, and back.
 *
 * Android saved-instance-state, a desktop window's persisted session, and a deep
 * link are three containers for the same thing: a route name plus primitive
 * arguments. Each host owns writing the map into its own container; none of them
 * owns its *shape*, because three hosts each deriving the shape from the same
 * prose is how they end up disagreeing.
 *
 * Decoding is fail-closed. An unknown route, a missing kanji, or a blank one
 * yields `null` — restoring nothing and landing on Home beats restoring a
 * half-built screen.
 *
 * **Kanji are not normalized here.** A kanji argument is checked non-blank but
 * not canonicalized: `TextUtil.normalizeSingleKanji` lives in `:core`, which this
 * module deliberately cannot see. A caller restoring from untrusted state must
 * normalize before trusting it, exactly as the Android restoration path does.
 */
object KaniDestinationCodec {
    const val KEY_DESTINATION: String = "destination"
    const val KEY_QUERY: String = "query"
    const val KEY_KANJI: String = "kanji"
    const val KEY_ONLY_SIMILAR: String = "only-similar"
    const val KEY_ALL_KANJI: String = "all-kanji"
    const val KEY_SHOW_SUSPENDED: String = "show-suspended"
    const val KEY_FROM_BROWSE: String = "from-browse"

    /**
     * The saved-query cap, carried over from Android's restoration limit.
     *
     * Saved state is a bounded transaction on Android, where an oversized query
     * is a crash rather than a truncation. The bound belongs in the codec so
     * every host inherits it instead of one host remembering to apply it.
     */
    const val MAX_QUERY_CHARS: Int = 512

    /** Argument-free destinations that survive a restart, keyed by wire route. */
    private val PARAMETERLESS: Map<String, KaniDestination> = listOf(
        KaniDestination.Home,
        KaniDestination.Study,
        KaniDestination.Stats,
        KaniDestination.FocusQueue,
        KaniDestination.RecentMistakes,
        KaniDestination.Games,
        KaniDestination.MissingKanji,
    ).associateBy(KaniDestination::route)

    private val SETTINGS_SECTIONS: Map<String, SettingsSection> =
        SettingsSection.entries.associateBy(SettingsSection::route)

    fun encode(destination: KaniDestination): Map<String, String> = buildMap {
        put(KEY_DESTINATION, destination.route)
        when (destination) {
            is KaniDestination.Browse -> {
                put(KEY_QUERY, destination.query.take(MAX_QUERY_CHARS))
                put(KEY_ONLY_SIMILAR, destination.onlySimilarKanji.toString())
                put(KEY_ALL_KANJI, destination.allKanjiScope.toString())
                put(KEY_SHOW_SUSPENDED, destination.showSuspended.toString())
            }

            is KaniDestination.Detail -> {
                put(KEY_KANJI, destination.kanji)
                put(KEY_QUERY, destination.query.take(MAX_QUERY_CHARS))
                put(KEY_ONLY_SIMILAR, destination.onlySimilarKanji.toString())
                put(KEY_ALL_KANJI, destination.allKanjiScope.toString())
                put(KEY_SHOW_SUSPENDED, destination.showSuspended.toString())
                put(KEY_FROM_BROWSE, destination.fromBrowse.toString())
            }

            is KaniDestination.ReadOnlyDetail -> {
                put(KEY_KANJI, destination.kanji)
                put(KEY_QUERY, destination.query.take(MAX_QUERY_CHARS))
            }

            else -> Unit
        }
    }

    fun decode(state: Map<String, String>): KaniDestination? {
        val route = state[KEY_DESTINATION] ?: return null
        PARAMETERLESS[route]?.let { return it }
        SETTINGS_SECTIONS[route]?.let { return KaniDestination.Settings(it) }
        return when (route) {
            KaniDestination.Browse.ROUTE -> KaniDestination.Browse(
                query = state.query(),
                onlySimilarKanji = state.flag(KEY_ONLY_SIMILAR),
                allKanjiScope = state.flag(KEY_ALL_KANJI),
                showSuspended = state.flag(KEY_SHOW_SUSPENDED),
            )

            KaniDestination.Detail.ROUTE -> state.kanji()?.let { kanji ->
                KaniDestination.Detail(
                    kanji = kanji,
                    fromBrowse = state.flag(KEY_FROM_BROWSE),
                    query = state.query(),
                    onlySimilarKanji = state.flag(KEY_ONLY_SIMILAR),
                    allKanjiScope = state.flag(KEY_ALL_KANJI),
                    showSuspended = state.flag(KEY_SHOW_SUSPENDED),
                )
            }

            KaniDestination.ReadOnlyDetail.ROUTE -> state.kanji()?.let { kanji ->
                KaniDestination.ReadOnlyDetail(kanji = kanji, query = state.query())
            }

            else -> null
        }
    }

    private fun Map<String, String>.kanji(): String? =
        this[KEY_KANJI]?.takeIf(String::isNotBlank)

    private fun Map<String, String>.query(): String =
        this[KEY_QUERY].orEmpty().take(MAX_QUERY_CHARS)

    /**
     * Absent or unparseable flags read as `false`.
     *
     * A restored screen showing a scope filter it cannot justify is worse than
     * one showing the default view, and every flag here defaults to the narrower
     * behavior.
     */
    private fun Map<String, String>.flag(key: String): Boolean =
        this[key]?.toBooleanStrictOrNull() ?: false
}
