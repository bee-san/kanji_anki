package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ImportAuditBuilderTest {
    @Test
    fun enabledSourcesPreserveCurrentOrderAndGuards() {
        assertEquals(
            listOf(ImportRuleMatch.SOURCE_SUSPENDED),
            ImportAuditBuilder.enabledImportSources(settings(false, true, false, emptyList(), false, false, "")),
        )
        assertEquals(
            listOf(
                ImportRuleMatch.SOURCE_ACTIVE,
                ImportRuleMatch.SOURCE_SUSPENDED,
                ImportRuleMatch.SOURCE_TAGGED,
                ImportRuleMatch.SOURCE_WEAK,
                ImportRuleMatch.SOURCE_BROWSER_QUERY,
            ),
            ImportAuditBuilder.enabledImportSources(settings(true, true, true, listOf("leech"), true, true, "deck:foo")),
        )
        assertEquals(
            emptyList<String>(),
            ImportAuditBuilder.enabledImportSources(settings(false, false, false, emptyList(), false, true, "   ")),
        )
        assertEquals(
            emptyList<String>(),
            ImportAuditBuilder.enabledImportSources(settings(false, false, false, listOf("leech"), false, false, "")),
        )
    }

    @Test
    fun reasonCodePreservesPrecedence() {
        assertEquals("multiple_import_rules", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_WEAK)))
        assertEquals("browser_query_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_BROWSER_QUERY)))
        assertEquals("suspended_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_SUSPENDED)))
        assertEquals("tagged_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_TAGGED)))
        assertEquals("weak_card_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_WEAK)))
        assertEquals("active_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_ACTIVE)))
        assertEquals("imported", ImportAuditBuilder.reasonCode(emptySet()))
    }

    @Test
    fun reasonTextPreservesCurrentExactWording() {
        val settings = settings(false, true, false, emptyList(), false, false, "")
        val known = candidate("拉", 999, true)
        val unknown = candidate("謎", null, false)

        assertEquals(
            "Imported by suspended; 1 source card; Jiten rank 999; rank range 500-2000; minimum matching cards 2.",
            ImportAuditBuilder.reasonText(known, settings, rules(ImportRuleMatch.SOURCE_SUSPENDED), 1),
        )
        assertEquals(
            "Imported by unknown rule; 3 source cards; Jiten rank unknown; rank range 500-2000; minimum matching cards 2.",
            ImportAuditBuilder.reasonText(unknown, settings, emptySet(), 3),
        )
    }

    @Test
    fun decisionSummarizesSourcesAndRulesInInsertionOrder() {
        val imported = ImportAuditBuilder.ImportCandidate(
            "拉",
            999,
            true,
            listOf(
                source(20, 200, ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_WEAK),
                source(10, 100, ImportRuleMatch.SOURCE_BROWSER_QUERY, ImportRuleMatch.SOURCE_BROWSER_QUERY),
            ),
        )

        val decision = ImportAuditBuilder.decision(
            imported,
            settings(true, false, false, emptyList(), true, true, "deck:foo"),
        )

        assertEquals("multiple_import_rules", decision.reasonCode())
        assertEquals("Imported by active + weak + browser_query; 2 source cards; Jiten rank 999; rank range 500-2000; minimum matching cards 2.", decision.reasonText())
        assertEquals(2, decision.sourceCount())
        assertEquals(listOf(ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_BROWSER_QUERY), decision.sourceTypes())
        assertEquals(listOf(ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_WEAK, ImportRuleMatch.SOURCE_BROWSER_QUERY), decision.ruleTypes())
        assertEquals("20 10", decision.sourceCardIds())
        assertEquals("200 100", decision.sourceNoteIds())
    }

    @Test
    fun settingsJsonPreservesCurrentShapeAndEscaping() {
        val audit = ImportAuditBuilder.ruleAudit(
            settings(true, true, true, listOf("leech", "hard\"tag"), true, true, "deck:\"foo\"\n"),
        )

        assertEquals(
            listOf(
                ImportRuleMatch.SOURCE_ACTIVE,
                ImportRuleMatch.SOURCE_SUSPENDED,
                ImportRuleMatch.SOURCE_TAGGED,
                ImportRuleMatch.SOURCE_WEAK,
                ImportRuleMatch.SOURCE_BROWSER_QUERY,
            ),
            audit.enabledSources(),
        )
        assertEquals(
            "{\"model_name\":\"Basic\",\"import_active_cards\":true,\"import_suspended_cards\":true,\"import_tagged_cards\":true,\"import_tags\":[\"leech\",\"hard\\\"tag\"],\"import_weak_cards\":true,\"import_weak_fsrs_difficulty\":0.85,\"import_weak_lapses\":3,\"import_browser_query_cards\":true,\"import_browser_query\":\"[redacted]\",\"rank_min\":500,\"rank_max\":2000,\"min_matching_cards\":2}",
            audit.settingsJson(),
        )
    }

    @Test
    fun settingsJsonRedactsBrowserQueryTextWhileKeepingSourceEnabled() {
        val privateQuery = "deck:Private Sentence Tag rated:30:1"

        val audit = ImportAuditBuilder.ruleAudit(
            settings(false, false, false, emptyList(), false, true, privateQuery),
        )

        assertEquals(listOf(ImportRuleMatch.SOURCE_BROWSER_QUERY), audit.enabledSources())
        assertFalse(audit.settingsJson().contains(privateQuery))
        assertEquals(
            "{\"model_name\":\"Basic\",\"import_active_cards\":false,\"import_suspended_cards\":false,\"import_tagged_cards\":false,\"import_tags\":[],\"import_weak_cards\":false,\"import_weak_fsrs_difficulty\":0.85,\"import_weak_lapses\":3,\"import_browser_query_cards\":true,\"import_browser_query\":\"[redacted]\",\"rank_min\":500,\"rank_max\":2000,\"min_matching_cards\":2}",
            audit.settingsJson(),
        )
    }

    @Test
    fun browserQueryTrimPreservesJavaWhitespaceSemantics() {
        val audit = ImportAuditBuilder.ruleAudit(
            settings(false, false, false, emptyList(), false, true, "\u00a0"),
        )

        assertEquals(listOf(ImportRuleMatch.SOURCE_BROWSER_QUERY), audit.enabledSources())
        assertEquals(
            "{\"model_name\":\"Basic\",\"import_active_cards\":false,\"import_suspended_cards\":false,\"import_tagged_cards\":false,\"import_tags\":[],\"import_weak_cards\":false,\"import_weak_fsrs_difficulty\":0.85,\"import_weak_lapses\":3,\"import_browser_query_cards\":true,\"import_browser_query\":\"[redacted]\",\"rank_min\":500,\"rank_max\":2000,\"min_matching_cards\":2}",
            audit.settingsJson(),
        )
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun jvmStaticBridgeIsInvocableFromJavaReflection() {
        val method = ImportAuditBuilder::class.java.getDeclaredMethod(
            "enabledImportSources",
            ImportAuditBuilder.SettingsSnapshot::class.java,
        )
        val sources = method.invoke(
            null,
            settings(true, false, false, emptyList(), false, false, ""),
        ) as List<String>

        assertNotNull(sources)
        assertEquals(listOf(ImportRuleMatch.SOURCE_ACTIVE), sources)
    }

    private fun settings(
        active: Boolean,
        suspended: Boolean,
        tagged: Boolean,
        tags: List<String>,
        weak: Boolean,
        browserQueryEnabled: Boolean,
        browserQuery: String,
    ): ImportAuditBuilder.SettingsSnapshot = ImportAuditBuilder.SettingsSnapshot(
        "Basic",
        active,
        suspended,
        tagged,
        tags,
        weak,
        0.85,
        3,
        2,
        browserQueryEnabled,
        browserQuery,
        500,
        2000,
    )

    private fun candidate(kanji: String, rank: Int?, known: Boolean) =
        ImportAuditBuilder.ImportCandidate(kanji, rank, known, emptyList())

    private fun source(cardId: Long, noteId: Long, sourceType: String, vararg rules: String) =
        ImportAuditBuilder.ImportSource(cardId, noteId, sourceType, listOf(*rules))

    private fun rules(vararg values: String) = linkedSetOf(*values)
}
