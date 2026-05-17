package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.sync.SyncRunEntity
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus

internal fun SyncRunEntity.toDomain(): SyncRun = SyncRun(
    id = id?.let(::SyncRunId),
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = SyncRunStatus.fromWireName(status),
    activeNotesCount = activeNotesCount,
    activeCardsCount = activeCardsCount,
    suspendedCardsArchivedCount = suspendedCardsArchivedCount,
    suspendedKanjiImportedCount = suspendedKanjiImportedCount,
    deletedNotesCount = deletedNotesCount,
    deletedCardsCount = deletedCardsCount,
    errorCode = errorCode,
    errorMessage = errorMessage,
    removalMessage = removalMessage,
)

internal fun SyncRun.toEntity(): SyncRunEntity = SyncRunEntity(
    id = id?.value,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status.wireName,
    activeNotesCount = activeNotesCount,
    activeCardsCount = activeCardsCount,
    suspendedCardsArchivedCount = suspendedCardsArchivedCount,
    suspendedKanjiImportedCount = suspendedKanjiImportedCount,
    deletedNotesCount = deletedNotesCount,
    deletedCardsCount = deletedCardsCount,
    errorCode = errorCode,
    errorMessage = errorMessage,
    removalMessage = removalMessage,
)
