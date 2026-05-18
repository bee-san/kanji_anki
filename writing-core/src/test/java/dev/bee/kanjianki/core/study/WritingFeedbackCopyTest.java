package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WritingFeedbackCopyTest {
    @Test
    public void guideLabelPreservesHintStageCopy() {
        StrokeGuide emptyGuide = new StrokeGuide("裂", Collections.emptyList());
        StrokeGuide guide = guide();

        assertTrue(WritingFeedbackCopy.guideLabel(3, emptyGuide).startsWith("Write from memory"));
        assertTrue(WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), emptyGuide).startsWith("Write from memory"));
        assertTrue(WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), emptyGuide).startsWith("No numbered stroke guide"));
        assertEquals("Trace the numbered strokes, then check. This is a learning attempt.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), guide));
        assertEquals("Copy the faint outline; the current stroke is emphasized.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(1), guide));
        assertEquals("Write with only the current stroke hinted, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(2), guide));
        assertEquals("Write from memory, then check. Use Hint if you are stuck.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), guide));
        assertEquals("Trace the numbered strokes, then check. This is a learning attempt.", WritingFeedbackCopy.guideLabel(null, guide));
    }

    @Test
    public void stageLabelPreservesShortNames() {
        assertEquals("Trace", WritingFeedbackCopy.stageLabel(HintLevel.TRACE));
        assertEquals("Outline", WritingFeedbackCopy.stageLabel(HintLevel.OUTLINE));
        assertEquals("Minimal", WritingFeedbackCopy.stageLabel(HintLevel.MINIMAL));
        assertEquals("Blind", WritingFeedbackCopy.stageLabel(HintLevel.BLIND));
    }

    @Test
    public void attemptProgressTextPreservesHintProgressMessages() {
        assertEquals("", WritingFeedbackCopy.attemptProgressText(null, 3, true));
        assertEquals(
                "\nNext writing review will have less help: Minimal.",
                WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.OUTLINE, 0), null, false)
        );
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.OUTLINE, 1), null, false));
        assertEquals(
                "\nTry cleaner for a cleaner pass, or Save hard to keep this help level.",
                WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0), 3, false)
        );
        assertEquals(
                "\nNext try will use more support: Minimal.",
                WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), 3, true)
        );
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), 3, false));
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), null, true));
    }

    @Test
    public void targetRevealTextPreservesTerminalStatusCopy() {
        assertEquals("", WritingFeedbackCopy.targetRevealText(null, "裂"));
        assertEquals("", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), null));
        assertEquals("", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0), "裂"));
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), "裂"));
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0), "裂"));
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), "裂"));
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0), "裂"));
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0), "裂"));
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0), "裂"));
    }

    @Test
    public void writingActionCopyPreservesButtonAndRatingChoices() {
        assertEquals("Checking...", WritingFeedbackCopy.checkWritingButtonText(true, false));
        assertEquals("Checking...", WritingFeedbackCopy.checkWritingButtonText(true, true));
        assertEquals("Check", WritingFeedbackCopy.checkWritingButtonText(false, false));
        assertEquals("Try cleaner", WritingFeedbackCopy.checkWritingButtonText(false, true));

        assertEquals("Fail", WritingFeedbackCopy.submitLabel(null));
        assertEquals("Fail", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)));
        assertEquals("Save hard", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)));
        assertEquals("Pass", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)));

        assertEquals("again", WritingFeedbackCopy.submitRating(null));
        assertEquals("again", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)));
        assertEquals("hard", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)));
        assertEquals("good", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)));
    }

    @Test
    public void writingActionPolicyPreservesSubmittableAndFallbackStatuses() {
        assertFalse(WritingFeedbackCopy.canSubmitAnalysis(null));
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)));
        assertFalse(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)));

        assertFalse(WritingFeedbackCopy.canManualOverride(null));
        assertFalse(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)));
        assertFalse(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)));

        assertFalse(WritingFeedbackCopy.canPracticeAfterAnalysis(null));
        assertTrue(WritingFeedbackCopy.canPracticeAfterAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)));
        assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(null));
        assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)));
        assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)));
        assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)));
    }

    @Test
    public void learningPanelVisibilityPreservesRecallAndTeachingRules() {
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, true, false, 1));
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0), true, false, 1));
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), true, false, 1));
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), true, false, 1));

        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, false, false, 1));
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(null, false, true, 1));
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, false, true, 3));
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), false, false, 3));
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), false, false, 3));
    }

    private static StrokeGuide guide() {
        return new StrokeGuide("裂", Collections.singletonList(new InkStroke(Arrays.asList(
                new InkPoint(0.1f, 0.2f, 0L),
                new InkPoint(0.3f, 0.4f, 1L)
        ))));
    }

    private static WritingAnalysis analysis(WritingAnalysis.Status status, boolean passed, HintLevel hintLevel, int hintsUsed) {
        return new WritingAnalysis(status, passed ? "good" : "again", passed, status.name(), Collections.emptyList(), null, hintLevel, hintsUsed);
    }
}
