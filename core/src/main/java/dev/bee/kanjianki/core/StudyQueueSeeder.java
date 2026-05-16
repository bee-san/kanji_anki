package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class StudyQueueSeeder {
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            Records.StudyLadderSettings ladder
    ) {
        return seedQueueInternal(new SeedQueueRequest(
                rows,
                rows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                new SeedQueueLimits(settings.newPerDay, false),
                StudyLadderRules.safeLadder(ladder)
        ));
    }

    List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            Records.AdaptiveLoadPlan plan,
            Records.StudyLadderSettings ladder
    ) {
        if (plan == null) {
            return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, ladder);
        }
        List<Records.DashboardRow> admissionRows = plan.allKanjiMode ? rows : rowsForFocus(rows, plan.focusKanji);
        int cappedAdmission = plan.allKanjiMode
                ? plan.newAdmissionLimit
                : Math.min(plan.newAdmissionLimit, settings.newPerDay);
        return seedQueueInternal(new SeedQueueRequest(
                rows,
                admissionRows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                new SeedQueueLimits(cappedAdmission, plan.allKanjiMode),
                StudyLadderRules.safeLadder(ladder)
        ));
    }

    BridgeScheduler.ExtraNewCardsResult seedExtraNewCards(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            int requestedCount,
            Records.StudyLadderSettings ladder
    ) {
        int requested = Math.max(0, requestedCount);
        SeedQueueRequest request = new SeedQueueRequest(
                rows,
                rows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                new SeedQueueLimits(Integer.MAX_VALUE, true),
                StudyLadderRules.safeLadder(ladder)
        );
        SeedRowIndex rowIndex = indexSeedRows(request.allRows);
        SeedQueueState state = reconcileExistingItems(request, rowIndex);
        List<String> admittedKanji = new ArrayList<>();
        int available = 0;
        for (Records.DashboardRow row : request.admissionRows) {
            String rowKey = rowFamilyKey(row);
            Records.StudyItem current = state.byFamily.get(rowKey);
            boolean eligible = current == null || canReopenRetiredExtraSeedItem(request.settings, row, current);
            if (eligible) {
                available++;
                if (admittedKanji.size() < requested) {
                    admitExtraSeedRow(request, state, row, rowKey, current);
                    admittedKanji.add(row.kanji);
                }
            }
        }
        sortSeedItems(state.items);
        return new BridgeScheduler.ExtraNewCardsResult(state.items, admittedKanji, available);
    }

    private boolean canReopenRetiredExtraSeedItem(
            Records.Settings settings,
            Records.DashboardRow row,
            Records.StudyItem current
    ) {
        return StudyLadderRules.STATE_RETIRED.equals(current.state)
                && row.matureSupportCount < settings.matureSupportThreshold;
    }

    private void admitExtraSeedRow(
            SeedQueueRequest request,
            SeedQueueState state,
            Records.DashboardRow row,
            String rowKey,
            Records.StudyItem current
    ) {
        Records.StudyItem admitted = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder);
        if (current != null) {
            state.items.remove(current);
        }
        state.items.add(admitted);
        state.byFamily.put(rowKey, admitted);
        state.activeCount++;
        state.newToday++;
    }

    private List<Records.StudyItem> seedQueueInternal(SeedQueueRequest request) {
        SeedRowIndex rowIndex = indexSeedRows(request.allRows);
        SeedQueueState state = reconcileExistingItems(request, rowIndex);
        for (Records.DashboardRow row : request.admissionRows) {
            admitSeedRow(request, state, row);
        }
        sortSeedItems(state.items);
        return state.items;
    }

    private void sortSeedItems(List<Records.StudyItem> items) {
        items.sort(Comparator
                .comparing((Records.StudyItem item) -> item.state.equals(StudyLadderRules.STATE_RETIRED))
                .thenComparingLong(item -> item.dueAtMillis)
                .thenComparing(item -> item.kanji));
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
        Records.StudyItem current = row == null
                ? StudyLadderRules.alignRungToLadder(item, request.ladder)
                : alignAnswerSignature(item, row, request.nowMillis, request.ladder);
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
        return !StudyLadderRules.STATE_RETIRED.equals(original.state)
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
        Records.StudyItem item = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder);
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
        return StudyLadderRules.STATE_RETIRED.equals(current.state)
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
        Records.StudyItem reopened = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder);
        state.items.remove(current);
        state.items.add(reopened);
        state.byFamily.put(rowKey, reopened);
        state.activeCount++;
        state.newToday++;
    }

    private Records.StudyItem retiredCopy(Records.StudyItem item) {
        return item.copyBuilder()
                .state(StudyLadderRules.STATE_RETIRED)
                .activeToken(null)
                .build();
    }

    private Records.StudyItem newStudyItem(String kanji, long nowMillis, String answerSignature, Records.StudyLadderSettings ladder) {
        Records.LadderRung startingRung = StudyLadderRules.safeLadder(ladder).startingRung(false);
        return new Records.StudyItem(
                kanji,
                StudyLadderRules.STATE_NEW,
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
                nowMillis,
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                startingRung,
                Records.SchedulerPhase.NEW_LEARNING,
                0,
                0,
                0L,
                false,
                Records.TaskMemory.initial()
        );
    }

    private Records.StudyItem alignAnswerSignature(
            Records.StudyItem item,
            Records.DashboardRow row,
            long nowMillis,
            Records.StudyLadderSettings ladder
    ) {
        String signature = answerSignature(row);
        if (item.answerSignature.isEmpty() || signature.equals(item.answerSignature)) {
            return StudyLadderRules.alignRungToLadder(item.withAnswerSignature(signature), ladder);
        }
        boolean retired = StudyLadderRules.STATE_RETIRED.equals(item.state);
        if (retired) {
            return StudyLadderRules.alignRungToLadder(item.copyBuilder().answerSignature(signature).build(), ladder);
        }
        Records.LadderRung fallbackRung = StudyLadderRules.demoteRung(item.rung, item.hasSimilarKanji, ladder);
        return item.copyBuilder()
                .state(StudyLadderRules.STATE_LEARNING)
                .dueAtMillis(nowMillis)
                .stability(0.4)
                .difficulty(5.0)
                .totalReviews(0)
                .lapses(0)
                .learningStep(0)
                .consecutiveFailedRecognitionDays(0)
                .lastFailedRecognitionDayMillis(0L)
                .writingRemediationPending(false)
                .suppressedByTaskType(null)
                .suppressedAtMillis(0L)
                .matureIntervalDays(0)
                .answerSignature(signature)
                .activeToken(null)
                .typingMeaningMemory(Records.TaskMemory.initial())
                .kanjiMeaningMemory(Records.TaskMemory.initial())
                .fontMeaningMemory(Records.TaskMemory.initial())
                .wordReadingMemory(Records.TaskMemory.initial())
                .writingRemediationMemory(Records.TaskMemory.initial())
                .similarKanjiMemory(Records.TaskMemory.initial())
                .rung(fallbackRung)
                .phase(Records.SchedulerPhase.NEW_LEARNING)
                .realPassStreak(0)
                .realAgainStreak(0)
                .lastRealReviewDueAtMillis(0L)
                .build();
    }

    static List<Records.DashboardRow> sortedAdmissionRows(List<Records.DashboardRow> rows, Records.Settings settings) {
        List<Records.DashboardRow> out = new ArrayList<>(rows);
        out.sort((left, right) -> compareRowsForNewCardSort(left, right, settings));
        return out;
    }

    static int compareRowsForNewCardSort(
            Records.DashboardRow left,
            Records.DashboardRow right,
            Records.Settings settings
    ) {
        String mode = settings == null ? Records.DEFAULT_NEW_CARD_SORT_MODE : settings.newCardSortMode;
        if (Records.NEW_CARD_SORT_FREQUENCY.equals(mode)) {
            return compareRank(left, right);
        }
        int primary = switch (mode) {
            case Records.NEW_CARD_SORT_FSRS_DIFFICULTY -> compareOptionalDescending(maxDifficulty(left), maxDifficulty(right));
            case Records.NEW_CARD_SORT_RETRIEVABILITY_RISK -> compareOptionalAscending(minRetrievability(left), minRetrievability(right));
            case Records.NEW_CARD_SORT_KANI_WEAKNESS -> compareWeakness(left, right);
            default -> compareRank(left, right);
        };
        if (primary != 0) {
            return primary;
        }
        int rank = compareRank(left, right);
        if (rank != 0) {
            return rank;
        }
        return rowKanji(left).compareTo(rowKanji(right));
    }

    private static int compareWeakness(Records.DashboardRow left, Records.DashboardRow right) {
        int weakness = Integer.compare(rowWeakness(right), rowWeakness(left));
        if (weakness != 0) {
            return weakness;
        }
        return Integer.compare(rowSuspendedExamples(right), rowSuspendedExamples(left));
    }

    private static int compareRank(Records.DashboardRow left, Records.DashboardRow right) {
        return Integer.compare(rankSortValue(left), rankSortValue(right));
    }

    private static int compareOptionalDescending(Double left, Double right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Double.compare(right, left);
    }

    private static int compareOptionalAscending(Double left, Double right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Double.compare(left, right);
    }

    private static int rankSortValue(Records.DashboardRow row) {
        return row == null || row.jitenRank == null ? Integer.MAX_VALUE : row.jitenRank;
    }

    private static int rowWeakness(Records.DashboardRow row) {
        return row == null ? 0 : row.weaknessScore;
    }

    private static int rowSuspendedExamples(Records.DashboardRow row) {
        return row == null ? 0 : row.suspendedExampleCount;
    }

    private static String rowKanji(Records.DashboardRow row) {
        return row == null ? "" : row.kanji;
    }

    private static Double maxDifficulty(Records.DashboardRow row) {
        if (row == null) {
            return null;
        }
        Double best = null;
        for (Records.Example example : row.examples) {
            if (example.fsrsDifficulty != null && Double.isFinite(example.fsrsDifficulty)) {
                best = best == null ? example.fsrsDifficulty : Math.max(best, example.fsrsDifficulty);
            }
        }
        return best;
    }

    private static Double minRetrievability(Records.DashboardRow row) {
        if (row == null) {
            return null;
        }
        Double lowest = null;
        for (Records.Example example : row.examples) {
            Double normalized = normalizedRetrievability(example.fsrsRetrievability);
            if (normalized != null) {
                lowest = lowest == null ? normalized : Math.min(lowest, normalized);
            }
        }
        return lowest;
    }

    private static Double normalizedRetrievability(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0.0) {
            return null;
        }
        if (value > 1.0 && value <= 100.0) {
            return value / 100.0;
        }
        return value > 1.0 ? null : value;
    }

    static String familyKey(Records.StudyItem item) {
        return familyKey(item.kanji, item.answerSignature);
    }

    static String rowFamilyKey(Records.DashboardRow row) {
        return familyKey(row.kanji, answerSignature(row));
    }

    private static String familyKey(String kanji, String answerSignature) {
        return kanji + "\u0000" + Objects.requireNonNullElse(answerSignature, "");
    }

    static String answerSignature(Records.DashboardRow row) {
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
        return MULTI_WHITESPACE.matcher(Objects.requireNonNullElse(value, "").trim()).replaceAll(" ");
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
        final Records.StudyLadderSettings ladder;

        SeedQueueRequest(
                List<Records.DashboardRow> allRows,
                List<Records.DashboardRow> admissionRows,
                List<Records.StudyItem> existing,
                Records.Settings settings,
                long nowMillis,
                long startOfDayMillis,
                SeedQueueLimits limits,
                Records.StudyLadderSettings ladder
        ) {
            this.allRows = allRows;
            this.admissionRows = sortedAdmissionRows(admissionRows, settings);
            this.existing = existing;
            this.settings = settings;
            this.nowMillis = nowMillis;
            this.startOfDayMillis = startOfDayMillis;
            this.limits = limits;
            this.ladder = StudyLadderRules.safeLadder(ladder);
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
            if (StudyLadderRules.STATE_RETIRED.equals(item.state)) {
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
}
