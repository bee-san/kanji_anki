package dev.bee.kanjianki

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.StudyTextCopy
import java.util.Locale

internal const val STUDY_ANSWER_USED_IN_ANKI_VISIBLE_ROW_LIMIT: Int = 3

private const val MAX_LABEL_LENGTH = 32

internal enum class StudyAnswerSectionContentState {
    READY,
    EMPTY,
    UNAVAILABLE,
}

internal data class StudyAnswerDetailSectionModel<T>(
    val label: String,
    val summary: String,
    val contentState: StudyAnswerSectionContentState,
    val body: T? = null,
    val emptyTitle: String? = null,
    val emptyBody: String? = null,
)

internal data class StudyAnswerKanjiDetailsModel(
    val kanji: String,
    val details: StudyAnswerDetailSectionModel<StudyAnswerDictionaryMetadataModel>,
    val breakdown: StudyAnswerDetailSectionModel<StudyAnswerBreakdownModel>,
    val strokeOrder: StudyAnswerDetailSectionModel<StudyAnswerStrokeOrderModel>,
    val usedInAnki: StudyAnswerDetailSectionModel<StudyAnswerUsedInAnkiModel>,
    val whyThisCard: StudyAnswerDetailSectionModel<StudyAnswerWhyThisCardModel>,
)

internal data class StudyAnswerReadingGroupModel(
    val label: String,
    val readings: List<String>,
)

internal data class StudyAnswerDictionaryMetadataModel(
    val meanings: List<String>,
    val readingGroups: List<StudyAnswerReadingGroupModel>,
    val strokeCount: Int?,
    val grade: Int?,
    val radical: Int?,
    val frequency: Int?,
    val jitenRank: Int?,
)

internal data class StudyAnswerBreakdownModel(
    val radicalNumber: Int?,
    val componentRows: List<String>,
    val fallbackCopy: String? = null,
)

enum class StudyAnswerStrokeOrderAvailability {
    COUNT_ONLY,
    ASSET_AVAILABLE,
    UNAVAILABLE,
}

internal data class StudyAnswerStrokeOrderModel(
    val strokeCount: Int?,
    val availability: StudyAnswerStrokeOrderAvailability,
    val assetReference: String? = null,
    val fallbackCopy: String? = null,
)

internal data class StudyAnswerUsedInAnkiRowModel(
    val expression: String,
    val reading: String,
    val meaning: String,
    val noteId: Long?,
    val cardId: Long?,
    val sourceLabel: String? = null,
    val deckLabel: String? = null,
    val modelLabel: String? = null,
    val isPrimarySource: Boolean,
    val tapAction: StudyAnswerAnkiTapActionModel,
) {
    val labels: List<String>
        get() = listOfNotNull(sourceLabel, deckLabel, modelLabel)
            .distinctBy { it.lowercase(Locale.ROOT) }
}

internal data class StudyAnswerUsedInAnkiModel(
    val rows: List<StudyAnswerUsedInAnkiRowModel>,
    val visibleRows: List<StudyAnswerUsedInAnkiRowModel>,
    val visibleRowLimit: Int,
    val showAll: Boolean,
    val hiddenRowCount: Int,
    val toggleLabel: String? = null,
)

internal enum class StudyAnswerAnkiCopiedIdKind {
    NOTE,
    CARD,
}

internal sealed class StudyAnswerAnkiTapActionModel {
    data class OpenAnkiDroid(
        val noteId: Long?,
        val cardId: Long?,
    ) : StudyAnswerAnkiTapActionModel()

    data class CopyId(
        val kind: StudyAnswerAnkiCopiedIdKind,
        val value: Long,
        val toastMessage: String,
    ) : StudyAnswerAnkiTapActionModel()

    object Unavailable : StudyAnswerAnkiTapActionModel()

