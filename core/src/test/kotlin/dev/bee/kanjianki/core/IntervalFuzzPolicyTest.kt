package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IntervalFuzzPolicyTest {
    @Test
    fun shortIntervalsAreNeverFuzzed() {
        for (days in 0..2) {
            for (ordinal in 0 until 20) {
                assertEquals(days, IntervalFuzzPolicy.fuzzedIntervalDays(days, "脱", CoreSkill.RECOGNITION, ordinal))
            }
        }
        assertEquals(0.0, IntervalFuzzPolicy.factorFor(2), 0.0)
    }

    @Test
    fun fuzzStaysInsideAnkiBandsAndNeverBelowTwoDays() {
        val cases = listOf(3 to 0.15, 5 to 0.15, 7 to 0.10, 15 to 0.10, 20 to 0.05, 60 to 0.05, 365 to 0.05)
        for ((days, factor) in cases) {
            assertEquals(factor, IntervalFuzzPolicy.factorFor(days), 0.0)
            val halfWidth = maxOf(1.0, days * factor)
            for (ordinal in 0 until 50) {
                val fuzzed = IntervalFuzzPolicy.fuzzedIntervalDays(days, "脱", CoreSkill.CONTEXTUAL_READING, ordinal)
                assertTrue("$days -> $fuzzed", abs(fuzzed - days) <= halfWidth + 0.5)
                assertTrue(fuzzed >= 2)
            }
        }
    }

    @Test
    fun sameInputsAlwaysProduceTheSameOffsetAndDifferentItemsSpread() {
        val a = IntervalFuzzPolicy.fuzzedIntervalDays(30, "脱", CoreSkill.RECOGNITION, 4)
        val b = IntervalFuzzPolicy.fuzzedIntervalDays(30, "脱", CoreSkill.RECOGNITION, 4)
        assertEquals(a, b)

        // Twenty kanji admitted together with identical 30-day intervals must not all
        // land on the same day.
        val kanji = listOf("脱", "徴", "微", "撤", "徹", "澄", "裂", "録", "認", "弱", "分", "強", "痛", "傷", "被", "疲", "皮", "彼", "波", "破")
        val distinctDays = kanji.map { IntervalFuzzPolicy.fuzzedIntervalDays(30, it, CoreSkill.RECOGNITION, 3) }.toSet()
        assertTrue("expected spread, got $distinctDays", distinctDays.size >= 3)

        // The ordinal participates, so consecutive reviews of one item vary too.
        val perOrdinal = (0 until 12).map { IntervalFuzzPolicy.fuzzedIntervalDays(30, "脱", CoreSkill.RECOGNITION, it) }.toSet()
        assertTrue(perOrdinal.size >= 2)
    }

    @Test
    fun maximumIntervalCapsTheHighSideAndNullKanjiIsStable() {
        val capped = IntervalFuzzPolicy.fuzzedIntervalDays(100, "脱", CoreSkill.RECOGNITION, 1, maximumIntervalDays = 100)
        assertTrue(capped <= 100)
        assertEquals(95, IntervalFuzzPolicy.fuzzedIntervalDays(100, "脱", CoreSkill.RECOGNITION, 1, maximumIntervalDays = 95))
        assertEquals(
            IntervalFuzzPolicy.fuzzedIntervalDays(30, null, CoreSkill.RECOGNITION, 1),
            IntervalFuzzPolicy.fuzzedIntervalDays(30, null, CoreSkill.RECOGNITION, 1),
        )
        val unit = IntervalFuzzPolicy.unitInterval("脱", CoreSkill.RECOGNITION, 7)
        assertTrue(unit >= 0.0 && unit < 1.0)
    }
}
