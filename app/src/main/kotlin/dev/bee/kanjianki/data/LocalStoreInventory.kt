package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.KanjiInventorySearchQuery
import dev.bee.kanjianki.core.KanjiReadingChoicePlanner
import dev.bee.kanjianki.core.ManualKanjiAdmissionPolicy
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import dev.bee.kanjianki.core.ReadingKanjiChoicePlanner
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyCollectionLookup
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.TextUtil
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal abstract class LocalStoreInventory(
    context: Context?,
    diagnosticLogger: DiagnosticLogger,
) : LocalStoreSimilarKanji(context, diagnosticLogger) {
    // These caches are populated/read from the UI thread and cleared/repopulated from
    // sync/background threads. Each reference is @Volatile so a reader sees either the
    // fully-built snapshot or null (never a partially-published one); the map-valued
    // caches use ConcurrentHashMap so concurrent puts are safe.
    @Volatile
    private var cachedDashboardRows: List<RecordsImportModels.DashboardRow>? = null
    @Volatile
    private var cachedActiveDashboardRows: List<RecordsImportModels.DashboardRow>? = null
    @Volatile
    private var cachedActiveStudyDashboardRows: List<RecordsImportModels.DashboardRow>? = null
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
    private var cachedKanjiInventoryStudyQueueAll: List<RecordsImportModels.KanjiInventoryItem>? = null
    @Volatile
    private var cachedKanjiInventoryStudyQueueWithSuspendedAll: List<RecordsImportModels.KanjiInventoryItem>? = null
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
    private val conditionalRungAvailabilityCache = ConditionalRungAvailabilityCache()
    private val newCardSortPreviewCacheVersion = AtomicLong(0L)
    @Volatile
    private var observedDashboardCacheEpoch: Long = DASHBOARD_CACHE_EPOCH.get()
    @Volatile
    private var observedStudyItemsCacheEpoch: Long = STUDY_ITEMS_CACHE_EPOCH.get()

    fun newCardSortPreviewCacheVersion(): Long {
        return newCardSortPreviewCacheVersion.get()
    }

    private fun bumpNewCardSortPreviewCacheVersion() {
        newCardSortPreviewCacheVersion.incrementAndGet()
    }

    internal fun clearDashboardRowsCache() {
        val publishedEpoch = DASHBOARD_CACHE_EPOCH.incrementAndGet()
        clearLocalDashboardRowsCache()
        observedDashboardCacheEpoch = publishedEpoch
    }

    /**
     * Drops every in-memory projection this helper holds. Only for the test seam that
     * replaces the underlying database file beneath a process-cached store; production
     * invalidation stays targeted so a single write cannot flush unrelated caches.
     */
    internal fun clearAllProjectionCachesForTest() {
        clearDashboardRowsCache()
        clearLocallySuspendedCache()
        clearStudyItemsCache()
        clearKanjiInventoryAllCache()
        clearSimilarKanjiNeighborsCache()
    }

    private fun clearLocalDashboardRowsCache() {
        cachedDashboardRows = null
        cachedActiveDashboardRows = null
        cachedActiveStudyDashboardRows = null
        cachedActiveDashboardRowsByKanji = null
        clearConditionalRungAvailabilityCaches()
        clearTimelineCache()
        bumpNewCardSortPreviewCacheVersion()
    }

    internal fun clearLocallySuspendedCache() {
        val publishedEpoch = DASHBOARD_CACHE_EPOCH.incrementAndGet()
        cachedLocallySuspendedKanji = null
        cachedActiveDashboardRows = null
        cachedActiveStudyDashboardRows = null
        cachedActiveDashboardRowsByKanji = null
        clearKanjiInventoryAllCache()
        bumpNewCardSortPreviewCacheVersion()
        observedDashboardCacheEpoch = publishedEpoch
    }

    private fun ensureDashboardCacheFresh() {
        val currentEpoch = DASHBOARD_CACHE_EPOCH.get()
        if (observedDashboardCacheEpoch == currentEpoch) {
            return
        }
        synchronized(this) {
            val latestEpoch = DASHBOARD_CACHE_EPOCH.get()
            if (observedDashboardCacheEpoch != latestEpoch) {
                cachedLocallySuspendedKanji = null
                clearLocalDashboardRowsCache()
                clearKanjiInventoryAllCache()
                observedDashboardCacheEpoch = latestEpoch
            }
        }
    }

    internal override fun clearStudyItemsCache() {
        observedStudyItemsCacheEpoch = STUDY_ITEMS_CACHE_EPOCH.incrementAndGet()
        clearLocalStudyItemsCache()
    }

    private fun clearLocalStudyItemsCache() {
        cachedStudyItems = null
        cachedStudyItemsByKanji = null
        cachedActiveDashboardRows = null
        cachedActiveStudyDashboardRows = null
        cachedActiveDashboardRowsByKanji = null
        cachedKanjiInventoryStudyQueueAll = null
        cachedKanjiInventoryStudyQueueWithSuspendedAll = null
        cachedKanjiInventorySearches = null
        clearTimelineCache()
        bumpNewCardSortPreviewCacheVersion()
    }

    private fun ensureStudyItemsCacheFresh() {
        val currentEpoch = STUDY_ITEMS_CACHE_EPOCH.get()
        if (observedStudyItemsCacheEpoch == currentEpoch) {
            return
        }
        synchronized(this) {
            val latestEpoch = STUDY_ITEMS_CACHE_EPOCH.get()
            if (observedStudyItemsCacheEpoch != latestEpoch) {
                clearLocalStudyItemsCache()
                observedStudyItemsCacheEpoch = latestEpoch
            }
        }
    }

    internal override fun clearKanjiInventoryAllCache() {
        cachedKanjiInventoryAll = null
        cachedKanjiInventoryStudyQueueAll = null
        cachedKanjiInventoryStudyQueueWithSuspendedAll = null
        cachedKanjiInventorySearches = null
        clearTimelineCache()
    }

    internal override fun clearTimelineCache() {
        cachedTimelinesByKanji = null
    }

    internal override fun clearSimilarKanjiNeighborsCache() {
        clearConditionalRungAvailabilityCaches()
        bumpNewCardSortPreviewCacheVersion()
    }

    private fun clearConditionalRungAvailabilityCaches() {
        cachedKanjiWithSimilarNeighbors = null
        cachedKanjiWithKanjiReading = null
        cachedKanjiWithReadingKanji = null
        cachedKanjiWithSentenceReading = null
        conditionalRungAvailabilityCache.invalidate()
    }

    fun dashboardRows(): List<RecordsImportModels.DashboardRow> {
        ensureDashboardCacheFresh()
        cachedDashboardRows?.let {
            diagnosticLogger.traceStudyLoad("dashboardRows cache hit size=${it.size}")
            return it
        }

        val rows = loadDashboardRows(excludeLocallySuspended = false)
        cachedDashboardRows = rows
        return rows
    }

    private fun loadDashboardRows(excludeLocallySuspended: Boolean): List<RecordsImportModels.DashboardRow> {
        val loadStart = android.os.SystemClock.elapsedRealtime()
        val db = readableDatabase
        // First read the capped row headers, then issue bounded ordered index seeks for their
        // examples. The old IN query scanned every matching example before Kotlin applied the
        // per-kanji cap, so a single high-volume kanji could dominate cold start.
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
        val headerStart = android.os.SystemClock.elapsedRealtime()
        db.query(
            TABLE_DASHBOARD_ROWS,
            null,
            if (excludeLocallySuspended) {
                "$COLUMN_KANJI NOT IN (SELECT $COLUMN_KANJI FROM $TABLE_LOCAL_KANJI_SUSPENSIONS)"
            } else {
                null
            },
            null,
            null,
            null,
            "weakness_score DESC, suspended_example_count DESC, kanji ASC",
            DASHBOARD_ROW_LIMIT,
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
        val headerDuration = android.os.SystemClock.elapsedRealtime() - headerStart
        logDashboardPhase("headers", "rows=${headers.size}", headerDuration)

        val examplesStart = android.os.SystemClock.elapsedRealtime()
        val examplesByKanji = examplesForKanjiBatch(db, headers.map { it.kanji })
        val examplesDuration = android.os.SystemClock.elapsedRealtime() - examplesStart
        val materializedExamples = examplesByKanji.values.sumOf { it.size }
        logDashboardPhase(
            "examples",
            "requested_kanji=${headers.size} materialized_rows=$materializedExamples",
            examplesDuration,
        )

        val assembleStart = android.os.SystemClock.elapsedRealtime()
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
        val assembleDuration = android.os.SystemClock.elapsedRealtime() - assembleStart
        val totalDuration = android.os.SystemClock.elapsedRealtime() - loadStart
        logDashboardPhase(
            "assembled",
            "rows=${rows.size} examples=$materializedExamples total_duration_ms=$totalDuration",
            assembleDuration,
        )
        return rows
    }

    private fun logDashboardPhase(phase: String, counts: String, durationMillis: Long) {
        val message = "dashboard phase=$phase $counts duration_ms=$durationMillis"
        diagnosticLogger.traceStudyLoad(message)
        if (diagnosticLogger.isCapturing()) {
            diagnosticLogger.log(message)
        }
    }

    fun activeDashboardRows(): List<RecordsImportModels.DashboardRow> {
        ensureDashboardCacheFresh()
        ensureStudyItemsCacheFresh()
        cachedActiveDashboardRows?.let { return it }

        val suspended = locallySuspendedKanji()
        val providerRows = if (suspended.isEmpty()) {
            dashboardRows()
        } else {
            // Apply local suspensions before the row cap. Filtering the already-capped dashboard
            // snapshot let suspended kanji consume slots and hid valid lower-ranked candidates.
            loadDashboardRows(excludeLocallySuspended = true)
        }
        val rows = mergeManualRows(
            providerRows = providerRows,
            sources = manualKanjiSources(admittedOnly = true),
            locallySuspended = suspended,
            admittedOnly = true,
        )
        cachedActiveDashboardRows = rows
        return rows
    }

    /**
     * Scheduler-facing projection. Unlike [activeDashboardRows], this includes
     * every active manual source so daily admission can continue beyond the
     * first capped Home page.
     */
    fun activeStudyDashboardRows(): List<RecordsImportModels.DashboardRow> {
        ensureDashboardCacheFresh()
        ensureStudyItemsCacheFresh()
        cachedActiveStudyDashboardRows?.let { return it }

        val suspended = locallySuspendedKanji()
        val providerRows = if (suspended.isEmpty()) {
            dashboardRows()
        } else {
            loadDashboardRows(excludeLocallySuspended = true)
        }
        val rows = mergeManualRows(
            providerRows = providerRows,
            sources = manualKanjiSources(admittedOnly = false),
            locallySuspended = suspended,
            admittedOnly = false,
        )
        cachedActiveStudyDashboardRows = rows
        return rows
    }

    fun activeDashboardRowsByKanji(): Map<String, RecordsImportModels.DashboardRow> {
        ensureDashboardCacheFresh()
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
        if (kanji == null) {
            return null
        }
        val provider = readDashboardRow(readableDatabase, kanji)
        val manual = manualKanjiSource(kanji) ?: return provider
        return ManualKanjiAdmissionPolicy.mergeRows(
            providerRows = listOfNotNull(provider),
            candidates = listOf(manual.candidate),
            reasonText = MissingKanjiTextCopy.dictionarySourceReason(),
        ).firstOrNull()
    }

    internal open fun manualKanjiSources(admittedOnly: Boolean): List<ManualKanjiSource> = emptyList()

    internal open fun manualKanjiSource(literal: String): ManualKanjiSource? = null

    private fun mergeManualRows(
        providerRows: List<RecordsImportModels.DashboardRow>,
        sources: List<ManualKanjiSource>,
        locallySuspended: Set<String>,
        admittedOnly: Boolean,
    ): List<RecordsImportModels.DashboardRow> {
        if (sources.isEmpty()) {
            return providerRows
        }
        val activeSources = if (locallySuspended.isEmpty()) {
            sources
        } else {
            sources.filterNot { source -> source.candidate.literal in locallySuspended }
        }
        if (activeSources.isEmpty()) {
            return providerRows
        }
        val providers = LinkedHashMap<String, RecordsImportModels.DashboardRow>()
        for (row in providerRows) {
            providers[row.kanji] = row
        }
        for (row in providerRowsForManualSources(admittedOnly)) {
            if (row.kanji !in locallySuspended) {
                providers[row.kanji] = row
            }
        }
        return ManualKanjiAdmissionPolicy.mergeRows(
            providerRows = ArrayList(providers.values),
            candidates = activeSources.map(ManualKanjiSource::candidate),
            reasonText = MissingKanjiTextCopy.dictionarySourceReason(),
        )
    }

    private fun providerRowsForManualSources(
        admittedOnly: Boolean,
    ): List<RecordsImportModels.DashboardRow> {
        val admittedClause = if (admittedOnly) {
            """
            AND EXISTS (
                SELECT 1
                FROM $TABLE_STUDY_ITEMS item
                WHERE item.$COLUMN_KANJI=manual.literal
                  AND item.$COLUMN_STATE<>'$STATE_RETIRED'
            )
            """.trimIndent()
        } else {
            ""
        }
        val rows = ArrayList<RecordsImportModels.DashboardRow>()
        readableDatabase.rawQuery(
            """
            SELECT dashboard.*
            FROM $TABLE_DASHBOARD_ROWS dashboard
            INNER JOIN $TABLE_MANUAL_KANJI_SOURCES manual
              ON manual.literal=dashboard.$COLUMN_KANJI
             AND manual.active=1
            $admittedClause
            ORDER BY dashboard.$COLUMN_KANJI
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(readDashboardRow(readableDatabase, cursor))
            }
        }
        return rows
    }

    fun inventoryItemForKanji(kanji: String?): RecordsImportModels.KanjiInventoryItem? {
        return readInventoryItem(readableDatabase, kanji)
    }

    fun searchKanjiInventory(query: String?): List<RecordsImportModels.KanjiInventoryItem> {
        return searchKanjiInventory(query, false)
    }

    fun searchKanjiInventory(query: String?, onlySimilarKanji: Boolean): List<RecordsImportModels.KanjiInventoryItem> {
        ensureDashboardCacheFresh()
        return searchKanjiInventory(query, onlySimilarKanji, InventorySearchScope.ALL)
    }

    /**
     * Searches the persisted scheduler projection shown by Browse.
     *
     * The default variant requires a non-retired `study_items` row and hides local
     * suspensions. The management variant also includes locally suspended inventory so a
     * suspension can always be reversed, even after the scheduler retired that row. Applying
     * the predicates in SQL keeps them ahead of the 300-row cap.
     */
    fun searchStudyQueueInventory(
        query: String?,
        onlySimilarKanji: Boolean,
        includeLocallySuspended: Boolean,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        ensureDashboardCacheFresh()
        ensureStudyItemsCacheFresh()
        val scope = if (includeLocallySuspended) {
            InventorySearchScope.STUDY_QUEUE_WITH_SUSPENDED
        } else {
            InventorySearchScope.STUDY_QUEUE
        }
        return searchKanjiInventory(query, onlySimilarKanji, scope)
    }

    private fun searchKanjiInventory(
        query: String?,
        onlySimilarKanji: Boolean,
        scope: InventorySearchScope,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        val db = readableDatabase
        val terms = KanjiInventorySearchQuery.parse(query).terms()
        cachedInventorySearch(scope, onlySimilarKanji, terms)?.let { return it }
        val selection = inventorySearchSelection(terms, onlySimilarKanji, scope)
        val matches = queryKanjiInventory(db, selection)
        val ranked = rankKanjiSearchResults(db, matches, terms, onlySimilarKanji, scope)
        cacheInventorySearch(scope, onlySimilarKanji, terms, ranked)
        return ranked
    }

    private fun inventorySearchSelection(
        terms: List<String>,
        onlySimilarKanji: Boolean,
        scope: InventorySearchScope,
    ): InventorySearchSelection {
        val clauses = ArrayList<String>()
        val argsList = ArrayList<String>()
        for (term in terms) {
            // Escape LIKE wildcards so a user typing % or _ searches literally rather than
            // matching everything.
            clauses.add("search_text LIKE ? ESCAPE '\\'")
            argsList.add("%${escapeLikeTerm(term)}%")
        }
        if (onlySimilarKanji) {
            clauses.add(similarKanjiInventoryClause())
        }
        studyQueueInventoryClause(scope)?.let { clause ->
            clauses.add(clause)
            argsList.add(StudyLadderRules.STATE_RETIRED)
        }
        return InventorySearchSelection(
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            argsList.takeIf { it.isNotEmpty() }?.toTypedArray(),
        )
    }

    private fun queryKanjiInventory(
        db: SQLiteDatabase,
        selection: InventorySearchSelection,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        val out = ArrayList<RecordsImportModels.KanjiInventoryItem>()
        db.query(
            TABLE_KANJI_INVENTORY,
            null,
            selection.where,
            selection.args,
            null,
            null,
            ORDER_KANJI_ASC,
            "300",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor))
            }
        }
        return out
    }

    private fun similarKanjiInventoryClause(): String {
        return "EXISTS (SELECT 1 FROM $TABLE_SIMILAR_KANJI_PAIRS WHERE " +
            "$TABLE_SIMILAR_KANJI_PAIRS.$COLUMN_KANJI_A=$TABLE_KANJI_INVENTORY.$COLUMN_KANJI OR " +
            "$TABLE_SIMILAR_KANJI_PAIRS.$COLUMN_KANJI_B=$TABLE_KANJI_INVENTORY.$COLUMN_KANJI)"
    }

    private fun studyQueueInventoryClause(scope: InventorySearchScope): String? {
        if (scope == InventorySearchScope.ALL) return null
        val activeItem = "EXISTS (SELECT 1 FROM $TABLE_STUDY_ITEMS WHERE " +
            "$TABLE_STUDY_ITEMS.$COLUMN_KANJI=$TABLE_KANJI_INVENTORY.$COLUMN_KANJI AND " +
            "$TABLE_STUDY_ITEMS.$COLUMN_STATE<>?)"
        val locallySuspended = "EXISTS (SELECT 1 FROM $TABLE_LOCAL_KANJI_SUSPENSIONS WHERE " +
            "$TABLE_LOCAL_KANJI_SUSPENSIONS.$COLUMN_KANJI=$TABLE_KANJI_INVENTORY.$COLUMN_KANJI)"
        return if (scope == InventorySearchScope.STUDY_QUEUE_WITH_SUSPENDED) {
            "($activeItem OR $locallySuspended)"
        } else {
            "($activeItem AND NOT $locallySuspended)"
        }
    }

    private fun cachedInventorySearch(
        scope: InventorySearchScope,
        onlySimilarKanji: Boolean,
        terms: List<String>,
    ): List<RecordsImportModels.KanjiInventoryItem>? {
        if (onlySimilarKanji) return null
        if (terms.isNotEmpty()) return cachedKanjiInventorySearches?.get(inventorySearchCacheKey(scope, terms))
        return when (scope) {
            InventorySearchScope.ALL -> cachedKanjiInventoryAll
            InventorySearchScope.STUDY_QUEUE -> cachedKanjiInventoryStudyQueueAll
            InventorySearchScope.STUDY_QUEUE_WITH_SUSPENDED -> cachedKanjiInventoryStudyQueueWithSuspendedAll
        }
    }

    private fun cacheInventorySearch(
        scope: InventorySearchScope,
        onlySimilarKanji: Boolean,
        terms: List<String>,
        results: List<RecordsImportModels.KanjiInventoryItem>,
    ) {
        if (onlySimilarKanji) return
        if (terms.isNotEmpty()) {
            val searches = cachedKanjiInventorySearches
                ?: ConcurrentHashMap<String, List<RecordsImportModels.KanjiInventoryItem>>().also {
                    cachedKanjiInventorySearches = it
                }
            searches[inventorySearchCacheKey(scope, terms)] = results
            return
        }
        when (scope) {
            InventorySearchScope.ALL -> cachedKanjiInventoryAll = results
            InventorySearchScope.STUDY_QUEUE -> cachedKanjiInventoryStudyQueueAll = results
            InventorySearchScope.STUDY_QUEUE_WITH_SUSPENDED -> cachedKanjiInventoryStudyQueueWithSuspendedAll = results
        }
    }

    private fun inventorySearchCacheKey(scope: InventorySearchScope, terms: List<String>): String {
        return scope.name + "\u0001" + terms.joinToString("\u0000")
    }

    /** Escape `\`, `%`, and `_` so a LIKE term matches those characters literally. */
    private fun escapeLikeTerm(term: String): String {
        return term
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    /**
     * Searching a kanji glyph must surface that kanji itself, not bury it. `search_text`
     * embeds the full expressions and sentences of every note a kanji appears in, so a
     * single-glyph query also matches every co-occurring kanji, and plain code-point
     * ordering hides the exact match mid-list (or the 300-row cap drops it entirely).
     * Rank rows whose glyph appears in the query first (stable, keeping kanji order
     * within each group), and restore the exact row for a single-glyph query when the
     * cap cut it.
     */
    private fun rankKanjiSearchResults(
        db: SQLiteDatabase,
        matches: List<RecordsImportModels.KanjiInventoryItem>,
        terms: List<String>,
        onlySimilarKanji: Boolean,
        scope: InventorySearchScope,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        if (terms.isEmpty()) {
            return matches
        }
        val queryGlyphs = LinkedHashSet<String>()
        for (term in terms) {
            queryGlyphs.addAll(TextUtil.extractKanji(term))
        }
        if (queryGlyphs.isEmpty()) {
            return matches
        }
        val withExact = ArrayList<RecordsImportModels.KanjiInventoryItem>(matches.size + 1)
        withExact.addAll(matches)
        // A one-glyph query always LIKE-matches its own row (each row indexes its glyph),
        // so a missing exact row can only mean the row cap cut it; restore it. The
        // similar-kanji-only filter is not re-checked here, so skip the restore there.
        val exactGlyph = if (terms.size == 1) TextUtil.normalizeSingleKanji(terms[0]) else ""
        if (!onlySimilarKanji && exactGlyph.isNotEmpty() && withExact.none { it.kanji == exactGlyph }) {
            readInventoryItem(db, exactGlyph)?.let { item ->
                // Exact-row restoration runs after LIMIT, so it must enforce the same scheduler
                // scope as the main query instead of resurrecting retired or unadmitted rows.
                if (inventoryItemMatchesScope(db, item, scope)) {
                    withExact.add(item)
                }
            }
        }
        return withExact.sortedBy { item -> if (queryGlyphs.contains(item.kanji)) 0 else 1 }
    }

    private fun inventoryItemMatchesScope(
        db: SQLiteDatabase,
        item: RecordsImportModels.KanjiInventoryItem,
        scope: InventorySearchScope,
    ): Boolean {
        if (scope == InventorySearchScope.ALL) return true
        if (scope == InventorySearchScope.STUDY_QUEUE && item.suspended) return false
        if (scope == InventorySearchScope.STUDY_QUEUE_WITH_SUSPENDED && item.suspended) return true
        db.query(
            TABLE_STUDY_ITEMS,
            arrayOf(COLUMN_KANJI),
            "$COLUMN_KANJI=? AND $COLUMN_STATE<>?",
            arrayOf(item.kanji, StudyLadderRules.STATE_RETIRED),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private enum class InventorySearchScope {
        ALL,
        STUDY_QUEUE,
        STUDY_QUEUE_WITH_SUSPENDED,
    }

    private class InventorySearchSelection(
        val where: String?,
        val args: Array<String>?,
    )

    fun locallySuspendedKanji(): Set<String> {
        ensureDashboardCacheFresh()
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
            StatsCacheStore(this@LocalStoreInventory as LocalStore).markDirty(this)
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
            StatsCacheStore(this@LocalStoreInventory as LocalStore).markDirty(this)
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
        ensureStudyItemsCacheFresh()
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
            "$WHERE_KANJI AND ($COLUMN_SYNC_ID IS NULL OR $COLUMN_SYNC_ID IN " +
                "(SELECT id FROM $TABLE_SYNC_RUNS WHERE $COLUMN_STATUS=?))",
            arrayOf(kanji, STATUS_SUCCESS),
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
        ensureStudyItemsCacheFresh()
        cachedStudyItems?.let { return it }

        val traceEnabled = diagnosticLogger.isCapturing()
        val totalStart = if (traceEnabled) elapsedRealtimeNanos() else 0L
        val queryStart = if (traceEnabled) elapsedRealtimeNanos() else 0L
        val db = readableDatabase
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, "due_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor))
            }
        }
        val requestedKanjiCount = items.asSequence().map { it.kanji }.distinct().count()
        logStudyItemsPhase(
            traceEnabled,
            mode = "all",
            phase = "query",
            requestedKanji = requestedKanjiCount,
            matchedKanji = items.size,
            startedAtNanos = queryStart,
        )
        annotateConditionalRungsInPlace(db, items, traceEnabled, "all")
        cachedStudyItems = items
        logStudyItemsPhase(
            traceEnabled,
            mode = "all",
            phase = "total",
            requestedKanji = requestedKanjiCount,
            matchedKanji = items.size,
            startedAtNanos = totalStart,
        )
        return items
    }

    fun studyItemsForKanji(kanji: Collection<String>): List<RecordsStudyModels.StudyItem> {
        ensureStudyItemsCacheFresh()
        val distinctKanji = kanji.filter { !it.isNullOrBlank() }.distinct().sorted()
        if (distinctKanji.isEmpty()) {
            return emptyList()
        }

        val cacheKey = distinctKanji.joinToString("\u0000")
        cachedStudyItemsByKanji?.get(cacheKey)?.let {
            diagnosticLogger.traceStudyLoad(
                "studyItemsForKanji cache hit size=${it.size} keys=${distinctKanji.size}",
            )
            return it
        }

        val traceEnabled = diagnosticLogger.isCapturing()
        val totalStart = if (traceEnabled) elapsedRealtimeNanos() else 0L
        val queryStart = elapsedRealtimeNanos()
        val db = readableDatabase
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        for (chunk in distinctKanji.chunked(SQLITE_BIND_CHUNK_SIZE)) {
            val placeholders = chunk.joinToString(",") { "?" }
            db.query(
                TABLE_STUDY_ITEMS,
                null,
                "$COLUMN_KANJI IN ($placeholders)",
                chunk.toTypedArray(),
                null,
                null,
                "due_at ASC",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    items.add(readStudyItem(cursor))
                }
            }
        }
        items.sortWith(compareBy<RecordsStudyModels.StudyItem> { it.dueAtMillis }.thenBy { it.kanji })
        diagnosticLogger.traceStudyLoad(
            "studyItemsForKanji LOADED size=${items.size} keys=${distinctKanji.size} " +
                "duration_ms=${nanosToMillis(elapsedRealtimeNanos() - queryStart)}"
        )
        logStudyItemsPhase(
            traceEnabled,
            mode = "for-kanji",
            phase = "query",
            requestedKanji = distinctKanji.size,
            matchedKanji = items.size,
            startedAtNanos = queryStart,
        )
        annotateConditionalRungsInPlace(db, items, traceEnabled, "for-kanji")
        val caches = cachedStudyItemsByKanji ?: ConcurrentHashMap<String, List<RecordsStudyModels.StudyItem>>().also {
            cachedStudyItemsByKanji = it
        }
        caches[cacheKey] = items
        logStudyItemsPhase(
            traceEnabled,
            mode = "for-kanji",
            phase = "total",
            requestedKanji = distinctKanji.size,
            matchedKanji = items.size,
            startedAtNanos = totalStart,
        )
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

    /** Attested reading usages for [kanji], feeding KanjiReadingChoicePlanner. */
    fun kanjiReadingUsagesFor(kanji: String?): List<KanjiReadingChoicePlanner.Usage> {
        val normalized = normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<KanjiReadingChoicePlanner.Usage>()
        readableDatabase.rawQuery(
            "SELECT $COLUMN_READING, $COLUMN_EXPRESSION, $COLUMN_NOTE_ID, $COLUMN_MATURE, $COLUMN_LAPSES " +
                "FROM $TABLE_KANJI_READING_USAGE WHERE $COLUMN_KANJI = ? " +
                "ORDER BY $COLUMN_NOTE_ID ASC",
            arrayOf(normalized),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    KanjiReadingChoicePlanner.Usage(
                        cursor.getString(1) ?: "",
                        cursor.getString(0) ?: "",
                        wordMeaningFor(cursor.getString(1)),
                        cursor.getLong(2),
                        cursor.getInt(3) == 1,
                        cursor.getInt(4),
                    ),
                )
            }
        }
        return out
    }

    /** Candidate reading pool for [kanji] (attested union dictionary readings). */
    fun kanjiReadingPoolFor(kanji: String?): List<KanjiReadingChoicePlanner.PoolReading> {
        val normalized = normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) {
            return emptyList()
        }
        // A pool reading is mature-attested when some usage row for this kanji
        // with that reading is mature.
        val matureReadings = HashSet<String>()
        readableDatabase.rawQuery(
            "SELECT DISTINCT $COLUMN_READING FROM $TABLE_KANJI_READING_USAGE " +
                "WHERE $COLUMN_KANJI = ? AND $COLUMN_MATURE = 1",
            arrayOf(normalized),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { matureReadings.add(it) }
            }
        }
        val out = ArrayList<KanjiReadingChoicePlanner.PoolReading>()
        readableDatabase.rawQuery(
            "SELECT $COLUMN_READING, $COLUMN_ATTESTED FROM $TABLE_KANJI_READING_POOL " +
                "WHERE $COLUMN_KANJI = ? ORDER BY $COLUMN_READING ASC",
            arrayOf(normalized),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val reading = cursor.getString(0) ?: continue
                val attested = cursor.getInt(1) == 1
                out.add(
                    KanjiReadingChoicePlanner.PoolReading(
                        reading,
                        attested,
                        attested && matureReadings.contains(reading),
                    ),
                )
            }
        }
        return out
    }

    /** Number of rows in kanji_reading_usage — used by the live gate assertion. */
    fun kanjiReadingUsageRowCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_KANJI_READING_USAGE", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** Attested usages for [kanji] shaped for ReadingKanjiChoicePlanner. */
    fun kanjiReadingUsagesForReadingKanji(kanji: String?): List<ReadingKanjiChoicePlanner.TargetUsage> {
        return kanjiReadingUsagesFor(kanji).map { usage ->
            ReadingKanjiChoicePlanner.TargetUsage(
                usage.word,
                usage.reading,
                usage.meaning,
                usage.noteId,
                usage.mature,
                usage.lapses,
            )
        }
    }

    /**
     * Same-reading distractor kanji for [kanji]'s reading_kanji card, keyed by
     * canonical reading: for each reading the target is attested with, the OTHER
     * inventory kanji attested with that reading, flagged mature when any of
     * their usages of that reading is mature.
     */
    fun readingKanjiCandidatesFor(kanji: String?): Map<String, List<ReadingKanjiChoicePlanner.Candidate>> {
        val normalized = normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) {
            return emptyMap()
        }
        val db = readableDatabase
        // Readings the target is attested with.
        val targetReadings = ArrayList<String>()
        db.rawQuery(
            "SELECT DISTINCT $COLUMN_READING FROM $TABLE_KANJI_READING_USAGE WHERE $COLUMN_KANJI = ?",
            arrayOf(normalized),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { targetReadings.add(it) }
            }
        }
        val out = LinkedHashMap<String, List<ReadingKanjiChoicePlanner.Candidate>>()
        for (reading in targetReadings) {
            val candidates = LinkedHashMap<String, Boolean>()
            db.rawQuery(
                "SELECT $COLUMN_KANJI, MAX($COLUMN_MATURE) FROM $TABLE_KANJI_READING_USAGE " +
                    "WHERE $COLUMN_READING = ? AND $COLUMN_KANJI <> ? GROUP BY $COLUMN_KANJI",
                arrayOf(reading, normalized),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val other = cursor.getString(0) ?: continue
                    candidates[other] = cursor.getInt(1) == 1
                }
            }
            if (candidates.isNotEmpty()) {
                out[reading] = candidates.map { ReadingKanjiChoicePlanner.Candidate(it.key, it.value) }
            }
        }
        return out
    }

    private fun wordMeaningFor(expression: String?): String {
        // Best-effort gloss for the prompt word from its example row; empty when
        // unknown (the planner's meaning is only a context cue).
        val expr = expression?.trim().orEmpty()
        if (expr.isEmpty()) {
            return ""
        }
        readableDatabase.rawQuery(
            "SELECT $COLUMN_MEANING FROM $TABLE_KANJI_EXAMPLES WHERE $COLUMN_EXPRESSION = ? LIMIT 1",
            arrayOf(expr),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0) ?: ""
            }
        }
        return ""
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

    /**
     * Annotate every conditional-rung availability flag on the given items from
     * the current content tables. Named for its original single flag but now
     * covers all conditional rungs (similar_kanji, kanji_reading — Goal 78) so
     * every seeding/annotate seam produces items whose rungAvailability() is
     * consistent with on-device data.
     */
    fun annotateSimilarKanjiAvailability(items: List<RecordsStudyModels.StudyItem>?): List<RecordsStudyModels.StudyItem> {
        if (items.isNullOrEmpty()) {
            return items ?: emptyList()
        }
        val db = readableDatabase
        val availability = conditionalRungAvailability(
            db,
            items.map { it.kanji },
            diagnosticLogger.isCapturing(),
            "annotate",
        )
        val out = ArrayList<RecordsStudyModels.StudyItem>(items.size)
        for (item in items) {
            out.add(
                annotateConditionalRungs(
                    item,
                    availability.similarKanji,
                    availability.kanjiReading,
                    availability.readingKanji,
                    availability.sentenceReading,
                ),
            )
        }
        return out
    }

    private fun annotateConditionalRungsInPlace(
        db: SQLiteDatabase,
        items: MutableList<RecordsStudyModels.StudyItem>,
        traceEnabled: Boolean,
        traceMode: String,
    ) {
        val availability = conditionalRungAvailability(
            db,
            items.map { it.kanji },
            traceEnabled,
            traceMode,
        )
        for (i in items.indices) {
            items[i] = annotateConditionalRungs(
                items[i],
                availability.similarKanji,
                availability.kanjiReading,
                availability.readingKanji,
                availability.sentenceReading,
            )
        }
    }

    private fun conditionalRungAvailability(
        db: SQLiteDatabase,
        requestedKanji: Collection<String>,
        traceEnabled: Boolean,
        traceMode: String,
    ): ConditionalRungAvailability {
        val requested = requestedKanji.filter { it.isNotBlank() }.distinct()
        val similar = loadConditionalCapability(
            db,
            requested,
            traceEnabled,
            traceMode,
            "capability-similar-kanji",
            ConditionalRungCapability.SIMILAR_KANJI,
            ConditionalRungAvailabilityQueries::similarKanji,
        )
        val kanjiReading = loadConditionalCapability(
            db,
            requested,
            traceEnabled,
            traceMode,
            "capability-kanji-reading",
            ConditionalRungCapability.KANJI_READING,
            ConditionalRungAvailabilityQueries::kanjiReading,
        )
        val readingKanji = loadConditionalCapability(
            db,
            requested,
            traceEnabled,
            traceMode,
            "capability-reading-kanji",
            ConditionalRungCapability.READING_KANJI,
            ConditionalRungAvailabilityQueries::readingKanji,
        )
        val sentenceReading = loadConditionalCapability(
            db,
            requested,
            traceEnabled,
            traceMode,
            "capability-sentence-reading",
            ConditionalRungCapability.SENTENCE_READING,
            ConditionalRungAvailabilityQueries::sentenceReading,
        )
        return ConditionalRungAvailability(similar, kanjiReading, readingKanji, sentenceReading)
    }

    private fun loadConditionalCapability(
        db: SQLiteDatabase,
        requestedKanji: List<String>,
        traceEnabled: Boolean,
        traceMode: String,
        phase: String,
        capability: ConditionalRungCapability,
        loader: (SQLiteDatabase, Collection<String>) -> Set<String>,
    ): Set<String> {
        val start = if (traceEnabled) elapsedRealtimeNanos() else 0L
        var queriedKanji = 0
        val result = conditionalRungAvailabilityCache.load(capability, requestedKanji) { missing ->
            queriedKanji += missing.size
            loader(db, missing)
        }
        logStudyItemsPhase(
            traceEnabled,
            mode = traceMode,
            phase = phase,
            requestedKanji = requestedKanji.size,
            matchedKanji = result.size,
            queriedKanji = queriedKanji,
            startedAtNanos = start,
        )
        return result
    }

    private fun logStudyItemsPhase(
        enabled: Boolean,
        mode: String,
        phase: String,
        requestedKanji: Int,
        matchedKanji: Int,
        queriedKanji: Int = requestedKanji,
        startedAtNanos: Long,
    ) {
        if (!enabled) {
            return
        }
        diagnosticLogger.log(
            String.format(
                Locale.US,
                "study-items mode=%s phase=%s requested_kanji=%d queried_kanji=%d " +
                    "matched_kanji=%d duration_ms=%.2f",
                mode,
                phase,
                requestedKanji,
                queriedKanji,
                matchedKanji,
                nanosToMillis(elapsedRealtimeNanos() - startedAtNanos),
            ),
        )
    }

    private fun elapsedRealtimeNanos(): Long {
        return runCatching { android.os.SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
    }

    private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0

    private data class ConditionalRungAvailability(
        val similarKanji: Set<String>,
        val kanjiReading: Set<String>,
        val readingKanji: Set<String>,
        val sentenceReading: Set<String>,
    )

    private fun annotateConditionalRungs(
        item: RecordsStudyModels.StudyItem,
        withSimilar: Set<String>,
        withKanjiReading: Set<String>,
        withReadingKanji: Set<String>,
        withSentenceReading: Set<String>,
    ): RecordsStudyModels.StudyItem {
        val hasSimilar = withSimilar.contains(item.kanji)
        val hasKanjiReading = withKanjiReading.contains(item.kanji)
        val hasReadingKanji = withReadingKanji.contains(item.kanji)
        val hasSentenceReading = withSentenceReading.contains(item.kanji)
        var result = item
        if (hasSimilar != result.hasSimilarKanji) {
            result = result.withHasSimilarKanji(hasSimilar)
        }
        if (hasKanjiReading != result.hasKanjiReading) {
            result = result.withHasKanjiReading(hasKanjiReading)
        }
        if (hasReadingKanji != result.hasReadingKanji) {
            result = result.withHasReadingKanji(hasReadingKanji)
        }
        if (hasSentenceReading != result.hasSentenceReading) {
            result = result.withHasSentenceReading(hasSentenceReading)
        }
        return result
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

    fun unrestoredSuspendedArchiveCardIds(): Set<Long> {
        val cardIds = LinkedHashSet<Long>()
        readableDatabase.query(
            TABLE_SUSPENDED_ARCHIVE,
            arrayOf(COLUMN_CARD_ID),
            "restored_at IS NULL",
            null,
            null,
            null,
            "$COLUMN_CARD_ID ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cardIds.add(cursor.getLong(0))
            }
        }
        return cardIds
    }

    private companion object {
        const val DASHBOARD_ROW_LIMIT = "120"
        const val SQLITE_BIND_CHUNK_SIZE = 900

        /** Shared by every LocalStore helper in this process. */
        val DASHBOARD_CACHE_EPOCH = AtomicLong(0L)

        /** Shared by every LocalStore helper in this process. */
        val STUDY_ITEMS_CACHE_EPOCH = AtomicLong(0L)
    }
}
