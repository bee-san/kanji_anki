package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class SimilarKanjiRepairPolicyTest {
    @Test
    public void newRepairNormalizesKanjiAndAppliesPendingDefaults() {
        SimilarKanjiRepairPolicy.RepairDraft draft = SimilarKanjiRepairPolicy.newRepair(
                choiceCard(),
                "　提　",
                null,
                4000L
        );

        assertNotNull(draft);
        assertEquals("拉", draft.targetKanji());
        assertEquals("提", draft.repairKanji());
        assertEquals("拉\t提", draft.choiceSignature());
        assertEquals("", draft.wrongSelection());
        assertEquals("pull", draft.promptMeaning());
        assertEquals("pending", draft.status());
        assertEquals(4000L, draft.dueAtMillis());
        assertEquals("", draft.activeToken());
        assertEquals(0, draft.attempts());
        assertEquals(4000L, draft.createdAtMillis());
        assertEquals(4000L, draft.updatedAtMillis());
        assertEquals(0L, draft.completedAtMillis());

        SimilarKanjiRepairPolicy.RepairDraft selected = SimilarKanjiRepairPolicy.newRepair(
                choiceCard(),
                "提",
                "拉",
                4500L
        );
        assertNotNull(selected);
        assertEquals("拉", selected.wrongSelection());
    }

    @Test
    public void newRepairRejectsMissingCardOrInvalidRepairKanji() {
        assertNull(SimilarKanjiRepairPolicy.newRepair(null, "提", "拉", 4000L));
        assertNull(SimilarKanjiRepairPolicy.newRepair(choiceCard(), "", "拉", 4000L));
        assertNull(SimilarKanjiRepairPolicy.newRepair(choiceCard(), "提拉", "拉", 4000L));
        assertNull(SimilarKanjiRepairPolicy.newRepair(choiceCard(), "A", "拉", 4000L));
    }

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

    private static RecordsImportModels.SimilarKanjiChoiceCard choiceCard() {
        return new RecordsImportModels.SimilarKanjiChoiceCard(
                "拉",
                "pull",
                Arrays.asList("拉", "提"),
                "拉\t提",
                1000L,
                0L,
                0L,
                0,
                0
        );
    }
}
