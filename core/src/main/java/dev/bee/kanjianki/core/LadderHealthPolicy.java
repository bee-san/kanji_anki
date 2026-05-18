package dev.bee.kanjianki.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LadderHealthPolicy {
    private LadderHealthPolicy() {
    }

    public static Metric summarize(
            List<ItemEvidence> items,
            int ladderPromotionIntervalDays,
            int ladderDemotionFailStreak
    ) {
        int promotionDays = Math.max(1, ladderPromotionIntervalDays);
        int failStreak = Math.max(1, ladderDemotionFailStreak);
        Accumulator accumulator = new Accumulator();
        for (ItemEvidence item : safeList(items)) {
            accumulator.addItem(item, promotionDays, failStreak);
        }
        return accumulator.metric(promotionDays, failStreak);
    }

    public static Metric fromCounts(
            Map<RecordsBase.LadderRung, Integer> rungCounts,
            int totalActiveItems,
            int ladderPromotionIntervalDays,
            int ladderDemotionFailStreak,
            int promotionReadyCount,
            int demotionRiskCount,
            int demotionReadyCount
    ) {
        return new Metric(
                rungCounts,
                totalActiveItems,
                ladderPromotionIntervalDays,
                ladderDemotionFailStreak,
                promotionReadyCount,
                demotionRiskCount,
                demotionReadyCount
        );
    }

    public static Map<RecordsBase.LadderRung, Integer> emptyRungDistribution() {
        Map<RecordsBase.LadderRung, Integer> out = new LinkedHashMap<>();
        for (RecordsBase.LadderRung rung : RecordsBase.LadderRung.values()) {
            out.put(rung, 0);
        }
        return out;
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? Collections.emptyList() : value;
    }

    public record ItemEvidence(
            String state,
            RecordsBase.LadderRung rung,
            RecordsBase.SchedulerPhase phase,
            int realPassStreak,
            int realAgainStreak,
            int matureIntervalDays
    ) {
        public ItemEvidence(
                String state,
                RecordsBase.LadderRung rung,
                RecordsBase.SchedulerPhase phase,
                int realPassStreak,
                int realAgainStreak
        ) {
            this(state, rung, phase, realPassStreak, realAgainStreak, 0);
        }

        public ItemEvidence {
            state = state == null ? "" : state;
            rung = rung == null ? RecordsBase.LadderRung.KANJI_MEANING : rung;
            phase = phase == null ? RecordsBase.SchedulerPhase.NEW_LEARNING : phase;
            realPassStreak = Math.max(0, realPassStreak);
            realAgainStreak = Math.max(0, realAgainStreak);
            matureIntervalDays = Math.max(0, matureIntervalDays);
        }
    }

    public record Metric(
            Map<RecordsBase.LadderRung, Integer> rungCounts,
            int totalActiveItems,
            int ladderPromotionIntervalDays,
            int ladderDemotionFailStreak,
            int promotionReadyCount,
            int demotionRiskCount,
            int demotionReadyCount
    ) {
        public Metric {
            Map<RecordsBase.LadderRung, Integer> normalized = emptyRungDistribution();
            if (rungCounts != null) {
                for (Map.Entry<RecordsBase.LadderRung, Integer> entry : rungCounts.entrySet()) {
                    if (entry.getKey() != null) {
                        normalized.put(entry.getKey(), Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
                    }
                }
            }
            rungCounts = Collections.unmodifiableMap(normalized);
            totalActiveItems = Math.max(0, totalActiveItems);
            ladderPromotionIntervalDays = Math.max(1, ladderPromotionIntervalDays);
            ladderDemotionFailStreak = Math.max(1, ladderDemotionFailStreak);
            promotionReadyCount = Math.max(0, promotionReadyCount);
            demotionRiskCount = Math.max(0, demotionRiskCount);
            demotionReadyCount = Math.max(0, demotionReadyCount);
        }

        public int countFor(RecordsBase.LadderRung rung) {
            Integer count = rungCounts.get(rung);
            return count == null ? 0 : count;
        }

        public static Metric empty() {
            RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
            return new Metric(
                    emptyRungDistribution(),
                    0,
                    defaults.ladderPromotionIntervalDays,
                    defaults.ladderDemotionFailStreak,
                    0,
                    0,
                    0
            );
        }
    }

    private static final class Accumulator {
        private final Map<RecordsBase.LadderRung, Integer> distribution = emptyRungDistribution();
        private int total;
        private int promotionReady;
        private int demotionRisk;
        private int demotionReady;

        private void addItem(ItemEvidence item, int promotionDays, int failStreak) {
            if (item == null || StudyLadderRules.STATE_RETIRED.equals(item.state())) {
                return;
            }
            distribution.put(item.rung(), distribution.get(item.rung()) + 1);
            total++;
            if (item.phase() == RecordsBase.SchedulerPhase.REVIEW) {
                recordReviewEvidence(item, promotionDays, failStreak);
            }
        }

        private void recordReviewEvidence(ItemEvidence item, int promotionDays, int failStreak) {
            if (item.matureIntervalDays() > promotionDays) {
                promotionReady++;
            }
            if (item.realAgainStreak() > 0) {
                demotionRisk++;
            }
            if (item.realAgainStreak() >= failStreak) {
                demotionReady++;
            }
        }

        private Metric metric(int promotionDays, int failStreak) {
            return new Metric(distribution, total, promotionDays, failStreak, promotionReady, demotionRisk, demotionReady);
        }
    }
}
