package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingKanjiSelectionTest {
    @Test
    fun individualToggleIsKeyedByLiteral() {
        val selected = MissingKanjiSelection.empty()
            .toggle("日")
            .toggle("月")
            .toggle("日")

        assertFalse(selected.isSelected("日"))
        assertTrue(selected.isSelected("月"))
        assertEquals(setOf("月"), selected.selectedLiterals)
    }

    @Test
    fun selectAllVisibleAddsFilteredRowsWithoutDroppingHiddenSelection() {
        val hidden = candidate("隠", 10)
        val visible = listOf(candidate("日", 1), candidate("月", 2), candidate("日", 1))

        val selected = MissingKanjiSelection.empty()
            .selectAllVisible(listOf(hidden))
            .selectAllVisible(visible)

        assertEquals(setOf("隠", "日", "月"), selected.selectedLiterals)
    }

    @Test
    fun clearVisibleLeavesSelectionsOutsideTheCurrentFilter() {
        val all = listOf(candidate("日", 1), candidate("月", 2), candidate("火", 3))
        val selected = MissingKanjiSelection.empty()
            .selectAllVisible(all)
            .clearVisible(listOf(all[0], all[2]))

        assertEquals(setOf("月"), selected.selectedLiterals)
        assertSame(MissingKanjiSelection.empty(), selected.clearAll())
    }

    @Test
    fun reconciliationDropsRowsThatLeftTheFrequencyRange() {
        val selected = MissingKanjiSelection.from(listOf("日", "月", "火"))

        val reconciled = selected.reconcile(
            listOf(candidate("火", 3), candidate("日", 1)),
        )

        assertEquals(setOf("日", "火"), reconciled.selectedLiterals)
    }

    @Test
    fun supplementaryPlaneLiteralsAreNeverSplit() {
        val supplementary = "\uD842\uDFB7"

        val selected = MissingKanjiSelection.empty()
            .selectAllVisible(listOf(candidate(supplementary, 1)))

        assertEquals(1, selected.size)
        assertTrue(selected.isSelected(supplementary))
        assertEquals(setOf(supplementary), selected.selectedLiterals)
    }

    @Test
    fun bulkSelectionOfThousandsOfRowsBuildsOneUniqueSet() {
        val candidates = (0 until 5_000).map { index ->
            val codePoint = 0x4E00 + index
            candidate(String(Character.toChars(codePoint)), index + 1)
        }

        val selected = MissingKanjiSelection.empty().selectAllVisible(candidates)
        val reconciled = selected.reconcile(candidates.reversed())

        assertEquals(5_000, selected.size)
        assertEquals(selected, reconciled)
    }

    @Test
    fun invalidAndMultiCodePointValuesAreIgnored() {
        val selection = MissingKanjiSelection.from(listOf("", "日本", "\uD800", "日"))

        assertEquals(setOf("日"), selection.selectedLiterals)
        assertSame(selection, selection.toggle("日本"))
    }

    private fun candidate(literal: String, rank: Int): MissingKanjiCandidate {
        return MissingKanjiCandidate(literal = literal, jitenRank = rank)
    }
}
