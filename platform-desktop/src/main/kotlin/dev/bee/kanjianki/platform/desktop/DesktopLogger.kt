package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppLogEvent
import dev.bee.kanjianki.platform.AppLogLevel
import dev.bee.kanjianki.platform.AppLogger
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Desktop's [AppLogger]: formats one line per event and hands it to a sink.
 *
 * The sink is injected rather than hard-wired to `System.err` so the composition
 * root decides where logs go (console during development, a rotating file when the
 * user enables `debugLogEnabled`) and so this class is testable without capturing
 * process streams.
 *
 * Two things are deliberate about the formatting:
 *
 *  - **[AppLogLevel.DEBUG] is dropped unless [debugEnabled].** Kani's debug log is
 *    an opt-in device setting on Android and must stay opt-in here. Filtering at
 *    the logger rather than at each call site means a new caller cannot leak
 *    verbose output by forgetting to check the setting.
 *  - **Stack traces are rendered inline, not `printStackTrace()`d.** A cause
 *    printed separately interleaves with other threads' output, which turns a
 *    user-submitted log into an unreadable braid exactly when it matters.
 *
 * No redaction happens here, because nothing this module can see is a secret:
 * `SecretValue` and `SecretReference` both render as `[REDACTED]` in their own
 * `toString`, and `PlatformFileReference` hides its opaque id the same way. A
 * logger that tried to scrub messages after the fact would be guessing; the
 * contracts already refuse to hand out the sensitive form.
 */
class DesktopLogger(
    private val debugEnabled: () -> Boolean = { false },
    private val sink: (String) -> Unit = System.err::println,
) : AppLogger {
    override fun log(event: AppLogEvent) {
        if (event.level == AppLogLevel.DEBUG && !debugEnabled()) return
        sink(format(event))
    }

    private fun format(event: AppLogEvent): String = buildString {
        append('[')
        append(event.level.name)
        append("] ")
        append(event.message)
        event.cause?.let { cause ->
            append(System.lineSeparator())
            append(stackTrace(cause))
        }
    }

    private fun stackTrace(cause: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use(cause::printStackTrace)
        return writer.toString().trimEnd()
    }
}
