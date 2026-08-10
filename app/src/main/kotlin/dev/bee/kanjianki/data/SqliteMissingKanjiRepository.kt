package dev.bee.kanjianki.data

internal class SqliteMissingKanjiRepository(
    private val store: LocalStore,
) : MissingKanjiRepository {
    private fun missingKanji(): MissingKanjiStore = store.missingKanjiStore()

    override suspend fun publishInventory(command: PublishMissingKanjiInventoryCommand) = safeStoreCall {
        missingKanji().publishInventory(
            command.inventory,
            command.startedAtMillis,
            command.completedAtMillis,
            command.providerFingerprint,
        )
    }

    override suspend fun recordUnsuccessfulScan(command: RecordMissingKanjiScanCommand) = safeStoreCall {
        missingKanji().recordUnsuccessfulScan(
            command.status,
            command.startedAtMillis,
            command.completedAtMillis,
            command.notesScanned,
            command.fieldsScanned,
            command.uniqueKanjiCount,
            command.skippedNotes,
            command.modelCount,
            command.providerFingerprint,
            command.failureCode,
        )
    }

    override suspend fun inventoryState() = safeStoreCall {
        missingKanji().inventoryState()
    }

    override suspend fun loadPreferences() = safeStoreCall {
        missingKanji().loadPreferences()
    }

    override suspend fun savePreferences(preferences: dev.bee.kanjianki.core.MissingKanjiPreferences) = safeStoreCall {
        missingKanji().savePreferences(preferences)
    }

    override suspend fun addManualSources(command: AddManualKanjiSourcesCommand) = safeStoreCall {
        missingKanji().addManualSources(command.candidates, command.nowMillis)
    }

    override suspend fun manualSources(activeOnly: Boolean) = safeStoreCall {
        missingKanji().manualSources(activeOnly)
    }

    override suspend fun admittedManualSources() = safeStoreCall {
        missingKanji().admittedManualSources()
    }

    override suspend fun manualSource(literal: String) = safeStoreCall {
        missingKanji().manualSource(literal)
    }

    override suspend fun removableManualSourceLiterals() = safeStoreCall {
        missingKanji().removableManualSourceLiterals()
    }

    override suspend fun removeUnreviewedManualSources(command: RemoveManualKanjiSourcesCommand) = safeStoreCall {
        missingKanji().removeUnreviewedManualSources(command.literals, command.nowMillis)
    }

    override suspend fun deactivateManualSources(command: DeactivateManualKanjiSourcesCommand) = safeStoreCall {
        missingKanji().deactivateManualSources(command.literals, command.nowMillis)
    }

    override suspend fun recordExportReceipts(receipts: Collection<dev.bee.kanjianki.core.MissingKanjiExportReceipt>) = safeStoreCall {
        missingKanji().recordExportReceipts(receipts)
    }

    override suspend fun exportReceipts(destinationKey: String) = safeStoreCall {
        missingKanji().exportReceipts(destinationKey)
    }
}
