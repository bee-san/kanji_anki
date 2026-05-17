package dev.bee.kanjianki.domain.importing

import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.sync.CollectionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCandidateSelectorTest {
    @Test
    fun defaultSuspendedImportSelectsRankedKanjiOnly() {
        val selector = selector("日" to 150, "本" to 120, "語" to 3001)
        val snapshot = CollectionSnapshot(
            notes = listOf(note(expression = "<b>日本語</b>", reading = "にほんご", meaning = "Japanese|language")),
            cards = listOf(card(suspended = true)),
        )

        val candidates = selector.select(snapshot, ImportSettings())

        assertEquals(listOf("本", "日"), candidates.map { it.kanji })
        assertEquals(120, candidates[0].jitenRank)
        assertEquals(3000, candidates[0].rankRangeMax)
        assertEquals("Japanese", candidates[0].sources.single().meaning)
        assertEquals(ImportSource.SUSPENDED, candidates[0].sources.single().sourceType)
        assertEquals(setOf(ImportSource.SUSPENDED), candidates[0].sources.single().ruleTypes)
        assertTrue(candidates[0].sources.single().forcePractice)
        assertTrue(candidates[0].sources.single().suspended)
        assertFalse(candidates[0].sources.single().mature)
    }

    @Test
    fun activeTaggedWeakAndBrowserRulesArePreservedOnSourceEvidence() {
        val selector = selector("裂" to 500)
        val snapshot = CollectionSnapshot(
            notes = listOf(note(expression = "分裂", tags = "priority leech")),
            cards = listOf(
                card(
                    suspended = false,
                    browserQueryMatched = true,
                    lapses = 3,
                    intervalDays = 30,
                    fsrsDifficulty = 8.0,
                ),
            ),
        )
        val settings = ImportSettings(
            importActiveCards = true,
            importSuspendedCards = false,
            importTaggedCards = true,
            importTags = listOf("priority"),
            importWeakCards = true,
            importBrowserQueryCards = true,
            importBrowserQuery = "tag:priority",
        )

        val source = selector.select(snapshot, settings).single().sources.single()

        assertEquals(ImportSource.BROWSER_QUERY, source.sourceType)
        assertEquals(
            setOf(ImportSource.ACTIVE, ImportSource.TAGGED, ImportSource.WEAK, ImportSource.BROWSER_QUERY),
            source.ruleTypes,
        )
        assertTrue(source.forcePractice)
        assertTrue(source.mature)
        assertEquals(3, source.lapses)
        assertEquals(30, source.intervalDays)
        assertEquals(8.0, source.fsrsDifficulty)
    }

    @Test
    fun minimumMatchingCardsAndRankRangeFilterCandidates() {
        val selector = selector("裂" to 500, "浅" to 3_500)
        val settings = ImportSettings(
            importMinMatchingCardsPerKanji = 2,
            suspendedRankMax = 1_000,
        )
        val snapshot = CollectionSnapshot(
            notes = listOf(
                note(noteId = 1, expression = "分裂"),
                note(noteId = 2, expression = "裂ける 浅い"),
            ),
            cards = listOf(
                card(cardId = 10, noteId = 1, suspended = true),
                card(cardId = 20, noteId = 2, suspended = true),
            ),
        )

        val candidates = selector.select(snapshot, settings)

        assertEquals(listOf("裂"), candidates.map { it.kanji })
        assertEquals(listOf(CardId(10), CardId(20)), candidates.single().sources.map { it.cardId })
    }

    @Test
    fun noEnabledSourcesReturnsNoCandidates() {
        val selector = selector("裂" to 500)
        val settings = ImportSettings(importSuspendedCards = false)

        assertTrue(
            selector.select(
                CollectionSnapshot(
                    notes = listOf(note(expression = "分裂")),
                    cards = listOf(card(suspended = true)),
                ),
                settings,
            ).isEmpty(),
        )
    }

    private fun selector(vararg ranks: Pair<String, Int>): ImportCandidateSelector {
        val rankMap = ranks.toMap()
        return ImportCandidateSelector(KanjiRankLookup { kanji -> rankMap[kanji] })
    }

    private fun note(
        noteId: Long = 1,
        expression: String = "日本",
        reading: String = "にほん",
        meaning: String = "Japan",
        sentence: String = "",
        tags: String = "",
    ): SourceNote = SourceNote(
        noteId = NoteId(noteId),
        modelName = "Kiku",
        expression = expression,
        reading = reading,
        meaning = meaning,
        sentence = sentence,
        fieldsJson = "{}",
        tags = tags,
        lastSeenSyncId = SyncRunId(0),
    )

    private fun card(
        cardId: Long = 10,
        noteId: Long = 1,
        suspended: Boolean = true,
        browserQueryMatched: Boolean = false,
        lapses: Int = 0,
        intervalDays: Int = 0,
        fsrsDifficulty: Double? = null,
    ): SourceCard = SourceCard(
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        deckName = "Mining",
        ord = 0,
        queue = if (suspended) -1 else 0,
        type = if (suspended) 3 else 2,
        due = 0,
        intervalDays = intervalDays,
        reps = 5,
        lapses = lapses,
        suspended = suspended,
        browserQueryMatched = browserQueryMatched,
        fsrsStability = null,
        fsrsDifficulty = fsrsDifficulty,
        fsrsRetrievability = null,
        lastSeenSyncId = SyncRunId(0),
    )
}
