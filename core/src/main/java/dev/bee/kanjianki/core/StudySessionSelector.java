package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class StudySessionSelector {
    Records.StudySession nextSession(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            Records.Settings settings,
            Records.StudyLadderSettings ladder
    ) {
        Records.StudyLadderSettings safeLadder = StudyLadderRules.safeLadder(ladder);
        long horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis);
        Map<String, Records.DashboardRow> rowByKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
        }
        Records.StudyItem best = null;
        for (Records.StudyItem item : activeQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, safeLadder)) {
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
        Records.DashboardRow row = rowByKanji.get(best.kanji);
        String token = best.activeToken == null || best.activeToken.isEmpty()
                ? best.kanji + "-" + UUID.randomUUID()
                : best.activeToken;
        String taskType = StudyTaskTypes.forRung(best.rung);
        boolean writingRequired = best.rung == Records.LadderRung.WRITE_KANJI;
        String prompt = row.reasonText;
        return new Records.StudySession(best.withToken(token), row, token, taskType, writingRequired, prompt);
    }

    int dueCount(List<Records.StudyItem> items, long nowMillis, long studyAheadMillis) {
        long horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis);
        int count = 0;
        for (Records.StudyItem item : items) {
            if (!StudyLadderRules.STATE_RETIRED.equals(item.state) && item.dueAtMillis <= horizon) {
                count++;
            }
        }
        return count;
    }

    int dueCount(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Records.StudyLadderSettings ladder
    ) {
        long horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis);
        int count = 0;
        for (Records.StudyItem item : activeQueueItems(items, rows, nowMillis, studyAheadMillis, null, ladder)) {
            if (item.dueAtMillis <= horizon) {
                count++;
            }
        }
        return count;
    }

    List<Records.StudyItem> activeQueueItems(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            Records.StudyLadderSettings ladder
    ) {
        Records.StudyLadderSettings safeLadder = StudyLadderRules.safeLadder(ladder);
        long horizon = nowMillis + StudyLadderRules.clampStudyAheadMillis(studyAheadMillis);
        Set<String> currentRows = new HashSet<>();
        Set<String> currentFamilies = new HashSet<>();
        for (Records.DashboardRow row : rows) {
            currentRows.add(row.kanji);
            currentFamilies.add(StudyQueueSeeder.rowFamilyKey(row));
        }
        Map<String, List<Records.StudyItem>> byFamily = new HashMap<>();
        for (Records.StudyItem item : items) {
            Records.StudyItem effective = StudyLadderRules.alignRungToLadder(item, safeLadder);
            if (isActiveQueueCandidate(effective, currentRows, currentFamilies, allowedKanji)) {
                addFamilyItem(byFamily, effective);
            }
        }
        List<Records.StudyItem> out = new ArrayList<>();
        for (List<Records.StudyItem> family : byFamily.values()) {
            out.add(activeFamilyItem(family, horizon, safeLadder));
        }
        return out;
    }

    private boolean isActiveQueueCandidate(
            Records.StudyItem item,
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
            Records.StudyItem item,
            Set<String> currentRows,
            Set<String> currentFamilies
    ) {
        return currentFamilies.contains(StudyQueueSeeder.familyKey(item))
                || (item.answerSignature.isEmpty() && currentRows.contains(item.kanji));
    }

    private void addFamilyItem(Map<String, List<Records.StudyItem>> byFamily, Records.StudyItem item) {
        String itemFamilyKey = StudyQueueSeeder.familyKey(item);
        byFamily.computeIfAbsent(itemFamilyKey, ignored -> new ArrayList<>()).add(item);
    }

    private static int compareDueItems(
            Records.StudyItem left,
            Records.StudyItem right,
            Map<String, Records.DashboardRow> rowByKanji,
            Records.Settings settings
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

    private static boolean isUnseenNewItem(Records.StudyItem item) {
        return item.phase == Records.SchedulerPhase.NEW_LEARNING && item.totalReviews == 0;
    }

    private static int duePriority(Records.StudyItem item) {
        if (item.rung == Records.LadderRung.WRITE_KANJI || item.phase == Records.SchedulerPhase.RELEARNING) {
            return 0;
        }
        if (item.phase == Records.SchedulerPhase.NEW_LEARNING) {
            return item.totalReviews > 0 ? 0 : 2;
        }
        return 1;
    }

    private static int rowWeakness(Records.StudyItem item, Map<String, Records.DashboardRow> rowByKanji) {
        Records.DashboardRow row = rowByKanji.get(item.kanji);
        return row == null ? 0 : row.weaknessScore;
    }

    private static Records.StudyItem activeFamilyItem(
            List<Records.StudyItem> family,
            long nowMillis,
            Records.StudyLadderSettings ladder
    ) {
        Records.StudyItem best = null;
        for (Records.StudyItem item : family) {
            if (best == null || compareFamilyActivity(item, best, nowMillis, ladder) < 0) {
                best = item;
            }
        }
        return best;
    }

    private static int compareFamilyActivity(
            Records.StudyItem left,
            Records.StudyItem right,
            long nowMillis,
            Records.StudyLadderSettings ladder
    ) {
        Records.StudyLadderSettings safeLadder = StudyLadderRules.safeLadder(ladder);
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
