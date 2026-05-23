package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DictionaryCoreTextParityTest {
    @Test
    public void dictionaryMeaningCleanerKeepsTextUtilFirstMeaningLineCompatible() {
        assertEquals(
                "Hidden visible & quoted",
                StudyCueFormatter.cleanMeaningText("<ruby>Hidden<rt>reading</rt></ruby><script>bad</script> visible &amp; quoted")
        );
        assertEquals(
                "pull",
                TextUtil.firstMeaningLine("Meaning: Jitendex (noun) pull; second meaning")
        );
    }

    @Test
    public void dictionaryRankKanjiRangeMatchesTextUtilKanjiRange() {
        assertTrue(TextUtil.isKanji("日".codePointAt(0)));
        assertEquals(0, JitenKanjiRanks.empty().size());
        assertFalse(TextUtil.isKanji("A".codePointAt(0)));
    }
}
