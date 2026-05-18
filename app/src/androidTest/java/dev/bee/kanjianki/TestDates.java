package dev.bee.kanjianki;

import dev.bee.kanjianki.core.LocalDayPolicy;

public final class TestDates {
    private TestDates() {
    }

    public static long localDayStart(long millis) {
        return LocalDayPolicy.localDayStart(millis);
    }

    public static long moveLocalDays(long localDayStart, int days) {
        return LocalDayPolicy.moveLocalDays(localDayStart, days);
    }
}
