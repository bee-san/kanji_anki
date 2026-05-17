package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomStudyLocalSuspensionRepositoryTest {
    @Test
    fun listSuspendedKanjiReadsOrderedRoomSuspensionSet() = runBlocking {
        val repository = RoomStudyLocalSuspensionRepository(
            FakeLocalKanjiSuspensionDao(
                LocalKanjiSuspensionEntity("火", 20L),
                LocalKanjiSuspensionEntity("日", 10L),
                LocalKanjiSuspensionEntity("火", 30L),
            ),
        )

        assertEquals(setOf("火", "日"), repository.listSuspendedKanji())
    }

    @Test
    fun observeSuspendedKanjiMapsDaoEntitiesToSet() = runBlocking {
        val dao = FakeLocalKanjiSuspensionDao(LocalKanjiSuspensionEntity("水", 10L))
        val repository = RoomStudyLocalSuspensionRepository(dao)

        assertEquals(setOf("水"), repository.observeSuspendedKanji().first())

        dao.replace(LocalKanjiSuspensionEntity("木", 20L), LocalKanjiSuspensionEntity("金", 30L))

        assertEquals(setOf("木", "金"), repository.observeSuspendedKanji().first())
    }

    private class FakeLocalKanjiSuspensionDao(
        private vararg val initial: LocalKanjiSuspensionEntity,
    ) : LocalKanjiSuspensionDao {
        private val entries = MutableStateFlow(initial.toList())

        override fun observeAll(): Flow<List<LocalKanjiSuspensionEntity>> = entries

        override suspend fun listAll(): List<LocalKanjiSuspensionEntity> = entries.value

        override suspend fun get(kanji: String): LocalKanjiSuspensionEntity? =
            entries.value.firstOrNull { it.kanji == kanji }

        override suspend fun upsert(suspension: LocalKanjiSuspensionEntity) {
            entries.value = entries.value.filterNot { it.kanji == suspension.kanji } + suspension
        }

        override suspend fun delete(kanji: String) {
            entries.value = entries.value.filterNot { it.kanji == kanji }
        }

        fun replace(vararg suspensions: LocalKanjiSuspensionEntity) {
            entries.value = suspensions.toList()
        }
    }
}
