package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarChoiceCodecTest {
    @Test
    fun serializeChoicesKeepsLegacyTabEncoding() {
        assertEquals("", SimilarChoiceCodec.serializeChoices(null))
        assertEquals("", SimilarChoiceCodec.serializeChoices(emptyList()))
        assertEquals("拉\t提\t謎", SimilarChoiceCodec.serializeChoices(listOf("拉", "提", "謎")))
    }

    @Test
    fun deserializeChoicesDropsEmptySegments() {
        assertTrue(SimilarChoiceCodec.deserializeChoices(null).isEmpty())
        assertTrue(SimilarChoiceCodec.deserializeChoices("").isEmpty())
        assertEquals(listOf("拉", "提"), SimilarChoiceCodec.deserializeChoices("拉\t\t提\t"))
    }
}
