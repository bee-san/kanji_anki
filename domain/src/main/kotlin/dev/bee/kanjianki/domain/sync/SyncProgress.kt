package dev.bee.kanjianki.domain.sync

import kotlin.coroutines.cancellation.CancellationException

fun interface SyncProgressListener {
    fun onSyncProgress(progress: SyncProgressSnapshot)
}

object NoOpSyncProgressListener : SyncProgressListener {
    override fun onSyncProgress(progress: SyncProgressSnapshot) = Unit
}

fun SyncProgressListener.reportSyncProgress(progress: SyncProgressSnapshot) {
    try {
        onSyncProgress(progress)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Progress observers are UI/status side effects and must not change sync outcome.
    }
}

data class SyncProgressSnapshot(
    val stage: SyncProgressStage,
    val scannedCards: Int = 0,
    val totalCards: Int = UNKNOWN_TOTAL,
) {
    init {
        require(scannedCards >= 0) { "scannedCards must be non-negative" }
        require(totalCards >= UNKNOWN_TOTAL) { "totalCards must be -1 or non-negative" }
    }

    val totalKnown: Boolean
        get() = totalCards >= 0

    companion object {
        const val UNKNOWN_TOTAL = -1

        fun atStage(stage: SyncProgressStage): SyncProgressSnapshot =
            SyncProgressSnapshot(stage = stage)

        fun cardsScanned(
            scannedCards: Int,
            totalCards: Int = UNKNOWN_TOTAL,
        ): SyncProgressSnapshot = SyncProgressSnapshot(
            stage = SyncProgressStage.SCANNING_CARDS,
            scannedCards = scannedCards,
            totalCards = totalCards.coerceAtLeast(UNKNOWN_TOTAL),
        )
    }
}

enum class SyncProgressStage {
    FINDING_NOTE_TYPE,
    READING_NOTES,
    SCANNING_CARDS,
    PROCESSING_IMPORTED_CARDS,
    BUILDING_PRACTICE_QUEUE,
    ARCHIVING_IMPORTED_CARDS,
}
