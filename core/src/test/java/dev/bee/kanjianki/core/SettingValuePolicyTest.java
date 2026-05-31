package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingValuePolicyTest {
    @Test
    public void parseIntUsesValueWhenValid() {
        assertEquals(42, SettingValuePolicy.parseInt("42", 7));
    }

    @Test
    public void parseIntUsesFallbackWhenInvalid() {
        assertEquals(7, SettingValuePolicy.parseInt("forty-two", 7));
    }

    @Test
    public void parseLongUsesValueWhenValid() {
        assertEquals(42000000000L, SettingValuePolicy.parseLong("42000000000", 7L));
    }

    @Test
    public void parseLongUsesFallbackWhenInvalid() {
        assertEquals(7L, SettingValuePolicy.parseLong("many", 7L));
    }

    @Test
    public void parseDoubleUsesValueWhenValid() {
        assertEquals(4.25, SettingValuePolicy.parseDouble("4.25", 7.0), 0.001);
    }

    @Test
    public void parseDoubleUsesFallbackWhenInvalid() {
        assertEquals(7.0, SettingValuePolicy.parseDouble("wide", 7.0), 0.001);
    }
}
