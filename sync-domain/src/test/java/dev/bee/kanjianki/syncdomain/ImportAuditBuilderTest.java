package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ImportAuditBuilderTest {
    @Test
    public void enabledSourcesPreserveCurrentOrderAndGuards() {
        assertEquals(
                Collections.singletonList(ImportRuleMatch.SOURCE_SUSPENDED),
                ImportAuditBuilder.enabledImportSources(settings(false, true, false, Collections.emptyList(), false, false, ""))
        );
        assertEquals(
                Arrays.asList(
                        ImportRuleMatch.SOURCE_ACTIVE,
                        ImportRuleMatch.SOURCE_SUSPENDED,
                        ImportRuleMatch.SOURCE_TAGGED,
                        ImportRuleMatch.SOURCE_WEAK,
                        ImportRuleMatch.SOURCE_BROWSER_QUERY
                ),
                ImportAuditBuilder.enabledImportSources(settings(true, true, true, Collections.singletonList("leech"), true, true, "deck:foo"))
        );
        assertEquals(
                Collections.emptyList(),
                ImportAuditBuilder.enabledImportSources(settings(false, false, false, Collections.emptyList(), false, true, "   "))
        );
        assertEquals(
                Collections.emptyList(),
                ImportAuditBuilder.enabledImportSources(settings(false, false, false, Collections.singletonList("leech"), false, false, ""))
        );
    }

    @Test
    public void reasonCodePreservesPrecedence() {
        assertEquals("multiple_import_rules", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_WEAK)));
        assertEquals("browser_query_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_BROWSER_QUERY)));
        assertEquals("suspended_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_SUSPENDED)));
        assertEquals("tagged_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_TAGGED)));
        assertEquals("weak_card_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_WEAK)));
        assertEquals("active_import", ImportAuditBuilder.reasonCode(rules(ImportRuleMatch.SOURCE_ACTIVE)));
        assertEquals("imported", ImportAuditBuilder.reasonCode(Collections.emptySet()));
    }

    @Test
    public void reasonTextPreservesCurrentExactWording() {
        ImportAuditBuilder.SettingsSnapshot settings = settings(false, true, false, Collections.emptyList(), false, false, "");
        ImportAuditBuilder.ImportCandidate known = candidate("拉", 999, true);
        ImportAuditBuilder.ImportCandidate unknown = candidate("謎", null, false);

        assertEquals(
                "Imported by suspended; 1 source card; Jiten rank 999; rank range 500-2000; minimum matching cards 2.",
                ImportAuditBuilder.reasonText(known, settings, rules(ImportRuleMatch.SOURCE_SUSPENDED), 1)
        );
        assertEquals(
                "Imported by unknown rule; 3 source cards; Jiten rank unknown; rank range 500-2000; minimum matching cards 2.",
                ImportAuditBuilder.reasonText(unknown, settings, Collections.emptySet(), 3)
        );
    }

    @Test
    public void decisionSummarizesSourcesAndRulesInInsertionOrder() {
        ImportAuditBuilder.ImportCandidate imported = new ImportAuditBuilder.ImportCandidate(
                "拉",
                999,
                true,
                Arrays.asList(
                        source(20, 200, ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_WEAK),
                        source(10, 100, ImportRuleMatch.SOURCE_BROWSER_QUERY, ImportRuleMatch.SOURCE_BROWSER_QUERY)
                )
        );

        ImportAuditBuilder.ImportDecisionAudit decision = ImportAuditBuilder.decision(
                imported,
                settings(true, false, false, Collections.emptyList(), true, true, "deck:foo")
        );

        assertEquals("multiple_import_rules", decision.reasonCode());
        assertEquals("Imported by active + weak + browser_query; 2 source cards; Jiten rank 999; rank range 500-2000; minimum matching cards 2.", decision.reasonText());
        assertEquals(2, decision.sourceCount());
        assertEquals(Arrays.asList(ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_BROWSER_QUERY), decision.sourceTypes());
        assertEquals(Arrays.asList(ImportRuleMatch.SOURCE_ACTIVE, ImportRuleMatch.SOURCE_WEAK, ImportRuleMatch.SOURCE_BROWSER_QUERY), decision.ruleTypes());
        assertEquals("20 10", decision.sourceCardIds());
        assertEquals("200 100", decision.sourceNoteIds());
    }

    @Test
    public void settingsJsonPreservesCurrentShapeAndEscaping() {
        ImportAuditBuilder.RuleAudit audit = ImportAuditBuilder.ruleAudit(
                settings(true, true, true, Arrays.asList("leech", "hard\"tag"), true, true, "deck:\"foo\"\n")
        );

        assertEquals(
                Arrays.asList(
                        ImportRuleMatch.SOURCE_ACTIVE,
                        ImportRuleMatch.SOURCE_SUSPENDED,
                        ImportRuleMatch.SOURCE_TAGGED,
                        ImportRuleMatch.SOURCE_WEAK,
                        ImportRuleMatch.SOURCE_BROWSER_QUERY
                ),
                audit.enabledSources()
        );
        assertEquals(
                "{\"model_name\":\"Basic\",\"import_active_cards\":true,\"import_suspended_cards\":true,\"import_tagged_cards\":true,\"import_tags\":[\"leech\",\"hard\\\"tag\"],\"import_weak_cards\":true,\"import_weak_fsrs_difficulty\":0.85,\"import_weak_lapses\":3,\"import_browser_query_cards\":true,\"import_browser_query\":\"[redacted]\",\"rank_min\":500,\"rank_max\":2000,\"min_matching_cards\":2}",
                audit.settingsJson()
        );
    }

    @Test
    public void settingsJsonRedactsBrowserQueryTextWhileKeepingSourceEnabled() {
        String privateQuery = "deck:Private Sentence Tag rated:30:1";

        ImportAuditBuilder.RuleAudit audit = ImportAuditBuilder.ruleAudit(
                settings(false, false, false, Collections.emptyList(), false, true, privateQuery)
        );

        assertEquals(Collections.singletonList(ImportRuleMatch.SOURCE_BROWSER_QUERY), audit.enabledSources());
        assertFalse(audit.settingsJson().contains(privateQuery));
        assertEquals(
                "{\"model_name\":\"Basic\",\"import_active_cards\":false,\"import_suspended_cards\":false,\"import_tagged_cards\":false,\"import_tags\":[],\"import_weak_cards\":false,\"import_weak_fsrs_difficulty\":0.85,\"import_weak_lapses\":3,\"import_browser_query_cards\":true,\"import_browser_query\":\"[redacted]\",\"rank_min\":500,\"rank_max\":2000,\"min_matching_cards\":2}",
                audit.settingsJson()
        );
    }

    @Test
    public void browserQueryTrimPreservesJavaWhitespaceSemantics() {
        ImportAuditBuilder.RuleAudit audit = ImportAuditBuilder.ruleAudit(
                settings(false, false, false, Collections.emptyList(), false, true, "\u00a0")
        );

        assertEquals(Collections.singletonList(ImportRuleMatch.SOURCE_BROWSER_QUERY), audit.enabledSources());
        assertEquals(
                "{\"model_name\":\"Basic\",\"import_active_cards\":false,\"import_suspended_cards\":false,\"import_tagged_cards\":false,\"import_tags\":[],\"import_weak_cards\":false,\"import_weak_fsrs_difficulty\":0.85,\"import_weak_lapses\":3,\"import_browser_query_cards\":true,\"import_browser_query\":\"[redacted]\",\"rank_min\":500,\"rank_max\":2000,\"min_matching_cards\":2}",
                audit.settingsJson()
        );
    }

    private static ImportAuditBuilder.SettingsSnapshot settings(
            boolean active,
            boolean suspended,
            boolean tagged,
            java.util.List<String> tags,
            boolean weak,
            boolean browserQueryEnabled,
            String browserQuery
    ) {
        return new ImportAuditBuilder.SettingsSnapshot(
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
                2000
        );
    }

    private static ImportAuditBuilder.ImportCandidate candidate(String kanji, Integer rank, boolean known) {
        return new ImportAuditBuilder.ImportCandidate(kanji, rank, known, Collections.emptyList());
    }

    private static ImportAuditBuilder.ImportSource source(long cardId, long noteId, String sourceType, String... rules) {
        return new ImportAuditBuilder.ImportSource(cardId, noteId, sourceType, Arrays.asList(rules));
    }

    private static LinkedHashSet<String> rules(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
