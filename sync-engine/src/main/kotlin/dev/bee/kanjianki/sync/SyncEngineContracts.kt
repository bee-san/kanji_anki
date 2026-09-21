package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import java.io.IOException

interface SyncAssetReaders {
    @Throws(IOException::class)
    fun loadRanks(): JitenKanjiRanks

    fun loadDictionary(): DictionaryLookup?

    @Throws(IOException::class)
    fun loadSimilarKanjiIndex(): SimilarKanjiIndex

    fun loadReadingExposure(): ReadingExposureModels.ExposureIndex
}

data class SyncPostCommitEffects(
    val reminderRescheduler: Runnable,
    val widgetRefresher: Runnable,
)
