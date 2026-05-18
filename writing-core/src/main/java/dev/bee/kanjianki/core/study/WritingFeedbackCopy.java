package dev.bee.kanjianki.core.study;

public final class WritingFeedbackCopy {
    private static final HintProgression HINT_PROGRESSION = new HintProgression();

    private WritingFeedbackCopy() {
    }

    public static String guideLabel(int level, StrokeGuide guide) {
        return guideLabel(HintState.fromWritingLevel(level), guide);
    }

    public static String guideLabel(HintState state, StrokeGuide guide) {
        HintLevel level = state == null ? HintLevel.TRACE : state.level();
        boolean hasGuide = guide != null && !guide.isEmpty();
        if (!hasGuide) {
            if (level == HintLevel.BLIND) {
                return "Write from memory, then check. No numbered stroke guide is bundled for this kanji yet.";
            }
            return "No numbered stroke guide is bundled for this kanji yet. Use the reference, draw it, then check. Stroke-order feedback will be limited.";
        }
        switch (level) {
            case TRACE:
                return "Trace the numbered strokes, then check. This is a learning attempt.";
            case OUTLINE:
                return "Copy the faint outline; the current stroke is emphasized.";
            case MINIMAL:
                return "Write with only the current stroke hinted, then check.";
            case BLIND:
            default:
                return "Write from memory, then check. Use Hint if you are stuck.";
        }
    }

    public static String attemptProgressText(WritingAnalysis analysis, Integer activeWritingLevel, boolean increaseSupportAfterAnalysis) {
        if (analysis == null) {
            return "";
        }
        if (analysis.status == WritingAnalysis.Status.PASS && analysis.hintsUsed() == 0) {
            HintState next = HINT_PROGRESSION.afterWriting(HintState.fromWritingLevel(analysis.hintLevel().writingLevel()), analysis);
            if (next.level() != analysis.hintLevel()) {
                return "\nNext writing review will have less help: " + stageLabel(next.level()) + ".";
            }
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            return "\nTry cleaner for a cleaner pass, or Save hard to keep this help level.";
        }
        if (increaseSupportAfterAnalysis && activeWritingLevel != null) {
            HintState next = HINT_PROGRESSION.afterWriting(HintState.fromWritingLevel(activeWritingLevel), analysis);
            if (next.level() != HintLevel.fromWritingLevel(activeWritingLevel)) {
                return "\nNext try will use more support: " + stageLabel(next.level()) + ".";
            }
        }
        return "";
    }

    public static String stageLabel(HintLevel level) {
        switch (level) {
            case TRACE:
                return "Trace";
            case OUTLINE:
                return "Outline";
            case MINIMAL:
                return "Minimal";
            case BLIND:
            default:
                return "Blind";
        }
    }

    public static String targetRevealText(WritingAnalysis analysis, String targetKanji) {
        if (targetKanji == null || analysis == null) {
            return "";
        }
        switch (analysis.status) {
            case PASS, CLOSE, WRONG, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
                return "\nTarget: " + targetKanji;
            default:
                return "";
        }
    }
}
