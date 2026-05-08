package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WritingAnalysisEngineTest {
    @Test
    public void exactTargetCandidatePassesWithCleanOrder() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "拉",
                sample(),
                guide(),
                Arrays.asList(new RecognitionCandidate("拉", 0.99f))
        );

        assertTrue(analysis.writingPassed);
        assertTrue(analysis.passed());
    }

    @Test
    public void candidateThatOnlyContainsTargetDoesNotPass() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "拉",
                sample(),
                guide(),
                Arrays.asList(new RecognitionCandidate("拉麺", 0.99f))
        );

        assertFalse(analysis.writingPassed);
        assertTrue(analysis.failed());
    }

    @Test
    public void missingStrokeGuideStillPassesRecognitionOnlyMatch() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "鿃",
                sample(),
                new StrokeGuide("鿃", Collections.emptyList()),
                Arrays.asList(new RecognitionCandidate("鿃", 0.93f))
        );

        assertTrue(analysis.writingPassed);
        assertEquals(WritingAnalysis.Status.CLOSE, analysis.status);
        assertEquals("good", analysis.rating);
        assertTrue(analysis.message.contains("Stroke order could not be checked"));
    }

    @Test
    public void missingStrokeGuideSecondCandidateUsesHardRating() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "鿃",
                sample(),
                new StrokeGuide("鿃", Collections.emptyList()),
                Arrays.asList(new RecognitionCandidate("提", 0.93f), new RecognitionCandidate("鿃", 0.75f))
        );

        assertTrue(analysis.writingPassed);
        assertEquals(WritingAnalysis.Status.CLOSE, analysis.status);
        assertEquals("hard", analysis.rating);
        assertTrue(analysis.message.contains("Recognized as the target kanji, but"));
    }

    @Test
    public void missingStrokeGuideDoesNotPassWrongRecognition() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "鿃",
                sample(),
                new StrokeGuide("鿃", Collections.emptyList()),
                Arrays.asList(new RecognitionCandidate("提", 0.93f))
        );

        assertFalse(analysis.writingPassed);
        assertEquals(WritingAnalysis.Status.NO_STROKE_DATA, analysis.status);
        assertTrue(analysis.message.contains("I could not read that as the target kanji"));
    }

    @Test
    public void analysisCarriesHintContextForRatingCaps() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "拉",
                sample(),
                guide(),
                Arrays.asList(new RecognitionCandidate("拉", 0.99f)),
                HintLevel.OUTLINE,
                2
        );

        assertTrue(analysis.writingPassed);
        assertEquals(HintLevel.OUTLINE, analysis.hintLevel());
        assertEquals(2, analysis.hintsUsed());
    }

    @Test
    public void noInkAndRecognizerFailuresCarryHintContext() {
        WritingAnalysis noInk = WritingAnalysisEngine.noInk(HintLevel.TRACE, 3);
        WritingAnalysis defaultNoInk = WritingAnalysisEngine.noInk();
        WritingAnalysis unavailable = WritingAnalysisEngine.modelUnavailable("offline", HintLevel.OUTLINE, 1);
        WritingAnalysis defaultUnavailable = WritingAnalysisEngine.modelUnavailable("offline");
        WritingAnalysis recognitionError = WritingAnalysisEngine.recognitionError("raw", HintLevel.MINIMAL, 2);
        WritingAnalysis defaultRecognitionError = WritingAnalysisEngine.recognitionError("raw");

        assertEquals(WritingAnalysis.Status.NO_INK, noInk.status);
        assertEquals(HintLevel.TRACE, noInk.hintLevel());
        assertEquals(3, noInk.hintsUsed());
        assertEquals(HintLevel.BLIND, defaultNoInk.hintLevel());
        assertEquals(WritingAnalysis.Status.MODEL_UNAVAILABLE, unavailable.status);
        assertEquals("offline", unavailable.message);
        assertEquals(HintLevel.OUTLINE, unavailable.hintLevel());
        assertEquals(HintLevel.BLIND, defaultUnavailable.hintLevel());
        assertEquals(WritingAnalysis.Status.RECOGNITION_ERROR, recognitionError.status);
        assertEquals("The handwriting checker could not read this attempt. Try once more.", recognitionError.message);
        assertEquals(HintLevel.MINIMAL, recognitionError.hintLevel());
        assertEquals(HintLevel.BLIND, defaultRecognitionError.hintLevel());
    }

    @Test
    public void nullOrBlankRecognitionDoesNotMatchTarget() {
        WritingAnalysis blank = WritingAnalysisEngine.analyze(
                "拉",
                sample(),
                guide(),
                Arrays.asList(new RecognitionCandidate(null, 0.2f))
        );
        WritingAnalysis variationSelector = WritingAnalysisEngine.analyze(
                "拉",
                sample(),
                guide(),
                Arrays.asList(new RecognitionCandidate(" 拉\uFE0F ", 0.99f))
        );
        WritingAnalysis noCandidates = WritingAnalysisEngine.analyze("拉", sample(), guide(), null);
        WritingAnalysis noSample = WritingAnalysisEngine.analyze("拉", null, guide(), Collections.emptyList());

        assertFalse(blank.writingPassed);
        assertEquals(WritingAnalysis.Status.WRONG, blank.status);
        assertTrue(variationSelector.writingPassed);
        assertEquals(WritingAnalysis.Status.WRONG, noCandidates.status);
        assertEquals(WritingAnalysis.Status.NO_INK, noSample.status);
    }

    @Test
    public void badStrokeOrderFailsEvenWhenRecognitionMatches() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "拉",
                extraStrokeSample(),
                guide(),
                Arrays.asList(new RecognitionCandidate("拉", 0.99f))
        );

        assertFalse(analysis.writingPassed);
        assertEquals(WritingAnalysis.Status.WRONG, analysis.status);
        assertFalse(analysis.message.isEmpty());
    }

    private StrokeGuide guide() {
        return new StrokeGuide(
                "拉",
                Arrays.asList(
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.1f, 0), new InkPoint(0.9f, 0.1f, 1))),
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.3f, 0), new InkPoint(0.9f, 0.3f, 1)))
                )
        );
    }

    private WritingSample sample() {
        return new WritingSample(Arrays.asList(
                new InkStroke(Arrays.asList(new InkPoint(10f, 10f, 0), new InkPoint(90f, 10f, 1))),
                new InkStroke(Arrays.asList(new InkPoint(10f, 30f, 0), new InkPoint(90f, 30f, 1)))
        ), 100f, 100f);
    }

    private WritingSample extraStrokeSample() {
        return new WritingSample(Arrays.asList(
                new InkStroke(Arrays.asList(new InkPoint(10f, 10f, 0), new InkPoint(90f, 10f, 1))),
                new InkStroke(Arrays.asList(new InkPoint(10f, 30f, 0), new InkPoint(90f, 30f, 1))),
                new InkStroke(Arrays.asList(new InkPoint(10f, 50f, 0), new InkPoint(90f, 50f, 1))),
                new InkStroke(Arrays.asList(new InkPoint(10f, 70f, 0), new InkPoint(90f, 70f, 1)))
        ), 100f, 100f);
    }
}
