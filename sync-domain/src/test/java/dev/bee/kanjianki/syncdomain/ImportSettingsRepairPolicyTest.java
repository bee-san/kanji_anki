package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImportSettingsRepairPolicyTest {
    @Test
    public void absentSettingsDoNotNeedRepair() {
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(null, 7.0, 2, 1));
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(null, null, null, null, null, null, null, null), 7.0, 2, 1));
    }

    @Test
    public void oldDefaultImportSettingsRepairToSuspendedOnly() {
        ImportSettingsRepairPolicy.RepairDecision repair = ImportSettingsRepairPolicy.oldDefaultRepair(
                settings(1, 1, 0, "", 0, 7.0, 2, 1),
                7.0,
                2,
                1
        );

        assertTrue(repair.shouldRepair());
        assertEquals(0, repair.importActiveCards());
        assertEquals(1, repair.importSuspendedCards());
    }

    @Test
    public void oldDefaultRepairToleratesAbsentAndEquivalentValues() {
        ImportSettingsRepairPolicy.RepairDecision commaOnlyTags = ImportSettingsRepairPolicy.oldDefaultRepair(
                settings(1, null, null, " , ", null, 7.00009, null, null),
                7.0,
                2,
                1
        );

        assertTrue(commaOnlyTags.shouldRepair());
    }

    @Test
    public void customizedImportSettingsDoNotRepair() {
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(0, 1, 0, "", 0, 7.0, 2, 1), 7.0, 2, 1));
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 0, 0, "", 0, 7.0, 2, 1), 7.0, 2, 1));
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 1, "", 0, 7.0, 2, 1), 7.0, 2, 1));
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "focus", 0, 7.0, 2, 1), 7.0, 2, 1));
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "", 1, 7.0, 2, 1), 7.0, 2, 1));
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "", 0, 7.1, 2, 1), 7.0, 2, 1));
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "", 0, 7.0, 3, 1), 7.0, 2, 1));
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "", 0, 7.0, 2, 2), 7.0, 2, 1));
    }

    private static void assertNoRepair(ImportSettingsRepairPolicy.RepairDecision repair) {
        assertFalse(repair.shouldRepair());
        assertEquals(0, repair.importActiveCards());
        assertEquals(1, repair.importSuspendedCards());
    }

    private static ImportSettingsRepairPolicy.StoredImportSettings settings(
            Integer importActiveCards,
            Integer importSuspendedCards,
            Integer importTaggedCards,
            String importTags,
            Integer importWeakCards,
            Double importWeakFsrsDifficulty,
            Integer importWeakLapses,
            Integer importMinMatchingCards
    ) {
        return new ImportSettingsRepairPolicy.StoredImportSettings()
                .importActiveCards(importActiveCards)
                .importSuspendedCards(importSuspendedCards)
                .importTaggedCards(importTaggedCards)
                .importTags(importTags)
                .importWeakCards(importWeakCards)
                .importWeakFsrsDifficulty(importWeakFsrsDifficulty)
                .importWeakLapses(importWeakLapses)
                .importMinMatchingCards(importMinMatchingCards);
    }
}
