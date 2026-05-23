package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class StudyImpactPolicyTest {
    @Test
    public void summarizePreservesReviewAndWritingCounts() {
        StudyImpactPolicy.Impact impact = StudyImpactPolicy.summarize(6, 4, 3, 2, 1, 1);

        assertEquals(6, impact.totalReviews());
        assertEquals(4, impact.distinctReviewedKanji());
        assertEquals(3, impact.writingRequired());
        assertEquals(2, impact.writingPassed());
        assertEquals(1, impact.writingFailed());
        assertEquals(1, impact.manualOverrides());
    }

    @Test
    public void summarizeDoesNotClampRawAggregateValues() {
        StudyImpactPolicy.Impact impact = StudyImpactPolicy.summarize(-1, -2, -3, -4, -5, -6);

        assertEquals(-1, impact.totalReviews());
        assertEquals(-2, impact.distinctReviewedKanji());
        assertEquals(-3, impact.writingRequired());
        assertEquals(-4, impact.writingPassed());
        assertEquals(-5, impact.writingFailed());
        assertEquals(-6, impact.manualOverrides());
    }
}
