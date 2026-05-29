package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class KanjiInventorySearchQueryTest {
    @Test
    public void requiresEachTermAcrossSearchText() {
        KanjiInventorySearchQuery query = KanjiInventorySearchQuery.parse("語 vocabulary");

        assertEquals(2, query.terms().size());
        assertTrue(query.matches("語 ご vocabulary words"));
        assertTrue(query.matches("vocabulary from 語彙 examples"));
        assertFalse(query.matches("語 ご words"));
        assertFalse(query.matches("vocabulary only"));
    }

    @Test
    public void normalizesWidthAndCaseForTerms() {
        KanjiInventorySearchQuery query = KanjiInventorySearchQuery.parse(" ｶﾀｶﾅ  ＬＡＮＧＵＡＧＥ ");

        assertTrue(query.matches("カタカナ language study"));
        assertFalse(query.matches("カタカナ reading"));
    }

    @Test
    public void blankQueryMatchesEverything() {
        KanjiInventorySearchQuery query = KanjiInventorySearchQuery.parse("  ");

        assertTrue(query.isEmpty());
        assertTrue(query.matches("any inventory row"));
    }
}
