package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.study.StudyItemEntity
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemory
import dev.bee.kanjianki.domain.model.study.TaskMemoryBank

internal fun StudyItemEntity.toDomain(
    hasSimilarKanji: Boolean = false,
): StudyQueueItem = StudyQueueItem(
    kanji = kanji,
    state = StudyItemState.fromWireName(state),
    dueAtMillis = dueAt,
    stability = stability,
    difficulty = difficulty,
    totalReviews = totalReviews,
    lapses = lapses,
    learningStep = learningStep,
    writingLevel = writingLevel,
    matureIntervalDays = matureIntervalDays,
    answerSignature = answerSignature,
    rung = StudyRung.fromWireName(rung),
    phase = StudyPhase.fromWireName(phase),
    realPassStreak = realPassStreak,
    realAgainStreak = realAgainStreak,
    lastRealReviewDueAtMillis = lastRealReviewDueAt,
    suppressedByTaskType = suppressedByTaskType,
    hasSimilarKanji = hasSimilarKanji,
    activeToken = activeToken,
    memories = TaskMemoryBank(
        typingMeaningMemory = TaskMemory.decode(typingMeaningMemory),
        meaningKanjiMemory = TaskMemory.decode(meaningKanjiMemory),
        kanjiMeaningMemory = TaskMemory.decode(kanjiMeaningMemory),
        fontMeaningMemory = TaskMemory.decode(fontMeaningMemory),
        wordReadingMemory = TaskMemory.decode(wordReadingMemory),
        writingRemediationMemory = TaskMemory.decode(writingRemediationMemory),
        similarKanjiMemory = TaskMemory.decode(similarKanjiMemory),
    ),
)

internal fun StudyItemEntity.withReviewUpdate(item: StudyQueueItem): StudyItemEntity = copy(
    state = item.state.wireName,
    dueAt = item.dueAtMillis,
    stability = item.stability,
    difficulty = item.difficulty,
    totalReviews = item.totalReviews,
    lapses = item.lapses,
    learningStep = item.learningStep,
    writingLevel = item.writingLevel,
    recognitionStage = item.rung.toLegacyRecognitionStage(),
    consecutiveFailedRecognitionDays = item.realAgainStreak,
    lastFailedRecognitionDay = item.lastRealReviewDueAtMillis,
    writingRemediationPending = if (item.rung == StudyRung.WRITE_KANJI) 1 else 0,
    matureIntervalDays = item.matureIntervalDays,
    rung = item.rung.wireName,
    phase = item.phase.wireName,
    realPassStreak = item.realPassStreak,
    realAgainStreak = item.realAgainStreak,
    lastRealReviewDueAt = item.lastRealReviewDueAtMillis,
    activeToken = item.activeToken,
    typingMeaningMemory = item.memories.typingMeaningMemory.encode(),
    meaningKanjiMemory = item.memories.meaningKanjiMemory.encode(),
    kanjiMeaningMemory = item.memories.kanjiMeaningMemory.encode(),
    fontMeaningMemory = item.memories.fontMeaningMemory.encode(),
    wordReadingMemory = item.memories.wordReadingMemory.encode(),
    writingRemediationMemory = item.memories.writingRemediationMemory.encode(),
    similarKanjiMemory = item.memories.similarKanjiMemory.encode(),
)

private fun StudyRung.toLegacyRecognitionStage(): Int = when (this) {
    StudyRung.TYPE_MEANING -> -1
    StudyRung.FONT_MEANING -> 1
    StudyRung.WORD_READING -> 2
    StudyRung.WRITE_KANJI,
    StudyRung.SIMILAR_KANJI,
    StudyRung.MEANING_KANJI,
    StudyRung.KANJI_MEANING -> 0
}
