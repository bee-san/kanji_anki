package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class SimilarKanjiStorageKeysTest {
    @Test
    public void canonicalPairOrdersKanjiLexically() {
        assertArrayEquals(new String[]{"拉", "麺"}, SimilarKanjiStorageKeys.canonicalPair("麺", "拉"));
        assertArrayEquals(new String[]{"拉", "麺"}, SimilarKanjiStorageKeys.canonicalPair("拉", "麺"));
    }

    @Test
    public void pairKeyKeepsLegacyNulDelimiter() {
        assertEquals("拉\u0000麺\u0000fixture", SimilarKanjiStorageKeys.pairKey("拉", "麺", "fixture"));
    }

    @Test
    public void choiceKeyKeepsLegacyControlDelimiter() {
        assertEquals("拉\u0001拉|麺", SimilarKanjiStorageKeys.choiceKey("拉", "拉|麺"));
        assertEquals("拉\u0001", SimilarKanjiStorageKeys.choiceKey("拉", null));
    }

    @Test
    public void splitChoiceKeyAcceptsMissingOrEmptySignature() {
        assertArrayEquals(new String[]{"拉", "拉|麺"}, SimilarKanjiStorageKeys.splitChoiceKey("拉\u0001拉|麺"));
        assertArrayEquals(new String[]{"拉", ""}, SimilarKanjiStorageKeys.splitChoiceKey("拉\u0001"));
        assertArrayEquals(new String[0], SimilarKanjiStorageKeys.splitChoiceKey(null));
    }
}
