package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiNeighborPanelPolicyTest {
    @Test
    fun emptyInputsProducesEmptyList() {
        val result = KanjiNeighborPanelPolicy.build(null, null, null, null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun blankKanjiProducesEmptyList() {
        val result = KanjiNeighborPanelPolicy.build("", emptyList(), emptyMap(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun noPairsProducesEmptyList() {
        val result = KanjiNeighborPanelPolicy.build("裂", emptyList(), emptyMap(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun pairsWithoutEvidenceStillAppear() {
        val pairs = listOf(makePair("裂", "烈"), makePair("裂", "列"))
        val result = KanjiNeighborPanelPolicy.build("裂", pairs, emptyMap(), mapOf("烈" to "fierce", "列" to "row"))
        assertEquals(2, result.size)
        val glyphs = result.map { it.kanji }.toSet()
        assertTrue(glyphs.contains("烈"))
        assertTrue(glyphs.contains("列"))
        val fierceRow = result.first { it.kanji == "烈" }
        assertEquals("fierce", fierceRow.meaning)
        assertFalse(fierceRow.hasEvidence)
        val rowRow = result.first { it.kanji == "列" }
        assertEquals("row", rowRow.meaning)
    }

    @Test
    fun evidencedNeighborsOrderedFirst() {
        val pairs = listOf(makePair("裂", "烈"), makePair("裂", "列"))
        val wrongPicks = mapOf("裂" to mapOf("列" to 3))
        val result = KanjiNeighborPanelPolicy.build("裂", pairs, wrongPicks, emptyMap())
        assertEquals("列", result[0].kanji)
        assertEquals(3, result[0].youPickedCount)
        assertTrue(result[0].hasEvidence)
        assertEquals("烈", result[1].kanji)
        assertEquals(0, result[1].youPickedCount)
    }

    @Test
    fun reverseDirectionCounted() {
        val pairs = listOf(makePair("裂", "烈"))
        val wrongPicks = mapOf("烈" to mapOf("裂" to 5))
        val result = KanjiNeighborPanelPolicy.build("裂", pairs, wrongPicks, emptyMap())
        assertEquals(1, result.size)
        assertEquals("烈", result[0].kanji)
        assertEquals(0, result[0].youPickedCount)
        assertEquals(5, result[0].itStoleCount)
        assertTrue(result[0].hasEvidence)
    }

    @Test
    fun kanaNeighborsFiltered() {
        val pairs = listOf(makePair("裂", "あ"), makePair("裂", "烈"))
        val result = KanjiNeighborPanelPolicy.build("裂", pairs, emptyMap(), emptyMap())
        assertEquals(1, result.size)
        assertEquals("烈", result[0].kanji)
    }

    @Test
    fun katakanaNeighborsFiltered() {
        val pairs = listOf(makePair("裂", "ア"), makePair("裂", "烈"))
        val result = KanjiNeighborPanelPolicy.build("裂", pairs, emptyMap(), emptyMap())
        assertEquals(1, result.size)
        assertEquals("烈", result[0].kanji)
    }

    @Test
    fun determinismOnTiedCounts() {
        val pairs = listOf(makePair("裂", "烈"), makePair("裂", "列"), makePair("裂", "劣"))
        val result1 = KanjiNeighborPanelPolicy.build("裂", pairs, emptyMap(), emptyMap())
        val result2 = KanjiNeighborPanelPolicy.build("裂", pairs, emptyMap(), emptyMap())
        assertEquals(result1.map { it.kanji }, result2.map { it.kanji })
    }

    @Test
    fun targetAppearsOnBSideOfPair() {
        val pairs = listOf(makePair("烈", "裂"))
        val result = KanjiNeighborPanelPolicy.build("裂", pairs, emptyMap(), mapOf("烈" to "fierce"))
        assertEquals(1, result.size)
        assertEquals("烈", result[0].kanji)
        assertEquals("fierce", result[0].meaning)
    }

    @Test
    fun duplicatePairsDeduplicatedByGlyph() {
        val pairs = listOf(makePair("裂", "烈"), makePair("烈", "裂"))
        val result = KanjiNeighborPanelPolicy.build("裂", pairs, emptyMap(), emptyMap())
        assertEquals(1, result.size)
        assertEquals("烈", result[0].kanji)
    }

    private fun makePair(a: String, b: String): RecordsImportModels.SimilarKanjiPair {
        return RecordsImportModels.SimilarKanjiPair(a, b, "test", 0L, 0L)
    }
}
