package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.MissingKanjiAnalysisResult
import dev.bee.kanjianki.core.MissingKanjiAnalyzer
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiReport
import java.util.Locale

internal enum class MissingKanjiPreset(
    val storedValue: String,
    val range: MissingKanjiFrequencyRange?,
) {
    TOP_1000("top_1000", MissingKanjiFrequencyRange.TOP_1000),
    TOP_2000("top_2000", MissingKanjiFrequencyRange.TOP_2000),
    TOP_3000("top_3000", MissingKanjiFrequencyRange.TOP_3000),
    TOP_5000("top_5000", MissingKanjiFrequencyRange.TOP_5000),
    CUSTOM("custom", null);

    companion object {
        fun fromStored(value: String?): MissingKanjiPreset =
            entries.firstOrNull { preset -> preset.storedValue == value } ?: TOP_2000
    }
}

internal enum class MissingKanjiProviderAvailability {
    READY,
    NOT_INSTALLED,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
}

internal enum class MissingKanjiPrimaryAction {
    SCAN,
    SCAN_AGAIN,
    GRANT_PERMISSION,
    INSTALL_ANKIDROID,
    RETRY,
}

internal enum class MissingKanjiStaleReason(val copyKey: String) {
    AGE("age"),
    FAILED("failed"),
    CANCELLED("cancelled"),
}

internal sealed interface MissingKanjiContentModel {
    data object FirstRun : MissingKanjiContentModel

    data object AnkiDroidMissing : MissingKanjiContentModel

    data object PermissionRequired : MissingKanjiContentModel

    data class Scanning(
        val progress: MissingKanjiScanProgressState,
    ) : MissingKanjiContentModel

    data class Error(
        val failureCode: String,
    ) : MissingKanjiContentModel

    data class Report(
        val report: MissingKanjiReportUiModel,
    ) : MissingKanjiContentModel
}

internal class MissingKanjiScanProgressState {
    var notesScanned by mutableIntStateOf(0)
        private set
    var uniqueKanjiCount by mutableIntStateOf(0)
        private set
    var skippedNotes by mutableIntStateOf(0)
        private set
    var isCancelling by mutableStateOf(false)
        private set

    fun update(
        notesScanned: Int,
        uniqueKanjiCount: Int,
        skippedNotes: Int,
    ) {
        this.notesScanned = notesScanned.coerceAtLeast(0)
        this.uniqueKanjiCount = uniqueKanjiCount.coerceAtLeast(0)
        this.skippedNotes = skippedNotes.coerceAtLeast(0)
    }

    fun markCancelling() {
        isCancelling = true
    }
}

internal data class MissingKanjiFrequencyModel(
    val preset: MissingKanjiPreset,
    val range: MissingKanjiFrequencyRange,
    val searchQuery: String,
)

internal data class MissingKanjiScanSummaryModel(
    val scanId: Long,
    val completedAtMillis: Long,
    val notesScanned: Int,
    val uniqueAnkiKanjiCount: Int,
    val skippedNotes: Int,
)

internal data class MissingKanjiReportUiModel(
    val reportKey: String,
    val scan: MissingKanjiScanSummaryModel,
    val eligibleDictionaryKanjiCount: Int,
    val missingKanjiCount: Int,
    val rows: List<MissingKanjiRowModel>,
    val staleReason: MissingKanjiStaleReason?,
) {
    init {
        require(eligibleDictionaryKanjiCount >= 0)
        require(missingKanjiCount >= 0)
        require(missingKanjiCount == rows.size)
    }
}

internal data class MissingKanjiRowModel(
    val literal: String,
    val meanings: List<String>,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val jitenRank: Int?,
    internal val normalizedSearchText: String,
    val inKani: Boolean = false,
    val canRemoveFromKani: Boolean = false,
) {
    val primaryMeaning: String
        get() = meanings.firstOrNull().orEmpty()

    val primaryReading: String
        get() = kunReadings.firstOrNull() ?: onReadings.firstOrNull().orEmpty()
}