    companion object {
        @JvmStatic
        fun from(
            noteId: Long?,
            cardId: Long?,
            openAnkiDroidSupported: Boolean,
        ): StudyAnswerAnkiTapActionModel {
            val safeNoteId = positiveId(noteId)
            val safeCardId = positiveId(cardId)
            if (openAnkiDroidSupported && (safeNoteId != null || safeCardId != null)) {
                return OpenAnkiDroid(safeNoteId, safeCardId)
            }
            if (safeNoteId != null) {
                return CopyId(
                    StudyAnswerAnkiCopiedIdKind.NOTE,
                    safeNoteId,
                    StudyTextCopy.studyAnswerAnkiNoteIdCopiedMessage(),
                )
            }
            if (safeCardId != null) {
                return CopyId(
                    StudyAnswerAnkiCopiedIdKind.CARD,
                    safeCardId,
                    StudyTextCopy.studyAnswerAnkiCardIdCopiedMessage(),
                )
            }
            return Unavailable
        }
    }
}

internal data class StudyAnswerWhyThisCardExampleModel(
    val expression: String,
    val reading: String,
    val meaning: String,
)

internal data class StudyAnswerWhyThisCardModel(
    val sourceExpression: String?,
    val sourceReading: String?,
    val previewExamples: List<StudyAnswerWhyThisCardExampleModel>,
    val fallbackCopy: String? = null,
)

internal fun studyAnswerKanjiDetailsModel(
    kanji: String,
    dictionaryEntry: DictionaryLookup.KanjiEntry?,
    examples: List<RecordsImportModels.Example>,
    currentExample: RecordsImportModels.Example? = null,
    showAllUsedInAnki: Boolean = false,
    openAnkiDroidSupported: Boolean = false,
    deckNamesByCardId: Map<Long, String> = emptyMap(),
    modelNamesByNoteId: Map<Long, String> = emptyMap(),
    strokeOrderAssetAvailable: Boolean = false,
    strokeOrderAssetReference: String? = null,
    breakdownComponentRows: List<String> = emptyList(),
): StudyAnswerKanjiDetailsModel {
    val normalizedKanji = normalizeBodyText(kanji) ?: kanji.trim()
    val details = studyAnswerDictionarySection(dictionaryEntry)
    val breakdown = studyAnswerBreakdownSection(dictionaryEntry, breakdownComponentRows)
    val strokeOrder = studyAnswerStrokeOrderSection(
        dictionaryEntry = dictionaryEntry,
        strokeOrderAssetAvailable = strokeOrderAssetAvailable,
        strokeOrderAssetReference = strokeOrderAssetReference,
    )
    val usedInAnki = studyAnswerUsedInAnkiSection(
        examples = examples,
        currentExample = currentExample,
        showAll = showAllUsedInAnki,
        openAnkiDroidSupported = openAnkiDroidSupported,
        deckNamesByCardId = deckNamesByCardId,
        modelNamesByNoteId = modelNamesByNoteId,
    )
    val whyThisCard = studyAnswerWhyThisCardSection(
        examples = examples,
        currentExample = currentExample,
    )
    return StudyAnswerKanjiDetailsModel(
        kanji = normalizedKanji,
        details = details,
        breakdown = breakdown,
        strokeOrder = strokeOrder,
        usedInAnki = usedInAnki,
        whyThisCard = whyThisCard,
    )
}

internal fun studyAnswerDictionarySection(
    dictionaryEntry: DictionaryLookup.KanjiEntry?,
): StudyAnswerDetailSectionModel<StudyAnswerDictionaryMetadataModel> {
    if (dictionaryEntry == null) {
        return StudyAnswerDetailSectionModel(
            label = StudyTextCopy.studyAnswerDetailsLabel(),
            summary = StudyTextCopy.studyAnswerLocalDictionarySummary(),
            contentState = StudyAnswerSectionContentState.EMPTY,
            body = null,
            emptyTitle = StudyTextCopy.studyAnswerDetailsEmptyTitle(),
            emptyBody = StudyTextCopy.studyAnswerDetailsEmptyBody(),
        )
    }
    val body = studyAnswerDictionaryMetadataModel(dictionaryEntry)
    if (!body.hasContent()) {
        return StudyAnswerDetailSectionModel(
            label = StudyTextCopy.studyAnswerDetailsLabel(),
            summary = StudyTextCopy.studyAnswerLocalDictionarySummary(),
            contentState = StudyAnswerSectionContentState.EMPTY,
            body = null,
            emptyTitle = StudyTextCopy.studyAnswerDetailsEmptyTitle(),
            emptyBody = StudyTextCopy.studyAnswerDetailsEmptyBody(),
        )
    }
    return StudyAnswerDetailSectionModel(
        label = StudyTextCopy.studyAnswerDetailsLabel(),
        summary = dictionarySummary(body),
        contentState = StudyAnswerSectionContentState.READY,
        body = body,
    )
}

