package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncdomain.SyncMirrorPolicy

internal object LocalStoreSyncMirrorAdapters {
    fun selectedSuspendedCardIds(imports: List<RecordsImportModels.SuspendedImport>): Set<Long> {
        val sources = ArrayList<SyncMirrorPolicy.SelectedSource>()
        for (imported in imports) {
            for (source in imported.sources) {
                sources.add(SyncMirrorPolicy.SelectedSource(source.cardId, source.suspended))
            }
        }
        return SyncMirrorPolicy.selectedSuspendedCardIds(sources)
    }

    fun activeCardIndex(cards: List<RecordsSyncModels.Card>): LocalStoreBase.ActiveCardIndex {
        val policyCards = ArrayList<SyncMirrorPolicy.Card>()
        for (card in cards) {
            policyCards.add(SyncMirrorPolicy.Card(card.cardId, card.noteId, card.suspended))
        }
        val index = SyncMirrorPolicy.activeCardIndex(policyCards)
        return LocalStoreBase.ActiveCardIndex(index.noteIds(), index.cardIds(), index.activeCardCount())
    }
}
