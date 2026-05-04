package dev.bee.kanjianki.data.local

import dev.bee.kanjianki.data.ankidroid.AnkiDroidCardSnapshot
import dev.bee.kanjianki.data.ankidroid.AnkiDroidCollectionSnapshot
import dev.bee.kanjianki.data.ankidroid.AnkiDroidNoteSnapshot
import dev.bee.kanjianki.domain.DashboardRowSnapshot
import dev.bee.kanjianki.domain.DashboardSnapshot
import dev.bee.kanjianki.domain.DashboardSummarySnapshot
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.domain.SettingsSnapshot
import dev.bee.kanjianki.domain.SourceCounts
import java.text.Normalizer
import kotlin.math.max
import org.json.JSONArray

internal data class LocalDashboardDerivationResult(
    val dashboard: DashboardSnapshot,
    val problemRows: List<ProblemKanjiSnapshotEntity>,
    val expressionSnapshots: List<ExpressionSnapshotEntity>,
    val sourceNotes: List<SourceNoteEntity>,
    val sourceCards: List<SourceCardEntity>,
)

private data class DashboardIndexes(
    val suspendedExpressions: List<String>,
    val activeExpressions: List<String>,
    val matureExpressions: List<String>,
    val collectionExpressions: List<String>,
    val suspendedIndex: Map<String, List<String>>,
    val activeIndex: Map<String, List<String>>,
    val matureIndex: Map<String, List<String>>,
    val collectionIndex: Map<String, List<String>>,
    val threshold: Int,
)

private data class ExpressionEntry(
    val expression: String,
    val reading: String,
    val meaning: String,
    val tags: List<String>,
    val noteIds: MutableSet<Long> = linkedSetOf(),
    val cardIds: MutableSet<Long> = linkedSetOf(),
    var suspendedCardCount: Int = 0,
    var activeCardCount: Int = 0,
    var matureCardCount: Int = 0,
)

private data class KanjiStats(
    val collectionExpressions: List<String>,
    val suspendedExpressions: List<String>,
    val activeRecurringExpressions: List<String>,
    val matureExpressions: List<String>,
    val supportDeficit: Int,
    val isUnknown: Boolean,
)

internal object LocalDashboardState {
    fun derive(
        snapshot: AnkiDroidCollectionSnapshot,
        settings: SettingsSnapshot,
        detailLookup: Map<String, KanjiDetailSnapshot>,
        nowTs: Long,
    ): LocalDashboardDerivationResult {
        val sourceNotes = snapshot.notes.map { it.toEntity(nowTs) }
        val sourceCards = snapshot.cards.map { it.toEntity(nowTs) }
        val expressionEntries = buildExpressionEntries(snapshot.notes, snapshot.cards)
        val expressionSnapshots = expressionEntries
            .toSortedMap()
            .map { (normalizedExpression, entry) ->
                ExpressionSnapshotEntity(
                    normalizedExpression = normalizedExpression,
                    expression = entry.expression,
                    reading = entry.reading,
                    meaning = entry.meaning,
                    tagsJson = JSONArray(entry.tags).toString(),
                    sourceNoteIdsJson = JSONArray(entry.noteIds.sorted()).toString(),
                    sourceCardIdsJson = JSONArray(entry.cardIds.sorted()).toString(),
                    suspendedCardCount = entry.suspendedCardCount,
                    activeCardCount = entry.activeCardCount,
                    matureCardCount = entry.matureCardCount,
                    updatedTs = nowTs,
                )
            }

        val suspendedExpressions = mutableListOf<String>()
        val activeExpressions = mutableListOf<String>()
        val matureExpressions = mutableListOf<String>()
        expressionEntries.values.forEach { entry ->
            repeat(entry.suspendedCardCount) { suspendedExpressions += entry.expression }
            repeat(entry.activeCardCount) { activeExpressions += entry.expression }
            repeat(entry.matureCardCount) { matureExpressions += entry.expression }
        }

        val indexes = buildIndexes(
            suspendedExpressions = suspendedExpressions,
            activeExpressions = activeExpressions,
            matureExpressions = matureExpressions,
            threshold = settings.kanjiSupportThreshold,
        )
        val rows = indexes.collectionIndex.keys
            .map { kanji ->
                buildDashboardRow(
                    kanji = kanji,
                    indexes = indexes,
                    settings = settings,
                    detailLookup = detailLookup,
                )
            }
            .sortedWith(compareBy<DashboardRowSnapshot>(
                { if (it.jitenRank == null) 1 else 0 },
                { it.jitenRank ?: Double.MAX_VALUE },
                { it.kanji },
            ))
        val rankedRows = rows.filter { it.jitenRank != null }
        val summary = DashboardSummarySnapshot(
            totalKanjiCount = rows.size,
            unknownKanjiCount = rows.count { it.isUnknown },
            averageKanjiRank = rankedRows.takeIf { it.isNotEmpty() }
                ?.mapNotNull { it.jitenRank }
                ?.average(),
            matureSupportThreshold = settings.kanjiSupportThreshold,
            rankedKanjiCount = rankedRows.size,
        )
        val problemRows = rows.mapIndexed { index, row ->
            val detail = buildDetail(
                row = row,
                stats = buildKanjiStats(indexes, row.kanji),
                baseDetail = detailLookup[row.kanji],
            )
            ProblemKanjiSnapshotEntity(
                kanji = row.kanji,
                jitenRank = row.jitenRank,
                collectionExpressionCount = row.collectionExpressionCount,
                suspendedExpressionCount = row.suspendedExpressionCount,
                activeRecurringExpressionCount = row.activeRecurringExpressionCount,
                matureSupportCount = row.matureSupportCount,
                supportDeficit = row.supportDeficit,
                isUnknown = row.isUnknown,
                browserSearch = row.browserSearch,
                detailJson = RoomCacheCodec.encodeKanjiDetail(detail),
                sortIndex = index,
                updatedTs = nowTs,
            )
        }
        val dashboard = DashboardSnapshot(
            summary = summary,
            rows = rows,
            problemSeedCount = rows.count { it.suspendedExpressionCount > 0 && it.supportDeficit > 0 },
            warnings = emptyList(),
            sourceCounts = SourceCounts(
                noteCount = snapshot.notes.size,
                cardCount = snapshot.cards.size,
            ),
        )
        return LocalDashboardDerivationResult(
            dashboard = dashboard,
            problemRows = problemRows,
            expressionSnapshots = expressionSnapshots,
            sourceNotes = sourceNotes,
            sourceCards = sourceCards,
        )
    }

