package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BridgeScheduler {
    private static final long MINUTE = 60_000L;
    private static final long DAY = 86_400_000L;

    public List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis
    ) {
        Map<String, Records.StudyItem> byKanji = new HashMap<>();
        int activeCount = 0;
        int newToday = 0;
        for (Records.StudyItem item : existing) {
            byKanji.put(item.kanji, item);
            if (!"retired".equals(item.state)) {
                activeCount++;
            }
            if (item.createdAtMillis >= startOfDayMillis) {
                newToday++;
            }
        }

        List<Records.StudyItem> out = new ArrayList<>(existing);
        for (Records.DashboardRow row : rows) {
            Records.StudyItem current = byKanji.get(row.kanji);
            if (current != null) {
                if (row.matureSupportCount >= settings.matureSupportThreshold && row.suspendedExampleCount == 0 && current.totalReviews > 0) {
                    out.remove(current);
                    out.add(new Records.StudyItem(
                            current.kanji,
                            "retired",
                            current.dueAtMillis,
                            current.stability,
                            current.difficulty,
                            current.totalReviews,
                            current.lapses,
                            current.learningStep,
                            current.writingLevel,
                            null,
                            current.createdAtMillis
                    ));
                }
                continue;
            }
            if (activeCount >= settings.activeQueueCap || newToday >= settings.newPerDay) {
                continue;
            }
            out.add(new Records.StudyItem(
                    row.kanji,
                    "new",
                    nowMillis,
                    0.4,
                    5.0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    nowMillis
            ));
            activeCount++;
            newToday++;
        }
        out.sort(Comparator
                .comparing((Records.StudyItem item) -> item.state.equals("retired"))
                .thenComparingLong(item -> item.dueAtMillis)
                .thenComparing(item -> item.kanji));
        return out;
    }

    public Records.StudySession nextSession(List<Records.StudyItem> items, List<Records.DashboardRow> rows, long nowMillis) {
        Map<String, Records.DashboardRow> rowByKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
        }
        Records.StudyItem best = null;
        for (Records.StudyItem item : items) {
            if ("retired".equals(item.state) || item.dueAtMillis > nowMillis) {
                continue;
            }
            if (best == null || item.dueAtMillis < best.dueAtMillis) {
                best = item;
            }
        }
        if (best == null) {
            return null;
        }
        String token = best.activeToken == null || best.activeToken.isEmpty()
                ? best.kanji + "-" + UUID.randomUUID()
                : best.activeToken;
        String taskType;
        boolean writing;
        if (best.totalReviews == 0 || best.learningStep == 0) {
            taskType = "context_writing";
            writing = true;
        } else if (best.learningStep == 1) {
            taskType = "confusable_recognition";
            writing = false;
        } else if (best.totalReviews % 3 == 0) {
            taskType = "sampled_handwriting";
            writing = true;
        } else {
            taskType = "recognition";
            writing = false;
        }
        Records.DashboardRow row = rowByKanji.get(best.kanji);
        String prompt = row == null ? best.kanji : row.reasonText;
        return new Records.StudySession(best.withToken(token), row, token, taskType, writing, prompt);
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, Records.SchedulerParameters.defaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters
    ) {
        if (parameters == null) {
            parameters = Records.SchedulerParameters.defaults();
        }
        if (consumedTokens.contains(request.token)) {
            return new Records.ReviewResult(item, "duplicate", true, "Review token already consumed.");
        }
        if (item.activeToken != null && !item.activeToken.isEmpty() && !item.activeToken.equals(request.token)) {
            return new Records.ReviewResult(item, "duplicate", true, "Review token does not match the active session.");
        }
        consumedTokens.add(request.token);
        String rating = normalizeRating(request.rating);
        if (request.writingRequired && !request.writingPassed && !request.manualOverride) {
            rating = "again";
        }

        int total = item.totalReviews + 1;
        int lapses = item.lapses;
        int step = item.learningStep;
        int writingLevel = item.writingLevel;
        double stability = item.stability;
        double difficulty = item.difficulty;
        long due;
        String state;

        switch (rating) {
            case "again":
                lapses++;
                step = 0;
                writingLevel = Math.max(0, writingLevel - 1);
                stability = Math.max(0.2, stability * parameters.againMultiplier);
                difficulty = Math.min(10.0, difficulty + 0.7);
                due = nowMillis + 10 * MINUTE;
                state = "learning";
                break;
            case "hard":
                step = Math.max(1, step);
                stability = Math.max(0.5, stability * parameters.hardMultiplier);
                difficulty = Math.min(10.0, difficulty + 0.2);
                due = nowMillis + DAY;
                state = "review";
                break;
            case "easy":
                step = 2;
                writingLevel = Math.min(3, writingLevel + 1);
                stability = Math.max(2.5, stability * parameters.easyMultiplier);
                difficulty = Math.max(1.0, difficulty - 0.35);
                due = nowMillis + reviewInterval(stability, parameters);
                state = "review";
                break;
            case "good":
            default:
                step = Math.min(2, step + 1);
                writingLevel = request.writingRequired && request.writingPassed ? Math.min(3, writingLevel + 1) : writingLevel;
                stability = Math.max(1.0, stability * parameters.goodMultiplier);
                difficulty = Math.max(1.0, difficulty - 0.1);
                due = step < 2 ? nowMillis + 10 * MINUTE : nowMillis + reviewInterval(stability, parameters);
                state = step < 2 ? "learning" : "review";
                break;
        }

        Records.StudyItem updated = new Records.StudyItem(
                item.kanji,
                state,
                due,
                round(stability),
                round(difficulty),
                total,
                lapses,
                step,
                writingLevel,
                null,
                item.createdAtMillis
        );
        return new Records.ReviewResult(updated, rating, false, "Review applied.");
    }

    public int dueCount(List<Records.StudyItem> items, long nowMillis) {
        int count = 0;
        for (Records.StudyItem item : items) {
            if (!"retired".equals(item.state) && item.dueAtMillis <= nowMillis) {
                count++;
            }
        }
        return count;
    }

    public Set<String> tokenSet(List<String> tokens) {
        return new HashSet<>(tokens);
    }

    private static String normalizeRating(String rating) {
        if (rating == null) {
            return "again";
        }
        switch (rating) {
            case "again":
            case "hard":
            case "good":
            case "easy":
                return rating;
            default:
                return "again";
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static long reviewInterval(double stability, Records.SchedulerParameters parameters) {
        double retentionFactor = Math.log(parameters.targetRetention) / Math.log(0.90);
        double days = Math.max(1.0, stability * retentionFactor);
        return Math.round(days * DAY);
    }
}
