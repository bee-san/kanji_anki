package dev.bee.kanjianki.core

data class MissingKanjiCandidate(
    val literal: String,
    val meanings: List<String> = emptyList(),
    val onReadings: List<String> = emptyList(),
    val kunReadings: List<String> = emptyList(),
    val jitenRank: Int? = null,
) {
    val primaryMeaning: String
        get() = meanings.firstOrNull().orEmpty()

    val primaryReading: String
        get() = kunReadings.firstOrNull() ?: onReadings.firstOrNull().orEmpty()
}

data class MissingKanjiFrequencyRange(
    val minimumRank: Int,
    val maximumRank: Int,
    val includeUnranked: Boolean = false,
) {
    companion object {
        val TOP_1000 = MissingKanjiFrequencyRange(1, 1_000)
        val TOP_2000 = MissingKanjiFrequencyRange(1, 2_000)
        val TOP_3000 = MissingKanjiFrequencyRange(1, 3_000)
        val TOP_5000 = MissingKanjiFrequencyRange(1, 5_000)
    }
}

enum class MissingKanjiRangeError {
    MINIMUM_BELOW_ONE,
    MAXIMUM_BELOW_ONE,
    MINIMUM_ABOVE_MAXIMUM,
}

sealed interface MissingKanjiAnalysisResult {
    data class Success(val report: MissingKanjiReport) : MissingKanjiAnalysisResult

    data class InvalidRange(
        val range: MissingKanjiFrequencyRange,
        val error: MissingKanjiRangeError,
    ) : MissingKanjiAnalysisResult
}

data class MissingKanjiReport(
    val range: MissingKanjiFrequencyRange,
    val missing: List<MissingKanjiCandidate>,
    val uniqueObservedKanjiCount: Int,
    val uniqueDictionaryKanjiCount: Int,
    val eligibleDictionaryKanjiCount: Int,
    val eligibleRankedKanjiCount: Int,
    val eligibleUnrankedKanjiCount: Int,
    val presentEligibleKanjiCount: Int,
    val invalidObservedValueCount: Int,
    val invalidDictionaryCandidateCount: Int,
) {
    val missingKanjiCount: Int
        get() = missing.size
}

// --- Missing Kanji persistence DTOs -----------------------------------------
// Promoted from the app's MissingKanjiStore so the :data-api MissingKanji
// repository contract and both its implementations (:data-sql and the legacy
// :app LocalStore adapter) share one set of types.

enum class MissingKanjiScanStatus(val storedValue: String) {
    SUCCESS("success"),
    FAILED("failed"),
    CANCELLED("cancelled");

    companion object {
        fun fromStored(value: String?): MissingKanjiScanStatus =
            entries.firstOrNull { it.storedValue == value } ?: FAILED
    }
}

data class MissingKanjiScanRecord(
    val id: Long,
    val startedAt: Long,
    val completedAt: Long,
    val status: MissingKanjiScanStatus,
    val notesScanned: Int,
    val fieldsScanned: Int,
    val uniqueKanjiCount: Int,
    val skippedNotes: Int,
    val modelCount: Int,
    val providerFingerprint: String,
    val failureCode: String,
)

data class StoredAnkiKanjiInventory(
    val scan: MissingKanjiScanRecord,
    val literals: Set<String>,
)

data class MissingKanjiInventoryState(
    val published: StoredAnkiKanjiInventory?,
    val latestAttempt: MissingKanjiScanRecord?,
) {
    val isStale: Boolean
        get() = latestAttempt != null &&
            (published == null || latestAttempt.id != published.scan.id)
}

data class MissingKanjiPreferences(
    val preset: String = PRESET_TOP_2000,
    val range: MissingKanjiFrequencyRange = MissingKanjiFrequencyRange.TOP_2000,
    val searchQuery: String = "",
) {
    companion object {
        const val PRESET_TOP_1000 = "top_1000"
        const val PRESET_TOP_2000 = "top_2000"
        const val PRESET_TOP_3000 = "top_3000"
        const val PRESET_TOP_5000 = "top_5000"
        const val PRESET_CUSTOM = "custom"

        val SUPPORTED_PRESETS = setOf(
            PRESET_TOP_1000,
            PRESET_TOP_2000,
            PRESET_TOP_3000,
            PRESET_TOP_5000,
            PRESET_CUSTOM,
        )
    }
}

data class ManualKanjiSource(
    val candidate: MissingKanjiCandidate,
    val sourceType: String,
    val addedAt: Long,
    val updatedAt: Long,
    val active: Boolean,
) {
    companion object {
        const val SOURCE_TYPE_DICTIONARY = "dictionary"
    }
}

data class ManualKanjiSourceWriteResult(
    val requestedCount: Int,
    val addedLiterals: Set<String>,
    val reactivatedLiterals: Set<String>,
    val alreadyActiveLiterals: Set<String>,
    val missingMeaningLiterals: Set<String>,
    val missingReadingLiterals: Set<String>,
    val invalidCount: Int,
    val duplicateCount: Int,
)

data class ManualKanjiSourceRemovalResult(
    val requestedCount: Int,
    val removedLiterals: Set<String>,
    val reviewedLiterals: Set<String>,
    val inactiveLiterals: Set<String>,
    val invalidCount: Int,
)

data class MissingKanjiExportReceipt(
    val literal: String,
    val destinationKey: String,
    val exportedAt: Long,
    val externalNoteId: Long?,
)
