package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AnkiFieldTextNormalizerTest {
    @Test
    fun removesSoundHtmlAndRubyReadingWhilePreservingVisibleKanji() {
        val normalized = AnkiFieldTextNormalizer.normalize(
            "<ruby>確認<rt>かくにん</rt></ruby>&nbsp;&amp;補助" +
                "[sound:日本語.mp3]<script>秘密</script>",
        )

        assertEquals("確認 &補助", normalized)
        assertEquals(listOf("確", "認", "補", "助"), TextUtil.extractKanji(normalized))
    }

    @Test
    fun handlesCaseInsensitiveAndMalformedSoundMarkers() {
        assertEquals("前 後", AnkiFieldTextNormalizer.normalize("前[SOUND:a.mp3]後"))
        assertEquals("前[sound:broken 後", AnkiFieldTextNormalizer.normalize("前[sound:broken 後"))
        assertEquals("", AnkiFieldTextNormalizer.normalize(null))
    }
}
