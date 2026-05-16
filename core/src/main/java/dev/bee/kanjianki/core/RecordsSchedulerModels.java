package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public abstract class RecordsSchedulerModels extends RecordsStudyModels {
    public static final class StudySession {
        public final StudyItem item;
        public final DashboardRow row;
        public final String token;
        public final String taskType;
        public final boolean writingRequired;
        public final String prompt;

        public StudySession(StudyItem item, DashboardRow row, String token, String taskType, boolean writingRequired, String prompt) {
            this.item = item;
            this.row = row;
            this.token = token;
            this.taskType = taskType;
            this.writingRequired = writingRequired;
            this.prompt = prompt;
        }
    }

    public static final class LearningStepSettings {
        protected static final Pattern STEP_SEPARATOR = Pattern.compile("[,\\s]+");
        public final List<Integer> newStepsMinutes;
        public final List<Integer> reviewStepsMinutes;

        public LearningStepSettings(List<Integer> newStepsMinutes, List<Integer> reviewStepsMinutes) {
            this.newStepsMinutes = Collections.unmodifiableList(normalizeSteps(newStepsMinutes, defaultNewSteps()));
            this.reviewStepsMinutes = Collections.unmodifiableList(normalizeSteps(reviewStepsMinutes, defaultReviewSteps()));
        }

        public static LearningStepSettings defaults() {
            return new LearningStepSettings(defaultNewSteps(), defaultReviewSteps());
        }

        public static List<Integer> parseSteps(String value, List<Integer> fallback) {
            List<Integer> parsed = tryParseSteps(value);
            return parsed.isEmpty() ? normalizeSteps(fallback, defaultNewSteps()) : parsed;
        }

        public static List<Integer> tryParseSteps(String value) {
            if (value == null || value.trim().isEmpty()) {
                return Collections.emptyList();
            }
            String[] parts = STEP_SEPARATOR.split(value.trim());
            List<Integer> parsed = new ArrayList<>();
            for (String part : parts) {
                Integer minutes = parseStepMinutes(part);
                if (minutes == null || minutes <= 0) {
                    return Collections.emptyList();
                }
                parsed.add(minutes);
            }
            return parsed.isEmpty() ? Collections.emptyList() : normalizeSteps(parsed, defaultNewSteps());
        }

        public static String formatSteps(List<Integer> steps) {
            List<Integer> normalized = normalizeSteps(steps, defaultNewSteps());
            List<String> parts = new ArrayList<>();
            for (int minutes : normalized) {
                if (minutes >= 60 && minutes % 60 == 0) {
                    parts.add((minutes / 60) + "h");
                } else {
                    parts.add(minutes + "m");
                }
            }
            return String.join(", ", parts);
        }

        public String newStepsText() {
            return formatSteps(newStepsMinutes);
        }

        public String reviewStepsText() {
            return formatSteps(reviewStepsMinutes);
        }

        public static List<Integer> defaultNewSteps() {
            List<Integer> out = new ArrayList<>();
            out.add(1);
            out.add(10);
            return out;
        }

        public static List<Integer> defaultReviewSteps() {
            List<Integer> out = new ArrayList<>();
            out.add(10);
            return out;
        }

        protected static Integer parseStepMinutes(String raw) {
            String value = raw.trim().toLowerCase();
            int multiplier = 1;
            if (value.endsWith("m")) {
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("h")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 60;
            }
            if (value.isEmpty()) {
                return null;
            }
            try {
                return Math.multiplyExact(Integer.parseInt(value), multiplier);
            } catch (ArithmeticException | NumberFormatException ignored) {
                return null;
            }
        }

        protected static List<Integer> normalizeSteps(List<Integer> steps, List<Integer> fallback) {
            List<Integer> out = new ArrayList<>();
            if (steps != null) {
                for (Integer step : steps) {
                    if (step == null || step <= 0) {
                        out.clear();
                        break;
                    }
                    out.add(step);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
            return new ArrayList<>(fallback);
        }
    }

    public static final class LearningRepeat {
        public final String kanji;
        public final String answerSignature;
        public final String taskType;
        public final String repeatType;
        public final int stepIndex;
        public final long dueAtMillis;
        public final String activeToken;
        public final long createdAtMillis;
        public final long updatedAtMillis;

        public LearningRepeat(String kanji, String answerSignature, String taskType, String repeatType, Object... rest) {
            requireArgCount(CONTEXT_LEARNING_REPEAT, rest, 5);
            this.kanji = nullToEmpty(kanji);
            this.answerSignature = nullToEmpty(answerSignature);
            this.taskType = nullToEmpty(taskType);
            this.repeatType = LEARNING_REPEAT_REVIEW.equals(repeatType) ? LEARNING_REPEAT_REVIEW : LEARNING_REPEAT_NEW;
            this.stepIndex = Math.max(0, intArg(rest, 0, CONTEXT_LEARNING_REPEAT));
            this.dueAtMillis = Math.max(0L, longArg(rest, 1, CONTEXT_LEARNING_REPEAT));
            String requestedActiveToken = stringArg(rest, 2, CONTEXT_LEARNING_REPEAT);
            this.activeToken = nullToEmpty(requestedActiveToken);
            this.createdAtMillis = Math.max(0L, longArg(rest, 3, CONTEXT_LEARNING_REPEAT));
            this.updatedAtMillis = Math.max(0L, longArg(rest, 4, CONTEXT_LEARNING_REPEAT));
        }

        public LearningRepeat withToken(String token, long updatedAtMillis) {
            return new LearningRepeat(kanji, answerSignature, taskType, repeatType, stepIndex, dueAtMillis, token, createdAtMillis, updatedAtMillis);
        }

        public LearningRepeat withStep(int stepIndex, long dueAtMillis, long updatedAtMillis) {
            return new LearningRepeat(kanji, answerSignature, taskType, repeatType, stepIndex, dueAtMillis, "", createdAtMillis, updatedAtMillis);
        }
    }

    public static final class ReviewRequest {
        public final String kanji;
        public final String token;
        public final String rating;
        public final String taskType;
        public final String answerSignature;
        public final String prompt;
        public final boolean writingRequired;
        public final boolean writingPassed;
        public final boolean writingClean;
        public final boolean manualOverride;
        public final int hintsUsed;

        public ReviewRequest(
                String kanji,
                String token,
                String rating,
                boolean writingRequired,
                boolean writingPassed,
                Object... rest
        ) {
            requireArgCount(CONTEXT_REVIEW_REQUEST, rest, 2, 3, 6);
            this.kanji = kanji;
            this.token = token;
            this.rating = rating;
            this.writingRequired = writingRequired;
            this.writingPassed = writingPassed;
            if (rest.length == 2) {
                this.writingClean = writingPassed && ("good".equals(rating) || "easy".equals(rating));
                this.manualOverride = booleanArg(rest, 0, CONTEXT_REVIEW_REQUEST);
                this.hintsUsed = intArg(rest, 1, CONTEXT_REVIEW_REQUEST);
                this.taskType = "";
                this.answerSignature = "";
                this.prompt = "";
            } else {
                this.writingClean = booleanArg(rest, 0, CONTEXT_REVIEW_REQUEST);
                this.manualOverride = booleanArg(rest, 1, CONTEXT_REVIEW_REQUEST);
                this.hintsUsed = intArg(rest, 2, CONTEXT_REVIEW_REQUEST);
                this.taskType = rest.length == 3 ? "" : nullToEmpty(stringArg(rest, 3, CONTEXT_REVIEW_REQUEST));
                this.answerSignature = rest.length == 3 ? "" : nullToEmpty(stringArg(rest, 4, CONTEXT_REVIEW_REQUEST));
                this.prompt = rest.length == 3 ? "" : nullToEmpty(stringArg(rest, 5, CONTEXT_REVIEW_REQUEST));
            }
        }
    }

    public static final class SchedulerParameters {
        public final double targetRetention;
        public final double againMultiplier;
        public final double hardMultiplier;
        public final double goodMultiplier;
        public final double easyMultiplier;
        public final long lastAdjustedAtMillis;
        public final int lastAdjustmentReviewCount;
        public final boolean frequencyRetentionEnabled;
        public final String frequencyRetentionRanges;

        public SchedulerParameters(
                double targetRetention,
                double againMultiplier,
                double hardMultiplier,
                double goodMultiplier,
                double easyMultiplier,
                long lastAdjustedAtMillis,
                int lastAdjustmentReviewCount
        ) {
            this(
                    targetRetention,
                    againMultiplier,
                    hardMultiplier,
                    goodMultiplier,
                    easyMultiplier,
                    lastAdjustedAtMillis,
                    lastAdjustmentReviewCount,
                    DEFAULT_FREQUENCY_RETENTION_ENABLED,
                    DEFAULT_FREQUENCY_RETENTION_RANGES
            );
        }

        public SchedulerParameters(
                double targetRetention,
                double againMultiplier,
                double hardMultiplier,
                double goodMultiplier,
                double easyMultiplier,
                long lastAdjustedAtMillis,
                int lastAdjustmentReviewCount,
                boolean frequencyRetentionEnabled,
                String frequencyRetentionRanges
        ) {
            this.targetRetention = targetRetention;
            this.againMultiplier = againMultiplier;
            this.hardMultiplier = hardMultiplier;
            this.goodMultiplier = goodMultiplier;
            this.easyMultiplier = easyMultiplier;
            this.lastAdjustedAtMillis = lastAdjustedAtMillis;
            this.lastAdjustmentReviewCount = lastAdjustmentReviewCount;
            this.frequencyRetentionEnabled = frequencyRetentionEnabled;
            this.frequencyRetentionRanges = nullToEmpty(frequencyRetentionRanges).trim();
        }

        public static SchedulerParameters defaults() {
            return new SchedulerParameters(0.90, 0.45, 1.20, 2.00, 3.10, 0L, 0);
        }

        public double targetRetentionForRank(Integer jitenRank) {
            if (!frequencyRetentionEnabled) {
                return targetRetention;
            }
            try {
                Double rankedRetention = FrequencyRetentionRanges.retentionForRank(frequencyRetentionRanges, jitenRank);
                return rankedRetention == null ? targetRetention : rankedRetention;
            } catch (IllegalArgumentException error) {
                return targetRetention;
            }
        }

        public SchedulerParameters withTargetRetention(double retention) {
            return new SchedulerParameters(
                    retention,
                    againMultiplier,
                    hardMultiplier,
                    goodMultiplier,
                    easyMultiplier,
                    lastAdjustedAtMillis,
                    lastAdjustmentReviewCount,
                    frequencyRetentionEnabled,
                    frequencyRetentionRanges
            );
        }

        public SchedulerParameters withAdjustment(double again, double hard, double good, double easy, long adjustedAtMillis, int reviewCount) {
            return new SchedulerParameters(
                    targetRetention,
                    clamp(again, 0.25, 0.75),
                    clamp(hard, 1.05, 1.80),
                    clamp(good, 1.35, 3.20),
                    clamp(easy, 2.00, 4.80),
                    adjustedAtMillis,
                    reviewCount,
                    frequencyRetentionEnabled,
                    frequencyRetentionRanges
            );
        }

        protected static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public static final class ReviewStats {
        public final int total;
        public final int again;
        public final int hard;
        public final int good;
        public final int easy;
        public final int writingRequired;
        public final int writingFailed;

        public ReviewStats(int total, int again, int hard, int good, int easy, int writingRequired, int writingFailed) {
            this.total = total;
            this.again = again;
            this.hard = hard;
            this.good = good;
            this.easy = easy;
            this.writingRequired = writingRequired;
            this.writingFailed = writingFailed;
        }

        public double retentionProxy() {
            if (total == 0) {
                return 1.0;
            }
            return (hard + good + easy) / (double) total;
        }

        public double writingFailureRate() {
            if (writingRequired == 0) {
                return 0.0;
            }
            return writingFailed / (double) writingRequired;
        }
    }

    public static final class ReviewResult {
        public final StudyItem item;
        public final String appliedRating;
        public final boolean duplicate;
        public final String message;

        public ReviewResult(StudyItem item, String appliedRating, boolean duplicate, String message) {
            this.item = item;
            this.appliedRating = appliedRating;
            this.duplicate = duplicate;
            this.message = message;
        }
    }

    public static final class AdaptiveLoadPlan {
        public final boolean autoMode;
        public final int workloadPercent;
        public final int target;
        public final int remaining;
        public final List<String> focusKanji;
        public final int newAdmissionLimit;
        public final boolean allKanjiMode;
        public final String status;

        public AdaptiveLoadPlan(
                int workloadPercent,
                int target,
                int remaining,
                List<String> focusKanji,
                int newAdmissionLimit,
                boolean allKanjiMode,
                String status
        ) {
            this(false, workloadPercent, target, remaining, focusKanji, newAdmissionLimit, allKanjiMode, status);
        }

        public AdaptiveLoadPlan(boolean autoMode, int workloadPercent, int target, int remaining, List<String> focusKanji, Object... rest) {
            requireArgCount(CONTEXT_ADAPTIVE_LOAD_PLAN, rest, 3);
            this.autoMode = autoMode;
            this.workloadPercent = workloadPercent;
            this.target = target;
            this.remaining = remaining;
            this.focusKanji = Collections.unmodifiableList(new ArrayList<>(focusKanji));
            this.newAdmissionLimit = intArg(rest, 0, CONTEXT_ADAPTIVE_LOAD_PLAN);
            this.allKanjiMode = booleanArg(rest, 1, CONTEXT_ADAPTIVE_LOAD_PLAN);
            this.status = nullToEmpty(stringArg(rest, 2, CONTEXT_ADAPTIVE_LOAD_PLAN));
        }

        public boolean focusComplete() {
            return !allKanjiMode && target > 0 && remaining <= 0;
        }
    }

    public static final class ReleaseAsset {
        public final String name;
        public final String downloadUrl;

        public ReleaseAsset(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }
    }

    public static final class ReleaseInfo {
        public final String tagName;
        public final String htmlUrl;
        public final List<ReleaseAsset> assets;

        public ReleaseInfo(String tagName, String htmlUrl, List<ReleaseAsset> assets) {
            this.tagName = tagName;
            this.htmlUrl = htmlUrl;
            this.assets = Collections.unmodifiableList(new ArrayList<>(assets));
        }

        public ReleaseAsset apkAsset() {
            for (ReleaseAsset asset : assets) {
                if (asset.name.endsWith(".apk")) {
                    return asset;
                }
            }
            return null;
        }

        public ReleaseAsset checksumAssetFor(String apkName) {
            for (ReleaseAsset asset : assets) {
                if (Objects.equals(asset.name, apkName + ".sha256")) {
                    return asset;
                }
            }
            return null;
        }
    }
}
