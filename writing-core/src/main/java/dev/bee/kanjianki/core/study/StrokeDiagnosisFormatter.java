package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.List;

public final class StrokeDiagnosisFormatter {
    private StrokeDiagnosisFormatter() {
    }

    public static String text(WritingAnalysis analysis) {
        if (!canShow(analysis)) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (StrokeDiagnosis.Entry entry : analysis.strokeOrder.diagnosis.entries) {
            String line = line(entry);
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    public static boolean canShow(WritingAnalysis analysis) {
        if (analysis == null
                || analysis.strokeOrder == null
                || analysis.strokeOrder.missingGuide
                || analysis.strokeOrder.diagnosis.isEmpty()) {
            return false;
        }
        switch (analysis.status) {
            case NO_INK, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
                return false;
            default:
                return true;
        }
    }

    public static String line(StrokeDiagnosis.Entry entry) {
        if (entry == null || entry.label == null) {
            return "";
        }
        return switch (entry.label) {
            case WRONG_ORDER -> strokeLine(entry, "likely wrong order");
            case WRONG_DIRECTION -> strokeLine(entry, "likely wrong direction");
            case MISSING_STROKE -> strokeLine(entry, "may be missing");
            case ROUGH_SHAPE -> strokeLine(entry, "shape looks rough");
            case RECOGNIZED_BUT_MESSY -> "Recognized, but the stroke path was messy";
        };
    }

    public static String strokeLine(StrokeDiagnosis.Entry entry, String label) {
        if (entry == null) {
            return "";
        }
        return "Stroke " + entry.strokeNumber + ": " + label;
    }
}
