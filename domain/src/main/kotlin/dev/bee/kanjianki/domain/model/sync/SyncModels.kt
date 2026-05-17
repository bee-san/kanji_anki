package dev.bee.kanjianki.domain.model.sync

import dev.bee.kanjianki.domain.model.SyncRunId

enum class SyncRunStatus(val wireName: String) {
    SUCCESS("success"),
    CONFIG_ERROR("config_error"),
    RETRYABLE_ERROR("retryable_error");

    companion object {
        fun fromWireName(wireName: String): SyncRunStatus =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown sync run status: $wireName")
    }
}

enum class SyncErrorCode(val wireName: String) {
    PERMANENT("permanent"),
    RETRYABLE("retryable"),
    UNEXPECTED("unexpected"),
    PERMANENT_PERMISSION("permanent_permission"),
    PERMANENT_CONFIGURATION("permanent_configuration");

    companion object {
        fun fromWireName(wireName: String): SyncErrorCode =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown sync error code: $wireName")
    }
}

data class SyncRun(
    val id: SyncRunId?,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: SyncRunStatus,
    val activeNotesCount: Int,
    val activeCardsCount: Int,
    val suspendedCardsArchivedCount: Int,
    val suspendedKanjiImportedCount: Int,
    val deletedNotesCount: Int,
    val deletedCardsCount: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val removalMessage: String?,
) {
    init {
        require(startedAt >= 0) { "startedAt must be non-negative" }
        require(finishedAt == null || finishedAt >= startedAt) {
            "finishedAt must be null or greater than startedAt"
        }
        require(activeNotesCount >= 0) { "activeNotesCount must be non-negative" }
        require(activeCardsCount >= 0) { "activeCardsCount must be non-negative" }
        require(suspendedCardsArchivedCount >= 0) {
            "suspendedCardsArchivedCount must be non-negative"
        }
        require(suspendedKanjiImportedCount >= 0) {
            "suspendedKanjiImportedCount must be non-negative"
        }
        require(deletedNotesCount >= 0) { "deletedNotesCount must be non-negative" }
        require(deletedCardsCount >= 0) { "deletedCardsCount must be non-negative" }
    }
}
