package dev.bee.kanjianki.core;

import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SimilarKanjiIndexTest {
    @Test
    public void parseTsvTreatsPairsAsSymmetricAndDeduplicates() throws Exception {
        SimilarKanjiIndex index = SimilarKanjiIndex.parseTsv(new StringReader(
                "# generated\n" +
                        "kanji_a\tkanji_b\tsource\n" +
                        "拉\t麺\tfixture\n" +
                        "麺\t拉\tfixture\n" +
                        "拉\t謎\tfixture\n"
        ));

        assertEquals(2, index.pairCount());
        assertTrue(index.areSimilar("拉", "麺"));
        assertTrue(index.areSimilar("麺", "拉"));
        assertTrue(index.areSimilar("謎", "拉"));
        assertFalse(index.areSimilar("拉", "拉"));
        assertFalse(index.areSimilar("拉", "提"));
        assertEquals(Arrays.asList("謎", "麺"), index.similarTo("拉"));
    }

    @Test
    public void parseTsvSkipsMalformedRows() throws Exception {
        SimilarKanjiIndex index = SimilarKanjiIndex.parseTsv(new StringReader(
                "not enough cells\n" +
                        "abc\t麺\tfixture\n" +
                        "拉\tkana\tfixture\n" +
                        "拉\t拉\tfixture\n" +
                        "拉\t麺\t\n"
        ));

        assertEquals(1, index.pairCount());
        assertTrue(index.areSimilar("拉", "麺"));
        assertEquals(SimilarKanjiIndex.SOURCE_KIKU_VISUALLY_SIMILAR, index.pairsWithin(Arrays.asList("拉", "麺")).get(0).source);
    }

    @Test
    public void pairsWithinOnlyReturnsPairsWhereBothKanjiArePresent() throws Exception {
        SimilarKanjiIndex index = SimilarKanjiIndex.parseTsv(new StringReader(
                "拉\t麺\tfixture\n" +
                        "拉\t謎\tfixture\n" +
                        "確\t認\tfixture\n"
        ));

        List<SimilarKanjiIndex.Pair> pairs = index.pairsWithin(Arrays.asList("拉", "謎", "提"));

        assertEquals(1, pairs.size());
        assertEquals("拉", pairs.get(0).kanjiA);
        assertEquals("謎", pairs.get(0).kanjiB);
        assertTrue(index.pairsWithin(Collections.singletonList("拉")).isEmpty());
    }
}
