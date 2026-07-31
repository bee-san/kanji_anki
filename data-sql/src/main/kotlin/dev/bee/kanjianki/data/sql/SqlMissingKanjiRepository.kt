package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.ManualKanjiAdmissionPolicy
import dev.bee.kanjianki.core.ManualKanjiSource
import dev.bee.kanjianki.core.ManualKanjiSourceRemovalResult
import dev.bee.kanjianki.core.ManualKanjiSourceWriteResult
import dev.bee.kanjianki.core.MissingKanjiAnalyzer
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportReceipt
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiInventoryState
import dev.bee.kanjianki.core.MissingKanjiPreferences
import dev.bee.kanjianki.core.MissingKanjiScanRecord
import dev.bee.kanjianki.core.MissingKanjiScanStatus
import dev.bee.kanjianki.core.StoredAnkiKanjiInventory
import dev.bee.kanjianki.core.StringListJsonCodec
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.data.AddManualKanjiSourcesCommand
import dev.bee.kanjianki.data.DeactivateManualKanjiSourcesCommand
import dev.bee.kanjianki.data.MissingKanjiRepository
import dev.bee.kanjianki.data.PublishMissingKanjiInventoryCommand
import dev.bee.kanjianki.data.RecordMissingKanjiScanCommand
import dev.bee.kanjianki.data.RemoveManualKanjiSourcesCommand
import java.util.regex.Pattern

/**
 * Driver-neutral Missing Kanji persistence. Ported from the app's
 * MissingKanjiStore: aggregate-only scan history + atomic inventory
 * publication, frequency-range preferences, manual dictionary sources, and
 * export receipts. Accepts inventory literals and dictionary metadata only.
 * Android production stays on LocalStore until Goal 184.
 */
