package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.importing.JapaneseText
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import java.util.Locale

class SyncKanjiInventoryBuilder {
    fun build(
        notes: List<SourceNote>,
        cards: List<SourceCard>,
        importCandidates: List<ImportedKanjiCandidate>,
        dashboardRows: List<StudyDashboardRow>,
        settings: ImportSettings,
        knownKanji: Set<String> = emptySet(),
    ): List<SyncKanjiInventoryRecord> {
        val inventory = linkedMapOf<String, MutableInventoryItem>()
        for (kanji in knownKanji) {
            inventory.item(kanji)
        }
        addActiveSourceNotes(inventory, notes, cards)
        addSuspendedImportEvidence(inventory, importCandidates)
        addDashboardEvidence(inventory, dashboardRows)
        return inventory.values.map { it.build(settings) }.sortedBy { it.kanji }
    }

    private fun addActiveSourceNotes(
        inventory: MutableMap<String, MutableInventoryItem>,
        notes: List<SourceNote>,
        cards: List<SourceCard>,
    ) {
        val activeNoteIds = cards.filter(SourceCard::active).mapTo(mutableSetOf()) { it.noteId }
        for (note in notes) {
            if (note.noteId !in activeNoteIds) {
                continue
            }
            val kanji = JapaneseText.extractKanji("${note.expression} ${note.sentence}")
            inventory.addInventoryText(kanji, note.meaning, note.reading, note.expression, note.sentence)
        }
    }

    private fun addSuspendedImportEvidence(
        inventory: MutableMap<String, MutableInventoryItem>,
        importCandidates: List<ImportedKanjiCandidate>,
    ) {
        for (candidate in importCandidates) {
            val item = inventory.item(candidate.kanji)
            for (source in candidate.sources.filter { it.suspended }) {
                item.add(source.meaning, source.reading, source.expression, source.sentence)
            }
        }
    }

    private fun addDashboardEvidence(
        inventory: MutableMap<String, MutableInventoryItem>,
        dashboardRows: List<StudyDashboardRow>,
    ) {
        for (row in dashboardRows) {
            val item = inventory.item(row.kanji)
            item.browserSearch = row.browserSearch
            item.add(row.primaryMeaning, row.reading, row.reasonText, row.browserSearch)
            for (example in row.examples) {
                item.exampleCount++
                item.add(example.meaning, example.reading, example.expression, example.sentence)
            }
        }
    }

    private fun MutableMap<String, MutableInventoryItem>.addInventoryText(
        kanji: List<String>,
        meaning: String,
        reading: String,
        expression: String,
        sentence: String,
    ) {
        for (glyph in kanji) {
            item(glyph).add(meaning, reading, expression, sentence)
        }
    }

    private fun MutableMap<String, MutableInventoryItem>.item(kanji: String): MutableInventoryItem =
        getOrPut(kanji) { MutableInventoryItem(kanji) }

    private class MutableInventoryItem(
        private val kanji: String,
    ) {
        var primaryMeaning = ""
            private set
        var browserSearch = ""
        var sourceCount = 0
            private set
        var exampleCount = 0
        private val readings = linkedSetOf<String>()
        private val searchParts = linkedSetOf(kanji.lowercase(Locale.ROOT))

        fun add(
            meaning: String,
            reading: String,
            expression: String,
            sentence: String,
        ) {
            sourceCount++
            if (primaryMeaning.isEmpty() && meaning.isNotEmpty()) {
                primaryMeaning = meaning
            }
            if (reading.isNotEmpty()) {
                readings += reading
            }
            addSearch(meaning)
            addSearch(reading)
            addSearch(expression)
            addSearch(sentence)
        }

        fun build(settings: ImportSettings): SyncKanjiInventoryRecord = SyncKanjiInventoryRecord(
            kanji = kanji,
            primaryMeaning = primaryMeaning,
            readings = readingsText(),
            browserSearch = browserSearch.ifBlank { browserSearchForKanji(kanji, settings) },
            searchText = searchParts.joinToString(" "),
            sourceCount = sourceCount,
            exampleCount = exampleCount,
        )

        private fun addSearch(value: String) {
            val normalized = JapaneseText.normalize(value)
            if (normalized.isNotEmpty()) {
                searchParts += normalized.lowercase(Locale.ROOT)
            }
        }

        private fun readingsText(): String {
            if (readings.isEmpty()) {
                return ""
            }
            val display = readings.take(MAX_DISPLAYED_READINGS)
            val hidden = readings.size - display.size
            val text = display.joinToString(" / ")
            return if (hidden == 0) text else "$text +$hidden more"
        }
    }

    companion object {
        private const val MAX_DISPLAYED_READINGS = 4

        private fun browserSearchForKanji(
            kanji: String,
            settings: ImportSettings,
        ): String =
            "note:${ankiSearchToken(settings.noteMapping.noteTypeName)} " +
                "${ankiSearchToken(settings.noteMapping.expressionField)}:*${ankiSearchValue(kanji)}*"

        private fun ankiSearchToken(value: String): String {
            val safe = ankiSearchValue(value.trim())
            return if (safe.matches(Regex("[A-Za-z0-9_\\-]+"))) {
                safe
            } else {
                "\"$safe\""
            }
        }

        private fun ankiSearchValue(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

data class SyncKanjiInventoryRecord(
    val kanji: String,
    val primaryMeaning: String,
    val readings: String,
    val browserSearch: String,
    val searchText: String,
    val sourceCount: Int,
    val exampleCount: Int,
)
