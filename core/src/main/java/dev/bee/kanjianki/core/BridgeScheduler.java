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
        return seedQueueInternal(new SeedQueueRequest(
                rows,
                rows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                new SeedQueueLimits(settings.newPerDay, false)
        ));
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
        return seedQueueInternal(new SeedQueueRequest(
                rows,
                admissionRows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                new SeedQueueLimits(plan.newAdmissionLimit, plan.allKanjiMode)
        ));
    }

    private List<Records.StudyItem> seedQueueInternal(SeedQueueRequest request) {
        SeedRowIndex rowIndex = indexSeedRows(request.allRows);
        SeedQueueState state = reconcileExistingItems(request, rowIndex);
        for (Records.DashboardRow row : request.admissionRows) {
            admitSeedRow(request, state, row);
        }
        state.items.sort(Comparator
                .comparing((Records.StudyItem item) -> item.state.equals(STATE_RETIRED))
                .thenComparingLong(item -> item.dueAtMillis)
                .thenComparing(item -> item.kanji));
        return state.items;
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

    private SeedRowIndex indexSeedRows(List<Records.DashboardRow> rows) {
        SeedRowIndex index = new SeedRowIndex();
        for (Records.DashboardRow row : rows) {
            index.rowByFamily.put(rowFamilyKey(row), row);
            List<Records.DashboardRow> familyRows = index.rowsByKanji.get(row.kanji);
            if (familyRows == null) {
                familyRows = new ArrayList<>();
                index.rowsByKanji.put(row.kanji, familyRows);
            }
            familyRows.add(row);
        }
        return index;
    }

    private SeedQueueState reconcileExistingItems(SeedQueueRequest request, SeedRowIndex rowIndex) {
        SeedQueueState state = new SeedQueueState();
        for (Records.StudyItem item : request.existing) {
            Records.StudyItem current = alignOrRetireSeedItem(request, rowIndex, item);
            state.byFamily.put(familyKey(current), current);
            state.items.add(current);
            state.trackActiveItem(current, request.startOfDayMillis);
        }
        return state;
    }

    private Records.StudyItem alignOrRetireSeedItem(
            SeedQueueRequest request,
            SeedRowIndex rowIndex,
            Records.StudyItem item
    ) {
        Records.DashboardRow row = seedRowForItem(rowIndex, item);
        Records.StudyItem current = row == null ? item : alignAnswerSignature(item, row, request.nowMillis);
        if (shouldRetireSeedItem(request.settings, row, item, current)) {
            return retiredCopy(current);
        }
        return current;
    }

    private Records.DashboardRow seedRowForItem(SeedRowIndex rowIndex, Records.StudyItem item) {
        Records.DashboardRow row = rowIndex.rowByFamily.get(familyKey(item));
        List<Records.DashboardRow> familyRows = rowIndex.rowsByKanji.get(item.kanji);
        if (row != null || familyRows == null || (!item.answerSignature.isEmpty() && familyRows.size() != 1)) {
            return row;
        }
        return familyRows.get(0);
    }

    private boolean shouldRetireSeedItem(
            Records.Settings settings,
            Records.DashboardRow row,
            Records.StudyItem original,
            Records.StudyItem current
    ) {
        return !STATE_RETIRED.equals(original.state)
                && (row == null || (row.matureSupportCount >= settings.matureSupportThreshold && current.totalReviews > 0));
    }

    private void admitSeedRow(SeedQueueRequest request, SeedQueueState state, Records.DashboardRow row) {
        String rowKey = rowFamilyKey(row);
        Records.StudyItem current = state.byFamily.get(rowKey);
        if (current == null) {
            addNewSeedItemIfRoom(request, state, row, rowKey);
        } else if (canReopenRetiredSeedItem(request, state, row, current)) {
            reopenSeedItem(request, state, row, rowKey, current);
        }
    }

    private void addNewSeedItemIfRoom(
            SeedQueueRequest request,
            SeedQueueState state,
            Records.DashboardRow row,
            String rowKey
    ) {
        if (!state.hasAdmissionRoom(request)) {
            return;
        }
        Records.StudyItem item = newStudyItem(row.kanji, request.nowMillis, answerSignature(row));
        state.items.add(item);
        state.byFamily.put(rowKey, item);
        state.activeCount++;
        state.newToday++;
    }

    private boolean canReopenRetiredSeedItem(
            SeedQueueRequest request,
            SeedQueueState state,
            Records.DashboardRow row,
            Records.StudyItem current
    ) {
        return STATE_RETIRED.equals(current.state)
                && row.matureSupportCount < request.settings.matureSupportThreshold
                && state.hasAdmissionRoom(request);
    }

    private void reopenSeedItem(
            SeedQueueRequest request,
            SeedQueueState state,
            Records.DashboardRow row,
            String rowKey,
            Records.StudyItem current
    ) {
        Records.StudyItem reopened = newStudyItem(row.kanji, request.nowMillis, answerSignature(row));
        state.items.remove(current);
        state.items.add(reopened);
        state.byFamily.put(rowKey, reopened);
        state.activeCount++;
        state.newToday++;
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
        Records.SchedulerParameters resolvedParameters = resolveParameters(parameters);
        Records.Settings resolvedSettings = resolveSettings(settings);
        Records.ReviewResult duplicate = duplicateReviewResult(item, request, consumedTokens);
        if (duplicate != null) {
            return duplicate;
        }
        consumedTokens.add(request.token);
        ReviewContext context = ReviewContext.from(item, request, resolvedParameters, resolvedSettings, nowMillis);
        ReviewState state = ReviewState.from(context);
        applyReviewSchedule(context, state);
        updateWritingLevel(context, state);
        updateRecognitionProgress(context, state);
        updateSuppression(context, state);
        return new Records.ReviewResult(updatedStudyItem(context, state), context.rating, false, "Review applied.");
    }

    private Records.SchedulerParameters resolveParameters(Records.SchedulerParameters parameters) {
        return parameters == null ? Records.SchedulerParameters.defaults() : parameters;
    }

    private Records.Settings resolveSettings(Records.Settings settings) {
        return settings == null ? Records.Settings.kikuDefaults() : settings;
    }

    private Records.ReviewResult duplicateReviewResult(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens
    ) {
        if (consumedTokens.contains(request.token)) {
            return new Records.ReviewResult(item, "duplicate", true, "Review token already consumed.");
        }
        if (item.activeToken != null && !item.activeToken.isEmpty() && !item.activeToken.equals(request.token)) {
            return new Records.ReviewResult(item, "duplicate", true, "Review token does not match the active session.");
        }
        return null;
    }

    private void applyReviewSchedule(ReviewContext context, ReviewState state) {
        switch (context.rating) {
            case RATING_AGAIN:
                applyAgainSchedule(context, state);
                break;
            case RATING_HARD:
                applyHardSchedule(context, state);
                break;
            case RATING_EASY:
                applyEasySchedule(context, state);
                break;
            case RATING_GOOD:
            default:
                applyGoodSchedule(context, state);
                break;
        }
    }

    private void applyAgainSchedule(ReviewContext context, ReviewState state) {
        state.lapses++;
        state.taskLapses++;
        state.step = 0;
        state.stability = Math.max(0.2, state.stability * context.parameters.againMultiplier);
        state.difficulty = Math.min(10.0, state.difficulty + 0.7);
        state.due = context.nowMillis + 10 * MINUTE;
        state.scheduledIntervalDays = 0;
        state.state = STATE_LEARNING;
    }

    private void applyHardSchedule(ReviewContext context, ReviewState state) {
        state.step = Math.max(1, state.step);
        state.stability = Math.max(0.5, state.stability * context.parameters.hardMultiplier);
        state.difficulty = Math.min(10.0, state.difficulty + 0.2);
        state.due = context.nowMillis + DAY;
        state.scheduledIntervalDays = 1;
        state.state = STATE_REVIEW;
    }

    private void applyEasySchedule(ReviewContext context, ReviewState state) {
        state.step = 2;
        state.stability = Math.max(2.5, state.stability * context.parameters.easyMultiplier);
        state.difficulty = Math.max(1.0, state.difficulty - 0.35);
        long interval = reviewInterval(state.stability, context.parameters);
        state.scheduledIntervalDays = intervalDays(interval);
        state.due = context.nowMillis + interval;
        state.state = STATE_REVIEW;
    }

    private void applyGoodSchedule(ReviewContext context, ReviewState state) {
        state.step = Math.min(2, state.step + 1);
        state.stability = Math.max(1.0, state.stability * context.parameters.goodMultiplier);
        state.difficulty = Math.max(1.0, state.difficulty - 0.1);
        long interval = state.step < 2 ? 10 * MINUTE : reviewInterval(state.stability, context.parameters);
        state.scheduledIntervalDays = state.step < 2 ? 0 : intervalDays(interval);
        state.due = context.nowMillis + interval;
        state.state = state.step < 2 ? STATE_LEARNING : STATE_REVIEW;
    }

    private void updateWritingLevel(ReviewContext context, ReviewState state) {
        if (context.failedWriting) {
            state.writingLevel = Math.max(0, state.writingLevel - 1);
        } else if (context.cleanWritingPass) {
            state.writingLevel = Math.min(3, state.writingLevel + 1);
        }
    }

    private void updateRecognitionProgress(ReviewContext context, ReviewState state) {
        if (!context.request.writingRequired) {
            updateRecognitionReviewProgress(context, state);
        } else if (context.writingRemediationReview) {
            updateWritingRemediationProgress(context, state);
        }
    }

    private void updateRecognitionReviewProgress(ReviewContext context, ReviewState state) {
        if (RATING_AGAIN.equals(context.rating)) {
            incrementFailedRecognitionDays(context, state);
            triggerWritingOrDemotionIfNeeded(context, state);
        } else {
            incrementRecognitionPasses(context, state);
            promoteRecognitionIfReady(context, state);
            state.failedRecognitionDays = 0;
            state.lastFailedRecognitionDay = 0L;
            state.writingRemediationPending = false;
        }
    }

    private void incrementFailedRecognitionDays(ReviewContext context, ReviewState state) {
        if (context.previousTaskMemory.dueAtMillis <= context.nowMillis
                && (state.failedRecognitionDays <= 0
                || state.lastFailedRecognitionDay != context.previousTaskMemory.dueAtMillis)) {
            state.failedRecognitionDays++;
            state.lastFailedRecognitionDay = context.previousTaskMemory.dueAtMillis;
        }
    }

    private void triggerWritingOrDemotionIfNeeded(ReviewContext context, ReviewState state) {
        if (state.failedRecognitionDays < context.settings.writingTriggerMissDays) {
            return;
        }
        state.failedRecognitionDays = 0;
        state.lastFailedRecognitionDay = 0L;
        if (state.recognitionStage <= MIN_RECOGNITION_STAGE) {
            state.writingRemediationPending = true;
        } else {
            state.recognitionStage = Math.max(MIN_RECOGNITION_STAGE, state.recognitionStage - 1);
            state.writingRemediationPending = false;
        }
    }

    private void incrementRecognitionPasses(ReviewContext context, ReviewState state) {
        if (context.previousTaskMemory.dueAtMillis <= context.nowMillis
                && (state.recognitionPasses <= 0
                || state.lastRecognitionPassDueAt != context.previousTaskMemory.dueAtMillis)) {
            state.recognitionPasses++;
            state.lastRecognitionPassDueAt = context.previousTaskMemory.dueAtMillis;
        }
    }

    private void promoteRecognitionIfReady(ReviewContext context, ReviewState state) {
        if (state.recognitionPasses < context.settings.recognitionPromotionPasses) {
            return;
        }
        state.recognitionStage = Math.min(MAX_RECOGNITION_STAGE, state.recognitionStage + 1);
        state.recognitionPasses = 0;
        state.lastRecognitionPassDueAt = 0L;
    }

    private void updateWritingRemediationProgress(ReviewContext context, ReviewState state) {
        if (context.request.manualOverride || context.request.writingPassed) {
            state.recognitionStage = MIN_RECOGNITION_STAGE;
            state.failedRecognitionDays = 0;
            state.lastFailedRecognitionDay = 0L;
            state.writingRemediationPending = false;
        } else {
            state.writingRemediationPending = true;
        }
    }

    private void updateSuppression(ReviewContext context, ReviewState state) {
        state.suppressedByTaskType = context.item.suppressedByTaskType;
        state.suppressedAtMillis = context.item.suppressedAtMillis;
        state.matureIntervalDays = state.scheduledIntervalDays;
        if (RATING_AGAIN.equals(context.rating)) {
            state.suppressedByTaskType = "";
            state.suppressedAtMillis = 0L;
            state.matureIntervalDays = 0;
        } else if (reviewCreatesMatureSuppression(context, state)) {
            state.suppressedByTaskType = context.reviewedTaskType;
            state.suppressedAtMillis = context.nowMillis;
        }
    }

    private boolean reviewCreatesMatureSuppression(ReviewContext context, ReviewState state) {
        return dominatesLowerSiblings(context.reviewedTaskType)
                && context.previousTaskMemory.totalReviews > 0
                && context.previousTaskMemory.dueAtMillis <= context.nowMillis
                && state.scheduledIntervalDays >= context.settings.matureDays;
    }

    private Records.StudyItem updatedStudyItem(ReviewContext context, ReviewState state) {
        return new Records.StudyItem(
                context.item.kanji,
                state.state,
                state.due,
                round(state.stability),
                round(state.difficulty),
                state.total,
                state.lapses,
                state.step,
                state.writingLevel,
                state.recognitionStage,
                state.failedRecognitionDays,
                state.lastFailedRecognitionDay,
                state.writingRemediationPending,
                state.suppressedByTaskType,
                state.suppressedAtMillis,
                state.matureIntervalDays,
                context.item.answerSignature,
                null,
                context.item.createdAtMillis,
                context.item.typingMeaningMemory,
                context.item.kanjiMeaningMemory,
                context.item.fontMeaningMemory,
                context.item.wordReadingMemory,
                context.item.writingRemediationMemory
        ).withTaskMemory(context.reviewedTaskType, updatedTaskMemory(context, state));
    }

    private Records.TaskMemory updatedTaskMemory(ReviewContext context, ReviewState state) {
        return new Records.TaskMemory(
                state.state,
                state.due,
                round(state.stability),
                round(state.difficulty),
                state.taskTotal,
                state.taskLapses,
                state.step,
                context.rating,
                state.scheduledIntervalDays,
                taskMemoryRecognitionPasses(context, state),
                taskMemoryRecognitionPassDueAt(context, state)
        );
    }

    private int taskMemoryRecognitionPasses(ReviewContext context, ReviewState state) {
        return RATING_AGAIN.equals(context.rating) || context.request.writingRequired ? 0 : state.recognitionPasses;
    }

    private long taskMemoryRecognitionPassDueAt(ReviewContext context, ReviewState state) {
        return RATING_AGAIN.equals(context.rating) || context.request.writingRequired ? 0L : state.lastRecognitionPassDueAt;
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
            if (isActiveQueueCandidate(item, currentRows, currentFamilies, allowedKanji)) {
                addFamilyItem(byFamily, item);
            }
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

    private boolean isActiveQueueCandidate(
            Records.StudyItem item,
            Set<String> currentRows,
            Set<String> currentFamilies,
            Set<String> allowedKanji
    ) {
        return !STATE_RETIRED.equals(item.state)
                && (allowedKanji == null || allowedKanji.contains(item.kanji))
                && hasCurrentQueueRow(item, currentRows, currentFamilies);
    }

    private boolean hasCurrentQueueRow(
            Records.StudyItem item,
            Set<String> currentRows,
            Set<String> currentFamilies
    ) {
        return currentFamilies.contains(familyKey(item))
                || (item.answerSignature.isEmpty() && currentRows.contains(item.kanji));
    }

    private void addFamilyItem(Map<String, List<Records.StudyItem>> byFamily, Records.StudyItem item) {
        String itemFamilyKey = familyKey(item);
        byFamily.computeIfAbsent(itemFamilyKey, ignored -> new ArrayList<>()).add(item);
    }

    public Set<String> tokenSet(List<String> tokens) {
        return new HashSet<>(tokens);
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

    private static final class SeedQueueLimits {
        final int newAdmissionLimit;
        final boolean allKanjiMode;

        SeedQueueLimits(int newAdmissionLimit, boolean allKanjiMode) {
            this.newAdmissionLimit = newAdmissionLimit;
            this.allKanjiMode = allKanjiMode;
        }

        int activeQueueCap(Records.Settings settings) {
            return allKanjiMode ? Integer.MAX_VALUE : settings.activeQueueCap;
        }

        int admissionLimit() {
            return allKanjiMode ? Integer.MAX_VALUE : Math.max(0, newAdmissionLimit);
        }
    }

    private static final class SeedQueueRequest {
        final List<Records.DashboardRow> allRows;
        final List<Records.DashboardRow> admissionRows;
        final List<Records.StudyItem> existing;
        final Records.Settings settings;
        final long nowMillis;
        final long startOfDayMillis;
        final SeedQueueLimits limits;

        SeedQueueRequest(
                List<Records.DashboardRow> allRows,
                List<Records.DashboardRow> admissionRows,
                List<Records.StudyItem> existing,
                Records.Settings settings,
                long nowMillis,
                long startOfDayMillis,
                SeedQueueLimits limits
        ) {
            this.allRows = allRows;
            this.admissionRows = admissionRows;
            this.existing = existing;
            this.settings = settings;
            this.nowMillis = nowMillis;
            this.startOfDayMillis = startOfDayMillis;
            this.limits = limits;
        }
    }

    private static final class SeedRowIndex {
        final Map<String, Records.DashboardRow> rowByFamily = new HashMap<>();
        final Map<String, List<Records.DashboardRow>> rowsByKanji = new HashMap<>();
    }

    private static final class SeedQueueState {
        final Map<String, Records.StudyItem> byFamily = new HashMap<>();
        final List<Records.StudyItem> items = new ArrayList<>();
        int activeCount;
        int newToday;

        void trackActiveItem(Records.StudyItem item, long startOfDayMillis) {
            if (STATE_RETIRED.equals(item.state)) {
                return;
            }
            activeCount++;
            if (item.createdAtMillis >= startOfDayMillis) {
                newToday++;
            }
        }

        boolean hasAdmissionRoom(SeedQueueRequest request) {
            return activeCount < request.limits.activeQueueCap(request.settings)
                    && newToday < request.limits.admissionLimit();
        }
    }

    private static final class ReviewContext {
        Records.StudyItem item;
        Records.ReviewRequest request;
        Records.SchedulerParameters parameters;
        Records.Settings settings;
        Records.TaskMemory previousTaskMemory;
        long nowMillis;
        String rating;
        String reviewedTaskType;
        boolean writingRemediationReview;
        boolean cleanWritingPass;
        boolean failedWriting;

        static ReviewContext from(
                Records.StudyItem item,
                Records.ReviewRequest request,
                Records.SchedulerParameters parameters,
                Records.Settings settings,
                long nowMillis
        ) {
            ReviewContext context = new ReviewContext();
            context.item = item;
            context.request = request;
            context.parameters = parameters;
            context.settings = settings;
            context.nowMillis = nowMillis;
            context.rating = reviewRating(item, request);
            context.reviewedTaskType = reviewedTaskType(item);
            context.previousTaskMemory = item.memoryForTaskType(context.reviewedTaskType);
            context.writingRemediationReview = request.writingRequired && item.writingRemediationPending;
            boolean writingReviewCanMoveHelp = request.writingRequired && !request.manualOverride;
            context.cleanWritingPass = writingReviewCanMoveHelp
                    && request.writingPassed
                    && request.writingClean
                    && request.hintsUsed <= 0;
            context.failedWriting = writingReviewCanMoveHelp && !request.writingPassed;
            return context;
        }

        private static String reviewRating(Records.StudyItem item, Records.ReviewRequest request) {
            if (request.writingRequired && item.writingRemediationPending && request.manualOverride) {
                return RATING_HARD;
            }
            if (request.writingRequired && !request.writingPassed && !request.manualOverride) {
                return RATING_AGAIN;
            }
            return normalizeRating(request.rating);
        }

        private static String normalizeRating(String rating) {
            if (rating == null) {
                return RATING_AGAIN;
            }
            return switch (rating) {
                case RATING_AGAIN, RATING_HARD, RATING_GOOD, RATING_EASY -> rating;
                default -> RATING_AGAIN;
            };
        }

        private static String reviewedTaskType(Records.StudyItem item) {
            return item.writingRemediationPending
                    ? TASK_WRITING_REMEDIATION
                    : recognitionTaskType(item.recognitionStage);
        }
    }

    private static final class ReviewState {
        int total;
        int lapses;
        int taskTotal;
        int taskLapses;
        int step;
        int writingLevel;
        int recognitionStage;
        int failedRecognitionDays;
        int recognitionPasses;
        int scheduledIntervalDays;
        int matureIntervalDays;
        long lastFailedRecognitionDay;
        long lastRecognitionPassDueAt;
        long due;
        long suppressedAtMillis;
        boolean writingRemediationPending;
        double stability;
        double difficulty;
        String state;
        String suppressedByTaskType;

        static ReviewState from(ReviewContext context) {
            ReviewState state = new ReviewState();
            state.total = context.item.totalReviews + 1;
            state.lapses = context.item.lapses;
            state.taskTotal = context.previousTaskMemory.totalReviews + 1;
            state.taskLapses = context.previousTaskMemory.lapses;
            state.step = context.previousTaskMemory.learningStep;
            state.writingLevel = context.item.writingLevel;
            state.recognitionStage = context.item.recognitionStage;
            state.failedRecognitionDays = context.item.consecutiveFailedRecognitionDays;
            state.lastFailedRecognitionDay = context.item.lastFailedRecognitionDayMillis;
            state.recognitionPasses = context.previousTaskMemory.consecutivePasses;
            state.lastRecognitionPassDueAt = context.previousTaskMemory.lastPassedDueAtMillis;
            state.writingRemediationPending = context.item.writingRemediationPending;
            state.stability = context.previousTaskMemory.stability;
            state.difficulty = context.previousTaskMemory.difficulty;
            return state;
        }
    }
}