internal data class MissingKanjiDestinationModel(
    val addToKaniEnabled: Boolean = false,
    val createAnkiDeckEnabled: Boolean = false,
    val csvExportEnabled: Boolean = false,
    val defaultDeckName: String = "Kani::Missing Kanji",
    val newPerDay: Int = 0,
    val operationInProgress: Boolean = false,
    val exportProgress: MissingKanjiExportProgressState? = null,
    val onAddToKani: (Set<String>) -> Unit = {},
    val onCreateAnkiDeck: (Set<String>, String) -> Unit = { _, _ -> },
    val onExportCsv: (Set<String>) -> Unit = {},
    val onCancelExport: () -> Unit = {},
    val onRemoveFromKani: (String) -> Unit = {},
)

internal class MissingKanjiExportProgressState {
    var totalCount by mutableIntStateOf(0)
        private set
    var processedCount by mutableIntStateOf(0)
        private set
    var createdCount by mutableIntStateOf(0)
        private set
    var alreadyPresentCount by mutableIntStateOf(0)
        private set
    var isCancelling by mutableStateOf(false)
        private set

    fun update(
        totalCount: Int,
        processedCount: Int,
        createdCount: Int,
        alreadyPresentCount: Int,
    ) {
        this.totalCount = totalCount.coerceAtLeast(0)
        this.processedCount = processedCount.coerceIn(0, this.totalCount)
        this.createdCount = createdCount.coerceAtLeast(0)
        this.alreadyPresentCount = alreadyPresentCount.coerceAtLeast(0)
    }

    fun markCancelling() {
        isCancelling = true
    }
}

internal sealed interface MissingKanjiOperationResultModel {
    data class KaniAdmission(
        val requestedCount: Int,
        val addedCount: Int,
        val alreadyInKaniCount: Int,
        val skippedMissingMeaningCount: Int,
        val skippedMissingReadingCount: Int,
        val invalidCount: Int,
        val admittedNowCount: Int,
        val deferredCount: Int,
    ) : MissingKanjiOperationResultModel

    data class KaniRemoval(
        val literal: String,
        val removed: Boolean,
        val reviewed: Boolean,
    ) : MissingKanjiOperationResultModel

    data class AnkiExport(
        val deckName: String,
        val createdCount: Int,
        val alreadyPresentCount: Int,
        val skippedCount: Int,
        val unfinishedCount: Int,
        val failureCode: String?,
        val csvFallbackAvailable: Boolean,
    ) : MissingKanjiOperationResultModel

    data class CsvExport(
        val exportedCount: Int,
        val skippedCount: Int,
        val fileName: String,
    ) : MissingKanjiOperationResultModel

    data object Failed : MissingKanjiOperationResultModel
}

internal data class MissingKanjiScreenModel(
    val content: MissingKanjiContentModel,
    val providerAvailability: MissingKanjiProviderAvailability,
    val frequency: MissingKanjiFrequencyModel,
    val primaryAction: MissingKanjiPrimaryAction,
    val onHome: () -> Unit,
    val onPrimaryAction: () -> Unit,
    val onCancelScan: () -> Unit,
    val onRangeApplied: (MissingKanjiPreset, MissingKanjiFrequencyRange) -> Unit,
    val onRangePreview: (MissingKanjiFrequencyRange, (Int) -> Unit) -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val initialSelectedLiterals: Set<String> = emptySet(),
    val destinations: MissingKanjiDestinationModel = MissingKanjiDestinationModel(),
    val operationResult: MissingKanjiOperationResultModel? = null,
    val onDismissOperationResult: () -> Unit = {},
    val onStudyNow: () -> Unit = {},
    val onExportCsvFallback: () -> Unit = {},
)

internal sealed interface MissingKanjiRangeInputResult {
    data class Valid(
        val range: MissingKanjiFrequencyRange,
    ) : MissingKanjiRangeInputResult

    data class Invalid(
        val reason: String,
    ) : MissingKanjiRangeInputResult
}

internal fun parseMissingKanjiRange(
    minimumText: String,
    maximumText: String,
    includeUnranked: Boolean,
): MissingKanjiRangeInputResult {
    val minimum = parseRank(minimumText)
        ?: return MissingKanjiRangeInputResult.Invalid("positive")
    val maximum = parseRank(maximumText)
        ?: return MissingKanjiRangeInputResult.Invalid("positive")
    if (minimum > maximum) {
        return MissingKanjiRangeInputResult.Invalid("inverted")
    }
    return MissingKanjiRangeInputResult.Valid(
        MissingKanjiFrequencyRange(
            minimumRank = minimum,
            maximumRank = maximum,
            includeUnranked = includeUnranked,
        ),
    )
}

