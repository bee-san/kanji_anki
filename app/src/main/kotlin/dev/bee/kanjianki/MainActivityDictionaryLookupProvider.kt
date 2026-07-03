package dev.bee.kanjianki

import dev.bee.kanjianki.core.DictionaryLookup

internal class MainActivityDictionaryLookupProvider(private val activity: MainActivityBase) {
    fun currentDictionaryLookup(): DictionaryLookup {
        return activity.warmDictionaryLookup()
    }
}
