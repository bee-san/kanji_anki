package dev.bee.kanjianki.core

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JitenKanjiRanksTest {
    @Test
    fun parsesRankKanjiCsv() {
        val ranks = JitenKanjiRanks.parseCsv(StringReader("Kanji,Rank\n日,1\n裂,824\n"))

        assertEquals(1, ranks.rankOf("日"))
        assertEquals(824, ranks.rankOf("裂"))
        assertNull(ranks.rankOf("謎"))
    }

    @Test
    fun parsesRankFirstRowsAndSkipsMalformedRows() {
        val ranks = JitenKanjiRanks.parseCsv(
            StringReader(
                """
                # comment

                1,日
                裂	824
                bad
                word,not-rank
                2,A
                ,3
                -1,提
                """.trimIndent(),
            ),
        )

        assertEquals(3, ranks.size())
        assertEquals(1, ranks.rankOf("日"))
        assertEquals(824, ranks.rankOf("裂"))
        assertEquals(-1, ranks.rankOf("提"))
        assertNull(ranks.rankOf("A"))
    }
}
