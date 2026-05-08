package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TextUtilTest {
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
    }
}
