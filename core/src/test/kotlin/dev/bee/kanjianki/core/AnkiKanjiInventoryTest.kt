package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiKanjiInventoryTest {
    @Test
    fun collectsUniqueCodePointSafeKanjiWithoutRetainingFields() {
        val collector = AnkiKanjiInventoryCollector()

        collector.addNormalizedField("確認確認")
        collector.addNormalizedField("補助面 \uD840\uDC00")
        collector.addNormalizedField("")

        val inventory = collector.finish(notesScanned = 2, skippedNotes = 0, modelCount = 2)

        assertEquals(setOf("確", "認", "補", "助", "面", "\uD840\uDC00"), inventory.literals)
        assertEquals(6, inventory.uniqueKanjiCount)
        assertEquals(3, inventory.fieldsScanned)
        assertNull(inventory.malformedRowWarning)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (inventory.literals as MutableSet<String>).add("追")
        }
    }

    @Test
    fun appliesBoundedMalformedRowWarningThreshold() {
        assertEquals(1, AnkiKanjiInventoryCollector.warningThreshold(0))
        assertEquals(1, AnkiKanjiInventoryCollector.warningThreshold(100))
        assertEquals(2, AnkiKanjiInventoryCollector.warningThreshold(101))
        assertEquals(100, AnkiKanjiInventoryCollector.warningThreshold(10_000))
        assertEquals(100, AnkiKanjiInventoryCollector.warningThreshold(Int.MAX_VALUE))

        val noWarning = AnkiKanjiInventoryCollector()
            .finish(notesScanned = 1_000, skippedNotes = 9, modelCount = 1)
        val warning = AnkiKanjiInventoryCollector()
            .finish(notesScanned = 99, skippedNotes = 1, modelCount = 1)

        assertNull(noWarning.malformedRowWarning)
        assertEquals(
            AnkiKanjiInventory.MalformedRowWarning(skippedNotes = 1, warningThreshold = 1),
            warning.malformedRowWarning,
        )
    }

    @Test
    fun progressExplicitlyRepresentsUnknownProviderTotal() {
        val indeterminate = AnkiKanjiInventoryProgress(4, 8, 1)
        val bounded = AnkiKanjiInventoryProgress(4, 8, 1, totalNotes = 10)

        assertTrue(indeterminate.isIndeterminate)
        assertFalse(bounded.isIndeterminate)
    }
}
