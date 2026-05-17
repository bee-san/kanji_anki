package dev.bee.kanjianki

import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyKanjiRecoveryTimeline
import dev.bee.kanjianki.domain.model.study.StudyKanjiTimelineEvent
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyKanjiInventoryItem
import dev.bee.kanjianki.domain.repository.StudyKanjiDetailRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLegacyKanjiTimelineBridgeTest {
    @Test
    fun timelineForKanjiMapsDomainDetailToLegacyTimeline() = runBlocking {
        val repository = FakeStudyKanjiDetailRepository(detail())
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)

        val timeline = bridge.timelineForKanji(" 日 ", eventLimit = 9)

        assertEquals("日", repository.lastKanji)
        assertEquals(9, repository.lastLimit)
        assertEquals("日", timeline.inventoryItem.kanji)
        assertTrue(timeline.inventoryItem.suspended)
        assertEquals("sun", timeline.currentRow.primaryMeaning)
        assertEquals("review", timeline.currentStudyItem.state)
        assertTrue(timeline.currentStudyItem.hasSimilarKanji)
        assertEquals(1L, timeline.events.single().id)
        assertEquals("Reviewed", timeline.events.single().title)
        assertTrue(timeline.events.single().writingRequired)
    }

    @Test
    fun blankKanjiReturnsEmptyTimelineWithoutRepositoryLookup() {
        val repository = FakeStudyKanjiDetailRepository(detail())
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE)

        val timeline = bridge.timelineForKanjiBlocking(" ")

        assertEquals("", repository.lastKanji)
        assertNull(timeline.inventoryItem)
        assertNull(timeline.currentRow)
        assertNull(timeline.currentStudyItem)
        assertTrue(timeline.events.isEmpty())
    }

    @Test
    fun disabledPolicyRejectsBeforeRepositoryAccess() {
        val repository = FakeStudyKanjiDetailRepository(detail())
        val bridge = bridge(repository, RoomStudyRuntimeOwnershipPolicy.DISABLED)

        assertFalse(bridge.canReadTimeline())
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                bridge.timelineForKanji("日")
            }
        }
        assertEquals("", repository.lastKanji)
    }

    private fun bridge(
        repository: FakeStudyKanjiDetailRepository,
        policy: RoomStudyRuntimeOwnershipPolicy,
    ): RoomLegacyKanjiTimelineBridge = RoomLegacyKanjiTimelineBridge(
        studyKanjiDetailRepository = repository,
        ownershipPolicy = policy,
    )

    private fun detail(): StudyKanjiRecoveryTimeline = StudyKanjiRecoveryTimeline(
        inventoryItem = StudyKanjiInventoryItem(
            kanji = "日",
            primaryMeaning = "sun",
            readings = "にち",
            browserSearch = "nid:1",
            sourceCount = 1,
            exampleCount = 1,
            suspended = true,
            lastSeenAtMillis = 200L,
        ),
        currentRow = StudyDashboardRow(
            kanji = "日",
            jitenRank = 42,
            primaryMeaning = "sun",
            reading = "にち",
            browserSearch = "nid:1",
            weaknessScore = 88,
            reasonCode = "suspended",
            reasonText = "Needs practice",
            activeExampleCount = 1,
            suspendedExampleCount = 1,
            matureSupportCount = 0,
        ),
        currentStudyItem = StudyQueueItem(
            kanji = "日",
            state = StudyItemState.REVIEW,
            dueAtMillis = 1_000L,
            stability = 4.0,
            difficulty = 6.0,
            totalReviews = 3,
            lapses = 1,
            learningStep = 0,
            writingLevel = 0,
            hasSimilarKanji = true,
        ),
        events = listOf(
            StudyKanjiTimelineEvent(
                id = 1L,
                kanji = "日",
                occurredAtMillis = 100L,
                eventType = "review",
                title = "Reviewed",
                detail = "Saved.",
                sourceExpression = "日本",
                sourceReading = "にほん",
                rating = "good",
                writingRequired = true,
                writingPassed = true,
                manualOverride = false,
                weaknessScore = 88,
                matureSupportCount = 1,
                syncId = 7L,
                dedupeKey = "review:1",
            ),
        ),
    )

    private class FakeStudyKanjiDetailRepository(
        private val detail: StudyKanjiRecoveryTimeline,
    ) : StudyKanjiDetailRepository {
        var lastKanji = ""
        var lastLimit = -1

        override suspend fun timelineForKanji(
            kanji: String,
            eventLimit: Int,
        ): StudyKanjiRecoveryTimeline {
            lastKanji = kanji
            lastLimit = eventLimit
            return detail
        }
    }
}
