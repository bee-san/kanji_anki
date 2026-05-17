package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleEntity
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample

internal fun DashboardRowEntity.toDomain(
    examples: List<KanjiExampleEntity> = emptyList(),
): StudyDashboardRow = StudyDashboardRow(
    kanji = kanji,
    jitenRank = jitenRank,
    primaryMeaning = primaryMeaning,
    reading = reading,
    browserSearch = browserSearch,
    weaknessScore = weaknessScore,
    reasonCode = reasonCode,
    reasonText = reasonText,
    activeExampleCount = activeExampleCount,
    suspendedExampleCount = suspendedExampleCount,
    matureSupportCount = matureSupportCount,
    examples = examples.map { it.toDomain() },
)

internal fun KanjiExampleEntity.toDomain(): StudyExample = StudyExample(
    sourceType = sourceType,
    expression = expression,
    reading = reading,
    meaning = meaning,
    fsrsDifficulty = fsrsDifficulty,
    fsrsRetrievability = fsrsRetrievability,
    mature = mature != 0,
    lapses = lapses,
    intervalDays = intervalDays,
    reps = reps,
    fsrsStability = fsrsStability,
    cardId = cardId,
    noteId = noteId,
    sentence = sentence,
)

internal fun StudyDashboardRow.toEntity(rebuiltAt: Long): DashboardRowEntity = DashboardRowEntity(
    kanji = kanji,
    jitenRank = jitenRank,
    primaryMeaning = primaryMeaning,
    reading = reading,
    browserSearch = browserSearch,
    weaknessScore = weaknessScore,
    reasonCode = reasonCode,
    reasonText = reasonText,
    activeExampleCount = activeExampleCount,
    suspendedExampleCount = suspendedExampleCount,
    matureSupportCount = matureSupportCount,
    rebuiltAt = rebuiltAt,
)

internal fun StudyDashboardRow.toExampleEntities(): List<KanjiExampleEntity> =
    examples.map { example ->
        KanjiExampleEntity(
            kanji = kanji,
            sourceType = example.sourceType,
            cardId = example.cardId,
            noteId = example.noteId,
            expression = example.expression,
            reading = example.reading,
            meaning = example.meaning,
            sentence = example.sentence,
            mature = if (example.mature) 1 else 0,
            lapses = example.lapses,
            intervalDays = example.intervalDays,
            reps = example.reps,
            fsrsStability = example.fsrsStability,
            fsrsDifficulty = example.fsrsDifficulty,
            fsrsRetrievability = example.fsrsRetrievability,
        )
    }
