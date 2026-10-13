package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingKanjiPersistenceModelsTest {
    @Test
    fun scanStatusMapsStoredValuesAndFallsBackToFailed() {
        assertEquals(MissingKanjiScanStatus.SUCCESS, MissingKanjiScanStatus.fromStored("success"))
        assertEquals(MissingKanjiScanStatus.CANCELLED, MissingKanjiScanStatus.fromStored("cancelled"))
        assertEquals(MissingKanjiScanStatus.FAILED, MissingKanjiScanStatus.fromStored("failed"))
        assertEquals(MissingKanjiScanStatus.FAILED, MissingKanjiScanStatus.fromStored("nonsense"))
        assertEquals(MissingKanjiScanStatus.FAILED, MissingKanjiScanStatus.fromStored(null))
        assertEquals("success", MissingKanjiScanStatus.SUCCESS.storedValue)
    }

    @Test
    fun inventoryStateStalenessTracksLatestVersusPublished() {
        val publishedScan = scan(id = 1L, status = MissingKanjiScanStatus.SUCCESS)
        val published = StoredAnkiKanjiInventory(publishedScan, setOf("裂", "脱"))
        assertEquals(setOf("裂", "脱"), published.literals)

        val fresh = MissingKanjiInventoryState(published, publishedScan)
        assertFalse("published == latest is not stale", fresh.isStale)

        val stale = MissingKanjiInventoryState(published, scan(id = 2L, status = MissingKanjiScanStatus.FAILED))
        assertTrue("a newer attempt makes it stale", stale.isStale)

        val neverPublished = MissingKanjiInventoryState(null, scan(id = 3L, status = MissingKanjiScanStatus.FAILED))
        assertTrue("an attempt with no publication is stale", neverPublished.isStale)

        assertFalse("no attempt at all is not stale", MissingKanjiInventoryState(null, null).isStale)
    }

    @Test
    fun preferencesDefaultsAndPresetSet() {
        val defaults = MissingKanjiPreferences()
        assertEquals(MissingKanjiPreferences.PRESET_TOP_2000, defaults.preset)
        assertEquals(MissingKanjiFrequencyRange.TOP_2000, defaults.range)
        assertEquals("", defaults.searchQuery)
        assertTrue(
            MissingKanjiPreferences.SUPPORTED_PRESETS.containsAll(
                listOf(
                    MissingKanjiPreferences.PRESET_TOP_1000,
                    MissingKanjiPreferences.PRESET_TOP_2000,
                    MissingKanjiPreferences.PRESET_TOP_3000,
                    MissingKanjiPreferences.PRESET_TOP_5000,
                    MissingKanjiPreferences.PRESET_CUSTOM,
                ),
            ),
        )
    }

    @Test
    fun manualSourceCarriesCandidateAndFlags() {
        val source = ManualKanjiSource(
            candidate = MissingKanjiCandidate("裂", listOf("split"), emptyList(), listOf("さ.く"), 1_200),
            sourceType = ManualKanjiSource.SOURCE_TYPE_DICTIONARY,
            addedAt = 100L,
            updatedAt = 200L,
            active = true,
        )
        assertEquals("裂", source.candidate.literal)
        assertEquals("dictionary", source.sourceType)
        assertEquals("dictionary", ManualKanjiSource.SOURCE_TYPE_DICTIONARY)
        assertTrue(source.active)
        assertEquals(200L, source.updatedAt)
    }

    @Test
    fun resultAndReceiptTypesRetainValues() {
        val write = ManualKanjiSourceWriteResult(
            requestedCount = 3,
            addedLiterals = setOf("裂"),
            reactivatedLiterals = setOf("脱"),
            alreadyActiveLiterals = setOf("痛"),
            missingMeaningLiterals = setOf("鬱"),
            missingReadingLiterals = emptySet(),
            invalidCount = 1,
            duplicateCount = 2,
        )
        assertEquals(3, write.requestedCount)
        assertEquals(setOf("裂"), write.addedLiterals)
        assertEquals(2, write.duplicateCount)

        val removal = ManualKanjiSourceRemovalResult(
            requestedCount = 2,
            removedLiterals = setOf("裂"),
            reviewedLiterals = setOf("脱"),
            inactiveLiterals = emptySet(),
            invalidCount = 0,
        )
        assertEquals(setOf("裂"), removal.removedLiterals)
        assertEquals(setOf("脱"), removal.reviewedLiterals)

        val receipt = MissingKanjiExportReceipt("裂", "csv", 900L, externalNoteId = null)
        assertEquals("裂", receipt.literal)
        assertEquals("csv", receipt.destinationKey)
        assertEquals(900L, receipt.exportedAt)
        assertNull(receipt.externalNoteId)
        assertEquals(42L, receipt.copy(externalNoteId = 42L).externalNoteId)
    }

    private fun scan(id: Long, status: MissingKanjiScanStatus): MissingKanjiScanRecord =
        MissingKanjiScanRecord(
            id = id,
            startedAt = 10L,
            completedAt = 20L,
            status = status,
            notesScanned = 5,
            fieldsScanned = 6,
            uniqueKanjiCount = 2,
            skippedNotes = 1,
            modelCount = 1,
            providerFingerprint = "authority=x;spec=1",
            failureCode = "",
        )
}
