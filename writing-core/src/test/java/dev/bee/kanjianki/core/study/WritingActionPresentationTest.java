package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WritingActionPresentationTest {
    @Test
    public void emptyWritingStateShowsPrimaryPracticeActions() {
        WritingActionPresentation.Input input = new WritingActionPresentation.Input(null);
        input.canUndoStroke = true;
        input.canRevealMoreHelp = true;
        input.teachingTask = true;
        input.currentPracticeLevel = 1;

        WritingActionPresentation presentation = WritingActionPresentation.from(input);

        assertFalse(presentation.hasResult);
        assertTrue(presentation.checkVisible);
        assertTrue(presentation.checkEnabled);
        assertEquals("Check", presentation.checkText);
        assertTrue(presentation.undoEnabled);
        assertTrue(presentation.downloadVisible);
        assertFalse(presentation.nextVisible);
        assertFalse(presentation.manualOverrideVisible);
        assertFalse(presentation.practiceWithGuideVisible);
        assertFalse(presentation.replayVisible);
        assertTrue(presentation.hintVisible);
        assertEquals("More help", presentation.hintText);
        assertTrue(presentation.answerPanelVisible);
        assertFalse(presentation.resultStatusVisible);
    }

    @Test
    public void cleanPassCanSubmitWithoutRetryActions() {
        WritingActionPresentation.Input input = new WritingActionPresentation.Input(analysis(WritingAnalysis.Status.PASS, true));
        input.writingModelStatusKnown = true;
        input.writingModelDownloaded = true;
        input.currentPracticeLevel = 3;

        WritingActionPresentation presentation = WritingActionPresentation.from(input);

        assertTrue(presentation.hasResult);
        assertTrue(presentation.passed);
        assertFalse(presentation.messyPass);
        assertFalse(presentation.checkVisible);
        assertFalse(presentation.downloadVisible);
        assertTrue(presentation.nextVisible);
        assertEquals("Pass", presentation.nextLabel);
        assertEquals(StudyRating.GOOD.code(), presentation.nextRating);
        assertFalse(presentation.manualOverrideVisible);
        assertFalse(presentation.practiceWithGuideVisible);
        assertFalse(presentation.hintVisible);
        assertEquals("Hint", presentation.hintText);
        assertTrue(presentation.answerPanelVisible);
        assertTrue(presentation.resultStatusVisible);
    }

    @Test
    public void closePassKeepsCleanerRetryAndHardSubmitVisible() {
        WritingActionPresentation presentation = WritingActionPresentation.from(
                new WritingActionPresentation.Input(analysis(WritingAnalysis.Status.CLOSE, true))
        );

        assertTrue(presentation.passed);
        assertTrue(presentation.messyPass);
        assertTrue(presentation.checkVisible);
        assertEquals("Try cleaner", presentation.checkText);
        assertTrue(presentation.nextVisible);
        assertEquals("Save hard", presentation.nextLabel);
        assertEquals(StudyRating.HARD.code(), presentation.nextRating);
        assertTrue(presentation.manualOverrideVisible);
        assertFalse(presentation.practiceWithGuideVisible);
    }

    @Test
    public void wrongRecallCanReplayOverridePracticeAndFail() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "拉",
                sample(),
                guide(),
                Collections.singletonList(new RecognitionCandidate("校", 0.8f))
        );
        WritingActionPresentation.Input input = new WritingActionPresentation.Input(analysis);
        input.hasReplaySnapshot = true;
        input.hasInk = true;
        input.guide = guide();
        input.recallTask = true;
        input.canRevealMoreHelp = true;

        WritingActionPresentation presentation = WritingActionPresentation.from(input);

        assertFalse(presentation.passed);
        assertTrue(presentation.checkVisible);
        assertTrue(presentation.nextVisible);
        assertEquals("Fail", presentation.nextLabel);
        assertEquals(StudyRating.AGAIN.code(), presentation.nextRating);
        assertTrue(presentation.manualOverrideVisible);
        assertTrue(presentation.practiceWithGuideVisible);
        assertTrue(presentation.replayVisible);
        assertTrue(presentation.hintVisible);
        assertTrue(presentation.answerPanelVisible);
    }

    @Test
    public void checkingStateDisablesWritingButtons() {
        WritingActionPresentation.Input input = new WritingActionPresentation.Input(null);
        input.checkingWriting = true;
        input.canUndoStroke = true;

        WritingActionPresentation presentation = WritingActionPresentation.from(input);

        assertFalse(presentation.checkEnabled);
        assertEquals("Checking...", presentation.checkText);
        assertFalse(presentation.undoEnabled);
    }

    private static WritingAnalysis analysis(WritingAnalysis.Status status, boolean passed) {
        return new WritingAnalysis(status, StudyRating.GOOD.code(), passed, "message", Collections.emptyList(), null);
    }

    private static WritingSample sample() {
        return new WritingSample(
                Arrays.asList(stroke(10f, 10f, 90f, 10f), stroke(10f, 30f, 90f, 30f)),
                100f,
                100f
        );
    }

    private static StrokeGuide guide() {
        return new StrokeGuide(
                "拉",
                Arrays.asList(stroke(0.1f, 0.1f, 0.9f, 0.1f), stroke(0.1f, 0.3f, 0.9f, 0.3f))
        );
    }

    private static InkStroke stroke(float startX, float startY, float endX, float endY) {
        return new InkStroke(Arrays.asList(new InkPoint(startX, startY, 0), new InkPoint(endX, endY, 1)));
    }
}
