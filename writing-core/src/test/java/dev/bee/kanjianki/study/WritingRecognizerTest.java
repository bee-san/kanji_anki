package dev.bee.kanjianki.study;

import dev.bee.kanjianki.core.study.RecognitionCandidate;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class WritingRecognizerTest {
    @Test
    public void modelStatusStoresDownloadState() {
        WritingRecognizer.ModelStatus status = new WritingRecognizer.ModelStatus(
                "Digital Ink",
                "ja-JP",
                true,
                "Ready"
        );

        assertEquals("Digital Ink", status.modelName);
        assertEquals("ja-JP", status.languageTag);
        assertTrue(status.downloaded);
        assertEquals("Ready", status.message);
    }

    @Test
    public void recognitionResultAndCandidateHandleEmptyMlKitOutputSafely() {
        WritingRecognizer.RecognitionResult empty = new WritingRecognizer.RecognitionResult(Collections.emptyList());
        WritingRecognizer.Candidate candidate = new WritingRecognizer.Candidate(null, 0.4f);

        assertEquals("", empty.topText());
        assertEquals("", candidate.text);
        assertEquals(Float.valueOf(0.4f), candidate.score);
    }

    @Test
    public void recognitionResultCopiesAndFreezesCandidates() {
        List<WritingRecognizer.Candidate> source = new ArrayList<>();
        source.add(new WritingRecognizer.Candidate("校", 0.61f));

        WritingRecognizer.RecognitionResult result = new WritingRecognizer.RecognitionResult(source);
        source.add(new WritingRecognizer.Candidate("拉", 0.94f));

        assertEquals("校", result.topText());
        assertEquals(1, result.candidates.size());
        WritingRecognizer.Candidate appendedCandidate = new WritingRecognizer.Candidate("雑", null);
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.candidates.add(appendedCandidate)
        );
    }

    @Test
    public void recognitionResultReturnsFirstCandidateText() {
        WritingRecognizer.RecognitionResult result = new WritingRecognizer.RecognitionResult(
                Arrays.asList(
                        new WritingRecognizer.Candidate("校", 0.61f),
                        new WritingRecognizer.Candidate("拉", 0.94f)
                )
        );

        assertEquals("校", result.topText());
    }

    @Test
    public void recognitionCandidatesConvertToAnalysisCandidates() {
        WritingRecognizer.RecognitionResult result = new WritingRecognizer.RecognitionResult(
                Arrays.asList(
                        new WritingRecognizer.Candidate("校", 0.61f),
                        new WritingRecognizer.Candidate("拉", null)
                )
        );

        List<RecognitionCandidate> candidates = WritingRecognizer.recognitionCandidates(result);
        List<RecognitionCandidate> nullResult = WritingRecognizer.recognitionCandidates(null);

        assertEquals(2, candidates.size());
        assertEquals("校", candidates.get(0).text);
        assertEquals(Float.valueOf(0.61f), candidates.get(0).score);
        assertEquals("拉", candidates.get(1).text);
        assertNull(candidates.get(1).score);
        assertEquals(0, nullResult.size());
    }
}
