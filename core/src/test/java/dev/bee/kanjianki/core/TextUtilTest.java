package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TextUtilTest {
    @Test
    public void normalizesNullAndFullWidthWhitespace() {
        assertEquals("", TextUtil.normalizeJapanese(null));
        assertEquals("ABC 語", TextUtil.normalizeJapanese("ＡＢＣ　語"));
    }

    @Test
    public void extractsUniqueKanjiFromNormalizedExpression() {
        assertEquals(Arrays.asList("提", "示"), TextUtil.extractKanji(" 提示[ていじ] 提示 "));
    }

    @Test
    public void stripsHtmlAndRubyReadingsForMeaning() {
        String html = "<div><ruby>提<rt>てい</rt></ruby><b>presentation</b>; showing</div>";
        assertEquals("提 presentation", TextUtil.firstMeaningLine(html));
    }

    @Test
    public void stripsScriptsStylesAndHtmlEntities() {
        String html = "<style>.x{}</style><script>alert(1)</script><p>A&nbsp;&amp;&quot;&#39;&lt;&gt;</p>";

        assertEquals("", TextUtil.stripHtml(null));
        assertEquals("", TextUtil.stripHtml(""));
        assertEquals("A &\"'<>", TextUtil.stripHtml(html));
    }

    @Test
    public void stripsHtmlTagsWithoutRegexBacktracking() {
        assertEquals("提 presentation", TextUtil.stripHtml("<RUBY>提<RT data-x=\"1\">てい</RT></RUBY><h1>presentation</h1>"));
        assertEquals(".x{} visible", TextUtil.stripHtml("<style>.x{} visible"));
        assertEquals("alert(1) visible", TextUtil.stripHtml("<script>alert(1) visible"));
        assertEquals("reading", TextUtil.stripHtml("<rt>reading"));
        assertEquals("<broken visible", TextUtil.stripHtml("<broken visible"));
        assertEquals("<> visible", TextUtil.stripHtml("<> visible"));
    }

    @Test
    public void meaningLineHandlesEmptySeparatorsAndLongText() {
        String longMeaning = "meaning ".repeat(20);

        assertEquals("", TextUtil.firstMeaningLine("<rt>だけ</rt>"));
        assertEquals("first", TextUtil.firstMeaningLine("first|second"));
        assertEquals(96, TextUtil.firstMeaningLine(longMeaning).length());
        assertTrue(TextUtil.firstMeaningLine(longMeaning).endsWith("..."));
    }

    @Test
    public void identifiesKanjiAcrossSupportedUnicodeBlocks() {
        assertTrue(TextUtil.isKanji('裂'));
        assertTrue(TextUtil.isKanji(0x3400));
        assertTrue(TextUtil.isKanji(0x4DBF));
        assertTrue(TextUtil.isKanji(0x4E00));
        assertTrue(TextUtil.isKanji(0x9FFF));
        assertTrue(TextUtil.isKanji(0xF900));
        assertTrue(TextUtil.isKanji(0xFAFF));
        assertTrue(TextUtil.isKanji(0x20000));
        assertTrue(TextUtil.isKanji(0x2A6DF));
        assertTrue(TextUtil.isKanji(0x2A700));
        assertTrue(TextUtil.isKanji(0x2B73F));
        assertTrue(TextUtil.isKanji(0x2B740));
        assertTrue(TextUtil.isKanji(0x2B81F));
        assertTrue(TextUtil.isKanji(0x2B820));
        assertTrue(TextUtil.isKanji(0x2CEAF));
        assertTrue(TextUtil.isKanji(0x2CEB0));
        assertTrue(TextUtil.isKanji(0x2EBEF));
        assertFalse(TextUtil.isKanji(0x2EBF0));
        assertFalse(TextUtil.isKanji(0x30000));
        assertFalse(TextUtil.isKanji('A'));
    }

    @Test
    public void browserSearchUsesKikuExpressionField() {
        assertEquals("note:Kiku Expression:*裂*", TextUtil.browserSearchForKanji("裂", Records.Settings.kikuDefaults()));
    }

    @Test
    public void browserSearchQuotesCustomNoteTypeAndFieldNames() {
        Records.Settings custom = new Records.Settings(
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
                Records.DEFAULT_WRITING_TRIGGER_MISS_DAYS
        );

        assertEquals(
                "note:\"Custom Japanese\" \"Japanese Field\":*裂*",
                TextUtil.browserSearchForKanji("裂", custom)
        );
    }

    @Test
    public void jsonQuoteEscapesControlCharacters() {
        assertEquals("\"a\\n\\\"b\\\"\"", TextUtil.jsonQuote("a\n\"b\""));
        assertEquals("null", TextUtil.jsonQuote(null));
        assertEquals("\"\\\\\\r\\t\\u0001\"", TextUtil.jsonQuote("\\\r\t\u0001"));
    }

    @Test
    public void browserSearchHandlesNullKanjiAndEscapedSearchText() {
        assertEquals("note:Kiku Expression:**", TextUtil.browserSearchForKanji(null, Records.Settings.kikuDefaults()));
        assertEquals("note:Kiku Expression:*a\\\\\\\"b*", TextUtil.browserSearchForKanji("a\\\"b", Records.Settings.kikuDefaults()));
    }

    @Test
    public void browserSearchQuotesNullCustomNoteTypeAndField() {
        Records.Settings custom = new Records.Settings(
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
                Records.DEFAULT_WRITING_TRIGGER_MISS_DAYS
        );

        assertEquals("note:\"\" \"\":*裂*", TextUtil.browserSearchForKanji("裂", custom));
    }
}
