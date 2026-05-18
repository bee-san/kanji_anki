package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KaniOutcomePolicy {
    private KaniOutcomePolicy() {
    }

    public static OutcomeStats summarize(List<OutcomeEvidence> outcomeEvidence, LadderHealthPolicy.Metric ladderHealth) {
        OutcomeAccumulator accumulator = new OutcomeAccumulator();
        for (OutcomeEvidence evidence : safeList(outcomeEvidence)) {
            if (evidence != null) {
                accumulator.add(evidence.kanji(), evidence.before(), evidence.after());
            }
        }
        accumulator.sort();
        int improvedCount = accumulator.improvements.size();
        return new OutcomeStats(
                new WeakKanjiImprovedMetric(
                        improvedCount,
                        improvedCount == 0 ? 0.0 : accumulator.beforeWeaknessSum / improvedCount,
                        improvedCount == 0 ? 0.0 : accumulator.afterWeaknessSum / improvedCount,
                        topThreeImprovements(accumulator.improvements)
                ),
                new MatureSupportGainedMetric(
                        accumulator.supportGains.size(),
                        accumulator.matureSupportGainSum,
                        accumulator.firstSupportCount,
                        topThreeSupportGains(accumulator.supportGains)
                ),
                ladderHealth
        );
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? Collections.emptyList() : value;
    }

    private static List<KanjiImprovement> topThreeImprovements(List<KanjiImprovement> improvements) {
        return new ArrayList<>(improvements.subList(0, Math.min(3, improvements.size())));
    }

    private static List<KanjiSupportGain> topThreeSupportGains(List<KanjiSupportGain> supportGains) {
        return new ArrayList<>(supportGains.subList(0, Math.min(3, supportGains.size())));
    }

    public record OutcomeStats(
            WeakKanjiImprovedMetric weakKanjiImproved,
            MatureSupportGainedMetric matureSupportGained,
            LadderHealthPolicy.Metric ladderHealth
    ) {
        public OutcomeStats {
            weakKanjiImproved = weakKanjiImproved == null ? WeakKanjiImprovedMetric.empty() : weakKanjiImproved;
            matureSupportGained = matureSupportGained == null ? MatureSupportGainedMetric.empty() : matureSupportGained;
            ladderHealth = ladderHealth == null ? LadderHealthPolicy.Metric.empty() : ladderHealth;
        }

        public static OutcomeStats empty() {
            return new OutcomeStats(
                    WeakKanjiImprovedMetric.empty(),
                    MatureSupportGainedMetric.empty(),
                    LadderHealthPolicy.Metric.empty()
            );
        }
    }

    public record WeakKanjiImprovedMetric(
            int improvedCount,
            double averageBeforeWeakness,
            double averageAfterWeakness,
            List<KanjiImprovement> examples
    ) {
        public WeakKanjiImprovedMetric {
            improvedCount = Math.max(0, improvedCount);
            averageBeforeWeakness = Math.max(0.0, averageBeforeWeakness);
            averageAfterWeakness = Math.max(0.0, averageAfterWeakness);
            examples = Collections.unmodifiableList(new ArrayList<>(examples == null ? Collections.emptyList() : examples));
        }

        public static WeakKanjiImprovedMetric empty() {
            return new WeakKanjiImprovedMetric(0, 0.0, 0.0, Collections.emptyList());
        }
    }

    public record MatureSupportGainedMetric(
            int gainedSupportCount,
            int matureSupportGained,
            int firstSupportCount,
            List<KanjiSupportGain> examples
    ) {
        public MatureSupportGainedMetric(int gainedSupportCount, int firstSupportCount, List<KanjiSupportGain> examples) {
            this(gainedSupportCount, gainedSupportCount, firstSupportCount, examples);
        }

        public MatureSupportGainedMetric {
            gainedSupportCount = Math.max(0, gainedSupportCount);
            matureSupportGained = Math.max(0, matureSupportGained);
            firstSupportCount = Math.max(0, firstSupportCount);
            examples = Collections.unmodifiableList(new ArrayList<>(examples == null ? Collections.emptyList() : examples));
        }

        public static MatureSupportGainedMetric empty() {
            return new MatureSupportGainedMetric(0, 0, 0, Collections.emptyList());
        }
    }

    public record KanjiImprovement(String kanji, double beforeWeakness, double afterWeakness) {
        public KanjiImprovement {
            kanji = kanji == null ? "" : kanji;
            beforeWeakness = Math.max(0.0, beforeWeakness);
            afterWeakness = Math.max(0.0, afterWeakness);
        }
    }

    public record KanjiSupportGain(String kanji, int beforeMatureSupport, int afterMatureSupport) {
        public KanjiSupportGain {
            kanji = kanji == null ? "" : kanji;
            beforeMatureSupport = Math.max(0, beforeMatureSupport);
            afterMatureSupport = Math.max(0, afterMatureSupport);
        }
    }

    public record OutcomeSnapshot(int weaknessScore, int matureSupportCount) {
        public OutcomeSnapshot {
            weaknessScore = Math.max(0, weaknessScore);
            matureSupportCount = Math.max(0, matureSupportCount);
        }
    }

    public record OutcomeEvidence(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
        public OutcomeEvidence {
            kanji = kanji == null ? "" : kanji;
        }
    }

    private static final class OutcomeAccumulator {
        private final List<KanjiImprovement> improvements = new ArrayList<>();
        private final List<KanjiSupportGain> supportGains = new ArrayList<>();
        private double beforeWeaknessSum;
        private double afterWeaknessSum;
        private int matureSupportGainSum;
        private int firstSupportCount;

        private void add(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            if (before == null || after == null) {
                return;
            }
            addImprovement(kanji, before, after);
            addSupportGain(kanji, before, after);
        }

        private void sort() {
            improvements.sort((left, right) -> {
                int dropCompare = Double.compare(
                        right.beforeWeakness() - right.afterWeakness(),
                        left.beforeWeakness() - left.afterWeakness()
                );
                return dropCompare == 0 ? left.kanji().compareTo(right.kanji()) : dropCompare;
            });
            supportGains.sort((left, right) -> {
                int gainCompare = Integer.compare(
                        right.afterMatureSupport() - right.beforeMatureSupport(),
                        left.afterMatureSupport() - left.beforeMatureSupport()
                );
                return gainCompare == 0 ? left.kanji().compareTo(right.kanji()) : gainCompare;
            });
        }

        private void addImprovement(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            int weaknessDrop = before.weaknessScore() - after.weaknessScore();
            if (before.weaknessScore() <= 0 || weaknessDrop < 5) {
                return;
            }
            double beforeWeakness = normalizedWeakness(before.weaknessScore());
            double afterWeakness = normalizedWeakness(after.weaknessScore());
            improvements.add(new KanjiImprovement(kanji, beforeWeakness, afterWeakness));
            beforeWeaknessSum += beforeWeakness;
            afterWeaknessSum += afterWeakness;
        }

        private void addSupportGain(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            int supportGain = after.matureSupportCount() - before.matureSupportCount();
            if (supportGain <= 0) {
                return;
            }
            supportGains.add(new KanjiSupportGain(kanji, before.matureSupportCount(), after.matureSupportCount()));
            matureSupportGainSum += supportGain;
            if (before.matureSupportCount() == 0) {
                firstSupportCount++;
            }
        }

        private static double normalizedWeakness(int weaknessScore) {
            return Math.max(0, weaknessScore) / 100.0;
        }
    }
}
