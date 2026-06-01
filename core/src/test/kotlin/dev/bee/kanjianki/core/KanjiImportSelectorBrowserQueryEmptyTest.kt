package dev.bee.kanjianki.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.util.LinkedHashMap

class KanjiImportSelectorBrowserQueryEmptyTest {
    @Test
    fun importsRemainEmptyWhenBrowserQueryDisabled() {
        assertImportsRemainEmpty(
            description = "disabled flag ignores matched card",
            browserQueryCards = false,
            browserQuery = "tag:kani",
            rankCsv = "裂,1500\n"
        )
    }

    @Test
    fun importsRemainEmptyWhenBrowserQueryBlank() {
        assertImportsRemainEmpty(
            description = "enabled with blank query ignores matched card",
            browserQueryCards = true,
            browserQuery = "  ",
            rankCsv = "裂,1500\n"
        )
    }

    @Test
    fun importsRemainEmptyWhenBrowserQueryRankOutOfRange() {
        assertImportsRemainEmpty(
            description = "enabled but rank out of range filters card",
            browserQueryCards = true,
            browserQuery = "tag:kani",
            rankCsv = "裂,5000\n"
        )
    }

    private fun assertImportsRemainEmpty(
        description: String,
        browserQueryCards: Boolean,
        browserQuery: String,
        rankCsv: String
    ) {
        val settings = settingsWithBrowserQuery(browserQueryCards, browserQuery)
        val ranks = JitenKanjiRanks.parseCsv(StringReader(rankCsv))
        val queryMatched = card(10, 1, false).withBrowserQueryMatched(true)
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(note(1, "裂ける", "さける")),
            listOf(queryMatched)
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertTrue(description, imports.isEmpty())
    }

    private fun note(id: Long, expression: String, reading: String): RecordsSyncModels.Note {
        val fields = LinkedHashMap<String, String>()
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        fields[defaults.expressionField] = expression
        fields[defaults.readingField] = reading
        fields[defaults.meaningField] = "meaning"
        fields[defaults.sentenceField] = expression + " sentence"
        fields[defaults.frequencyField] = "9999"
        fields[defaults.frequencySortField] = "9999"
        return RecordsSyncModels.Note(id, "Kiku", fields, emptyList())
    }

    private fun card(cardId: Long, noteId: Long, suspended: Boolean): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(
            cardId,
            noteId,
            0,
            "例文マイニング",
            if (suspended) -1 else 2,
            if (suspended) 3 else 2,
            0,
            if (suspended) 0 else 30,
            3,
            0,
            suspended,
            null,
            null,
            null
        )
    }

    private fun settingsWithBrowserQuery(browserQueryCards: Boolean, browserQuery: String): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            defaults.modelName,
            defaults.templateName,
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            defaults.matureDays,
            defaults.matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
            false,
            false,
            false,
            emptyList<String>(),
            false,
            7.0,
            2,
            1,
            browserQueryCards,
            browserQuery
        )
    }
}
