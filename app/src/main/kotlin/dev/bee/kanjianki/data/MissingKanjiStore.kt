package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.AnkiKanjiInventory
import dev.bee.kanjianki.core.ManualKanjiAdmissionPolicy
import dev.bee.kanjianki.core.MissingKanjiAnalyzer
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.TextUtil
import org.json.JSONArray
import org.json.JSONException
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.regex.Pattern

internal enum class MissingKanjiScanStatus(val storedValue: String) {
    SUCCESS("success"),
    FAILED("failed"),
    CANCELLED("cancelled");

    companion object {
        fun fromStored(value: String?): MissingKanjiScanStatus {
            return entries.firstOrNull { it.storedValue == value } ?: FAILED
        }
    }
}

internal data class MissingKanjiScanRecord(
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

internal data class StoredAnkiKanjiInventory(
    val scan: MissingKanjiScanRecord,
    val literals: Set<String>,
)

internal data class MissingKanjiInventoryState(
    val published: StoredAnkiKanjiInventory?,
    val latestAttempt: MissingKanjiScanRecord?,
) {
    val isStale: Boolean
        get() = latestAttempt != null &&
            (published == null || latestAttempt.id != published.scan.id)
}

internal data class MissingKanjiPreferences(
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

internal data class ManualKanjiSource(
    val candidate: MissingKanjiCandidate,
    val sourceType: String,
    val addedAt: Long,
    val updatedAt: Long,
    val active: Boolean,
)

internal data class ManualKanjiSourceWriteResult(
    val requestedCount: Int,
    val addedLiterals: Set<String>,
    val reactivatedLiterals: Set<String>,
    val alreadyActiveLiterals: Set<String>,
    val missingMeaningLiterals: Set<String>,
    val missingReadingLiterals: Set<String>,
    val invalidCount: Int,
    val duplicateCount: Int,
)

internal data class ManualKanjiSourceRemovalResult(
    val requestedCount: Int,
    val removedLiterals: Set<String>,
    val reviewedLiterals: Set<String>,
    val inactiveLiterals: Set<String>,
    val invalidCount: Int,
)

internal data class MissingKanjiExportReceipt(
    val literal: String,
    val destinationKey: String,
    val exportedAt: Long,
    val externalNoteId: Long?,
)

/**
 * Durable, aggregate-only Missing Kanji state.
 *
 * This repository accepts inventory literals and dictionary metadata only. It
 * has no API capable of persisting Anki note fields.
 */
internal class MissingKanjiStore(
    private val store: LocalStoreBase,
    private val publicationHook: PublicationHook = PublicationHook.NONE,
    private val manualSourcesChanged: () -> Unit = {},
) {
    fun publishInventory(
        inventory: AnkiKanjiInventory,
        startedAt: Long,
        completedAt: Long,
        providerFingerprint: String,
    ): MissingKanjiScanRecord {
        val normalizedLiterals = normalizeLiterals(inventory.literals)
        val safeStartedAt = startedAt.coerceAtLeast(0L)
        val safeCompletedAt = completedAt.coerceAtLeast(safeStartedAt)
        return store.writableDatabase.transaction {
            val scanId = insertScan(
                db = this,
                startedAt = safeStartedAt,
                completedAt = safeCompletedAt,
                status = MissingKanjiScanStatus.SUCCESS,
                notesScanned = inventory.notesScanned,
                fieldsScanned = inventory.fieldsScanned,
                uniqueKanjiCount = normalizedLiterals.size,
                skippedNotes = inventory.skippedNotes,
                modelCount = inventory.modelCount,
                providerFingerprint = providerFingerprint,
                failureCode = "",
            )
            delete(LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY, null, null)
            for (literal in normalizedLiterals) {
                val values = ContentValues().apply {
                    put(COLUMN_LITERAL, literal)
                    put(COLUMN_SCAN_ID, scanId)
                    put(COLUMN_OBSERVED_AT, safeCompletedAt)
                }
                insertOrThrow(LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY, null, values)
            }
            publicationHook.beforeCommit()
            pruneScanHistory(this)
            scanById(this, scanId) ?: error("Published Missing Kanji scan was not readable.")
        }
    }

    fun recordUnsuccessfulScan(
        status: MissingKanjiScanStatus,
        startedAt: Long,
        completedAt: Long,
        notesScanned: Int,
        fieldsScanned: Int,
        uniqueKanjiCount: Int,
        skippedNotes: Int,
        modelCount: Int,
        providerFingerprint: String,
        failureCode: String,
    ): MissingKanjiScanRecord {
        require(status != MissingKanjiScanStatus.SUCCESS) {
            "Successful scans must be atomically published with their inventory."
        }
        val safeStartedAt = startedAt.coerceAtLeast(0L)
        val safeCompletedAt = completedAt.coerceAtLeast(safeStartedAt)
        return store.writableDatabase.transaction {
            val scanId = insertScan(
                db = this,
                startedAt = safeStartedAt,
                completedAt = safeCompletedAt,
                status = status,
                notesScanned = notesScanned,
                fieldsScanned = fieldsScanned,
                uniqueKanjiCount = uniqueKanjiCount,
                skippedNotes = skippedNotes,
                modelCount = modelCount,
                providerFingerprint = providerFingerprint,
                failureCode = failureCode,
            )
            pruneScanHistory(this)
            scanById(this, scanId) ?: error("Recorded Missing Kanji scan was not readable.")
        }
    }

    fun inventoryState(): MissingKanjiInventoryState {
        val db = store.readableDatabase
        var latestAttempt: MissingKanjiScanRecord? = null
        var publishedScan: MissingKanjiScanRecord? = null
        val literals = LinkedHashSet<String>()
        db.rawQuery(
            """
            SELECT scans.*, inventory.$COLUMN_LITERAL AS $COLUMN_INVENTORY_LITERAL
            FROM ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS} scans
            LEFT JOIN ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY} inventory
              ON inventory.$COLUMN_SCAN_ID = scans.id
            WHERE scans.id = (
                SELECT id
                FROM ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS}
                ORDER BY id DESC
                LIMIT 1
            )
            OR scans.id = (
                SELECT id
                FROM ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS}
                WHERE ${LocalStoreBase.COLUMN_STATUS}=?
                ORDER BY id DESC
                LIMIT 1
            )
            ORDER BY scans.id DESC, inventory.$COLUMN_LITERAL ASC
            """.trimIndent(),
            arrayOf(MissingKanjiScanStatus.SUCCESS.storedValue),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val record = scan(cursor)
                if (latestAttempt == null) {
                    latestAttempt = record
                }
                if (record.status == MissingKanjiScanStatus.SUCCESS) {
                    publishedScan = publishedScan ?: record
                    val literalIndex = cursor.getColumnIndexOrThrow(COLUMN_INVENTORY_LITERAL)
                    if (!cursor.isNull(literalIndex)) {
                        literals.add(cursor.getString(literalIndex))
                    }
                }
            }
        }
        val published = publishedScan?.let { scan ->
            StoredAnkiKanjiInventory(scan, immutableSet(literals))
        }
        return MissingKanjiInventoryState(published, latestAttempt)
    }

    fun loadPreferences(): MissingKanjiPreferences {
        val settings = store.settingsStore()
        val defaults = MissingKanjiPreferences()
        val range = MissingKanjiFrequencyRange(
            minimumRank = settings.getInt(SETTING_RANK_MIN, defaults.range.minimumRank),
            maximumRank = settings.getInt(SETTING_RANK_MAX, defaults.range.maximumRank),
            includeUnranked = settings.getInt(SETTING_INCLUDE_UNRANKED, 0) == 1,
        )
        if (MissingKanjiAnalyzer.validateRange(range) != null) {
            return defaults
        }
        val storedPreset = settings.getString(SETTING_PRESET, defaults.preset).orEmpty()
        val preset = storedPreset.takeIf(MissingKanjiPreferences.SUPPORTED_PRESETS::contains)
            ?: MissingKanjiPreferences.PRESET_CUSTOM
        return MissingKanjiPreferences(
            preset = preset,
            range = range,
            searchQuery = settings.getString(SETTING_SEARCH_QUERY, "").orEmpty(),
        )
    }

    fun savePreferences(preferences: MissingKanjiPreferences) {
        require(MissingKanjiAnalyzer.validateRange(preferences.range) == null) {
            "Missing Kanji frequency range is invalid."
        }
        val preset = preferences.preset.takeIf(MissingKanjiPreferences.SUPPORTED_PRESETS::contains)
            ?: MissingKanjiPreferences.PRESET_CUSTOM
        store.writableDatabase.transaction {
            val settings = store.settingsStore()
            settings.putString(SETTING_PRESET, preset)
            settings.putInt(SETTING_RANK_MIN, preferences.range.minimumRank)
            settings.putInt(SETTING_RANK_MAX, preferences.range.maximumRank)
            settings.putInt(SETTING_INCLUDE_UNRANKED, if (preferences.range.includeUnranked) 1 else 0)
            settings.putString(SETTING_SEARCH_QUERY, preferences.searchQuery.trim())
        }
    }

    fun addManualSources(
        candidates: Collection<MissingKanjiCandidate>,
        nowMillis: Long,
    ): ManualKanjiSourceWriteResult {
        val structurallyValid = ArrayList<MissingKanjiCandidate>(candidates.size)
        var invalidCount = 0
        for (candidate in candidates) {
            val normalizedCandidate = normalizeCandidate(candidate)
            if (normalizedCandidate == null) {
                invalidCount += 1
            } else {
                structurallyValid.add(normalizedCandidate)
            }
        }
        val admission = ManualKanjiAdmissionPolicy.planAddition(
            candidates = structurallyValid,
            existingStudyLiterals = emptySet(),
            activeManualLiterals = emptySet(),
        )
        val normalized = admission.candidatesToAdd.associateByTo(
            LinkedHashMap(),
            MissingKanjiCandidate::literal,
        )
        val added = LinkedHashSet<String>()
        val reactivated = LinkedHashSet<String>()
        val alreadyActive = LinkedHashSet<String>()
        val safeNow = nowMillis.coerceAtLeast(0L)
        store.writableDatabase.transaction {
            val existing = loadManualSources(this, activeOnly = false)
                .associateBy { source -> source.candidate.literal }
            for ((literal, candidate) in normalized) {
                val current = existing[literal]
                when {
                    current == null -> {
                        insertManualSource(this, candidate, safeNow)
                        added.add(literal)
                    }
                    current.active -> {
                        updateManualSource(this, candidate, safeNow, active = true)
                        alreadyActive.add(literal)
                    }
                    else -> {
                        updateManualSource(this, candidate, safeNow, active = true)
                        reactivated.add(literal)
                    }
                }
            }
        }
        if (normalized.isNotEmpty()) {
            manualSourcesChanged()
        }
        return ManualKanjiSourceWriteResult(
            requestedCount = candidates.size,
            addedLiterals = immutableSet(added),
            reactivatedLiterals = immutableSet(reactivated),
            alreadyActiveLiterals = immutableSet(alreadyActive),
            missingMeaningLiterals = admission.missingMeaningLiterals,
            missingReadingLiterals = admission.missingReadingLiterals,
            invalidCount = invalidCount,
            duplicateCount = admission.duplicateCount,
        )
    }

    fun manualSources(activeOnly: Boolean = true): List<ManualKanjiSource> {
        return loadManualSources(store.readableDatabase, activeOnly)
    }

    fun admittedManualSources(): List<ManualKanjiSource> {
        val sources = ArrayList<ManualKanjiSource>()
        store.readableDatabase.rawQuery(
            """
            SELECT manual.*
            FROM ${LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES} manual
            WHERE manual.$COLUMN_ACTIVE=1
              AND EXISTS (
                SELECT 1
                FROM ${LocalStoreBase.TABLE_STUDY_ITEMS} item
                WHERE item.${LocalStoreBase.COLUMN_KANJI}=manual.$COLUMN_LITERAL
                  AND item.${LocalStoreBase.COLUMN_STATE}<>?
              )
            ORDER BY manual.${LocalStoreBase.COLUMN_JITEN_RANK} IS NULL,
                     manual.${LocalStoreBase.COLUMN_JITEN_RANK},
                     manual.$COLUMN_LITERAL
            """.trimIndent(),
            arrayOf(LocalStoreBase.STATE_RETIRED),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sources.add(manualSource(cursor))
            }
        }
        return Collections.unmodifiableList(sources)
    }

    fun manualSource(literal: String): ManualKanjiSource? {
        val normalized = normalizeLiteral(literal) ?: return null
        store.readableDatabase.query(
            LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES,
            null,
            "$COLUMN_LITERAL=? AND $COLUMN_ACTIVE=1",
            arrayOf(normalized),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) manualSource(cursor) else null
        }
    }

    fun removableManualSourceLiterals(): Set<String> {
        val literals = LinkedHashSet<String>()
        store.readableDatabase.rawQuery(
            """
            SELECT manual.$COLUMN_LITERAL
            FROM ${LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES} manual
            WHERE manual.$COLUMN_ACTIVE=1
              AND NOT EXISTS (
                SELECT 1
                FROM ${LocalStoreBase.TABLE_REVIEW_LOG} review
                WHERE review.${LocalStoreBase.COLUMN_KANJI}=manual.$COLUMN_LITERAL
              )
              AND NOT EXISTS (
                SELECT 1
                FROM ${LocalStoreBase.TABLE_STUDY_ITEMS} item
                WHERE item.${LocalStoreBase.COLUMN_KANJI}=manual.$COLUMN_LITERAL
                  AND item.total_reviews>0
              )
            ORDER BY manual.$COLUMN_LITERAL
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                literals.add(cursor.text(COLUMN_LITERAL))
            }
        }
        return immutableSet(literals)
    }

    fun removeUnreviewedManualSources(
        literals: Collection<String>,
        nowMillis: Long,
    ): ManualKanjiSourceRemovalResult {
        val normalized = LinkedHashSet<String>()
        var invalidCount = 0
        for (literal in literals) {
            val value = normalizeLiteral(literal)
            if (value == null) {
                invalidCount += 1
            } else {
                normalized.add(value)
            }
        }
        val removed = LinkedHashSet<String>()
        val reviewed = LinkedHashSet<String>()
        val inactive = LinkedHashSet<String>()
        val safeNow = nowMillis.coerceAtLeast(0L)
        store.writableDatabase.transaction {
            for (literal in normalized) {
                if (!isActiveManualSource(this, literal)) {
                    inactive.add(literal)
                    continue
                }
                if (hasReviewHistory(this, literal)) {
                    reviewed.add(literal)
                    continue
                }
                val values = ContentValues().apply {
                    put(COLUMN_ACTIVE, 0)
                    put(LocalStoreBase.COLUMN_UPDATED_AT, safeNow)
                }
                val changed = update(
                    LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES,
                    values,
                    "$COLUMN_LITERAL=? AND $COLUMN_ACTIVE=1",
                    arrayOf(literal),
                )
                if (changed == 0) {
                    inactive.add(literal)
                    continue
                }
                removed.add(literal)
                if (!hasProviderDashboardRow(this, literal)) {
                    delete(
                        LocalStoreBase.TABLE_STUDY_ITEMS,
                        "${LocalStoreBase.COLUMN_KANJI}=?",
                        arrayOf(literal),
                    )
                }
            }
        }
        if (removed.isNotEmpty()) {
            manualSourcesChanged()
        }
        return ManualKanjiSourceRemovalResult(
            requestedCount = literals.size,
            removedLiterals = immutableSet(removed),
            reviewedLiterals = immutableSet(reviewed),
            inactiveLiterals = immutableSet(inactive),
            invalidCount = invalidCount,
        )
    }

    fun deactivateManualSources(literals: Collection<String>, nowMillis: Long): Int {
        val normalized = normalizeLiterals(literals)
        if (normalized.isEmpty()) {
            return 0
        }
        val safeNow = nowMillis.coerceAtLeast(0L)
        val changed = store.writableDatabase.transaction {
            var changed = 0
            val values = ContentValues().apply {
                put(COLUMN_ACTIVE, 0)
                put(LocalStoreBase.COLUMN_UPDATED_AT, safeNow)
            }
            for (literal in normalized) {
                changed += update(
                    LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES,
                    values,
                    "$COLUMN_LITERAL=? AND $COLUMN_ACTIVE=1",
                    arrayOf(literal),
                )
            }
            changed
        }
        if (changed > 0) {
            manualSourcesChanged()
        }
        return changed
    }

    fun recordExportReceipts(receipts: Collection<MissingKanjiExportReceipt>): Int {
        if (receipts.isEmpty()) {
            return 0
        }
        return store.writableDatabase.transaction {
            var written = 0
            for (receipt in receipts) {
                val literal = normalizeLiteral(receipt.literal) ?: continue
                val destination = normalizeMetadata(receipt.destinationKey, MAX_DESTINATION_KEY_LENGTH)
                if (destination.isEmpty()) {
                    continue
                }
                val values = ContentValues().apply {
                    put(COLUMN_LITERAL, literal)
                    put(COLUMN_DESTINATION_KEY, destination)
                    put(COLUMN_EXPORTED_AT, receipt.exportedAt.coerceAtLeast(0L))
                    if (receipt.externalNoteId == null) {
                        putNull(COLUMN_EXTERNAL_NOTE_ID)
                    } else {
                        put(COLUMN_EXTERNAL_NOTE_ID, receipt.externalNoteId)
                    }
                }
                val rowId = insertWithOnConflict(
                    LocalStoreBase.TABLE_MISSING_KANJI_EXPORTS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                if (rowId >= 0L) {
                    written += 1
                }
            }
            written
        }
    }

    fun exportReceipts(destinationKey: String): Map<String, MissingKanjiExportReceipt> {
        val destination = normalizeMetadata(destinationKey, MAX_DESTINATION_KEY_LENGTH)
        if (destination.isEmpty()) {
            return emptyMap()
        }
        val receipts = LinkedHashMap<String, MissingKanjiExportReceipt>()
        store.readableDatabase.query(
            LocalStoreBase.TABLE_MISSING_KANJI_EXPORTS,
            null,
            "$COLUMN_DESTINATION_KEY=?",
            arrayOf(destination),
            null,
            null,
            "$COLUMN_LITERAL ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val receipt = exportReceipt(cursor)
                receipts[receipt.literal] = receipt
            }
        }
        return Collections.unmodifiableMap(receipts)
    }

    private fun insertScan(
        db: SQLiteDatabase,
        startedAt: Long,
        completedAt: Long,
        status: MissingKanjiScanStatus,
        notesScanned: Int,
        fieldsScanned: Int,
        uniqueKanjiCount: Int,
        skippedNotes: Int,
        modelCount: Int,
        providerFingerprint: String,
        failureCode: String,
    ): Long {
        val values = ContentValues().apply {
            put(LocalStoreBase.COLUMN_STARTED_AT, startedAt)
            put(LocalStoreBase.COLUMN_COMPLETED_AT, completedAt)
            put(LocalStoreBase.COLUMN_STATUS, status.storedValue)
            put(COLUMN_NOTES_SCANNED, notesScanned.coerceAtLeast(0))
            put(COLUMN_FIELDS_SCANNED, fieldsScanned.coerceAtLeast(0))
            put(COLUMN_UNIQUE_KANJI, uniqueKanjiCount.coerceAtLeast(0))
            put(COLUMN_SKIPPED_NOTES, skippedNotes.coerceAtLeast(0))
            put(COLUMN_MODEL_COUNT, modelCount.coerceAtLeast(0))
            put(
                COLUMN_PROVIDER_FINGERPRINT,
                normalizeProviderFingerprint(providerFingerprint),
            )
            put(COLUMN_FAILURE_CODE, normalizeFailureCode(failureCode))
        }
        return db.insertOrThrow(LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS, null, values)
    }

    private fun scanById(db: SQLiteDatabase, id: Long): MissingKanjiScanRecord? {
        db.query(
            LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS,
            null,
            "id=?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) scan(cursor) else null
        }
    }

    private fun loadManualSources(
        db: SQLiteDatabase,
        activeOnly: Boolean,
    ): List<ManualKanjiSource> {
        val sources = ArrayList<ManualKanjiSource>()
        db.query(
            LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES,
            null,
            if (activeOnly) "$COLUMN_ACTIVE=1" else null,
            null,
            null,
            null,
            "$COLUMN_ACTIVE DESC, ${LocalStoreBase.COLUMN_JITEN_RANK} IS NULL, " +
                "${LocalStoreBase.COLUMN_JITEN_RANK} ASC, $COLUMN_LITERAL ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sources.add(manualSource(cursor))
            }
        }
        return Collections.unmodifiableList(sources)
    }

    private fun isActiveManualSource(db: SQLiteDatabase, literal: String): Boolean {
        return db.rawQuery(
            """
            SELECT 1
            FROM ${LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES}
            WHERE $COLUMN_LITERAL=? AND $COLUMN_ACTIVE=1
            LIMIT 1
            """.trimIndent(),
            arrayOf(literal),
        ).use(Cursor::moveToFirst)
    }

    private fun hasReviewHistory(db: SQLiteDatabase, literal: String): Boolean {
        return db.rawQuery(
            """
            SELECT 1
            WHERE EXISTS (
                SELECT 1
                FROM ${LocalStoreBase.TABLE_REVIEW_LOG}
                WHERE ${LocalStoreBase.COLUMN_KANJI}=?
            )
            OR EXISTS (
                SELECT 1
                FROM ${LocalStoreBase.TABLE_STUDY_ITEMS}
                WHERE ${LocalStoreBase.COLUMN_KANJI}=?
                  AND total_reviews>0
            )
            LIMIT 1
            """.trimIndent(),
            arrayOf(literal, literal),
        ).use(Cursor::moveToFirst)
    }

    private fun hasProviderDashboardRow(db: SQLiteDatabase, literal: String): Boolean {
        return db.rawQuery(
            """
            SELECT 1
            FROM ${LocalStoreBase.TABLE_DASHBOARD_ROWS}
            WHERE ${LocalStoreBase.COLUMN_KANJI}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(literal),
        ).use(Cursor::moveToFirst)
    }

    private fun insertManualSource(
        db: SQLiteDatabase,
        candidate: MissingKanjiCandidate,
        nowMillis: Long,
    ) {
        val values = manualSourceValues(candidate, nowMillis, active = true).apply {
            put(COLUMN_ADDED_AT, nowMillis)
        }
        db.insertOrThrow(LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES, null, values)
    }

    private fun updateManualSource(
        db: SQLiteDatabase,
        candidate: MissingKanjiCandidate,
        nowMillis: Long,
        active: Boolean,
    ) {
        db.update(
            LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES,
            manualSourceValues(candidate, nowMillis, active),
            "$COLUMN_LITERAL=?",
            arrayOf(candidate.literal),
        )
    }

    private fun manualSourceValues(
        candidate: MissingKanjiCandidate,
        nowMillis: Long,
        active: Boolean,
    ): ContentValues {
        return ContentValues().apply {
            put(COLUMN_LITERAL, candidate.literal)
            put(COLUMN_SOURCE_TYPE, SOURCE_TYPE_DICTIONARY)
            if (candidate.jitenRank == null) {
                putNull(LocalStoreBase.COLUMN_JITEN_RANK)
            } else {
                put(LocalStoreBase.COLUMN_JITEN_RANK, candidate.jitenRank)
            }
            put(COLUMN_MEANINGS_JSON, encodeList(candidate.meanings))
            put(COLUMN_ON_READINGS_JSON, encodeList(candidate.onReadings))
            put(COLUMN_KUN_READINGS_JSON, encodeList(candidate.kunReadings))
            put(LocalStoreBase.COLUMN_UPDATED_AT, nowMillis)
            put(COLUMN_ACTIVE, if (active) 1 else 0)
        }
    }

    private fun pruneScanHistory(db: SQLiteDatabase) {
        db.execSQL(
            "DELETE FROM ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS} " +
                "WHERE id NOT IN (" +
                "SELECT id FROM ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS} " +
                "ORDER BY id DESC LIMIT $MAX_SCAN_HISTORY" +
                ") AND id NOT IN (" +
                "SELECT DISTINCT $COLUMN_SCAN_ID FROM ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY}" +
                ") AND id <> COALESCE((" +
                "SELECT id FROM ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS} " +
                "WHERE ${LocalStoreBase.COLUMN_STATUS}='${MissingKanjiScanStatus.SUCCESS.storedValue}' " +
                "ORDER BY id DESC LIMIT 1" +
                "), -1" +
                ")",
        )
    }

    fun interface PublicationHook {
        fun beforeCommit()

        companion object {
            val NONE = PublicationHook { }
        }
    }

    companion object {
        const val SOURCE_TYPE_DICTIONARY = "dictionary"
        private const val COLUMN_LITERAL = "literal"
        private const val COLUMN_INVENTORY_LITERAL = "inventory_literal"
        private const val COLUMN_SCAN_ID = "scan_id"
        private const val COLUMN_OBSERVED_AT = "observed_at"
        private const val COLUMN_NOTES_SCANNED = "notes_scanned"
        private const val COLUMN_FIELDS_SCANNED = "fields_scanned"
        private const val COLUMN_UNIQUE_KANJI = "unique_kanji"
        private const val COLUMN_SKIPPED_NOTES = "skipped_notes"
        private const val COLUMN_MODEL_COUNT = "model_count"
        private const val COLUMN_PROVIDER_FINGERPRINT = "provider_fingerprint"
        private const val COLUMN_FAILURE_CODE = "failure_code"
        private const val COLUMN_SOURCE_TYPE = "source_type"
        private const val COLUMN_MEANINGS_JSON = "meanings_json"
        private const val COLUMN_ON_READINGS_JSON = "on_readings_json"
        private const val COLUMN_KUN_READINGS_JSON = "kun_readings_json"
        private const val COLUMN_ADDED_AT = "added_at"
        private const val COLUMN_ACTIVE = "active"
        private const val COLUMN_DESTINATION_KEY = "destination_key"
        private const val COLUMN_EXPORTED_AT = "exported_at"
        private const val COLUMN_EXTERNAL_NOTE_ID = "external_note_id"
        private const val SETTING_PRESET = "missing_kanji_frequency_preset"
        private const val SETTING_RANK_MIN = "missing_kanji_rank_min"
        private const val SETTING_RANK_MAX = "missing_kanji_rank_max"
        private const val SETTING_INCLUDE_UNRANKED = "missing_kanji_include_unranked"
        private const val SETTING_SEARCH_QUERY = "missing_kanji_search_query"
        private const val MAX_SCAN_HISTORY = 50
        private const val MAX_DESTINATION_KEY_LENGTH = 256
        private const val UNKNOWN_PROVIDER_FINGERPRINT = "authority=unknown;spec=-1"
        private const val UNKNOWN_FAILURE_CODE = "unknown"
        private val PROVIDER_FINGERPRINT_PATTERN = Pattern.compile(
            "authority=[A-Za-z0-9._-]{1,160};spec=-?[0-9]{1,10}",
        )
        private val ALLOWED_FAILURE_CODES = setOf(
            "",
            "not_installed",
            "permission_missing",
            "provider_unavailable",
            "cancelled",
            "malformed_rows",
            UNKNOWN_FAILURE_CODE,
        )

        private fun scan(cursor: Cursor): MissingKanjiScanRecord {
            return MissingKanjiScanRecord(
                id = cursor.long("id"),
                startedAt = cursor.long(LocalStoreBase.COLUMN_STARTED_AT),
                completedAt = cursor.long(LocalStoreBase.COLUMN_COMPLETED_AT),
                status = MissingKanjiScanStatus.fromStored(cursor.text(LocalStoreBase.COLUMN_STATUS)),
                notesScanned = cursor.integer(COLUMN_NOTES_SCANNED),
                fieldsScanned = cursor.integer(COLUMN_FIELDS_SCANNED),
                uniqueKanjiCount = cursor.integer(COLUMN_UNIQUE_KANJI),
                skippedNotes = cursor.integer(COLUMN_SKIPPED_NOTES),
                modelCount = cursor.integer(COLUMN_MODEL_COUNT),
                providerFingerprint = cursor.text(COLUMN_PROVIDER_FINGERPRINT),
                failureCode = cursor.text(COLUMN_FAILURE_CODE),
            )
        }

        private fun manualSource(cursor: Cursor): ManualKanjiSource {
            return ManualKanjiSource(
                candidate = MissingKanjiCandidate(
                    literal = cursor.text(COLUMN_LITERAL),
                    meanings = decodeList(cursor.text(COLUMN_MEANINGS_JSON)),
                    onReadings = decodeList(cursor.text(COLUMN_ON_READINGS_JSON)),
                    kunReadings = decodeList(cursor.text(COLUMN_KUN_READINGS_JSON)),
                    jitenRank = cursor.nullableInt(LocalStoreBase.COLUMN_JITEN_RANK),
                ),
                sourceType = cursor.text(COLUMN_SOURCE_TYPE),
                addedAt = cursor.long(COLUMN_ADDED_AT),
                updatedAt = cursor.long(LocalStoreBase.COLUMN_UPDATED_AT),
                active = cursor.integer(COLUMN_ACTIVE) == 1,
            )
        }

        private fun exportReceipt(cursor: Cursor): MissingKanjiExportReceipt {
            return MissingKanjiExportReceipt(
                literal = cursor.text(COLUMN_LITERAL),
                destinationKey = cursor.text(COLUMN_DESTINATION_KEY),
                exportedAt = cursor.long(COLUMN_EXPORTED_AT),
                externalNoteId = cursor.nullableLong(COLUMN_EXTERNAL_NOTE_ID),
            )
        }

        private fun normalizeCandidate(candidate: MissingKanjiCandidate): MissingKanjiCandidate? {
            val literal = normalizeLiteral(candidate.literal) ?: return null
            val rank = candidate.jitenRank
            if (rank != null && rank < 1) {
                return null
            }
            return MissingKanjiCandidate(
                literal = literal,
                meanings = normalizeTextList(candidate.meanings),
                onReadings = normalizeTextList(candidate.onReadings),
                kunReadings = normalizeTextList(candidate.kunReadings),
                jitenRank = rank,
            )
        }

        private fun normalizeLiterals(values: Iterable<String>): Set<String> {
            val normalized = LinkedHashSet<String>()
            for (value in values) {
                normalizeLiteral(value)?.let(normalized::add)
            }
            return normalized
        }

        private fun normalizeLiteral(value: String?): String? {
            return TextUtil.normalizeSingleKanji(value).takeIf(String::isNotEmpty)
        }

        private fun normalizeTextList(values: List<String>): List<String> {
            return values.asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .toList()
        }

        private fun normalizeMetadata(value: String?, maxLength: Int): String {
            val normalized = value.orEmpty().trim()
            if (normalized.length <= maxLength) {
                return normalized
            }
            var end = maxLength
            if (end > 0 && Character.isHighSurrogate(normalized[end - 1])) {
                end -= 1
            }
            return normalized.substring(0, end)
        }

        private fun normalizeProviderFingerprint(value: String?): String {
            val normalized = value.orEmpty().trim()
            return if (PROVIDER_FINGERPRINT_PATTERN.matcher(normalized).matches()) {
                normalized
            } else {
                UNKNOWN_PROVIDER_FINGERPRINT
            }
        }

        private fun normalizeFailureCode(value: String?): String {
            val normalized = value.orEmpty().trim()
            return normalized.takeIf(ALLOWED_FAILURE_CODES::contains) ?: UNKNOWN_FAILURE_CODE
        }

        private fun encodeList(values: List<String>): String {
            val json = JSONArray()
            for (value in values) {
                json.put(value)
            }
            return json.toString()
        }

        private fun decodeList(value: String): List<String> {
            return try {
                val json = JSONArray(value)
                val values = ArrayList<String>(json.length())
                for (index in 0 until json.length()) {
                    val item = json.optString(index).trim()
                    if (item.isNotEmpty() && !values.contains(item)) {
                        values.add(item)
                    }
                }
                Collections.unmodifiableList(values)
            } catch (_: JSONException) {
                emptyList()
            }
        }

        private fun <T> immutableSet(values: LinkedHashSet<T>): Set<T> {
            return Collections.unmodifiableSet(LinkedHashSet(values))
        }

        private fun Cursor.text(column: String): String {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) "" else getString(index)
        }

        private fun Cursor.integer(column: String): Int = getInt(getColumnIndexOrThrow(column))

        private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

        private fun Cursor.nullableInt(column: String): Int? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getInt(index)
        }

        private fun Cursor.nullableLong(column: String): Long? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getLong(index)
        }
    }
}
