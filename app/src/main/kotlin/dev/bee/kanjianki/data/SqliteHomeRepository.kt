package dev.bee.kanjianki.data

internal class SqliteHomeRepository(
    private val store: LocalStore,
) : HomeRepository {
    override suspend fun loadHome(nowMillis: Long) = safeStoreCall {
        val rows = store.activeDashboardRows()
        HomeSnapshot(
            activeRows = rows.toList(),
            studyItems = if (rows.isEmpty()) {
                emptyList()
            } else {
                store.studyItemsForKanji(rows.map { it.kanji }).toList()
            },
            locallySuspendedKanji = store.locallySuspendedKanji().toSet(),
            latestSync = store.latestSync()?.toRepositorySnapshot(),
            latestSuccessfulSyncAtMillis = store.latestSuccessfulSyncFinishedAt(),
            studyStreak = store.studyStreak(nowMillis).toRepositorySnapshot(),
            dueLegacyWritingRepairs = store.dueSimilarWritingRepairs(nowMillis).toList(),
            repairedHandoffKanji = store.pendingRepairedHandoffKanji().toList(),
            updateCheckFailedAtMillis = store.updateCheckFailedAt(),
        )
    }

    override suspend fun searchInventory(query: String, onlySimilarKanji: Boolean) = safeStoreCall {
        store.searchKanjiInventory(query, onlySimilarKanji).toList()
    }

    override suspend fun loadKanjiDetail(kanji: String, nowMillis: Long) = safeStoreCall {
        HomeKanjiDetailSnapshot(
            kanji = kanji,
            dashboardRow = store.rowForKanji(kanji),
            inventoryItem = store.inventoryItemForKanji(kanji),
            timeline = store.timelineForKanji(kanji),
            mnemonic = store.kanjiMnemonicNote(kanji),
            similarPairs = store.similarPairsForKanji(kanji).toList(),
            wrongPickCounts = store.choiceWrongPickCounts(nowMillis)
                .mapValues { (_, counts) -> counts.toMap() },
            inventory = store.searchKanjiInventory("", false).toList(),
            locallySuspended = store.isKanjiLocallySuspended(kanji),
        )
    }

    override suspend fun saveMnemonic(command: SaveMnemonicCommand) = safeStoreCall {
        store.saveKanjiMnemonicNote(command.kanji, command.note, command.updatedAtMillis)
    }

    override suspend fun setLocalSuspension(command: SetLocalSuspensionCommand) = safeStoreCall {
        store.setKanjiLocallySuspendedForKanji(
            command.kanji.toList(),
            command.suspended,
            command.updatedAtMillis,
        )
    }
}
