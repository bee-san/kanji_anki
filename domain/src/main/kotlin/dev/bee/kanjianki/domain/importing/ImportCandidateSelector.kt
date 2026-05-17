package dev.bee.kanjianki.domain.importing

import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.sync.CollectionSnapshot

fun interface KanjiRankLookup {
    fun rankOf(kanji: String): Int?
}

class ImportCandidateSelector(
    private val ranks: KanjiRankLookup,
) {
    fun select(
        snapshot: CollectionSnapshot,
        settings: ImportSettings,
    ): List<ImportedKanjiCandidate> {
        if (settings.enabledSources.isEmpty()) {
            return emptyList()
        }
        val notesById = snapshot.notes.associateBy { it.noteId }
        val sourcesByKanji = linkedMapOf<String, LinkedHashMap<CardId, ImportSourceEvidence>>()
        for (card in snapshot.cards) {
            val note = notesById[card.noteId] ?: continue
            val match = ImportRuleMatch.from(card, note, settings)
            if (!match.matches) {
                continue
            }
            addSources(sourcesByKanji, card, note, settings, match)
        }
        return sourcesByKanji.mapNotNull { (kanji, sources) ->
            if (sources.size < settings.importMinMatchingCardsPerKanji) {
                null
            } else {
                ImportedKanjiCandidate(
                    kanji = kanji,
                    jitenRank = requireNotNull(ranks.rankOf(kanji)),
                    rankRangeMax = settings.suspendedRankMax,
                    sources = sources.values.toList(),
                )
            }
        }.sortedWith(compareBy<ImportedKanjiCandidate> { it.jitenRank }.thenBy { it.kanji })
    }

    private fun addSources(
        sourcesByKanji: MutableMap<String, LinkedHashMap<CardId, ImportSourceEvidence>>,
        card: SourceCard,
        note: SourceNote,
        settings: ImportSettings,
        match: ImportRuleMatch,
    ) {
        val expression = JapaneseText.normalize(note.expression)
        for (kanji in JapaneseText.extractKanji(expression)) {
            val rank = ranks.rankOf(kanji)
            if (rank != null && rank in settings.suspendedRankMin..settings.suspendedRankMax) {
                sourcesByKanji.getOrPut(kanji) { linkedMapOf() }[card.cardId] =
                    sourceFromCard(kanji, card, note, expression, settings, match)
            }
        }
    }

    private fun sourceFromCard(
        kanji: String,
        card: SourceCard,
        note: SourceNote,
        expression: String,
        settings: ImportSettings,
        match: ImportRuleMatch,
    ): ImportSourceEvidence = ImportSourceEvidence(
        kanji = kanji,
        cardId = card.cardId,
        noteId = note.noteId,
        expression = expression,
        reading = JapaneseText.normalize(note.reading),
        meaning = JapaneseText.firstMeaningLine(note.meaning),
        sentence = JapaneseText.normalize(note.sentence),
        sourceType = match.sourceType,
        suspended = card.suspended,
        forcePractice = match.forcePractice,
        mature = card.mature(settings.matureDays),
        lapses = card.lapses,
        intervalDays = card.intervalDays,
        reps = card.reps,
        fsrsStability = card.fsrsStability,
        fsrsDifficulty = card.fsrsDifficulty,
        fsrsRetrievability = card.fsrsRetrievability,
        ruleTypes = match.ruleTypes,
    )
}

data class ImportedKanjiCandidate(
    val kanji: String,
    val jitenRank: Int,
    val rankRangeMax: Int,
    val sources: List<ImportSourceEvidence>,
) {
    init {
        require(kanji.isNotBlank()) { "kanji must not be blank" }
        require(jitenRank in 1..20_000) { "jitenRank must be in 1..20000" }
        require(rankRangeMax in 1..20_000) { "rankRangeMax must be in 1..20000" }
        require(sources.isNotEmpty()) { "sources must not be empty" }
    }
}

data class ImportSourceEvidence(
    val kanji: String,
    val cardId: CardId,
    val noteId: NoteId,
    val expression: String,
    val reading: String,
    val meaning: String,
    val sentence: String,
    val sourceType: ImportSource,
    val suspended: Boolean,
    val forcePractice: Boolean,
    val mature: Boolean,
    val lapses: Int,
    val intervalDays: Int,
    val reps: Int,
    val fsrsStability: Double?,
    val fsrsDifficulty: Double?,
    val fsrsRetrievability: Double?,
    val ruleTypes: Set<ImportSource>,
) {
    init {
        require(kanji.isNotBlank()) { "kanji must not be blank" }
        require(ruleTypes.isNotEmpty()) { "ruleTypes must not be empty" }
    }
}

private data class ImportRuleMatch(
    val active: Boolean,
    val suspended: Boolean,
    val tagged: Boolean,
    val weak: Boolean,
    val browserQuery: Boolean,
) {
    val matches: Boolean = active || suspended || tagged || weak || browserQuery

    val forcePractice: Boolean = suspended || tagged || weak || browserQuery

    val sourceType: ImportSource = when {
        suspended -> ImportSource.SUSPENDED
        browserQuery -> ImportSource.BROWSER_QUERY
        else -> ImportSource.ACTIVE
    }

    val ruleTypes: Set<ImportSource> = buildSet {
        if (active) add(ImportSource.ACTIVE)
        if (suspended) add(ImportSource.SUSPENDED)
        if (tagged) add(ImportSource.TAGGED)
        if (weak) add(ImportSource.WEAK)
        if (browserQuery) add(ImportSource.BROWSER_QUERY)
    }

    companion object {
        fun from(
            card: SourceCard,
            note: SourceNote,
            settings: ImportSettings,
        ): ImportRuleMatch = ImportRuleMatch(
            active = settings.importActiveCards && card.active,
            suspended = settings.importSuspendedCards && card.suspended,
            tagged = settings.importTaggedCards && note.hasAnyTag(settings.importTags),
            weak = settings.importWeakCards && card.isWeak(settings),
            browserQuery = settings.importBrowserQueryCards &&
                settings.importBrowserQuery.isNotBlank() &&
                card.browserQueryMatched,
        )
    }
}

private fun SourceCard.isWeak(settings: ImportSettings): Boolean =
    fsrsDifficulty?.let { it >= settings.importWeakFsrsDifficultyThreshold } == true ||
        lapses >= settings.importWeakLapsesThreshold

private fun SourceNote.hasAnyTag(importTags: List<String>): Boolean {
    if (tags.isBlank() || importTags.isEmpty()) {
        return false
    }
    val noteTags = tags.split(Regex("\\s+")).filterTo(mutableSetOf()) { it.isNotBlank() }
    return importTags.any { noteTags.contains(it.trim()) }
}
