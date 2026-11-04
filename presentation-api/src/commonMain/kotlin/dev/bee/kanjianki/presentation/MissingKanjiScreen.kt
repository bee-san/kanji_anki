package dev.bee.kanjianki.presentation

/**
 * The Missing Kanji surface, as portable data both hosts render.
 *
 * The Android host drove this from `MainActivityMissingKanji` and a screen model with
 * a dozen callbacks; this is the same surface as one value, with the callbacks
 * replaced by [KaniAction.MissingKanji] intents. The collection scan, dictionary
 * eligibility, and Anki/CSV writing stay in `:core`/`:application`; a host maps its
 * scan report and operation results into this.
 *
 * [content] is the state machine — first run, provider-missing, scanning, error, or a
 * report — and [providerAvailability] plus [primaryAction] decide what the one primary
 * button does. [operationResult], when present, is the outcome dialog after an add,
 * create, export, or removal.
 */
data class MissingKanjiScreen(
    val content: MissingKanjiContent,
    val providerAvailability: MissingKanjiProvider,
    val primaryActionLabel: String,
    val primaryAction: KaniAction,
    val destinations: MissingKanjiDestinations = MissingKanjiDestinations(),
    val operationResult: MissingKanjiOperationResult? = null,
)

/** The screen's state-machine branch. */
sealed interface MissingKanjiContent {
    /** No scan yet: the first-run invitation. */
    data object FirstRun : MissingKanjiContent

    /** AnkiDroid is not installed (Android) or no provider is reachable (desktop). */
    data object ProviderMissing : MissingKanjiContent

    /** The provider needs permission granted before a scan can read it. */
    data object PermissionRequired : MissingKanjiContent

    /** A scan is running; [notesScanned]/[uniqueKanji] climb as it progresses. */
    data class Scanning(
        val notesScanned: Int,
        val uniqueKanji: Int,
        val skippedNotes: Int,
        val cancelling: Boolean,
    ) : MissingKanjiContent

    /** The scan or a read failed; [failureCode] is a copy key the surface resolves. */
    data class Error(val failureCode: String) : MissingKanjiContent

    /** A finished scan's report: the missing kanji and the filter over them. */
    data class Report(
        val summaryLine: String,
        val missingCountLine: String,
        val staleLine: String? = null,
        val rows: List<MissingKanjiRow>,
    ) : MissingKanjiContent
}

/** Whether the collection provider is usable for a scan. */
enum class MissingKanjiProvider {
    READY,
    NOT_INSTALLED,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
}

/**
 * One kanji missing from the user's collection.
 *
 * [selectable] rows can be ticked for a batch add/create/export; [inKani] marks one
 * already admitted, which the report shows and offers to remove.
 */
data class MissingKanjiRow(
    val literal: String,
    val meaning: String,
    val reading: String,
    val rankLine: String = "",
    val inKani: Boolean = false,
    val canRemove: Boolean = false,
) {
    init {
        require(literal.isNotBlank()) { "a missing-kanji row is about a kanji" }
    }

    /** Removing an admitted kanji from Kani's queue (Kani-side, not a collection write). */
    val removeAction: KaniAction
        get() = KaniAction.MissingKanji.Remove(literal = literal)
}

/**
 * The batch destinations for the selected rows.
 *
 * Each is enabled independently: add-to-Kani is always local; direct Anki creation is
 * capability-gated (absent on a provider that cannot accept notes) and CSV export is
 * the always-available fallback. [operationInProgress] disables them while a write
 * runs; [exportLine] narrates a running export.
 */
data class MissingKanjiDestinations(
    val addToKaniEnabled: Boolean = false,
    val createAnkiEnabled: Boolean = false,
    val csvExportEnabled: Boolean = false,
    val defaultDeckName: String = "",
    val operationInProgress: Boolean = false,
    val exportLine: String? = null,
) {
    fun addAction(selected: Set<String>): KaniAction =
        KaniAction.MissingKanji.AddToKani(literals = selected)

    fun createAnkiAction(selected: Set<String>): KaniAction =
        KaniAction.MissingKanji.CreateAnkiNotes(literals = selected, deckName = defaultDeckName)

    fun exportCsvAction(selected: Set<String>): KaniAction =
        KaniAction.MissingKanji.ExportCsv(literals = selected)
}

/** The outcome of a completed batch operation, shown as a dialog. */
sealed interface MissingKanjiOperationResult {
    val title: String
    val lines: List<String>

    data class Added(
        override val title: String,
        override val lines: List<String>,
    ) : MissingKanjiOperationResult

    data class Removed(
        override val title: String,
        override val lines: List<String>,
    ) : MissingKanjiOperationResult

    data class AnkiCreated(
        override val title: String,
        override val lines: List<String>,
        val csvFallbackAvailable: Boolean,
    ) : MissingKanjiOperationResult

    data class CsvExported(
        override val title: String,
        override val lines: List<String>,
    ) : MissingKanjiOperationResult

    data class Failed(
        override val title: String,
        override val lines: List<String>,
    ) : MissingKanjiOperationResult
}
