package dev.bee.kanjianki.domain.model.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportContractsTest {
    @Test
    fun kikuDefaultMappingMatchesProductContract() {
        assertEquals(
            NoteTypeMapping(
                noteTypeName = "Kiku",
                templateName = "Mining",
                expressionField = "Expression",
                readingField = "ExpressionReading",
                meaningField = "MainDefinition",
                sentenceField = "Sentence",
                frequencyField = "Frequency",
                frequencySortField = "FreqSort",
            ),
            NoteTypeMapping.kikuDefault,
        )
    }

    @Test
    fun importDefaultsStaySuspendedOnly() {
        val settings = ImportSettings()

        assertFalse(settings.importActiveCards)
        assertTrue(settings.importSuspendedCards)
        assertFalse(settings.importTaggedCards)
        assertFalse(settings.importWeakCards)
        assertFalse(settings.importBrowserQueryCards)
        assertEquals(100, settings.suspendedRankMin)
        assertEquals(3000, settings.suspendedRankMax)
        assertEquals(setOf(ImportSource.SUSPENDED), settings.enabledSources)
    }

    @Test
    fun noteTypeMappingAllowsOptionalBlankFields() {
        val mapping = NoteTypeMapping(
            noteTypeName = "Custom Japanese",
            templateName = "Mining",
            expressionField = "Front",
            readingField = "",
            meaningField = "",
            sentenceField = "",
            frequencyField = "",
            frequencySortField = "",
        )

        assertEquals("Custom Japanese", mapping.noteTypeName)
        assertEquals("", mapping.readingField)
        assertEquals("", mapping.meaningField)
    }

    @Test
    fun optionalImportSourcesOnlyEnableWhenConfigured() {
        val settings = ImportSettings(
            importActiveCards = true,
            importTaggedCards = true,
            importTags = listOf("kiku"),
            importWeakCards = true,
            importBrowserQueryCards = true,
            importBrowserQuery = "deck:Mining",
        )

        assertEquals(
            setOf(
                ImportSource.ACTIVE,
                ImportSource.SUSPENDED,
                ImportSource.TAGGED,
                ImportSource.WEAK,
                ImportSource.BROWSER_QUERY,
            ),
            settings.enabledSources,
        )
    }

    @Test
    fun importSettingsRejectInvalidThresholds() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportSettings(suspendedRankMin = 3000, suspendedRankMax = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportSettings(importWeakFsrsDifficultyThreshold = Double.NaN)
        }
    }

    @Test
    fun wireNamesStayStable() {
        assertEquals(ImportSource.SUSPENDED, ImportSource.fromWireName("suspended"))
        assertEquals(ImportSource.BROWSER_QUERY, ImportSource.fromWireName("browser_query"))
        assertEquals(NewCardSortMode.FREQUENCY, NewCardSortMode.fromWireName("frequency"))
        assertEquals(NewCardSortMode.FSRS_DIFFICULTY, NewCardSortMode.fromWireName("fsrs_difficulty"))
        assertEquals(NewCardSortMode.RETRIEVABILITY_RISK, NewCardSortMode.fromWireName("retrievability_risk"))
        assertEquals(NewCardSortMode.KANI_WEAKNESS, NewCardSortMode.fromWireName("kani_weakness"))
        assertEquals(NewCardSortMode.FREQUENCY, NewCardSortMode.fromWireName("unknown"))
    }
}
