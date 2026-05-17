package dev.bee.kanjianki

import dev.bee.kanjianki.domain.model.similar.SimilarKanjiPair

class CoreSimilarKanjiIndexAdapter(
    private val delegate: dev.bee.kanjianki.core.SimilarKanjiIndex,
) : dev.bee.kanjianki.domain.model.similar.SimilarKanjiIndex {
    override fun pairsWithin(kanji: Collection<String>): List<SimilarKanjiPair> =
        delegate.pairsWithin(kanji).map { pair ->
            SimilarKanjiPair.canonical(pair.kanjiA, pair.kanjiB, pair.source)
        }
}
