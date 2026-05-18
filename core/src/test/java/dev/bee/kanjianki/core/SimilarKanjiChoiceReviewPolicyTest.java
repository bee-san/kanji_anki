package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SimilarKanjiChoiceReviewPolicyTest {
    @Test
    public void correctChoicePassesCardAndIncrementsCorrectCount() {
        SimilarKanjiChoiceReviewPolicy.ReviewUpdate update = SimilarKanjiChoiceReviewPolicy.reviewUpdate(
                card(2, 3),
                result(true),
                5000L
        );

        assertEquals(5000L, update.lastReviewedAtMillis());
        assertEquals(5000L, update.passedAtMillis());
        assertNull(update.dueAtMillis());
        assertEquals(Integer.valueOf(3), update.correctCount());
        assertNull(update.wrongCount());
    }

    @Test
    public void wrongChoiceKeepsCardDueAndIncrementsWrongCount() {
        SimilarKanjiChoiceReviewPolicy.ReviewUpdate update = SimilarKanjiChoiceReviewPolicy.reviewUpdate(
                card(2, 3),
                result(false),
                6000L
        );

        assertEquals(6000L, update.lastReviewedAtMillis());
        assertEquals(0L, update.passedAtMillis());
        assertEquals(Long.valueOf(6000L), update.dueAtMillis());
        assertNull(update.correctCount());
        assertEquals(Integer.valueOf(4), update.wrongCount());
    }

    private static RecordsImportModels.SimilarKanjiChoiceCard card(int correctCount, int wrongCount) {
        return new RecordsImportModels.SimilarKanjiChoiceCard(
                "拉",
                "pull",
                Arrays.asList("拉", "提"),
                "拉\t提",
                1000L,
                0L,
                0L,
                correctCount,
                wrongCount
        );
    }

    private static RecordsImportModels.SimilarKanjiChoiceResult result(boolean correct) {
        return new RecordsImportModels.SimilarKanjiChoiceResult(
                card(2, 3),
                correct ? "拉" : "提",
                correct,
                Collections.emptyList()
        );
    }
}
