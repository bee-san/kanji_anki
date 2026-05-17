package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.study.StudyKanjiInventoryItem

interface StudyKanjiInventoryRepository {
    suspend fun get(kanji: String): StudyKanjiInventoryItem?

    suspend fun search(
        query: String,
        limit: Int,
    ): List<StudyKanjiInventoryItem>
}
