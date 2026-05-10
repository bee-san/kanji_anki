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
    private static final int MIN_RECOGNITION_STAGE = -1;
    private static final int MAX_RECOGNITION_STAGE = 2;
    private static final String STATE_NEW = "new";
    private static final String STATE_LEARNING = "learning";
    private static final String STATE_REVIEW = "review";
    private static final String STATE_RETIRED = "retired";
    private static final String RATING_AGAIN = "again";
    private static final String RATING_HARD = "hard";
    private static final String RATING_GOOD = "good";
    private static final String RATING_EASY = "easy";
    public static final String TASK_TYPING_MEANING = "typing_meaning";
    public static final String TASK_KANJI_MEANING = "kanji_meaning";
    public static final String TASK_FONT_MEANING = "font_meaning";
    public static final String TASK_WORD_READING = "word_reading";
    public static final String TASK_WRITING_REMEDIATION = "writing_remediation";

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
        Map<String, Records.DashboardRow> rowByFamily = new HashMap<>();
        Map<String, List<Records.DashboardRow>> rowsByKanji = new HashMap<>();
        for (Records.DashboardRow row : allRows) {
            rowByFamily.put(rowFamilyKey(row), row);
            List<Records.DashboardRow> familyRows = rowsByKanji.get(row.kanji);
            if (familyRows == null) {
                familyRows = new ArrayList<>();
                rowsByKanji.put(row.kanji, familyRows);
            }
            familyRows.add(row);
        }

        Map<String, Records.StudyItem> byFamily = new HashMap<>();
        List<Records.StudyItem> out = new ArrayList<>();
        int activeCount = 0;
        int newToday = 0;
        for (Records.StudyItem item : existing) {
            Records.DashboardRow row = rowByFamily.get(familyKey(item));
            List<Records.DashboardRow> familyRows = rowsByKanji.get(item.kanji);
            if (row == null && familyRows != null && (item.answerSignature.isEmpty() || familyRows.size() == 1)) {
                row = familyRows.get(0);
            }
            Records.StudyItem current = item;
            if (row != null) {
                current = alignAnswerSignature(current, row, nowMillis);
            }
            if (!STATE_RETIRED.equals(item.state)
                    && (row == null || (row.matureSupportCount >= settings.matureSupportThreshold && current.totalReviews > 0))) {
                current = retiredCopy(current);
            }
            byFamily.put(familyKey(current), current);
            out.add(current);
            if (!STATE_RETIRED.equals(current.state)) {
                activeCount++;
                if (current.createdAtMillis >= startOfDayMillis) {
                    newToday++;
                }
            }
        }

        for (Records.DashboardRow row : admissionRows) {
            String rowKey = rowFamilyKey(row);
            Records.StudyItem current = byFamily.get(rowKey);
            if (current != null) {
                if (STATE_RETIRED.equals(current.state)
                        && row.matureSupportCount < settings.matureSupportThreshold
                        && activeCount < activeQueueCap
                        && newToday < admissionLimit) {
                    Records.StudyItem reopened = newStudyItem(row.kanji, nowMillis, answerSignature(row));
                    out.remove(current);
                    out.add(reopened);
                    byFamily.put(rowKey, reopened);
                    activeCount++;
                    newToday++;
                }
                continue;
            }
            if (activeCount >= activeQueueCap || newToday >= admissionLimit) {
                continue;
            }
            out.add(newStudyItem(row.kanji, nowMillis, answerSignature(row)));
            activeCount++;
            newToday++;
        }
        out.sort(Comparator
                .comparing((Records.StudyItem item) -> item.state.equals(STATE_RETIRED))
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
        for (Records.StudyItem item : activeQueueItems(items, rows, nowMillis, allowedKanji)) {
            if (item.dueAtMillis > nowMillis) {
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
        if (best.writingRemediationPending) {
            taskType = TASK_WRITING_REMEDIATION;
            writingRequired = true;
        } else {
            taskType = recognitionTaskType(best.recognitionStage);
            writingRequired = false;
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
                STATE_RETIRED,
                item.dueAtMillis,
                item.stability,
                item.difficulty,
                item.totalReviews,
                item.lapses,
                item.learningStep,
                item.writingLevel,
                item.recognitionStage,
                item.consecutiveFailedRecognitionDays,
                item.lastFailedRecognitionDayMillis,
                item.writingRemediationPending,
                item.suppressedByTaskType,
                item.suppressedAtMillis,
                item.matureIntervalDays,
                item.answerSignature,
                null,
                item.createdAtMillis,
                item.typingMeaningMemory,
                item.kanjiMeaningMemory,
                item.fontMeaningMemory,
                item.wordReadingMemory,
                item.writingRemediationMemory
        );
    }

    private Records.StudyItem newStudyItem(String kanji, long nowMillis, String answerSignature) {
        return new Records.StudyItem(
                kanji,
                STATE_NEW,
                nowMillis,
                0.4,
                5.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                false,
                null,
                0L,
                0,
                answerSignature,
                null,
                nowMillis
        );
    }

    private Records.StudyItem alignAnswerSignature(Records.StudyItem item, Records.DashboardRow row, long nowMillis) {
        String signature = answerSignature(row);
        if (signature.isEmpty() || item.answerSignature.isEmpty() || signature.equals(item.answerSignature)) {
            return item.withAnswerSignature(signature);
        }
        int fallbackStage = Math.max(MIN_RECOGNITION_STAGE, item.recognitionStage - 1);
        boolean retired = STATE_RETIRED.equals(item.state);
        return new Records.StudyItem(
                item.kanji,
                retired ? item.state : STATE_LEARNING,
                retired ? item.dueAtMillis : nowMillis,
                retired ? item.stability : 0.4,
                retired ? item.difficulty : 5.0,
                retired ? item.totalReviews : 0,
                retired ? item.lapses : 0,
                retired ? item.learningStep : 0,
                item.writingLevel,
                fallbackStage,
                retired ? item.consecutiveFailedRecognitionDays : 0,
                retired ? item.lastFailedRecognitionDayMillis : 0L,
                retired && item.writingRemediationPending,
                null,
                0L,
                0,
                signature,
                null,
                item.createdAtMillis,
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial()
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
        if (item.writingRemediationPending) {
            return 0;
        }
        if (STATE_LEARNING.equals(item.state) || (item.totalReviews > 0 && item.learningStep < 2)) {
            return 0;
        }
        if (STATE_REVIEW.equals(item.state) || item.totalReviews > 0) {
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
        return applyReview(item, request, consumedTokens, nowMillis, Records.SchedulerParameters.defaults(), Records.Settings.kikuDefaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, Records.Settings.kikuDefaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters,
            Records.Settings settings
    ) {
        if (parameters == null) {
            parameters = Records.SchedulerParameters.defaults();
        }
        if (settings == null) {
            settings = Records.Settings.kikuDefaults();
        }
        if (consumedTokens.contains(request.token)) {
            return new Records.ReviewResult(item, "duplicate", true, "Review token already consumed.");
        }
        if (item.activeToken != null && !item.activeToken.isEmpty() && !item.activeToken.equals(request.token)) {
            return new Records.ReviewResult(item, "duplicate", true, "Review token does not match the active session.");
        }
        consumedTokens.add(request.token);
        String rating = normalizeRating(request.rating);
        boolean writingRemediationReview = request.writingRequired && item.writingRemediationPending;
        String reviewedTaskType = item.writingRemediationPending
                ? TASK_WRITING_REMEDIATION
                : recognitionTaskType(item.recognitionStage);
        if (writingRemediationReview && request.manualOverride) {
            rating = RATING_HARD;
        } else if (request.writingRequired && !request.writingPassed && !request.manualOverride) {
            rating = RATING_AGAIN;
        }
        boolean writingReviewCanMoveHelp = request.writingRequired && !request.manualOverride;
        boolean cleanWritingPass = writingReviewCanMoveHelp
                && request.writingPassed
                && request.writingClean
                && request.hintsUsed <= 0;
        boolean failedWriting = writingReviewCanMoveHelp && !request.writingPassed;

        Records.TaskMemory previousTaskMemory = item.memoryForTaskType(reviewedTaskType);
        int total = item.totalReviews + 1;
        int lapses = item.lapses;
        int taskTotal = previousTaskMemory.totalReviews + 1;
        int taskLapses = previousTaskMemory.lapses;
        int step = previousTaskMemory.learningStep;
        int writingLevel = item.writingLevel;
        int recognitionStage = item.recognitionStage;
        int failedRecognitionDays = item.consecutiveFailedRecognitionDays;
        long lastFailedRecognitionDay = item.lastFailedRecognitionDayMillis;
        int recognitionPasses = previousTaskMemory.consecutivePasses;
        long lastRecognitionPassDueAt = previousTaskMemory.lastPassedDueAtMillis;
        boolean writingRemediationPending = item.writingRemediationPending;
        double stability = previousTaskMemory.stability;
        double difficulty = previousTaskMemory.difficulty;
        long due;
        int scheduledIntervalDays;
        String state;

        switch (rating) {
            case RATING_AGAIN:
                lapses++;
                taskLapses++;
                step = 0;
                stability = Math.max(0.2, stability * parameters.againMultiplier);
                difficulty = Math.min(10.0, difficulty + 0.7);
                due = nowMillis + 10 * MINUTE;
                scheduledIntervalDays = 0;
                state = STATE_LEARNING;
                break;
            case RATING_HARD:
                step = Math.max(1, step);
                stability = Math.max(0.5, stability * parameters.hardMultiplier);
                difficulty = Math.min(10.0, difficulty + 0.2);
                due = nowMillis + DAY;
                scheduledIntervalDays = 1;
                state = STATE_REVIEW;
                break;
            case RATING_EASY:
                step = 2;
                stability = Math.max(2.5, stability * parameters.easyMultiplier);
                difficulty = Math.max(1.0, difficulty - 0.35);
                long easyInterval = reviewInterval(stability, parameters);
                scheduledIntervalDays = intervalDays(easyInterval);
                due = nowMillis + easyInterval;
                state = STATE_REVIEW;
                break;
            case RATING_GOOD:
            default:
                step = Math.min(2, step + 1);
                stability = Math.max(1.0, stability * parameters.goodMultiplier);
                difficulty = Math.max(1.0, difficulty - 0.1);
                long goodInterval = step < 2 ? 10 * MINUTE : reviewInterval(stability, parameters);
                scheduledIntervalDays = step < 2 ? 0 : intervalDays(goodInterval);
                due = nowMillis + goodInterval;
                state = step < 2 ? STATE_LEARNING : STATE_REVIEW;
                break;
        }
        if (failedWriting) {
            writingLevel = Math.max(0, writingLevel - 1);
        } else if (cleanWritingPass) {
            writingLevel = Math.min(3, writingLevel + 1);
        }
        if (!request.writingRequired) {
            if (RATING_AGAIN.equals(rating)) {
                if (previousTaskMemory.dueAtMillis <= nowMillis
                        && (failedRecognitionDays <= 0 || lastFailedRecognitionDay != previousTaskMemory.dueAtMillis)) {
                    failedRecognitionDays++;
                    lastFailedRecognitionDay = previousTaskMemory.dueAtMillis;
                }
                if (failedRecognitionDays >= settings.writingTriggerMissDays) {
                    failedRecognitionDays = 0;
                    lastFailedRecognitionDay = 0L;
                    if (recognitionStage <= MIN_RECOGNITION_STAGE) {
                        writingRemediationPending = true;
                    } else {
                        recognitionStage = Math.max(MIN_RECOGNITION_STAGE, recognitionStage - 1);
                        writingRemediationPending = false;
                    }
                }
            } else {
                if (previousTaskMemory.dueAtMillis <= nowMillis
                        && (recognitionPasses <= 0 || lastRecognitionPassDueAt != previousTaskMemory.dueAtMillis)) {
                    recognitionPasses++;
                    lastRecognitionPassDueAt = previousTaskMemory.dueAtMillis;
                }
                if (recognitionPasses >= settings.recognitionPromotionPasses) {
                    recognitionStage = Math.min(MAX_RECOGNITION_STAGE, recognitionStage + 1);
                    recognitionPasses = 0;
                    lastRecognitionPassDueAt = 0L;
                }
                failedRecognitionDays = 0;
                lastFailedRecognitionDay = 0L;
                writingRemediationPending = false;
            }
        } else if (writingRemediationReview) {
            if (request.manualOverride || request.writingPassed) {
                recognitionStage = MIN_RECOGNITION_STAGE;
                failedRecognitionDays = 0;
                lastFailedRecognitionDay = 0L;
                writingRemediationPending = false;
            } else {
                writingRemediationPending = true;
            }
        }

        String suppressedByTaskType = item.suppressedByTaskType;
        long suppressedAtMillis = item.suppressedAtMillis;
        int matureIntervalDays = scheduledIntervalDays;
        if (RATING_AGAIN.equals(rating)) {
            suppressedByTaskType = "";
            suppressedAtMillis = 0L;
            matureIntervalDays = 0;
        } else if (dominatesLowerSiblings(reviewedTaskType)
                && previousTaskMemory.totalReviews > 0
                && previousTaskMemory.dueAtMillis <= nowMillis
                && scheduledIntervalDays >= settings.matureDays) {
            suppressedByTaskType = reviewedTaskType;
            suppressedAtMillis = nowMillis;
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
                recognitionStage,
                failedRecognitionDays,
                lastFailedRecognitionDay,
                writingRemediationPending,
                suppressedByTaskType,
                suppressedAtMillis,
                matureIntervalDays,
                item.answerSignature,
                null,
                item.createdAtMillis,
                item.typingMeaningMemory,
                item.kanjiMeaningMemory,
                item.fontMeaningMemory,
                item.wordReadingMemory,
                item.writingRemediationMemory
        ).withTaskMemory(
                reviewedTaskType,
                new Records.TaskMemory(
                        state,
                        due,
                        round(stability),
                        round(difficulty),
                        taskTotal,
                        taskLapses,
                        step,
                        rating,
                        scheduledIntervalDays,
                        RATING_AGAIN.equals(rating) || request.writingRequired ? 0 : recognitionPasses,
                        RATING_AGAIN.equals(rating) || request.writingRequired ? 0L : lastRecognitionPassDueAt
                )
        );
        return new Records.ReviewResult(updated, rating, false, "Review applied.");
    }

    public int dueCount(List<Records.StudyItem> items, long nowMillis) {
        int count = 0;
        for (Records.StudyItem item : items) {
            if (!STATE_RETIRED.equals(item.state) && item.dueAtMillis <= nowMillis) {
                count++;
            }
        }
        return count;
    }

    public int dueCount(List<Records.StudyItem> items, List<Records.DashboardRow> rows, long nowMillis) {
        int count = 0;
        for (Records.StudyItem item : activeQueueItems(items, rows, nowMillis, null)) {
            if (item.dueAtMillis <= nowMillis) {
                count++;
            }
        }
        return count;
    }

    public List<Records.StudyItem> activeQueueItems(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            Set<String> allowedKanji
    ) {
        Set<String> currentRows = new HashSet<>();
        Set<String> currentFamilies = new HashSet<>();
        for (Records.DashboardRow row : rows) {
            currentRows.add(row.kanji);
            currentFamilies.add(rowFamilyKey(row));
        }
        Map<String, List<Records.StudyItem>> byFamily = new HashMap<>();
        for (Records.StudyItem item : items) {
            if (STATE_RETIRED.equals(item.state)) {
                continue;
            }
            if (allowedKanji != null && !allowedKanji.contains(item.kanji)) {
                continue;
            }
            if (!currentFamilies.contains(familyKey(item)) && !(item.answerSignature.isEmpty() && currentRows.contains(item.kanji))) {
                continue;
            }
            String familyKey = familyKey(item);
            List<Records.StudyItem> family = byFamily.get(familyKey);
            if (family == null) {
                family = new ArrayList<>();
                byFamily.put(familyKey, family);
            }
            family.add(item);
        }
        List<Records.StudyItem> out = new ArrayList<>();
        for (List<Records.StudyItem> family : byFamily.values()) {
            Records.StudyItem active = activeFamilyItem(family, nowMillis);
            if (active != null) {
                out.add(active);
            }
        }
        return out;
    }

    public Set<String> tokenSet(List<String> tokens) {
        return new HashSet<>(tokens);
    }

    private static String normalizeRating(String rating) {
        if (rating == null) {
            return RATING_AGAIN;
        }
        switch (rating) {
            case RATING_AGAIN, RATING_HARD, RATING_GOOD, RATING_EASY:
                return rating;
            default:
                return RATING_AGAIN;
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

    private static String recognitionTaskType(int stage) {
        switch (Math.max(MIN_RECOGNITION_STAGE, Math.min(MAX_RECOGNITION_STAGE, stage))) {
            case -1:
                return TASK_TYPING_MEANING;
            case 1:
                return TASK_FONT_MEANING;
            case 2:
                return TASK_WORD_READING;
            default:
                return TASK_KANJI_MEANING;
        }
    }

    private static Records.StudyItem activeFamilyItem(List<Records.StudyItem> family, long nowMillis) {
        Records.StudyItem best = null;
        for (Records.StudyItem item : family) {
            if (suppressedByMatureSibling(item, family)) {
                continue;
            }
            if (best == null || compareFamilyActivity(item, best, nowMillis) < 0) {
                best = item;
            }
        }
        return best;
    }

    private static int compareFamilyActivity(Records.StudyItem left, Records.StudyItem right, long nowMillis) {
        int rank = Integer.compare(-taskRank(left), -taskRank(right));
        if (rank != 0) {
            return rank;
        }
        int due = Integer.compare(left.dueAtMillis <= nowMillis ? 0 : 1, right.dueAtMillis <= nowMillis ? 0 : 1);
        if (due != 0) {
            return due;
        }
        return Long.compare(left.dueAtMillis, right.dueAtMillis);
    }

    private static boolean suppressedByMatureSibling(Records.StudyItem item, List<Records.StudyItem> family) {
        for (Records.StudyItem sibling : family) {
            if (sibling.suppressedByTaskType == null || sibling.suppressedByTaskType.isEmpty()) {
                continue;
            }
            int dominantRank = taskRank(sibling.suppressedByTaskType);
            if (ladderRank(item) < dominantRank && sameAnswerSignature(item, sibling)) {
                return true;
            }
        }
        return false;
    }

    private static String familyKey(Records.StudyItem item) {
        return familyKey(item.kanji, item.answerSignature);
    }

    private static String rowFamilyKey(Records.DashboardRow row) {
        return familyKey(row.kanji, answerSignature(row));
    }

    private static String familyKey(String kanji, String answerSignature) {
        return kanji + "\u0000" + (answerSignature == null ? "" : answerSignature);
    }

    private static boolean sameAnswerSignature(Records.StudyItem left, Records.StudyItem right) {
        String leftSignature = left.answerSignature == null ? "" : left.answerSignature;
        String rightSignature = right.answerSignature == null ? "" : right.answerSignature;
        return leftSignature.equals(rightSignature);
    }

    private static int taskRank(Records.StudyItem item) {
        if (item.writingRemediationPending) {
            return 5;
        }
        return taskRank(recognitionTaskType(item.recognitionStage));
    }

    private static int ladderRank(Records.StudyItem item) {
        if (item.writingRemediationPending) {
            return taskRank(TASK_WRITING_REMEDIATION);
        }
        return taskRank(recognitionTaskType(item.recognitionStage));
    }

    private static int taskRank(String taskType) {
        if (TASK_WRITING_REMEDIATION.equals(taskType)) {
            return 0;
        }
        if (TASK_TYPING_MEANING.equals(taskType)) {
            return 1;
        }
        if (TASK_KANJI_MEANING.equals(taskType)) {
            return 2;
        }
        if (TASK_FONT_MEANING.equals(taskType)) {
            return 3;
        }
        if (TASK_WORD_READING.equals(taskType)) {
            return 4;
        }
        return 2;
    }

    private static boolean dominatesLowerSiblings(String taskType) {
        return TASK_KANJI_MEANING.equals(taskType)
                || TASK_FONT_MEANING.equals(taskType)
                || TASK_WORD_READING.equals(taskType);
    }

    private static int intervalDays(long intervalMillis) {
        return Math.max(0, (int) Math.round((double) intervalMillis / DAY));
    }

    private static String answerSignature(Records.DashboardRow row) {
        if (row == null) {
            return "";
        }
        Records.Example example = null;
        for (Records.Example candidate : row.examples) {
            if ("suspended".equals(candidate.sourceType)) {
                example = candidate;
                break;
            }
            if (example == null && "active".equals(candidate.sourceType)) {
                example = candidate;
            }
        }
        if (example == null && !row.examples.isEmpty()) {
            example = row.examples.get(0);
        }
        String expression = example == null ? "" : example.expression;
        String reading = example == null ? row.reading : example.reading;
        String meaning = example == null ? row.primaryMeaning : example.meaning;
        return normalizeSignature(row.kanji) + "|"
                + normalizeSignature(expression) + "|"
                + normalizeSignature(reading) + "|"
                + normalizeSignature(meaning);
    }

    private static String normalizeSignature(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
