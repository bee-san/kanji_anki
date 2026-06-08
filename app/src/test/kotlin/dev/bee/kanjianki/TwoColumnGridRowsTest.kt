package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Test

class TwoColumnGridRowsTest {
    @Test
    fun iteratesTwoItemsPerRowAndKeepsOddTail() {
        val seen = mutableListOf<String>()

        listOf("A", "B", "C", "D", "E").forEachTwoColumnRowIndexed { rowIndex, first, second ->
            seen += "$rowIndex:$first:${second ?: "-"}"
        }

        assertEquals(listOf("0:A:B", "1:C:D", "2:E:-"), seen)
    }

    @Test
    fun doesNothingForEmptyLists() {
        var calls = 0

        emptyList<String>().forEachTwoColumnRowIndexed { _, _, _ ->
            calls += 1
        }

        assertEquals(0, calls)
    }
}
