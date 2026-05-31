package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudyMoreNewCardsPolicyTest {
    @Test
    public void defaultRequestCountPreservesOneToFiveClamp() {
        assertEquals(1, StudyMoreNewCardsPolicy.defaultRequestCount(0));
        assertEquals(1, StudyMoreNewCardsPolicy.defaultRequestCount(1));
        assertEquals(3, StudyMoreNewCardsPolicy.defaultRequestCount(3));
        assertEquals(5, StudyMoreNewCardsPolicy.defaultRequestCount(9));
    }

    @Test
    public void requestedCountAcceptsTrimmedPositiveIntegers() {
        StudyMoreNewCardsPolicy.RequestDecision decision = StudyMoreNewCardsPolicy.requestedCount(" 3 ");

        assertTrue(decision.accepted());
        assertEquals(3, decision.requestedCount());
        assertEquals("", decision.message());
    }

    @Test
    public void requestedCountRejectsNonIntegersAndNonPositiveValues() {
        StudyMoreNewCardsPolicy.RequestDecision nonInteger = StudyMoreNewCardsPolicy.requestedCount("not a number");
        StudyMoreNewCardsPolicy.RequestDecision zero = StudyMoreNewCardsPolicy.requestedCount("0");

        assertFalse(nonInteger.accepted());
        assertEquals(-1, nonInteger.requestedCount());
        assertEquals("Use a whole number of new cards.", nonInteger.message());
        assertFalse(zero.accepted());
        assertEquals(-1, zero.requestedCount());
        assertEquals("Use at least 1 new card.", zero.message());
    }

    @Test
    public void partialAvailabilityMessagePreservesPluralCopy() {
        assertEquals("Only 1 new card was available.", StudyMoreNewCardsPolicy.partialAvailabilityMessage(1));
        assertEquals("Only 2 new cards were available.", StudyMoreNewCardsPolicy.partialAvailabilityMessage(2));
    }
}
