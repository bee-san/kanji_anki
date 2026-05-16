package dev.bee.kanjianki.core;

final class KaniFsrsReviewResult {
    final double stability;
    final double difficulty;
    final long intervalMillis;

    KaniFsrsReviewResult(double stability, double difficulty, long intervalMillis) {
        this.stability = stability;
        this.difficulty = difficulty;
        this.intervalMillis = intervalMillis;
    }

    int intervalDays() {
        long safeInterval = Math.max(1L, intervalMillis);
        long days = ((safeInterval - 1L) / BridgeScheduler.DAY) + 1L;
        return days > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) days;
    }
}
