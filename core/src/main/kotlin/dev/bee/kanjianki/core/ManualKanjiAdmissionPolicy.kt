package dev.bee.kanjianki.core

import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * Converts durable dictionary-origin sources into ordinary dashboard rows.
 *
 * The dictionary cue is the literal plus an attested KANJIDIC2 reading. It is
 * deliberately marked as [SOURCE_DICTIONARY], so it cannot be mistaken for
 * active or suspended Anki evidence.
 */
object ManualKanjiAdmissionPolicy {
    const val SOURCE_DICTIONARY: String = "dictionary"
    const val REASON_CODE: String = "dictionary_origin"

    data class Plan(
        val rows: List<RecordsImportModels.DashboardRow>,
        val acceptedCandidates: List<MissingKanjiCandidate>,
        val missingMeaningLiterals: Set<String>,
        val missingReadingLiterals: Set<String>,
        val invalidLiterals: Set<String>,
        val duplicateCount: Int,
    ) {
        val skippedLiterals: Set<String>
            get() = LinkedHashSet<String>().apply {
                addAll(missingMeaningLiterals)
                addAll(missingReadingLiterals)
                addAll(invalidLiterals)
            }
    }

    data class AdditionPlan(
        val candidatesToAdd: List<MissingKanjiCandidate>,
        val alreadyInKaniLiterals: Set<String>,
        val missingMeaningLiterals: Set<String>,
        val missingReadingLiterals: Set<String>,
        val invalidLiterals: Set<String>,
        val duplicateCount: Int,
    )

    private data class CandidateAnalysis(
        val acceptedCandidates: List<MissingKanjiCandidate>,
        val missingMeaningLiterals: Set<String>,
        val missingReadingLiterals: Set<String>,
        val invalidLiterals: Set<String>,
        val duplicateCount: Int,
    )

    @JvmStatic
    fun plan(
        candidates: Iterable<MissingKanjiCandidate>,
        reasonText: String,
    ): Plan {
        val analysis = analyzeCandidates(candidates)
        val rows = analysis.acceptedCandidates.map { candidate -> dashboardRow(candidate, reasonText) }
        return Plan(
            rows = Collections.unmodifiableList(rows),
            acceptedCandidates = analysis.acceptedCandidates,
            missingMeaningLiterals = analysis.missingMeaningLiterals,
            missingReadingLiterals = analysis.missingReadingLiterals,
            invalidLiterals = analysis.invalidLiterals,
            duplicateCount = analysis.duplicateCount,
        )
    }

    private fun analyzeCandidates(
        candidates: Iterable<MissingKanjiCandidate>,
    ): CandidateAnalysis {
        val accepted = LinkedHashMap<String, MissingKanjiCandidate>()
        val missingMeaning = LinkedHashSet<String>()
        val missingReading = LinkedHashSet<String>()
        val invalid = LinkedHashSet<String>()
        val seen = HashSet<String>()
        var duplicateCount = 0
        for (candidate in candidates) {
            val literal = TextUtil.normalizeSingleKanji(candidate.literal)
            if (literal.isEmpty()) {
                invalid.add(candidate.literal.trim())
                continue
            }
            if (!seen.add(literal)) {
                duplicateCount += 1
            }
            val normalized = normalizeCandidate(candidate, literal)
            when {
                normalized.meanings.isEmpty() -> {
                    if (literal !in accepted) {
                        missingMeaning.add(literal)
                    }
                }
                preferredReading(normalized).isEmpty() -> {
                    if (literal !in accepted) {
                        missingReading.add(literal)
                    }
                }
                else -> {
                    accepted.putIfAbsent(literal, normalized)
                    missingMeaning.remove(literal)
                    missingReading.remove(literal)
                }
            }
        }
        val acceptedCandidates = accepted.values.sortedWith(CANDIDATE_COMPARATOR)
        return CandidateAnalysis(
            acceptedCandidates = Collections.unmodifiableList(acceptedCandidates),
            missingMeaningLiterals = immutableSet(missingMeaning),
            missingReadingLiterals = immutableSet(missingReading),
            invalidLiterals = immutableSet(invalid),
            duplicateCount = duplicateCount,
        )
    }

    @JvmStatic
    fun planAddition(
        candidates: Iterable<MissingKanjiCandidate>,
        existingStudyLiterals: Set<String>,
        activeManualLiterals: Set<String>,
    ): AdditionPlan {
        val admission = analyzeCandidates(candidates)
        val existing = HashSet<String>(existingStudyLiterals.size + activeManualLiterals.size)
        existingStudyLiterals.mapNotNullTo(existing) { literal ->
            TextUtil.normalizeSingleKanji(literal).takeIf(String::isNotEmpty)
        }
        activeManualLiterals.mapNotNullTo(existing) { literal ->
            TextUtil.normalizeSingleKanji(literal).takeIf(String::isNotEmpty)
        }
        val already = LinkedHashSet<String>()
        val additions = ArrayList<MissingKanjiCandidate>()
        for (candidate in admission.acceptedCandidates) {
            if (candidate.literal in existing) {
                already.add(candidate.literal)
            } else {
                additions.add(candidate)
            }
        }
        return AdditionPlan(
            candidatesToAdd = Collections.unmodifiableList(additions),
            alreadyInKaniLiterals = immutableSet(already),
            missingMeaningLiterals = admission.missingMeaningLiterals,
            missingReadingLiterals = admission.missingReadingLiterals,
            invalidLiterals = admission.invalidLiterals,
            duplicateCount = admission.duplicateCount,
        )
    }

