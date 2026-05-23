package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StrokeDiagnosisFormatterTest {
    @Test
    public void formatsCurrentTutorDiagnosisLinesExactly() {
        StrokeDiagnosis diagnosis = StrokeDiagnosis.builder()
                .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
                .add(StrokeDiagnosis.Label.WRONG_DIRECTION, 2)
                .add(StrokeDiagnosis.Label.MISSING_STROKE, 3)
                .add(StrokeDiagnosis.Label.ROUGH_SHAPE, 4)
                .add(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY, 0)
                .build();

        assertEquals(
                String.join(
                        "\n",
                        "Stroke 1: likely wrong order",
                        "Stroke 2: likely wrong direction",
                        "Stroke 3: may be missing",
                        "Stroke 4: shape looks rough",
                        "Recognized, but the stroke path was messy"
                ),
                StrokeDiagnosisFormatter.text(analysisWith(diagnosis, WritingAnalysis.Status.CLOSE))
        );
    }

    @Test
    public void hidesDiagnosisForNonActionableAnalysisStates() {
        StrokeDiagnosis diagnosis = StrokeDiagnosis.builder()
                .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
                .build();

        assertFalse(StrokeDiagnosisFormatter.canShow(null));
        assertFalse(StrokeDiagnosisFormatter.canShow(new WritingAnalysis(WritingAnalysis.Status.PASS, "good", true, "", Collections.emptyList(), null)));
        assertFalse(StrokeDiagnosisFormatter.canShow(analysisWith(StrokeDiagnosis.empty(), WritingAnalysis.Status.PASS)));
        assertFalse(StrokeDiagnosisFormatter.canShow(new WritingAnalysis(WritingAnalysis.Status.NO_INK, "again", false, "", Collections.emptyList(), cleanResult(diagnosis))));
        assertFalse(StrokeDiagnosisFormatter.canShow(new WritingAnalysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, "again", false, "", Collections.emptyList(), cleanResult(diagnosis))));
        assertFalse(StrokeDiagnosisFormatter.canShow(new WritingAnalysis(WritingAnalysis.Status.NO_STROKE_DATA, "again", false, "", Collections.emptyList(), cleanResult(diagnosis))));
        assertFalse(StrokeDiagnosisFormatter.canShow(new WritingAnalysis(WritingAnalysis.Status.RECOGNITION_ERROR, "again", false, "", Collections.emptyList(), cleanResult(diagnosis))));
        assertFalse(StrokeDiagnosisFormatter.canShow(new WritingAnalysis(WritingAnalysis.Status.CLOSE, "hard", true, "", Collections.emptyList(), StrokeOrderEvaluator.evaluate(null, sample()))));
    }

    @Test
    public void showsDiagnosisForPassCloseAndWrongWhenGuideAndDiagnosisExist() {
        StrokeDiagnosis diagnosis = StrokeDiagnosis.builder()
                .add(StrokeDiagnosis.Label.ROUGH_SHAPE, 1)
                .build();

        assertTrue(StrokeDiagnosisFormatter.canShow(analysisWith(diagnosis, WritingAnalysis.Status.PASS)));
        assertTrue(StrokeDiagnosisFormatter.canShow(analysisWith(diagnosis, WritingAnalysis.Status.CLOSE)));
        assertTrue(StrokeDiagnosisFormatter.canShow(analysisWith(diagnosis, WritingAnalysis.Status.WRONG)));
    }

    @Test
    public void lineHelpersHandleNulls() {
        assertEquals("", StrokeDiagnosisFormatter.line(null));
        assertEquals("", StrokeDiagnosisFormatter.strokeLine(null, "ignored"));
    }

    private static WritingAnalysis analysisWith(StrokeDiagnosis diagnosis, WritingAnalysis.Status status) {
        return new WritingAnalysis(
                status,
                "hard",
                status != WritingAnalysis.Status.WRONG,
                "",
                Collections.emptyList(),
                cleanResult(diagnosis)
        );
    }

    private static StrokeOrderEvaluator.StrokeOrderResult cleanResult(StrokeDiagnosis diagnosis) {
        return StrokeOrderEvaluator.evaluate(guide(), sample()).withDiagnosis(diagnosis);
    }

    private static StrokeGuide guide() {
        return new StrokeGuide(
                "拉",
                Arrays.asList(
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.1f, 0), new InkPoint(0.9f, 0.1f, 1))),
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.3f, 0), new InkPoint(0.9f, 0.3f, 1)))
                )
        );
    }

    private static WritingSample sample() {
        return new WritingSample(Arrays.asList(
                new InkStroke(Arrays.asList(new InkPoint(10f, 10f, 0), new InkPoint(90f, 10f, 1))),
                new InkStroke(Arrays.asList(new InkPoint(10f, 30f, 0), new InkPoint(90f, 30f, 1)))
        ), 100f, 100f);
    }
}