private fun StudyAnswerDictionaryMetadataModel.hasContent(): Boolean {
    return meanings.isNotEmpty() ||
        readingGroups.isNotEmpty() ||
        strokeCount != null ||
        grade != null ||
        radical != null ||
        frequency != null ||
        jitenRank != null
}

internal fun studyAnswerDictionaryMetadataModel(
    dictionaryEntry: DictionaryLookup.KanjiEntry,
): StudyAnswerDictionaryMetadataModel {
    return StudyAnswerDictionaryMetadataModel(
        meanings = normalizedTextList(dictionaryEntry.meanings),
        readingGroups = buildList {
            normalizedTextList(dictionaryEntry.onReadings).takeIf { it.isNotEmpty() }?.let {
                add(StudyAnswerReadingGroupModel(StudyTextCopy.studyAnswerOnReadingLabel(), it))
            }
            normalizedTextList(dictionaryEntry.kunReadings).takeIf { it.isNotEmpty() }?.let {
                add(StudyAnswerReadingGroupModel(StudyTextCopy.studyAnswerKunReadingLabel(), it))
            }
            normalizedTextList(dictionaryEntry.nanoriReadings).takeIf { it.isNotEmpty() }?.let {
                add(StudyAnswerReadingGroupModel(StudyTextCopy.studyAnswerNanoriReadingLabel(), it))
            }
        },
        strokeCount = dictionaryEntry.strokeCount.takeIf { it > 0 },
        grade = dictionaryEntry.grade.takeIf { it > 0 },
        radical = dictionaryEntry.radical.takeIf { it > 0 },
        frequency = dictionaryEntry.kanjidicFrequency.takeIf { it > 0 },
        jitenRank = dictionaryEntry.jitenRank?.takeIf { it > 0 },
    )
}

internal fun studyAnswerBreakdownSection(
    dictionaryEntry: DictionaryLookup.KanjiEntry?,
    componentRows: List<String>,
): StudyAnswerDetailSectionModel<StudyAnswerBreakdownModel> {
    val radical = dictionaryEntry?.radical?.takeIf { it > 0 }
    val normalizedComponents = normalizedTextList(componentRows)
    if (radical == null && normalizedComponents.isEmpty()) {
        return StudyAnswerDetailSectionModel(
            label = StudyTextCopy.studyAnswerBreakdownLabel(),
            summary = StudyTextCopy.studyAnswerBreakdownEmptyTitle(),
            contentState = StudyAnswerSectionContentState.EMPTY,
            body = null,
            emptyTitle = StudyTextCopy.studyAnswerBreakdownEmptyTitle(),
            emptyBody = null,
        )
    }
    val fallbackCopy = if (normalizedComponents.isEmpty()) StudyTextCopy.studyAnswerBreakdownEmptyBody() else null
    val body = StudyAnswerBreakdownModel(
        radicalNumber = radical,
        componentRows = normalizedComponents,
        fallbackCopy = fallbackCopy,
    )
    return StudyAnswerDetailSectionModel(
        label = StudyTextCopy.studyAnswerBreakdownLabel(),
        summary = if (normalizedComponents.isEmpty()) {
            StudyTextCopy.studyAnswerRadicalOnlySummary()
        } else {
            StudyTextCopy.studyAnswerRadicalAndComponentsSummary()
        },
        contentState = StudyAnswerSectionContentState.READY,
        body = body,
    )
}

