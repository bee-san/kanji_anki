package dev.bee.kanjianki

import android.os.Trace
import java.util.Locale

private val PERF_TRACE_TOKEN_RE = Regex("[^A-Za-z0-9._-]+")

internal fun <T> withUiTrace(section: String, action: () -> T): T {
    if (section.isBlank()) {
        return action()
    }
    Trace.beginSection(section)
    try {
        return action()
    } finally {
        Trace.endSection()
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
