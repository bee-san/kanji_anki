package dev.bee.kanjianki

import android.os.Trace

internal fun withUiTrace(section: String, action: () -> Unit) {
    if (section.isBlank()) {
        action()
        return
    }
    Trace.beginSection(section)
    try {
        action()
    } finally {
        Trace.endSection()
    }
}
