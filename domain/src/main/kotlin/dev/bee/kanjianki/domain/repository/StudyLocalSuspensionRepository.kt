package dev.bee.kanjianki.domain.repository

import kotlinx.coroutines.flow.Flow

interface StudyLocalSuspensionRepository {
    fun observeSuspendedKanji(): Flow<Set<String>>

    suspend fun listSuspendedKanji(): Set<String>
}
