package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.KanjiInventorySearchQuery
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import java.util.Collections

internal abstract class LocalStoreInventory(context: Context?) : LocalStoreSimilarKanji(context) {
    private var cachedDashboardRows: List<RecordsImportModels.DashboardRow>? = null
    private var cachedActiveDashboardRows: List<RecordsImportModels.DashboardRow>? = null
    private var cachedLocallySuspendedKanji: Set<String>? = null
    private var cachedStudyItems: List<RecordsStudyModels.StudyItem>? = null
    private var cachedKanjiInventoryAll: List<RecordsImportModels.KanjiInventoryItem>? = null

    internal fun clearDashboardRowsCache() {
        cachedDashboardRows = null
        cachedActiveDashboardRows = null
    }

    internal fun clearLocallySuspendedCache() {
        cachedLocallySuspendedKanji = null
        cachedActiveDashboardRows = null
    }

    internal fun clearStudyItemsCache() {
        cachedStudyItems = null
    }

    internal override fun clearKanjiInventoryAllCache() {
        cachedKanjiInventoryAll = null
    }

    fun dashboardRows(): List<RecordsImportModels.DashboardRow> {
        cachedDashboardRows?.let { return it }

        val db = readableDatabase
        val rows = ArrayList<RecordsImportModels.DashboardRow>()
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
                val kanji = string(cursor, COLUMN_KANJI)
                rows.add(
                    RecordsImportModels.DashboardRow(
                        kanji,
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
                        examplesForKanji(db, kanji),
                    ),
                )
            }
        }
        cachedDashboardRows = rows
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

    fun rowForKanji(kanji: String?): RecordsImportModels.DashboardRow? {
        return readDashboardRow(readableDatabase, kanji)
    }

    fun inventoryItemForKanji(kanji: String?): RecordsImportModels.KanjiInventoryItem? {
        return readInventoryItem(readableDatabase, kanji)
    }

    fun searchKanjiInventory(query: String?): List<RecordsImportModels.KanjiInventoryItem> {
        return searchKanjiInventory(query, false)
    }

    fun searchKanjiInventory(query: String?, onlySimilarKanji: Boolean): List<RecordsImportModels.KanjiInventoryItem> {
        val db = readableDatabase
        val parsed = KanjiInventorySearchQuery.parse(query)
        if (parsed.isEmpty() && !onlySimilarKanji) {
            cachedKanjiInventoryAll?.let { return it }
        }

        val out = ArrayList<RecordsImportModels.KanjiInventoryItem>()
        val clauses = ArrayList<String>()
        val argsList = ArrayList<String>()
        if (!parsed.isEmpty()) {
            for (term in parsed.terms()) {
                clauses.add("search_text LIKE ?")
                argsList.add("%$term%")
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
        if (parsed.isEmpty() && !onlySimilarKanji) {
            cachedKanjiInventoryAll = out
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
        return RecordsStudyModels.KanjiRecoveryTimeline(inventoryItem, row, item, events)
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
        val distinctKanji = kanji.filter { !it.isNullOrBlank() }.distinct()
        if (distinctKanji.isEmpty()) {
            return emptyList()
        }

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
        val withSimilar = kanjiWithSimilarNeighbors(db)
        for (i in items.indices) {
            val current = items[i]
            val hasSimilar = withSimilar.contains(current.kanji)
            if (hasSimilar != current.hasSimilarKanji) {
                items[i] = current.withHasSimilarKanji(hasSimilar)
            }
        }
        return items
    }

    fun kanjiWithSimilarNeighbors(db: SQLiteDatabase): Set<String> {
        val out = HashSet<String>()
        db.rawQuery(
            "SELECT kanji_a FROM $TABLE_SIMILAR_KANJI_PAIRS UNION SELECT kanji_b FROM $TABLE_SIMILAR_KANJI_PAIRS",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val kanji = cursor.getString(0)
                if (!kanji.isNullOrEmpty()) {
                    out.add(kanji)
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