internal fun studyAnswerStrokeOrderSection(
    dictionaryEntry: DictionaryLookup.KanjiEntry?,
    strokeOrderAssetAvailable: Boolean,
    strokeOrderAssetReference: String?,
): StudyAnswerDetailSectionModel<StudyAnswerStrokeOrderModel> {
    val strokeCount = dictionaryEntry?.strokeCount?.takeIf { it > 0 }
    val assetReference = normalizeBodyText(strokeOrderAssetReference)
    return when {
        strokeCount != null -> {
            val body = StudyAnswerStrokeOrderModel(
                strokeCount = strokeCount,
                availability = if (strokeOrderAssetAvailable) {
                    StudyAnswerStrokeOrderAvailability.ASSET_AVAILABLE
                } else {
                    StudyAnswerStrokeOrderAvailability.COUNT_ONLY
                },
                assetReference = assetReference,
                fallbackCopy = if (strokeOrderAssetAvailable) {
                    null
                } else {
                    StudyTextCopy.studyAnswerStrokeOrderEmptyBody()
                },
            )
            StudyAnswerDetailSectionModel(
                label = StudyTextCopy.studyAnswerStrokeOrderLabel(),
                summary = StudyTextCopy.studyAnswerStrokeCountSummary(strokeCount),
                contentState = StudyAnswerSectionContentState.READY,
                body = body,
            )
        }
        strokeOrderAssetAvailable -> {
            val body = StudyAnswerStrokeOrderModel(
                strokeCount = null,
                availability = StudyAnswerStrokeOrderAvailability.ASSET_AVAILABLE,
                assetReference = assetReference,
                fallbackCopy = null,
            )
            StudyAnswerDetailSectionModel(
                label = StudyTextCopy.studyAnswerStrokeOrderLabel(),
                summary = StudyTextCopy.studyAnswerAnimatedGuideReadySummary(),
                contentState = StudyAnswerSectionContentState.READY,
                body = body,
            )
        }
        else -> StudyAnswerDetailSectionModel(
            label = StudyTextCopy.studyAnswerStrokeOrderLabel(),
            summary = StudyTextCopy.studyAnswerStrokeOrderEmptyTitle(),
            contentState = StudyAnswerSectionContentState.UNAVAILABLE,
            body = null,
            emptyTitle = StudyTextCopy.studyAnswerStrokeOrderEmptyTitle(),
            emptyBody = StudyTextCopy.studyAnswerStrokeOrderEmptyBody(),
        )
    }
}

internal fun studyAnswerUsedInAnkiSection(
    examples: List<RecordsImportModels.Example>,
    currentExample: RecordsImportModels.Example? = null,
    showAll: Boolean = false,
    openAnkiDroidSupported: Boolean = false,
    deckNamesByCardId: Map<Long, String> = emptyMap(),
    modelNamesByNoteId: Map<Long, String> = emptyMap(),
): StudyAnswerDetailSectionModel<StudyAnswerUsedInAnkiModel> {
    val rows = studyAnswerUsedInAnkiRows(
        examples = examples,
        currentExample = currentExample,
        openAnkiDroidSupported = openAnkiDroidSupported,
        deckNamesByCardId = deckNamesByCardId,
        modelNamesByNoteId = modelNamesByNoteId,
    )
    if (rows.isEmpty()) {
        return StudyAnswerDetailSectionModel(
            label = StudyTextCopy.studyAnswerUsedInAnkiLabel(),
            summary = StudyTextCopy.studyAnswerNoSyncedWordsSummary(),
            contentState = StudyAnswerSectionContentState.EMPTY,
            body = null,
            emptyTitle = StudyTextCopy.studyAnswerUsedInAnkiEmptyTitle(),
            emptyBody = StudyTextCopy.studyAnswerUsedInAnkiEmptyBody(),
        )
    }
    val visibleRows = if (showAll || rows.size <= STUDY_ANSWER_USED_IN_ANKI_VISIBLE_ROW_LIMIT) {
        rows
    } else {
        rows.take(STUDY_ANSWER_USED_IN_ANKI_VISIBLE_ROW_LIMIT)
    }
    val hiddenRowCount = (rows.size - visibleRows.size).coerceAtLeast(0)
    val toggleLabel = when {
        rows.size <= STUDY_ANSWER_USED_IN_ANKI_VISIBLE_ROW_LIMIT -> null
        showAll -> StudyTextCopy.showFewerLabel()
        else -> StudyTextCopy.showAllLabel(rows.size)
    }
    val body = StudyAnswerUsedInAnkiModel(
        rows = rows,
        visibleRows = visibleRows,
        visibleRowLimit = STUDY_ANSWER_USED_IN_ANKI_VISIBLE_ROW_LIMIT,
        showAll = showAll,
        hiddenRowCount = hiddenRowCount,
        toggleLabel = toggleLabel,
    )
    return StudyAnswerDetailSectionModel(
        label = StudyTextCopy.studyAnswerUsedInAnkiLabel(),
        summary = usedInAnkiSummary(rows.size),
        contentState = StudyAnswerSectionContentState.READY,
        body = body,
    )
}

