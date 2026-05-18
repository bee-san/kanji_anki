package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SimilarKanjiRepairPolicyTest {
    @Test
    public void passCompletesRepairAndClearsToken() {
        SimilarKanjiRepairPolicy.FinishUpdate update = SimilarKanjiRepairPolicy.finishUpdate(
                repair(2),
                true,
                5000L
        );

        assertEquals("", update.activeToken());
        assertEquals(5000L, update.updatedAtMillis());
        assertEquals("complete", update.status());
        assertEquals(Long.valueOf(5000L), update.completedAtMillis());
        assertNull(update.attempts());
        assertNull(update.dueAtMillis());
    }

    @Test
    public void failClearsTokenAndRetriesImmediately() {
        SimilarKanjiRepairPolicy.FinishUpdate update = SimilarKanjiRepairPolicy.finishUpdate(
                repair(2),
                false,
                6000L
        );

        assertEquals("", update.activeToken());
        assertEquals(6000L, update.updatedAtMillis());
        assertNull(update.status());
        assertNull(update.completedAtMillis());
        assertEquals(Integer.valueOf(3), update.attempts());
        assertEquals(Long.valueOf(6000L), update.dueAtMillis());
    }

    private static RecordsImportModels.SimilarKanjiWritingRepair repair(int attempts) {
        return new RecordsImportModels.SimilarKanjiWritingRepair(
                9L,
                "拉",
                "提",
                "拉\t提",
                "提",
                "pull",
                "pending",
                1000L,
                "token",
                attempts,
                1000L,
                2000L,
                0L
        );
    }
}
