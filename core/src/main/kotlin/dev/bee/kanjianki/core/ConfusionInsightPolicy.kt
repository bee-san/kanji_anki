package dev.bee.kanjianki.core

object ConfusionInsightPolicy {
    data class Pair(
        val firstKanji: String,
        val secondKanji: String,
        val firstToSecond: Int,
        val secondToFirst: Int,
        val firstMeaning: String,
        val secondMeaning: String,
    ) {
        val total: Int get() = firstToSecond + secondToFirst
    }

    @JvmStatic
    @JvmOverloads
    fun topPairs(
        wrongPickCounts: Map<String, Map<String, Int>>?,
        inventory: List<RecordsImportModels.KanjiInventoryItem>?,
        limit: Int = 5,
    ): List<Pair> {
        val meanings = inventory.orEmpty().associate { TextUtil.normalizeSingleKanji(it.kanji) to it.primaryMeaning }
        data class Counts(var forward: Int = 0, var reverse: Int = 0)
        val merged = linkedMapOf<String, Counts>()
        wrongPickCounts.orEmpty().forEach { (rawTarget, selections) ->
            val target = TextUtil.normalizeSingleKanji(rawTarget)
            selections.forEach { (rawSelected, rawCount) ->
                val selected = TextUtil.normalizeSingleKanji(rawSelected)
                val count = rawCount.coerceAtLeast(0)
                if (target.isEmpty() || selected.isEmpty() || target == selected || count == 0) return@forEach
                val first = minOf(target, selected)
                val second = maxOf(target, selected)
                val counts = merged.getOrPut("$first\u0000$second") { Counts() }
                if (target == first) counts.forward += count else counts.reverse += count
            }
        }
        return merged.mapNotNull { (key, counts) ->
            val total = counts.forward + counts.reverse
            if (total < ConfusionPairMiner.MIN_WRONG_PICKS) return@mapNotNull null
            val glyphs = key.split('\u0000')
            if (counts.reverse > counts.forward) {
                Pair(
                    glyphs[1], glyphs[0], counts.reverse, counts.forward,
                    meanings[glyphs[1]].orEmpty(), meanings[glyphs[0]].orEmpty(),
                )
            } else {
                Pair(
                    glyphs[0], glyphs[1], counts.forward, counts.reverse,
                    meanings[glyphs[0]].orEmpty(), meanings[glyphs[1]].orEmpty(),
                )
            }
        }.sortedWith(
            compareByDescending<Pair> { it.total }
                .thenByDescending { maxOf(it.firstToSecond, it.secondToFirst) }
                .thenBy { it.firstKanji }
                .thenBy { it.secondKanji }
        ).take(limit.coerceAtLeast(0))
    }
}
