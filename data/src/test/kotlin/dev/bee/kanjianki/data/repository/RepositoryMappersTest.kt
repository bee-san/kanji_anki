package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.data.source.SourceCardEntity
import dev.bee.kanjianki.data.source.SourceNoteEntity
import dev.bee.kanjianki.data.study.StudyItemEntity
import dev.bee.kanjianki.data.sync.SyncRunEntity
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemory
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryMappersTest {
    @Test
    fun sourceNoteRoundTripsThroughEntity() {
        val note = SourceNote(
            noteId = NoteId(10),
            modelName = "Kiku",
            expression = "日本",
            reading = "にほん",
            meaning = "Japan",
            sentence = "日本へ行く",
            fieldsJson = """{"Expression":"日本"}""",
            tags = "kiku",
            lastSeenSyncId = SyncRunId(5),
        )

        assertEquals(note, note.toEntity().toDomain())
    }

    @Test
    fun sourceCardRoundTripsThroughEntity() {
        val card = SourceCard(
            cardId = CardId(11),
            noteId = NoteId(10),
            deckName = "Mining",
            ord = 0,
            queue = -1,
            type = 2,
            due = 30,
            intervalDays = 21,
            reps = 7,
            lapses = 1,
            suspended = true,
            browserQueryMatched = true,
            fsrsStability = 12.5,
            fsrsDifficulty = 6.5,
            fsrsRetrievability = null,
            lastSeenSyncId = SyncRunId(5),
        )

        assertEquals(card, card.toEntity().toDomain())
    }

    @Test
    fun syncRunMapsStatusWireName() {
        val syncRun = SyncRun(
            id = SyncRunId(3),
            startedAt = 100,
            finishedAt = 200,
            status = SyncRunStatus.SUCCESS,
            activeNotesCount = 1,
            activeCardsCount = 2,
            suspendedCardsArchivedCount = 3,
            suspendedKanjiImportedCount = 4,
            deletedNotesCount = 5,
            deletedCardsCount = 6,
            errorCode = null,
            errorMessage = null,
            removalMessage = "done",
        )

        assertEquals(syncRun, syncRun.toEntity().toDomain())
        assertEquals("success", syncRun.toEntity().status)
    }

    @Test
    fun rawEntitiesMapToDomainIds() {
        assertEquals(NoteId(1), SourceNoteEntity(1, "Kiku", "", "", "", "", "{}", "", 9).toDomain().noteId)
        assertEquals(CardId(2), SourceCardEntity(2, 1, "", 0, 0, 0, 0, 0, 0, 0, false, false, null, null, null, 9).toDomain().cardId)
        assertEquals(SyncRunId(4), SyncRunEntity(4, 1, null, "success", 0, 0, 0, 0, 0, 0, null, null, null).toDomain().id)
    }

    @Test
    fun studyItemEntityMapsToQueueDomainModel() {
        val typingMemory = TaskMemory.from(
            state = "review",
            dueAtMillis = 12,
            stability = 2.0,
            difficulty = 4.0,
            totalReviews = 3,
            lapses = 1,
            learningStep = 0,
            lastRating = "good",
            matureIntervalDays = 5,
        )
        val entity = studyItemEntity(typingMeaningMemory = typingMemory.encode())

        val item = entity.toDomain(hasSimilarKanji = true)

        assertEquals("裂", item.kanji)
        assertEquals(StudyItemState.REVIEW, item.state)
        assertEquals(StudyRung.FONT_MEANING, item.rung)
        assertEquals(StudyPhase.REVIEW, item.phase)
        assertEquals(21, item.matureIntervalDays)
        assertEquals(2, item.realPassStreak)
        assertEquals(0, item.realAgainStreak)
        assertEquals(900, item.lastRealReviewDueAtMillis)
        assertEquals(10, item.createdAtMillis)
        assertEquals("裂|sig", item.answerSignature)
        assertEquals("font_meaning", item.suppressedByTaskType)
        assertEquals(50, item.suppressedAtMillis)
        assertEquals("active", item.activeToken)
        assertEquals(true, item.hasSimilarKanji)
        assertEquals(typingMemory, item.memories.typingMeaningMemory)
        assertEquals(TaskMemory.initial(), item.memories.wordReadingMemory)
    }

    @Test
    fun reviewUpdateMapsDomainFieldsAndPreservesEntityOnlyColumns() {
        val current = studyItemEntity()
        val reviewedMemory = TaskMemory.from(
            state = "learning",
            dueAtMillis = 2000,
            stability = 1.23,
            difficulty = 5.68,
            totalReviews = 8,
            lapses = 2,
            learningStep = 1,
            lastRating = "again",
            matureIntervalDays = 0,
        )
        val item = current.toDomain().copy(
            state = StudyItemState.LEARNING,
            dueAtMillis = 2000,
            stability = 1.23,
            difficulty = 5.68,
            totalReviews = 8,
            lapses = 2,
            learningStep = 1,
            writingLevel = 3,
            matureIntervalDays = 0,
            rung = StudyRung.WRITE_KANJI,
            phase = StudyPhase.RELEARNING,
            realPassStreak = 0,
            realAgainStreak = 2,
            lastRealReviewDueAtMillis = 1500,
            activeToken = null,
            memories = current.toDomain().memories.copy(
                kanjiMeaningMemory = reviewedMemory,
                writingRemediationMemory = reviewedMemory,
            ),
        )

        val updated = current.withReviewUpdate(item)

        assertEquals("learning", updated.state)
        assertEquals(2000, updated.dueAt)
        assertEquals(1.23, updated.stability, 0.0)
        assertEquals(5.68, updated.difficulty, 0.0)
        assertEquals(8, updated.totalReviews)
        assertEquals(2, updated.lapses)
        assertEquals(1, updated.learningStep)
        assertEquals(3, updated.writingLevel)
        assertEquals(0, updated.recognitionStage)
        assertEquals(2, updated.consecutiveFailedRecognitionDays)
        assertEquals(1500, updated.lastFailedRecognitionDay)
        assertEquals(1, updated.writingRemediationPending)
        assertEquals(0, updated.matureIntervalDays)
        assertEquals("write_kanji", updated.rung)
        assertEquals("relearning", updated.phase)
        assertEquals(0, updated.realPassStreak)
        assertEquals(2, updated.realAgainStreak)
        assertEquals(1500, updated.lastRealReviewDueAt)
        assertEquals(null, updated.activeToken)
        assertEquals(reviewedMemory.encode(), updated.kanjiMeaningMemory)
        assertEquals(reviewedMemory.encode(), updated.writingRemediationMemory)
        assertEquals(50, updated.suppressedAt)
        assertEquals(10, updated.createdAt)
        assertEquals("裂|sig", updated.answerSignature)
        assertEquals(1, current.withReviewUpdate(item.copy(rung = StudyRung.FONT_MEANING)).recognitionStage)
        assertEquals(2, current.withReviewUpdate(item.copy(rung = StudyRung.WORD_READING)).recognitionStage)
    }

    @Test
    fun studyQueueItemMapsToFullSeedEntity() {
        val memory = TaskMemory.from(
            state = "review",
            dueAtMillis = 12,
            stability = 2.0,
            difficulty = 4.0,
            totalReviews = 3,
            lapses = 1,
            learningStep = 0,
            lastRating = "good",
            matureIntervalDays = 5,
        )
        val item = studyItemEntity().toDomain().copy(
            state = StudyItemState.LEARNING,
            dueAtMillis = 200,
            rung = StudyRung.WRITE_KANJI,
            phase = StudyPhase.RELEARNING,
            suppressedByTaskType = "kanji_meaning",
            suppressedAtMillis = 150,
            createdAtMillis = 25,
            activeToken = null,
            memories = studyItemEntity().toDomain().memories.copy(
                similarKanjiMemory = memory,
            ),
        )

        val entity = item.toEntity()

        assertEquals("learning", entity.state)
        assertEquals(200, entity.dueAt)
        assertEquals(0, entity.recognitionStage)
        assertEquals(1, entity.writingRemediationPending)
        assertEquals("write_kanji", entity.rung)
        assertEquals("relearning", entity.phase)
        assertEquals("kanji_meaning", entity.suppressedByTaskType)
        assertEquals(150, entity.suppressedAt)
        assertEquals(25, entity.createdAt)
        assertEquals(null, entity.activeToken)
        assertEquals(memory.encode(), entity.similarKanjiMemory)
    }

    @Test
    fun dashboardRowEntityMapsToDomainModelWithExamples() {
        val row = dashboardRowEntity()
        val example = kanjiExampleEntity()

        val domain = row.toDomain(listOf(example))

        assertEquals("裂", domain.kanji)
        assertEquals(352, domain.jitenRank)
        assertEquals("split", domain.primaryMeaning)
        assertEquals("レツ", domain.reading)
        assertEquals("裂", domain.browserSearch)
        assertEquals(86, domain.weaknessScore)
        assertEquals("imported", domain.reasonCode)
        assertEquals("Imported from suspended cards", domain.reasonText)
        assertEquals(2, domain.activeExampleCount)
        assertEquals(1, domain.suspendedExampleCount)
        assertEquals(3, domain.matureSupportCount)
        assertEquals(1, domain.examples.size)
        assertEquals("suspended", domain.examples[0].sourceType)
        assertEquals("分裂", domain.examples[0].expression)
        assertEquals("ぶんれつ", domain.examples[0].reading)
        assertEquals("division", domain.examples[0].meaning)
        assertEquals(7.5, domain.examples[0].fsrsDifficulty)
        assertEquals(0.42, domain.examples[0].fsrsRetrievability)
    }

    private fun studyItemEntity(
        typingMeaningMemory: String = "",
    ): StudyItemEntity = StudyItemEntity(
        kanji = "裂",
        state = "review",
        dueAt = 1000,
        stability = 3.0,
        difficulty = 6.0,
        totalReviews = 7,
        lapses = 1,
        learningStep = 2,
        writingLevel = 1,
        recognitionStage = 1,
        consecutiveFailedRecognitionDays = 0,
        lastFailedRecognitionDay = 0,
        writingRemediationPending = 0,
        suppressedByTaskType = "font_meaning",
        suppressedAt = 50,
        matureIntervalDays = 21,
        answerSignature = "裂|sig",
        typingMeaningMemory = typingMeaningMemory,
        meaningKanjiMemory = "",
        kanjiMeaningMemory = "",
        fontMeaningMemory = "",
        wordReadingMemory = "",
        writingRemediationMemory = "",
        rung = "font_meaning",
        phase = "review",
        realPassStreak = 2,
        realAgainStreak = 0,
        lastRealReviewDueAt = 900,
        similarKanjiMemory = "",
        activeToken = "active",
        createdAt = 10,
    )

    private fun dashboardRowEntity(): DashboardRowEntity = DashboardRowEntity(
        kanji = "裂",
        jitenRank = 352,
        primaryMeaning = "split",
        reading = "レツ",
        browserSearch = "裂",
        weaknessScore = 86,
        reasonCode = "imported",
        reasonText = "Imported from suspended cards",
        activeExampleCount = 2,
        suspendedExampleCount = 1,
        matureSupportCount = 3,
        rebuiltAt = 100,
    )

    private fun kanjiExampleEntity(): KanjiExampleEntity = KanjiExampleEntity(
        id = 1,
        kanji = "裂",
        sourceType = "suspended",
        cardId = 11,
        noteId = 10,
        expression = "分裂",
        reading = "ぶんれつ",
        meaning = "division",
        sentence = "細胞分裂",
        mature = 0,
        lapses = 2,
        intervalDays = 14,
        reps = 5,
        fsrsStability = 12.0,
        fsrsDifficulty = 7.5,
        fsrsRetrievability = 0.42,
    )
}
