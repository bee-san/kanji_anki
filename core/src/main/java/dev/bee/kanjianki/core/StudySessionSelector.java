package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StudySessionSelector {
    RecordsSchedulerModels.StudySession nextSession(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            RecordsSyncModels.Settings settings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        RecordsBase.StudyLadderSettings safeLadder = StudyLadderRules.safeLadder(ladder);
        long horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis);
        Map<String, RecordsImportModels.DashboardRow> rowByKanji = new HashMap<>();
        for (RecordsImportModels.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
        }
        RecordsStudyModels.StudyItem best = null;
        for (RecordsStudyModels.StudyItem item : activeQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, safeLadder)) {
            if (item.dueAtMillis > horizon) {
                continue;
            }
            if (best == null || compareDueItems(item, best, rowByKanji, settings) < 0) {
                best = item;
            }
        }
        if (best == null) {
            return null;
        }
        RecordsImportModels.DashboardRow row = rowByKanji.get(best.kanji);
        String token = StudyTokenPolicy.studyItem(best.kanji, best.activeToken);
        String taskType = StudyTaskTypes.forRung(best.rung);
        boolean writingRequired = best.rung == RecordsBase.LadderRung.WRITE_KANJI;
        String prompt = row.reasonText;
        return new RecordsSchedulerModels.StudySession(best.withToken(token), row, token, taskType, writingRequired, prompt);
    }

    int dueCount(List<RecordsStudyModels.StudyItem> items, long nowMillis, long studyAheadMillis) {
        long horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis);
        int count = 0;
        for (RecordsStudyModels.StudyItem item : items) {
            if (!StudyLadderRules.STATE_RETIRED.equals(item.state) && item.dueAtMillis <= horizon) {
                count++;
            }
        }
        return count;
    }

    int dueCount(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        long horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis);
        int count = 0;
        for (RecordsStudyModels.StudyItem item : activeQueueItems(items, rows, nowMillis, studyAheadMillis, null, ladder)) {
            if (item.dueAtMillis <= horizon) {
                count++;
            }
        }
        return count;
    }

    List<RecordsStudyModels.StudyItem> activeQueueItems(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            RecordsBase.StudyLadderSettings ladder
    ) {
        RecordsBase.StudyLadderSettings safeLadder = StudyLadderRules.safeLadder(ladder);
        long horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis);
        Set<String> currentRows = new HashSet<>();
        Set<String> currentFamilies = new HashSet<>();
        for (RecordsImportModels.DashboardRow row : rows) {
            currentRows.add(row.kanji);
            currentFamilies.add(StudyQueueSeeder.rowFamilyKey(row));
        }
        Map<String, List<RecordsStudyModels.StudyItem>> byFamily = new HashMap<>();
        for (RecordsStudyModels.StudyItem item : items) {
            RecordsStudyModels.StudyItem effective = StudyLadderRules.alignRungToLadder(item, safeLadder);
            if (isActiveQueueCandidate(effective, currentRows, currentFamilies, allowedKanji)) {
                addFamilyItem(byFamily, effective);
            }
        }
        List<RecordsStudyModels.StudyItem> out = new ArrayList<>();
        for (List<RecordsStudyModels.StudyItem> family : byFamily.values()) {
            out.add(activeFamilyItem(family, horizon, safeLadder));
        }
        return out;
    }

    private boolean isActiveQueueCandidate(
            RecordsStudyModels.StudyItem item,
            Set<String> currentRows,
            Set<String> currentFamilies,
            Set<String> allowedKanji
    ) {
        return !StudyLadderRules.STATE_RETIRED.equals(item.state)
                && item.suppressedByTaskType.isEmpty()
                && (allowedKanji == null || allowedKanji.contains(item.kanji))
                && hasCurrentQueueRow(item, currentRows, currentFamilies);
    }

    private boolean hasCurrentQueueRow(
            RecordsStudyModels.StudyItem item,
            Set<String> currentRows,
            Set<String> currentFamilies
    ) {
        return currentFamilies.contains(StudyQueueSeeder.familyKey(item))
                || (item.answerSignature.isEmpty() && currentRows.contains(item.kanji));
    }

    private void addFamilyItem(Map<String, List<RecordsStudyModels.StudyItem>> byFamily, RecordsStudyModels.StudyItem item) {
        String itemFamilyKey = StudyQueueSeeder.familyKey(item);
        byFamily.computeIfAbsent(itemFamilyKey, ignored -> new ArrayList<>()).add(item);
    }

    private static int compareDueItems(
            RecordsStudyModels.StudyItem left,
            RecordsStudyModels.StudyItem right,
            Map<String, RecordsImportModels.DashboardRow> rowByKanji,
            RecordsSyncModels.Settings settings
    ) {
        int priority = Integer.compare(duePriority(left), duePriority(right));
        if (priority != 0) {
            return priority;
        }
        int due = Long.compare(left.dueAtMillis, right.dueAtMillis);
        if (due != 0) {
            return due;
        }
        if (isUnseenNewItem(left) && isUnseenNewItem(right)) {
            int newCardSort = StudyQueueSeeder.compareRowsForNewCardSort(
                    rowByKanji.get(left.kanji),
                    rowByKanji.get(right.kanji),
                    settings
            );
            if (newCardSort != 0) {
                return newCardSort;
            }
        }
        int weakness = Integer.compare(rowWeakness(right, rowByKanji), rowWeakness(left, rowByKanji));
        if (weakness != 0) {
            return weakness;
        }
        return left.kanji.compareTo(right.kanji);
    }

    private static boolean isUnseenNewItem(RecordsStudyModels.StudyItem item) {
        return item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING && item.totalReviews == 0;
    }

    private static int duePriority(RecordsStudyModels.StudyItem item) {
        if (item.rung == RecordsBase.LadderRung.WRITE_KANJI || item.phase == RecordsBase.SchedulerPhase.RELEARNING) {
            return 0;
        }
        if (item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING) {
            return item.totalReviews > 0 ? 0 : 2;
        }
        return 1;
    }

    private static int rowWeakness(RecordsStudyModels.StudyItem item, Map<String, RecordsImportModels.DashboardRow> rowByKanji) {
        RecordsImportModels.DashboardRow row = rowByKanji.get(item.kanji);
        return row == null ? 0 : row.weaknessScore;
    }

    private static RecordsStudyModels.StudyItem activeFamilyItem(
            List<RecordsStudyModels.StudyItem> family,
            long nowMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        RecordsStudyModels.StudyItem best = null;
        for (RecordsStudyModels.StudyItem item : family) {
            if (best == null || compareFamilyActivity(item, best, nowMillis, ladder) < 0) {
                best = item;
            }
        }
        return best;
    }

    private static int compareFamilyActivity(
            RecordsStudyModels.StudyItem left,
            RecordsStudyModels.StudyItem right,
            long nowMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        RecordsBase.StudyLadderSettings safeLadder = StudyLadderRules.safeLadder(ladder);
        int rank = Integer.compare(-safeLadder.rankForRung(left.rung), -safeLadder.rankForRung(right.rung));
        if (rank != 0) {
            return rank;
        }
        int due = Integer.compare(left.dueAtMillis <= nowMillis ? 0 : 1, right.dueAtMillis <= nowMillis ? 0 : 1);
        if (due != 0) {
            return due;
        }
        return Long.compare(left.dueAtMillis, right.dueAtMillis);
    }
}
