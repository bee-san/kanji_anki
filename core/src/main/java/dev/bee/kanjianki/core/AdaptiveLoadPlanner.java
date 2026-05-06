package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final int AUTO_PARETO_CAP = 20;

    public Records.AdaptiveLoadPlan plan(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> items,
            Records.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            int workloadPercent,
            long nowMillis
    ) {
        return plan(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, nowMillis, Records.Settings.kikuDefaults());
    }

    public Records.AdaptiveLoadPlan plan(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> items,
            Records.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            int workloadPercent,
            String workloadMode,
            long nowMillis,
            Records.Settings settings
    ) {
        return planInternal(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, workloadMode, nowMillis, settings);
    }

    public Records.AdaptiveLoadPlan plan(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> items,
            Records.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            int workloadPercent,
            long nowMillis,
            Records.Settings settings
    ) {
        return planInternal(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, MODE_MANUAL, nowMillis, settings);
    }

    private Records.AdaptiveLoadPlan planInternal(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> items,
            Records.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            int workloadPercent,
            String workloadMode,
            long nowMillis,
            Records.Settings settings
    ) {
        List<Records.DashboardRow> safeRows = rows == null ? Collections.emptyList() : rows;
        List<Records.StudyItem> safeItems = items == null ? Collections.emptyList() : items;
        Records.ReviewStats stats = recentStats == null ? new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0) : recentStats;
        Set<String> studied = studiedToday == null ? Collections.emptySet() : studiedToday;
        Records.Settings effectiveSettings = settings == null ? Records.Settings.kikuDefaults() : settings;
        int snapped = snapWorkloadPercent(workloadPercent);
        boolean autoMode = isAutoMode(workloadMode);

        Map<String, Records.StudyItem> itemByKanji = new HashMap<>();
        for (Records.StudyItem item : safeItems) {
            itemByKanji.put(item.kanji, item);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Records.DashboardRow row : safeRows) {
            candidates.add(new Candidate(row, itemByKanji.get(row.kanji), nowMillis, effectiveSettings));
        }
        candidates.sort(autoMode ? AUTO_CANDIDATE_ORDER : CANDIDATE_ORDER);

        if (candidates.isEmpty()) {
            return new Records.AdaptiveLoadPlan(
                    autoMode,
                    snapped,
                    0,
                    0,
                    Collections.emptyList(),
                    0,
                    !autoMode && snapped >= 100,
                    "No current problem kanji."
            );
        }

        boolean allKanji = !autoMode && snapped >= 100;
        if (allKanji) {
            List<String> focus = kanjiList(candidates);
            int remaining = remainingCount(focus, itemByKanji, studied, nowMillis);
            return new Records.AdaptiveLoadPlan(
                    false,
                    snapped,
                    focus.size(),
                    remaining,
                    focus,
                    focus.size(),
                    true,
                    "All current problem kanji are available today."
            );
        }

        int recoveryDue = recoveryDueCount(candidates);
        int ceiling;
        int adjustedTarget;
        AutoTarget autoTarget = null;
        if (autoMode) {
            autoTarget = autoParetoTarget(candidates);
            ceiling = Math.min(candidates.size(), AUTO_PARETO_CAP);
            adjustedTarget = adjustedAutoTarget(autoTarget.target, ceiling, stats, currentStreakDays, recoveryDue);
        } else {
            ceiling = targetCeiling(snapped);
            adjustedTarget = adjustedTarget(ceiling, stats, currentStreakDays, recoveryDue);
        }
        int displayTarget = Math.max(adjustedTarget, recoveryDue);
        int newAdmissionLimit = Math.max(0, adjustedTarget - recoveryDue);

        LinkedHashSet<String> focus = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            if (candidate.recoveryDue) {
                focus.add(candidate.row.kanji);
            }
        }
        for (Candidate candidate : candidates) {
            if (focus.size() >= displayTarget) {
                break;
            }
            focus.add(candidate.row.kanji);
        }

        List<String> focusKanji = new ArrayList<>(focus);
        int remaining = remainingCount(focusKanji, itemByKanji, studied, nowMillis);
        return new Records.AdaptiveLoadPlan(
                autoMode,
                snapped,
                focusKanji.size(),
                remaining,
                focusKanji,
                newAdmissionLimit,
                false,
                autoMode
                        ? autoStatusFor(adjustedTarget, autoTarget, stats, recoveryDue)
                        : statusFor(snapped, adjustedTarget, ceiling, stats, recoveryDue)
        );
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
            if (current > 0.0 && next <= current * 0.70 && drop >= absoluteDrop) {
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
        if ("learning".equals(item.state)) {
            return item.dueAtMillis <= nowMillis;
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
    }

    private static final class AutoTarget {
        private final int target;
        private final boolean dropFound;

        private AutoTarget(int target, boolean dropFound) {
            this.target = Math.max(1, target);
            this.dropFound = dropFound;
        }
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
