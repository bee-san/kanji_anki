package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.SettingValuePolicy;

final class SettingValueParser {
    private SettingValueParser() {
    }

    static int parseInt(String value, int fallback) {
        return SettingValuePolicy.parseInt(value, fallback);
    }

    static long parseLong(String value, long fallback) {
        return SettingValuePolicy.parseLong(value, fallback);
    }

    static double parseDouble(String value, double fallback) {
        return SettingValuePolicy.parseDouble(value, fallback);
    }
}
