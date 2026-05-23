package dev.bee.kanjianki.core

class SuspendedKanjiImporter {
    private val ranks: JitenKanjiRanks
    private val minRank: Int
    private val maxRank: Int

    constructor(ranks: JitenKanjiRanks, cutoff: Int) : this(
        ranks,
        RecordsBase.DEFAULT_SUSPENDED_RANK_MIN,
        cutoff,
    )

    constructor(ranks: JitenKanjiRanks, minRank: Int, maxRank: Int) {
        this.ranks = ranks
        val rankRange = SettingsInputRules.normalizedRankRange(minRank, maxRank)
        this.minRank = rankRange.minRank
        this.maxRank = rankRange.maxRank
    }

    fun importFrom(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        settings: RecordsSyncModels.Settings,
    ): List<RecordsImportModels.SuspendedImport> {
        val notesById = snapshot.notesById()
        val sourcesByKanji = LinkedHashMap<String, List<RecordsImportModels.SuspendedSource>>()
        for (card in snapshot.cards) {
            val note = notesById[card.noteId]
            if (card.suspended && note != null) {
                addSuspendedSources(sourcesByKanji, card, note, settings)
            }
        }

        return sourcesByKanji.map { (kanji, sources) ->
            val rank = ranks.rankOf(kanji)
            RecordsImportModels.SuspendedImport(
                kanji,
                rank,
                true,
                maxRank,
                sources.toList(),
            )
        }.sortedWith(compareBy<RecordsImportModels.SuspendedImport> { it.jitenRank ?: Int.MAX_VALUE }.thenBy { it.kanji })
    }

    private fun addSuspendedSources(
        sourcesByKanji: MutableMap<String, List<RecordsImportModels.SuspendedSource>>,
        card: RecordsSyncModels.Card,
        note: RecordsSyncModels.Note,
        settings: RecordsSyncModels.Settings,
    ) {
        val expression = TextUtil.normalizeJapanese(note.expression(settings))
        for (kanji in TextUtil.extractKanji(expression)) {
            val rank = ranks.rankOf(kanji)
            if (rank != null && rank >= minRank && rank <= maxRank) {
                val source = RecordsImportModels.SuspendedSource(
                    kanji,
                    card.cardId,
                    note.noteId,
                    expression,
                    TextUtil.normalizeJapanese(note.reading(settings)),
                    TextUtil.firstMeaningLine(note.meaning(settings)),
                    TextUtil.normalizeJapanese(note.sentence(settings)),
                )
                sourcesByKanji[kanji] = sourcesByKanji[kanji].orEmpty() + source
            }
        }
    }
}
