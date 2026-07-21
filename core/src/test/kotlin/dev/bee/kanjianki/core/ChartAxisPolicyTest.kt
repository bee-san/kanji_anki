package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartAxisPolicyTest {
    @Test fun niceTicksCoverRequiredFixtures() {
        assertEquals(ChartAxisPolicy.Axis(0, listOf(0)), ChartAxisPolicy.forMaximum(0))
        assertEquals(ChartAxisPolicy.Axis(1, listOf(0, 1)), ChartAxisPolicy.forMaximum(1))
        assertEquals(ChartAxisPolicy.Axis(8, listOf(0, 2, 4, 6, 8)), ChartAxisPolicy.forMaximum(7))
        assertEquals(ChartAxisPolicy.Axis(200, listOf(0, 50, 100, 150, 200)), ChartAxisPolicy.forMaximum(180))
        assertEquals(ChartAxisPolicy.Axis(1500, listOf(0, 500, 1000, 1500)), ChartAxisPolicy.forMaximum(1026))
    }

    @Test fun valuesClampNegativeInputsAndExposeMatchingLabels() {
        val axis = ChartAxisPolicy.forValues(listOf(-5, 7), preferredIntervals = 0)
        assertEquals(10, axis.axisMax)
        assertEquals(axis.ticks.map(Int::toString), axis.labels)
        assertEquals(ChartAxisPolicy.Axis(0, listOf(0)), ChartAxisPolicy.forValues(null))
    }

    @Test fun maximumDomainAndUntrustedIntervalCountStayBounded() {
        val axis = ChartAxisPolicy.forMaximum(Int.MAX_VALUE, Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE, axis.axisMax)
        assertEquals(0, axis.ticks.first())
        assertEquals(Int.MAX_VALUE, axis.ticks.last())
        assertEquals(axis.ticks.sorted(), axis.ticks)
        assertEquals(axis.ticks.distinct(), axis.ticks)
        org.junit.Assert.assertTrue(axis.ticks.size <= 102)
    }
}
