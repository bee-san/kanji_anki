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
)
