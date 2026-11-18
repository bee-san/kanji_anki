// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Pedrubik2000/kuma3-anki-core (rslib/src/rwkv/engine.rs,
// interval search), Copyright: Ankitects Pty Ltd and contributors,
// GNU AGPL version 3 or later.

package dev.bee.rwkv

import kotlin.math.ceil

/**
 * Where a forgetting curve crosses a target retention, searched on upstream's
 * grid (sub-day points when the curve is already below target at one day,
 * then days 1–30 and ×1.5 steps to the maximum) and refined with a guarded
 * Illinois false-position search.
 */
object RwkvIntervals {
    const val S90_TARGET_RETENTION: Float = 0.9f
    private const val SECONDS_PER_DAY: Float = 86_400f

    private val SUB_DAY_SEARCH_POINTS = floatArrayOf(
        1f / 86_400f,
        1f / 1440f,
        5f / 1440f,
        10f / 1440f,
        20f / 1440f,
        30f / 1440f,
        1f / 24f,
        2f / 24f,
        3f / 24f,
        4f / 24f,
        6f / 24f,
        8f / 24f,
        12f / 24f,
        16f / 24f,
        20f / 24f,
    )

    /** Unrounded days until [curve] meets [targetRetention], or null for an invalid target. */
    @JvmStatic
    fun unroundedInterval(curve: RwkvCurve, targetRetention: Float, maxIntervalDays: Int): Float? {
        if (!(targetRetention in 0f..1f) || maxIntervalDays < 1) {
            return null
        }
        return crossingsOnGrid(maxIntervalDays, 1) { days ->
            floatArrayOf(curve.predict(days * SECONDS_PER_DAY) - targetRetention)
        }[0]
    }

    /** Whole days for scheduling: `ceil`, clamped to `1..maxIntervalDays`. */
    @JvmStatic
    fun roundedDays(unroundedDays: Float, maxIntervalDays: Int): Int =
        ceil(unroundedDays).coerceIn(1f, maxIntervalDays.toFloat()).toInt()

    /**
     * Each answer's unrounded interval at its target. With
     * [enforceGradeOrder] the four margins are made non-decreasing in grade
     * (pool-adjacent-violators) at every searched time, so Again ≤ Hard ≤
     * Good ≤ Easy.
     */
    @JvmStatic
    fun answerIntervals(
        curves: Array<RwkvCurve>,
        targetRetentions: FloatArray,
        maxIntervalDays: Int,
        enforceGradeOrder: Boolean,
    ): Array<Float?> {
        require(curves.size == 4 && targetRetentions.size == 4) { "expected four answers" }
        val valid = BooleanArray(4) { targetRetentions[it] in 0f..1f }
        if (maxIntervalDays < 1 || (enforceGradeOrder && valid.any { !it })) {
            return arrayOfNulls(4)
        }
        val crossings = crossingsOnGrid(maxIntervalDays, 4) { days ->
            val margins = FloatArray(4) { curves[it].predict(days * SECONDS_PER_DAY) - targetRetentions[it] }
            if (enforceGradeOrder) pavaNonDecreasing(margins) else margins
        }
        return Array(4) { if (valid[it]) crossings[it] else null }
    }

    internal fun crossingsOnGrid(maxIntervalDays: Int, count: Int, marginsAt: (Float) -> FloatArray): FloatArray {
        val points = ArrayList<Float>()
        if (marginsAt(1f).any { it <= 0f }) {
            SUB_DAY_SEARCH_POINTS.forEach { points += it }
        }
        intervalSearchDays(maxIntervalDays).forEach { points += it.toFloat() }

        val maximum = maxIntervalDays.toFloat()
        val crossings = arrayOfNulls<Float>(count)
        val previousPoint = arrayOfNulls<Float>(count)
        val previousMargin = FloatArray(count)
        var atZero: FloatArray? = null
        for (point in points) {
            val margins = marginsAt(point)
            for (index in 0 until count) {
                if (crossings[index] != null) {
                    continue
                }
                val margin = margins[index]
                if (margin <= 0f) {
                    val low: Float
                    val lowMargin: Float
                    if (previousPoint[index] != null) {
                        low = previousPoint[index]!!
                        lowMargin = previousMargin[index]
                    } else {
                        low = 0f
                        val zero = atZero ?: marginsAt(0f).also { atZero = it }
                        lowMargin = zero[index]
                    }
                    val crossing = if (lowMargin > 0f) {
                        crossingBetween(low, lowMargin, point, margin) { days -> marginsAt(days)[index] }
                    } else {
                        point
                    }
                    crossings[index] = minOf(crossing, maximum)
                }
                previousPoint[index] = point
                previousMargin[index] = margin
            }
            if (crossings.all { it != null }) {
                break
            }
        }
        return FloatArray(count) { crossings[it] ?: maximum }
    }

    internal fun crossingBetween(
        lowIn: Float,
        lowMarginIn: Float,
        highIn: Float,
        highMarginIn: Float,
        margin: (Float) -> Float,
    ): Float {
        var low = lowIn.toDouble()
        var lowMargin = lowMarginIn.toDouble()
        var high = highIn.toDouble()
        var highMargin = highMarginIn.toDouble()
        var lastMoved = 0
        repeat(64) {
            val tolerance = maxOf(1.0 / 86_400.0, high * 1e-6)
            if (high - low <= tolerance || lowMargin <= highMargin) {
                return high.toFloat()
            }
            var day = (low * highMargin - high * lowMargin) / (highMargin - lowMargin)
            if (!(day > low && day < high)) {
                day = 0.5 * (low + high)
            }
            val dayMargin = margin(day.toFloat()).toDouble()
            if (dayMargin > 0.0) {
                low = day
                lowMargin = dayMargin
                if (lastMoved == -1) {
                    highMargin *= 0.5
                }
                lastMoved = -1
            } else {
                high = day
                highMargin = dayMargin
                if (lastMoved == 1) {
                    lowMargin *= 0.5
                }
                lastMoved = 1
            }
        }
        return high.toFloat()
    }

    internal fun pavaNonDecreasing(values: FloatArray): FloatArray {
        val first = IntArray(values.size)
        val last = IntArray(values.size)
        val sum = FloatArray(values.size)
        val count = IntArray(values.size)
        var blocks = 0
        for (index in values.indices) {
            first[blocks] = index
            last[blocks] = index
            sum[blocks] = values[index]
            count[blocks] = 1
            blocks++
            while (blocks >= 2) {
                val right = blocks - 1
                val left = blocks - 2
                if (sum[left] / count[left] <= sum[right] / count[right]) {
                    break
                }
                last[left] = last[right]
                sum[left] += sum[right]
                count[left] += count[right]
                blocks--
            }
        }
        val adjusted = FloatArray(values.size)
        for (block in 0 until blocks) {
            val mean = sum[block] / count[block]
            for (index in first[block]..last[block]) {
                adjusted[index] = mean
            }
        }
        return adjusted
    }

    internal fun intervalSearchDays(maxIntervalDays: Int): IntArray {
        if (maxIntervalDays <= 30) {
            return IntArray(maxIntervalDays) { it + 1 }
        }
        val days = ArrayList<Int>()
        for (day in 1..30) {
            days += day
        }
        var day = 45
        while (day < maxIntervalDays) {
            days += day
            day = (day.toFloat() * 1.5f).toInt()
        }
        days += maxIntervalDays
        return days.toIntArray()
    }
}
