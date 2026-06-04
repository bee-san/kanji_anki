package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilTest {
    @Test
    fun normalizesNullAndFullWidthWhitespace() {
        assertEquals("", TextUtil.normalizeJapanese(null))
        assertEquals("ABC 語", TextUtil.normalizeJapanese("ＡＢＣ　語"))
    }

    @Test
    fun extractsUniqueKanjiFromNormalizedExpression() {
        assertEquals(listOf("提", "示"), TextUtil.extractKanji(" 提示[ていじ] 提示 "))
    }

    @Test
    fun normalizesOnlySingleKanjiGlyphs() {
        assertEquals("裂", TextUtil.normalizeSingleKanji("　裂　"))
        assertEquals("", TextUtil.normalizeSingleKanji(null))
        assertEquals("", TextUtil.normalizeSingleKanji("裂提"))
        assertEquals("", TextUtil.normalizeSingleKanji("あ"))
        assertEquals("", TextUtil.normalizeSingleKanji("A"))
    }

    @Test
    fun stripsHtmlAndRubyReadingsForMeaning() {
        val html = "<div><ruby>提<rt>てい</rt></ruby><b>presentation</b>; showing</div>"
        assertEquals("提 presentation", TextUtil.firstMeaningLine(html))
    }

    @Test
    fun stripsScriptsStylesAndHtmlEntities() {
        val html = "<style>.x{}</style><script>alert(1)</script><p>A&nbsp;&amp;&quot;&#39;&lt;&gt;</p>"

        assertEquals("", TextUtil.stripHtml(null))
        assertEquals("", TextUtil.stripHtml(""))
        assertEquals("A &\"'<>", TextUtil.stripHtml(html))
    }

    @Test
    fun stripsHtmlTagsWithoutRegexBacktracking() {
        assertEquals("提 presentation", TextUtil.stripHtml("<RUBY>提<RT data-x=\"1\">てい</RT></RUBY><h1>presentation</h1>"))
        assertEquals(".x{} visible", TextUtil.stripHtml("<style>.x{} visible"))
        assertEquals("alert(1) visible", TextUtil.stripHtml("<script>alert(1) visible"))
        assertEquals("reading", TextUtil.stripHtml("<rt>reading"))
        assertEquals("<broken visible", TextUtil.stripHtml("<broken visible"))
        assertEquals("<> visible", TextUtil.stripHtml("<> visible"))
    }

    @Test
    fun meaningLineHandlesEmptySeparatorsAndLongText() {
        val longMeaning = "meaning ".repeat(20)

        assertEquals("", TextUtil.firstMeaningLine("<rt>だけ</rt>"))
        assertEquals("first", TextUtil.firstMeaningLine("first|second"))
        assertEquals("to pull", TextUtil.firstMeaningLine("[2026-05-12] JMdict [v1] (priority news) 1. godan transitive to pull|ignored"))
        assertEquals("quiet", TextUtil.firstMeaningLine("(form note) (suru verb) Jitendex.org na-adjective quiet"))
        assertEquals("movement", TextUtil.firstMeaningLine("Meaning: Jitendex (noun) movement"))
        assertEquals(96, TextUtil.firstMeaningLine(longMeaning).length)
        assertTrue(TextUtil.firstMeaningLine(longMeaning).endsWith("..."))
    }

    @Test
    fun identifiesKanjiAcrossSupportedUnicodeBlocks() {
        assertTrue(TextUtil.isKanji('裂'.code))
        assertTrue(TextUtil.isKanji(0x3400))
        assertTrue(TextUtil.isKanji(0x4DBF))
        assertTrue(TextUtil.isKanji(0x4E00))
        assertTrue(TextUtil.isKanji(0x9FFF))
        assertTrue(TextUtil.isKanji(0xF900))
        assertTrue(TextUtil.isKanji(0xFAFF))
        assertTrue(TextUtil.isKanji(0x20000))
        assertTrue(TextUtil.isKanji(0x2A6DF))
        assertTrue(TextUtil.isKanji(0x2A700))
        assertTrue(TextUtil.isKanji(0x2B73F))
        assertTrue(TextUtil.isKanji(0x2B740))
        assertTrue(TextUtil.isKanji(0x2B81F))
        assertTrue(TextUtil.isKanji(0x2B820))
        assertTrue(TextUtil.isKanji(0x2CEAF))
        assertTrue(TextUtil.isKanji(0x2CEB0))
        assertTrue(TextUtil.isKanji(0x2EBEF))
        assertFalse(TextUtil.isKanji(0x2EBF0))
        assertFalse(TextUtil.isKanji(0x30000))
        assertFalse(TextUtil.isKanji('A'.code))
    }

    @Test
    fun browserSearchUsesKikuExpressionField() {
        assertEquals("note:Kiku Expression:*裂*", TextUtil.browserSearchForKanji("裂", RecordsSyncModels.Settings.kikuDefaults()))
    }

    @Test
    fun browserSearchQuotesCustomNoteTypeAndFieldNames() {
        val custom = RecordsSyncModels.Settings(
            "Custom Japanese",
            "Mining",
            "Japanese Field",
            "",
            "Back",
            "",
            "",
            "",
            21,
            2,
            100,
            3000,
            24,
            3,
            RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS
        )

        assertEquals(
            "note:\"Custom Japanese\" \"Japanese Field\":*裂*",
            TextUtil.browserSearchForKanji("裂", custom)
        )
    }

    @Test
    fun jsonQuoteEscapesControlCharacters() {
        assertEquals("\"a\\n\\\"b\\\"\"", TextUtil.jsonQuote("a\n\"b\""))
        assertEquals("null", TextUtil.jsonQuote(null))
        assertEquals("\"\\\\\\r\\t\\u0001\"", TextUtil.jsonQuote("\\\r\t\u0001"))
    }

    @Test
    fun browserSearchHandlesNullKanjiAndEscapedSearchText() {
        assertEquals("note:Kiku Expression:**", TextUtil.browserSearchForKanji(null, RecordsSyncModels.Settings.kikuDefaults()))
        assertEquals("note:Kiku Expression:*a\\\\\\\"b*", TextUtil.browserSearchForKanji("a\\\"b", RecordsSyncModels.Settings.kikuDefaults()))
    }

    @Test
    fun browserSearchQuotesNullCustomNoteTypeAndField() {
        val custom = RecordsSyncModels.Settings(
            null,
            "Mining",
            null,
            "",
            "Back",
            "",
            "",
            "",
            21,
            2,
            100,
            3000,
            24,
            3,
            RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS
        )

        assertEquals("note:\"\" \"\":*裂*", TextUtil.browserSearchForKanji("裂", custom))
    }
}
