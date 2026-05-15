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
        return Math.max(1, (int) Math.round((double) intervalMillis / BridgeScheduler.DAY));
    }
}
