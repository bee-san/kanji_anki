package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.sync.SyncProgress;

public interface CollectionGateway {
    Records.CollectionSnapshot readCollection(Records.Settings settings) throws AnkiDroidGateway.SyncFailure;

    default Records.CollectionSnapshot readCollection(Records.Settings settings, SyncProgress.Listener progress) throws AnkiDroidGateway.SyncFailure {
        return readCollection(settings);
    }

    AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot);

    default AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot, SyncProgress.Listener progress) {
        return removeArchivedSuspendedCards(snapshot);
    }

    default AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(
            Records.CollectionSnapshot snapshot,
            java.util.List<Records.SuspendedImport> selectedSuspendedImports,
            SyncProgress.Listener progress
    ) {
        return removeArchivedSuspendedCards(snapshot, progress);
    }
}
