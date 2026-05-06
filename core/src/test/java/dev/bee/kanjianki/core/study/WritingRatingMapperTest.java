package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WritingRatingMapperTest {
    @Test
    public void failedRequiredWritingCapsRequestedRatingToAgain() {
        WritingRatingMapper mapper = new WritingRatingMapper();
        WritingAnalysis failed = new WritingAnalysis(
                WritingAnalysis.Status.WRONG,
                "again",
                false,
                "wrong",
                Collections.emptyList(),
                null
        );

        StudyRating applied = mapper.applyRequestedRating(StudyRating.EASY, true, failed, false);

        assertFalse(failed.passed());
        assertEquals(StudyRating.AGAIN, applied);
    }

    @Test
    public void manualOverrideKeepsRequestedRatingWhenAnalysisFails() {
        WritingRatingMapper mapper = new WritingRatingMapper();
        WritingAnalysis failed = new WritingAnalysis(WritingAnalysis.Status.WRONG, "again", false, "wrong", Collections.emptyList(), null);

        StudyRating applied = mapper.applyRequestedRating(StudyRating.GOOD, true, failed, true);

        assertEquals(StudyRating.GOOD, applied);
    }

    @Test
    public void blindHighConfidenceWritingCanBeEasy() {
        WritingRatingMapper mapper = new WritingRatingMapper();
        WritingAnalysis blind = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "easy",
                true,
                "clean",
                Arrays.asList(new RecognitionCandidate("拉", 0.98f)),
                cleanStrokeOrder()
        );

        assertEquals(StudyRating.EASY, mapper.maxAllowedRating(blind));
        assertEquals(StudyRating.EASY, mapper.suggestedRating(blind));
    }

    @Test
    public void traceOrHintAssistedWritingCapsAtHard() {
        WritingRatingMapper mapper = new WritingRatingMapper();
        WritingAnalysis trace = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "easy",
                true,
                "clean",
                Arrays.asList(new RecognitionCandidate("拉", 0.99f)),
                cleanStrokeOrder(),
                HintLevel.TRACE,
                0
        );
        WritingAnalysis hintedBlind = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "easy",
                true,
                "clean",
                Arrays.asList(new RecognitionCandidate("拉", 0.99f)),
                cleanStrokeOrder(),
                HintLevel.BLIND,
                1
        );

        assertEquals(StudyRating.HARD, mapper.applyRequestedRating(StudyRating.EASY, true, trace, false));
        assertEquals(StudyRating.HARD, mapper.applyRequestedRating(StudyRating.EASY, true, hintedBlind, false));
    }

    @Test
    public void outlineWritingCapsEasyAtGood() {
        WritingRatingMapper mapper = new WritingRatingMapper();
        WritingAnalysis outline = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "easy",
                true,
                "clean",
                Arrays.asList(new RecognitionCandidate("拉", 0.99f)),
                cleanStrokeOrder(),
                HintLevel.OUTLINE,
                0
        );

        assertEquals(StudyRating.GOOD, mapper.applyRequestedRating(StudyRating.EASY, true, outline, false));
    }

    @Test
    public void messyRecognizedWritingCapsAtHard() {
        WritingRatingMapper mapper = new WritingRatingMapper();
        WritingAnalysis messy = new WritingAnalysis(
                WritingAnalysis.Status.CLOSE,
                "hard",
                true,
                "messy",
                Arrays.asList(new RecognitionCandidate("拉", 0.99f)),
                cleanStrokeOrder(),
                HintLevel.BLIND,
                0
        );

        assertEquals(StudyRating.HARD, mapper.suggestedRating(messy));
        assertEquals(StudyRating.HARD, mapper.applyRequestedRating(StudyRating.EASY, true, messy, false));
    }

    @Test
    public void recognitionOnlySessionLeavesRatingUnchanged() {
        WritingRatingMapper mapper = new WritingRatingMapper();

        StudyRating applied = mapper.applyRequestedRating(StudyRating.EASY, false, null, false);

        assertEquals(StudyRating.EASY, applied);
        assertEquals(StudyRating.AGAIN, StudyRating.fromCode("unexpected"));
    }

    private StrokeOrderEvaluator.StrokeOrderResult cleanStrokeOrder() {
        StrokeGuide guide = new StrokeGuide("拉", Arrays.asList(
                new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.1f, 0), new InkPoint(0.9f, 0.1f, 1)))
        ));
        WritingSample sample = new WritingSample(Arrays.asList(
                new InkStroke(Arrays.asList(new InkPoint(10f, 10f, 0), new InkPoint(90f, 10f, 1)))
        ), 100f, 100f);
        return StrokeOrderEvaluator.evaluate(guide, sample);
    }
}