    private fun buildExpressionEntries(
        notes: List<AnkiDroidNoteSnapshot>,
        cards: List<AnkiDroidCardSnapshot>,
    ): MutableMap<String, ExpressionEntry> {
        val notesById = notes.associateBy(AnkiDroidNoteSnapshot::noteId)
        val entries = linkedMapOf<String, ExpressionEntry>()
        cards.sortedBy(AnkiDroidCardSnapshot::cardId).forEach { card ->
            val note = notesById[card.noteId] ?: return@forEach
            val normalizedExpression = normalizeExpression(note.expression)
            if (normalizedExpression.isBlank()) {
                return@forEach
            }
            val entry = entries.getOrPut(normalizedExpression) {
                ExpressionEntry(
                    expression = normalizedExpression,
                    reading = note.reading,
                    meaning = note.meaning,
                    tags = note.tags,
                )
            }
            entry.noteIds += note.noteId
            entry.cardIds += card.cardId
            if (card.isSuspended) {
                entry.suspendedCardCount += 1
            }
            if (card.isActive) {
                entry.activeCardCount += 1
            }
            if (card.isMature) {
                entry.matureCardCount += 1
            }
        }
        return entries
    }

    private fun buildIndexes(
        suspendedExpressions: List<String>,
        activeExpressions: List<String>,
        matureExpressions: List<String>,
        threshold: Int,
    ): DashboardIndexes {
        val normalizedSuspended = uniqueExpressions(suspendedExpressions)
        val normalizedActive = uniqueExpressions(activeExpressions)
        val normalizedMature = uniqueExpressions(matureExpressions)
        val normalizedCollection = uniqueExpressions(
            normalizedSuspended + normalizedActive + normalizedMature,
        )
        return DashboardIndexes(
            suspendedExpressions = normalizedSuspended,
            activeExpressions = normalizedActive,
            matureExpressions = normalizedMature,
            collectionExpressions = normalizedCollection,
            suspendedIndex = buildKanjiExpressionIndex(normalizedSuspended),
            activeIndex = buildKanjiExpressionIndex(normalizedActive),
            matureIndex = buildKanjiExpressionIndex(normalizedMature),
            collectionIndex = buildKanjiExpressionIndex(normalizedCollection),
            threshold = max(0, threshold),
        )
    }

    private fun buildDashboardRow(
        kanji: String,
        indexes: DashboardIndexes,
        settings: SettingsSnapshot,
        detailLookup: Map<String, KanjiDetailSnapshot>,
    ): DashboardRowSnapshot {
        val stats = buildKanjiStats(indexes, kanji)
        return DashboardRowSnapshot(
            kanji = kanji,
            jitenRank = detailLookup[kanji]?.jitenRank,
            collectionExpressionCount = stats.collectionExpressions.size,
            suspendedExpressionCount = stats.suspendedExpressions.size,
            activeRecurringExpressionCount = stats.activeRecurringExpressions.size,
            matureSupportCount = stats.matureExpressions.size,
            supportDeficit = stats.supportDeficit,
            isUnknown = stats.isUnknown,
            browserSearch = buildBrowserSearch(
                kanji = kanji,
                modelNames = settings.noteModels,
                searchFieldName = settings.expressionField,
            ),
        )
    }

