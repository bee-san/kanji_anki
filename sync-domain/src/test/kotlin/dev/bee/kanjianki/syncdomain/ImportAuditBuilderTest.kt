package dev.bee.kanjianki.syncdomain

import java.util.LinkedHashSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                source(20L, 200L, ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_WEAK),
                source(10L, 100L, ImportRuleMatch.SOURCE_BROWSER_QUERY, ImportRuleMatch.SOURCE_BROWSER_QUERY),
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
            """{"model_name":"Basic","import_active_cards":true,"import_suspended_cards":true,"import_tagged_cards":true,"import_tags":["leech","hard\"tag"],"import_weak_cards":true,"import_weak_fsrs_difficulty":0.85,"import_weak_lapses":3,"import_browser_query_cards":true,"import_browser_query":"[redacted]","rank_min":500,"rank_max":2000,"min_matching_cards":2}""",
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
            """{"model_name":"Basic","import_active_cards":false,"import_suspended_cards":false,"import_tagged_cards":false,"import_tags":[],"import_weak_cards":false,"import_weak_fsrs_difficulty":0.85,"import_weak_lapses":3,"import_browser_query_cards":true,"import_browser_query":"[redacted]","rank_min":500,"rank_max":2000,"min_matching_cards":2}""",
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
            """{"model_name":"Basic","import_active_cards":false,"import_suspended_cards":false,"import_tagged_cards":false,"import_tags":[],"import_weak_cards":false,"import_weak_fsrs_difficulty":0.85,"import_weak_lapses":3,"import_browser_query_cards":true,"import_browser_query":"[redacted]","rank_min":500,"rank_max":2000,"min_matching_cards":2}""",
            audit.settingsJson(),
        )
    }

    @Test
    fun staticWrappersStayAvailableForJavaInterop() {
        val settings = settings(false, true, false, emptyList(), false, false, "")
        val imported = candidate("拉", 999, true)
        val sourceRules = rules(ImportRuleMatch.SOURCE_ACTIVE)
        val ruleAudit = ImportAuditBuilder::class.java.getMethod(
            "ruleAudit",
            ImportAuditBuilder.SettingsSnapshot::class.java,
        )
        val decision = ImportAuditBuilder::class.java.getMethod(
            "decision",
            ImportAuditBuilder.ImportCandidate::class.java,
            ImportAuditBuilder.SettingsSnapshot::class.java,
        )
        val enabledImportSources = ImportAuditBuilder::class.java.getMethod(
            "enabledImportSources",
            ImportAuditBuilder.SettingsSnapshot::class.java,
        )
        val reasonCode = ImportAuditBuilder::class.java.getMethod(
            "reasonCode",
            Set::class.java,
        )
        val reasonText = ImportAuditBuilder::class.java.getMethod(
            "reasonText",
            ImportAuditBuilder.ImportCandidate::class.java,
            ImportAuditBuilder.SettingsSnapshot::class.java,
            Set::class.java,
            Integer.TYPE,
        )
        val settingsJson = ImportAuditBuilder::class.java.getMethod(
            "settingsJson",
            ImportAuditBuilder.SettingsSnapshot::class.java,
        )
        val browserQueryAuditValue = ImportAuditBuilder::class.java.getMethod(
            "browserQueryAuditValue",
            ImportAuditBuilder.SettingsSnapshot::class.java,
        )

        assertEquals(listOf(ImportRuleMatch.SOURCE_SUSPENDED), (ruleAudit.invoke(null, settings) as ImportAuditBuilder.RuleAudit).enabledSources())
        assertEquals("imported", (decision.invoke(null, imported, settings) as ImportAuditBuilder.ImportDecisionAudit).reasonCode())
        assertEquals(listOf(ImportRuleMatch.SOURCE_SUSPENDED), enabledImportSources.invoke(null, settings))
        assertEquals("active_import", reasonCode.invoke(null, sourceRules))
        assertEquals(
            "Imported by active; 1 source card; Jiten rank 999; rank range 500-2000; minimum matching cards 2.",
            reasonText.invoke(null, imported, settings, sourceRules, 1),
        )
        assertEquals(
            """{"model_name":"Basic","import_active_cards":false,"import_suspended_cards":true,"import_tagged_cards":false,"import_tags":[],"import_weak_cards":false,"import_weak_fsrs_difficulty":0.85,"import_weak_lapses":3,"import_browser_query_cards":false,"import_browser_query":"","rank_min":500,"rank_max":2000,"min_matching_cards":2}""",
            settingsJson.invoke(null, settings),
        )
        assertEquals("", browserQueryAuditValue.invoke(null, settings))
    }

    private fun settings(
        active: Boolean,
        suspended: Boolean,
        tagged: Boolean,
        tags: List<String>,
        weak: Boolean,
        browserQueryEnabled: Boolean,
        browserQuery: String,
    ): ImportAuditBuilder.SettingsSnapshot {
        return ImportAuditBuilder.SettingsSnapshot(
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
    }

    private fun candidate(kanji: String, rank: Int?, known: Boolean): ImportAuditBuilder.ImportCandidate {
        return ImportAuditBuilder.ImportCandidate(kanji, rank, known, emptyList())
    }

    private fun source(cardId: Long, noteId: Long, sourceType: String, vararg rules: String): ImportAuditBuilder.ImportSource {
        return ImportAuditBuilder.ImportSource(cardId, noteId, sourceType, rules.asList())
    }

    private fun rules(vararg values: String): LinkedHashSet<String> {
        return LinkedHashSet(values.asList())
    }
}
