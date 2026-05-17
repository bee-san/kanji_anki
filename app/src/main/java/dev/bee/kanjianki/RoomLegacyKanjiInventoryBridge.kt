package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.repository.StudyKanjiInventoryRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class RoomLegacyKanjiInventoryBridge @Inject constructor(
    private val studyKanjiInventoryRepository: StudyKanjiInventoryRepository,
    private val ownershipPolicy: RoomStudyRuntimeOwnershipPolicy,
) {
    fun canReadInventory(): Boolean =
        ownershipPolicy.canReadStudyRuntimeFromRoom()

    suspend fun get(
        kanji: String?,
    ): RecordsImportModels.KanjiInventoryItem? {
        requireCanRead()
        val normalizedKanji = kanji?.trim().orEmpty()
        if (normalizedKanji.isEmpty()) {
            return null
        }
        return studyKanjiInventoryRepository.get(normalizedKanji)?.let { item ->
            LegacyStudyMappers.toLegacy(item)
        }
    }

    suspend fun search(
        query: String?,
        limit: Int = DEFAULT_LIMIT,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        requireCanRead()
        return LegacyStudyMappers.toLegacyInventoryItems(
            studyKanjiInventoryRepository.search(query.orEmpty(), limit.coerceAtLeast(0)),
        )
    }

    fun getBlocking(
        kanji: String?,
    ): RecordsImportModels.KanjiInventoryItem? = runBlocking {
        get(kanji)
    }

    @JvmOverloads
    fun searchBlocking(
        query: String?,
        limit: Int = DEFAULT_LIMIT,
    ): List<RecordsImportModels.KanjiInventoryItem> = runBlocking {
        search(query, limit)
    }

    private fun requireCanRead() {
        check(canReadInventory()) {
            "Room inventory reads require completed legacy reset/migration or active double-write ownership."
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 300
    }
}
