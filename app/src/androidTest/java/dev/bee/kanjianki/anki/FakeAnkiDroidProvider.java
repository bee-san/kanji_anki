package dev.bee.kanjianki.anki;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

public final class FakeAnkiDroidProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.bee.kanjianki.test.ankidroid";
    private static final char FIELD_SEPARATOR = '\u001f';

    public static int topLevelCardsQueries;
    public static int perNoteCardsQueries;
    public static int explicitIdProjectionQueries;
    public static int schedulerProjectionRejects;
    public static String activeTags = "";
    public static String suspendedTags = "";
    public static boolean failSuspendedSearch;
    public static boolean rejectSchedulerProjection;

    public static void reset() {
        topLevelCardsQueries = 0;
        perNoteCardsQueries = 0;
        explicitIdProjectionQueries = 0;
        schedulerProjectionRejects = 0;
        activeTags = "";
        suspendedTags = "";
        failSuspendedSearch = false;
        rejectSchedulerProjection = false;
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
        if ("failSuspendedSearch".equals(method)) {
            failSuspendedSearch = true;
            result.putBoolean("ok", true);
            return result;
        }
        if ("rejectSchedulerProjection".equals(method)) {
            rejectSchedulerProjection = true;
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
            MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "name", "field_names"});
            cursor.addRow(new Object[]{100L, "Kiku", fields("Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort", "Glossary")});
            return cursor;
        }
        if ("/notes".equals(path) || "/notes_v2".equals(path)) {
            return notes(selection);
        }
        if (path.matches("/notes/\\d+/cards")) {
            rejectRawSchedulerProjection(uri, projection);
            perNoteCardsQueries++;
            long noteId = Long.parseLong(uri.getPathSegments().get(1));
            String[] columns = projection == null ? new String[]{"_id", "note_id", "ord", "deck_id", "card_name"} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            if (noteId == 1L) {
                addCardRow(cursor, columns, 10L, 1L, 0, "Kiku", "Mining", 2, 2, 12, 42, 80, 3);
            } else if (noteId == 2L) {
                addCardRow(cursor, columns, 20L, 2L, 0, "Kiku", "Mining", -1, 2, 0, 10, 5, 1);
            }
            return cursor;
        }
        if ("/cards".equals(path)) {
            topLevelCardsQueries++;
            throw new UnsupportedOperationException("uri " + uri + " is not supported; raw card queries expose unsupported scheduler columns");
        }
        return null;
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

    private void rejectRawSchedulerProjection(Uri uri, String[] projection) {
        if (!rejectSchedulerProjection || projection == null) {
            return;
        }
        for (String column : projection) {
            if ("queue".equals(column)
                    || "type".equals(column)
                    || "due".equals(column)
                    || "interval".equals(column)
                    || "reps".equals(column)
                    || "lapses".equals(column)) {
                schedulerProjectionRejects++;
                throw new IllegalArgumentException(column + " is not part of the public card projection for " + uri);
            }
        }
    }

    private Cursor notes(String selection) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "mid", "flds", "tags"});
        boolean suspendedOnly = selection != null && selection.contains("is:suspended");
        if (suspendedOnly && failSuspendedSearch) {
            throw new IllegalArgumentException("queue _id is unknown");
        }
        if (!suspendedOnly) {
            cursor.addRow(new Object[]{1L, 100L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeat("active-glossary", 200)), activeTags});
        }
        cursor.addRow(new Object[]{2L, 100L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500", repeat("suspended-glossary", 200)), suspendedTags});
        return cursor;
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
            int lapses
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
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.matches("/notes/\\d+")) {
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
}
