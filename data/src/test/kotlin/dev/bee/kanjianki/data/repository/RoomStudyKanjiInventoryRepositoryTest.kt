package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.inventory.KanjiInventoryDao
import dev.bee.kanjianki.data.inventory.KanjiInventoryEntity
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudyKanjiInventoryRepositoryTest {
    @Test
    fun blankSearchListsInventoryWithLocalSuspensionState() = runBlocking {
        val kanjiInventory = FakeKanjiInventoryDao(
            inventory("日", meaning = "sun"),
            inventory("語", meaning = "language"),
        )
        val localSuspensions = FakeLocalKanjiSuspensionDao("語")
        val repository = repository(kanjiInventory, localSuspensions)

        val items = repository.search("", limit = 10)

        assertEquals(listOf("日", "語"), items.map { it.kanji })
        assertFalse(items[0].suspended)
        assertTrue(items[1].suspended)
        assertEquals(1, kanjiInventory.listLimitedCalls)
        assertEquals(0, kanjiInventory.searchCalls)
    }

    @Test
    fun searchNormalizesQueryAndMapsResults() = runBlocking {
        val kanjiInventory = FakeKanjiInventoryDao(
            inventory("日", searchText = "日 sun にち"),
            inventory("語", searchText = "語 language ご"),
        )
        val repository = repository(kanjiInventory, FakeLocalKanjiSuspensionDao())

        val items = repository.search("　ＳＵＮ　", limit = 10)

        assertEquals("sun", kanjiInventory.lastSearchQuery)
        assertEquals(listOf("日"), items.map { it.kanji })
        assertEquals(0, kanjiInventory.listLimitedCalls)
        assertEquals(1, kanjiInventory.searchCalls)
    }

    @Test
    fun getTrimsKanjiAndReturnsSuspensionState() = runBlocking {
        val kanjiInventory = FakeKanjiInventoryDao(
            inventory("日", meaning = "sun", readings = "にち", browserSearch = "nid:1"),
        )
        val repository = repository(kanjiInventory, FakeLocalKanjiSuspensionDao("日"))

        val item = repository.get(" 日 ")

        assertEquals("日", item?.kanji)
        assertEquals("sun", item?.primaryMeaning)
        assertEquals("にち", item?.readings)
        assertEquals("nid:1", item?.browserSearch)
        assertTrue(item?.suspended == true)
    }

    @Test
    fun blankGetAndZeroLimitDoNotTouchDao() = runBlocking {
        val kanjiInventory = FakeKanjiInventoryDao(inventory("日"))
        val repository = repository(kanjiInventory, FakeLocalKanjiSuspensionDao())

        assertNull(repository.get(" "))
        assertTrue(repository.search("日", limit = 0).isEmpty())
        assertEquals(0, kanjiInventory.getCalls)
        assertEquals(0, kanjiInventory.searchCalls)
        assertEquals(0, kanjiInventory.listLimitedCalls)
    }

    private fun repository(
        kanjiInventory: FakeKanjiInventoryDao,
        localSuspensions: FakeLocalKanjiSuspensionDao,
    ): RoomStudyKanjiInventoryRepository = RoomStudyKanjiInventoryRepository(
        kanjiInventory = kanjiInventory,
        localSuspensions = localSuspensions,
        runInTransaction = { block -> block() },
        runSingleInTransaction = { block -> block() },
    )

    private fun inventory(
        kanji: String,
        meaning: String = "",
        readings: String = "",
        browserSearch: String = "",
        searchText: String = kanji.lowercase(),
    ): KanjiInventoryEntity = KanjiInventoryEntity(
        kanji = kanji,
        primaryMeaning = meaning,
        readings = readings,
        browserSearch = browserSearch,
        searchText = searchText,
        sourceCount = 1,
        exampleCount = 2,
        firstSeenAt = 100L,
        lastSeenAt = 200L,
    )

    private class FakeKanjiInventoryDao(
        vararg rows: KanjiInventoryEntity,
    ) : KanjiInventoryDao {
        private val inventory = rows.associateBy { it.kanji }.toMutableMap()
        var getCalls = 0
        var listLimitedCalls = 0
        var searchCalls = 0
        var lastSearchQuery = ""

        override fun observeAll(): Flow<List<KanjiInventoryEntity>> = emptyFlow()

        override suspend fun get(kanji: String): KanjiInventoryEntity? {
            getCalls++
            return inventory[kanji]
        }

        override suspend fun listAll(): List<KanjiInventoryEntity> =
            inventory.values.sortedBy { it.kanji }

        override suspend fun listLimited(limit: Int): List<KanjiInventoryEntity> {
            listLimitedCalls++
            return listAll().take(limit)
        }

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<KanjiInventoryEntity> {
            searchCalls++
            lastSearchQuery = query
            return listAll()
                .filter { it.searchText.contains(query) }
                .take(limit)
        }

        override suspend fun upsertAll(items: List<KanjiInventoryEntity>) {
            for (item in items) {
                inventory[item.kanji] = item
            }
        }
    }

    private class FakeLocalKanjiSuspensionDao(
        vararg kanji: String,
    ) : LocalKanjiSuspensionDao {
        private val entries = kanji.associateWith { LocalKanjiSuspensionEntity(it, suspendedAt = 100L) }
            .toMutableMap()

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
}
