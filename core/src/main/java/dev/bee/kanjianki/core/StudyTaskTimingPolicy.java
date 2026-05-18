package dev.bee.kanjianki.core;

public final class StudyTaskTimingPolicy {
    private StudyTaskTimingPolicy() {
    }

    public static Window windowFor(long nowMillis) {
        long today = LocalDayPolicy.localDayStart(nowMillis);
        return new Window(today, LocalDayPolicy.moveLocalDays(today, -6), LocalDayPolicy.moveLocalDays(today, 1));
    }

    public static Summary summarize(long todayMillis, long lastSevenDaysMillis, int answeredTasks) {
        return new Summary(todayMillis, lastSevenDaysMillis, answeredTasks);
    }

    public static long boundedElapsed(long activeElapsedMillis, long maxElapsedMillis) {
        return Math.min(Math.max(0L, maxElapsedMillis), Math.max(0L, activeElapsedMillis));
    }

    public static long elapsedAfterPause(long activeElapsedMillis, long visibleSinceElapsedMillis, long nowElapsedMillis) {
        if (visibleSinceElapsedMillis <= 0L) {
            return Math.max(0L, activeElapsedMillis);
        }
        return Math.max(0L, activeElapsedMillis) + Math.max(0L, nowElapsedMillis - visibleSinceElapsedMillis);
    }

    public static long visibleSinceAfterResume(long visibleSinceElapsedMillis, long nowElapsedMillis) {
        return visibleSinceElapsedMillis <= 0L ? nowElapsedMillis : visibleSinceElapsedMillis;
    }

    public record Window(long todayStartMillis, long sevenDayStartMillis, long tomorrowStartMillis) {
    }

    public record Summary(long todayMillis, long lastSevenDaysMillis, int answeredTasks) {
        public Summary {
            todayMillis = Math.max(0L, todayMillis);
            lastSevenDaysMillis = Math.max(0L, lastSevenDaysMillis);
            answeredTasks = Math.max(0, answeredTasks);
        }

        public long averageMillisPerTask() {
            if (answeredTasks == 0) {
                return 0L;
            }
            return lastSevenDaysMillis / answeredTasks;
        }
    }
}
