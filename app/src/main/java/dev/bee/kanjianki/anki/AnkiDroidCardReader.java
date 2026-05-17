package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import dev.bee.kanjianki.sync.SyncProgress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AnkiDroidCardReader {
    private static final String TAG = "AnkiDroidGateway";
    private static final String CONTENT_SCHEME = "content";
    private static final String NOTE_MODEL_QUERY_PREFIX = "note:\"";
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
    private static final Pattern FSRS_DATA_VALUE = Pattern.compile(
            "(?:\"|')?(stability|difficulty|retrievability|s|d|r)(?:\"|')?\\s*[:=]\\s*\"?([-+]?[0-9]+(?:\\.[0-9]+)?)\"?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FINITE_DOUBLE_VALUE = Pattern.compile("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");

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
        if (scanned <= 0 || scanned == total || total <= 100) {
            return true;
        }
        if (scanned <= 10) {
            return true;
        }
        return scanned % (total <= 1000 ? 10 : 50) == 0;
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
                Integer cardQueue = nullableIntValue(cardCursor, COLUMN_QUEUE);
                int queue = cardQueue == null ? (suspendedFromSearch ? -1 : 0) : cardQueue;
                Integer cardType = nullableIntValue(cardCursor, COLUMN_TYPE);
                boolean suspended = isCardSuspended(cardQueue, cardType, suspendedFromSearch);
                FsrsMemoryState fsrs = fsrsMemoryState(cardCursor);
                String deckId = value(cardCursor, COLUMN_DECK_ID);
                cards.add(new RecordsSyncModels.Card(
                        longValue(cardCursor, COLUMN_ID, noteId * 1000L + ord),
                        longValue(cardCursor, COLUMN_NOTE_ID, noteId),
                        ord,
                        deckId,
                        deckId,
                        queue,
                        cardType == null ? (suspended ? 3 : 0) : cardType,
                        intValue(cardCursor, COLUMN_DUE, 0),
                        intValue(cardCursor, COLUMN_INTERVAL, 0),
                        intValue(cardCursor, COLUMN_REPS, 0),
                        intValue(cardCursor, COLUMN_LAPSES, 0),
                        suspended,
                        fsrs.stability,
                        fsrs.difficulty,
                        fsrs.retrievability
                ));
            }
        }
        return cards;
    }

    static boolean isCardSuspended(Integer cardQueue, Integer cardType, boolean suspendedFromSearch) {
        if (cardQueue != null) {
            return cardQueue < 0;
        }
        if (cardType != null) {
            return cardType == 3;
        }
        return suspendedFromSearch;
    }

    private Set<Long> querySuspendedNoteIds(String authority, RecordsSyncModels.Settings settings) {
        Set<Long> ids = new LinkedHashSet<>();
        Cursor cursor;
        try {
            cursor = resolver.query(
                    uriFor(authority, URI_SEGMENT_NOTES),
                    null,
                    NOTE_MODEL_QUERY_PREFIX + settings.modelName + "\" is:suspended",
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

    private static long longValue(Cursor cursor, String column, long fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getLong(index);
    }

    private static int intValue(Cursor cursor, String column, int fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getInt(index);
    }

    private static Integer nullableIntValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return null;
        }
        try {
            String value = cursor.getString(index);
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static FsrsMemoryState fsrsMemoryState(Cursor cursor) {
        Double stability = firstDouble(cursor, COLUMN_FSRS_STABILITY, COLUMN_STABILITY);
        Double difficulty = firstDouble(cursor, COLUMN_FSRS_DIFFICULTY, COLUMN_DIFFICULTY);
        Double retrievability = firstDouble(cursor, COLUMN_FSRS_RETRIEVABILITY, COLUMN_RETRIEVABILITY);
        if (stability != null || difficulty != null || retrievability != null) {
            return new FsrsMemoryState(stability, difficulty, retrievability);
        }
        return parseFsrsData(value(cursor, COLUMN_DATA));
    }

    private static Double firstDouble(Cursor cursor, String... columns) {
        for (String column : columns) {
            Double value = doubleValue(cursor, column);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Double doubleValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return null;
        }
        String value = cursor.getString(index);
        if (value == null) {
            return null;
        }
        return parseDouble(value.trim());
    }

    private static FsrsMemoryState parseFsrsData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return FsrsMemoryState.EMPTY;
        }
        Double stability = null;
        Double difficulty = null;
        Double retrievability = null;
        Matcher matcher = FSRS_DATA_VALUE.matcher(data);
        while (matcher.find()) {
            Double value = parseDouble(matcher.group(2));
            if (value == null) {
                continue;
            }
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            if (COLUMN_STABILITY.equals(key) || "s".equals(key)) {
                stability = value;
            } else if (COLUMN_DIFFICULTY.equals(key) || "d".equals(key)) {
                difficulty = value;
            } else {
                retrievability = value;
            }
        }
        return new FsrsMemoryState(stability, difficulty, retrievability);
    }

    private static Double parseDouble(String value) {
        if (value == null || !FINITE_DOUBLE_VALUE.matcher(value).matches()) {
            return null;
        }
        double parsed = Double.parseDouble(value);
        return Double.isInfinite(parsed) ? null : parsed;
    }

    record ProjectionReadResult(List<RecordsSyncModels.Card> cards, int projectionIndex) {
    }

    private static final class FsrsMemoryState {
        private static final FsrsMemoryState EMPTY = new FsrsMemoryState(null, null, null);
        private final Double stability;
        private final Double difficulty;
        private final Double retrievability;

        private FsrsMemoryState(Double stability, Double difficulty, Double retrievability) {
            this.stability = stability;
            this.difficulty = difficulty;
            this.retrievability = retrievability;
        }
    }
}
