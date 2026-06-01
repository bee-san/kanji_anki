package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSettingsRepairPolicyTest {
    @Test
    fun absentSettingsDoNotNeedRepair() {
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(null, 7.0, 2, 1))
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(null, null, null, null, null, null, null, null), 7.0, 2, 1))
    }

    @Test
    fun oldDefaultImportSettingsRepairToSuspendedOnly() {
        val repair = ImportSettingsRepairPolicy.oldDefaultRepair(
            settings(1, 1, 0, "", 0, 7.0, 2, 1),
            7.0,
            2,
            1,
        )

        assertTrue(repair.shouldRepair())
        assertEquals(0, repair.importActiveCards())
        assertEquals(1, repair.importSuspendedCards())
    }

    @Test
    fun oldDefaultRepairToleratesAbsentAndEquivalentValues() {
        val commaOnlyTags = ImportSettingsRepairPolicy.oldDefaultRepair(
            settings(1, null, null, " , ", null, 7.00009, null, null),
            7.0,
            2,
            1,
        )

        assertTrue(commaOnlyTags.shouldRepair())
    }

    @Test
    fun customizedImportSettingsDoNotRepair() {
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(0, 1, 0, "", 0, 7.0, 2, 1), 7.0, 2, 1))
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 0, 0, "", 0, 7.0, 2, 1), 7.0, 2, 1))
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 1, "", 0, 7.0, 2, 1), 7.0, 2, 1))
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "focus", 0, 7.0, 2, 1), 7.0, 2, 1))
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "", 1, 7.0, 2, 1), 7.0, 2, 1))
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "", 0, 7.1, 2, 1), 7.0, 2, 1))
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "", 0, 7.0, 3, 1), 7.0, 2, 1))
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "", 0, 7.0, 2, 2), 7.0, 2, 1))
    }

    @Test
    fun importTagTrimPreservesJavaWhitespaceSemantics() {
        assertNoRepair(ImportSettingsRepairPolicy.oldDefaultRepair(settings(1, 1, 0, "\u00a0", 0, 7.0, 2, 1), 7.0, 2, 1))
    }
}

private fun assertNoRepair(repair: ImportSettingsRepairPolicy.RepairDecision) {
    assertFalse(repair.shouldRepair())
    assertEquals(0, repair.importActiveCards())
    assertEquals(1, repair.importSuspendedCards())
}

private fun settings(
    importActiveCards: Int?,
    importSuspendedCards: Int?,
    importTaggedCards: Int?,
    importTags: String?,
    importWeakCards: Int?,
    importWeakFsrsDifficulty: Double?,
    importWeakLapses: Int?,
    importMinMatchingCards: Int?,
): ImportSettingsRepairPolicy.StoredImportSettings {
    return ImportSettingsRepairPolicy.StoredImportSettings()
        .importActiveCards(importActiveCards)
        .importSuspendedCards(importSuspendedCards)
        .importTaggedCards(importTaggedCards)
        .importTags(importTags)
        .importWeakCards(importWeakCards)
        .importWeakFsrsDifficulty(importWeakFsrsDifficulty)
        .importWeakLapses(importWeakLapses)
        .importMinMatchingCards(importMinMatchingCards)
}
