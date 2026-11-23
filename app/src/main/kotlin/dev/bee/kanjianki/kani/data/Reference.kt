package dev.bee.kanjianki.kani.data

import android.content.Context
import dev.bee.kanjianki.R
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.data.DictionaryStore
import dev.bee.kanjianki.kani.core.LookAlikes
import dev.bee.kanjianki.kani.core.StrokeData
import dev.bee.kanjianki.kani.core.Stroke

/** Bundled reference data: KANJIDIC meanings and readings, look-alike pairs, KanjiVG strokes. */
class Reference(context: Context) {
    private val appContext = context.applicationContext

    private val dictionary: DictionaryLookup by lazy {
        runCatching { DictionaryStore.open(appContext) }.getOrElse { DictionaryLookup.empty() }
    }

    private val similarIndex: SimilarKanjiIndex by lazy {
        appContext.resources.openRawResource(R.raw.similar_kanji).bufferedReader().use { SimilarKanjiIndex.parseTsv(it) }
    }

    private val lookAlikes: LookAlikes by lazy {
        val scores = appContext.resources.openRawResource(R.raw.look_alike_scores).bufferedReader().use { LookAlikes.parseScores(it) }
        LookAlikes(similarIndex::similarTo, scores)
    }

    private val strokeData: StrokeData by lazy {
        appContext.resources.openRawResource(R.raw.kanji_strokes).bufferedReader().use { StrokeData.parse(it) }
    }

    val lookup: DictionaryLookup get() = dictionary

    fun meaning(kanji: String): String = dictionary.lookupKanji(kanji)?.meanings?.firstOrNull().orEmpty()

    fun meanings(kanji: String): List<String> = dictionary.lookupKanji(kanji)?.meanings.orEmpty()

    fun onReadings(kanji: String): List<String> = dictionary.lookupKanji(kanji)?.onReadings.orEmpty()

    /** Kanji that look like [kanji] closely enough to say so, closest first. */
    fun similar(kanji: String): List<String> = lookAlikes.of(kanji)

    /** Every kanji the pair list names for [kanji], closest first, loose ones included. */
    fun listedSimilar(kanji: String): List<String> = lookAlikes.listed(kanji)

    fun strokes(kanji: String): List<Stroke>? = strokeData.of(kanji)

    /** Loads the lazy data off the main thread. */
    fun warm() {
        lookAlikes.of("一")
        strokeData.of("一")
        dictionary.lookupKanji("一")
    }
}
