package dev.bee.kanjianki;

import dev.bee.kanjianki.core.DateTextPolicy;

final class UiDateText {
    private UiDateText() {
    }

    static String humanSyncTime(long timestampMillis) {
        return DateTextPolicy.humanSyncTime(timestampMillis);
    }

    static String dueText(long dueAt, long now) {
        return DateTextPolicy.dueText(dueAt, now);
    }

    static String timelineDate(long occurredAt) {
        return DateTextPolicy.timelineDate(occurredAt);
    }

    static boolean sameLocalDay(long leftMillis, long rightMillis) {
        return DateTextPolicy.sameLocalDay(leftMillis, rightMillis);
    }

    static long nextLocalDayStart(long nowMillis) {
        return DateTextPolicy.nextLocalDayStart(nowMillis);
    }

    static String shortDateTime(long millis) {
        return DateTextPolicy.shortDateTime(millis);
    }

    static String autoUpdateLastCheckText(long lastCheckAtMillis) {
        return DateTextPolicy.autoUpdateLastCheckText(lastCheckAtMillis);
    }
}
