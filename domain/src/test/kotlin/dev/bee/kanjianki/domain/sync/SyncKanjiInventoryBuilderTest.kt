package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.importing.ImportSourceEvidence
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncKanjiInventoryBuilderTest {
    @Test
    fun buildsInventoryFromActiveSuspendedAndDashboardEvidence() {
        val records = SyncKanjiInventoryBuilder().build(
            notes = listOf(sourceNote()),
            cards = listOf(sourceCard(suspended = false)),
            importCandidates = listOf(
                candidate(
                    "裂",
                    sourceEvidence(
                        kanji = "裂",
                        sourceType = ImportSource.SUSPENDED,
                        suspended = true,
                    ),
                ),
            ),
            dashboardRows = listOf(dashboardRow("裂")),
            settings = ImportSettings(),
        )

        val active = records.single { it.kanji == "日" }
        assertEquals("Japan", active.primaryMeaning)
        assertEquals("にほん", active.readings)
        assertTrue(active.searchText.contains("日本"))

        val suspended = records.single { it.kanji == "裂" }
        assertEquals("split", suspended.primaryMeaning)
        assertEquals(3, suspended.sourceCount)
        assertEquals(1, suspended.exampleCount)
        assertEquals("note:Kiku Expression:*裂*", suspended.browserSearch)
    }

    @Test
    fun keepsKnownKanjiWithDefaultSearchSurface() {
        val records = SyncKanjiInventoryBuilder().build(
            notes = emptyList(),
            cards = emptyList(),
            importCandidates = emptyList(),
            dashboardRows = emptyList(),
            settings = ImportSettings(),
            knownKanji = setOf("古"),
        )

        assertEquals("古", records.single().kanji)
        assertEquals("note:Kiku Expression:*古*", records.single().browserSearch)
        assertEquals(0, records.single().sourceCount)
    }

    private fun sourceNote(): SourceNote = SourceNote(
        noteId = NoteId(1),
        modelName = "Kiku",
        expression = "日本",
        reading = "にほん",
        meaning = "Japan",
        sentence = "日本へ行く。",
        fieldsJson = "{}",
        tags = "",
        lastSeenSyncId = SyncRunId(0),
    )

    private fun sourceCard(suspended: Boolean): SourceCard = SourceCard(
        cardId = CardId(10),
        noteId = NoteId(1),
        deckName = "Mining",
        ord = 0,
        queue = if (suspended) -1 else 0,
        type = 2,
        due = 0,
        intervalDays = 21,
        reps = 3,
        lapses = 0,
        suspended = suspended,
        browserQueryMatched = false,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        lastSeenSyncId = SyncRunId(0),
    )

    private fun candidate(
        kanji: String,
        source: ImportSourceEvidence,
    ): ImportedKanjiCandidate = ImportedKanjiCandidate(
        kanji = kanji,
        jitenRank = 100,
        rankRangeMax = 3000,
        sources = listOf(source),
    )

    private fun sourceEvidence(
        kanji: String,
        sourceType: ImportSource,
        suspended: Boolean,
    ): ImportSourceEvidence = ImportSourceEvidence(
        kanji = kanji,
        cardId = CardId(20),
        noteId = NoteId(2),
        expression = "${kanji}ける",
        reading = "さける",
        meaning = "split",
        sentence = "${kanji}けた。",
        sourceType = sourceType,
        suspended = suspended,
        forcePractice = true,
        mature = false,
        lapses = 0,
        intervalDays = 0,
        reps = 0,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        ruleTypes = setOf(sourceType),
    )

    private fun dashboardRow(kanji: String): StudyDashboardRow = StudyDashboardRow(
        kanji = kanji,
        jitenRank = 100,
        primaryMeaning = "split",
        reading = "さける",
        browserSearch = "note:Kiku Expression:*$kanji*",
        weaknessScore = 22,
        reasonCode = "suspended_archive",
        reasonText = "1 missed example made this a writing-practice target.",
        activeExampleCount = 0,
        suspendedExampleCount = 1,
        matureSupportCount = 0,
        examples = listOf(
            StudyExample(
                sourceType = "suspended",
                expression = "${kanji}ける",
                reading = "さける",
                meaning = "split",
                cardId = 20,
                noteId = 2,
                sentence = "${kanji}けた。",
            ),
        ),
    )
}
