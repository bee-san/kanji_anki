package dev.bee.kanjianki

import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLocalKanjiSuspensionBridgeTest {
    @Test
    fun enabledOwnershipDelegatesNormalizedLocalSuspensionWrite() = runBlocking {
        val repository = FakeStudyDashboardRepository(writeResult = true)
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)

        val result = bridge.setLocallySuspended(" 裂 ", suspended = true, nowMillis = -5L)

        assertTrue(result)
        assertEquals(listOf(LocalSuspensionWrite("裂", true, -5L)), repository.writes)
    }

    @Test
    fun blankKanjiReturnsFalseWithoutRepositoryWrite() = runBlocking {
        val repository = FakeStudyDashboardRepository(writeResult = true)
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)

        val result = bridge.setLocallySuspended("   ", suspended = true, nowMillis = 10L)

        assertFalse(result)
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun disabledOwnershipRejectsValidWriteBeforeRepositoryMutation() {
        val repository = FakeStudyDashboardRepository(writeResult = true)
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.DISABLED)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                bridge.setLocallySuspended("裂", suspended = false, nowMillis = 10L)
            }
        }
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun blockingEntryPointReturnsRepositoryResult() {
        val repository = FakeStudyDashboardRepository(writeResult = false)
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)

        val result = bridge.setLocallySuspendedBlocking("浅", suspended = false, nowMillis = 20L)

        assertFalse(result)
        assertEquals(listOf(LocalSuspensionWrite("浅", false, 20L)), repository.writes)
    }

    private fun bridge(
        repository: StudyDashboardRepository,
        policy: RoomStudyRuntimeOwnershipPolicy,
    ): RoomLocalKanjiSuspensionBridge =
        RoomLocalKanjiSuspensionBridge(repository, policy)

    private data class LocalSuspensionWrite(
        val kanji: String,
        val suspended: Boolean,
        val nowMillis: Long,
    )

    private class FakeStudyDashboardRepository(
        private val writeResult: Boolean,
    ) : StudyDashboardRepository {
        val writes = mutableListOf<LocalSuspensionWrite>()

        override fun observeTop(limit: Int): Flow<List<StudyDashboardRow>> = emptyFlow()

        override fun observeActive(limit: Int): Flow<List<StudyDashboardRow>> = emptyFlow()

        override suspend fun listTop(limit: Int): List<StudyDashboardRow> = emptyList()

        override suspend fun listActive(limit: Int): List<StudyDashboardRow> = emptyList()

        override suspend fun get(kanji: String): StudyDashboardRow? = null

        override suspend fun isLocallySuspended(kanji: String): Boolean = false

        override suspend fun setLocallySuspended(
            kanji: String,
            suspended: Boolean,
            nowMillis: Long,
        ): Boolean {
            writes += LocalSuspensionWrite(kanji, suspended, nowMillis)
            return writeResult
        }
    }
}
