package dev.bee.kanjianki.study;

import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingSample;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class StudyWritingModelsTest {
    @Test
    public void recognitionResultAndCandidateHandleEmptyMlKitOutputSafely() {
        WritingRecognizer.RecognitionResult empty = new WritingRecognizer.RecognitionResult(Collections.emptyList());
        WritingRecognizer.Candidate candidate = new WritingRecognizer.Candidate(null, 0.4f);

        assertEquals("", empty.topText());
        assertEquals("", candidate.text);
        assertEquals(Float.valueOf(0.4f), candidate.score);
    }

    @Test
    public void recognitionCandidatesCanDriveCoreWritingAnalysis() {
        WritingRecognizer.RecognitionResult result = new WritingRecognizer.RecognitionResult(
                Arrays.asList(
                        new WritingRecognizer.Candidate("校", 0.61f),
                        new WritingRecognizer.Candidate(" 拉\uFE0F ", 0.94f)
                )
        );

        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "拉",
                writingSample(),
                strokeGuide(),
                recognitionCandidates(result)
        );

        assertEquals("校", result.topText());
        assertEquals(WritingAnalysis.Status.PASS, analysis.status);
        assertTrue(analysis.writingPassed);
        assertEquals("good", analysis.rating);
        assertEquals(2, analysis.candidates.size());
        assertEquals(" 拉\uFE0F ", analysis.candidates.get(1).text);
    }

    private static List<RecognitionCandidate> recognitionCandidates(WritingRecognizer.RecognitionResult result) {
        List<RecognitionCandidate> candidates = new ArrayList<>();
        for (WritingRecognizer.Candidate candidate : result.candidates) {
            candidates.add(new RecognitionCandidate(candidate.text, candidate.score));
        }
        return candidates;
    }

    private static StrokeGuide strokeGuide() {
        return new StrokeGuide(
                "拉",
                Arrays.asList(
                        inkStroke(0.1f, 0.1f, 0.9f, 0.1f),
                        inkStroke(0.1f, 0.3f, 0.9f, 0.3f)
                )
        );
    }

    private static WritingSample writingSample() {
        return new WritingSample(
                Arrays.asList(
                        inkStroke(10f, 10f, 90f, 10f),
                        inkStroke(10f, 30f, 90f, 30f)
                ),
                100f,
                100f
        );
    }

    private static InkStroke inkStroke(float x1, float y1, float x2, float y2) {
        return new InkStroke(Arrays.asList(new InkPoint(x1, y1, 0), new InkPoint(x2, y2, 1)));
    }
}
