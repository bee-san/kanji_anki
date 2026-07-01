package dev.bee.kanjianki

import android.os.SystemClock
import android.util.Log
import java.util.LinkedHashSet
import java.util.Locale

internal data class TimingDiagnosticsEvent(
    val name: String,
    val elapsedMs: Long,
)

internal data class TimingDiagnosticsSnapshot(
    val startedAtElapsedMs: Long,
    val events: List<TimingDiagnosticsEvent>,
) {
    val totalElapsedMs: Long
        get() = events.lastOrNull()?.elapsedMs ?: 0L

    fun summaryText(): String {
        if (events.isEmpty()) {
            return "0 events; total 0 ms; first=-; last=-"
        }
        return String.format(
            Locale.ROOT,
            "%d events; total %d ms; first=%s; last=%s",
            events.size,
            totalElapsedMs,
            events.first().name,
            events.last().name,
        )
    }

    fun previewText(limit: Int = 5): String {
        if (events.isEmpty()) {
            return "No timing events recorded yet."
        }
        val visible = events.take(limit)
        val preview = visible.joinToString("\n") { event ->
            String.format(Locale.ROOT, "%d ms %s", event.elapsedMs, event.name)
        }
        return if (events.size > limit) {
            "$preview\n… (+${events.size - limit} more)"
        } else {
            preview
        }
    }

    fun reportText(): String {
        val lines = buildList {
            add("Kani timing diagnostics")
            add("summary=${summaryText()}")
            add("started_at_elapsed_ms=$startedAtElapsedMs")
            add("event_count=${events.size}")
            add("total_elapsed_ms=$totalElapsedMs")
            if (events.isEmpty()) {
                add("(no events yet)")
            } else {
                events.forEachIndexed { index, event ->
                    add(String.format(Locale.ROOT, "%02d. %d ms %s", index + 1, event.elapsedMs, event.name))
                }
            }
        }
        return lines.joinToString(separator = "\n")
    }
}

internal class TimingDiagnosticsRecorder(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val logger: (String) -> Unit = { message -> Log.i(TAG, message) },
) {
    private val lock = Any()
    private val seenNames = LinkedHashSet<String>()
    private val events = mutableListOf<TimingDiagnosticsEvent>()
    private var startedAtElapsedMs: Long? = null

    fun markProcessStart() {
        recordOnce(PROCESS_START_EVENT)
    }

    fun markHomeLoadingShown() {
        recordOnce("home.loading.shown")
    }

    fun markHomeFirstFrame() {
        recordOnce("home.first_frame")
    }

    fun markStudyCtaVisible() {
        recordOnce("home.study_cta.visible")
    }

    fun markStudyTapReceived() {
        recordOnce("home.study_cta.tapped")
    }

    fun markStudyLoadingStarted() {
        recordOnce("study.loading.started")
    }

    fun markStudyCardUsable() {
        recordOnce("study.card.usable")
    }

    fun markStudyAnswerRevealed() {
        recordOnce("study.answer.revealed")
    }

    fun markStudyFeedbackShown() {
        recordOnce("study.feedback.shown")
    }

    fun markDictionaryLoaded() {
        recordOnce("dictionary.loaded")
    }

    fun markStrokeGuideLoaded() {
        recordOnce("stroke.loaded")
    }

    fun markStudyPrewarmStarted() {
        recordOnce("study.prewarm.started")
    }

    fun markStudyPrewarmDictionary() {
        recordOnce("study.prewarm.dictionary")
    }

    fun markStudyPrewarmStroke() {
        recordOnce("study.prewarm.stroke")
    }

    fun markStudyPrewarmFinished() {
        recordOnce("study.prewarm.finished")
    }

    fun snapshot(): TimingDiagnosticsSnapshot {
        synchronized(lock) {
            return TimingDiagnosticsSnapshot(
                startedAtElapsedMs = startedAtElapsedMs ?: 0L,
                events = events.toList(),
            )
        }
    }

    fun reset() {
        synchronized(lock) {
            seenNames.clear()
            events.clear()
            startedAtElapsedMs = null
        }
    }

    private fun recordOnce(name: String) {
        val now = clock()
        synchronized(lock) {
            if (startedAtElapsedMs == null) {
                startedAtElapsedMs = now
                if (name != PROCESS_START_EVENT && seenNames.add(PROCESS_START_EVENT)) {
                    events.add(TimingDiagnosticsEvent(PROCESS_START_EVENT, 0L))
                    logger("event=$PROCESS_START_EVENT elapsed_ms=0")
                }
            }
            if (!seenNames.add(name)) {
                return
            }
            val started = startedAtElapsedMs ?: now
            val elapsed = (now - started).coerceAtLeast(0L)
            events.add(TimingDiagnosticsEvent(name, elapsed))
            logger("event=$name elapsed_ms=$elapsed")
        }
    }

    companion object {
        private const val TAG = "KaniTiming"
        private const val PROCESS_START_EVENT = "process.start"
    }
}

internal object AppTimingDiagnostics {
    private val recorder = TimingDiagnosticsRecorder()

    fun markProcessStart() = recorder.markProcessStart()
    fun markHomeLoadingShown() = recorder.markHomeLoadingShown()
    fun markHomeFirstFrame() = recorder.markHomeFirstFrame()
    fun markStudyCtaVisible() = recorder.markStudyCtaVisible()
    fun markStudyTapReceived() = recorder.markStudyTapReceived()
    fun markStudyLoadingStarted() = recorder.markStudyLoadingStarted()
    fun markStudyCardUsable() = recorder.markStudyCardUsable()
    fun markStudyAnswerRevealed() = recorder.markStudyAnswerRevealed()
    fun markStudyFeedbackShown() = recorder.markStudyFeedbackShown()
    fun markDictionaryLoaded() = recorder.markDictionaryLoaded()
    fun markStrokeGuideLoaded() = recorder.markStrokeGuideLoaded()
    fun markStudyPrewarmStarted() = recorder.markStudyPrewarmStarted()
    fun markStudyPrewarmDictionary() = recorder.markStudyPrewarmDictionary()
    fun markStudyPrewarmStroke() = recorder.markStudyPrewarmStroke()
    fun markStudyPrewarmFinished() = recorder.markStudyPrewarmFinished()
    fun snapshot(): TimingDiagnosticsSnapshot = recorder.snapshot()
    fun reset() = recorder.reset()
}
