package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FocusQueuePolicy {
    private FocusQueuePolicy() {
    }

    public static List<QueueEntry> queuedEntries(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            long nowMillis,
            long studyAheadMillis,
            RecordsSchedulerModels.AdaptiveLoadPlan plan,
            RecordsBase.StudyLadderSettings ladder
    ) {
        List<RecordsImportModels.DashboardRow> safeRows = rows == null ? Collections.emptyList() : rows;
        Map<String, RecordsImportModels.DashboardRow> rowByKanji = new HashMap<>();
        for (RecordsImportModels.DashboardRow row : safeRows) {
            rowByKanji.put(row.kanji, row);
        }
        Map<String, Integer> focusOrder = focusOrder(plan);
        List<QueueEntry> entries = new ArrayList<>();
        List<RecordsStudyModels.StudyItem> activeItems = new BridgeScheduler().activeQueueItems(
                items == null ? Collections.emptyList() : items,
                safeRows,
                nowMillis,
                studyAheadMillis,
                null,
                ladder
        );
        for (RecordsStudyModels.StudyItem item : activeItems) {
            RecordsImportModels.DashboardRow row = rowByKanji.get(item.kanji);
            if (row != null) {
                entries.add(new QueueEntry(row, item));
            }
        }
        entries.sort(Comparator
                .comparingInt((QueueEntry entry) -> focusOrder.getOrDefault(entry.row.kanji, Integer.MAX_VALUE))
                .thenComparingInt((QueueEntry entry) -> entry.item.dueAtMillis <= nowMillis ? 0 : 1)
                .thenComparingInt(entry -> stateRank(entry.item.state))
                .thenComparingLong(entry -> entry.item.dueAtMillis)
                .thenComparingInt(entry -> -entry.row.weaknessScore)
                .thenComparing(entry -> entry.row.kanji));
        return entries;
    }

    private static Map<String, Integer> focusOrder(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        Map<String, Integer> focusOrder = new HashMap<>();
        if (plan != null) {
            for (int i = 0; i < plan.focusKanji.size(); i++) {
                focusOrder.put(plan.focusKanji.get(i), i);
            }
        }
        return focusOrder;
    }

    public static int stateRank(String state) {
        if (StudyLadderRules.STATE_LEARNING.equals(state)) {
            return 0;
        }
        if (StudyLadderRules.STATE_REVIEW.equals(state)) {
            return 1;
        }
        if (StudyLadderRules.STATE_NEW.equals(state)) {
            return 2;
        }
        return 3;
    }

    public static QueueTone rowTone(RecordsStudyModels.StudyItem item, long nowMillis) {
        if (item != null && item.dueAtMillis <= nowMillis) {
            return QueueTone.DUE;
        }
        if (item != null && StudyLadderRules.STATE_LEARNING.equals(item.state)) {
            return QueueTone.LEARNING;
        }
        return QueueTone.RESTING;
    }

    public enum QueueTone {
        DUE,
        LEARNING,
        RESTING
    }

    public static class QueueEntry {
        public final RecordsImportModels.DashboardRow row;
        public final RecordsStudyModels.StudyItem item;

        public QueueEntry(RecordsImportModels.DashboardRow row, RecordsStudyModels.StudyItem item) {
            this.row = row;
            this.item = item;
        }
    }
}
