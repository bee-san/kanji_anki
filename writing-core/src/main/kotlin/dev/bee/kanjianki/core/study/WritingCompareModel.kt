package dev.bee.kanjianki.core.study

/**
 * Stroke-by-stroke comparison shown after a writing check: the learner's ink
 * beside the model, both numbered and tagged with a per-stroke verdict.
 *
 * Pure and deterministic. All points are normalized to the unit square so the
 * two sides can be drawn at any size.
 */
class WritingCompareModel private constructor(
    @JvmField val yours: List<Stroke>,
    @JvmField val model: List<Stroke>,
) {
    enum class Verdict {
        OK,
        ROUGH,
        WRONG_DIRECTION,
        WRONG_ORDER,
        MISSING,
        EXTRA,
    }

    class Stroke internal constructor(
        /** 1-based stroke number, as drawn (yours) or as expected (model). */
        @JvmField val number: Int,
        @JvmField val points: List<InkPoint>,
        @JvmField val verdict: Verdict,
    )

    /** Verdicts that appear on either side, in display order, without [Verdict.OK]. */
    fun problems(): List<Verdict> {
        val present = HashSet<Verdict>()
        yours.forEach { present.add(it.verdict) }
        model.forEach { present.add(it.verdict) }
        return Verdict.values().filter { it != Verdict.OK && present.contains(it) }
    }

    companion object {
        /**
         * @return null when there is nothing to compare (no guide or no ink).
         */
        @JvmStatic
        fun from(guide: StrokeGuide?, sample: WritingSample?, diagnosis: StrokeDiagnosis?): WritingCompareModel? {
            if (guide == null || guide.isEmpty() || sample == null || !sample.hasInk()) {
                return null
            }
            val safeDiagnosis = diagnosis ?: StrokeDiagnosis.empty()
            val drawn = sample.strokes.filter { !it.isEmpty() }
            val expected = guide.strokeCount()
            val yours = drawn.mapIndexed { index, stroke ->
                val number = index + 1
                Stroke(number, normalize(stroke, sample), drawnVerdict(safeDiagnosis, number, expected))
            }
            val model = guide.strokes.mapIndexed { index, stroke ->
                val number = index + 1
                val verdict = when {
                    safeDiagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE, number) -> Verdict.MISSING
                    number <= yours.size -> yours[index].verdict
                    else -> Verdict.MISSING
                }
                Stroke(number, stroke.points, verdict)
            }
            return WritingCompareModel(yours, model)
        }

        private fun drawnVerdict(diagnosis: StrokeDiagnosis, number: Int, expected: Int): Verdict {
            return when {
                number > expected || diagnosis.hasLabel(StrokeDiagnosis.Label.EXTRA_STROKE, number) -> Verdict.EXTRA
                diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, number) -> Verdict.WRONG_ORDER
                diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, number) -> Verdict.WRONG_DIRECTION
                diagnosis.hasLabel(StrokeDiagnosis.Label.FAR_FROM_GUIDE, number) ||
                    diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, number) -> Verdict.ROUGH
                else -> Verdict.OK
            }
        }

        private fun normalize(stroke: InkStroke, sample: WritingSample): List<InkPoint> {
            val width = if (sample.width > 0f) sample.width else 1f
            val height = if (sample.height > 0f) sample.height else 1f
            return stroke.points.map { point ->
                InkPoint(unit(point.x / width), unit(point.y / height), point.timestampMillis)
            }
        }

        private fun unit(value: Float): Float {
            return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }
    }
}
