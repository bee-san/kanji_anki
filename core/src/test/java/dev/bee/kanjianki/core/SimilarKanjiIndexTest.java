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
    public void emptyIndexAndNullLookupsAreSafe() {
        SimilarKanjiIndex index = SimilarKanjiIndex.empty();

        assertEquals(0, index.pairCount());
        assertFalse(index.areSimilar(null, "拉"));
        assertFalse(index.areSimilar("拉", null));
        assertTrue(index.similarTo(null).isEmpty());
        assertTrue(index.similarTo("拉").isEmpty());
        assertTrue(index.pairsWithin(null).isEmpty());
        assertTrue(index.pairsWithin(Collections.emptyList()).isEmpty());
    }

    @Test
    public void parseTsvTreatsPairsAsSymmetricAndDeduplicates() throws Exception {
        SimilarKanjiIndex index = SimilarKanjiIndex.parseTsv(new StringReader(
                """
                # generated
                kanji_a\tkanji_b\tsource
                拉\t麺\tfixture
                麺\t拉\tfixture
                拉\t謎\tfixture
                """
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
                """
                not enough cells
                abc\t麺\tfixture
                拉\tkana\tfixture
                拉\t拉\tfixture
                拉\t麺\t
                """
        ));

        assertEquals(1, index.pairCount());
        assertTrue(index.areSimilar("拉", "麺"));
        assertEquals(SimilarKanjiIndex.SOURCE_KIKU_VISUALLY_SIMILAR, index.pairsWithin(Arrays.asList("拉", "麺")).get(0).source);
    }

    @Test
    public void pairsWithinOnlyReturnsPairsWhereBothKanjiArePresent() throws Exception {
        SimilarKanjiIndex index = SimilarKanjiIndex.parseTsv(new StringReader(
                """
                拉\t麺\tfixture
                拉\t謎\tfixture
                確\t認\tfixture
                """
        ));

        List<SimilarKanjiIndex.Pair> pairs = index.pairsWithin(Arrays.asList("拉", "謎", "提"));

        assertEquals(1, pairs.size());
        assertEquals("拉", pairs.get(0).kanjiA);
        assertEquals("謎", pairs.get(0).kanjiB);
        assertTrue(index.pairsWithin(Collections.singletonList("拉")).isEmpty());
        assertTrue(index.pairsWithin(Arrays.asList("拉", "not-kanji", "")).isEmpty());
    }

    @Test
    public void pairEqualityUsesCanonicalKanjiAndSource() {
        SimilarKanjiIndex.Pair pair = SimilarKanjiIndex.Pair.canonical("麺", "拉", "fixture");
        SimilarKanjiIndex.Pair same = SimilarKanjiIndex.Pair.canonical("拉", "麺", "fixture");
        SimilarKanjiIndex.Pair differentSource = SimilarKanjiIndex.Pair.canonical("拉", "麺", "other");
        SimilarKanjiIndex.Pair differentKanji = SimilarKanjiIndex.Pair.canonical("拉", "謎", "fixture");

        assertEquals(pair, pair);
        assertEquals(pair, same);
        assertEquals(pair.hashCode(), same.hashCode());
        assertEquals(0, pair.compareTo(same));
        boolean equalsDifferentSource = pair.equals(differentSource);
        assertFalse(equalsDifferentSource);
        boolean equalsDifferentKanji = pair.equals(differentKanji);
        assertFalse(equalsDifferentKanji);
        boolean equalsDifferentType = pair.equals("not a pair");
        assertFalse(equalsDifferentType);
    }

    @Test
    public void pairCanonicalDefaultsBlankSourceAndSortsBySource() {
        SimilarKanjiIndex.Pair defaultSource = SimilarKanjiIndex.Pair.canonical("拉", "麺", " ");
        SimilarKanjiIndex.Pair explicitSource = SimilarKanjiIndex.Pair.canonical("拉", "麺", "zz");

        assertEquals(SimilarKanjiIndex.SOURCE_KIKU_VISUALLY_SIMILAR, defaultSource.source);
        assertTrue(defaultSource.compareTo(explicitSource) < 0);
        assertTrue(SimilarKanjiIndex.Pair.canonical("拉", "麺", "a").compareTo(defaultSource) < 0);
    }
}
