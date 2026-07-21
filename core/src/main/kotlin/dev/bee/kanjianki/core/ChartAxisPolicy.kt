package dev.bee.kanjianki.core

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Creates integer 1/2/5 x 10^n ticks that cover the exact chart domain. */
object ChartAxisPolicy {
    private const val MAX_INTERVALS = 100

    data class Axis(
        val axisMax: Int,
        val ticks: List<Int>,
    ) {
        val labels: List<String> get() = ticks.map(Int::toString)
    }

    @JvmStatic
    @JvmOverloads
    fun forValues(values: List<Int>?, preferredIntervals: Int = 4): Axis {
        val maximum = values.orEmpty().maxOrNull()?.coerceAtLeast(0) ?: 0
        return forMaximum(maximum, preferredIntervals)
    }

    @JvmStatic
    @JvmOverloads
    fun forMaximum(maximum: Int, preferredIntervals: Int = 4): Axis {
        val safeMaximum = maximum.coerceAtLeast(0)
        if (safeMaximum == 0) return Axis(0, listOf(0))
        if (safeMaximum == 1) return Axis(1, listOf(0, 1))
        val intervals = preferredIntervals.coerceIn(1, MAX_INTERVALS)
        val rawStep = safeMaximum.toDouble() / intervals
        val exponent = floor(log10(rawStep))
        val scale = 10.0.pow(exponent)
        val fraction = rawStep / scale
        val niceFraction = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        val step = maxOf(1L, (niceFraction * scale).toLong())
        val roundedMaximum = ((safeMaximum.toLong() + step - 1L) / step) * step
        val axisMax = minOf(Int.MAX_VALUE.toLong(), roundedMaximum).toInt()
        val ticks = ArrayList<Int>(intervals + 2)
        var tick = 0L
        while (tick < axisMax) {
            ticks.add(tick.toInt())
            tick += step
        }
        if (ticks.lastOrNull() != axisMax) {
            ticks.add(axisMax)
        }
        return Axis(axisMax, ticks)
    }
}
