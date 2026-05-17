package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.repository.StudyReviewPersistenceInput
import dev.bee.kanjianki.domain.repository.StudyReviewPersistenceRepository
import dev.bee.kanjianki.domain.scheduler.ApplyStudyReviewUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudyReviewBridgeTest {
    @Test
    fun applyReviewPersistsThroughRoomUseCaseAndConsumesTokenAfterPersistence() = runBlocking {
        val repository = FakeStudyReviewPersistenceRepository(saveResult = true)
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)
        val consumed = linkedSetOf<String>()

        val result = bridge.applyReview(
            item = reviewItem(token = "tok"),
            request = reviewRequest(token = "tok", rating = "good"),
            consumedTokens = consumed,
            nowMillis = 1_000L,
            parameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
            learningSettings = RecordsSchedulerModels.LearningStepSettings.defaults(),
            ladder = RecordsBase.StudyLadderSettings.defaults(),
        )

        assertTrue(result.persisted)
        assertFalse(result.reviewResult.duplicate)
        assertEquals("good", result.reviewResult.appliedRating)
        assertEquals(setOf("tok"), consumed)
        val saved = repository.saved.single()
        assertEquals("裂", saved.before.kanji)
        assertEquals("裂", saved.after.kanji)
        assertEquals("tok", saved.request.token)
        assertEquals("good", saved.appliedRating.wireName)
    }

    @Test
    fun duplicateTokenDoesNotPersistOrMutateConsumedTokens() = runBlocking {
        val repository = FakeStudyReviewPersistenceRepository(saveResult = true)
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)
        val consumed = linkedSetOf("tok")

        val result = bridge.applyReview(
            item = reviewItem(token = "tok"),
            request = reviewRequest(token = "tok", rating = "good"),
            consumedTokens = consumed,
            nowMillis = 1_000L,
            parameters = null,
            settings = null,
            learningSettings = null,
            ladder = null,
        )

        assertFalse(result.persisted)
        assertTrue(result.reviewResult.duplicate)
        assertEquals(setOf("tok"), consumed)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun disabledOwnershipRejectsRoomReviewWriteBeforePersistence() {
        val repository = FakeStudyReviewPersistenceRepository(saveResult = true)
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.DISABLED)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                bridge.applyReview(
                    item = reviewItem(token = "tok"),
                    request = reviewRequest(token = "tok", rating = "good"),
                    consumedTokens = linkedSetOf(),
                    nowMillis = 1_000L,
                    parameters = null,
                    settings = null,
                    learningSettings = null,
                    ladder = null,
                )
            }
        }
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun failedPersistenceDoesNotConsumeToken() = runBlocking {
        val repository = FakeStudyReviewPersistenceRepository(saveResult = false)
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)
        val consumed = linkedSetOf<String>()
        val item = reviewItem(token = "tok")

        val result = bridge.applyReview(
            item = item,
            request = reviewRequest(token = "tok", rating = "good"),
            consumedTokens = consumed,
            nowMillis = 1_000L,
            parameters = null,
            settings = null,
            learningSettings = null,
            ladder = null,
        )

        assertFalse(result.persisted)
        assertTrue(result.reviewResult.duplicate)
        assertEquals("not_persisted", result.reviewResult.appliedRating)
        assertEquals(item, result.reviewResult.item)
        assertEquals(emptySet<String>(), consumed)
        assertEquals(1, repository.saved.size)
    }

    private fun bridge(
        repository: StudyReviewPersistenceRepository,
        policy: RoomStudyRuntimeOwnershipPolicy,
    ): RoomStudyReviewBridge =
        RoomStudyReviewBridge(
            applyStudyReviewUseCase = ApplyStudyReviewUseCase(repository),
            ownershipPolicy = policy,
        )

    private fun reviewRequest(
        token: String,
        rating: String,
    ): RecordsSchedulerModels.ReviewRequest =
        RecordsSchedulerModels.ReviewRequest(
            "裂",
            token,
            rating,
            false,
            false,
            false,
            0,
        )

    private fun reviewItem(token: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            "裂",
            "review",
            0L,
            5.0,
            6.0,
            1,
            0,
            0,
            0,
            0,
            0,
            0L,
            false,
            "",
            0L,
            7,
            "裂|meaning",
            token,
            10L,
        )

    private class FakeStudyReviewPersistenceRepository(
        private val saveResult: Boolean,
    ) : StudyReviewPersistenceRepository {
        val saved = mutableListOf<StudyReviewPersistenceInput>()

        override suspend fun saveAppliedReview(input: StudyReviewPersistenceInput): Boolean {
            saved += input
            return saveResult
        }
    }
}
