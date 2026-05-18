package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FocusQueueCopy {
    private FocusQueueCopy() {
    }

    public static String queueCardBody(RecordsImportModels.DashboardRow row) {
        if (row.reasonText == null || row.reasonText.isEmpty()) {
            return "Needs focused kanji practice.";
        }
        String normalized = row.reasonText.toLowerCase(Locale.ROOT);
        if (normalized.contains("similar-kanji") || normalized.contains("similar kanji") || normalized.contains("similar choice")) {
            return "Shape mix-up made this a writing-practice target.";
        }
        return row.reasonText;
    }

    public static String focusReasonLine(
            RecordsImportModels.DashboardRow row,
            RecordsStudyModels.StudyItem item,
            long nowMillis,
            int matureSupportThreshold
    ) {
        List<String> parts = new ArrayList<>();
        if (row.weaknessScore > 0) {
            parts.add("weakness " + row.weaknessScore);
        }
        if (row.matureSupportCount < matureSupportThreshold) {
            parts.add("support " + row.matureSupportCount + "/" + matureSupportThreshold);
        }
        parts.add(recognitionStageLabel(item));
        if (item.dueAtMillis <= nowMillis) {
            parts.add("due now");
        } else if (StudyLadderRules.STATE_LEARNING.equals(item.state)) {
            parts.add(StudyLadderRules.STATE_LEARNING);
        }
        return "Why: " + String.join(" · ", parts);
    }

    public static String recognitionStageLabel(RecordsStudyModels.StudyItem item) {
        switch (item.rung) {
            case WRITE_KANJI:
                return "write kanji";
            case TYPE_MEANING:
                return "type meaning";
            case SIMILAR_KANJI:
                return "similar kanji";
            case MEANING_KANJI:
                return "meaning -> kanji";
            case FONT_MEANING:
                return "font -> meaning";
            case WORD_READING:
                return "word -> reading";
            case KANJI_MEANING:
            default:
                return "kanji -> meaning";
        }
    }
}
