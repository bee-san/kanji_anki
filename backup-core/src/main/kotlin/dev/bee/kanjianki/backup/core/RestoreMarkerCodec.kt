package dev.bee.kanjianki.backup.core

/**
 * The pure restore-marker state machine, shared by Android and desktop. The
 * marker is a tiny `key=value` text file that records a durable commit intent
 * for an in-progress restore; startup classifies it to decide whether to
 * finish, block, or ignore. Only the content encoding/parsing lives here —
 * durable file I/O, atomic replace, and directory fsync stay in platform
 * adapters, so this stays a pure JVM state machine.
 *
 * Byte-compatible with the Android `BackupRestoreStager` marker: format `2`,
 * phase `safety_ready`, with a non-empty `source_name` and a non-negative
 * `staged_at`. A marker with `source_name` + `staged_at` but no `format`/`phase`
 * is a pre-versioned LEGACY marker; anything else is INVALID.
 */
object RestoreMarkerCodec {
    const val READY_FORMAT = "2"
    const val READY_PHASE = "safety_ready"
    const val MAX_MARKER_BYTES = 1_024L

    enum class MarkerState {
        MISSING,
        LEGACY,
        SAFETY_READY,
        INVALID,
    }

    /** The durable ready-marker payload. */
    data class ReadyMarker(
        val sourceName: String,
        val stagedAtMillis: Long,
    ) {
        init {
            require(sourceName.isNotEmpty()) { "ready marker source_name must not be empty" }
            require(stagedAtMillis >= 0L) { "ready marker staged_at must not be negative" }
        }
    }

    /** Serializes a versioned ready marker to its canonical `key=value` text. */
    fun encodeReady(marker: ReadyMarker): String =
        buildString {
            append("format=").append(READY_FORMAT).append('\n')
            append("phase=").append(READY_PHASE).append('\n')
            append("source_name=").append(marker.sourceName).append('\n')
            append("staged_at=").append(marker.stagedAtMillis).append('\n')
        }

    /**
     * Classifies raw marker text. [present] is false when no marker file
     * exists; [tooLargeOrUnreadable] is true when the file is absent-of-content,
     * over [MAX_MARKER_BYTES], or could not be read — all of which are INVALID
     * except a genuinely missing file, which is MISSING.
     */
    fun classify(
        present: Boolean,
        tooLargeOrUnreadable: Boolean,
        rawText: String?,
    ): MarkerState {
        if (!present) return MarkerState.MISSING
        if (tooLargeOrUnreadable || rawText == null) return MarkerState.INVALID
        val values = parse(rawText)
        val readySource = values["source_name"]?.trim()
        val readyStagedAt = values["staged_at"]?.toLongOrNull()
        if (values["format"] == READY_FORMAT &&
            values["phase"] == READY_PHASE &&
            !readySource.isNullOrEmpty() &&
            readyStagedAt != null &&
            readyStagedAt >= 0L
        ) {
            return MarkerState.SAFETY_READY
        }
        val legacyStagedAt = values["staged_at"]?.toLongOrNull()
        return if (values.containsKey("source_name") && legacyStagedAt != null &&
            !values.containsKey("format") && !values.containsKey("phase")
        ) {
            MarkerState.LEGACY
        } else {
            MarkerState.INVALID
        }
    }

    /** Reads a SAFETY_READY marker's fields, or null if the text is not ready. */
    fun readReady(rawText: String?): ReadyMarker? {
        if (rawText == null) return null
        if (classify(present = true, tooLargeOrUnreadable = false, rawText = rawText) != MarkerState.SAFETY_READY) {
            return null
        }
        val values = parse(rawText)
        return ReadyMarker(
            sourceName = requireNotNull(values["source_name"]).trim(),
            stagedAtMillis = requireNotNull(values["staged_at"]).toLong(),
        )
    }

    private fun parse(rawText: String): Map<String, String> =
        rawText.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
}