private fun parseRank(value: String): Int? {
    val normalized = value.trim()
    if (normalized.isEmpty() || normalized.length > MAX_RANK_DIGITS) {
        return null
    }
    return normalized.toIntOrNull()?.takeIf { rank -> rank >= 1 }
}

internal fun missingKanjiRows(
    candidates: List<MissingKanjiCandidate>,
    activeManualLiterals: Set<String> = emptySet(),
    removableManualLiterals: Set<String> = emptySet(),
): List<MissingKanjiRowModel> {
    return candidates.map { candidate ->
        val searchValues = buildList {
            add(candidate.literal)
            addAll(candidate.meanings)
            addAll(candidate.onReadings)
            addAll(candidate.kunReadings)
            candidate.jitenRank?.let { rank -> add(rank.toString()) }
        }
        MissingKanjiRowModel(
            literal = candidate.literal,
            meanings = candidate.meanings,
            onReadings = candidate.onReadings,
            kunReadings = candidate.kunReadings,
            jitenRank = candidate.jitenRank,
            normalizedSearchText = searchValues.joinToString("\u0000").lowercase(Locale.ROOT),
            inKani = candidate.literal in activeManualLiterals,
            canRemoveFromKani = candidate.literal in removableManualLiterals,
        )
    }
}

internal fun filterMissingKanjiRows(
    rows: List<MissingKanjiRowModel>,
    query: String,
): List<MissingKanjiRowModel> {
    val terms = query
        .trim()
        .lowercase(Locale.ROOT)
        .split(WHITESPACE)
        .filter(String::isNotEmpty)
    if (terms.isEmpty()) {
        return rows
    }
    return rows.filter { row ->
        terms.all(row.normalizedSearchText::contains)
    }
}

internal object MissingKanjiReportLoader {
    private const val DEFAULT_PAGE_SIZE = 500

    fun load(
        dictionary: DictionaryLookup,
        observedKanji: Set<String>,
        range: MissingKanjiFrequencyRange,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): MissingKanjiReport {
        require(MissingKanjiAnalyzer.validateRange(range) == null) {
            "Missing Kanji frequency range is invalid."
        }
        val boundedPageSize = pageSize.coerceIn(1, DictionaryLookup.MAX_KANJI_PAGE_SIZE)
        val dictionaryRange = DictionaryLookup.JitenRankRange(
            minimumRank = range.minimumRank,
            maximumRank = range.maximumRank,
            includeUnranked = range.includeUnranked,
        )
        val candidates = ArrayList<MissingKanjiCandidate>()
        var offset = 0
        var expectedTotal: Int? = null
        while (true) {
            check(!Thread.currentThread().isInterrupted) {
                "Missing Kanji report loading was cancelled."
            }
            val page = dictionary.kanjiByJitenRank(
                range = dictionaryRange,
                offset = offset,
                limit = boundedPageSize,
            )
            val knownTotal = expectedTotal
            if (knownTotal == null) {
                expectedTotal = page.totalEligible.coerceAtLeast(0)
                candidates.ensureCapacity(requireNotNull(expectedTotal))
            } else {
                check(page.totalEligible == knownTotal) {
                    "Dictionary candidate total changed while paging."
                }
            }
            page.entries.forEach { entry ->
                candidates.add(
                    MissingKanjiCandidate(
                        literal = entry.literal,
                        meanings = entry.meanings,
                        onReadings = entry.onReadings,
                        kunReadings = entry.kunReadings,
                        jitenRank = entry.jitenRank,
                    ),
                )
            }
            val nextOffset = page.nextOffset ?: break
            check(page.entries.isNotEmpty() && nextOffset > offset) {
                "Dictionary candidate paging did not advance."
            }
            offset = nextOffset
        }
        check(expectedTotal == candidates.size) {
            "Dictionary candidate paging returned ${candidates.size} of $expectedTotal rows."
        }
        return when (
            val analysis = MissingKanjiAnalyzer.analyze(
                dictionaryCandidates = candidates,
                observedKanji = observedKanji,
                range = range,
            )
        ) {
            is MissingKanjiAnalysisResult.Success -> analysis.report
            is MissingKanjiAnalysisResult.InvalidRange ->
                error("Validated Missing Kanji range was rejected: ${analysis.error}.")
        }
    }
}

private const val MAX_RANK_DIGITS = 9
private val WHITESPACE = Regex("\\s+")
