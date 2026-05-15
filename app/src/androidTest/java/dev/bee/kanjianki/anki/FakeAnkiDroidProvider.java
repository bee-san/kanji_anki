package dev.bee.kanjianki.anki;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.OperationCanceledException;

public final class FakeAnkiDroidProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.bee.kanjianki.test.ankidroid";
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final java.util.regex.Pattern NOTES_CARDS_PATH = java.util.regex.Pattern.compile("/notes/\\d+/cards");
    private static final java.util.regex.Pattern NOTES_ID_PATH = java.util.regex.Pattern.compile("/notes/\\d+");

    public static int topLevelCardsQueries;
    public static int perNoteCardsQueries;
    public static int explicitIdProjectionQueries;
    public static int schedulerProjectionRejects;
    public static int fsrsProjectionRejects;
    public static int browserQueryQueries;
    public static int cardProjectionRejects;
    public static String activeTags = "";
    public static String suspendedTags = "";
    public static boolean failSuspendedSearch;
    public static boolean rejectFsrsProjection;
    public static boolean dataOnlyFsrs;
    public static boolean unparseableFsrsData;
    public static boolean rejectSchedulerProjection;
    public static boolean deferSchedulerProjectionFailure;
    public static boolean browserQueryMatchesActive;
    public static boolean browserQueryMatchesSuspended;
    public static boolean failBrowserQuery;
    public static boolean failConfiguredSearch;
    public static boolean secondTemplateCard;
    public static boolean browserQueryMatchesMissingNote;
    public static boolean permanentProviderFailure;
    public static boolean retryableProviderFailure;
    public static boolean rejectAllCardProjections;
    public static boolean nullCardCursor;
    public static boolean partiallySuspendedNote;
    public static boolean operationCanceledProviderFailure;
    public static boolean securityProviderFailure;
    public static boolean nullModelsCursor;
    public static boolean nullConfiguredSearchCursor;
    public static boolean nullSqlNotesCursor;
    public static boolean nullSuspendedSearchCursor;
    public static boolean nullBrowserQueryCursor;
    public static boolean browserQueryWrongModel;
    public static boolean nullNoteCursor;
    public static boolean configuredSearchIncludesWrongModel;
    public static boolean failBrowserQueryReread;

    public static void reset() {
        topLevelCardsQueries = 0;
        perNoteCardsQueries = 0;
        explicitIdProjectionQueries = 0;
        schedulerProjectionRejects = 0;
        fsrsProjectionRejects = 0;
        browserQueryQueries = 0;
        cardProjectionRejects = 0;
        activeTags = "";
        suspendedTags = "";
        failSuspendedSearch = false;
        rejectFsrsProjection = false;
        dataOnlyFsrs = false;
        unparseableFsrsData = false;
        rejectSchedulerProjection = false;
        deferSchedulerProjectionFailure = false;
        browserQueryMatchesActive = false;
        browserQueryMatchesSuspended = false;
        failBrowserQuery = false;
        failConfiguredSearch = false;
        secondTemplateCard = false;
        browserQueryMatchesMissingNote = false;
        permanentProviderFailure = false;
        retryableProviderFailure = false;
        rejectAllCardProjections = false;
        nullCardCursor = false;
        partiallySuspendedNote = false;
        operationCanceledProviderFailure = false;
        securityProviderFailure = false;
        nullModelsCursor = false;
        nullConfiguredSearchCursor = false;
        nullSqlNotesCursor = false;
        nullSuspendedSearchCursor = false;
        nullBrowserQueryCursor = false;
        browserQueryWrongModel = false;
        nullNoteCursor = false;
        configuredSearchIncludesWrongModel = false;
        failBrowserQueryReread = false;
    }

    @Override
    public boolean onCreate() {
        reset();
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if ("reset".equals(method)) {
            reset();
            result.putBoolean("ok", true);
            return result;
        }
        if ("topLevelCardsQueries".equals(method)) {
            result.putInt("value", topLevelCardsQueries);
            return result;
        }
        if ("perNoteCardsQueries".equals(method)) {
            result.putInt("value", perNoteCardsQueries);
            return result;
        }
        if ("explicitIdProjectionQueries".equals(method)) {
            result.putInt("value", explicitIdProjectionQueries);
            return result;
        }
        if ("schedulerProjectionRejects".equals(method)) {
            result.putInt("value", schedulerProjectionRejects);
            return result;
        }
        if ("fsrsProjectionRejects".equals(method)) {
            result.putInt("value", fsrsProjectionRejects);
            return result;
        }
        if ("browserQueryQueries".equals(method)) {
            result.putInt("value", browserQueryQueries);
            return result;
        }
        if ("cardProjectionRejects".equals(method)) {
            result.putInt("value", cardProjectionRejects);
            return result;
        }
        if ("suspendedTags".equals(method)) {
            result.putString("value", suspendedTags);
            return result;
        }
        if ("failSuspendedSearch".equals(method)) {
            failSuspendedSearch = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("rejectFsrsProjection".equals(method)) {
            rejectFsrsProjection = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("unparseableFsrsData".equals(method)) {
            unparseableFsrsData = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("dataOnlyFsrs".equals(method)) {
            dataOnlyFsrs = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("rejectSchedulerProjection".equals(method)) {
            rejectSchedulerProjection = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("deferSchedulerProjectionFailure".equals(method)) {
            rejectSchedulerProjection = true;
            deferSchedulerProjectionFailure = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("browserQueryMatchesActive".equals(method)) {
            browserQueryMatchesActive = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("browserQueryMatchesSuspended".equals(method)) {
            browserQueryMatchesSuspended = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("browserQueryMatchesMissingNote".equals(method)) {
            browserQueryMatchesMissingNote = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("failBrowserQuery".equals(method)) {
            failBrowserQuery = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("failConfiguredSearch".equals(method)) {
            failConfiguredSearch = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("secondTemplateCard".equals(method)) {
            secondTemplateCard = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("permanentProviderFailure".equals(method)) {
            permanentProviderFailure = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("retryableProviderFailure".equals(method)) {
            retryableProviderFailure = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("rejectAllCardProjections".equals(method)) {
            rejectAllCardProjections = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("nullCardCursor".equals(method)) {
            nullCardCursor = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("partiallySuspendedNote".equals(method)) {
            partiallySuspendedNote = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("operationCanceledProviderFailure".equals(method)) {
            operationCanceledProviderFailure = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("securityProviderFailure".equals(method)) {
            securityProviderFailure = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("nullModelsCursor".equals(method)) {
            nullModelsCursor = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("nullConfiguredSearchCursor".equals(method)) {
            nullConfiguredSearchCursor = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("nullSqlNotesCursor".equals(method)) {
            nullSqlNotesCursor = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("nullSuspendedSearchCursor".equals(method)) {
            nullSuspendedSearchCursor = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("nullBrowserQueryCursor".equals(method)) {
            nullBrowserQueryCursor = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("browserQueryWrongModel".equals(method)) {
            browserQueryWrongModel = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("nullNoteCursor".equals(method)) {
            nullNoteCursor = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("configuredSearchIncludesWrongModel".equals(method)) {
            configuredSearchIncludesWrongModel = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("failBrowserQueryReread".equals(method)) {
            failBrowserQueryReread = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("pretagSuspendedArchived".equals(method)) {
            suspendedTags = "leech kani_archived";
            result.putBoolean("ok", true);
            return result;
        }
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        rejectExplicitIdProjection(projection);
        if ("/models".equals(path)) {
            if (operationCanceledProviderFailure) {
                throw new OperationCanceledException("fake provider timed out");
            }
            if (securityProviderFailure) {
                throw new SecurityException("fake provider denied access");
            }
            if (permanentProviderFailure) {
                throw new IllegalStateException("model metadata cursor failed");
            }
            if (retryableProviderFailure) {
                throw new IllegalStateException("database locked");
            }
            if (nullModelsCursor) {
                return null;
            }
            MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "name", "field_names"});
            cursor.addRow(new Object[]{100L, "Kiku", fields("Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort", "Glossary")});
            cursor.addRow(new Object[]{200L, "Custom Japanese", fields("Front", "Reading", "Back", "Example", "Frequency", "FrequencySort")});
            return cursor;
        }
        if ("/notes".equals(path) && nullConfiguredSearchCursor && isConfiguredModelSearch(selection)) {
            return null;
        }
        if ("/notes_v2".equals(path) && nullSqlNotesCursor) {
            return null;
        }
        if ("/notes".equals(path) && failConfiguredSearch && isConfiguredModelSearch(selection)) {
            throw new IllegalArgumentException("model search failed");
        }
        if ("/notes".equals(path) || "/notes_v2".equals(path)) {
            return notes(selection);
        }
        if (NOTES_ID_PATH.matcher(path).matches()) {
            return noteById(Long.parseLong(uri.getLastPathSegment()), projection);
        }
        if (NOTES_CARDS_PATH.matcher(path).matches()) {
            if (nullCardCursor) {
                return null;
            }
            if (rejectAllCardProjections) {
                cardProjectionRejects++;
                throw new IllegalArgumentException("card projection exhausted for " + firstProjectionColumn(projection));
            }
            rejectFsrsProjection(projection);
            Cursor rejected = rejectedSchedulerProjectionCursor(uri, projection);
            if (rejected != null) {
                return rejected;
            }
            perNoteCardsQueries++;
            long noteId = Long.parseLong(uri.getPathSegments().get(1));
            String[] columns = projection == null ? new String[]{"_id", "note_id", "ord", "deck_id", "card_name"} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            if (noteId == 1L || noteId == 101L) {
                addCardRow(
                        cursor,
                        columns,
                        noteId == 101L ? 110L : 10L,
                        noteId,
                        secondTemplateCard && noteId == 1L ? 1 : 0,
                        "Kiku",
                        "Mining",
                        2,
                        2,
                        12,
                        42,
                        80,
                        3,
                        12.5,
                        7.0,
                        0.42
                );
            } else if (noteId == 2L || noteId == 102L) {
                addCardRow(
                        cursor,
                        columns,
                        noteId == 102L ? 120L : 20L,
                        noteId,
                        0,
                        "Kiku",
                        "Mining",
                        -1,
                        2,
                        0,
                        10,
                        5,
                        1,
                        null,
                        null,
                        null
                );
                if (partiallySuspendedNote) {
                    addCardRow(
                            cursor,
                            columns,
                            21L,
                            noteId,
                            0,
                            "Kiku",
                            "Mining",
                            2,
                            2,
                            4,
                            5,
                            6,
                            0,
                            null,
                            null,
                            null
                    );
                }
            } else if (noteId == 3L) {
                addCardRow(
                        cursor,
                        columns,
                        30L,
                        noteId,
                        0,
                        "Kiku",
                        "Mining",
                        2,
                        2,
                        4,
                        5,
                        6,
                        0,
                        null,
                        null,
                        null
                );
            }
            return cursor;
        }
        if ("/cards".equals(path)) {
            topLevelCardsQueries++;
            throw new UnsupportedOperationException("uri " + uri + " is not supported; raw card queries expose unsupported scheduler columns");
        }
        return null;
    }

    private static String firstProjectionColumn(String[] projection) {
        if (projection == null || projection.length == 0) {
            return "<none>";
        }
        return projection[0];
    }

    private void rejectExplicitIdProjection(String[] projection) {
        if (projection == null) {
            return;
        }
        for (String column : projection) {
            if ("_id".equals(column)) {
                explicitIdProjectionQueries++;
                throw new IllegalArgumentException("_id is unknown");
            }
        }
    }

    private void rejectFsrsProjection(String[] projection) {
        if (!rejectFsrsProjection || projection == null) {
            return;
        }
        for (String column : projection) {
            if ("fsrs_stability".equals(column)
                    || "fsrs_difficulty".equals(column)
                    || "fsrs_retrievability".equals(column)
                    || "stability".equals(column)
                    || "difficulty".equals(column)
                    || "retrievability".equals(column)
                    || "data".equals(column)) {
                fsrsProjectionRejects++;
                throw new IllegalArgumentException(column + " is not part of this fake provider");
            }
        }
    }

    private Cursor rejectedSchedulerProjectionCursor(Uri uri, String[] projection) {
        if (!rejectSchedulerProjection || projection == null) {
            return null;
        }
        for (String column : projection) {
            if ("queue".equals(column)
                    || "type".equals(column)
                    || "due".equals(column)
                    || "interval".equals(column)
                    || "reps".equals(column)
                    || "lapses".equals(column)) {
                schedulerProjectionRejects++;
                if (deferSchedulerProjectionFailure) {
                    perNoteCardsQueries++;
                    return new ThrowingCursor(projection, "Queue \"" + column + "\" is unknown");
                }
                throw new IllegalArgumentException(column + " is not part of the public card projection for " + uri);
            }
        }
        return null;
    }

    private Cursor notes(String selection) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "mid", "flds", "tags"});
        boolean suspendedOnly = selection != null && selection.contains("is:suspended");
        boolean browserQuery = selection != null && selection.contains("(") && !suspendedOnly;
        if (suspendedOnly && nullSuspendedSearchCursor) {
            return null;
        }
        if (suspendedOnly && failSuspendedSearch) {
            throw new IllegalArgumentException("queue _id is unknown");
        }
        if (browserQuery && failBrowserQuery) {
            throw new IllegalArgumentException("Invalid search: malformed browser query");
        }
        boolean custom = selection != null && selection.contains("Custom Japanese");
        if (custom) {
            if (!suspendedOnly) {
                cursor.addRow(new Object[]{101L, 200L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100"), activeTags});
            }
            cursor.addRow(new Object[]{102L, 200L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500"), suspendedTags});
            return cursor;
        }
        if (browserQuery) {
            browserQueryQueries++;
            if (failBrowserQueryReread && browserQueryQueries > 1) {
                throw new IllegalArgumentException("second browser query failed");
            }
            if (nullBrowserQueryCursor) {
                return null;
            }
            if (browserQueryWrongModel) {
                cursor.addRow(new Object[]{1L, 999L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeat("active-glossary", 200)), activeTags});
            }
            if (browserQueryMatchesActive) {
                cursor.addRow(new Object[]{1L, 100L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeat("active-glossary", 200)), activeTags});
            }
            if (browserQueryMatchesSuspended) {
                cursor.addRow(new Object[]{2L, 100L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500", repeat("suspended-glossary", 200)), suspendedTags});
            }
            if (browserQueryMatchesMissingNote) {
                cursor.addRow(new Object[]{3L, 100L, fields("認", "みとめる", "recognize", "認めた。", "200", "200", repeat("missing-glossary", 200)), ""});
            }
            return cursor;
        }
        if (!suspendedOnly) {
            if (configuredSearchIncludesWrongModel) {
                cursor.addRow(new Object[]{999L, 999L, fields("無視", "むし", "ignored", "無視した。", "10", "10", "ignored"), activeTags});
            }
            cursor.addRow(new Object[]{1L, 100L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeat("active-glossary", 200)), activeTags});
        }
        cursor.addRow(new Object[]{2L, 100L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500", repeat("suspended-glossary", 200)), suspendedTags});
        return cursor;
    }

    private Cursor noteById(long noteId, String[] projection) {
        if (nullNoteCursor) {
            return null;
        }
        String[] columns = projection == null ? new String[]{"_id", "mid", "flds", "tags"} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        if (noteId == 1L) {
            addNoteRow(cursor, columns, 1L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeat("active-glossary", 200)), activeTags);
        } else if (noteId == 2L) {
            addNoteRow(cursor, columns, 2L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500", repeat("suspended-glossary", 200)), suspendedTags);
        } else if (noteId == 3L) {
            addNoteRow(cursor, columns, 3L, fields("認", "みとめる", "recognize", "認めた。", "200", "200", repeat("missing-glossary", 200)), "");
        }
        return cursor;
    }

    private void addNoteRow(MatrixCursor cursor, String[] columns, long noteId, String fields, String tags) {
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if ("_id".equals(columns[i])) {
                row[i] = noteId;
            } else if ("mid".equals(columns[i])) {
                row[i] = 100L;
            } else if ("flds".equals(columns[i])) {
                row[i] = fields;
            } else if ("tags".equals(columns[i])) {
                row[i] = tags;
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
    }

    private void addCardRow(
            MatrixCursor cursor,
            String[] columns,
            long cardId,
            long noteId,
            int ord,
            String deckId,
            String cardName,
            int queue,
            int type,
            int due,
            int interval,
            int reps,
            int lapses,
            Double fsrsStability,
            Double fsrsDifficulty,
            Double fsrsRetrievability
    ) {
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if ("_id".equals(columns[i])) {
                row[i] = cardId;
            } else if ("note_id".equals(columns[i])) {
                row[i] = noteId;
            } else if ("ord".equals(columns[i])) {
                row[i] = ord;
            } else if ("deck_id".equals(columns[i])) {
                row[i] = deckId;
            } else if ("card_name".equals(columns[i])) {
                row[i] = cardName;
            } else if ("queue".equals(columns[i])) {
                row[i] = queue;
            } else if ("type".equals(columns[i])) {
                row[i] = type;
            } else if ("due".equals(columns[i])) {
                row[i] = due;
            } else if ("interval".equals(columns[i])) {
                row[i] = interval;
            } else if ("reps".equals(columns[i])) {
                row[i] = reps;
            } else if ("lapses".equals(columns[i])) {
                row[i] = lapses;
            } else if ("fsrs_stability".equals(columns[i]) || "stability".equals(columns[i])) {
                row[i] = unparseableFsrsData || dataOnlyFsrs ? null : fsrsStability;
            } else if ("fsrs_difficulty".equals(columns[i]) || "difficulty".equals(columns[i])) {
                row[i] = unparseableFsrsData || dataOnlyFsrs ? null : fsrsDifficulty;
            } else if ("fsrs_retrievability".equals(columns[i]) || "retrievability".equals(columns[i])) {
                row[i] = unparseableFsrsData || dataOnlyFsrs ? null : fsrsRetrievability;
            } else if ("data".equals(columns[i])) {
                row[i] = unparseableFsrsData ? "{memory:'later'}" : "stability=12.5,difficulty=7.0,retrievability=0.42";
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!NOTES_ID_PATH.matcher(path).matches()) {
            return 0;
        }
        long noteId = Long.parseLong(uri.getLastPathSegment());
        String tags = values == null ? "" : values.getAsString("tags");
        if (noteId == 1L) {
            activeTags = tags == null ? "" : tags;
            return 1;
        }
        if (noteId == 2L) {
            suspendedTags = tags == null ? "" : tags;
            return 1;
        }
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete not supported by fake AnkiDroid");
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.com.ichi2.anki";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    private static String fields(String... values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(FIELD_SEPARATOR);
            }
            out.append(values[i]);
        }
        return out.toString();
    }

    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            out.append(value);
        }
        return out.toString();
    }

    private static boolean isConfiguredModelSearch(String selection) {
        return selection != null
                && selection.contains("note:\"")
                && !selection.contains("is:suspended")
                && !selection.contains("(");
    }

    private static final class ThrowingCursor extends MatrixCursor {
        private final RuntimeException error;

        private ThrowingCursor(String[] columns, String message) {
            super(columns);
            this.error = new IllegalArgumentException(message);
            addRow(new Object[columns.length]);
        }

        @Override
        public boolean onMove(int oldPosition, int newPosition) {
            throw error;
        }
    }
}
