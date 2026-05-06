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
}
