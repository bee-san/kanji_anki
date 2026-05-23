package dev.bee.kanjianki.core;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FrequencyRetentionRangesTest {
    @Test
    public void parsesPercentAndDecimalRetentionRules() {
        List<FrequencyRetentionRanges.Rule> rules = FrequencyRetentionRanges.parse(
                "501-2000=0.90\n1-500=95%\n2001..20000=85"
        );

        assertEquals(3, rules.size());
        assertEquals(1, rules.get(0).minRank);
        assertEquals(500, rules.get(0).maxRank);
        assertEquals(0.95, rules.get(0).retention, 0.001);
        assertEquals(0.90, FrequencyRetentionRanges.retentionForRank("1-500=95%\n501-2000=90%", 1000), 0.001);
        assertNull(FrequencyRetentionRanges.retentionForRank("1-500=95%", 900));
        assertNull(FrequencyRetentionRanges.retentionForRank("1-500=95%", null));
    }

    @Test
    public void rejectsInvalidRangesAndRetentionValues() {
        assertThrows(IllegalArgumentException.class, () -> FrequencyRetentionRanges.parse("500-1=90%"));
        assertThrows(IllegalArgumentException.class, () -> FrequencyRetentionRanges.parse("1-500=5%"));
        assertThrows(IllegalArgumentException.class, () -> FrequencyRetentionRanges.parse("1-500=100%"));
        assertThrows(IllegalArgumentException.class, () -> FrequencyRetentionRanges.parse("1-500=90%\n500-800=85%"));
        assertThrows(IllegalArgumentException.class, () -> FrequencyRetentionRanges.parse("abc=90%"));
    }

    @Test
    public void ruleConstructorStaysPrivateForJavaInterop() {
        Constructor<?>[] constructors = FrequencyRetentionRanges.Rule.class.getDeclaredConstructors();

        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
    }
}
