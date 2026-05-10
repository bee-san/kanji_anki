package dev.bee.kanjianki.core;

import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JitenKanjiRanksTest {
    @Test
    public void parsesRankKanjiCsv() throws Exception {
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("Kanji,Rank\n日,1\n裂,824\n"));
        assertEquals(Integer.valueOf(1), ranks.rankOf("日"));
        assertEquals(Integer.valueOf(824), ranks.rankOf("裂"));
        assertNull(ranks.rankOf("謎"));
    }

    @Test
    public void parsesRankFirstRowsAndSkipsMalformedRows() throws Exception {
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader(
                """
                # comment

                1,日
                裂\t824
                bad
                word,not-rank
                2,A
                ,3
                -1,提
                """
        ));

        assertEquals(3, ranks.size());
        assertEquals(Integer.valueOf(1), ranks.rankOf("日"));
        assertEquals(Integer.valueOf(824), ranks.rankOf("裂"));
        assertEquals(Integer.valueOf(-1), ranks.rankOf("提"));
        assertNull(ranks.rankOf("A"));
    }
}
