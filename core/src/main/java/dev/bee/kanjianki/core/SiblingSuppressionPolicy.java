package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SiblingSuppressionPolicy {
    private static final int MATURE_DAYS_THRESHOLD = 21;

    List<RecordsStudyModels.StudyItem> apply(List<RecordsStudyModels.StudyItem> items) {
        Map<String, List<RecordsStudyModels.StudyItem>> byKanji = new HashMap<>();
        for (RecordsStudyModels.StudyItem item : items) {
            byKanji.computeIfAbsent(item.kanji, k -> new ArrayList<>()).add(item);
        }
        List<RecordsStudyModels.StudyItem> result = new ArrayList<>(items.size());
        for (RecordsStudyModels.StudyItem item : items) {
            List<RecordsStudyModels.StudyItem> siblings = byKanji.get(item.kanji);
            RecordsStudyModels.StudyItem updated = evaluateSuppression(item, siblings);
            result.add(updated);
        }
        return result;
    }

    private RecordsStudyModels.StudyItem evaluateSuppression(RecordsStudyModels.StudyItem item, List<RecordsStudyModels.StudyItem> siblings) {
        if (StudyLadderRules.STATE_RETIRED.equals(item.state)) {
            return item;
        }
        String dominator = findDominatingMatureSibling(item, siblings);
        boolean currentlySuppressed = !item.suppressedByTaskType.isEmpty();
        if (dominator != null && !currentlySuppressed) {
            return item.copyBuilder()
                    .suppressedByTaskType(dominator)
                    .suppressedAtMillis(System.currentTimeMillis())
                    .build();
        }
        if (dominator == null && currentlySuppressed) {
            return item.copyBuilder()
                    .suppressedByTaskType(null)
                    .suppressedAtMillis(0L)
                    .build();
        }
        return item;
    }

    private String findDominatingMatureSibling(RecordsStudyModels.StudyItem item, List<RecordsStudyModels.StudyItem> siblings) {
        RecordsBase.LadderRung itemRung = item.rung;
        for (RecordsStudyModels.StudyItem sibling : siblings) {
            boolean skip = sibling == item
                    || StudyLadderRules.STATE_RETIRED.equals(sibling.state)
                    || !dominates(sibling.rung, itemRung);
            if (!skip && isMature(sibling)) {
                return sibling.rung.wireName();
            }
        }
        return null;
    }

    private static boolean dominates(RecordsBase.LadderRung higher, RecordsBase.LadderRung lower) {
        if (higher == RecordsBase.LadderRung.WORD_READING) {
            return lower == RecordsBase.LadderRung.FONT_MEANING || lower == RecordsBase.LadderRung.KANJI_MEANING;
        }
        if (higher == RecordsBase.LadderRung.FONT_MEANING) {
            return lower == RecordsBase.LadderRung.KANJI_MEANING;
        }
        return false;
    }

    private static boolean isMature(RecordsStudyModels.StudyItem item) {
        return item.matureIntervalDays >= MATURE_DAYS_THRESHOLD
                && item.totalReviews > 0
                && item.phase == RecordsBase.SchedulerPhase.REVIEW;
    }
}
