package dev.bee.kanjianki.core

object KanjiNeighborPanelPolicy {
    @JvmStatic
    fun build(
        targetKanji: String?,
        pairs: List<RecordsImportModels.SimilarKanjiPair>?,
        wrongPickCounts: Map<String, Map<String, Int>>?,
        inventoryMeanings: Map<String, String>?,
    ): List<NeighborRow> {
        val normalizedTarget = TextUtil.normalizeSingleKanji(targetKanji)
        if (normalizedTarget.isEmpty()) return emptyList()
        val meanings = inventoryMeanings.orEmpty()

        val neighborGlyphs = linkedSetOf<String>()
        pairs.orEmpty().forEach { pair ->
            val a = TextUtil.normalizeSingleKanji(pair.kanjiA)
            val b = TextUtil.normalizeSingleKanji(pair.kanjiB)
            if (a == normalizedTarget && b.isNotEmpty() && !isKana(b)) neighborGlyphs.add(b)
            if (b == normalizedTarget && a.isNotEmpty() && !isKana(a)) neighborGlyphs.add(a)
        }
        if (neighborGlyphs.isEmpty()) return emptyList()

        val targetPicks = wrongPickCounts.orEmpty()[normalizedTarget].orEmpty()
        val rows = neighborGlyphs.map { neighbor ->
            val youPicked = targetPicks[neighbor] ?: 0
            val reversePicks = wrongPickCounts.orEmpty()[neighbor]?.get(normalizedTarget) ?: 0
            NeighborRow(
                kanji = neighbor,
                meaning = meanings[neighbor].orEmpty(),
                youPickedCount = youPicked,
                itStoleCount = reversePicks,
            )
        }
        return rows.sortedWith(
            compareByDescending<NeighborRow> { it.youPickedCount + it.itStoleCount }
                .thenByDescending { maxOf(it.youPickedCount, it.itStoleCount) }
                .thenBy { it.kanji }
        )
    }

    private fun isKana(ch: String): Boolean {
        if (ch.isEmpty()) return false
        val cp = ch.codePointAt(0)
        return cp in 0x3040..0x309F || cp in 0x30A0..0x30FF
    }

    class NeighborRow(
        @JvmField val kanji: String,
        @JvmField val meaning: String,
        @JvmField val youPickedCount: Int,
        @JvmField val itStoleCount: Int,
    ) {
        val hasEvidence: Boolean get() = youPickedCount > 0 || itStoleCount > 0
    }
}