    private fun buildKanjiStats(
        indexes: DashboardIndexes,
        kanji: String,
    ): KanjiStats {
        val collectionExpressions = indexes.collectionIndex[kanji].orEmpty()
        val suspended = indexes.suspendedIndex[kanji].orEmpty()
        val mature = indexes.matureIndex[kanji].orEmpty()
        val matureSet = mature.toSet()
        val activeRecurring = indexes.activeIndex[kanji].orEmpty()
            .filterNot(matureSet::contains)
        val matureSupportCount = mature.size
        return KanjiStats(
            collectionExpressions = collectionExpressions,
            suspendedExpressions = suspended,
            activeRecurringExpressions = activeRecurring,
            matureExpressions = mature,
            supportDeficit = max(indexes.threshold - matureSupportCount, 0),
            isUnknown = suspended.isNotEmpty() && matureSupportCount < indexes.threshold,
        )
    }

    private fun buildDetail(
        row: DashboardRowSnapshot,
        stats: KanjiStats,
        baseDetail: KanjiDetailSnapshot?,
    ): KanjiDetailSnapshot {
        val fallbackKeyword = stats.collectionExpressions.firstOrNull() ?: row.kanji
        return KanjiDetailSnapshot(
            kanji = row.kanji,
            jitenRank = row.jitenRank,
            keyword = baseDetail?.keyword ?: fallbackKeyword,
            meanings = baseDetail?.meanings ?: listOf("fixture"),
            onReadings = baseDetail?.onReadings ?: emptyList(),
            kunReadings = baseDetail?.kunReadings ?: emptyList(),
            components = baseDetail?.components ?: emptyList(),
            componentHint = baseDetail?.componentHint ?: "",
            strokeCount = baseDetail?.strokeCount ?: 0,
            browserSearch = row.browserSearch,
            collectionExamples = stats.collectionExpressions,
            suspendedExamples = stats.suspendedExpressions,
            activeRecurringExamples = stats.activeRecurringExpressions,
            matureExamples = stats.matureExpressions,
        )
    }

    private fun buildKanjiExpressionIndex(expressions: List<String>): Map<String, List<String>> {
        val index = sortedMapOf<String, MutableList<String>>()
        expressions.forEach { expression ->
            extractKanjiChars(expression).forEach { kanji ->
                index.getOrPut(kanji) { mutableListOf() }.add(expression)
            }
        }
        return index
    }

    private fun uniqueExpressions(expressions: List<String>): List<String> =
        expressions
            .map(::normalizeExpression)
            .filter(String::isNotBlank)
            .toSet()
            .sorted()

    private fun buildBrowserSearch(
        kanji: String,
        modelNames: List<String>,
        searchFieldName: String,
    ): String {
        val modelQuery = modelNames
            .filter(String::isNotBlank)
            .joinToString(" or ") { "note:\"$it\"" }
            .takeIf { it.isNotBlank() }
            ?.let { "($it)" }
            .orEmpty()
        val fieldQuery = "\"$searchFieldName:*$kanji*\""
        return if (modelQuery.isNotBlank()) {
            "$modelQuery $fieldQuery"
        } else {
            fieldQuery
        }
    }

    private fun normalizeExpression(expression: String): String {
        val stripped = expression.replace(HTML_TAG_REGEX, "")
        val normalized = Normalizer.normalize(stripped, Normalizer.Form.NFKC)
        return WHITESPACE_REGEX.replace(normalized, " ").trim()
    }

    private fun extractKanjiChars(text: String): List<String> =
        normalizeExpression(text)
            .asSequence()
            .map(Char::toString)
            .filter { value ->
                value.singleOrNull()?.let { char ->
                    Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN
                } == true
            }
            .toList()

    private fun AnkiDroidNoteSnapshot.toEntity(nowTs: Long): SourceNoteEntity =
        SourceNoteEntity(
            noteId = noteId,
            modelName = modelName,
            expression = expression,
            reading = reading,
            meaning = meaning,
            fieldsJson = org.json.JSONObject(fields).toString(),
            tagsJson = JSONArray(tags).toString(),
            isDeleted = false,
            firstSeenTs = nowTs,
            updatedTs = nowTs,
            syncedTs = nowTs,
        )

    private fun AnkiDroidCardSnapshot.toEntity(nowTs: Long): SourceCardEntity =
        SourceCardEntity(
            cardId = cardId,
            noteId = noteId,
            deckName = deckName,
            intervalDays = intervalDays,
            modifiedTs = modifiedTs,
            dueValue = dueValue,
            cardOrd = cardOrd,
            queueValue = queueValue,
            cardType = cardType,
            reps = reps,
            lapses = lapses,
            isSuspended = isSuspended,
            isActive = isActive,
            isMature = isMature,
            isDeleted = false,
            firstSeenTs = nowTs,
            updatedTs = nowTs,
            syncedTs = nowTs,
        )
}

private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val WHITESPACE_REGEX = Regex("\\s+")
