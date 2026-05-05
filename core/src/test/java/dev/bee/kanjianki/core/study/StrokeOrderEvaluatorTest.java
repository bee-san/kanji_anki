package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StrokeOrderEvaluatorTest {
    @Test
    public void matchingStrokeStartsAndEndsPassCleanly() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(10f, 10f, 90f, 10f),
                stroke(10f, 30f, 90f, 30f)
        ));

        assertTrue(result.acceptable);
        assertTrue(result.clean);
    }

    @Test
    public void reversedStrokeDirectionIsPenalized() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(90f, 10f, 10f, 10f),
                stroke(10f, 30f, 90f, 30f)
        ));

        assertFalse(result.clean);
        assertTrue(result.score < 1f);
    }

    @Test
    public void scaledAndTranslatedWritingStillChecksOrder() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(220f, 240f, 460f, 240f),
                stroke(220f, 300f, 460f, 300f)
        ));

        assertTrue(result.acceptable);
        assertTrue(result.clean);
    }

    @Test
    public void missingGuideDoesNotSilentlyPass() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(null, sample(stroke(0f, 0f, 1f, 1f)));

        assertTrue(result.missingGuide);
        assertFalse(result.acceptable);
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

    private WritingSample sample(InkStroke... strokes) {
        return new WritingSample(Arrays.asList(strokes), 100f, 100f);
    }

    private InkStroke stroke(float x1, float y1, float x2, float y2) {
        return new InkStroke(Arrays.asList(new InkPoint(x1, y1, 0), new InkPoint(x2, y2, 1)));
    }
}
