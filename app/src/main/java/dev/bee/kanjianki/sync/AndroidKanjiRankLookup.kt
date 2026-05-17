package dev.bee.kanjianki.sync

import android.content.Context
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.data.DictionaryStore
import dev.bee.kanjianki.domain.importing.KanjiRankLookup

class AndroidKanjiRankLookup(
    context: Context,
) : KanjiRankLookup {
    private val appContext = context.applicationContext

    @Volatile
    private var cachedRanks: JitenKanjiRanks? = null

    override fun rankOf(kanji: String): Int? =
        ranks().rankOf(kanji)

    private fun ranks(): JitenKanjiRanks {
        cachedRanks?.let { return it }
        return synchronized(this) {
            cachedRanks ?: DictionaryStore.open(appContext).jitenRanks().also {
                cachedRanks = it
            }
        }
    }
}
