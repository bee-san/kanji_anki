package dev.bee.kanjianki.data

internal data class SyncRunRecord(
    val startedAt: Long,
    val finishedAt: Long,
    val status: String?,
    val activeNotesCount: Int,
    val activeCardsCount: Int,
    val archivedSuspendedCardCount: Int,
    val importedSuspendedKanjiCount: Int,
    val deletedNotesCount: Int,
    val deletedCardsCount: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val removalMessage: String?,
)
