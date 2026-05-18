package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImportRuleMatchTest {
    @Test
    public void emptyMatchDoesNotImportOrForcePractice() {
        ImportRuleMatch match = ImportRuleMatch.of(false, false, false, false, false);

        assertFalse(match.matches());
        assertFalse(match.browserQuery());
        assertFalse(match.forcePractice());
        assertEquals(ImportRuleMatch.SOURCE_ACTIVE, match.sourceType(false));
        assertEquals(Collections.emptyList(), match.ruleTypes(false));
    }

    @Test
    public void activeRuleKeepsActiveSourceWithoutForcingPractice() {
        ImportRuleMatch match = ImportRuleMatch.of(true, false, false, false, false);

        assertTrue(match.matches());
        assertFalse(match.forcePractice());
        assertEquals(ImportRuleMatch.SOURCE_ACTIVE, match.sourceType(false));
        assertEquals(Collections.singletonList(ImportRuleMatch.SOURCE_ACTIVE), match.ruleTypes(false));
    }

    @Test
    public void suspendedCardsKeepSuspendedSourceEvenWhenOtherRulesMatch() {
        ImportRuleMatch match = ImportRuleMatch.of(true, true, true, true, true);

        assertTrue(match.matches());
        assertTrue(match.browserQuery());
        assertTrue(match.forcePractice());
        assertEquals(ImportRuleMatch.SOURCE_SUSPENDED, match.sourceType(true));
        assertEquals(Arrays.asList(
                ImportRuleMatch.SOURCE_SUSPENDED,
                ImportRuleMatch.SOURCE_TAGGED,
                ImportRuleMatch.SOURCE_WEAK,
                ImportRuleMatch.SOURCE_BROWSER_QUERY
        ), match.ruleTypes(true));
    }

    @Test
    public void browserQueryActiveCardsUseBrowserQuerySourceType() {
        ImportRuleMatch match = ImportRuleMatch.of(false, false, false, false, true);

        assertTrue(match.matches());
        assertTrue(match.forcePractice());
        assertEquals(ImportRuleMatch.SOURCE_BROWSER_QUERY, match.sourceType(false));
        assertEquals(Collections.singletonList(ImportRuleMatch.SOURCE_BROWSER_QUERY), match.ruleTypes(false));
    }
}
