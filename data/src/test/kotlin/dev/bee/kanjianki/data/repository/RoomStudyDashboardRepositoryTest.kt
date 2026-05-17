package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudyDashboardRepositoryTest {
    @Test
    fun localSuspensionWritesFilterActiveRows() = runBlocking {
        val localSuspensions = FakeLocalKanjiSuspensionDao()
        val repository = RoomStudyDashboardRepository(
            dashboardRows = FakeDashboardRowDao(listOf(row("裂"), row("浅"))),
            kanjiExamples = FakeKanjiExampleDao(),
            localSuspensions = localSuspensions,
        )

        assertEquals(listOf("裂", "浅"), repository.listActive(10).map { it.kanji })
        assertFalse(repository.isLocallySuspended("裂"))

        assertTrue(repository.setLocallySuspended(" 裂 ", suspended = true, nowMillis = -5L))

        assertTrue(repository.isLocallySuspended("裂"))
        assertEquals(0L, localSuspensions.entries.getValue("裂").suspendedAt)
        assertEquals(listOf("浅"), repository.listActive(10).map { it.kanji })

        assertFalse(repository.setLocallySuspended("   ", suspended = true, nowMillis = 1L))
        assertTrue(repository.setLocallySuspended("裂", suspended = false, nowMillis = 10L))

        assertFalse(repository.isLocallySuspended("裂"))
        assertEquals(listOf("裂", "浅"), repository.listActive(10).map { it.kanji })
    }

    private class FakeDashboardRowDao(
        private var rows: List<DashboardRowEntity>,
    ) : DashboardRowDao {
        override fun observeTop(limit: Int): Flow<List<DashboardRowEntity>> =
            flowOf(rows.take(limit))

        override suspend fun listTop(limit: Int): List<DashboardRowEntity> =
            rows.take(limit)

        override suspend fun get(kanji: String): DashboardRowEntity? =
            rows.firstOrNull { it.kanji == kanji }

        override suspend fun upsertAll(rows: List<DashboardRowEntity>) {
            val incoming = rows.associateBy { it.kanji }
            this.rows = this.rows.filterNot { incoming.containsKey(it.kanji) } + rows
        }

        override suspend fun deleteAll() {
            rows = emptyList()
        }
    }

    private class FakeKanjiExampleDao(
        private var examples: List<KanjiExampleEntity> = emptyList(),
    ) : KanjiExampleDao {
        override suspend fun listForKanji(
            kanji: String,
            limit: Int,
        ): List<KanjiExampleEntity> = examples.filter { it.kanji == kanji }.take(limit)

        override suspend fun upsertAll(examples: List<KanjiExampleEntity>) {
            this.examples = this.examples + examples
        }

        override suspend fun deleteAll() {
            examples = emptyList()
        }
    }

    private class FakeLocalKanjiSuspensionDao : LocalKanjiSuspensionDao {
        val entries = linkedMapOf<String, LocalKanjiSuspensionEntity>()

        override fun observeAll(): Flow<List<LocalKanjiSuspensionEntity>> =
            flowOf(entries.values.toList())

        override suspend fun listAll(): List<LocalKanjiSuspensionEntity> =
            entries.values.sortedBy { it.kanji }

        override suspend fun get(kanji: String): LocalKanjiSuspensionEntity? =
            entries[kanji]

        override suspend fun upsert(suspension: LocalKanjiSuspensionEntity) {
            entries[suspension.kanji] = suspension
        }

        override suspend fun delete(kanji: String) {
            entries.remove(kanji)
        }
    }

    private fun row(kanji: String): DashboardRowEntity = DashboardRowEntity(
        kanji = kanji,
        jitenRank = 100,
        primaryMeaning = "meaning",
        reading = "reading",
        browserSearch = kanji,
        weaknessScore = 10,
        reasonCode = "reason",
        reasonText = "reason text",
        activeExampleCount = 1,
        suspendedExampleCount = 0,
        matureSupportCount = 0,
        rebuiltAt = 100,
    )
}
