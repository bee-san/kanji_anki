package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.Records;

public interface CollectionGateway {
    Records.CollectionSnapshot readCollection(Records.Settings settings) throws AnkiDroidGateway.SyncException;

    AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot);
}
