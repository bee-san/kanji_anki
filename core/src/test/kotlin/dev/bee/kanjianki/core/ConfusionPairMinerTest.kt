package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfusionPairMinerTest {
    private val miner = ConfusionPairMiner()
    private val now = 1_000L * 24L * 60L * 60L * 1000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun minesUnorderedPairsWithAtLeastTwoRecentWrongPicks() {
        val pairs = miner.minePairs(
            listOf(
                wrongPick("拉", "提", now - day),
                wrongPick("提", "拉", now - 2 * day),
                wrongPick("裂", "列", now - day),
            ),
            now,
        )

        assertEquals(1, pairs.size)
        val pair = pairs[0]
        assertEquals(ConfusionPairMiner.SOURCE_USER_CONFUSION, pair.source)
        assertEquals(setOf("拉", "提"), setOf(pair.kanjiA, pair.kanjiB))
        assertEquals(now - 2 * day, pair.firstSeenAtMillis)
        assertEquals(now - day, pair.lastSeenAtMillis)
    }

    @Test
    fun picksOutsideNinetyDayWindowDoNotCount() {
        val pairs = miner.minePairs(
            listOf(
                wrongPick("拉", "提", now - 91 * day),
                wrongPick("拉", "提", now - day),
            ),
            now,
        )

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun exactlyTwoPicksAtWindowEdgeMine() {
        val pairs = miner.minePairs(
            listOf(
                wrongPick("拉", "提", now - 90 * day),
                wrongPick("拉", "提", now),
            ),
            now,
        )

        assertEquals(1, pairs.size)
    }

    @Test
    fun correctPicksSelfPicksAndBlankGlyphsAreExcluded() {
        val pairs = miner.minePairs(
            listOf(
                ConfusionPairMiner.WrongPickRow("拉", "提", true, now - day),
                ConfusionPairMiner.WrongPickRow("拉", "提", true, now - day),
                wrongPick("拉", "拉", now - day),
                wrongPick("拉", "拉", now - 2 * day),
                wrongPick("", "提", now - day),
                wrongPick("拉", "", now - day),
                wrongPick("拉", "x", now - day),
                null,
            ),
            now,
        )

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun singleWrongPickIsBelowThreshold() {
        assertTrue(miner.minePairs(listOf(wrongPick("拉", "提", now - day)), now).isEmpty())
    }

    @Test
    fun minedPairsAreSortedDeterministically() {
        val pairs = miner.minePairs(
            listOf(
                wrongPick("謎", "迷", now - day),
                wrongPick("謎", "迷", now - day),
                wrongPick("拉", "提", now - day),
                wrongPick("拉", "提", now - day),
            ),
            now,
        )

        assertEquals(2, pairs.size)
        assertEquals("拉", pairs[0].kanjiA)
        assertEquals("謎", pairs[1].kanjiA)
    }

    private fun wrongPick(target: String, selected: String, reviewedAt: Long): ConfusionPairMiner.WrongPickRow {
        return ConfusionPairMiner.WrongPickRow(target, selected, false, reviewedAt)
    }
}
