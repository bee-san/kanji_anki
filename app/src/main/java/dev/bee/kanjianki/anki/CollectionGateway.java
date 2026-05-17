package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.sync.SyncProgress;

public interface CollectionGateway {
    RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) throws AnkiDroidGateway.SyncFailure;

    default RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings, SyncProgress.Listener progress) throws AnkiDroidGateway.SyncFailure {
        return readCollection(settings);
    }

    AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot);

    default AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot, SyncProgress.Listener progress) {
        return removeArchivedSuspendedCards(snapshot);
    }

    default AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(
            RecordsSyncModels.CollectionSnapshot snapshot,
            java.util.List<RecordsImportModels.SuspendedImport> selectedSuspendedImports,
            SyncProgress.Listener progress
    ) {
        return removeArchivedSuspendedCards(snapshot, progress);
    }
}
