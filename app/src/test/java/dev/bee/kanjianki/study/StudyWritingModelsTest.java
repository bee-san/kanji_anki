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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class StudyWritingModelsTest {
    @Test
    public void capturedStrokeRejectsInvalidInputs() {
        assertThrows(NullPointerException.class, () -> new CapturedStroke(null));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke(Collections.emptyList()));
        assertThrows(
                NullPointerException.class,
                () -> new CapturedStroke(Arrays.asList(point(1f, 2f), null))
        );
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke.Point(Float.NaN, 0f));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke.Point(Float.POSITIVE_INFINITY, 0f));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke.Point(0f, Float.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke.Point(0f, 0f, -1L));
    }

    @Test
    public void capturedStrokeCopiesAndFreezesPoints() {
        List<CapturedStroke.Point> source = new ArrayList<>();
        source.add(new CapturedStroke.Point(1f, 2f, 30L));
        source.add(point(3f, 4f));

        CapturedStroke stroke = CapturedStroke.of(source);
        source.add(point(9f, 9f));

        assertEquals(2, stroke.points.size());
        assertEquals(1f, stroke.points.get(0).x, 0f);
        assertEquals(2f, stroke.points.get(0).y, 0f);
        assertEquals(Long.valueOf(30L), stroke.points.get(0).timestampMillis);
        assertEquals(3f, stroke.points.get(1).x, 0f);
        assertEquals(4f, stroke.points.get(1).y, 0f);
        assertNull(stroke.points.get(1).timestampMillis);
        assertThrows(UnsupportedOperationException.class, () -> stroke.points.add(point(5f, 6f)));
    }

    @Test
    public void capturedWritingRejectsInvalidInputs() {
        CapturedStroke stroke = stroke(point(1f, 1f));

        assertThrows(NullPointerException.class, () -> new CapturedWriting(null));
        assertThrows(IllegalArgumentException.class, () -> new CapturedWriting(Collections.emptyList()));
        assertThrows(NullPointerException.class, () -> new CapturedWriting(Collections.singletonList(null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CapturedWriting(Collections.singletonList(stroke), 100f, null, "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CapturedWriting(Collections.singletonList(stroke), null, 100f, "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CapturedWriting(Collections.singletonList(stroke), 0f, 100f, "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CapturedWriting(Collections.singletonList(stroke), 100f, Float.NaN, "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CapturedWriting(Collections.singletonList(stroke), Float.POSITIVE_INFINITY, 100f, "")
        );
    }

    @Test
    public void capturedWritingCopiesStrokesAndReportsContextFlags() {
        CapturedStroke stroke = stroke(point(1f, 1f));
        List<CapturedStroke> strokes = new ArrayList<>();
        strokes.add(stroke);

        CapturedWriting withAreaAndNullContext = new CapturedWriting(strokes, 200f, 300f, null);
        strokes.add(stroke(point(2f, 2f)));
        CapturedWriting withTextContext = new CapturedWriting(Collections.singletonList(stroke), null, null, "previous");
        CapturedWriting basic = CapturedWriting.of(Collections.singletonList(stroke));

        assertEquals(1, withAreaAndNullContext.strokes.size());
        assertEquals(200f, withAreaAndNullContext.writingAreaWidth, 0f);
        assertEquals(300f, withAreaAndNullContext.writingAreaHeight, 0f);
        assertEquals("", withAreaAndNullContext.preContext);
        assertTrue(withAreaAndNullContext.hasWritingArea());
        assertTrue(withAreaAndNullContext.hasRecognitionContext());
        assertFalse(withTextContext.hasWritingArea());
        assertTrue(withTextContext.hasRecognitionContext());
        assertFalse(basic.hasWritingArea());
        assertFalse(basic.hasRecognitionContext());
        assertThrows(UnsupportedOperationException.class, () -> withAreaAndNullContext.strokes.add(stroke));
    }

    @Test
    public void prepareForRecognitionRejectsInvalidInputs() {
        List<CapturedStroke> strokes = Collections.singletonList(stroke(point(1f, 1f), point(2f, 2f)));

        assertThrows(NullPointerException.class, () -> CapturedWriting.prepareForRecognition(null, 100f, 100f));
        assertThrows(
                IllegalArgumentException.class,
                () -> CapturedWriting.prepareForRecognition(Collections.emptyList(), 100f, 100f)
        );
        assertThrows(IllegalArgumentException.class, () -> CapturedWriting.prepareForRecognition(strokes, 0f, 100f));
        assertThrows(IllegalArgumentException.class, () -> CapturedWriting.prepareForRecognition(strokes, 100f, Float.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> CapturedWriting.prepareForRecognition(strokes, Float.NEGATIVE_INFINITY, 100f)
        );
    }

    @Test
    public void prepareForRecognitionSimplifiesDenseStrokesAndNormalizesIntoSquare() {
        CapturedStroke duplicateFinal = stroke(
                new CapturedStroke.Point(10f, 10f, 0L),
                new CapturedStroke.Point(11f, 11f, 1L),
                new CapturedStroke.Point(13f, 10f, 2L),
                new CapturedStroke.Point(13f, 10f, 3L)
        );
        CapturedStroke addedFinal = stroke(
                new CapturedStroke.Point(20f, 20f, 4L),
                new CapturedStroke.Point(21f, 20f, 5L),
                new CapturedStroke.Point(24f, 20f, 6L)
        );

        CapturedWriting prepared = CapturedWriting.prepareForRecognition(
                Arrays.asList(duplicateFinal, addedFinal),
                320f,
                240f
        );

        assertEquals(2, prepared.strokes.size());
        assertEquals(2, prepared.strokes.get(0).points.size());
        assertEquals(2, prepared.strokes.get(1).points.size());
        assertEquals(1000f, prepared.writingAreaWidth, 0f);
        assertEquals(1000f, prepared.writingAreaHeight, 0f);
        assertEquals("", prepared.preContext);
        assertTrue(prepared.hasWritingArea());
        assertTrue(prepared.hasRecognitionContext());
        assertPoint(prepared.strokes.get(0).points.get(0), 140f, 242.85715f, 0L);
        assertPoint(prepared.strokes.get(0).points.get(1), 294.2857f, 242.85715f, 2L);
        assertPoint(prepared.strokes.get(1).points.get(0), 654.2857f, 757.1429f, 4L);
        assertPoint(prepared.strokes.get(1).points.get(1), 860f, 757.1429f, 6L);
    }

    @Test
    public void prepareForRecognitionKeepsShortStrokesAndCentersMinimumSizeBounds() {
        CapturedStroke shortStroke = stroke(
                new CapturedStroke.Point(5f, 7f, 10L),
                new CapturedStroke.Point(5f, 7f, 11L)
        );

        CapturedWriting prepared = CapturedWriting.prepareForRecognition(
                Collections.singletonList(shortStroke),
                80f,
                60f
        );

        assertEquals(1, prepared.strokes.size());
        assertEquals(2, prepared.strokes.get(0).points.size());
        assertPoint(prepared.strokes.get(0).points.get(0), 140f, 140f, 10L);
        assertPoint(prepared.strokes.get(0).points.get(1), 140f, 140f, 11L);
    }

    @Test
    public void prepareForRecognitionKeepsFinalPointWhenOnlyYChanges() {
        CapturedStroke verticalFinal = stroke(
                new CapturedStroke.Point(10f, 10f, 0L),
                new CapturedStroke.Point(10f, 11f, 1L),
                new CapturedStroke.Point(10f, 12f, 2L)
        );

        CapturedWriting prepared = CapturedWriting.prepareForRecognition(
                Collections.singletonList(verticalFinal),
                100f,
                100f
        );

        assertEquals(1, prepared.strokes.size());
        assertEquals(2, prepared.strokes.get(0).points.size());
        assertEquals(Long.valueOf(0L), prepared.strokes.get(0).points.get(0).timestampMillis);
        assertEquals(Long.valueOf(2L), prepared.strokes.get(0).points.get(1).timestampMillis);
    }

    @Test
    public void prepareForRecognitionRejectsStrokeStateThatSimplifiesAway() throws Exception {
        CapturedStroke emptyStroke = emptyStrokeForDefensiveBranch();

        assertThrows(
                IllegalArgumentException.class,
                () -> CapturedWriting.prepareForRecognition(Collections.singletonList(emptyStroke), 100f, 100f)
        );
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

    private static CapturedStroke.Point point(float x, float y) {
        return new CapturedStroke.Point(x, y);
    }

    private static CapturedStroke stroke(CapturedStroke.Point... points) {
        return new CapturedStroke(Arrays.asList(points));
    }

    private static void assertPoint(CapturedStroke.Point point, float x, float y, long timestamp) {
        assertNotNull(point);
        assertEquals(x, point.x, 0.0001f);
        assertEquals(y, point.y, 0.0001f);
        assertEquals(Long.valueOf(timestamp), point.timestampMillis);
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

    private static CapturedStroke emptyStrokeForDefensiveBranch() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        java.lang.reflect.Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        CapturedStroke stroke = (CapturedStroke) allocateInstance.invoke(unsafe, CapturedStroke.class);
        java.lang.reflect.Field points = CapturedStroke.class.getDeclaredField("points");
        points.setAccessible(true);
        points.set(stroke, Collections.emptyList());
        return stroke;
    }
}
