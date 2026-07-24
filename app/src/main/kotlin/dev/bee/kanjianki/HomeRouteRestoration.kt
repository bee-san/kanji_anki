package dev.bee.kanjianki

import android.os.Bundle
import dev.bee.kanjianki.core.TextUtil

/**
 * Minimal durable state for Home-owned secondary screens.
 *
 * Game rounds intentionally restore to the Games menu. Their in-memory question objects are
 * cosmetic session state and are not stable process-recreation payloads.
 */
internal data class HomeRouteRestoration(
    val destination: Destination,
    val query: String = "",
    val kanji: String = "",
    val onlySimilarKanji: Boolean = false,
    val allKanjiScope: Boolean = false,
    val showSuspended: Boolean = false,
    val fromBrowse: Boolean = false,
) {
    enum class Destination(val wireName: String) {
        FOCUS_QUEUE("focus-queue"),
        RECENT_MISTAKES("recent-mistakes"),
        BROWSE("browse"),
        DETAIL("detail"),
        READ_ONLY_DETAIL("read-only-detail"),
        GAMES("games"),
        MISSING_KANJI("missing-kanji"),
    }

    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_DESTINATION, destination.wireName)
        putString(KEY_QUERY, query.take(MAX_SAVED_QUERY_CHARS))
        putString(KEY_KANJI, kanji)
        putBoolean(KEY_ONLY_SIMILAR, onlySimilarKanji)
        putBoolean(KEY_ALL_KANJI, allKanjiScope)
        putBoolean(KEY_SHOW_SUSPENDED, showSuspended)
        putBoolean(KEY_FROM_BROWSE, fromBrowse)
    }

    companion object {
        fun focusQueue() = HomeRouteRestoration(Destination.FOCUS_QUEUE)

        fun recentMistakes() = HomeRouteRestoration(Destination.RECENT_MISTAKES)

        fun browse(
            query: String,
            onlySimilarKanji: Boolean,
            allKanjiScope: Boolean,
            showSuspended: Boolean = false,
        ) = HomeRouteRestoration(
            destination = Destination.BROWSE,
            query = query.take(MAX_SAVED_QUERY_CHARS),
            onlySimilarKanji = onlySimilarKanji,
            allKanjiScope = allKanjiScope,
            showSuspended = showSuspended,
        )

        fun detail(
            kanji: String,
            fromBrowse: Boolean,
            query: String,
            onlySimilarKanji: Boolean,
            allKanjiScope: Boolean,
            showSuspended: Boolean = false,
        ) = HomeRouteRestoration(
            destination = Destination.DETAIL,
            query = query.take(MAX_SAVED_QUERY_CHARS),
            kanji = kanji,
            onlySimilarKanji = onlySimilarKanji,
            allKanjiScope = allKanjiScope,
            showSuspended = showSuspended,
            fromBrowse = fromBrowse,
        )

        fun readOnlyDetail(kanji: String, query: String) = HomeRouteRestoration(
            destination = Destination.READ_ONLY_DETAIL,
            query = query.take(MAX_SAVED_QUERY_CHARS),
            kanji = kanji,
            allKanjiScope = true,
            fromBrowse = true,
        )

        fun games() = HomeRouteRestoration(Destination.GAMES)

        fun missingKanji() = HomeRouteRestoration(Destination.MISSING_KANJI)

        fun fromBundle(bundle: Bundle?): HomeRouteRestoration? {
            bundle ?: return null
            return try {
                val destinationName = bundle.getString(KEY_DESTINATION) ?: return null
                val destination = Destination.entries.firstOrNull { it.wireName == destinationName }
                    ?: return null
                val query = bundle.getString(KEY_QUERY).orEmpty().take(MAX_SAVED_QUERY_CHARS)
                val kanji = bundle.getString(KEY_KANJI).orEmpty()
                val normalizedKanji = if (
                    destination == Destination.DETAIL ||
                    destination == Destination.READ_ONLY_DETAIL
                ) {
                    TextUtil.normalizeSingleKanji(kanji).takeIf(String::isNotEmpty) ?: return null
                } else {
                    ""
                }
                HomeRouteRestoration(
                    destination = destination,
                    query = query,
                    kanji = normalizedKanji,
                    onlySimilarKanji = bundle.getBoolean(KEY_ONLY_SIMILAR, false),
                    allKanjiScope = bundle.getBoolean(KEY_ALL_KANJI, false),
                    showSuspended = bundle.getBoolean(KEY_SHOW_SUSPENDED, false),
                    fromBrowse = bundle.getBoolean(KEY_FROM_BROWSE, false),
                )
            } catch (_: Exception) {
                null
            }
        }

        private const val MAX_SAVED_QUERY_CHARS = 512
        private const val KEY_DESTINATION = "destination"
        private const val KEY_QUERY = "query"
        private const val KEY_KANJI = "kanji"
        private const val KEY_ONLY_SIMILAR = "only-similar"
        private const val KEY_ALL_KANJI = "all-kanji"
        private const val KEY_SHOW_SUSPENDED = "show-suspended"
        private const val KEY_FROM_BROWSE = "from-browse"
    }
}
