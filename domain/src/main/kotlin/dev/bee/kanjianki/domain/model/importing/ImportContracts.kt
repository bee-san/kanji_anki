package dev.bee.kanjianki.domain.model.importing

enum class ImportSource(val wireName: String) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    TAGGED("tagged"),
    WEAK("weak"),
    BROWSER_QUERY("browser_query");

    companion object {
        fun fromWireName(wireName: String): ImportSource =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown import source: $wireName")
    }
}

enum class NewCardSortMode(val wireName: String) {
    FREQUENCY("frequency"),
    FSRS_DIFFICULTY("fsrs_difficulty"),
    RETRIEVABILITY_RISK("retrievability_risk"),
    KANI_WEAKNESS("kani_weakness");

    companion object {
        val default: NewCardSortMode = FREQUENCY

        fun fromWireName(wireName: String): NewCardSortMode =
            entries.firstOrNull { it.wireName == wireName } ?: default
    }
}

data class NoteTypeMapping(
    val noteTypeName: String,
    val templateName: String,
    val expressionField: String,
    val readingField: String,
    val meaningField: String,
    val sentenceField: String,
    val frequencyField: String,
    val frequencySortField: String,
) {
    init {
        require(noteTypeName.isNotBlank()) { "noteTypeName must not be blank" }
        require(templateName.isNotBlank()) { "templateName must not be blank" }
        require(expressionField.isNotBlank()) { "expressionField must not be blank" }
        require(readingField.isNotBlank()) { "readingField must not be blank" }
        require(meaningField.isNotBlank()) { "meaningField must not be blank" }
        require(sentenceField.isNotBlank()) { "sentenceField must not be blank" }
        require(frequencyField.isNotBlank()) { "frequencyField must not be blank" }
        require(frequencySortField.isNotBlank()) { "frequencySortField must not be blank" }
    }

    companion object {
        val kikuDefault = NoteTypeMapping(
            noteTypeName = "Kiku",
            templateName = "Mining",
            expressionField = "Expression",
            readingField = "ExpressionReading",
            meaningField = "MainDefinition",
            sentenceField = "Sentence",
            frequencyField = "Frequency",
            frequencySortField = "FreqSort",
        )
    }
}

data class ImportSettings(
    val noteMapping: NoteTypeMapping = NoteTypeMapping.kikuDefault,
    val matureDays: Int = 21,
    val matureSupportThreshold: Int = 2,
    val importActiveCards: Boolean = false,
    val importSuspendedCards: Boolean = true,
    val importTaggedCards: Boolean = false,
    val importTags: List<String> = emptyList(),
    val importWeakCards: Boolean = false,
    val importWeakFsrsDifficultyThreshold: Double = 7.0,
    val importWeakLapsesThreshold: Int = 2,
    val importMinMatchingCardsPerKanji: Int = 1,
    val importBrowserQueryCards: Boolean = false,
    val importBrowserQuery: String = "",
    val suspendedRankMin: Int = 100,
    val suspendedRankMax: Int = 3000,
    val newCardSortMode: NewCardSortMode = NewCardSortMode.default,
) {
    init {
        require(matureDays in 1..3650) { "matureDays must be in 1..3650" }
        require(matureSupportThreshold in 1..100) { "matureSupportThreshold must be in 1..100" }
        require(suspendedRankMin in 1..20_000) { "suspendedRankMin must be in 1..20000" }
        require(suspendedRankMax in 1..20_000) { "suspendedRankMax must be in 1..20000" }
        require(suspendedRankMin <= suspendedRankMax) {
            "suspendedRankMin must be less than or equal to suspendedRankMax"
        }
        require(importWeakFsrsDifficultyThreshold in 1.0..10.0) {
            "importWeakFsrsDifficultyThreshold must be in 1.0..10.0"
        }
        require(importWeakLapsesThreshold in 1..100) {
            "importWeakLapsesThreshold must be in 1..100"
        }
        require(importMinMatchingCardsPerKanji in 1..1000) {
            "importMinMatchingCardsPerKanji must be in 1..1000"
        }
    }

    val enabledSources: Set<ImportSource>
        get() = buildSet {
            if (importActiveCards) add(ImportSource.ACTIVE)
            if (importSuspendedCards) add(ImportSource.SUSPENDED)
            if (importTaggedCards && importTags.isNotEmpty()) add(ImportSource.TAGGED)
            if (importWeakCards) add(ImportSource.WEAK)
            if (importBrowserQueryCards && importBrowserQuery.isNotBlank()) {
                add(ImportSource.BROWSER_QUERY)
            }
        }
}
