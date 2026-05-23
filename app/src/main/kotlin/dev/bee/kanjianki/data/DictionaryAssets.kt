package dev.bee.kanjianki.data

import android.content.Context
import dev.bee.kanjianki.core.DictionaryLookup
import java.io.IOException

object DictionaryAssets {
    const val DATABASE_ASSET_NAME: String = "kanji_dictionary.db"
    const val DATABASE_SHA256_ASSET_NAME: String = "kanji_dictionary.db.sha256"
    const val DATABASE_ASSET: String = "dictionaries/$DATABASE_ASSET_NAME"
    const val DATABASE_SHA256_ASSET: String = "dictionaries/$DATABASE_SHA256_ASSET_NAME"
    const val SOURCES_ASSET: String = "dictionaries/dictionary_sources.json"

    @JvmStatic
    fun load(context: Context): DictionaryLookup {
        return try {
            DictionaryStore.open(context)
        } catch (_: IOException) {
            DictionaryLookup.empty()
        }
    }
}