class SqlMissingKanjiRepository(
    private val database: SqlDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : MissingKanjiRepository {
    override suspend fun publishInventory(command: PublishMissingKanjiInventoryCommand) = safeSqlStoreCall {
        val literals = normalizeLiterals(command.inventory.literals)
        val startedAt = command.startedAtMillis.coerceAtLeast(0L)
        val completedAt = command.completedAtMillis.coerceAtLeast(startedAt)
        database.write {
            val scanId = insertScan(
                startedAt, completedAt, MissingKanjiScanStatus.SUCCESS,
                command.inventory.notesScanned, command.inventory.fieldsScanned, literals.size,
                command.inventory.skippedNotes, command.inventory.modelCount,
                command.providerFingerprint, "",
            )
            executeBound("DELETE FROM anki_kanji_inventory")
            literals.forEach { literal ->
                executeBound(
                    "INSERT INTO anki_kanji_inventory(literal, scan_id, observed_at) VALUES (?, ?, ?)",
                ) {
                    bindText(1, literal)
                    bindLong(2, scanId)
                    bindLong(3, completedAt)
                }
            }
            pruneScanHistory()
            scanById(scanId) ?: error("Published Missing Kanji scan was not readable.")
        }
    }

    override suspend fun recordUnsuccessfulScan(command: RecordMissingKanjiScanCommand) = safeSqlStoreCall {
        require(command.status != MissingKanjiScanStatus.SUCCESS) {
            "Successful scans must be atomically published with their inventory."
        }
        val startedAt = command.startedAtMillis.coerceAtLeast(0L)
        val completedAt = command.completedAtMillis.coerceAtLeast(startedAt)
        database.write {
            val scanId = insertScan(
                startedAt, completedAt, command.status, command.notesScanned, command.fieldsScanned,
                command.uniqueKanjiCount, command.skippedNotes, command.modelCount,
                command.providerFingerprint, command.failureCode,
            )
            pruneScanHistory()
            scanById(scanId) ?: error("Recorded Missing Kanji scan was not readable.")
        }
    }

    override suspend fun inventoryState() = safeSqlStoreCall {
        database.readSnapshot {
            val latest = latestScan()
            val publishedScan = latestScan(MissingKanjiScanStatus.SUCCESS)
            val published = publishedScan?.let { scan ->
                StoredAnkiKanjiInventory(scan, publishedLiterals(scan.id))
            }
            MissingKanjiInventoryState(published, latest)
        }
    }

    override suspend fun loadPreferences() = safeSqlStoreCall {
        database.readSnapshot {
            val defaults = MissingKanjiPreferences()
            val range = MissingKanjiFrequencyRange(
                minimumRank = intSetting(SETTING_RANK_MIN, defaults.range.minimumRank),
                maximumRank = intSetting(SETTING_RANK_MAX, defaults.range.maximumRank),
                includeUnranked = intSetting(SETTING_INCLUDE_UNRANKED, 0) == 1,
            )
            if (MissingKanjiAnalyzer.validateRange(range) != null) {
                defaults
            } else {
                val stored = stringSetting(SETTING_PRESET, defaults.preset)
                val preset = stored.takeIf(MissingKanjiPreferences.SUPPORTED_PRESETS::contains)
                    ?: MissingKanjiPreferences.PRESET_CUSTOM
                MissingKanjiPreferences(preset, range, stringSetting(SETTING_SEARCH_QUERY, ""))
            }
        }
    }

    override suspend fun savePreferences(preferences: MissingKanjiPreferences) = safeSqlStoreCall {
        require(MissingKanjiAnalyzer.validateRange(preferences.range) == null) {
            "Missing Kanji frequency range is invalid."
        }
        val preset = preferences.preset.takeIf(MissingKanjiPreferences.SUPPORTED_PRESETS::contains)
            ?: MissingKanjiPreferences.PRESET_CUSTOM
        database.write {
            putSetting(SETTING_PRESET, preset)
            putSetting(SETTING_RANK_MIN, preferences.range.minimumRank.toString())
            putSetting(SETTING_RANK_MAX, preferences.range.maximumRank.toString())
            putSetting(SETTING_INCLUDE_UNRANKED, if (preferences.range.includeUnranked) "1" else "0")
            putSetting(SETTING_SEARCH_QUERY, preferences.searchQuery.trim())
        }
        Unit
    }

    override suspend fun addManualSources(command: AddManualKanjiSourcesCommand) = safeSqlStoreCall {
        var invalidCount = 0
        val structurallyValid = ArrayList<MissingKanjiCandidate>(command.candidates.size)
        for (candidate in command.candidates) {
            val normalized = normalizeCandidate(candidate)
            if (normalized == null) invalidCount++ else structurallyValid.add(normalized)
        }
        val admission = ManualKanjiAdmissionPolicy.planAddition(structurallyValid, emptySet(), emptySet())
        val normalized = admission.candidatesToAdd.associateByTo(LinkedHashMap(), MissingKanjiCandidate::literal)
        val added = LinkedHashSet<String>()
        val reactivated = LinkedHashSet<String>()
        val alreadyActive = LinkedHashSet<String>()
        val now = command.nowMillis.coerceAtLeast(0L)
        database.write {
            val existing = loadManualSources(activeOnly = false).associateBy { it.candidate.literal }
            for ((literal, candidate) in normalized) {
                val current = existing[literal]
                when {
                    current == null -> {
                        insertManualSource(candidate, now)
                        added.add(literal)
                    }
                    current.active -> {
                        updateManualSource(candidate, now, active = true)
                        alreadyActive.add(literal)
                    }
                    else -> {
                        updateManualSource(candidate, now, active = true)
                        reactivated.add(literal)
                    }
                }
            }
        }
        ManualKanjiSourceWriteResult(
            requestedCount = command.candidates.size,
            addedLiterals = added.toSet(),
            reactivatedLiterals = reactivated.toSet(),
            alreadyActiveLiterals = alreadyActive.toSet(),
            missingMeaningLiterals = admission.missingMeaningLiterals,
            missingReadingLiterals = admission.missingReadingLiterals,
            invalidCount = invalidCount,
            duplicateCount = admission.duplicateCount,
        )
    }

    override suspend fun manualSources(activeOnly: Boolean) = safeSqlStoreCall {
        database.readSnapshot { loadManualSources(activeOnly) }
    }

    override suspend fun admittedManualSources() = safeSqlStoreCall {
        database.readSnapshot {
            queryList(
                """
                SELECT manual.* FROM manual_kanji_sources manual
                WHERE manual.active = 1
                  AND EXISTS (
                    SELECT 1 FROM study_items item
                    WHERE item.kanji = manual.literal AND item.state <> ?
                  )
                ORDER BY manual.jiten_rank IS NULL, manual.jiten_rank, manual.literal
                """.trimIndent(),
                bind = { bindText(1, STATE_RETIRED) },
                map = ::manualSource,
            )
        }
    }

    override suspend fun manualSource(literal: String) = safeSqlStoreCall {
        val normalized = normalizeLiteral(literal) ?: return@safeSqlStoreCall null
        database.readSnapshot {
            queryOneOrNull(
                "SELECT * FROM manual_kanji_sources WHERE literal = ? AND active = 1 LIMIT 1",
                bind = { bindText(1, normalized) },
                map = ::manualSource,
            )
        }
    }

    override suspend fun removableManualSourceLiterals() = safeSqlStoreCall {
        database.readSnapshot {
            queryList(
                """
                SELECT manual.literal FROM manual_kanji_sources manual
                WHERE manual.active = 1
                  AND NOT EXISTS (SELECT 1 FROM review_log review WHERE review.kanji = manual.literal)
                  AND NOT EXISTS (
                    SELECT 1 FROM study_items item WHERE item.kanji = manual.literal AND item.total_reviews > 0
                  )
                ORDER BY manual.literal
                """.trimIndent(),
            ) { row -> row.text(0) }.toSet()
        }
    }

    override suspend fun removeUnreviewedManualSources(command: RemoveManualKanjiSourcesCommand) = safeSqlStoreCall {
        val normalized = LinkedHashSet<String>()
        var invalidCount = 0
        command.literals.forEach { literal ->
            val value = normalizeLiteral(literal)
            if (value == null) invalidCount++ else normalized.add(value)
        }
        val removed = LinkedHashSet<String>()
        val reviewed = LinkedHashSet<String>()
        val inactive = LinkedHashSet<String>()
        val now = command.nowMillis.coerceAtLeast(0L)
        database.write {
            for (literal in normalized) {
                if (!isActiveManualSource(literal)) {
                    inactive.add(literal)
                    continue
                }
                if (hasReviewHistory(literal)) {
                    reviewed.add(literal)
                    continue
                }
                executeBound(
                    "UPDATE manual_kanji_sources SET active = 0, updated_at = ? WHERE literal = ? AND active = 1",
                ) {
                    bindLong(1, now)
                    bindText(2, literal)
                }
                if (changes() == 0L) {
                    inactive.add(literal)
                    continue
                }
                removed.add(literal)
                if (!hasProviderDashboardRow(literal)) {
                    executeBound("DELETE FROM study_items WHERE kanji = ?", bind = { bindText(1, literal) })
                }
            }
        }
        ManualKanjiSourceRemovalResult(
            requestedCount = command.literals.size,
            removedLiterals = removed.toSet(),
            reviewedLiterals = reviewed.toSet(),
            inactiveLiterals = inactive.toSet(),
            invalidCount = invalidCount,
        )
    }

    override suspend fun deactivateManualSources(command: DeactivateManualKanjiSourcesCommand) = safeSqlStoreCall {
        val normalized = normalizeLiterals(command.literals)
        if (normalized.isEmpty()) {
            return@safeSqlStoreCall 0
        }
        val now = command.nowMillis.coerceAtLeast(0L)
        database.write {
            var changed = 0
            for (literal in normalized) {
                executeBound(
                    "UPDATE manual_kanji_sources SET active = 0, updated_at = ? WHERE literal = ? AND active = 1",
                ) {
                    bindLong(1, now)
                    bindText(2, literal)
                }
                changed += changes().toInt()
            }
            changed
        }
    }

    override suspend fun recordExportReceipts(receipts: Collection<MissingKanjiExportReceipt>) = safeSqlStoreCall {
        if (receipts.isEmpty()) {
            return@safeSqlStoreCall 0
        }
        database.write {
            var written = 0
            for (receipt in receipts) {
                val literal = normalizeLiteral(receipt.literal) ?: continue
                val destination = normalizeMetadata(receipt.destinationKey)
                if (destination.isEmpty()) continue
                prepare(
                    """
                    INSERT OR REPLACE INTO missing_kanji_exports(literal, destination_key, exported_at, external_note_id)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.bindText(1, literal)
                    statement.bindText(2, destination)
                    statement.bindLong(3, receipt.exportedAt.coerceAtLeast(0L))
                    val externalNoteId = receipt.externalNoteId
                    if (externalNoteId == null) statement.bindNull(4) else statement.bindLong(4, externalNoteId)
                    statement.execute()
                }
                written++
            }
            written
        }
    }

    override suspend fun exportReceipts(destinationKey: String) = safeSqlStoreCall {
        val destination = normalizeMetadata(destinationKey)
        if (destination.isEmpty()) {
            return@safeSqlStoreCall emptyMap()
        }
        database.readSnapshot {
            queryList(
                "SELECT * FROM missing_kanji_exports WHERE destination_key = ? ORDER BY literal ASC",
                bind = { bindText(1, destination) },
                map = ::exportReceipt,
            ).associateBy { it.literal }
        }
    }

    // --- transaction helpers ----------------------------------------------

    private fun SqlTransactionScope.insertScan(
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
        executeBound(
            """
            INSERT INTO anki_kanji_inventory_scans(
                started_at, completed_at, status, notes_scanned, fields_scanned, unique_kanji,
                skipped_notes, model_count, provider_fingerprint, failure_code
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindLong(1, startedAt)
            bindLong(2, completedAt)
            bindText(3, status.storedValue)
            bindLong(4, notesScanned.coerceAtLeast(0).toLong())
            bindLong(5, fieldsScanned.coerceAtLeast(0).toLong())
            bindLong(6, uniqueKanjiCount.coerceAtLeast(0).toLong())
            bindLong(7, skippedNotes.coerceAtLeast(0).toLong())
            bindLong(8, modelCount.coerceAtLeast(0).toLong())
            bindText(9, normalizeProviderFingerprint(providerFingerprint))
            bindText(10, normalizeFailureCode(failureCode))
        }
        return lastInsertRowId()
    }

    private fun SqlTransactionScope.pruneScanHistory() {
        executeBound(
            """
            DELETE FROM anki_kanji_inventory_scans
            WHERE id NOT IN (SELECT id FROM anki_kanji_inventory_scans ORDER BY id DESC LIMIT $MAX_SCAN_HISTORY)
              AND id NOT IN (SELECT DISTINCT scan_id FROM anki_kanji_inventory)
              AND id <> COALESCE(
                (SELECT id FROM anki_kanji_inventory_scans WHERE status = ? ORDER BY id DESC LIMIT 1), -1
              )
            """.trimIndent(),
        ) {
            bindText(1, MissingKanjiScanStatus.SUCCESS.storedValue)
        }
    }

    private fun SqlSession.scanById(id: Long): MissingKanjiScanRecord? =
        queryOneOrNull(
            "SELECT * FROM anki_kanji_inventory_scans WHERE id = ? LIMIT 1",
            bind = { bindLong(1, id) },
            map = ::scan,
        )

    private fun SqlSession.latestScan(status: MissingKanjiScanStatus? = null): MissingKanjiScanRecord? =
        if (status == null) {
            queryOneOrNull("SELECT * FROM anki_kanji_inventory_scans ORDER BY id DESC LIMIT 1", map = ::scan)
        } else {
            queryOneOrNull(
                "SELECT * FROM anki_kanji_inventory_scans WHERE status = ? ORDER BY id DESC LIMIT 1",
                bind = { bindText(1, status.storedValue) },
                map = ::scan,
            )
        }

    private fun SqlSession.publishedLiterals(scanId: Long): Set<String> =
        queryList(
            "SELECT literal FROM anki_kanji_inventory WHERE scan_id = ? ORDER BY literal ASC",
            bind = { bindLong(1, scanId) },
        ) { row -> row.text(0) }.toCollection(LinkedHashSet())

    private fun SqlSession.loadManualSources(activeOnly: Boolean): List<ManualKanjiSource> {
        val where = if (activeOnly) "WHERE active = 1" else ""
        return queryList(
            """
            SELECT * FROM manual_kanji_sources $where
            ORDER BY active DESC, jiten_rank IS NULL, jiten_rank ASC, literal ASC
            """.trimIndent(),
            map = ::manualSource,
        )
    }

    private fun SqlSession.isActiveManualSource(literal: String): Boolean =
        queryOneOrNull(
            "SELECT 1 FROM manual_kanji_sources WHERE literal = ? AND active = 1 LIMIT 1",
            bind = { bindText(1, literal) },
        ) { true } == true

    private fun SqlSession.hasReviewHistory(literal: String): Boolean =
        queryOneOrNull(
            """
            SELECT 1 WHERE EXISTS (SELECT 1 FROM review_log WHERE kanji = ?)
              OR EXISTS (SELECT 1 FROM study_items WHERE kanji = ? AND total_reviews > 0)
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, literal)
                bindText(2, literal)
            },
        ) { true } == true

    private fun SqlSession.hasProviderDashboardRow(literal: String): Boolean =
        queryOneOrNull(
            "SELECT 1 FROM dashboard_rows WHERE kanji = ? LIMIT 1",
            bind = { bindText(1, literal) },
        ) { true } == true

    private fun SqlTransactionScope.insertManualSource(candidate: MissingKanjiCandidate, now: Long) {
        prepare(
            """
            INSERT INTO manual_kanji_sources(
                literal, source_type, jiten_rank, meanings_json, on_readings_json, kun_readings_json,
                added_at, updated_at, active
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, candidate.literal)
            statement.bindText(2, ManualKanjiSource.SOURCE_TYPE_DICTIONARY)
            val insertRank = candidate.jitenRank
            if (insertRank == null) statement.bindNull(3) else statement.bindLong(3, insertRank.toLong())
            statement.bindText(4, StringListJsonCodec.encode(candidate.meanings))
            statement.bindText(5, StringListJsonCodec.encode(candidate.onReadings))
            statement.bindText(6, StringListJsonCodec.encode(candidate.kunReadings))
            statement.bindLong(7, now)
            statement.bindLong(8, now)
            statement.execute()
        }
    }

    private fun SqlTransactionScope.updateManualSource(candidate: MissingKanjiCandidate, now: Long, active: Boolean) {
        prepare(
            """
            UPDATE manual_kanji_sources SET
                source_type = ?, jiten_rank = ?, meanings_json = ?, on_readings_json = ?,
                kun_readings_json = ?, updated_at = ?, active = ?
            WHERE literal = ?
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, ManualKanjiSource.SOURCE_TYPE_DICTIONARY)
            val updateRank = candidate.jitenRank
            if (updateRank == null) statement.bindNull(2) else statement.bindLong(2, updateRank.toLong())
            statement.bindText(3, StringListJsonCodec.encode(candidate.meanings))
            statement.bindText(4, StringListJsonCodec.encode(candidate.onReadings))
            statement.bindText(5, StringListJsonCodec.encode(candidate.kunReadings))
            statement.bindLong(6, now)
            statement.bindLong(7, if (active) 1L else 0L)
            statement.bindText(8, candidate.literal)
            statement.execute()
        }
    }

    private fun SqlSession.intSetting(key: String, fallback: Int): Int =
        queryOneOrNull(
            "SELECT value FROM settings WHERE key = ? LIMIT 1",
            bind = { bindText(1, key) },
        ) { row -> row.text(0).toIntOrNull() } ?: fallback

    private fun SqlSession.stringSetting(key: String, fallback: String): String =
        queryOneOrNull(
            "SELECT value FROM settings WHERE key = ? LIMIT 1",
            bind = { bindText(1, key) },
        ) { row -> row.text(0) } ?: fallback

    private fun SqlTransactionScope.putSetting(key: String, value: String) {
        executeBound(
            """
            INSERT INTO settings(key, value, updated_at) VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
            """.trimIndent(),
        ) {
            bindText(1, key)
            bindText(2, value)
            bindLong(3, clock())
        }
    }

    private fun scan(row: SqlRow): MissingKanjiScanRecord {
        val values = NamedSqlRow(row)
        return MissingKanjiScanRecord(
            id = values.long("id"),
            startedAt = values.long("started_at"),
            completedAt = values.long("completed_at"),
            status = MissingKanjiScanStatus.fromStored(values.text("status")),
            notesScanned = values.int("notes_scanned"),
            fieldsScanned = values.int("fields_scanned"),
            uniqueKanjiCount = values.int("unique_kanji"),
            skippedNotes = values.int("skipped_notes"),
            modelCount = values.int("model_count"),
            providerFingerprint = values.text("provider_fingerprint"),
            failureCode = values.text("failure_code"),
        )
    }

    private fun manualSource(row: SqlRow): ManualKanjiSource {
        val values = NamedSqlRow(row)
        return ManualKanjiSource(
            candidate = MissingKanjiCandidate(
                literal = values.text("literal"),
                meanings = StringListJsonCodec.decode(values.text("meanings_json")),
                onReadings = StringListJsonCodec.decode(values.text("on_readings_json")),
                kunReadings = StringListJsonCodec.decode(values.text("kun_readings_json")),
                jitenRank = values.nullableInt("jiten_rank"),
            ),
            sourceType = values.text("source_type"),
            addedAt = values.long("added_at"),
            updatedAt = values.long("updated_at"),
            active = values.int("active") == 1,
        )
    }

    private fun exportReceipt(row: SqlRow): MissingKanjiExportReceipt {
        val values = NamedSqlRow(row)
        return MissingKanjiExportReceipt(
            literal = values.text("literal"),
            destinationKey = values.text("destination_key"),
            exportedAt = values.long("exported_at"),
            externalNoteId = values.nullableLong("external_note_id"),
        )
    }

    private companion object {
        const val STATE_RETIRED = "retired"
        const val MAX_SCAN_HISTORY = 50
        const val MAX_DESTINATION_KEY_LENGTH = 256
        const val UNKNOWN_PROVIDER_FINGERPRINT = "authority=unknown;spec=-1"
        const val UNKNOWN_FAILURE_CODE = "unknown"
        const val SETTING_PRESET = "missing_kanji_frequency_preset"
        const val SETTING_RANK_MIN = "missing_kanji_rank_min"
        const val SETTING_RANK_MAX = "missing_kanji_rank_max"
        const val SETTING_INCLUDE_UNRANKED = "missing_kanji_include_unranked"
        const val SETTING_SEARCH_QUERY = "missing_kanji_search_query"

        val PROVIDER_FINGERPRINT_PATTERN: Pattern =
            Pattern.compile("authority=[A-Za-z0-9._-]{1,160};spec=-?[0-9]{1,10}")
        val ALLOWED_FAILURE_CODES = setOf(
            "", "not_installed", "permission_missing", "provider_unavailable",
            "cancelled", "malformed_rows", UNKNOWN_FAILURE_CODE,
        )

        fun normalizeCandidate(candidate: MissingKanjiCandidate): MissingKanjiCandidate? {
            val literal = normalizeLiteral(candidate.literal) ?: return null
            val rank = candidate.jitenRank
            if (rank != null && rank < 1) return null
            return MissingKanjiCandidate(
                literal = literal,
                meanings = normalizeTextList(candidate.meanings),
                onReadings = normalizeTextList(candidate.onReadings),
                kunReadings = normalizeTextList(candidate.kunReadings),
                jitenRank = rank,
            )
        }

        fun normalizeLiterals(values: Iterable<String>): Set<String> {
            val out = LinkedHashSet<String>()
            values.forEach { normalizeLiteral(it)?.let(out::add) }
            return out
        }

        fun normalizeLiteral(value: String?): String? =
            TextUtil.normalizeSingleKanji(value).takeIf(String::isNotEmpty)

        fun normalizeTextList(values: List<String>): List<String> =
            values.asSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()

        fun normalizeMetadata(value: String?): String {
            val normalized = value.orEmpty().trim()
            if (normalized.length <= MAX_DESTINATION_KEY_LENGTH) return normalized
            var end = MAX_DESTINATION_KEY_LENGTH
            if (end > 0 && Character.isHighSurrogate(normalized[end - 1])) end -= 1
            return normalized.substring(0, end)
        }

        fun normalizeProviderFingerprint(value: String?): String {
            val normalized = value.orEmpty().trim()
            return if (PROVIDER_FINGERPRINT_PATTERN.matcher(normalized).matches()) normalized else UNKNOWN_PROVIDER_FINGERPRINT
        }

        fun normalizeFailureCode(value: String?): String {
            val normalized = value.orEmpty().trim()
            return normalized.takeIf(ALLOWED_FAILURE_CODES::contains) ?: UNKNOWN_FAILURE_CODE
        }
    }
}
