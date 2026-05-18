package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import dev.bee.kanjianki.sync.SyncProgress;
import dev.bee.kanjianki.syncdomain.ProviderCardPolicy;
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AnkiDroidCardReader {
    private static final String TAG = "AnkiDroidGateway";
    private static final String CONTENT_SCHEME = "content";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_NOTE_ID = "note_id";
    private static final String COLUMN_ORD = "ord";
    private static final String COLUMN_DECK_ID = "deck_id";
    private static final String COLUMN_QUEUE = "queue";
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_DUE = "due";
    private static final String COLUMN_INTERVAL = "interval";
    private static final String COLUMN_REPS = "reps";
    private static final String COLUMN_LAPSES = "lapses";
    private static final String COLUMN_FSRS_STABILITY = "fsrs_stability";
    private static final String COLUMN_FSRS_DIFFICULTY = "fsrs_difficulty";
    private static final String COLUMN_FSRS_RETRIEVABILITY = "fsrs_retrievability";
    private static final String COLUMN_STABILITY = "stability";
    private static final String COLUMN_DIFFICULTY = "difficulty";
    private static final String COLUMN_RETRIEVABILITY = "retrievability";
    private static final String COLUMN_DATA = "data";
    private static final String URI_SEGMENT_NOTES = "notes";
    private static final String[] CARD_COLUMNS_WITH_FSRS = {
            COLUMN_NOTE_ID,
            COLUMN_ORD,
            COLUMN_DECK_ID,
            COLUMN_QUEUE,
            COLUMN_TYPE,
            COLUMN_DUE,
            COLUMN_INTERVAL,
            COLUMN_REPS,
            COLUMN_LAPSES,
            COLUMN_FSRS_STABILITY,
            COLUMN_FSRS_DIFFICULTY,
            COLUMN_FSRS_RETRIEVABILITY,
            COLUMN_STABILITY,
            COLUMN_DIFFICULTY,
            COLUMN_RETRIEVABILITY,
            COLUMN_DATA
    };
    private static final String[] CARD_COLUMNS_WITH_SCHEDULER = {
            COLUMN_NOTE_ID,
            COLUMN_ORD,
            COLUMN_DECK_ID,
            COLUMN_QUEUE,
            COLUMN_TYPE,
            COLUMN_DUE,
            COLUMN_INTERVAL,
            COLUMN_REPS,
            COLUMN_LAPSES
    };
    private static final String[] CARD_COLUMNS_MINIMAL = {COLUMN_NOTE_ID, COLUMN_ORD, COLUMN_DECK_ID};
    private final ContentResolver resolver;

    AnkiDroidCardReader(ContentResolver resolver) {
        this.resolver = resolver;
    }

    List<RecordsSyncModels.Card> queryCardsByNote(
            String authority,
            RecordsSyncModels.Settings settings,
            Set<Long> noteIds,
            SyncProgress.Listener progress
    ) throws AnkiDroidGateway.SyncFailure {
        int total = noteIds.size();
        int scanned = 0;
        progress.onSyncProgress(SyncProgress.cardsScanned(scanned, total));
        Set<Long> suspendedNoteIds = querySuspendedNoteIds(authority, settings);
        List<RecordsSyncModels.Card> cards = new ArrayList<>();
        String[][] projections = new String[][]{
                CARD_COLUMNS_WITH_FSRS,
                CARD_COLUMNS_WITH_SCHEDULER,
                CARD_COLUMNS_MINIMAL
        };
        int projectionIndex = 0;
        for (Long noteId : noteIds) {
            ProjectionReadResult result = readCardsForNote(authority, noteId, suspendedNoteIds, projections, projectionIndex);
            projectionIndex = result.projectionIndex;
            cards.addAll(result.cards);
            scanned++;
            reportCardProgressIfNeeded(progress, scanned, total);
        }
        return cards;
    }

    static void reportCardProgressIfNeeded(SyncProgress.Listener progress, int scanned, int total) {
        if (shouldReportCardProgress(scanned, total)) {
            progress.onSyncProgress(SyncProgress.cardsScanned(scanned, total));
        }
    }

    ProjectionReadResult readCardsForNote(
            String authority,
            long noteId,
            Set<Long> suspendedNoteIds,
            String[][] projections,
            int startProjectionIndex
    ) throws AnkiDroidGateway.SyncFailure {
        int projectionIndex = startProjectionIndex;
        while (projectionIndex < projections.length) {
            try {
                return new ProjectionReadResult(
                        queryCardsForNote(authority, noteId, suspendedNoteIds, projections[projectionIndex]),
                        projectionIndex
                );
            } catch (Exception unsupportedColumns) {
                projectionIndex++;
                if (projectionIndex >= projections.length) {
                    if (unsupportedColumns instanceof AnkiDroidGateway.SyncFailure syncFailure) {
                        throw syncFailure;
                    }
                    throw AnkiDroidGateway.SyncFailure.retryable("AnkiDroid card projection failed: " + unsupportedColumns.getMessage(), unsupportedColumns);
                }
            }
        }
        return new ProjectionReadResult(Collections.emptyList(), projectionIndex);
    }

    static boolean shouldReportCardProgress(int scanned, int total) {
        return ProviderCardPolicy.shouldReportCardProgress(scanned, total);
    }

    private List<RecordsSyncModels.Card> queryCardsForNote(String authority, long noteId, Set<Long> suspendedNoteIds, String[] columns) throws AnkiDroidGateway.SyncFailure {
        Cursor cursor = resolver.query(uriFor(authority, URI_SEGMENT_NOTES, Long.toString(noteId), "cards"), columns, null, null, null);
        if (cursor == null) {
            throw AnkiDroidGateway.SyncFailure.retryable("AnkiDroid returned no per-note card cursor.");
        }
        List<RecordsSyncModels.Card> cards = new ArrayList<>();
        try (Cursor cardCursor = cursor) {
            while (cardCursor.moveToNext()) {
                int ord = intValue(cardCursor, COLUMN_ORD, 0);
                boolean suspendedFromSearch = suspendedNoteIds.contains(noteId);
                int queue = intValue(cardCursor, COLUMN_QUEUE, suspendedFromSearch ? -1 : 0);
                boolean suspended = suspendedFromSearch || queue < 0;
                ProviderCardPolicy.FsrsMemoryState fsrs = fsrsMemoryState(cardCursor);
                String deckId = value(cardCursor, COLUMN_DECK_ID);
                cards.add(new RecordsSyncModels.Card(
                        longValue(cardCursor, COLUMN_ID, noteId * 1000L + ord),
                        longValue(cardCursor, COLUMN_NOTE_ID, noteId),
                        ord,
                        deckId,
                        deckId,
                        queue,
                        intValue(cardCursor, COLUMN_TYPE, suspended ? 3 : 0),
                        intValue(cardCursor, COLUMN_DUE, 0),
                        intValue(cardCursor, COLUMN_INTERVAL, 0),
                        intValue(cardCursor, COLUMN_REPS, 0),
                        intValue(cardCursor, COLUMN_LAPSES, 0),
                        suspended,
                        fsrs.stability(),
                        fsrs.difficulty(),
                        fsrs.retrievability()
                ));
            }
            return cards;
        }
    }

    private Set<Long> querySuspendedNoteIds(String authority, RecordsSyncModels.Settings settings) {
        Set<Long> ids = new LinkedHashSet<>();
        Cursor cursor;
        try {
            cursor = resolver.query(
                    uriFor(authority, URI_SEGMENT_NOTES),
                    null,
                    ProviderNotePolicy.modelSearch(settings.modelName) + " is:suspended",
                    null,
                    null
            );
        } catch (Exception error) {
            Log.d(TAG, "AnkiDroid suspended-note search unavailable.", error);
            return ids;
        }
        if (cursor == null) {
            return ids;
        }
        try (Cursor suspendedCursor = cursor) {
            while (suspendedCursor.moveToNext()) {
                ids.add(longValue(suspendedCursor, COLUMN_ID, 0));
            }
        }
        return ids;
    }

    private static Uri uriFor(String authority, String... segments) {
        Uri.Builder builder = new Uri.Builder().scheme(CONTENT_SCHEME).authority(authority);
        for (String segment : segments) {
            builder.appendPath(segment);
        }
        return builder.build();
    }

    private static String value(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return "";
        }
        return cursor.getString(index);
    }

    private static String nullableValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static long longValue(Cursor cursor, String column, long fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getLong(index);
    }

    private static int intValue(Cursor cursor, String column, int fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getInt(index);
    }

    private static ProviderCardPolicy.FsrsMemoryState fsrsMemoryState(Cursor cursor) {
        return ProviderCardPolicy.fsrsMemoryState(
                nullableValue(cursor, COLUMN_FSRS_STABILITY),
                nullableValue(cursor, COLUMN_STABILITY),
                nullableValue(cursor, COLUMN_FSRS_DIFFICULTY),
                nullableValue(cursor, COLUMN_DIFFICULTY),
                nullableValue(cursor, COLUMN_FSRS_RETRIEVABILITY),
                nullableValue(cursor, COLUMN_RETRIEVABILITY),
                nullableValue(cursor, COLUMN_DATA)
        );
    }

    record ProjectionReadResult(List<RecordsSyncModels.Card> cards, int projectionIndex) {
    }
}
