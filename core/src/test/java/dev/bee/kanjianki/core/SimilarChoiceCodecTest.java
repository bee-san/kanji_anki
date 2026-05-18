package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SimilarChoiceCodecTest {
    @Test
    public void serializeChoicesKeepsLegacyTabEncoding() {
        assertEquals("", SimilarChoiceCodec.serializeChoices(null));
        assertEquals("", SimilarChoiceCodec.serializeChoices(Collections.emptyList()));
        assertEquals("拉\t提\t謎", SimilarChoiceCodec.serializeChoices(Arrays.asList("拉", "提", "謎")));
    }

    @Test
    public void deserializeChoicesDropsEmptySegments() {
        assertTrue(SimilarChoiceCodec.deserializeChoices(null).isEmpty());
        assertTrue(SimilarChoiceCodec.deserializeChoices("").isEmpty());
        assertEquals(
                Arrays.asList("拉", "提"),
                SimilarChoiceCodec.deserializeChoices("拉\t\t提\t")
        );
    }
}
