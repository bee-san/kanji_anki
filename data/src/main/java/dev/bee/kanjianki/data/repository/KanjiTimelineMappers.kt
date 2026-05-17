package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.history.KanjiTimelineEventEntity
import dev.bee.kanjianki.domain.model.study.StudyKanjiTimelineEvent

internal fun KanjiTimelineEventEntity.toDomain(): StudyKanjiTimelineEvent =
    StudyKanjiTimelineEvent(
        id = id ?: 0L,
        kanji = kanji,
        occurredAtMillis = occurredAt.coerceAtLeast(0L),
        eventType = eventType,
        title = title,
        detail = detail,
        sourceExpression = sourceExpression,
        sourceReading = sourceReading,
        rating = rating,
        writingRequired = writingRequired != 0,
        writingPassed = writingPassed != 0,
        manualOverride = manualOverride != 0,
        weaknessScore = weaknessScore,
        matureSupportCount = matureSupportCount,
        syncId = syncId,
        dedupeKey = dedupeKey,
    )
