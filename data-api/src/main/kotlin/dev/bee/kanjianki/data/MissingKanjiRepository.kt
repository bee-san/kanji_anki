package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.AnkiKanjiInventory
import dev.bee.kanjianki.core.ManualKanjiSource
import dev.bee.kanjianki.core.ManualKanjiSourceRemovalResult
import dev.bee.kanjianki.core.ManualKanjiSourceWriteResult
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportReceipt
import dev.bee.kanjianki.core.MissingKanjiInventoryState
import dev.bee.kanjianki.core.MissingKanjiPreferences
import dev.bee.kanjianki.core.MissingKanjiScanRecord
import dev.bee.kanjianki.core.MissingKanjiScanStatus

/**
 * Aggregate-only Missing Kanji persistence: Anki-inventory scan history and its
 * atomic publication, frequency-range preferences, manual dictionary sources,
 * and export receipts. It accepts inventory literals and dictionary metadata
 * only — it has no capability to persist Anki note fields.
 */
interface MissingKanjiRepository {
    suspend fun publishInventory(command: PublishMissingKanjiInventoryCommand): StoreResult<MissingKanjiScanRecord>

    suspend fun recordUnsuccessfulScan(command: RecordMissingKanjiScanCommand): StoreResult<MissingKanjiScanRecord>

    suspend fun inventoryState(): StoreResult<MissingKanjiInventoryState>

    suspend fun loadPreferences(): StoreResult<MissingKanjiPreferences>

    suspend fun savePreferences(preferences: MissingKanjiPreferences): StoreResult<Unit>

    suspend fun addManualSources(command: AddManualKanjiSourcesCommand): StoreResult<ManualKanjiSourceWriteResult>

    suspend fun manualSources(activeOnly: Boolean): StoreResult<List<ManualKanjiSource>>

    suspend fun admittedManualSources(): StoreResult<List<ManualKanjiSource>>

    suspend fun manualSource(literal: String): StoreResult<ManualKanjiSource?>

    suspend fun removableManualSourceLiterals(): StoreResult<Set<String>>

    suspend fun removeUnreviewedManualSources(
        command: RemoveManualKanjiSourcesCommand,
    ): StoreResult<ManualKanjiSourceRemovalResult>

    suspend fun deactivateManualSources(command: DeactivateManualKanjiSourcesCommand): StoreResult<Int>

    suspend fun recordExportReceipts(receipts: Collection<MissingKanjiExportReceipt>): StoreResult<Int>

    suspend fun exportReceipts(destinationKey: String): StoreResult<Map<String, MissingKanjiExportReceipt>>
}

data class PublishMissingKanjiInventoryCommand(
    val inventory: AnkiKanjiInventory,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val providerFingerprint: String,
)

data class RecordMissingKanjiScanCommand(
    val status: MissingKanjiScanStatus,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val notesScanned: Int,
    val fieldsScanned: Int,
    val uniqueKanjiCount: Int,
    val skippedNotes: Int,
    val modelCount: Int,
    val providerFingerprint: String,
    val failureCode: String,
)

data class AddManualKanjiSourcesCommand(
    val candidates: Collection<MissingKanjiCandidate>,
    val nowMillis: Long,
)

data class RemoveManualKanjiSourcesCommand(
    val literals: Collection<String>,
    val nowMillis: Long,
)

data class DeactivateManualKanjiSourcesCommand(
    val literals: Collection<String>,
    val nowMillis: Long,
)
