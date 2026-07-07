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

        // Mirror every completed trace section (button taps, route renders, settings writes,
        // async loads) into the user-toggleable debug log. The isCapturing() gate keeps the
        // string formatting off this path entirely while the debug log is off.
        if (AppDebugLog.isCapturing()) {
            runCatching {
                AppDebugLog.log(
                    String.format(Locale.US, "perf section=%s duration_ms=%.2f", section, durationMs),
                )
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

/**
 * Debug-only timing probe that ALWAYS logs (unlike [withUiTrace], which only logs when a
 * section runs past [PERF_TRACE_LOG_THRESHOLD_MS]). Used to trace the cold-boot study-load path
 * where we want to see every stage's duration, including the fast ones, so slow stages stand out.
 */
internal fun <T> withStudyLoadProbe(stage: String, action: () -> T): T {
    if (!BuildConfig.DEBUG) {
        return action()
    }
    val startNanos = runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
    val onMainThread = runCatching {
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
    }.getOrDefault(false)
    try {
        return action()
    } finally {
        val durationMs = (runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime()) - startNanos) / 1_000_000.0
        val line = String.format(
            Locale.US,
            "stage=%s duration_ms=%.2f main_thread=%b",
            stage,
            durationMs,
            onMainThread,
        )
        runCatching { Log.d(STUDY_LOAD_LOG_TAG, line) }
        StudyLoadDebugLog.log(line)
    }
}

internal fun studyLoadDebug(message: String) {
    if (BuildConfig.DEBUG) {
        runCatching { Log.d(STUDY_LOAD_LOG_TAG, message) }
        StudyLoadDebugLog.log(message)
    }
}

internal const val STUDY_LOAD_LOG_TAG = "KaniStudyLoad"

internal fun buttonTraceSection(label: String): String {
    return "kani.button.${traceToken(label)}"
}

internal fun withRouteTrace(route: String, action: () -> Unit) {
    withUiTrace("kani.route.${traceToken(route)}", action)
}

internal fun withButtonTrace(label: String, action: () -> Unit) {
    withUiTrace(buttonTraceSection(label), action)
}

internal fun asyncLoadTraceSection(route: String, phase: String): String {
    return "kani.${traceToken(phase)}.${traceToken(route)}"
}

internal fun <T> withAsyncLoadTrace(route: String, phase: String, action: () -> T): T {
    return withUiTrace(asyncLoadTraceSection(route, phase), action)
}

internal fun traceToken(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return "unknown"
    }
    return PERF_TRACE_TOKEN_RE.replace(trimmed.lowercase(Locale.ROOT), "-")
        .trim('-')
        .ifEmpty { "unknown" }
}
