package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.List;

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

    public static String candidateText(List<RecognitionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            values.add(candidates.get(i).text);
        }
        return String.join(", ", values);
    }

    public static String checkWritingButtonText(boolean checkingWriting, boolean messyPass) {
        if (checkingWriting) {
            return "Checking...";
        }
        return messyPass ? "Try cleaner" : "Check";
    }

    public static String submitLabel(WritingAnalysis analysis) {
        if (analysis == null || !analysis.writingPassed) {
            return "Fail";
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            return "Save hard";
        }
        return "Pass";
    }

    public static String submitRating(WritingAnalysis analysis) {
        if (analysis == null || !analysis.writingPassed) {
            return StudyRating.AGAIN.code();
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            return StudyRating.HARD.code();
        }
        return StudyRating.GOOD.code();
    }

    public static boolean canSubmitAnalysis(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case PASS, CLOSE, WRONG, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    public static boolean canManualOverride(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case CLOSE, WRONG, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    public static boolean canPracticeAfterAnalysis(WritingAnalysis analysis) {
        return canManualOverride(analysis);
    }

    public static boolean canReplayAnalysis(WritingAnalysis analysis, boolean hasInk, StrokeGuide guide) {
        if (analysis == null
                || !hasInk
                || guide == null
                || guide.isEmpty()
                || analysis.strokeOrder == null
                || analysis.strokeOrder.missingGuide) {
            return false;
        }
        switch (analysis.status) {
            case NO_INK, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
                return false;
            default:
                return true;
        }
    }

    public static boolean shouldIncreaseSupportAfterAnalysis(WritingAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        switch (analysis.status) {
            case WRONG, NO_STROKE_DATA, RECOGNITION_ERROR:
                return true;
            default:
                return false;
        }
    }

    public static boolean shouldShowLearningPanel(
            WritingAnalysis analysis,
            boolean recallTask,
            boolean teachingTask,
            int currentPracticeLevel
    ) {
        if (recallTask) {
            return analysis != null && analysis.status != WritingAnalysis.Status.NO_INK && !analysis.writingPassed;
        }
        if (analysis == null || analysis.status == WritingAnalysis.Status.NO_INK) {
            return teachingTask && currentPracticeLevel < 3;
        }
        return true;
    }
}
