package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingValuePolicyTest {
    @Test
    fun parseIntUsesValueWhenValid() {
        assertEquals(42, SettingValuePolicy.parseInt("42", 7))
    }

    @Test
    fun parseIntUsesFallbackWhenInvalid() {
        assertEquals(7, SettingValuePolicy.parseInt("forty-two", 7))
    }

    @Test
    fun parseLongUsesValueWhenValid() {
        assertEquals(42000000000L, SettingValuePolicy.parseLong("42000000000", 7L))
    }

    @Test
    fun parseLongUsesFallbackWhenInvalid() {
        assertEquals(7L, SettingValuePolicy.parseLong("many", 7L))
    }

    @Test
    fun parseDoubleUsesValueWhenValid() {
        assertEquals(4.25, SettingValuePolicy.parseDouble("4.25", 7.0), 0.001)
    }

    @Test
    fun parseDoubleUsesFallbackWhenInvalid() {
        assertEquals(7.0, SettingValuePolicy.parseDouble("wide", 7.0), 0.001)
    }
}
