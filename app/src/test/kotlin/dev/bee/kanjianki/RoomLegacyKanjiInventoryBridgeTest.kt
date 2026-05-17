package dev.bee.kanjianki

import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.model.study.StudyKanjiInventoryItem
import dev.bee.kanjianki.domain.repository.StudyKanjiInventoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLegacyKanjiInventoryBridgeTest {
    @Test
    fun searchMapsDomainInventoryToLegacyRecords() = runBlocking {
        val repository = FakeStudyKanjiInventoryRepository(
            searchItems = listOf(
                inventory(
                    kanji = "日",
                    meaning = "sun",
                    readings = "にち",
                    browserSearch = "nid:1",
                    suspended = true,
                ),
            ),
        )
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)

        val items = bridge.search(" sun ", limit = 12)

        assertEquals(" sun ", repository.searchQuery)
        assertEquals(12, repository.searchLimit)
        val item = items.single()
        assertEquals("日", item.kanji)
        assertEquals("sun", item.primaryMeaning)
        assertEquals("にち", item.readings)
        assertEquals("nid:1", item.browserSearch)
        assertEquals(1, item.sourceCount)
        assertEquals(2, item.exampleCount)
        assertTrue(item.suspended)
        assertEquals(200L, item.lastSeenAtMillis)
    }

    @Test
    fun getTrimsInputBeforeRepositoryLookup() = runBlocking {
        val repository = FakeStudyKanjiInventoryRepository(getItem = inventory("語"))
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)

        val item = bridge.get(" 語 ")

        assertEquals("語", repository.getKanji)
        assertEquals("語", item?.kanji)
    }

    @Test
    fun disabledPolicyRejectsBeforeRepositoryAccess() {
        val repository = FakeStudyKanjiInventoryRepository()
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.DISABLED)

        assertFalse(bridge.canReadInventory())
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                bridge.search("", limit = 1)
            }
        }
        assertEquals(0, repository.searchCalls)
    }

    @Test
    fun blockingSearchDelegatesToSuspendSearch() {
        val repository = FakeStudyKanjiInventoryRepository(searchItems = listOf(inventory("日")))
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)

        val items = bridge.searchBlocking(null)

        assertTrue(bridge.canReadInventory())
        assertEquals("", repository.searchQuery)
        assertEquals(RoomLegacyKanjiInventoryBridge.DEFAULT_LIMIT, repository.searchLimit)
        assertEquals("日", items.single().kanji)
    }

    private fun bridge(
        repository: FakeStudyKanjiInventoryRepository,
        policy: RoomStudyRuntimeOwnershipPolicy,
    ): RoomLegacyKanjiInventoryBridge = RoomLegacyKanjiInventoryBridge(
        studyKanjiInventoryRepository = repository,
        ownershipPolicy = policy,
    )

    private fun inventory(
        kanji: String,
        meaning: String = "meaning",
        readings: String = "",
        browserSearch: String = "",
        suspended: Boolean = false,
    ): StudyKanjiInventoryItem = StudyKanjiInventoryItem(
        kanji = kanji,
        primaryMeaning = meaning,
        readings = readings,
        browserSearch = browserSearch,
        sourceCount = 1,
        exampleCount = 2,
        suspended = suspended,
        lastSeenAtMillis = 200L,
    )

    private class FakeStudyKanjiInventoryRepository(
        private val getItem: StudyKanjiInventoryItem? = null,
        private val searchItems: List<StudyKanjiInventoryItem> = emptyList(),
    ) : StudyKanjiInventoryRepository {
        var getKanji = ""
        var getCalls = 0
        var searchQuery = ""
        var searchLimit = -1
        var searchCalls = 0

        override suspend fun get(kanji: String): StudyKanjiInventoryItem? {
            getKanji = kanji
            getCalls++
            return getItem
        }

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<StudyKanjiInventoryItem> {
            searchQuery = query
            searchLimit = limit
            searchCalls++
            return searchItems
        }
    }
}
