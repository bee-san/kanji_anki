package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Constructor

class WritingValueModelsTest {
    @Test
    fun strokeEvaluationAndPointValuesCoverAccessorsAndFallbacks() {
        val empty = StrokeOrderEvaluation(-1, -1, -1, null, null, null, null, 2.0)
        assertEquals(0, empty.expectedCount())
        assertEquals(0, empty.attemptedCount())
        assertEquals(0, empty.orderedMatchCount())
        assertEquals(1.0, empty.score(), 0.001)
        assertFalse(empty.complete())
        assertFalse(empty.exactOrder())
        assertFalse(empty.passed())

        val exact = StrokeOrderEvaluation(
            2,
            2,
            2,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            -1.0,
        )
        assertTrue(exact.complete())
        assertTrue(exact.exactOrder())
        assertTrue(exact.passed())
        assertEquals(0.0, exact.score(), 0.001)

        val imperfect = StrokeOrderEvaluation(
            2,
            2,
            1,
            emptyList(),
            emptyList(),
            emptyList(),
            listOf("2"),
            0.5,
        )
        assertTrue(imperfect.complete())
        assertFalse(imperfect.exactOrder())
        assertEquals(listOf("2"), imperfect.outOfPositionStrokeIds())
        assertEquals(emptyList<String>(), imperfect.missingStrokeIds())
        assertEquals(emptyList<String>(), imperfect.extraStrokeIds())
        assertEquals(emptyList<String>(), imperfect.duplicateStrokeIds())

        val point = InkPoint(0.25f, 0.5f, 7L)
        assertEquals(InkPoint(25f, 100f, 7L), point.scaled(100f, 200f))
        val nonPoint: Any = "not a point"
        val equalsNonPoint = point == nonPoint
        assertFalse(equalsNonPoint)
        assertNotEquals(point, InkPoint(0.25f, 0.6f, 7L))
        assertEquals(point.hashCode(), InkPoint(0.25f, 0.5f, 7L).hashCode())
    }

    @Test
    fun writingAnalysisAndDiagnosisCoverFallbacks() {
        val fallback = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            null,
            listOf(RecognitionCandidate("拉", null)),
            null,
            null,
            -1,
        )

        assertEquals("", fallback.message)
        assertEquals(HintLevel.BLIND, fallback.hintLevel())
        assertEquals(0, fallback.hintsUsed())
        assertTrue(fallback.passed())
        assertFalse(fallback.failed())
        assertEquals((0.78 * 0.55) + (0.7 * 0.45), fallback.confidenceScore(), 0.001)

        val failed = WritingAnalysis(
            WritingAnalysis.Status.WRONG,
            "again",
            false,
            "wrong",
            emptyList(),
            null,
        )
        assertTrue(failed.failed())
        assertEquals(0.0, failed.confidenceScore(), 0.001)

        val diagnosis = StrokeDiagnosis.builder()
            .add(null, 1)
            .add(StrokeDiagnosis.Label.WRONG_ORDER, -1)
            .add(StrokeDiagnosis.Label.WRONG_ORDER, -1)
            .build()
        assertFalse(diagnosis.isEmpty())
        assertTrue(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER))
        assertTrue(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 0))
        assertFalse(diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE))
        assertFalse(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 1))
        assertEquals(2, diagnosis.plus(StrokeDiagnosis.Label.MISSING_STROKE, 2).entries.size)
        assertTrue(StrokeDiagnosis.builder().build().isEmpty())
    }

    @Test
    fun writingAnalysisKeepsJavaConstructorCompatibility() {
        val basic: Constructor<WritingAnalysis> = WritingAnalysis::class.java.getConstructor(
            WritingAnalysis.Status::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
            String::class.java,
            List::class.java,
            StrokeOrderEvaluator.StrokeOrderResult::class.java,
        )
        val hintArray: Constructor<WritingAnalysis> = WritingAnalysis::class.java.getConstructor(
            WritingAnalysis.Status::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
            String::class.java,
            List::class.java,
            StrokeOrderEvaluator.StrokeOrderResult::class.java,
            Array<Any>::class.java,
        )
        val singleHint: Constructor<WritingAnalysis> = WritingAnalysis::class.java.getConstructor(
            WritingAnalysis.Status::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
            String::class.java,
            List::class.java,
            StrokeOrderEvaluator.StrokeOrderResult::class.java,
            Any::class.java,
        )
        val varargsHints: Constructor<WritingAnalysis> = WritingAnalysis::class.java.getConstructor(
            WritingAnalysis.Status::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
            String::class.java,
            List::class.java,
            StrokeOrderEvaluator.StrokeOrderResult::class.java,
            Any::class.java,
            Any::class.java,
            Array<Any>::class.java,
        )

        assertFalse(basic.isVarArgs)
        assertFalse(hintArray.isVarArgs)
        assertFalse(singleHint.isVarArgs)
        assertTrue(varargsHints.isVarArgs)

        val analysis = varargsHints.newInstance(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "ok",
            emptyList<Any>(),
            null,
            HintLevel.OUTLINE,
            2,
            emptyArray<Any>(),
        )
        assertEquals(HintLevel.OUTLINE, analysis.hintLevel())
        assertEquals(2, analysis.hintsUsed())
    }
}
