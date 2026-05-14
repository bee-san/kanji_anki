package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdaptiveLoadPlanner {
    public static final String SETTING_KEY = "adaptive_load_work_percent";
    public static final String MODE_SETTING_KEY = "adaptive_load_mode";
    public static final String MODE_AUTO = "auto";
    public static final String MODE_MANUAL = "manual";
    public static final int DEFAULT_WORKLOAD_PERCENT = 20;
    public static final String DEFAULT_WORKLOAD_MODE = MODE_AUTO;
    public static final int DEFAULT_MAX_ITEMS = 5;
    public static final int MIN_MAX_ITEMS = 1;
    public static final int MAX_MAX_ITEMS = 20;
    private static final int AUTO_PARETO_CAP = 20;

    public Records.AdaptiveLoadPlan plan(PlanRequest request) {
        return planInternal(PlanInputs.from(request));
    }

    public Records.AdaptiveLoadPlan plan(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> items,
            Records.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            int workloadPercent,
            long nowMillis
    ) {
        return plan(new PlanRequest.Builder(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, nowMillis).build());
    }

    public Records.AdaptiveLoadPlan plan(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> items,
            Records.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            int workloadPercent,
            Object... options
    ) {
        return plan(PlanRequest.fromLegacy(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, options));
    }

    private Records.AdaptiveLoadPlan planInternal(PlanInputs inputs) {
        List<Candidate> candidates = candidatesFor(inputs);
        if (candidates.isEmpty()) {
            return emptyPlan(inputs);
        }

        if (inputs.allKanjiMode()) {
            return allKanjiPlan(candidates, inputs);
        }

        int recoveryDue = recoveryDueCount(candidates);
        TargetPlan targetPlan = targetPlanFor(candidates, inputs, recoveryDue);
        int cappedRecoveryDue = Math.min(recoveryDue, inputs.itemCap);
        List<String> focusKanji = focusKanji(candidates, Math.min(inputs.itemCap, Math.max(targetPlan.adjustedTarget, cappedRecoveryDue)));
        int remaining = remainingCount(focusKanji, inputs.itemByKanji, inputs.studiedToday, inputs.nowMillis);
        return new Records.AdaptiveLoadPlan(
                inputs.autoMode,
                inputs.workloadPercent,
                focusKanji.size(),
                remaining,
                focusKanji,
                Math.max(0, focusKanji.size() - cappedRecoveryDue),
                false,
                statusFor(targetPlan, inputs, recoveryDue)
        );
    }

    private static List<Candidate> candidatesFor(PlanInputs inputs) {
        List<Candidate> candidates = new ArrayList<>();
        for (Records.DashboardRow row : inputs.rows) {
            candidates.add(new Candidate(row, inputs.itemByKanji.get(row.kanji), inputs.nowMillis, inputs.settings));
        }
        candidates.sort(inputs.autoMode ? AUTO_CANDIDATE_ORDER : CANDIDATE_ORDER);
        return candidates;
    }

    private static Records.AdaptiveLoadPlan emptyPlan(PlanInputs inputs) {
        return new Records.AdaptiveLoadPlan(
                inputs.autoMode,
                inputs.workloadPercent,
                0,
                0,
                Collections.emptyList(),
                0,
                inputs.allKanjiMode(),
                "No current problem kanji."
        );
    }

    private static Records.AdaptiveLoadPlan allKanjiPlan(List<Candidate> candidates, PlanInputs inputs) {
        List<String> focus = kanjiList(candidates);
        boolean allIncluded = true;
        if (inputs.itemCap != Integer.MAX_VALUE && focus.size() > inputs.itemCap) {
            focus = new ArrayList<>(focus.subList(0, inputs.itemCap));
            allIncluded = false;
        }
        int remaining = remainingCount(focus, inputs.itemByKanji, inputs.studiedToday, inputs.nowMillis);
        return new Records.AdaptiveLoadPlan(
                false,
                inputs.workloadPercent,
                focus.size(),
                remaining,
                focus,
                focus.size(),
                allIncluded,
                allIncluded
                        ? "All current problem kanji are available today."
                        : "All kanji mode is capped to today's maximum."
        );
    }

    private static TargetPlan targetPlanFor(List<Candidate> candidates, PlanInputs inputs, int recoveryDue) {
        if (inputs.autoMode) {
            AutoTarget autoTarget = autoParetoTarget(candidates);
            int ceiling = Math.min(Math.min(candidates.size(), AUTO_PARETO_CAP), inputs.itemCap);
            int adjustedTarget = adjustedAutoTarget(autoTarget.target, ceiling, inputs.stats, inputs.currentStreakDays, recoveryDue);
            return new TargetPlan(ceiling, adjustedTarget, autoTarget);
        }
        int ceiling = Math.min(targetCeiling(inputs.workloadPercent), inputs.itemCap);
        return new TargetPlan(ceiling, adjustedTarget(ceiling, inputs.stats, inputs.currentStreakDays, recoveryDue), null);
    }

    private static List<String> focusKanji(List<Candidate> candidates, int displayTarget) {
        LinkedHashSet<String> focus = new LinkedHashSet<>();
        addDueRecovery(candidates, focus, displayTarget);
        addByPriority(candidates, focus, displayTarget);
        return new ArrayList<>(focus);
    }

    private static void addDueRecovery(List<Candidate> candidates, LinkedHashSet<String> focus, int displayTarget) {
        for (Candidate candidate : candidates) {
            if (focus.size() >= displayTarget) {
                return;
            }
            if (candidate.recoveryDue) {
                focus.add(candidate.row.kanji);
            }
        }
    }

    private static void addByPriority(List<Candidate> candidates, LinkedHashSet<String> focus, int displayTarget) {
        for (Candidate candidate : candidates) {
            if (focus.size() >= displayTarget) {
                return;
            }
            focus.add(candidate.row.kanji);
        }
    }

    private static String statusFor(TargetPlan targetPlan, PlanInputs inputs, int recoveryDue) {
        if (inputs.autoMode) {
            return autoStatusFor(targetPlan.adjustedTarget, targetPlan.autoTarget, inputs.stats, recoveryDue);
        }
        return statusFor(inputs.workloadPercent, targetPlan.adjustedTarget, targetPlan.ceiling, inputs.stats, recoveryDue);
    }

    public static final class PlanRequest {
        private final List<Records.DashboardRow> rows;
        private final List<Records.StudyItem> items;
        private final Records.ReviewStats recentStats;
        private final int currentStreakDays;
        private final Set<String> studiedToday;
        private final int workloadPercent;
        private final String workloadMode;
        private final int maxItems;
        private final long nowMillis;
        private final Records.Settings settings;

        private PlanRequest(Builder builder) {
            this.rows = builder.rows;
            this.items = builder.items;
            this.recentStats = builder.recentStats;
            this.currentStreakDays = builder.currentStreakDays;
            this.studiedToday = builder.studiedToday;
            this.workloadPercent = builder.workloadPercent;
            this.workloadMode = builder.workloadMode;
            this.maxItems = builder.maxItems;
            this.nowMillis = builder.nowMillis;
            this.settings = builder.settings;
        }

        private static PlanRequest fromLegacy(
                List<Records.DashboardRow> rows,
                List<Records.StudyItem> items,
                Records.ReviewStats recentStats,
                int currentStreakDays,
                Set<String> studiedToday,
                int workloadPercent,
                Object[] options
        ) {
            Builder builder = new Builder(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, 0L);
            Object[] safeOptions = options == null ? new Object[0] : options;
            if (safeOptions.length == 2) {
                applyNowAndSettings(builder, safeOptions[0], safeOptions[1], true);
            } else if (safeOptions.length == 3) {
                builder.workloadMode((String) safeOptions[0]);
                applyNowAndSettings(builder, safeOptions[1], safeOptions[2], false);
            } else if (safeOptions.length >= 4) {
                builder.workloadMode((String) safeOptions[0]);
                builder.maxItems((Integer) safeOptions[1]);
                applyNowAndSettings(builder, safeOptions[2], safeOptions[3], false);
            } else {
                applyLooseOptions(builder, safeOptions);
            }
            return builder.build();
        }

        private static void applyNowAndSettings(Builder builder, Object nowMillis, Object settings, boolean manualMode) {
            if (manualMode) {
                builder.workloadMode(MODE_MANUAL);
            }
            if (nowMillis instanceof Long value) {
                builder.nowMillis(value);
            }
            builder.settings((Records.Settings) settings);
        }

        private static void applyLooseOptions(Builder builder, Object[] options) {
            for (Object option : options) {
                if (option instanceof Long nowMillis) {
                    builder.nowMillis(nowMillis);
                } else if (option instanceof Records.Settings settings) {
                    builder.settings(settings);
                }
            }
        }

        public static final class Builder {
            private final List<Records.DashboardRow> rows;
            private final List<Records.StudyItem> items;
            private final Records.ReviewStats recentStats;
            private final int currentStreakDays;
            private final Set<String> studiedToday;
            private final int workloadPercent;
            private String workloadMode = MODE_MANUAL;
            private int maxItems = Integer.MAX_VALUE;
            private long nowMillis;
            private Records.Settings settings = Records.Settings.kikuDefaults();

            public Builder(
                    List<Records.DashboardRow> rows,
                    List<Records.StudyItem> items,
                    Records.ReviewStats recentStats,
                    int currentStreakDays,
                    Set<String> studiedToday,
                    int workloadPercent,
                    long nowMillis
            ) {
                this.rows = rows;
                this.items = items;
                this.recentStats = recentStats;
                this.currentStreakDays = currentStreakDays;
                this.studiedToday = studiedToday;
                this.workloadPercent = workloadPercent;
                this.nowMillis = nowMillis;
            }

            public Builder workloadMode(String workloadMode) {
                this.workloadMode = workloadMode;
                return this;
            }

            public Builder maxItems(int maxItems) {
                this.maxItems = maxItems;
                return this;
            }

            public Builder nowMillis(long nowMillis) {
                this.nowMillis = nowMillis;
                return this;
            }

            public Builder settings(Records.Settings settings) {
                this.settings = settings;
                return this;
            }

            public PlanRequest build() {
                return new PlanRequest(this);
            }
        }
    }

    private static final class PlanInputs {
        private final List<Records.DashboardRow> rows;
        private final Records.ReviewStats stats;
        private final Set<String> studiedToday;
        private final int currentStreakDays;
        private final int workloadPercent;
        private final boolean autoMode;
        private final int itemCap;
        private final long nowMillis;
        private final Records.Settings settings;
        private final Map<String, Records.StudyItem> itemByKanji;

        private PlanInputs(PlanRequest request) {
            this.rows = request.rows == null ? Collections.emptyList() : request.rows;
            this.stats = request.recentStats == null ? new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0) : request.recentStats;
            this.studiedToday = request.studiedToday == null ? Collections.emptySet() : request.studiedToday;
            this.currentStreakDays = request.currentStreakDays;
            this.workloadPercent = snapWorkloadPercent(request.workloadPercent);
            this.autoMode = isAutoMode(request.workloadMode);
            this.itemCap = request.maxItems == Integer.MAX_VALUE ? Integer.MAX_VALUE : normalizeMaxItems(request.maxItems);
            this.nowMillis = request.nowMillis;
            this.settings = request.settings == null ? Records.Settings.kikuDefaults() : request.settings;
            this.itemByKanji = itemIndex(request.items);
        }

        private static PlanInputs from(PlanRequest request) {
            return new PlanInputs(request == null
                    ? new PlanRequest.Builder(null, null, null, 0, null, DEFAULT_WORKLOAD_PERCENT, 0L).build()
                    : request);
        }

        private static Map<String, Records.StudyItem> itemIndex(List<Records.StudyItem> items) {
            Map<String, Records.StudyItem> itemByKanji = new HashMap<>();
            for (Records.StudyItem item : items == null ? Collections.<Records.StudyItem>emptyList() : items) {
                itemByKanji.put(item.kanji, item);
            }
            return itemByKanji;
        }

        private boolean allKanjiMode() {
            return !autoMode && workloadPercent >= 100;
        }
    }

    private static final class TargetPlan {
        private final int ceiling;
        private final int adjustedTarget;
        private final AutoTarget autoTarget;

        private TargetPlan(int ceiling, int adjustedTarget, AutoTarget autoTarget) {
            this.ceiling = ceiling;
            this.adjustedTarget = adjustedTarget;
            this.autoTarget = autoTarget;
        }
    }

    public static String normalizeWorkloadMode(String mode) {
        return MODE_MANUAL.equals(mode) ? MODE_MANUAL : MODE_AUTO;
    }

    public static boolean isAutoMode(String mode) {
        return MODE_AUTO.equals(normalizeWorkloadMode(mode));
    }

    public static int snapWorkloadPercent(int value) {
        int clamped = Math.max(0, Math.min(100, value));
        if (clamped == 100) {
            return 100;
        }
        return Math.max(0, Math.min(95, Math.round(clamped / 5.0f) * 5));
    }

    public static int targetCeiling(int workloadPercent) {
        int snapped = snapWorkloadPercent(workloadPercent);
        if (snapped >= 100) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, Math.min(20, 1 + snapped / 5));
    }

    public static int normalizeMaxItems(int value) {
        return Math.max(MIN_MAX_ITEMS, Math.min(MAX_MAX_ITEMS, value));
    }

    public static String workloadLabel(int workloadPercent) {
        int snapped = snapWorkloadPercent(workloadPercent);
        if (snapped <= 0) {
            return "Very little";
        }
        if (snapped <= 20) {
            return "Pareto";
        }
        if (snapped <= 50) {
            return "Balanced";
        }
        if (snapped < 100) {
            return "More";
        }
        return "All kanji";
    }

    private static int adjustedTarget(int ceiling, Records.ReviewStats stats, int currentStreakDays, int recoveryDue) {
        int target = stats.total == 0
                ? Math.min(3, ceiling)
                : Math.max(1, Math.round(ceiling * 0.65f));
        return adjustedTargetFromBase(target, ceiling, stats, currentStreakDays, recoveryDue);
    }

    private static int adjustedAutoTarget(int autoTarget, int ceiling, Records.ReviewStats stats, int currentStreakDays, int recoveryDue) {
        int target = stats.total == 0 ? Math.min(3, autoTarget) : autoTarget;
        return adjustedTargetFromBase(target, ceiling, stats, currentStreakDays, recoveryDue);
    }

    private static int adjustedTargetFromBase(int target, int ceiling, Records.ReviewStats stats, int currentStreakDays, int recoveryDue) {
        double missRate = stats.total == 0 ? 0.0 : stats.again / (double) stats.total;
        double hardRate = stats.total == 0 ? 0.0 : stats.hard / (double) stats.total;
        double writingFailureRate = stats.writingFailureRate();
        if (missRate >= 0.50) {
            target -= 2;
        } else if (missRate >= 0.25) {
            target -= 1;
        }
        if (hardRate >= 0.45) {
            target -= 1;
        }
        if (writingFailureRate >= 0.30) {
            target -= 1;
        }
        if (recoveryDue >= target) {
            target = Math.max(1, target - 1);
        }
        if (stats.total >= 3
                && currentStreakDays >= 3
                && missRate <= 0.10
                && hardRate <= 0.25
                && writingFailureRate <= 0.10) {
            target += 1;
        }
        return Math.max(1, Math.min(ceiling, target));
    }

    private static AutoTarget autoParetoTarget(List<Candidate> candidates) {
        int recoveryDue = recoveryDueCount(candidates);
        List<Candidate> ranked = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!candidate.recoveryDue) {
                ranked.add(candidate);
            }
        }
        if (ranked.isEmpty()) {
            return new AutoTarget(Math.max(1, recoveryDue), false);
        }

        int fallback = Math.min(ranked.size(), targetCeiling(DEFAULT_WORKLOAD_PERCENT));
        double top = ranked.get(0).priorityScore;
        if (top <= 0.0) {
            return new AutoTarget(Math.min(AUTO_PARETO_CAP, recoveryDue + fallback), false);
        }
        double absoluteDrop = Math.max(4.0, top * 0.15);
        int scanLimit = Math.min(ranked.size() - 1, Math.max(0, AUTO_PARETO_CAP - recoveryDue - 1));
        for (int i = 0; i < scanLimit; i++) {
            double current = ranked.get(i).priorityScore;
            double next = ranked.get(i + 1).priorityScore;
            double drop = current - next;
            if (next <= current * 0.70 && drop >= absoluteDrop) {
                return new AutoTarget(Math.max(1, Math.min(AUTO_PARETO_CAP, recoveryDue + i + 1)), true);
            }
        }
        return new AutoTarget(Math.max(1, Math.min(AUTO_PARETO_CAP, recoveryDue + fallback)), false);
    }

    private static int recoveryDueCount(List<Candidate> candidates) {
        int count = 0;
        for (Candidate candidate : candidates) {
            if (candidate.recoveryDue) {
                count++;
            }
        }
        return count;
    }

    private static int remainingCount(
            List<String> focusKanji,
            Map<String, Records.StudyItem> itemByKanji,
            Set<String> studiedToday,
            long nowMillis
    ) {
        int remaining = 0;
        for (String kanji : focusKanji) {
            Records.StudyItem item = itemByKanji.get(kanji);
            if (!studiedToday.contains(kanji) || recoveryDue(item, nowMillis)) {
                remaining++;
            }
        }
        return remaining;
    }

    private static List<String> kanjiList(List<Candidate> candidates) {
        List<String> out = new ArrayList<>();
        for (Candidate candidate : candidates) {
            out.add(candidate.row.kanji);
        }
        return out;
    }

    private static String statusFor(int workloadPercent, int target, int ceiling, Records.ReviewStats stats, int recoveryDue) {
        if (workloadPercent <= 0) {
            return "Very little work today: one focused kanji unless recovery is already due.";
        }
        if (stats.total == 0) {
            return "Pareto focus starts small until Kani has review history.";
        }
        if (recoveryDue >= target) {
            return "Due recovery fills today's focus, so new kanji wait.";
        }
        if (target >= ceiling) {
            return "Recent reviews are steady, so Kani can use the full focus range.";
        }
        return "Adaptive focus is set from recent misses, hard ratings, and writing results.";
    }

    private static String autoStatusFor(int target, AutoTarget autoTarget, Records.ReviewStats stats, int recoveryDue) {
        if (recoveryDue >= target) {
            return "Due recovery fills today's auto Pareto focus, so new kanji wait.";
        }
        if (stats.total == 0) {
            return autoTarget.dropFound
                    ? "Auto Pareto found today's drop-off, then starts small until Kani has review history."
                    : "Auto Pareto starts small until Kani has review history.";
        }
        if (!autoTarget.dropFound) {
            return "Auto Pareto did not find a sharp drop-off, so Kani uses the small Pareto focus.";
        }
        if (target < autoTarget.target) {
            return "Auto Pareto found today's drop-off, then recent review strain lowered the focus.";
        }
        if (target > autoTarget.target) {
            return "Auto Pareto found today's drop-off and your steady streak allows one extra kanji.";
        }
        return "Auto Pareto uses today's problem-kanji drop-off.";
    }

    private static boolean recoveryDue(Records.StudyItem item, long nowMillis) {
        if (item == null || "retired".equals(item.state)) {
            return false;
        }
        // A card in learning/relearning state is still actively being worked on
        // even if its next step hasn't arrived yet. Without this, a failed card
        // whose relearning step is a minute in the future causes remainingCount
        // to hit zero and the session ends prematurely (focusComplete fires).
        if ("learning".equals(item.state)) {
            return true;
        }
        return item.totalReviews > 0 && item.dueAtMillis <= nowMillis;
    }

    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparingInt((Candidate candidate) -> candidate.recoveryDue ? 0 : 1)
            .thenComparingDouble((Candidate candidate) -> -candidate.fsrsRisk)
            .thenComparingInt((Candidate candidate) -> -candidate.suspendedCount)
            .thenComparingInt((Candidate candidate) -> -candidate.lapseScore)
            .thenComparingInt((Candidate candidate) -> -candidate.supportDeficit)
            .thenComparingInt((Candidate candidate) -> -candidate.row.weaknessScore)
            .thenComparing(candidate -> candidate.row.kanji);

    private static final Comparator<Candidate> AUTO_CANDIDATE_ORDER = Comparator
            .comparingInt((Candidate candidate) -> candidate.recoveryDue ? 0 : 1)
            .thenComparingDouble((Candidate candidate) -> -candidate.priorityScore)
            .thenComparing(CANDIDATE_ORDER);

    private static final class Candidate {
        private final Records.DashboardRow row;
        private final boolean recoveryDue;
        private final double fsrsRisk;
        private final int suspendedCount;
        private final int lapseScore;
        private final int supportDeficit;
        private final double priorityScore;

        private Candidate(Records.DashboardRow row, Records.StudyItem item, long nowMillis, Records.Settings settings) {
            this.row = row;
            this.recoveryDue = recoveryDue(item, nowMillis);
            this.fsrsRisk = fsrsRisk(row, settings);
            this.suspendedCount = row.suspendedExampleCount;
            this.lapseScore = lapseScore(row, item);
            this.supportDeficit = Math.max(0, settings.matureSupportThreshold - row.matureSupportCount);
            this.priorityScore = row.weaknessScore
                    + fsrsRisk
                    + suspendedCount * 8.0
                    + lapseScore * 2.0
                    + supportDeficit * 4.0;
        }

        private static int lapseScore(Records.DashboardRow row, Records.StudyItem item) {
            int score = item == null ? 0 : item.lapses * 3 + Math.max(0, 3 - item.writingLevel);
            for (Records.Example example : row.examples) {
                score += example.lapses;
            }
            return score;
        }

        private static double fsrsRisk(Records.DashboardRow row, Records.Settings settings) {
            double best = 0.0;
            for (Records.Example example : row.examples) {
                double risk = 0.0;
                Double retrievability = normalizedRetrievability(example.fsrsRetrievability);
                if (retrievability != null) {
                    risk += Math.max(0.0, 0.90 - retrievability) * 120.0;
                }
                if (example.fsrsDifficulty != null) {
                    risk += Math.max(0.0, example.fsrsDifficulty - 5.0) * 5.0;
                }
                if (example.fsrsStability != null) {
                    if (example.reps >= 5 && example.fsrsStability < settings.matureDays) {
                        risk += (settings.matureDays - example.fsrsStability) * 1.4;
                    } else if (example.mature && example.fsrsStability >= settings.matureDays * 2.0) {
                        risk -= 8.0;
                    }
                } else if (example.reps >= 8 && example.intervalDays < settings.matureDays) {
                    risk += Math.min(16.0, (settings.matureDays - example.intervalDays) * 0.6);
                }
                best = Math.max(best, risk);
            }
            return best;
        }

        private static Double normalizedRetrievability(Double value) {
            if (value == null) {
                return null;
            }
            if (value < 0.0) {
                return null;
            }
            if (value > 1.0 && value <= 100.0) {
                return value / 100.0;
            }
            if (value > 1.0) {
                return null;
            }
            return value;
        }
    }

    private static final class AutoTarget {
        private final int target;
        private final boolean dropFound;

        private AutoTarget(int target, boolean dropFound) {
            this.target = Math.max(1, target);
            this.dropFound = dropFound;
        }
    }
}
