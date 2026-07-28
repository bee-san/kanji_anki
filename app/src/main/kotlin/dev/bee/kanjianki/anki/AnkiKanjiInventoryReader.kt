package dev.bee.kanjianki.anki

import dev.bee.kanjianki.core.AnkiKanjiInventory
import dev.bee.kanjianki.core.AnkiKanjiInventoryCollector
import dev.bee.kanjianki.core.AnkiKanjiInventoryProgress

/**
 * Collection-wide aggregate reader. This does not use configured-model sync
 * and returns no note IDs, model names, field names, or raw note text.
 */
class AnkiKanjiInventoryReader internal constructor(
    private val source: NoteStream,
) {
    constructor(gateway: AnkiDroidCollectionInventoryGateway) : this(
        NoteStream { consumer, progress -> gateway.scan(consumer, progress) },
    )

    fun read(
        progress: ProgressListener = ProgressListener.NONE,
    ): AnkiKanjiInventory {
        val collector = AnkiKanjiInventoryCollector()
        progress.onProgress(AnkiKanjiInventoryProgress(0, 0, 0))
        val result = source.scan(
            AnkiDroidCollectionInventoryGateway.NoteConsumer { note ->
                for (field in note.fields) {
                    collector.addNormalizedField(AnkiFieldTextNormalizer.normalize(field))
                }
            },
            AnkiDroidCollectionInventoryGateway.ProgressListener { gatewayProgress ->
                progress.onProgress(
                    AnkiKanjiInventoryProgress(
                        notesScanned = gatewayProgress.notesRead,
                        uniqueKanjiCount = collector.uniqueKanjiCount(),
                        skippedNotes = gatewayProgress.skippedNotes,
                    ),
                )
            },
        )
        return collector.finish(
            notesScanned = result.notesRead,
            skippedNotes = result.skippedNotes,
            modelCount = result.modelCount,
        )
    }

    fun interface ProgressListener {
        fun onProgress(progress: AnkiKanjiInventoryProgress)

        companion object {
            val NONE = ProgressListener { }
        }
    }

    internal fun interface NoteStream {
        fun scan(
            consumer: AnkiDroidCollectionInventoryGateway.NoteConsumer,
            progress: AnkiDroidCollectionInventoryGateway.ProgressListener,
        ): AnkiDroidCollectionInventoryGateway.ScanResult
    }
}
