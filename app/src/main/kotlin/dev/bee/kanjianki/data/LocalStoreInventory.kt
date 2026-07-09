package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.KanjiInventorySearchQuery
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyCollectionLookup
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal abstract class LocalStoreInventory(context: Context?) : LocalStoreSimilarKanji(context) {
    // These caches are populated/read from the UI thread and cleared/repopulated from
    // sync/background threads. Each reference is @Volatile so a reader sees either the
    // fully-built snapshot or null (never a partially-published one); the map-valued
    // caches use ConcurrentHashMap so concurrent puts are safe.
    @Volatile
    private var cachedDashboardRows: List<RecordsImportModels.DashboardRow>? = null
    @Volatile
    private var cachedActiveDashboardRows: List<RecordsImportModels.DashboardRow>? = null
    @Volatile
    private var cachedActiveDashboardRowsByKanji: Map<String, RecordsImportModels.DashboardRow>? = null
    @Volatile
    private var cachedLocallySuspendedKanji: Set<String>? = null
    @Volatile
    private var cachedStudyItems: List<RecordsStudyModels.StudyItem>? = null
    @Volatile
    private var cachedStudyItemsByKanji: MutableMap<String, List<RecordsStudyModels.StudyItem>>? = null
    @Volatile
    private var cachedKanjiInventoryAll: List<RecordsImportModels.KanjiInventoryItem>? = null
    @Volatile
    private var cachedKanjiInventorySearches: MutableMap<String, List<RecordsImportModels.KanjiInventoryItem>>? = null
    @Volatile
    private var cachedTimelinesByKanji: MutableMap<String, RecordsStudyModels.KanjiRecoveryTimeline>? = null
    @Volatile
    private var cachedKanjiWithSimilarNeighbors: Set<String>? = null
    @Volatile
    private var cachedKanjiWithKanjiReading: Set<String>? = null
    @Volatile
    private var cachedKanjiWithReadingKanji: Set<String>? = null
    @Volatile
    private var cachedKanjiWithSentenceReading: Set<String>? = null
    private val newCardSortPreviewCacheVersion = AtomicLong(0L)

    fun newCardSortPreviewCacheVersion(): Long {
        return newCardSortPreviewCacheVersion.get()
    }

    private fun bumpNewCardSortPreviewCacheVersion() {
        newCardSortPreviewCacheVersion.incrementAndGet()
    }

    internal fun clearDashboardRowsCache() {
        cachedDashboardRows = null
        cachedActiveDashboardRows = null
        cachedActiveDashboardRowsByKanji = null
        clearTimelineCache()
        bumpNewCardSortPreviewCacheVersion()
    }

    internal fun clearLocallySuspendedCache() {
        cachedLocallySuspendedKanji = null
        cachedActiveDashboardRows = null
        cachedActiveDashboardRowsByKanji = null
        clearKanjiInventoryAllCache()
        bumpNewCardSortPreviewCacheVersion()
    }

    internal override fun clearStudyItemsCache() {
        cachedStudyItems = null
        cachedStudyItemsByKanji = null
        clearTimelineCache()
        bumpNewCardSortPreviewCacheVersion()
    }

    internal override fun clearKanjiInventoryAllCache() {
        cachedKanjiInventoryAll = null
        cachedKanjiInventorySearches = null
        clearTimelineCache()
    }

    internal override fun clearTimelineCache() {
        cachedTimelinesByKanji = null
    }

    internal override fun clearSimilarKanjiNeighborsCache() {
        cachedKanjiWithSimilarNeighbors = null
        cachedKanjiWithKanjiReading = null
        cachedKanjiWithReadingKanji = null
        cachedKanjiWithSentenceReading = null
        bumpNewCardSortPreviewCacheVersion()
    }

    fun dashboardRows(): List<RecordsImportModels.DashboardRow> {
        cachedDashboardRows?.let {
            dev.bee.kanjianki.studyLoadDebug("dashboardRows cache hit size=${it.size}")
            return it
        }

        val loadStart = android.os.SystemClock.elapsedRealtime()
        val db = readableDatabase
        // First read the (capped) row headers, then batch-load examples for all of them in a
        // single query instead of firing one example query per kanji. On cold boot the old N+1
        // pattern cost ~240ms on the main thread; batching collapses it to two queries.
        data class RowHeader(
            val kanji: String,
            val jitenRank: Int?,
            val primaryMeaning: String,
            val reading: String,
            val browserSearch: String,
            val weaknessScore: Int,
            val reasonCode: String,
            val reasonText: String,
            val activeExampleCount: Int,
            val suspendedExampleCount: Int,
            val matureSupportCount: Int,
        )
        val headers = ArrayList<RowHeader>()
        db.query(
            TABLE_DASHBOARD_ROWS,
            null,
            null,
            null,
            null,
            null,
            "weakness_score DESC, suspended_example_count DESC, kanji ASC",
            "120",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                headers.add(
                    RowHeader(
                        string(cursor, COLUMN_KANJI),
                        nullableInt(cursor, COLUMN_JITEN_RANK),
                        string(cursor, COLUMN_PRIMARY_MEANING),
                        string(cursor, COLUMN_READING),
                        string(cursor, COLUMN_BROWSER_SEARCH),
                        integer(cursor, COLUMN_WEAKNESS_SCORE),
                        string(cursor, COLUMN_REASON_CODE),
                        string(cursor, COLUMN_REASON_TEXT),
                        integer(cursor, COLUMN_ACTIVE_EXAMPLE_COUNT),
                        integer(cursor, COLUMN_SUSPENDED_EXAMPLE_COUNT),
                        integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                    ),
                )
            }
        }
        val examplesByKanji = examplesForKanjiBatch(db, headers.map { it.kanji })
        val rows = ArrayList<RecordsImportModels.DashboardRow>(headers.size)
        for (header in headers) {
            rows.add(
                RecordsImportModels.DashboardRow(
                    header.kanji,
                    header.jitenRank,
                    header.primaryMeaning,
                    header.reading,
                    header.browserSearch,
                    header.weaknessScore,
                    header.reasonCode,
                    header.reasonText,
                    header.activeExampleCount,
                    header.suspendedExampleCount,
                    header.matureSupportCount,
                    examplesByKanji[header.kanji] ?: emptyList<RecordsImportModels.Example>(),
                ),
            )
        }
        cachedDashboardRows = rows
        dev.bee.kanjianki.studyLoadDebug(
            "dashboardRows LOADED size=${rows.size} " +
                "(batched examples) duration_ms=${android.os.SystemClock.elapsedRealtime() - loadStart}"
        )
        return rows
    }

    fun activeDashboardRows(): List<RecordsImportModels.DashboardRow> {
        cachedActiveDashboardRows?.let { return it }

        val suspended = locallySuspendedKanji()
        if (suspended.isEmpty()) {
            val rows = dashboardRows()
            cachedActiveDashboardRows = rows
            return rows
        }
        val out = ArrayList<RecordsImportModels.DashboardRow>()
        for (row in dashboardRows()) {
            if (!suspended.contains(row.kanji)) {
                out.add(row)
            }
        }
        cachedActiveDashboardRows = out
        return out
    }

    fun activeDashboardRowsByKanji(): Map<String, RecordsImportModels.DashboardRow> {
        cachedActiveDashboardRowsByKanji?.let { return it }

        val rows = activeDashboardRows()
        if (rows.isEmpty()) {
            val empty = emptyMap<String, RecordsImportModels.DashboardRow>()
            cachedActiveDashboardRowsByKanji = empty
            return empty
        }

        val rowsByKanji = Collections.unmodifiableMap(StudyCollectionLookup.dashboardRowsByKanji(rows))
        cachedActiveDashboardRowsByKanji = rowsByKanji
        return rowsByKanji
    }

    fun rowForKanji(kanji: String?): RecordsImportModels.DashboardRow? {
        return readDashboardRow(readableDatabase, kanji)
    }

    fun inventoryItemForKanji(kanji: String?): RecordsImportModels.KanjiInventoryItem? {
        return readInventoryItem(readableDatabase, kanji)
    }

    fun searchKanjiInventory(query: String?): List<RecordsImportModels.KanjiInventoryItem> {
        return searchKanjiInventory(query, false)
    }

    /** Escape `\`, `%`, and `_` so a LIKE term matches those characters literally. */
    private fun escapeLikeTerm(term: String): String {
        return term
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    fun searchKanjiInventory(query: String?, onlySimilarKanji: Boolean): List<RecordsImportModels.KanjiInventoryItem> {
        val db = readableDatabase
        val parsed = KanjiInventorySearchQuery.parse(query)
        val terms = parsed.terms()
        val cacheKey = if (!onlySimilarKanji && terms.isNotEmpty()) terms.joinToString("\u0000") else null
        if (cacheKey == null) {
            if (!onlySimilarKanji && terms.isEmpty()) {
                cachedKanjiInventoryAll?.let { return it }
            }
        } else {
            cachedKanjiInventorySearches?.get(cacheKey)?.let { return it }
        }

        val out = ArrayList<RecordsImportModels.KanjiInventoryItem>()
        val clauses = ArrayList<String>()
        val argsList = ArrayList<String>()
        if (terms.isNotEmpty()) {
            for (term in terms) {
                // Escape LIKE wildcards so a user typing % or _ searches literally
                // rather than matching everything.
                clauses.add("search_text LIKE ? ESCAPE '\\'")
                argsList.add("%${escapeLikeTerm(term)}%")
            }
        }
        if (onlySimilarKanji) {
            clauses.add(
                "EXISTS (SELECT 1 FROM $TABLE_SIMILAR_KANJI_PAIRS WHERE " +
                    "$TABLE_SIMILAR_KANJI_PAIRS.$COLUMN_KANJI_A=$TABLE_KANJI_INVENTORY.$COLUMN_KANJI OR " +
                    "$TABLE_SIMILAR_KANJI_PAIRS.$COLUMN_KANJI_B=$TABLE_KANJI_INVENTORY.$COLUMN_KANJI)"
            )
        }
        val selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        val args = argsList.takeIf { it.isNotEmpty() }?.toTypedArray()
        db.query(
            TABLE_KANJI_INVENTORY,
            null,
            selection,
            args,
            null,
            null,
            ORDER_KANJI_ASC,
            "300",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor))
            }
        }
        if (!onlySimilarKanji && terms.isEmpty()) {
            cachedKanjiInventoryAll = out
        } else if (!onlySimilarKanji) {
            val searches = cachedKanjiInventorySearches ?: ConcurrentHashMap<String, List<RecordsImportModels.KanjiInventoryItem>>().also {
                cachedKanjiInventorySearches = it
            }
            searches[cacheKey!!] = out
        }
        return out
    }

    fun locallySuspendedKanji(): Set<String> {
        cachedLocallySuspendedKanji?.let { return it }

        val out = HashSet<String>()
        readableDatabase.query(
            TABLE_LOCAL_KANJI_SUSPENSIONS,
            arrayOf(COLUMN_KANJI),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(string(cursor, COLUMN_KANJI))
            }
        }
        cachedLocallySuspendedKanji = out
        return out
    }

    fun isKanjiLocallySuspended(kanji: String): Boolean {
        readableDatabase.query(
            TABLE_LOCAL_KANJI_SUSPENSIONS,
            arrayOf(COLUMN_KANJI),
            WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun setKanjiLocallySuspended(kanji: String?, suspended: Boolean, nowMillis: Long) {
        if (kanji.isNullOrEmpty()) {
            return
        }
        writableDatabase.transaction {
            setKanjiLocallySuspendedInTransaction(this, kanji, suspended, nowMillis)
        }
        clearLocallySuspendedCache()
        clearKanjiInventoryAllCache()
    }

    fun setKanjiLocallySuspendedForKanji(kanji: Collection<String?>?, suspended: Boolean, nowMillis: Long) {
        val normalized = kanji
            .orEmpty()
            .map { it.orEmpty() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalized.isEmpty()) {
            return
        }
        writableDatabase.transaction {
            for (value in normalized) {
                setKanjiLocallySuspendedInTransaction(this, value, suspended, nowMillis)
            }
        }
        clearLocallySuspendedCache()
        clearKanjiInventoryAllCache()
    }

    private fun setKanjiLocallySuspendedInTransaction(
        db: SQLiteDatabase,
        kanji: String,
        suspended: Boolean,
        nowMillis: Long,
    ) {
        if (suspended) {
            val values = ContentValues()
            values.put(COLUMN_KANJI, kanji)
            values.put("suspended_at", nowMillis)
            db.insertWithOnConflict(
                TABLE_LOCAL_KANJI_SUSPENSIONS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.delete(TABLE_LEARNING_REPEATS, WHERE_KANJI, arrayOf(kanji))
        } else {
            db.delete(TABLE_LOCAL_KANJI_SUSPENSIONS, WHERE_KANJI, arrayOf(kanji))
        }
    }

    fun timelineForKanji(kanji: String): RecordsStudyModels.KanjiRecoveryTimeline {
        if (kanji.isNotBlank()) {
            cachedTimelinesByKanji?.get(kanji)?.let { return it }
        }

        val db = readableDatabase
        val inventoryItem = readInventoryItem(db, kanji)
        val row = readDashboardRow(db, kanji)
        val item = studyItemForKanji(db, kanji)
        val events = ArrayList<RecordsImportModels.KanjiTimelineEvent>()
        db.query(
            TABLE_KANJI_TIMELINE_EVENTS,
            null,
            WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            "occurred_at DESC, id DESC",
            "50",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events.add(readTimelineEvent(cursor))
            }
        }
        Collections.reverse(events)
        val timeline = RecordsStudyModels.KanjiRecoveryTimeline(inventoryItem, row, item, events)
        if (kanji.isNotBlank()) {
            val caches = cachedTimelinesByKanji ?: ConcurrentHashMap<String, RecordsStudyModels.KanjiRecoveryTimeline>().also {
                cachedTimelinesByKanji = it
            }
            caches[kanji] = timeline
        }
        return timeline
    }

    fun studyItems(): List<RecordsStudyModels.StudyItem> {
        cachedStudyItems?.let { return it }

        val db = readableDatabase
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, "due_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor))
            }
        }
        val withSimilar = kanjiWithSimilarNeighbors(db)
        for (i in items.indices) {
            val current = items[i]
            val hasSimilar = withSimilar.contains(current.kanji)
            if (hasSimilar != current.hasSimilarKanji) {
                items[i] = current.withHasSimilarKanji(hasSimilar)
            }
        }
        cachedStudyItems = items
        return items
    }

    fun studyItemsForKanji(kanji: Collection<String>): List<RecordsStudyModels.StudyItem> {
        val distinctKanji = kanji.filter { !it.isNullOrBlank() }.distinct().sorted()
        if (distinctKanji.isEmpty()) {
            return emptyList()
        }

        val cacheKey = distinctKanji.joinToString("\u0000")
        cachedStudyItemsByKanji?.get(cacheKey)?.let {
            dev.bee.kanjianki.studyLoadDebug("studyItemsForKanji cache hit size=${it.size} keys=${distinctKanji.size}")
            return it
        }

        val start = android.os.SystemClock.elapsedRealtime()
        val db = readableDatabase
        val placeholders = distinctKanji.joinToString(",") { "?" }
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        db.query(
            TABLE_STUDY_ITEMS,
            null,
            "$COLUMN_KANJI IN ($placeholders)",
            distinctKanji.toTypedArray(),
            null,
            null,
            "due_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor))
            }
        }
        dev.bee.kanjianki.studyLoadDebug(
            "studyItemsForKanji LOADED size=${items.size} keys=${distinctKanji.size} " +
                "duration_ms=${android.os.SystemClock.elapsedRealtime() - start}"
        )
        val withSimilar = kanjiWithSimilarNeighbors(db)
        for (i in items.indices) {
            val current = items[i]
            val hasSimilar = withSimilar.contains(current.kanji)
            if (hasSimilar != current.hasSimilarKanji) {
                items[i] = current.withHasSimilarKanji(hasSimilar)
            }
        }
        val caches = cachedStudyItemsByKanji ?: ConcurrentHashMap<String, List<RecordsStudyModels.StudyItem>>().also {
            cachedStudyItemsByKanji = it
        }
        caches[cacheKey] = items
        return items
    }

    fun kanjiWithSimilarNeighbors(db: SQLiteDatabase): Set<String> {
        cachedKanjiWithSimilarNeighbors?.let { return it }

        // Goal 69: a kanji has a *buildable* similar-kanji card only when it
        // participates in a pair whose BOTH endpoints are present in the local
        // kanji inventory the choice planner draws from — i.e. the planner's
        // minimum input (the answer plus at least one valid distractor = two
        // choices) provably exists. This mirrors
        // SimilarKanjiChoicePlanner.validPair / SimilarKanjiIndex.pairsWithin
        // (both endpoints in the glyph set) so the hasSimilarKanji predicate and
        // the renderer cannot diverge: a pair whose partner is absent from the
        // inventory no longer marks the kanji as having similar content, which
        // would otherwise record a plain flashcard exercise into
        // similar_kanji_memory.
        val bothInInventory =
            "$COLUMN_KANJI_A IN (SELECT $COLUMN_KANJI FROM $TABLE_KANJI_INVENTORY) AND " +
                "$COLUMN_KANJI_B IN (SELECT $COLUMN_KANJI FROM $TABLE_KANJI_INVENTORY)"
        val out = HashSet<String>()
        db.rawQuery(
            "SELECT $COLUMN_KANJI_A FROM $TABLE_SIMILAR_KANJI_PAIRS WHERE $bothInInventory " +
                "UNION SELECT $COLUMN_KANJI_B FROM $TABLE_SIMILAR_KANJI_PAIRS WHERE $bothInInventory",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val kanji = cursor.getString(0)
                if (!kanji.isNullOrEmpty()) {
                    out.add(kanji)
                }
            }
        }
        val cached = Collections.unmodifiableSet(out)
        cachedKanjiWithSimilarNeighbors = cached
        return cached
    }

    /**
     * Kanji that can support the `kanji_reading` rung (Goal 77): at least one
     * attested usage row (a real word to prompt with) AND at least two distinct
     * readings available in the pool (attested ∪ dictionary) so a ≥ 2-choice
     * card is buildable. Per the Goal 69 lesson, availability must mean a choice
     * card can actually be built.
     */
    fun kanjiWithKanjiReading(db: SQLiteDatabase): Set<String> {
        cachedKanjiWithKanjiReading?.let { return it }
        val sql =
            "SELECT u.$COLUMN_KANJI FROM $TABLE_KANJI_READING_USAGE u " +
                "JOIN (SELECT $COLUMN_KANJI, COUNT(*) AS reading_count FROM $TABLE_KANJI_READING_POOL " +
                "GROUP BY $COLUMN_KANJI HAVING reading_count >= 2) p ON u.$COLUMN_KANJI = p.$COLUMN_KANJI " +
                "GROUP BY u.$COLUMN_KANJI"
        val cached = Collections.unmodifiableSet(querySingleColumnSet(db, sql))
        cachedKanjiWithKanjiReading = cached
        return cached
    }

    /**
     * Kanji that can support the `reading_kanji` homophone rung (Goal 77): some
     * attested canonical reading of this kanji is also attested for at least two
     * OTHER kanji, so a ≥ 3-choice card (target + 2 same-reading distractors)
     * can be built from attested evidence alone.
     */
    fun kanjiWithReadingKanji(db: SQLiteDatabase): Set<String> {
        cachedKanjiWithReadingKanji?.let { return it }
        // Readings shared by >= 3 distinct kanji (attested usage rows).
        val sharedReadings =
            "SELECT $COLUMN_READING FROM " +
                "(SELECT $COLUMN_READING, COUNT(DISTINCT $COLUMN_KANJI) AS kanji_count " +
                "FROM $TABLE_KANJI_READING_USAGE GROUP BY $COLUMN_READING) " +
                "WHERE kanji_count >= 3"
        val sql =
            "SELECT DISTINCT $COLUMN_KANJI FROM $TABLE_KANJI_READING_USAGE " +
                "WHERE $COLUMN_READING IN ($sharedReadings)"
        val cached = Collections.unmodifiableSet(querySingleColumnSet(db, sql))
        cachedKanjiWithReadingKanji = cached
        return cached
    }

    /**
     * Kanji that can support the `sentence_reading` rung (Goal 77): at least one
     * example row with both a non-blank sentence and a non-blank reading.
     */
    fun kanjiWithSentenceReading(db: SQLiteDatabase): Set<String> {
        cachedKanjiWithSentenceReading?.let { return it }
        val sql =
            "SELECT DISTINCT $COLUMN_KANJI FROM $TABLE_KANJI_EXAMPLES " +
                "WHERE $COLUMN_SENTENCE IS NOT NULL AND TRIM($COLUMN_SENTENCE) <> '' " +
                "AND $COLUMN_READING IS NOT NULL AND TRIM($COLUMN_READING) <> ''"
        val cached = Collections.unmodifiableSet(querySingleColumnSet(db, sql))
        cachedKanjiWithSentenceReading = cached
        return cached
    }

    private fun querySingleColumnSet(db: SQLiteDatabase, sql: String): Set<String> {
        val out = HashSet<String>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(0)
                if (!value.isNullOrEmpty()) {
                    out.add(value)
                }
            }
        }
        return out
    }

    fun annotateSimilarKanjiAvailability(items: List<RecordsStudyModels.StudyItem>?): List<RecordsStudyModels.StudyItem> {
        if (items.isNullOrEmpty()) {
            return items ?: emptyList()
        }
        val withSimilar = kanjiWithSimilarNeighbors(readableDatabase)
        val out = ArrayList<RecordsStudyModels.StudyItem>(items.size)
        for (item in items) {
            val hasSimilar = withSimilar.contains(item.kanji)
            out.add(if (hasSimilar == item.hasSimilarKanji) item else item.withHasSimilarKanji(hasSimilar))
        }
        return out
    }

    fun suspendedImports(): List<RecordsImportModels.SuspendedImport> {
        val db = readableDatabase
        val imports = LinkedHashMap<String, LocalStoreBase.MutableSuspendedImport>()
        db.query(TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "jiten_rank ASC, kanji ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val kanji = string(cursor, COLUMN_KANJI)
                imports[kanji] = LocalStoreBase.MutableSuspendedImport(
                    kanji,
                    nullableInt(cursor, COLUMN_JITEN_RANK),
                    integer(cursor, COLUMN_RANK_KNOWN) == 1,
                    integer(cursor, COLUMN_CUTOFF_USED),
                )
            }
        }

        db.query(TABLE_SUSPENDED_SOURCES, null, null, null, null, null, "kanji ASC, card_id ASC").use { sources ->
            while (sources.moveToNext()) {
                val imported = imports[string(sources, COLUMN_KANJI)] ?: continue
                imported.sources.add(
                    RecordsImportModels.SuspendedSource(
                        imported.kanji,
                        longValue(sources, COLUMN_CARD_ID),
                        longValue(sources, COLUMN_NOTE_ID),
                        string(sources, COLUMN_EXPRESSION),
                        string(sources, COLUMN_READING),
                        string(sources, COLUMN_MEANING),
                        string(sources, COLUMN_SENTENCE),
                    ),
                )
            }
        }

        val out = ArrayList<RecordsImportModels.SuspendedImport>()
        for (imported in imports.values) {
            out.add(imported.build())
        }
        return out
    }
}
