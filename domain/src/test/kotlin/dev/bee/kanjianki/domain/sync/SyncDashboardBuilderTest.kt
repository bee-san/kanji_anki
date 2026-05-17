package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.importing.ImportSourceEvidence
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDashboardBuilderTest {
    @Test
    fun buildsSuspendedArchiveRowFromSelectedEvidence() {
        val rows = builder().build(
            importCandidates = listOf(
                candidate(
                    kanji = "裂",
                    source = source(
                        kanji = "裂",
                        sourceType = ImportSource.SUSPENDED,
                        suspended = true,
                        forcePractice = true,
                    ),
                ),
            ),
            settings = ImportSettings(),
        )

        val row = rows.single()
        assertEquals("裂", row.kanji)
        assertEquals(100, row.jitenRank)
        assertEquals(12 + 10, row.weaknessScore)
        assertEquals("suspended_archive", row.reasonCode)
        assertEquals(0, row.activeExampleCount)
        assertEquals(1, row.suspendedExampleCount)
        assertEquals(0, row.matureSupportCount)
        assertEquals("suspended", row.examples.single().sourceType)
        assertEquals(42L, row.examples.single().cardId)
        assertEquals("note:Kiku Expression:*裂*", row.browserSearch)
    }

    @Test
    fun sortsByWeaknessSuspendedCountRankAndKanji() {
        val rows = builder().build(
            importCandidates = listOf(
                candidate("乙", source("乙", cardId = 2, mature = true)),
                candidate("甲", source("甲", cardId = 1, sourceType = ImportSource.SUSPENDED, suspended = true)),
            ),
            settings = ImportSettings(),
        )

        assertEquals(listOf("甲", "乙"), rows.map { it.kanji })
    }

    @Test
    fun activeWeakFsrsEvidenceUsesActiveExampleAndReason() {
        val rows = builder().build(
            importCandidates = listOf(
                candidate(
                    kanji = "弱",
                    source = source(
                        kanji = "弱",
                        cardId = 7,
                        fsrsDifficulty = 8.0,
                        fsrsRetrievability = 0.4,
                        reps = 10,
                    ),
                ),
            ),
            settings = ImportSettings(),
        )

        val row = rows.single()
        assertEquals("fsrs_weak_memory", row.reasonCode)
        assertEquals(1, row.activeExampleCount)
        assertEquals(0, row.suspendedExampleCount)
        assertEquals("active", row.examples.single().sourceType)
    }

    @Test
    fun preservesNonSuspendedSourceProvenance() {
        val rows = builder().build(
            importCandidates = listOf(
                candidate(
                    kanji = "弱",
                    source = source(
                        kanji = "弱",
                        cardId = 8,
                        sourceType = ImportSource.BROWSER_QUERY,
                        forcePractice = true,
                    ),
                ),
            ),
            settings = ImportSettings(importBrowserQueryCards = true, importBrowserQuery = "deck:Mining"),
        )

        assertEquals("browser_query", rows.single().examples.single().sourceType)
    }

    private fun builder(): SyncDashboardBuilder = SyncDashboardBuilder { kanji ->
        rankOf(kanji)
    }

    private fun candidate(
        kanji: String,
        source: ImportSourceEvidence,
    ): ImportedKanjiCandidate = ImportedKanjiCandidate(
        kanji = kanji,
        jitenRank = requireNotNull(rankOf(kanji)),
        rankRangeMax = 3000,
        sources = listOf(source),
    )

    private fun rankOf(kanji: String): Int? = when (kanji) {
        "裂" -> 100
        "甲" -> 200
        "乙" -> 300
        "弱" -> 400
        else -> null
    }

    private fun source(
        kanji: String,
        cardId: Long = 42,
        noteId: Long = 24,
        sourceType: ImportSource = ImportSource.ACTIVE,
        suspended: Boolean = false,
        forcePractice: Boolean = false,
        mature: Boolean = false,
        lapses: Int = 0,
        intervalDays: Int = 0,
        reps: Int = 0,
        fsrsStability: Double? = null,
        fsrsDifficulty: Double? = null,
        fsrsRetrievability: Double? = null,
    ): ImportSourceEvidence = ImportSourceEvidence(
        kanji = kanji,
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        expression = "${kanji}語",
        reading = "よみ",
        meaning = "meaning",
        sentence = "${kanji}を練習する。",
        sourceType = sourceType,
        suspended = suspended,
        forcePractice = forcePractice,
        mature = mature,
        lapses = lapses,
        intervalDays = intervalDays,
        reps = reps,
        fsrsStability = fsrsStability,
        fsrsDifficulty = fsrsDifficulty,
        fsrsRetrievability = fsrsRetrievability,
        ruleTypes = setOf(sourceType),
    )
}
