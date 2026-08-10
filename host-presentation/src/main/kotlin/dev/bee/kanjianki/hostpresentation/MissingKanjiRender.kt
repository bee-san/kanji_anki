package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.MissingKanjiReport

/**
 * The Missing Kanji scan state a host hands the route loader.
 *
 * A sealed hierarchy rather than a nullable bundle of fields, because the four states
 * carry genuinely different data and the combinations that a flat record would allow —
 * scanning with a report, failed with progress counts — do not exist. Making them
 * unrepresentable is cheaper than checking for them.
 *
 * Held by the host rather than derived from persisted state on purpose: a scan is a
 * long-running operation with progress and cancellation, and Kani deliberately does not
 * persist a partial one. A route load during a scan must show that scan, and a route
 * load after navigating away must not resurrect a finished report.
 */
sealed interface MissingKanjiRender {
    /**
     * No scan has run in this session.
     *
     * [dictionaryAvailable] is the host's answer, because only it knows whether the
     * reference assets were unpacked into this install. False renders the
     * dictionary-unavailable failure rather than inviting a scan that could only produce
     * an empty report.
     */
    data class Idle(val dictionaryAvailable: Boolean) : MissingKanjiRender

    /** A scan in progress, with the counts it has reached. */
    data class Scanning(
        val notesScanned: Int,
        val uniqueKanji: Int,
        val skippedNotes: Int,
        val cancelling: Boolean = false,
    ) : MissingKanjiRender

    /** A scan or read that failed; [failureCode] is a copy key, never a raw message. */
    data class Failed(val failureCode: String) : MissingKanjiRender {
        init {
            require(failureCode.isNotBlank()) { "a failure needs a code the copy can resolve" }
        }
    }

    /**
     * A finished report.
     *
     * [admittedKanji] is passed rather than folded into the report because admission is
     * Kani-side state that changes without rescanning: admitting a kanji must update the
     * row without invalidating the scan that found it.
     */
    data class Ready(
        val report: MissingKanjiReport,
        val admittedKanji: Set<String> = emptySet(),
        val canCreateAnkiNotes: Boolean = false,
        val defaultDeckName: String = "",
        val staleReason: String? = null,
        val operationInProgress: Boolean = false,
    ) : MissingKanjiRender
}
