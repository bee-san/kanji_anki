package dev.bee.kanjianki.anki;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public final class FakeAnkiDroidProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.bee.kanjianki.test.ankidroid";
    private static final char FIELD_SEPARATOR = '\u001f';

    public static int topLevelCardsQueries;
    public static int perNoteCardsQueries;
    public static String activeTags = "";
    public static String suspendedTags = "";

    public static void reset() {
        topLevelCardsQueries = 0;
        perNoteCardsQueries = 0;
        activeTags = "";
        suspendedTags = "";
    }

    @Override
    public boolean onCreate() {
        reset();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if ("/models".equals(path)) {
            MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "name", "field_names"});
            cursor.addRow(new Object[]{100L, "Kiku", fields("Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort", "Glossary")});
            return cursor;
        }
        if ("/notes".equals(path) || "/notes_v2".equals(path)) {
            return notes(selection);
        }
        if (path.matches("/notes/\\d+/cards")) {
            perNoteCardsQueries++;
            long noteId = Long.parseLong(uri.getPathSegments().get(1));
            MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "note_id", "ord", "deck_id", "card_name"});
            if (noteId == 1L) {
                cursor.addRow(new Object[]{10L, 1L, 0, "Kiku", "Mining"});
            } else if (noteId == 2L) {
                cursor.addRow(new Object[]{20L, 2L, 0, "Kiku", "Mining"});
            }
            return cursor;
        }
        if ("/cards".equals(path)) {
            topLevelCardsQueries++;
            throw new UnsupportedOperationException("uri " + uri + " is not supported");
        }
        return null;
    }

    private Cursor notes(String selection) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "mid", "flds", "tags"});
        boolean suspendedOnly = selection != null && selection.contains("is:suspended");
        if (!suspendedOnly) {
            cursor.addRow(new Object[]{1L, 100L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeat("active-glossary", 200)), activeTags});
        }
        cursor.addRow(new Object[]{2L, 100L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500", repeat("suspended-glossary", 200)), suspendedTags});
        return cursor;
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
