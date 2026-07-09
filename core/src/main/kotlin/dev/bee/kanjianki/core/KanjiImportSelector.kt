package dev.bee.kanjianki.core

import dev.bee.kanjianki.syncdomain.ImportRuleMatch

class KanjiImportSelector {
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
        snapshot: RecordsSyncModels.CollectionSnapshot?,
        settings: RecordsSyncModels.Settings?,
    ): List<RecordsImportModels.SuspendedImport> {
        if (snapshot == null || settings == null || !settings.hasImportSourceEnabled()) {
            return ArrayList()
        }
        val notesById = snapshot.notesById()
        val sourcesByKanji = LinkedHashMap<String, MutableMap<Long, RecordsImportModels.SuspendedSource>>()
        for (card in snapshot.cards) {
            val note = notesById[card.noteId]
            if (note != null) {
                val match = sourceMatch(card, note, settings)
                if (match.matches()) {
                    addSources(sourcesByKanji, card, note, settings, match)
                }
            }
        }

        val results = ArrayList<RecordsImportModels.SuspendedImport>()
        for ((kanji, sources) in sourcesByKanji) {
            if (sources.size < settings.importMinMatchingCardsPerKanji) {
                continue
            }
            val rank = ranks.rankOf(kanji)
            results.add(
                RecordsImportModels.SuspendedImport(
                    kanji,
                    rank,
                    rank != null,
                    maxRank,
                    ArrayList(sources.values),
                ),
            )
        }
        // Known ranks sort ascending (more frequent first); unknown-rank kanji
        // are still imported but sorted last, matching the suspended-import spec
        // (rare/unlisted kanji the user deliberately suspended stay in scope).
        results.sortWith(
            compareBy<RecordsImportModels.SuspendedImport> { it.jitenRank ?: Int.MAX_VALUE }
                .thenBy { it.kanji },
        )
        return results
    }

    private fun sourceMatch(
        card: RecordsSyncModels.Card,
        note: RecordsSyncModels.Note,
        settings: RecordsSyncModels.Settings,
    ): ImportRuleMatch {
        val activeMatch = settings.importActiveCards && !card.suspended
        val suspendedMatch = settings.importSuspendedCards && card.suspended
        val taggedMatch = settings.importTaggedCardsEnabled() && hasMatchingTag(note, settings.importTags)
        val weakMatch = settings.importWeakCards && weakCard(card, settings)
        val browserQueryMatch = settings.browserQueryImportEnabled() && card.browserQueryMatched
        return ImportRuleMatch.of(activeMatch, suspendedMatch, taggedMatch, weakMatch, browserQueryMatch)
    }

    private fun hasMatchingTag(note: RecordsSyncModels.Note, importTags: List<String>): Boolean {
        if (note.tags.isEmpty()) {
            return false
        }
        val noteTags = LinkedHashSet(note.tags)
        for (tag in importTags) {
            if (noteTags.contains(tag)) {
                return true
            }
        }
        return false
    }

    private fun weakCard(card: RecordsSyncModels.Card, settings: RecordsSyncModels.Settings): Boolean {
        return card.fsrsDifficulty != null && card.fsrsDifficulty >= settings.importWeakFsrsDifficultyThreshold ||
            card.lapses >= settings.importWeakLapsesThreshold
    }

    private fun addSources(
        sourcesByKanji: MutableMap<String, MutableMap<Long, RecordsImportModels.SuspendedSource>>,
        card: RecordsSyncModels.Card,
        note: RecordsSyncModels.Note,
        settings: RecordsSyncModels.Settings,
        match: ImportRuleMatch,
    ) {
        val expression = TextUtil.normalizeJapanese(note.expression(settings))
        for (kanji in TextUtil.extractKanji(expression)) {
            val rank = ranks.rankOf(kanji)
            // Import kanji whose Jiten rank falls in range, and also kanji with
            // no Jiten rank at all (names, domain vocabulary): the user
            // suspended them for a reason. Unknown-rank kanji are treated as
            // "rare" and sorted last rather than silently dropped.
            if (rank == null || (rank >= minRank && rank <= maxRank)) {
                sourcesByKanji.getOrPut(kanji) { LinkedHashMap() }[card.cardId] =
                    sourceFromCard(kanji, card, note, expression, settings, match)
            }
        }
    }

    private fun sourceFromCard(
        kanji: String,
        card: RecordsSyncModels.Card,
        note: RecordsSyncModels.Note,
        expression: String,
        settings: RecordsSyncModels.Settings,
        match: ImportRuleMatch,
    ): RecordsImportModels.SuspendedSource {
        val sourceType = match.sourceType(card.suspended)
        return RecordsImportModels.SuspendedSource(
            kanji,
            card.cardId,
            note.noteId,
            expression,
            TextUtil.normalizeJapanese(note.reading(settings)),
            TextUtil.firstMeaningLine(note.meaning(settings)),
            RecordsImportModels.SuspendedSourceDetails.builder(TextUtil.normalizeJapanese(note.sentence(settings)))
                .sourceType(sourceType)
                .suspended(card.suspended)
                .forcePractice(match.forcePractice())
                .mature(card.mature(settings.matureDays))
                .reviewStats(card.lapses, card.intervalDays, card.reps)
                .fsrs(card.fsrsStability, card.fsrsDifficulty, card.fsrsRetrievability)
                .ruleTypes(match.ruleTypes(card.suspended))
                .build(),
        )
    }
}
