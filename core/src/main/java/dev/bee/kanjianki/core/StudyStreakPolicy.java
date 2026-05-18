package dev.bee.kanjianki.core;

import java.util.Collections;
import java.util.List;

public final class StudyStreakPolicy {
    private StudyStreakPolicy() {
    }

    public static Streak summarize(List<Long> daysDescending, long today, int reviewsToday, long lastStudyAtMillis) {
        List<Long> days = daysDescending == null ? Collections.emptyList() : daysDescending;
        if (days.isEmpty()) {
            return new Streak(0, 0, false, 0, 0L);
        }
        boolean studiedToday = days.get(0) == today;
        return new Streak(
                currentStreak(days, today),
                bestStreak(days),
                studiedToday,
                reviewsToday,
                lastStudyAtMillis
        );
    }

    private static int currentStreak(List<Long> days, long today) {
        long yesterday = LocalDayPolicy.moveLocalDays(today, -1);
        boolean studiedToday = days.get(0) == today;
        if (!studiedToday && days.get(0) != yesterday) {
            return 0;
        }
        long expected = studiedToday ? today : yesterday;
        int current = 0;
        for (long day : days) {
            if (day != expected) {
                break;
            }
            current++;
            expected = LocalDayPolicy.moveLocalDays(expected, -1);
        }
        return current;
    }

    private static int bestStreak(List<Long> days) {
        int best = 0;
        int run = 0;
        long expectedPrevious = Long.MIN_VALUE;
        for (int i = days.size() - 1; i >= 0; i--) {
            long day = days.get(i);
            if (run == 0 || day == LocalDayPolicy.moveLocalDays(expectedPrevious, 1)) {
                run++;
            } else {
                run = 1;
            }
            best = Math.max(best, run);
            expectedPrevious = day;
        }
        return best;
    }

    public record Streak(
            int currentDays,
            int bestDays,
            boolean studiedToday,
            int reviewsToday,
            long lastStudyAtMillis
    ) {
    }
}
