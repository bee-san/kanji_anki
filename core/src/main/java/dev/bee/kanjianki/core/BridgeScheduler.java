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
        return seedQueueInternal(rows, rows, existing, settings, nowMillis, startOfDayMillis, settings.newPerDay, false);
    }

    public List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            Records.AdaptiveLoadPlan plan
    ) {
        if (plan == null) {
            return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis);
        }
        List<Records.DashboardRow> admissionRows = plan.allKanjiMode ? rows : rowsForFocus(rows, plan.focusKanji);
        return seedQueueInternal(
                rows,
                admissionRows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                plan.newAdmissionLimit,
                plan.allKanjiMode
        );
    }

    private List<Records.StudyItem> seedQueueInternal(
            List<Records.DashboardRow> allRows,
            List<Records.DashboardRow> admissionRows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            int newAdmissionLimit,
            boolean allKanjiMode
    ) {
        int activeQueueCap = allKanjiMode ? Integer.MAX_VALUE : settings.activeQueueCap;
        int admissionLimit = allKanjiMode ? Integer.MAX_VALUE : Math.max(0, newAdmissionLimit);
        Map<String, Records.DashboardRow> rowByKanji = new HashMap<>();
        for (Records.DashboardRow row : allRows) {
            rowByKanji.put(row.kanji, row);
        }

        Map<String, Records.StudyItem> byKanji = new HashMap<>();
        List<Records.StudyItem> out = new ArrayList<>();
        int activeCount = 0;
        int newToday = 0;
        for (Records.StudyItem item : existing) {
            Records.DashboardRow row = rowByKanji.get(item.kanji);
            Records.StudyItem current = item;
            if (!"retired".equals(item.state)) {
                if (row == null || (row.matureSupportCount >= settings.matureSupportThreshold && item.totalReviews > 0)) {
                    current = retiredCopy(item);
                }
            }
            byKanji.put(current.kanji, current);
            out.add(current);
            if (!"retired".equals(current.state)) {
                activeCount++;
                if (current.createdAtMillis >= startOfDayMillis) {
                    newToday++;
                }
            }
        }

        for (Records.DashboardRow row : admissionRows) {
            Records.StudyItem current = byKanji.get(row.kanji);
            if (current != null) {
                if ("retired".equals(current.state)
                        && row.matureSupportCount < settings.matureSupportThreshold
                        && activeCount < activeQueueCap
                        && newToday < admissionLimit) {
                    Records.StudyItem reopened = newStudyItem(row.kanji, nowMillis);
                    out.remove(current);
                    out.add(reopened);
                    byKanji.put(row.kanji, reopened);
                    activeCount++;
                    newToday++;
                }
                continue;
            }
            if (activeCount >= activeQueueCap || newToday >= admissionLimit) {
                continue;
            }
            out.add(newStudyItem(row.kanji, nowMillis));
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
        return nextSession(items, rows, nowMillis, null);
    }

    public Records.StudySession nextSession(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            Set<String> allowedKanji
    ) {
        Map<String, Records.DashboardRow> rowByKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
        }
        Records.StudyItem best = null;
        for (Records.StudyItem item : items) {
            if ("retired".equals(item.state) || item.dueAtMillis > nowMillis) {
                continue;
            }
            if (allowedKanji != null && !allowedKanji.contains(item.kanji)) {
                continue;
            }
            if (!rowByKanji.containsKey(item.kanji)) {
                continue;
            }
            if (best == null || compareDueItems(item, best, rowByKanji) < 0) {
                best = item;
            }
        }
        if (best == null) {
            return null;
        }
        Records.DashboardRow row = rowByKanji.get(best.kanji);
        String token = best.activeToken == null || best.activeToken.isEmpty()
                ? best.kanji + "-" + UUID.randomUUID()
                : best.activeToken;
        String taskType;
        boolean writingRequired;
        if (best.totalReviews == 0 || best.learningStep == 0) {
            taskType = best.totalReviews == 0 ? "meaning_flashcard" : "font_recognition";
            writingRequired = false;
        } else if (best.learningStep == 1) {
            taskType = "guided_writing";
            writingRequired = true;
        } else if (best.totalReviews % 5 == 0) {
            taskType = "font_recognition";
            writingRequired = false;
        } else if (best.totalReviews % 5 == 1) {
            taskType = "meaning_flashcard";
            writingRequired = false;
        } else if (best.totalReviews % 3 == 0) {
            taskType = "sampled_handwriting";
            writingRequired = true;
        } else {
            taskType = "blind_writing";
            writingRequired = true;
        }
        String prompt = row.reasonText;
        return new Records.StudySession(best.withToken(token), row, token, taskType, writingRequired, prompt);
    }

    private List<Records.DashboardRow> rowsForFocus(List<Records.DashboardRow> rows, List<String> focusKanji) {
        Map<String, Records.DashboardRow> byKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            byKanji.put(row.kanji, row);
        }
        List<Records.DashboardRow> out = new ArrayList<>();
        for (String kanji : focusKanji) {
            Records.DashboardRow row = byKanji.get(kanji);
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    private Records.StudyItem retiredCopy(Records.StudyItem item) {
        return new Records.StudyItem(
                item.kanji,
                "retired",
                item.dueAtMillis,
                item.stability,
                item.difficulty,
                item.totalReviews,
                item.lapses,
                item.learningStep,
                item.writingLevel,
                null,
                item.createdAtMillis
        );
    }

    private Records.StudyItem newStudyItem(String kanji, long nowMillis) {
        return new Records.StudyItem(
                kanji,
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
        );
    }

    private static int compareDueItems(
            Records.StudyItem left,
            Records.StudyItem right,
            Map<String, Records.DashboardRow> rowByKanji
    ) {
        int priority = Integer.compare(duePriority(left), duePriority(right));
        if (priority != 0) {
            return priority;
        }
        int due = Long.compare(left.dueAtMillis, right.dueAtMillis);
        if (due != 0) {
            return due;
        }
        int weakness = Integer.compare(rowWeakness(right, rowByKanji), rowWeakness(left, rowByKanji));
        if (weakness != 0) {
            return weakness;
        }
        return left.kanji.compareTo(right.kanji);
    }

    private static int duePriority(Records.StudyItem item) {
        if ("learning".equals(item.state) || (item.totalReviews > 0 && item.learningStep < 2)) {
            return 0;
        }
        if ("review".equals(item.state) || item.totalReviews > 0) {
            return 1;
        }
        return 2;
    }

    private static int rowWeakness(Records.StudyItem item, Map<String, Records.DashboardRow> rowByKanji) {
        Records.DashboardRow row = rowByKanji.get(item.kanji);
        return row == null ? 0 : row.weaknessScore;
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
        boolean writingReviewCanMoveHelp = request.writingRequired && !request.manualOverride;
        boolean cleanWritingPass = writingReviewCanMoveHelp
                && request.writingPassed
                && request.writingClean
                && request.hintsUsed <= 0;
        boolean failedWriting = writingReviewCanMoveHelp && !request.writingPassed;

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
                stability = Math.max(2.5, stability * parameters.easyMultiplier);
                difficulty = Math.max(1.0, difficulty - 0.35);
                due = nowMillis + reviewInterval(stability, parameters);
                state = "review";
                break;
            case "good":
            default:
                step = Math.min(2, step + 1);
                stability = Math.max(1.0, stability * parameters.goodMultiplier);
                difficulty = Math.max(1.0, difficulty - 0.1);
                due = step < 2 ? nowMillis + 10 * MINUTE : nowMillis + reviewInterval(stability, parameters);
                state = step < 2 ? "learning" : "review";
                break;
        }
        if (failedWriting) {
            writingLevel = Math.max(0, writingLevel - 1);
        } else if (cleanWritingPass) {
            writingLevel = Math.min(3, writingLevel + 1);
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