internal fun studyAnswerUsedInAnkiRows(
    examples: List<RecordsImportModels.Example>,
    currentExample: RecordsImportModels.Example? = null,
    openAnkiDroidSupported: Boolean = false,
    deckNamesByCardId: Map<Long, String> = emptyMap(),
    modelNamesByNoteId: Map<Long, String> = emptyMap(),
): List<StudyAnswerUsedInAnkiRowModel> {
    val mergedExamples = LinkedHashMap<String, RecordsImportModels.Example>()
    if (currentExample != null) {
        mergedExamples[exampleKey(currentExample)] = currentExample
    }
    for (example in examples) {
        mergedExamples.putIfAbsent(exampleKey(example), example)
    }
    return mergedExamples.values.sortedWith(exampleComparator(currentExample)).map { example ->
        val sourceLabel = sourceTypeLabel(example.sourceType)
        val deckLabel = compactChipLabel(deckNamesByCardId[example.cardId])
            ?.takeUnless { sameDisplayLabel(it, sourceLabel) }
        val modelLabel = compactChipLabel(modelNamesByNoteId[example.noteId])
            ?.takeUnless { sameDisplayLabel(it, sourceLabel) || sameDisplayLabel(it, deckLabel) }
        StudyAnswerUsedInAnkiRowModel(
            expression = normalizeBodyText(example.expression) ?: "",
            reading = normalizeBodyText(example.reading) ?: "",
            meaning = normalizeBodyText(example.meaning) ?: "",
            noteId = positiveId(example.noteId),
            cardId = positiveId(example.cardId),
            sourceLabel = sourceLabel,
            deckLabel = deckLabel,
            modelLabel = modelLabel,
            isPrimarySource = currentExample != null && sameExample(example, currentExample),
            tapAction = StudyAnswerAnkiTapActionModel.from(
                noteId = example.noteId,
                cardId = example.cardId,
                openAnkiDroidSupported = openAnkiDroidSupported,
            ),
        )
    }
}

internal fun studyAnswerAnkiTapAction(
    noteId: Long?,
    cardId: Long?,
    openAnkiDroidSupported: Boolean,
): StudyAnswerAnkiTapActionModel {
    return StudyAnswerAnkiTapActionModel.from(noteId, cardId, openAnkiDroidSupported)
}

internal fun studyAnswerWhyThisCardSection(
    examples: List<RecordsImportModels.Example>,
    currentExample: RecordsImportModels.Example? = null,
): StudyAnswerDetailSectionModel<StudyAnswerWhyThisCardModel> {
    val sourceExpression = normalizeBodyText(currentExample?.expression)
    val sourceReading = normalizeBodyText(currentExample?.reading)
    val previewExamples = studyAnswerWhyThisCardPreviewExamples(
        examples = examples,
        currentExample = currentExample,
    )
    if (sourceExpression == null && sourceReading == null && previewExamples.isEmpty()) {
        return StudyAnswerDetailSectionModel(
            label = StudyTextCopy.studyAnswerWhyThisCardLabel(),
            summary = "",
            contentState = StudyAnswerSectionContentState.EMPTY,
            body = null,
            emptyTitle = StudyTextCopy.studyAnswerWhyThisCardLabel(),
            emptyBody = StudyTextCopy.studyAnswerWhyThisCardEmptyBody(),
        )
    }
    val body = StudyAnswerWhyThisCardModel(
        sourceExpression = sourceExpression,
        sourceReading = sourceReading,
        previewExamples = previewExamples,
        fallbackCopy = null,
    )
    val summaryExpression = sourceExpression ?: previewExamples.firstOrNull()?.expression
    return StudyAnswerDetailSectionModel(
        label = StudyTextCopy.studyAnswerWhyThisCardLabel(),
        summary = summaryExpression?.let { StudyTextCopy.studyAnswerFromSummary(it) }.orEmpty(),
        contentState = StudyAnswerSectionContentState.READY,
        body = body,
    )
}

