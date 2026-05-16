package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SiblingSuppressionPolicy {
    private static final int MATURE_DAYS_THRESHOLD = 21;

    List<Records.StudyItem> apply(List<Records.StudyItem> items) {
        Map<String, List<Records.StudyItem>> byKanji = new HashMap<>();
        for (Records.StudyItem item : items) {
            byKanji.computeIfAbsent(item.kanji, k -> new ArrayList<>()).add(item);
        }
        List<Records.StudyItem> result = new ArrayList<>(items.size());
        for (Records.StudyItem item : items) {
            List<Records.StudyItem> siblings = byKanji.get(item.kanji);
            Records.StudyItem updated = evaluateSuppression(item, siblings);
            result.add(updated);
        }
        return result;
    }

    private Records.StudyItem evaluateSuppression(Records.StudyItem item, List<Records.StudyItem> siblings) {
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

    private String findDominatingMatureSibling(Records.StudyItem item, List<Records.StudyItem> siblings) {
        Records.LadderRung itemRung = item.rung;
        for (Records.StudyItem sibling : siblings) {
            boolean skip = sibling == item
                    || StudyLadderRules.STATE_RETIRED.equals(sibling.state)
                    || !dominates(sibling.rung, itemRung);
            if (!skip && isMature(sibling)) {
                return sibling.rung.wireName();
            }
        }
        return null;
    }

    private static boolean dominates(Records.LadderRung higher, Records.LadderRung lower) {
        if (higher == Records.LadderRung.WORD_READING) {
            return lower == Records.LadderRung.FONT_MEANING || lower == Records.LadderRung.KANJI_MEANING;
        }
        if (higher == Records.LadderRung.FONT_MEANING) {
            return lower == Records.LadderRung.KANJI_MEANING;
        }
        return false;
    }

    private static boolean isMature(Records.StudyItem item) {
        return item.matureIntervalDays >= MATURE_DAYS_THRESHOLD
                && item.totalReviews > 0
                && item.phase == Records.SchedulerPhase.REVIEW;
    }
}
