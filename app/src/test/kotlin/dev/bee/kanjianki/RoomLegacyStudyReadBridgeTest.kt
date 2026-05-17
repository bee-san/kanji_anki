package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemory
import dev.bee.kanjianki.domain.model.study.TaskMemoryBank
import dev.bee.kanjianki.domain.repository.StudyRuntimeSnapshot
import dev.bee.kanjianki.domain.repository.StudyRuntimeSnapshotRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLegacyStudyReadBridgeTest {
    @Test
    fun activeSnapshotMapsRoomRowsAndItemsToLegacyRuntimeRecords() = runBlocking {
        val bridge = RoomLegacyStudyReadBridge(
            studyRuntimeSnapshotRepository = FakeStudyRuntimeSnapshotRepository(
                rows = listOf(
                    StudyDashboardRow(
                        kanji = "日",
                        jitenRank = 42,
                        primaryMeaning = "sun",
                        reading = "にち",
                        browserSearch = "nid:1",
                        weaknessScore = 88,
                        reasonCode = "suspended",
                        reasonText = "Suspended support",
                        activeExampleCount = 1,
                        suspendedExampleCount = 1,
                        matureSupportCount = 1,
                        examples = listOf(
                            StudyExample(
                                sourceType = "suspended",
                                expression = "日本",
                                reading = "にほん",
                                meaning = "Japan",
                                fsrsDifficulty = 7.5,
                                fsrsRetrievability = 0.41,
                                mature = true,
                                lapses = 2,
                                intervalDays = 21,
                                reps = 9,
                                fsrsStability = 13.0,
                                cardId = 10L,
                                noteId = 20L,
                                sentence = "日本へ行く。",
                            ),
                        ),
                    ),
                ),
                items = listOf(
                    StudyQueueItem(
                        kanji = "日",
                        state = StudyItemState.REVIEW,
                        dueAtMillis = 1_000L,
                        stability = 4.0,
                        difficulty = 6.0,
                        totalReviews = 3,
                        lapses = 1,
                        learningStep = 2,
                        writingLevel = 1,
                        matureIntervalDays = 12,
                        answerSignature = "日|日本|にほん|Japan",
                        rung = StudyRung.WRITE_KANJI,
                        phase = StudyPhase.RELEARNING,
                        realPassStreak = 4,
                        realAgainStreak = 2,
                        lastRealReviewDueAtMillis = 900L,
                        suppressedByTaskType = "font_meaning",
                        hasSimilarKanji = true,
                        activeToken = "tok",
                        memories = TaskMemoryBank(
                            writingRemediationMemory = TaskMemory.from(
                                state = "review",
                                dueAtMillis = 1_000L,
                                stability = 4.0,
                                difficulty = 6.0,
                                totalReviews = 3,
                                lapses = 1,
                                learningStep = 2,
                                lastRating = "again",
                                matureIntervalDays = 12,
                                consecutivePasses = 5,
                                lastPassedDueAtMillis = 777L,
                            ),
                        ),
                        createdAtMillis = 100L,
                        suppressedAtMillis = 800L,
                    ),
                ),
            ),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE,
        )

        val snapshot = bridge.activeSnapshot(dashboardLimit = 1)

        assertEquals(1, snapshot.rows.size)
        val row = snapshot.rows.single()
        assertEquals("日", row.kanji)
        assertEquals(42, row.jitenRank)
        assertEquals("sun", row.primaryMeaning)
        assertEquals(88, row.weaknessScore)
        assertEquals(1, row.activeExampleCount)
        assertEquals(1, row.suspendedExampleCount)
        assertEquals(1, row.matureSupportCount)
        val example = row.examples.single()
        assertEquals("suspended", example.sourceType)
        assertEquals(10L, example.cardId)
        assertEquals(20L, example.noteId)
        assertEquals("日本", example.expression)
        assertEquals("日本へ行く。", example.sentence)
        assertTrue(example.mature)
        assertEquals(13.0, example.fsrsStability!!, 0.0)
        assertEquals(7.5, example.fsrsDifficulty!!, 0.0)
        assertEquals(0.41, example.fsrsRetrievability!!, 0.0)

        assertEquals(1, snapshot.items.size)
        val item = snapshot.items.single()
        assertEquals("日", item.kanji)
        assertEquals("review", item.state)
        assertEquals(1_000L, item.dueAtMillis)
        assertEquals(4.0, item.stability, 0.0)
        assertEquals(6.0, item.difficulty, 0.0)
        assertEquals(3, item.totalReviews)
        assertEquals(1, item.lapses)
        assertEquals(2, item.learningStep)
        assertEquals(1, item.writingLevel)
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, item.rung)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, item.phase)
        assertTrue(item.writingRemediationPending)
        assertEquals(2, item.consecutiveFailedRecognitionDays)
        assertEquals(900L, item.lastFailedRecognitionDayMillis)
        assertEquals("font_meaning", item.suppressedByTaskType)
        assertEquals(800L, item.suppressedAtMillis)
        assertEquals(12, item.matureIntervalDays)
        assertEquals("tok", item.activeToken)
        assertTrue(item.hasSimilarKanji)
        assertEquals(4, item.realPassStreak)
        assertEquals(2, item.realAgainStreak)
        assertEquals(900L, item.lastRealReviewDueAtMillis)
        assertEquals("again", item.writingRemediationMemory.lastRating)
        assertEquals(5, item.writingRemediationMemory.consecutivePasses)
        assertEquals(777L, item.writingRemediationMemory.lastPassedDueAtMillis)
    }

    @Test
    fun activeSnapshotRequiresRoomRuntimeOwnership() {
        val bridge = RoomLegacyStudyReadBridge(
            studyRuntimeSnapshotRepository = FakeStudyRuntimeSnapshotRepository(
                rows = emptyList(),
                items = emptyList(),
            ),
            ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.DISABLED,
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                bridge.activeSnapshot(dashboardLimit = 1)
            }
        }
    }

    private class FakeStudyRuntimeSnapshotRepository(
        private val rows: List<StudyDashboardRow>,
        private val items: List<StudyQueueItem>,
    ) : StudyRuntimeSnapshotRepository {
        override suspend fun activeSnapshot(dashboardLimit: Int): StudyRuntimeSnapshot =
            StudyRuntimeSnapshot(rows.take(dashboardLimit), items)
    }
}
