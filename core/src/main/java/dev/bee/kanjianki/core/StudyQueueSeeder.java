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

    List<RecordsStudyModels.StudyItem> seedQueue(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            RecordsBase.StudyLadderSettings ladder
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

    List<RecordsStudyModels.StudyItem> seedQueue(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            RecordsSchedulerModels.AdaptiveLoadPlan plan,
            RecordsBase.StudyLadderSettings ladder
    ) {
        if (plan == null) {
            return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, ladder);
        }
        List<RecordsImportModels.DashboardRow> admissionRows = plan.allKanjiMode ? rows : rowsForFocus(rows, plan.focusKanji);
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
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            int requestedCount,
            RecordsBase.StudyLadderSettings ladder
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
        for (RecordsImportModels.DashboardRow row : request.admissionRows) {
            String rowKey = rowFamilyKey(row);
            RecordsStudyModels.StudyItem current = state.byFamily.get(rowKey);
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
            RecordsSyncModels.Settings settings,
            RecordsImportModels.DashboardRow row,
            RecordsStudyModels.StudyItem current
    ) {
        return StudyLadderRules.STATE_RETIRED.equals(current.state)
                && row.matureSupportCount < settings.matureSupportThreshold;
    }

    private void admitExtraSeedRow(
            SeedQueueRequest request,
            SeedQueueState state,
            RecordsImportModels.DashboardRow row,
            String rowKey,
            RecordsStudyModels.StudyItem current
    ) {
        RecordsStudyModels.StudyItem admitted = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder);
        if (current != null) {
            state.items.remove(current);
        }
        state.items.add(admitted);
        state.byFamily.put(rowKey, admitted);
        state.activeCount++;
        state.newToday++;
    }

    private List<RecordsStudyModels.StudyItem> seedQueueInternal(SeedQueueRequest request) {
        SeedRowIndex rowIndex = indexSeedRows(request.allRows);
        SeedQueueState state = reconcileExistingItems(request, rowIndex);
        for (RecordsImportModels.DashboardRow row : request.admissionRows) {
            admitSeedRow(request, state, row);
        }
        sortSeedItems(state.items);
        return state.items;
    }

    private void sortSeedItems(List<RecordsStudyModels.StudyItem> items) {
        items.sort(Comparator
                .comparing((RecordsStudyModels.StudyItem item) -> item.state.equals(StudyLadderRules.STATE_RETIRED))
                .thenComparingLong(item -> item.dueAtMillis)
                .thenComparing(item -> item.kanji));
    }

    private List<RecordsImportModels.DashboardRow> rowsForFocus(List<RecordsImportModels.DashboardRow> rows, List<String> focusKanji) {
        Map<String, RecordsImportModels.DashboardRow> byKanji = new HashMap<>();
        for (RecordsImportModels.DashboardRow row : rows) {
            byKanji.put(row.kanji, row);
        }
        List<RecordsImportModels.DashboardRow> out = new ArrayList<>();
        for (String kanji : focusKanji) {
            RecordsImportModels.DashboardRow row = byKanji.get(kanji);
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    private SeedRowIndex indexSeedRows(List<RecordsImportModels.DashboardRow> rows) {
        SeedRowIndex index = new SeedRowIndex();
        for (RecordsImportModels.DashboardRow row : rows) {
            index.rowByFamily.put(rowFamilyKey(row), row);
            List<RecordsImportModels.DashboardRow> familyRows = index.rowsByKanji.get(row.kanji);
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
        for (RecordsStudyModels.StudyItem item : request.existing) {
            RecordsStudyModels.StudyItem current = alignOrRetireSeedItem(request, rowIndex, item);
            state.byFamily.put(familyKey(current), current);
            state.items.add(current);
            state.trackActiveItem(current, request.startOfDayMillis);
        }
        return state;
    }

    private RecordsStudyModels.StudyItem alignOrRetireSeedItem(
            SeedQueueRequest request,
            SeedRowIndex rowIndex,
            RecordsStudyModels.StudyItem item
    ) {
        RecordsImportModels.DashboardRow row = seedRowForItem(rowIndex, item);
        RecordsStudyModels.StudyItem current = row == null
                ? StudyLadderRules.alignRungToLadder(item, request.ladder)
                : alignAnswerSignature(item, row, request.nowMillis, request.ladder);
        if (shouldRetireSeedItem(request.settings, row, item, current)) {
            return retiredCopy(current);
        }
        return current;
    }

    private RecordsImportModels.DashboardRow seedRowForItem(SeedRowIndex rowIndex, RecordsStudyModels.StudyItem item) {
        RecordsImportModels.DashboardRow row = rowIndex.rowByFamily.get(familyKey(item));
        List<RecordsImportModels.DashboardRow> familyRows = rowIndex.rowsByKanji.get(item.kanji);
        if (row != null || familyRows == null || (!item.answerSignature.isEmpty() && familyRows.size() != 1)) {
            return row;
        }
        return familyRows.get(0);
    }

    private boolean shouldRetireSeedItem(
            RecordsSyncModels.Settings settings,
            RecordsImportModels.DashboardRow row,
            RecordsStudyModels.StudyItem original,
            RecordsStudyModels.StudyItem current
    ) {
        return !StudyLadderRules.STATE_RETIRED.equals(original.state)
                && (row == null || (row.matureSupportCount >= settings.matureSupportThreshold && current.totalReviews > 0));
    }

    private void admitSeedRow(SeedQueueRequest request, SeedQueueState state, RecordsImportModels.DashboardRow row) {
        String rowKey = rowFamilyKey(row);
        RecordsStudyModels.StudyItem current = state.byFamily.get(rowKey);
        if (current == null) {
            addNewSeedItemIfRoom(request, state, row, rowKey);
        } else if (canReopenRetiredSeedItem(request, state, row, current)) {
            reopenSeedItem(request, state, row, rowKey, current);
        }
    }

    private void addNewSeedItemIfRoom(
            SeedQueueRequest request,
            SeedQueueState state,
            RecordsImportModels.DashboardRow row,
            String rowKey
    ) {
        if (!state.hasAdmissionRoom(request)) {
            return;
        }
        RecordsStudyModels.StudyItem item = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder);
        state.items.add(item);
        state.byFamily.put(rowKey, item);
        state.activeCount++;
        state.newToday++;
    }

    private boolean canReopenRetiredSeedItem(
            SeedQueueRequest request,
            SeedQueueState state,
            RecordsImportModels.DashboardRow row,
            RecordsStudyModels.StudyItem current
    ) {
        return StudyLadderRules.STATE_RETIRED.equals(current.state)
                && row.matureSupportCount < request.settings.matureSupportThreshold
                && state.hasAdmissionRoom(request);
    }

    private void reopenSeedItem(
            SeedQueueRequest request,
            SeedQueueState state,
            RecordsImportModels.DashboardRow row,
            String rowKey,
            RecordsStudyModels.StudyItem current
    ) {
        RecordsStudyModels.StudyItem reopened = newStudyItem(row.kanji, request.nowMillis, answerSignature(row), request.ladder);
        state.items.remove(current);
        state.items.add(reopened);
        state.byFamily.put(rowKey, reopened);
        state.activeCount++;
        state.newToday++;
    }

    private RecordsStudyModels.StudyItem retiredCopy(RecordsStudyModels.StudyItem item) {
        return item.copyBuilder()
                .state(StudyLadderRules.STATE_RETIRED)
                .activeToken(null)
                .build();
    }

    private RecordsStudyModels.StudyItem newStudyItem(String kanji, long nowMillis, String answerSignature, RecordsBase.StudyLadderSettings ladder) {
        RecordsBase.LadderRung startingRung = StudyLadderRules.safeLadder(ladder).startingRung(false);
        return new RecordsStudyModels.StudyItem(
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
                RecordsStudyModels.TaskMemory.initial(),
                RecordsStudyModels.TaskMemory.initial(),
                RecordsStudyModels.TaskMemory.initial(),
                RecordsStudyModels.TaskMemory.initial(),
                RecordsStudyModels.TaskMemory.initial(),
                startingRung,
                RecordsBase.SchedulerPhase.NEW_LEARNING,
                0,
                0,
                0L,
                false,
                RecordsStudyModels.TaskMemory.initial()
        );
    }

    private RecordsStudyModels.StudyItem alignAnswerSignature(
            RecordsStudyModels.StudyItem item,
            RecordsImportModels.DashboardRow row,
            long nowMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        String signature = answerSignature(row);
        if (item.answerSignature.isEmpty() || signature.equals(item.answerSignature)) {
            return StudyLadderRules.alignRungToLadder(item.withAnswerSignature(signature), ladder);
        }
        boolean retired = StudyLadderRules.STATE_RETIRED.equals(item.state);
        if (retired) {
            return StudyLadderRules.alignRungToLadder(item.copyBuilder().answerSignature(signature).build(), ladder);
        }
        RecordsBase.LadderRung fallbackRung = StudyLadderRules.demoteRung(item.rung, item.hasSimilarKanji, ladder);
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
                .typingMeaningMemory(RecordsStudyModels.TaskMemory.initial())
                .meaningKanjiMemory(RecordsStudyModels.TaskMemory.initial())
                .kanjiMeaningMemory(RecordsStudyModels.TaskMemory.initial())
                .fontMeaningMemory(RecordsStudyModels.TaskMemory.initial())
                .wordReadingMemory(RecordsStudyModels.TaskMemory.initial())
                .writingRemediationMemory(RecordsStudyModels.TaskMemory.initial())
                .similarKanjiMemory(RecordsStudyModels.TaskMemory.initial())
                .rung(fallbackRung)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .realPassStreak(0)
                .realAgainStreak(0)
                .lastRealReviewDueAtMillis(0L)
                .build();
    }

    static List<RecordsImportModels.DashboardRow> sortedAdmissionRows(List<RecordsImportModels.DashboardRow> rows, RecordsSyncModels.Settings settings) {
        List<RecordsImportModels.DashboardRow> out = new ArrayList<>(rows);
        out.sort((left, right) -> compareRowsForNewCardSort(left, right, settings));
        return out;
    }

    static int compareRowsForNewCardSort(
            RecordsImportModels.DashboardRow left,
            RecordsImportModels.DashboardRow right,
            RecordsSyncModels.Settings settings
    ) {
        String mode = settings == null ? RecordsBase.DEFAULT_NEW_CARD_SORT_MODE : settings.newCardSortMode;
        if (RecordsBase.NEW_CARD_SORT_FREQUENCY.equals(mode)) {
            return compareRank(left, right);
        }
        int primary = switch (mode) {
            case RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> compareOptionalDescending(maxDifficulty(left), maxDifficulty(right));
            case RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> compareOptionalAscending(minRetrievability(left), minRetrievability(right));
            case RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> compareWeakness(left, right);
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

    private static int compareWeakness(RecordsImportModels.DashboardRow left, RecordsImportModels.DashboardRow right) {
        int weakness = Integer.compare(rowWeakness(right), rowWeakness(left));
        if (weakness != 0) {
            return weakness;
        }
        return Integer.compare(rowSuspendedExamples(right), rowSuspendedExamples(left));
    }

    private static int compareRank(RecordsImportModels.DashboardRow left, RecordsImportModels.DashboardRow right) {
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

    private static int rankSortValue(RecordsImportModels.DashboardRow row) {
        return row == null || row.jitenRank == null ? Integer.MAX_VALUE : row.jitenRank;
    }

    private static int rowWeakness(RecordsImportModels.DashboardRow row) {
        return row == null ? 0 : row.weaknessScore;
    }

    private static int rowSuspendedExamples(RecordsImportModels.DashboardRow row) {
        return row == null ? 0 : row.suspendedExampleCount;
    }

    private static String rowKanji(RecordsImportModels.DashboardRow row) {
        return row == null ? "" : row.kanji;
    }

    private static Double maxDifficulty(RecordsImportModels.DashboardRow row) {
        if (row == null) {
            return null;
        }
        Double best = null;
        for (RecordsImportModels.Example example : row.examples) {
            if (example.fsrsDifficulty != null && Double.isFinite(example.fsrsDifficulty)) {
                best = best == null ? example.fsrsDifficulty : Math.max(best, example.fsrsDifficulty);
            }
        }
        return best;
    }

    private static Double minRetrievability(RecordsImportModels.DashboardRow row) {
        if (row == null) {
            return null;
        }
        Double lowest = null;
        for (RecordsImportModels.Example example : row.examples) {
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

    static String familyKey(RecordsStudyModels.StudyItem item) {
        return familyKey(item.kanji, item.answerSignature);
    }

    static String rowFamilyKey(RecordsImportModels.DashboardRow row) {
        return familyKey(row.kanji, answerSignature(row));
    }

    private static String familyKey(String kanji, String answerSignature) {
        return kanji + "\u0000" + Objects.requireNonNullElse(answerSignature, "");
    }

    static String answerSignature(RecordsImportModels.DashboardRow row) {
        RecordsImportModels.Example example = null;
        for (RecordsImportModels.Example candidate : row.examples) {
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

        int activeQueueCap(RecordsSyncModels.Settings settings) {
            return allKanjiMode ? Integer.MAX_VALUE : settings.activeQueueCap;
        }

        int admissionLimit() {
            return allKanjiMode ? Integer.MAX_VALUE : Math.max(0, newAdmissionLimit);
        }
    }

    private static final class SeedQueueRequest {
        final List<RecordsImportModels.DashboardRow> allRows;
        final List<RecordsImportModels.DashboardRow> admissionRows;
        final List<RecordsStudyModels.StudyItem> existing;
        final RecordsSyncModels.Settings settings;
        final long nowMillis;
        final long startOfDayMillis;
        final SeedQueueLimits limits;
        final RecordsBase.StudyLadderSettings ladder;

        SeedQueueRequest(
                List<RecordsImportModels.DashboardRow> allRows,
                List<RecordsImportModels.DashboardRow> admissionRows,
                List<RecordsStudyModels.StudyItem> existing,
                RecordsSyncModels.Settings settings,
                long nowMillis,
                long startOfDayMillis,
                SeedQueueLimits limits,
                RecordsBase.StudyLadderSettings ladder
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
        final Map<String, RecordsImportModels.DashboardRow> rowByFamily = new HashMap<>();
        final Map<String, List<RecordsImportModels.DashboardRow>> rowsByKanji = new HashMap<>();
    }

    private static final class SeedQueueState {
        final Map<String, RecordsStudyModels.StudyItem> byFamily = new HashMap<>();
        final List<RecordsStudyModels.StudyItem> items = new ArrayList<>();
        int activeCount;
        int newToday;

        void trackActiveItem(RecordsStudyModels.StudyItem item, long startOfDayMillis) {
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