private fun studyAnswerWhyThisCardPreviewExamples(
    examples: List<RecordsImportModels.Example>,
    currentExample: RecordsImportModels.Example?,
): List<StudyAnswerWhyThisCardExampleModel> {
    return sortedExampleList(examples, currentExample)
        .filterNot { currentExample != null && sameExample(it, currentExample) }
        .take(2)
        .map {
            StudyAnswerWhyThisCardExampleModel(
                expression = normalizeBodyText(it.expression) ?: "",
                reading = normalizeBodyText(it.reading) ?: "",
                meaning = normalizeBodyText(it.meaning) ?: "",
            )
        }
}

private fun dictionarySummary(model: StudyAnswerDictionaryMetadataModel): String {
    val parts = ArrayList<String>(2)
    model.strokeCount?.let { parts.add(StudyTextCopy.studyAnswerStrokeCountSummary(it)) }
    model.radical?.let { parts.add(StudyTextCopy.studyAnswerRadicalSummary(it)) }
    if (parts.isEmpty()) {
        return StudyTextCopy.studyAnswerLocalDictionarySummary()
    }
    return parts.joinToString(" • ")
}

private fun sameExample(
    left: RecordsImportModels.Example,
    right: RecordsImportModels.Example,
): Boolean {
    return left.noteId == right.noteId && left.cardId == right.cardId
}

private fun exampleKey(example: RecordsImportModels.Example): String {
    return "${example.noteId}:${example.cardId}"
}

private fun exampleComparator(
    currentExample: RecordsImportModels.Example?,
): Comparator<RecordsImportModels.Example> {
    return compareBy(
        { if (currentExample != null && sameExample(it, currentExample)) 0 else 1 },
        { normalizedSortKey(it.expression) },
        { it.noteId },
        { it.cardId },
    )
}

private fun sortedExampleList(
    examples: List<RecordsImportModels.Example>,
    currentExample: RecordsImportModels.Example?,
): List<RecordsImportModels.Example> {
    val mergedExamples = LinkedHashMap<String, RecordsImportModels.Example>()
    if (currentExample != null) {
        mergedExamples[exampleKey(currentExample)] = currentExample
    }
    for (example in examples) {
        mergedExamples.putIfAbsent(exampleKey(example), example)
    }
    return mergedExamples.values.sortedWith(exampleComparator(currentExample))
}

private fun usedInAnkiSummary(rowCount: Int): String {
    return StudyTextCopy.studyAnswerSyncedWordsSummary(rowCount)
}

private fun normalizedTextList(values: List<String>?): List<String> {
    if (values.isNullOrEmpty()) {
        return emptyList()
    }
    val out = ArrayList<String>(values.size)
    for (value in values) {
        val normalized = normalizeBodyText(value)
        if (!normalized.isNullOrBlank() && out.none { sameDisplayLabel(it, normalized) }) {
            out.add(normalized)
        }
    }
    return out
}

private fun normalizeBodyText(value: String?): String? {
    val normalized = DictionaryLookup.normalize(value)
    return normalized.takeIf { it.isNotBlank() }
}

private fun normalizedSortKey(value: String?): String {
    return normalizeBodyText(value)?.lowercase(Locale.ROOT).orEmpty()
}

private fun compactChipLabel(value: String?): String? {
    val normalized = normalizeBodyText(value) ?: return null
    if (normalized.length > MAX_LABEL_LENGTH) {
        return null
    }
    return normalized.replace('_', ' ')
}

private fun sourceTypeLabel(value: String?): String? {
    val compact = compactChipLabel(value) ?: return null
    return compact
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase(Locale.ROOT)
                } else {
                    char.toString()
                }
            }
        }
}

private fun sameDisplayLabel(
    left: String?,
    right: String?,
): Boolean {
    val leftText = normalizeBodyText(left) ?: return false
    val rightText = normalizeBodyText(right) ?: return false
    return leftText.equals(rightText, ignoreCase = true)
}

private fun positiveId(value: Long?): Long? {
    if (value == null || value <= 0L) {
        return null
    }
    return value
}
