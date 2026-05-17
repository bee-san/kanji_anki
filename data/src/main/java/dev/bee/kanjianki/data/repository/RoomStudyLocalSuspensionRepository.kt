package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionEntity
import dev.bee.kanjianki.domain.repository.StudyLocalSuspensionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStudyLocalSuspensionRepository internal constructor(
    private val localSuspensions: LocalKanjiSuspensionDao,
) : StudyLocalSuspensionRepository {
    constructor(database: KaniRoomDatabase) : this(
        localSuspensions = database.localKanjiSuspensionDao(),
    )

    override fun observeSuspendedKanji(): Flow<Set<String>> =
        localSuspensions.observeAll().map { suspensions ->
            suspensions.toKanjiSet()
        }

    override suspend fun listSuspendedKanji(): Set<String> =
        localSuspensions.listAll().toKanjiSet()

    private fun List<LocalKanjiSuspensionEntity>.toKanjiSet(): Set<String> =
        mapTo(linkedSetOf()) { it.kanji }
}
