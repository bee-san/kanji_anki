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
    answerSignature = answerSignature,
    rung = StudyRung.fromWireName(rung),
    phase = StudyPhase.fromWireName(phase),
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
