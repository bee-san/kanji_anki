package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.SyncProgressCopy
import dev.bee.kanjianki.syncapi.CollectionProgress

/** Provider-neutral progress exposed by shared sync orchestration. */
class SyncProgress private constructor(
    @JvmField val stage: Stage?,
    @JvmField val scannedCards: Int,
    @JvmField val totalCards: Int,
) {
    fun totalKnown(): Boolean = totalCards >= 0

    fun coreStage(): SyncProgressCopy.Stage? = coreStage(stage)

    fun interface Listener {
        fun onSyncProgress(progress: SyncProgress)
    }

    enum class Stage {
        FINDING_NOTE_TYPE,
        READING_NOTES,
        SCANNING_CARDS,
        PROCESSING_IMPORTED_CARDS,
        SAVING_LOCAL_DATA,
        BUILDING_PRACTICE_QUEUE,
        ARCHIVING_IMPORTED_CARDS,
        TAGGING_REPAIRED,
    }

    companion object {
        @JvmField
        val NONE: Listener = Listener { }

        @JvmStatic
        fun atStage(stage: Stage?): SyncProgress {
            return SyncProgress(stage, 0, -1)
        }

        @JvmStatic
        fun cardsScanned(scannedCards: Int, totalCards: Int): SyncProgress {
            return SyncProgress(Stage.SCANNING_CARDS, scannedCards.coerceAtLeast(0), totalCards.coerceAtLeast(0))
        }

        @JvmStatic
        fun fromCollection(progress: CollectionProgress): SyncProgress {
            val stage = when (progress.stage) {
                CollectionProgress.Stage.FINDING_NOTE_TYPE -> Stage.FINDING_NOTE_TYPE
                CollectionProgress.Stage.READING_NOTES -> Stage.READING_NOTES
                CollectionProgress.Stage.SCANNING_CARDS -> Stage.SCANNING_CARDS
                CollectionProgress.Stage.ARCHIVING_IMPORTED_CARDS -> Stage.ARCHIVING_IMPORTED_CARDS
                CollectionProgress.Stage.TAGGING_REPAIRED -> Stage.TAGGING_REPAIRED
                CollectionProgress.Stage.READING_INVENTORY -> Stage.READING_NOTES
                CollectionProgress.Stage.WRITING_MISSING_KANJI -> Stage.SAVING_LOCAL_DATA
            }
            return SyncProgress(stage, progress.completed, progress.total ?: -1)
        }

        @JvmStatic
        fun coreStage(stage: Stage?): SyncProgressCopy.Stage? {
            return when (stage) {
                null -> null
                Stage.FINDING_NOTE_TYPE -> SyncProgressCopy.Stage.FINDING_NOTE_TYPE
                Stage.READING_NOTES -> SyncProgressCopy.Stage.READING_NOTES
                Stage.SCANNING_CARDS -> SyncProgressCopy.Stage.SCANNING_CARDS
                Stage.PROCESSING_IMPORTED_CARDS -> SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS
                Stage.SAVING_LOCAL_DATA -> SyncProgressCopy.Stage.SAVING_LOCAL_DATA
                Stage.BUILDING_PRACTICE_QUEUE -> SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE
                Stage.ARCHIVING_IMPORTED_CARDS -> SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS
                Stage.TAGGING_REPAIRED -> SyncProgressCopy.Stage.TAGGING_REPAIRED
            }
        }
    }
}
