package dev.bee.kanjianki.application

import dev.bee.kanjianki.data.ReviewTokenQuery
import dev.bee.kanjianki.data.ReviewTokenStatus
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.fakes.FakeStudyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudyUseCasesTest {
    @Test
    fun delegatesNarrowReadsAndQueueWrites() = runTest {
        val repository = FakeStudyRepository().apply {
            loadAllItemsHandler = { StoreResult.ok(emptyList()) }
            loadItemsHandler = { StoreResult.ok(emptyList()) }
            queueVersionHandler = { StoreResult.ok(42L) }
            tokenHandler = { StoreResult.ok(ReviewTokenStatus(true, true)) }
        }
        val useCases = StudyUseCases(repository)
        val query = ReviewTokenQuery("token", "痛", "kanji_meaning", "signature")

        assertTrue(useCases.loadAllItems().isEmpty())
        assertTrue(useCases.loadItems(listOf("痛")).isEmpty())
        assertEquals(42L, useCases.loadQueueVersion())
        assertEquals(ReviewTokenStatus(true, true), useCases.reviewTokenStatus(query))

        useCases.replaceQueue(emptyList(), emptyList())
        assertEquals(1, repository.queueWrites.size)
        assertEquals(emptyList<Any>(), repository.queueWrites.single().items)
    }

    @Test
    fun mapsRepositoryFailuresAtTheApplicationBoundary() = runTest {
        val repository = FakeStudyRepository().apply {
            loadAllItemsHandler = {
                StoreResult.transient(IllegalStateException("database busy"))
            }
        }

        try {
            StudyUseCases(repository).loadAllItems()
            fail("expected repository failure")
        } catch (error: RepositoryOperationException) {
            assertEquals("load all study items", error.operation)
            assertEquals(RepositoryFailureKind.TRANSIENT, error.kind)
        }
    }
}
