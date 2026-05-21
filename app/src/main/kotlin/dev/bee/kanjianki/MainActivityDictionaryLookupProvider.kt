package dev.bee.kanjianki

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.data.DictionaryAssets

internal class MainActivityDictionaryLookupProvider(private val activity: MainActivityBase) {
    fun currentDictionaryLookup(): DictionaryLookup {
        if (activity.dictionaryLookup == null) {
            activity.dictionaryLookup = DictionaryAssets.load(activity)
        }
        return activity.dictionaryLookup!!
    }
}
