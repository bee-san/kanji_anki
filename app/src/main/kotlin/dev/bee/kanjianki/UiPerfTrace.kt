package dev.bee.kanjianki

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import java.util.Locale

private val PERF_TRACE_TOKEN_RE = Regex("[^A-Za-z0-9._-]+")
private const val PERF_TRACE_LOG_TAG = "KaniPerf"
private const val PERF_TRACE_LOG_THRESHOLD_MS = 16L

internal fun <T> withUiTrace(section: String, action: () -> T): T {
    if (section.isBlank()) {
        return action()
    }

    val startNanos = runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
    val traceStarted = runCatching {
        Trace.beginSection(section)
        true
    }.getOrDefault(false)

    try {
        return action()
    } finally {
        val durationMs = (runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime()) - startNanos) / 1_000_000.0
        if (traceStarted) {
            runCatching {
                Trace.endSection()
            }
        }

        if (BuildConfig.DEBUG && durationMs >= PERF_TRACE_LOG_THRESHOLD_MS) {
            runCatching {
                Log.d(
                    PERF_TRACE_LOG_TAG,
                    String.format(
                        Locale.US,
                        "perf section=%s duration_ms=%.2f",
                        section,
                        durationMs
                    )
                )
            }
        }
    }
}

internal fun withRouteTrace(route: String, action: () -> Unit) {
    withUiTrace("kani.route.${traceToken(route)}", action)
}

internal fun withButtonTrace(label: String, action: () -> Unit) {
    withUiTrace("kani.button.${traceToken(label)}", action)
}

internal fun <T> withAsyncLoadTrace(route: String, phase: String, action: () -> T): T {
    return withUiTrace("kani.${traceToken(phase)}.${traceToken(route)}", action)
}

private fun traceToken(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return "unknown"
    }
    return PERF_TRACE_TOKEN_RE.replace(trimmed.lowercase(Locale.ROOT), "-")
        .trim('-')
        .ifEmpty { "unknown" }
}
