package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportRuleMatchTest {
    @Test
    fun emptyMatchDoesNotImportOrForcePractice() {
        val match = ImportRuleMatch.of(false, false, false, false, false)

        assertFalse(match.matches())
        assertFalse(match.browserQuery())
        assertFalse(match.forcePractice())
        assertEquals(ImportRuleMatch.SOURCE_ACTIVE, match.sourceType(false))
        assertEquals(emptyList<String>(), match.ruleTypes(false))
    }

    @Test
    fun activeRuleKeepsActiveSourceWithoutForcingPractice() {
        val match = ImportRuleMatch.of(true, false, false, false, false)

        assertTrue(match.matches())
        assertFalse(match.forcePractice())
        assertEquals(ImportRuleMatch.SOURCE_ACTIVE, match.sourceType(false))
        assertEquals(listOf(ImportRuleMatch.SOURCE_ACTIVE), match.ruleTypes(false))
    }

    @Test
    fun suspendedCardsKeepSuspendedSourceEvenWhenOtherRulesMatch() {
        val match = ImportRuleMatch.of(true, true, true, true, true)

        assertTrue(match.matches())
        assertTrue(match.browserQuery())
        assertTrue(match.forcePractice())
        assertEquals(ImportRuleMatch.SOURCE_SUSPENDED, match.sourceType(true))
        assertEquals(
            listOf(
                ImportRuleMatch.SOURCE_SUSPENDED,
                ImportRuleMatch.SOURCE_TAGGED,
                ImportRuleMatch.SOURCE_WEAK,
                ImportRuleMatch.SOURCE_BROWSER_QUERY,
            ),
            match.ruleTypes(true),
        )
    }

    @Test
    fun browserQueryActiveCardsUseBrowserQuerySourceType() {
        val match = ImportRuleMatch.of(false, false, false, false, true)

        assertTrue(match.matches())
        assertTrue(match.forcePractice())
        assertEquals(ImportRuleMatch.SOURCE_BROWSER_QUERY, match.sourceType(false))
        assertEquals(listOf(ImportRuleMatch.SOURCE_BROWSER_QUERY), match.ruleTypes(false))
    }
}
