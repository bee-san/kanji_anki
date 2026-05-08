package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.sync.SyncProgress;

public interface CollectionGateway {
    Records.CollectionSnapshot readCollection(Records.Settings settings) throws AnkiDroidGateway.SyncException;

    default Records.CollectionSnapshot readCollection(Records.Settings settings, SyncProgress.Listener progress) throws AnkiDroidGateway.SyncException {
        return readCollection(settings);
    }

    AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot);

    default AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot, SyncProgress.Listener progress) {
        return removeArchivedSuspendedCards(snapshot);
    }
}
