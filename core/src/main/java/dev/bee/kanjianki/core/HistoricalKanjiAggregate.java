package dev.bee.kanjianki.core;

public final class HistoricalKanjiAggregate {
    private final String kanji;
    private int activeCards;
    private int suspendedCards;
    private int matureSupportCount;
    private int totalLapses;
    private int totalReps;
    private int intervalCount;
    private double intervalSum;
    private int stabilityCount;
    private double stabilitySum;
    private int difficultyCount;
    private double difficultySum;
    private int retrievabilityCount;
    private double retrievabilitySum;
    private int weaknessScore;
    private String reasonCode = "";
    private int activeExampleCount;
    private int suspendedExampleCount;

    public HistoricalKanjiAggregate(String kanji) {
        this.kanji = kanji == null ? "" : kanji;
    }

    public void add(RecordsSyncModels.Card card, int matureDays) {
        addCard(
                card.intervalDays,
                card.reps,
                card.lapses,
                card.suspended,
                card.mature(matureDays),
                card.fsrsStability,
                card.fsrsDifficulty,
                card.fsrsRetrievability
        );
    }

    public void addCard(
            int intervalDays,
            int reps,
            int lapses,
            boolean suspended,
            boolean mature,
            Double fsrsStability,
            Double fsrsDifficulty,
            Double fsrsRetrievability
    ) {
        if (suspended) {
            suspendedCards++;
        } else {
            activeCards++;
        }
        if (mature) {
            matureSupportCount++;
        }
        totalLapses += Math.max(0, lapses);
        totalReps += Math.max(0, reps);
        intervalSum += Math.max(0, intervalDays);
        intervalCount++;
        if (fsrsStability != null) {
            stabilitySum += fsrsStability;
            stabilityCount++;
        }
        if (fsrsDifficulty != null) {
            difficultySum += fsrsDifficulty;
            difficultyCount++;
        }
        if (fsrsRetrievability != null) {
            retrievabilitySum += fsrsRetrievability;
            retrievabilityCount++;
        }
    }

    public void mergeDashboardEvidence(
            int rowWeaknessScore,
            String rowReasonCode,
            int rowActiveExampleCount,
            int rowSuspendedExampleCount,
            int rowMatureSupportCount
    ) {
        weaknessScore = rowWeaknessScore;
        reasonCode = rowReasonCode;
        activeExampleCount = Math.max(activeExampleCount, rowActiveExampleCount);
        suspendedExampleCount = Math.max(suspendedExampleCount, rowSuspendedExampleCount);
        matureSupportCount = Math.max(matureSupportCount, rowMatureSupportCount);
    }

    public String kanji() {
        return kanji;
    }

    public int activeCards() {
        return activeCards;
    }

    public int suspendedCards() {
        return suspendedCards;
    }

    public int matureSupportCount() {
        return matureSupportCount;
    }

    public int totalLapses() {
        return totalLapses;
    }

    public int totalReps() {
        return totalReps;
    }

    public double averageIntervalDays() {
        return intervalCount == 0 ? 0.0 : intervalSum / intervalCount;
    }

    public Double averageStability() {
        return stabilityCount == 0 ? null : stabilitySum / stabilityCount;
    }

    public Double averageDifficulty() {
        return difficultyCount == 0 ? null : difficultySum / difficultyCount;
    }

    public Double averageRetrievability() {
        return retrievabilityCount == 0 ? null : retrievabilitySum / retrievabilityCount;
    }

    public int weaknessScore() {
        return weaknessScore;
    }

    public String reasonCode() {
        return reasonCode;
    }

    public int activeExampleCount() {
        return activeExampleCount;
    }

    public int suspendedExampleCount() {
        return suspendedExampleCount;
    }
}
