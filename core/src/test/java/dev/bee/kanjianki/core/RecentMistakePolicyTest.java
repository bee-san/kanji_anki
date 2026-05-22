package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class RecentMistakePolicyTest {
    @Test
    public void boundedLimitKeepsAtLeastOneResult() {
        assertEquals(1, RecentMistakePolicy.boundedLimit(-5));
        assertEquals(1, RecentMistakePolicy.boundedLimit(0));
        assertEquals(12, RecentMistakePolicy.boundedLimit(12));
    }

    @Test
    public void mistakeRatingsAreAgainAndHardOnly() {
        assertArrayEquals(
                new String[]{StudyRatings.AGAIN, StudyRatings.HARD},
                RecentMistakePolicy.mistakeRatings()
        );
    }

    @Test
    public void mistakeNormalizesTextButPreservesTimestamp() {
        RecentMistakePolicy.RecentMistake mistake = RecentMistakePolicy.mistake(null, null, -10L);

        assertEquals(true, mistake.getClass().isRecord());
        assertEquals("", mistake.kanji());
        assertEquals("", mistake.rating());
        assertEquals(-10L, mistake.reviewedAtMillis());
        assertEquals("RecentMistake[kanji=, rating=, reviewedAtMillis=-10]", mistake.toString());
    }
}
