package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class StudyRepairActionsTest {
    @Test
    public void activateSimilarWritingRepairStoresActiveTokenAndProgressKeys() {
        RecordsImportModels.SimilarKanjiWritingRepair repair = repair("");
        AtomicReference<RecordsImportModels.SimilarKanjiWritingRepair> saved = new AtomicReference<>();

        StudyRepairActions.ActiveRepair active = StudyRepairActions.activateSimilarWritingRepair(repair, 1234L, saved::set);

        assertSame(saved.get(), active.repair());
        assertEquals(active.token(), active.repair().activeToken);
        assertEquals(1234L, active.repair().updatedAtMillis);
        assertTrue(active.token().startsWith("repair-42-"));
        assertEquals("repair:42", active.progressKey());
        assertEquals("repair:42:" + active.token(), active.studyTaskKey());
    }

    @Test
    public void activateSimilarWritingRepairKeepsExistingActiveToken() {
        RecordsImportModels.SimilarKanjiWritingRepair repair = repair("existing-token");

        StudyRepairActions.ActiveRepair active = StudyRepairActions.activateSimilarWritingRepair(repair, 1234L, ignored -> {
        });

        assertEquals("existing-token", active.token());
        assertEquals("existing-token", active.repair().activeToken);
        assertEquals("repair:42:existing-token", active.studyTaskKey());
    }

    private static RecordsImportModels.SimilarKanjiWritingRepair repair(String activeToken) {
        return new RecordsImportModels.SimilarKanjiWritingRepair(
                42L,
                "末",
                "未",
                "末|未",
                "末",
                "not yet",
                "pending",
                1000L,
                activeToken,
                0,
                900L,
                901L,
                0L
        );
    }
}
