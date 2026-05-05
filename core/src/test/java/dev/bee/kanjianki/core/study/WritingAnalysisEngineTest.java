package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;

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
