package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.inventory.KanjiInventoryDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.domain.model.study.StudyKanjiInventoryItem
import dev.bee.kanjianki.domain.repository.StudyKanjiInventoryRepository
import java.text.Normalizer
import java.util.Locale

class RoomStudyKanjiInventoryRepository internal constructor(
    private val kanjiInventory: KanjiInventoryDao,
    private val localSuspensions: LocalKanjiSuspensionDao,
    private val runInTransaction: suspend (suspend () -> List<StudyKanjiInventoryItem>) -> List<StudyKanjiInventoryItem>,
    private val runSingleInTransaction: suspend (suspend () -> StudyKanjiInventoryItem?) -> StudyKanjiInventoryItem?,
) : StudyKanjiInventoryRepository {
    constructor(database: KaniRoomDatabase) : this(
        kanjiInventory = database.kanjiInventoryDao(),
        localSuspensions = database.localKanjiSuspensionDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
        runSingleInTransaction = { block -> database.withTransaction { block() } },
    )

    override suspend fun get(kanji: String): StudyKanjiInventoryItem? {
        val normalizedKanji = kanji.trim()
        if (normalizedKanji.isEmpty()) {
            return null
        }
        return runSingleInTransaction {
            val entity = kanjiInventory.get(normalizedKanji) ?: return@runSingleInTransaction null
            entity.toDomain(suspended = localSuspensions.get(normalizedKanji) != null)
        }
    }

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<StudyKanjiInventoryItem> {
        val boundedLimit = limit.coerceAtLeast(0)
        if (boundedLimit == 0) {
            return emptyList()
        }
        val normalizedQuery = normalizedSearchQuery(query)
        return runInTransaction {
            val suspendedKanji = localSuspensions.listAll().mapTo(mutableSetOf()) { it.kanji }
            val rows = if (normalizedQuery.isEmpty()) {
                kanjiInventory.listLimited(boundedLimit)
            } else {
                kanjiInventory.search(normalizedQuery, boundedLimit)
            }
            rows.map { row -> row.toDomain(suspended = row.kanji in suspendedKanji) }
        }
    }

    private fun normalizedSearchQuery(query: String): String =
        Normalizer.normalize(query, Normalizer.Form.NFKC)
            .replace('\u3000', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
}