    /**
     * Provider rows remain authoritative for Anki counts, examples and copy.
     * A dictionary example is appended only as explicit fallback/provenance.
     */
    @JvmStatic
    fun mergeRows(
        providerRows: List<RecordsImportModels.DashboardRow>,
        candidates: Iterable<MissingKanjiCandidate>,
        reasonText: String,
    ): List<RecordsImportModels.DashboardRow> {
        val plan = plan(candidates, reasonText)
        if (plan.rows.isEmpty()) {
            return providerRows
        }
        val byKanji = LinkedHashMap<String, RecordsImportModels.DashboardRow>(
            providerRows.size + plan.rows.size,
        )
        for (row in providerRows) {
            byKanji[row.kanji] = row
        }
        for (manualRow in plan.rows) {
            val provider = byKanji[manualRow.kanji]
            byKanji[manualRow.kanji] = if (provider == null) {
                manualRow
            } else {
                mergeProviderRow(provider, manualRow)
            }
        }
        return Collections.unmodifiableList(ArrayList(byKanji.values))
    }

    @JvmStatic
    fun hasDictionarySource(row: RecordsImportModels.DashboardRow?): Boolean =
        row?.examples?.any { example -> example.sourceType == SOURCE_DICTIONARY } == true

    @JvmStatic
    fun isDictionaryOnly(row: RecordsImportModels.DashboardRow?): Boolean =
        row != null &&
            row.examples.isNotEmpty() &&
            row.examples.all(::isDictionaryExample)

    @JvmStatic
    fun isDictionaryExample(example: RecordsImportModels.Example?): Boolean =
        example?.sourceType == SOURCE_DICTIONARY

    @JvmStatic
    fun preferredReading(candidate: MissingKanjiCandidate): String {
        for (reading in candidate.kunReadings) {
            normalizeKunReading(reading).takeIf(String::isNotEmpty)?.let { return it }
        }
        for (reading in candidate.onReadings) {
            normalizeReading(reading).takeIf(String::isNotEmpty)?.let { return it }
        }
        return ""
    }

    private fun dashboardRow(
        candidate: MissingKanjiCandidate,
        reasonText: String,
    ): RecordsImportModels.DashboardRow {
        val reading = preferredReading(candidate)
        val meaning = candidate.meanings.first()
        val example = RecordsImportModels.Example(
            SOURCE_DICTIONARY,
            0L,
            0L,
            candidate.literal,
            reading,
            meaning,
            "",
            false,
            0,
            0,
            0,
            null,
            null,
            null,
        )
        return RecordsImportModels.DashboardRow(
            candidate.literal,
            candidate.jitenRank,
            meaning,
            reading,
            candidate.literal,
            0,
            REASON_CODE,
            reasonText.trim(),
            0,
            0,
            0,
            listOf(example),
        )
    }

    private fun mergeProviderRow(
        provider: RecordsImportModels.DashboardRow,
        manual: RecordsImportModels.DashboardRow,
    ): RecordsImportModels.DashboardRow {
        val examples = ArrayList<RecordsImportModels.Example>(provider.examples.size + 1)
        examples.addAll(provider.examples)
        if (!hasDictionarySource(provider)) {
            examples.add(manual.examples.single())
        }
        return RecordsImportModels.DashboardRow(
            provider.kanji,
            provider.jitenRank ?: manual.jitenRank,
            provider.primaryMeaning.ifBlank { manual.primaryMeaning },
            provider.reading.ifBlank { manual.reading },
            provider.browserSearch.ifBlank { manual.browserSearch },
            provider.weaknessScore,
            provider.reasonCode,
            provider.reasonText,
            provider.activeExampleCount,
            provider.suspendedExampleCount,
            provider.matureSupportCount,
            examples,
        )
    }

    private fun normalizeCandidate(
        candidate: MissingKanjiCandidate,
        literal: String,
    ): MissingKanjiCandidate = MissingKanjiCandidate(
        literal = literal,
        meanings = normalizeValues(candidate.meanings),
        onReadings = normalizeValues(candidate.onReadings),
        kunReadings = normalizeValues(candidate.kunReadings),
        jitenRank = candidate.jitenRank?.takeIf { rank -> rank > 0 },
    )

    private fun normalizeValues(values: List<String>): List<String> =
        values.asSequence()
            .map(DictionaryLookup::normalize)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()

    private fun normalizeKunReading(value: String): String {
        val stem = normalizeReading(value).substringBefore('.')
        return stem.trim('-')
    }

    private fun normalizeReading(value: String): String =
        DictionaryLookup.normalize(value).replace(" ", "")

    private fun immutableSet(values: Collection<String>): Set<String> =
        Collections.unmodifiableSet(LinkedHashSet(values))

    private val CANDIDATE_COMPARATOR = compareBy<MissingKanjiCandidate>(
        { candidate -> candidate.jitenRank ?: Int.MAX_VALUE },
        MissingKanjiCandidate::literal,
    )
}
