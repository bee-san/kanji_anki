package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class WritingRatingMapperTest {
    @Test
    public void nullWritingInputsFallBackToAgain() {
        WritingRatingMapper mapper = new WritingRatingMapper();

        assertEquals(StudyRating.AGAIN, mapper.applyRequestedRating(null, true, null, false));
        assertEquals(StudyRating.AGAIN, mapper.suggestedRating(null));
        assertEquals(StudyRating.AGAIN, mapper.maxAllowedRating(null));
        assertEquals(StudyRating.AGAIN, StudyRating.fromCode(null));
    }

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
    public void passedLowConfidenceWritingStaysHardAndMediumConfidenceIsGood() {
        WritingRatingMapper mapper = new WritingRatingMapper();
        WritingAnalysis lowConfidence = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "hard",
                true,
                "weak",
                Arrays.asList(new RecognitionCandidate("拉", 0.45f)),
                cleanStrokeOrder(),
                HintLevel.BLIND,
                0
        );
        WritingAnalysis mediumConfidence = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                "ok",
                Arrays.asList(new RecognitionCandidate("拉", 0.50f)),
                cleanStrokeOrder(),
                HintLevel.BLIND,
                0
        );

        assertEquals(StudyRating.HARD, mapper.suggestedRating(lowConfidence));
        assertEquals(StudyRating.HARD, mapper.maxAllowedRating(lowConfidence));
        assertEquals(StudyRating.GOOD, mapper.suggestedRating(mediumConfidence));
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
        assertEquals("easy", StudyRating.EASY.code());
        assertEquals(StudyRating.AGAIN, StudyRating.fromCode("unexpected"));
        assertEquals(StudyRating.GOOD, StudyRating.fromCode("good"));
        assertEquals(StudyRating.GOOD, StudyRating.EASY.cappedAt(StudyRating.GOOD));
        assertEquals(StudyRating.HARD, StudyRating.HARD.cappedAt(StudyRating.GOOD));
    }

    @Test
    public void hintVisibilityNormalizesNullLevelAndNegativeStrokeCount() {
        HintVisibility visibility = new HintVisibility(null, false, false, false, false, false, -4);

        assertEquals(HintLevel.TRACE, visibility.level());
        assertEquals(0, visibility.visibleStrokeCount());
    }

    @Test
    public void writingAnalysisDefaultsConfidenceAndHintOptions() {
        WritingAnalysis passedWithoutScore = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                null,
                Collections.singletonList(new RecognitionCandidate("拉", null)),
                null,
                null,
                -3
        );
        WritingAnalysis close = new WritingAnalysis(
                WritingAnalysis.Status.CLOSE,
                "hard",
                true,
                "close",
                Collections.emptyList(),
                null
        );
        WritingAnalysis failedWithoutScore = new WritingAnalysis(
                WritingAnalysis.Status.WRONG,
                "again",
                false,
                "wrong",
                Collections.singletonList(new RecognitionCandidate("拉", null)),
                null,
                HintLevel.BLIND,
                "ignored"
        );
        WritingAnalysis oneHintOption = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                "clean",
                Collections.emptyList(),
                null,
                HintLevel.TRACE
        );
        WritingAnalysis manyHintOptions = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                "clean",
                Collections.emptyList(),
                null,
                HintLevel.OUTLINE,
                3,
                "ignored"
        );
        WritingAnalysis nullHintOptions = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                "clean",
                Collections.emptyList(),
                null,
                (Object[]) null
        );
        WritingAnalysis emptyHintOptions = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                "clean",
                Collections.emptyList(),
                null,
                new Object[0]
        );

        assertEquals("", passedWithoutScore.message);
        assertEquals(HintLevel.BLIND, passedWithoutScore.hintLevel());
        assertEquals(0, passedWithoutScore.hintsUsed());
        assertEquals(0.744, passedWithoutScore.confidenceScore(), 0.001);
        assertEquals(0.0, failedWithoutScore.confidenceScore(), 0.001);
        assertEquals(0, failedWithoutScore.hintsUsed());
        assertEquals(HintLevel.TRACE, oneHintOption.hintLevel());
        assertEquals(0, oneHintOption.hintsUsed());
        assertEquals(HintLevel.OUTLINE, manyHintOptions.hintLevel());
        assertEquals(3, manyHintOptions.hintsUsed());
        assertEquals(HintLevel.BLIND, nullHintOptions.hintLevel());
        assertEquals(0, nullHintOptions.hintsUsed());
        assertEquals(HintLevel.BLIND, emptyHintOptions.hintLevel());
        assertEquals(0, emptyHintOptions.hintsUsed());
        assertFalse(close.failed());
    }

    @Test
    public void nonBlindOrHintedHighConfidenceWritingDoesNotEarnEasy() {
        WritingRatingMapper mapper = new WritingRatingMapper();
        WritingAnalysis minimal = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                "clean",
                Arrays.asList(new RecognitionCandidate("拉", 0.99f)),
                cleanStrokeOrder(),
                HintLevel.MINIMAL,
                0
        );
        WritingAnalysis hintedBlind = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                "clean",
                Arrays.asList(new RecognitionCandidate("拉", 0.99f)),
                cleanStrokeOrder(),
                HintLevel.BLIND,
                1
        );

        assertEquals(StudyRating.GOOD, mapper.suggestedRating(minimal));
        assertEquals(StudyRating.GOOD, mapper.suggestedRating(hintedBlind));
        assertEquals(StudyRating.AGAIN, mapper.suggestedRating(new WritingAnalysis(
                WritingAnalysis.Status.WRONG,
                "again",
                false,
                "wrong",
                Collections.emptyList(),
                null
        )));
        assertEquals(StudyRating.AGAIN, mapper.maxAllowedRating(new WritingAnalysis(
                WritingAnalysis.Status.WRONG,
                "again",
                false,
                "wrong",
                Collections.emptyList(),
                null
        )));
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
