package dev.bee.kanjianki.domain.model.similar

interface SimilarKanjiIndex {
    fun pairsWithin(kanji: Collection<String>): List<SimilarKanjiPair>
}

data class SimilarKanjiPair(
    val kanjiA: String,
    val kanjiB: String,
    val source: String = DEFAULT_SOURCE,
) {
    init {
        require(kanjiA.isNotBlank()) { "kanjiA must not be blank" }
        require(kanjiB.isNotBlank()) { "kanjiB must not be blank" }
        require(kanjiA != kanjiB) { "similar kanji pair must contain two different kanji" }
        require(source.isNotBlank()) { "source must not be blank" }
    }

    fun canonical(): SimilarKanjiPair =
        if (kanjiA <= kanjiB) this else copy(kanjiA = kanjiB, kanjiB = kanjiA)

    fun key(): String = "$kanjiA\u0000$kanjiB\u0000$source"

    companion object {
        const val DEFAULT_SOURCE = "kiku:wk-visually-similar"

        fun canonical(
            first: String,
            second: String,
            source: String = DEFAULT_SOURCE,
        ): SimilarKanjiPair = SimilarKanjiPair(
            kanjiA = first.trim(),
            kanjiB = second.trim(),
            source = source.trim().ifEmpty { DEFAULT_SOURCE },
        ).canonical()
    }
}
