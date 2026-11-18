package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingCompareModelTest {
    @Test
    fun nothingToCompareWithoutGuideOrInk() {
        val sample = sample(stroke(10f, 10f, 90f, 10f))

        assertNull(WritingCompareModel.from(null, sample, null))
        assertNull(WritingCompareModel.from(StrokeGuide("拉", emptyList()), sample, null))
        assertNull(WritingCompareModel.from(guide(3), null, null))
        assertNull(WritingCompareModel.from(guide(3), WritingSample.empty(), null))
        assertNull(WritingCompareModel.from(guide(3), sample(InkStroke(emptyList())), null))
    }

    @Test
    fun cleanWritingNumbersBothSidesAndNormalizesInkToTheUnitSquare() {
        val model = WritingCompareModel.from(
            guide(2),
            sample(stroke(20f, 10f, 180f, 10f), stroke(20f, 30f, 180f, 250f)),
            null,
        )!!

        assertEquals(listOf(1, 2), model.yours.map { it.number })
        assertEquals(listOf(1, 2), model.model.map { it.number })
        assertTrue(model.yours.all { it.verdict == WritingCompareModel.Verdict.OK })
        assertTrue(model.model.all { it.verdict == WritingCompareModel.Verdict.OK })
        assertTrue(model.problems().isEmpty())
        assertEquals(0.1f, model.yours[0].points[0].x, 0.0001f)
        assertEquals(0.05f, model.yours[0].points[0].y, 0.0001f)
        assertEquals(0.9f, model.yours[1].points[1].x, 0.0001f)
        // Ink outside the pad is clamped rather than drawn off the square.
        assertEquals(1f, model.yours[1].points[1].y, 0.0001f)
        assertEquals(guide(2).strokes[1].points, model.model[1].points)
    }

    @Test
    fun diagnosisLabelsBecomeVerdictsOnBothSides() {
        val diagnosis = StrokeDiagnosis.builder()
            .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
            .add(StrokeDiagnosis.Label.WRONG_DIRECTION, 2)
            .add(StrokeDiagnosis.Label.FAR_FROM_GUIDE, 3)
            .add(StrokeDiagnosis.Label.ROUGH_SHAPE, 3)
            .add(StrokeDiagnosis.Label.ROUGH_SHAPE, 4)
            .add(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY, 0)
            .build()

        val model = WritingCompareModel.from(guide(5), sample(*strokes(5)), diagnosis)!!

        val expected = listOf(
            WritingCompareModel.Verdict.WRONG_ORDER,
            WritingCompareModel.Verdict.WRONG_DIRECTION,
            WritingCompareModel.Verdict.ROUGH,
            WritingCompareModel.Verdict.ROUGH,
            WritingCompareModel.Verdict.OK,
        )
        assertEquals(expected, model.yours.map { it.verdict })
        assertEquals(expected, model.model.map { it.verdict })
        assertEquals(
            listOf(
                WritingCompareModel.Verdict.ROUGH,
                WritingCompareModel.Verdict.WRONG_DIRECTION,
                WritingCompareModel.Verdict.WRONG_ORDER,
            ),
            model.problems(),
        )
    }

    @Test
    fun wrongOrderOutranksOtherLabelsOnTheSameStroke() {
        val diagnosis = StrokeDiagnosis.builder()
            .add(StrokeDiagnosis.Label.FAR_FROM_GUIDE, 1)
            .add(StrokeDiagnosis.Label.WRONG_DIRECTION, 1)
            .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
            .build()

        val model = WritingCompareModel.from(guide(1), sample(*strokes(1)), diagnosis)!!

        assertEquals(WritingCompareModel.Verdict.WRONG_ORDER, model.yours[0].verdict)
    }

    @Test
    fun missingStrokesAreMarkedOnTheModelSide() {
        val diagnosis = StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.MISSING_STROKE, 2).build()

        val model = WritingCompareModel.from(guide(4), sample(*strokes(2)), diagnosis)!!

        assertEquals(2, model.yours.size)
        assertEquals(
            listOf(
                WritingCompareModel.Verdict.OK,
                WritingCompareModel.Verdict.MISSING,
                WritingCompareModel.Verdict.MISSING,
                WritingCompareModel.Verdict.MISSING,
            ),
            model.model.map { it.verdict },
        )
        assertEquals(listOf(WritingCompareModel.Verdict.MISSING), model.problems())
    }

    @Test
    fun extraStrokesAreMarkedOnYourSideOnlyAndEmptyStrokesAreNotNumbered() {
        val diagnosis = StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.EXTRA_STROKE, 2).build()
        val drawn = strokes(3)

        val model = WritingCompareModel.from(
            guide(2),
            sample(drawn[0], InkStroke(emptyList()), drawn[1], drawn[2]),
            diagnosis,
        )!!

        assertEquals(listOf(1, 2, 3), model.yours.map { it.number })
        assertEquals(
            listOf(
                WritingCompareModel.Verdict.OK,
                WritingCompareModel.Verdict.EXTRA,
                WritingCompareModel.Verdict.EXTRA,
            ),
            model.yours.map { it.verdict },
        )
        // An extra stroke has no model counterpart to blame.
        assertEquals(
            listOf(WritingCompareModel.Verdict.OK, WritingCompareModel.Verdict.EXTRA),
            model.model.map { it.verdict },
        )
    }

    @Test
    fun degenerateSampleSizeAndNonFinitePointsStayInsideTheSquare() {
        val model = WritingCompareModel.from(
            guide(1),
            WritingSample(listOf(InkStroke(listOf(InkPoint(0.4f, Float.NaN, 0L), InkPoint(-3f, 0.5f, 1L)))), 0f, 0f),
            null,
        )!!

        assertEquals(0.4f, model.yours[0].points[0].x, 0.0001f)
        assertEquals(0f, model.yours[0].points[0].y, 0.0001f)
        assertEquals(0f, model.yours[0].points[1].x, 0.0001f)
    }

    private fun guide(strokeCount: Int): StrokeGuide {
        return StrokeGuide("拉", (0 until strokeCount).map { line(0.1f, 0.1f + it * 0.15f, 0.9f, 0.1f + it * 0.15f) })
    }

    private fun strokes(count: Int): Array<InkStroke> {
        return Array(count) { stroke(20f, 20f + it * 30f, 180f, 20f + it * 30f) }
    }

    private fun sample(vararg strokes: InkStroke): WritingSample = WritingSample(strokes.toList(), 200f, 200f)

    private fun stroke(x1: Float, y1: Float, x2: Float, y2: Float): InkStroke = line(x1, y1, x2, y2)

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float): InkStroke {
        return InkStroke(listOf(InkPoint(x1, y1, 0L), InkPoint(x2, y2, 1L)))
    }
}
