package dev.bee.kanjianki.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingValueParserTest {
    @Test
    public void parseIntUsesValueWhenValid() {
        assertEquals(42, SettingValueParser.parseInt("42", 7));
    }

    @Test
    public void parseIntUsesFallbackWhenInvalid() {
        assertEquals(7, SettingValueParser.parseInt("forty-two", 7));
    }

    @Test
    public void parseLongUsesValueWhenValid() {
        assertEquals(42000000000L, SettingValueParser.parseLong("42000000000", 7L));
    }

    @Test
    public void parseLongUsesFallbackWhenInvalid() {
        assertEquals(7L, SettingValueParser.parseLong("many", 7L));
    }

    @Test
    public void parseDoubleUsesValueWhenValid() {
        assertEquals(4.25, SettingValueParser.parseDouble("4.25", 7.0), 0.001);
    }

    @Test
    public void parseDoubleUsesFallbackWhenInvalid() {
        assertEquals(7.0, SettingValueParser.parseDouble("wide", 7.0), 0.001);
    }
}
