package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SaturatingArithmeticTest {
    @Test
    fun saturatingAddClampsBothLongBoundaries() {
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE, 1L))
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE - 1L, 1L))
        assertEquals(Long.MIN_VALUE, saturatingAdd(Long.MIN_VALUE, -1L))
        assertEquals(Long.MIN_VALUE, saturatingAdd(Long.MIN_VALUE + 1L, -1L))
        assertEquals(3L, saturatingAdd(1L, 2L))
    }

    @Test
    fun saturatingSubtractClampsBothLongBoundaries() {
        assertEquals(Long.MIN_VALUE, saturatingSubtract(Long.MIN_VALUE, 1L))
        assertEquals(Long.MIN_VALUE, saturatingSubtract(Long.MIN_VALUE + 1L, 1L))
        assertEquals(Long.MAX_VALUE, saturatingSubtract(Long.MAX_VALUE, -1L))
        assertEquals(Long.MAX_VALUE, saturatingSubtract(Long.MAX_VALUE - 1L, -1L))
        assertEquals(1L, saturatingSubtract(3L, 2L))
    }

    @Test
    fun nonNegativeDifferenceDoesNotWrapAcrossLongRange() {
        assertEquals(Long.MAX_VALUE, nonNegativeDifference(Long.MAX_VALUE, Long.MIN_VALUE))
        assertEquals(0L, nonNegativeDifference(Long.MIN_VALUE, Long.MAX_VALUE))
        assertEquals(2L, nonNegativeDifference(3L, 1L))
    }
}
