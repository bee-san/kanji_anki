package dev.bee.kanjianki.core

internal fun saturatingAdd(left: Long, right: Long): Long {
    if (right > 0L && left > Long.MAX_VALUE - right) {
        return Long.MAX_VALUE
    }
    if (right < 0L && left < Long.MIN_VALUE - right) {
        return Long.MIN_VALUE
    }
    return left + right
}

internal fun saturatingSubtract(left: Long, right: Long): Long {
    if (right > 0L && left < Long.MIN_VALUE + right) {
        return Long.MIN_VALUE
    }
    if (right < 0L && left > Long.MAX_VALUE + right) {
        return Long.MAX_VALUE
    }
    return left - right
}

internal fun nonNegativeDifference(later: Long, earlier: Long): Long {
    if (later <= earlier) {
        return 0L
    }
    if (earlier < 0L && later > Long.MAX_VALUE + earlier) {
        return Long.MAX_VALUE
    }
    return later - earlier
}

internal fun saturatingAddNonNegative(left: Int, right: Int): Int {
    val safeLeft = left.coerceAtLeast(0)
    val safeRight = right.coerceAtLeast(0)
    return if (safeLeft > Int.MAX_VALUE - safeRight) Int.MAX_VALUE else safeLeft + safeRight
}

internal fun saturatingMultiplyNonNegative(left: Int, right: Int): Int {
    val product = left.coerceAtLeast(0).toLong() * right.coerceAtLeast(0).toLong()
    return product.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
