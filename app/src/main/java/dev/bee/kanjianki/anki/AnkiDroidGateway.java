package dev.bee.kanjianki.anki;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SyncValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnkiDroidGateway implements CollectionGateway {
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final String ARCHIVED_TAG = "kanji_anki_archived";

    private final Context context;
    private final ContentResolver resolver;

    public AnkiDroidGateway(Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
    }

    public ProviderStatus status() {
        ProviderTarget target = resolveProviderTarget();
        if (target == null) {
            return new ProviderStatus(false, false, false, null, null, "Install AnkiDroid and enable its API/database permission.");
        }
        boolean granted = hasPermission(target.permission);
        return new ProviderStatus(true, granted, granted, target.authority, target.permission,
                granted
                        ? "AnkiDroid is ready for live Kiku sync."
                        : "Grant " + target.permission + " so Kanji Anki can read the live collection.");
    }

    public Records.CollectionSnapshot readCollection(Records.Settings settings) throws SyncException {
        ProviderTarget target = requireProvider();
        if (!hasPermission(target.permission)) {
            throw SyncException.permanent("AnkiDroid permission is missing: " + target.permission);
        }
        try {
            ModelMapping mapping = findKikuModel(target, settings);
            List<Records.Card> cards = queryCards(target, settings);
            validateTemplateCards(cards, settings);
            Map<Long, Records.Note> notes = queryNotes(target, mapping, noteIds(cards), settings);
            cards = cardsWithNotes(cards, notes.keySet());
            return new Records.CollectionSnapshot(new ArrayList<>(notes.values()), cards);
        } catch (SyncException error) {
            throw error;
        } catch (OperationCanceledException error) {
            throw SyncException.retryable("Timed out while reading AnkiDroid.", error);
        } catch (SecurityException error) {
            throw SyncException.permanent("AnkiDroid denied database access.", error);
        } catch (Throwable error) {
            String kind = SyncValidator.classifyProviderFailure(error);
            if (kind.startsWith("permanent")) {
                throw SyncException.permanent(error.getMessage() == null ? "Permanent AnkiDroid sync error." : error.getMessage(), error);
            }
            throw SyncException.retryable("AnkiDroid provider read failed: " + error.getMessage(), error);
        }
    }

    public RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot) {
        ProviderTarget target = resolveProviderTarget();
        if (target == null || snapshot.cards.isEmpty()) {
            return new RemovalSummary(0, 0, 0, "No provider removal attempted.");
        }
        Map<Long, Integer> cardsByNote = new LinkedHashMap<>();
        Map<Long, Integer> suspendedByNote = new LinkedHashMap<>();
        List<Records.Card> suspendedCards = new ArrayList<>();
        for (Records.Card card : snapshot.cards) {
            cardsByNote.put(card.noteId, cardsByNote.getOrDefault(card.noteId, 0) + 1);
            if (card.suspended) {
                suspendedCards.add(card);
                suspendedByNote.put(card.noteId, suspendedByNote.getOrDefault(card.noteId, 0) + 1);
            }
        }
        if (suspendedCards.isEmpty()) {
            return new RemovalSummary(0, 0, 0, "No suspended cards needed provider cleanup.");
        }

        int deletedCards = 0;
        int deletedNotes = 0;
        int tagged = 0;
        int failed = 0;
        Set<Long> notesToTag = new LinkedHashSet<>();
        for (Records.Card card : suspendedCards) {
            try {
                int count = resolver.delete(uriFor(target.authority, "cards", String.valueOf(card.cardId)), null, null);
                if (count > 0) {
                    deletedCards += count;
                    continue;
                }
            } catch (Throwable ignored) {
                // Fall through to note-level cleanup when it is safe.
            }
            if (cardsByNote.get(card.noteId).equals(suspendedByNote.get(card.noteId))) {
                try {
                    int noteDeleteCount = resolver.delete(uriFor(target.authority, "notes", String.valueOf(card.noteId)), null, null);
                    if (noteDeleteCount > 0) {
                        deletedNotes += noteDeleteCount;
                        continue;
                    }
                } catch (Throwable ignored) {
                    // Fall through to tag fallback.
                }
                notesToTag.add(card.noteId);
            }
            failed++;
        }

        for (Long noteId : notesToTag) {
            if (tagNoteArchived(target, noteId)) {
                tagged++;
            }
        }
        int totalDeleted = deletedCards + deletedNotes;
        String message;
        if (failed == 0) {
            message = "Archived suspended cards were removed from AnkiDroid.";
        } else if (totalDeleted > 0) {
            message = "Archived suspended cards were partly removed; tagged leftovers will be ignored on future syncs.";
        } else {
            message = "Archived suspended cards were tagged locally but AnkiDroid did not allow provider deletion.";
        }
        return new RemovalSummary(suspendedCards.size(), deletedNotes, tagged, message);
    }

    private boolean tagNoteArchived(ProviderTarget target, long noteId) {
        Uri noteUri = uriFor(target.authority, "notes", String.valueOf(noteId));
        String tags = "";
        Cursor cursor = resolver.query(noteUri, new String[]{"tags"}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    tags = value(cursor, "tags");
                }
            } finally {
                cursor.close();
            }
        }
        if (!Arrays.asList(tags.split("\\s+")).contains(ARCHIVED_TAG)) {
            tags = (tags + " " + ARCHIVED_TAG).trim();
        }
        ContentValues values = new ContentValues();
        values.put("tags", tags);
        return resolver.update(noteUri, values, null, null) > 0;
    }

    private ProviderTarget requireProvider() throws SyncException {
        ProviderTarget target = resolveProviderTarget();
        if (target == null) {
            throw SyncException.permanent("AnkiDroid's flashcard provider is not installed.");
        }
        return target;
    }

    private ProviderTarget resolveProviderTarget() {
        for (ProviderTarget target : ProviderTarget.TARGETS) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (context.getPackageManager().resolveContentProvider(target.authority, PackageManager.ComponentInfoFlags.of(0)) != null) {
                    return target;
                }
            } else if (context.getPackageManager().resolveContentProvider(target.authority, 0) != null) {
                return target;
            }
        }
        return null;
    }

    private boolean hasPermission(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private ModelMapping findKikuModel(ProviderTarget target, Records.Settings settings) throws SyncException {
        Cursor cursor = resolver.query(uriFor(target.authority, "models"), new String[]{"_id", "name", "field_names"}, null, null, null);
        if (cursor == null) {
            throw SyncException.retryable("AnkiDroid returned no note model cursor.");
        }
        try {
            while (cursor.moveToNext()) {
                String name = value(cursor, "name");
                if (!name.equalsIgnoreCase(settings.modelName)) {
                    continue;
                }
                long id = longValue(cursor, "_id", 0);
                List<String> fields = splitFields(value(cursor, "field_names"));
                List<String> errors = SyncValidator.validateModelFields(name, fields, settings);
                if (!errors.isEmpty()) {
                    throw SyncException.permanent(String.join("\n", errors));
                }
                return new ModelMapping(id, name, fields);
            }
        } finally {
            cursor.close();
        }
        throw SyncException.permanent("Kiku note type was not found in AnkiDroid.");
    }

    private Map<Long, Records.Note> queryNotes(ProviderTarget target, ModelMapping mapping, Set<Long> noteIds, Records.Settings settings) throws SyncException {
        Map<Long, Records.Note> notes = new LinkedHashMap<>();
        for (Long noteId : noteIds) {
            Cursor cursor = resolver.query(
                    uriFor(target.authority, "notes_v2"),
                    new String[]{"_id", "flds", "tags"},
                    "_id=" + noteId + " AND mid=" + mapping.modelId,
                    null,
                    null
            );
            if (cursor == null) {
                throw SyncException.retryable("AnkiDroid returned no Kiku note cursor.");
            }
            try {
                if (!cursor.moveToFirst()) {
                    continue;
                }
                List<String> values = splitFields(value(cursor, "flds"));
                Map<String, String> fieldMap = selectedFields(mapping, values, settings);
                List<String> tags = splitTags(value(cursor, "tags"));
                if (!tags.contains(ARCHIVED_TAG)) {
                    notes.put(noteId, new Records.Note(noteId, mapping.name, fieldMap, tags));
                }
            } finally {
                cursor.close();
            }
        }
        return notes;
    }

    private List<Records.Card> queryCards(ProviderTarget target, Records.Settings settings) throws SyncException {
        try {
            return queryRawCards(target, settings);
        } catch (Throwable ignored) {
            return queryBasicCards(target, settings);
        }
    }

    private Map<String, String> selectedFields(ModelMapping mapping, List<String> values, Records.Settings settings) {
        return selectRequiredFields(mapping.fields, values, settings);
    }

    static Map<String, String> selectRequiredFields(List<String> modelFields, List<String> values, Records.Settings settings) {
        Map<String, String> fieldMap = new LinkedHashMap<>();
        for (String field : settings.requiredFields()) {
            int index = modelFields.indexOf(field);
            fieldMap.put(field, index >= 0 && index < values.size() ? values.get(index) : "");
        }
        return fieldMap;
    }

    private Set<Long> noteIds(List<Records.Card> cards) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Records.Card card : cards) {
            ids.add(card.noteId);
        }
        return ids;
    }

    private List<Records.Card> cardsWithNotes(List<Records.Card> cards, Set<Long> noteIds) {
        List<Records.Card> out = new ArrayList<>();
        for (Records.Card card : cards) {
            if (noteIds.contains(card.noteId)) {
                out.add(card);
            }
        }
        return out;
    }

    private void validateTemplateCards(List<Records.Card> cards, Records.Settings settings) throws SyncException {
        for (Records.Card card : cards) {
            if (card.ord != 0) {
                throw SyncException.permanent(settings.modelName + " has card template ord " + card.ord + ". This app supports the " + settings.templateName + " template at ord 0 only.");
            }
        }
    }

    private List<Records.Card> queryRawCards(ProviderTarget target, Records.Settings settings) throws SyncException {
        List<Records.Card> cards = new ArrayList<>();
        Cursor cursor = resolver.query(
                uriFor(target.authority, "cards"),
                new String[]{"_id", "note_id", "ord", "deck_id", "queue", "type", "due", "ivl", "reps", "lapses"},
                "note:\"" + settings.modelName + "\"",
                null,
                null
        );
        if (cursor == null) {
            throw SyncException.retryable("AnkiDroid returned no card cursor.");
        }
        try {
            while (cursor.moveToNext()) {
                int queue = intValue(cursor, "queue", 0);
                cards.add(new Records.Card(
                        longValue(cursor, "_id", 0),
                        longValue(cursor, "note_id", 0),
                        intValue(cursor, "ord", 0),
                        value(cursor, "deck_id"),
                        queue,
                        intValue(cursor, "type", 0),
                        intValue(cursor, "due", 0),
                        intValue(cursor, "ivl", 0),
                        intValue(cursor, "reps", 0),
                        intValue(cursor, "lapses", 0),
                        queue == -1
                ));
            }
        } finally {
            cursor.close();
        }
        return cards;
    }

    private List<Records.Card> queryBasicCards(ProviderTarget target, Records.Settings settings) throws SyncException {
        Set<Long> suspendedIds = queryBasicCardIds(target, "note:\"" + settings.modelName + "\" is:suspended");
        List<Records.Card> cards = new ArrayList<>();
        Cursor cursor = resolver.query(uriFor(target.authority, "cards"), null, "note:\"" + settings.modelName + "\"", null, null);
        if (cursor == null) {
            throw SyncException.retryable("AnkiDroid returned no basic card cursor.");
        }
        try {
            while (cursor.moveToNext()) {
                long cardId = longValue(cursor, "_id", 0);
                long noteId = longValue(cursor, "note_id", 0);
                boolean suspended = suspendedIds.contains(cardId);
                cards.add(new Records.Card(
                        cardId,
                        noteId,
                        intValue(cursor, "ord", 0),
                        value(cursor, "deck_id"),
                        suspended ? -1 : 0,
                        suspended ? 3 : 0,
                        0,
                        0,
                        0,
                        0,
                        suspended
                ));
            }
        } finally {
            cursor.close();
        }
        return cards;
    }

    private Set<Long> queryBasicCardIds(ProviderTarget target, String search) {
        Set<Long> ids = new LinkedHashSet<>();
        Cursor cursor = resolver.query(uriFor(target.authority, "cards"), new String[]{"_id"}, search, null, null);
        if (cursor == null) {
            return ids;
        }
        try {
            while (cursor.moveToNext()) {
                ids.add(longValue(cursor, "_id", 0));
            }
        } finally {
            cursor.close();
        }
        return ids;
    }

    private static List<String> splitFields(String value) {
        return Arrays.asList(value.split(String.valueOf(FIELD_SEPARATOR), -1));
    }

    private static List<String> splitTags(String value) {
        List<String> tags = new ArrayList<>();
        for (String tag : value.split("\\s+")) {
            if (!tag.trim().isEmpty()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private static Uri uriFor(String authority, String... segments) {
        Uri.Builder builder = new Uri.Builder().scheme("content").authority(authority);
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

    private static final class ModelMapping {
        private final long modelId;
        private final String name;
        private final List<String> fields;

        private ModelMapping(long modelId, String name, List<String> fields) {
            this.modelId = modelId;
            this.name = name;
            this.fields = fields;
        }
    }

    private static final class ProviderTarget {
        private static final List<ProviderTarget> TARGETS = Arrays.asList(
                new ProviderTarget("com.ichi2.anki.flashcards", "com.ichi2.anki.permission.READ_WRITE_DATABASE"),
                new ProviderTarget("com.ichi2.anki.api.provider", "com.ichi2.anki.permission.READ_WRITE_DATABASE"),
                new ProviderTarget("com.ichi2.anki.debug.flashcards", "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE"),
                new ProviderTarget("com.ichi2.anki.debug.api.provider", "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE")
        );

        private final String authority;
        private final String permission;

        private ProviderTarget(String authority, String permission) {
            this.authority = authority;
            this.permission = permission;
        }
    }

    public static final class ProviderStatus {
        public final boolean installed;
        public final boolean permissionGranted;
        public final boolean canSync;
        public final String authority;
        public final String permission;
        public final String message;

        private ProviderStatus(boolean installed, boolean permissionGranted, boolean canSync, String authority, String permission, String message) {
            this.installed = installed;
            this.permissionGranted = permissionGranted;
            this.canSync = canSync;
            this.authority = authority;
            this.permission = permission;
            this.message = message;
        }
    }

    public static final class RemovalSummary {
        public final int sourceCards;
        public final int deletedNotes;
        public final int taggedNotes;
        public final String message;

        public RemovalSummary(int sourceCards, int deletedNotes, int taggedNotes, String message) {
            this.sourceCards = sourceCards;
            this.deletedNotes = deletedNotes;
            this.taggedNotes = taggedNotes;
            this.message = message;
        }
    }

    public static final class SyncException extends Exception {
        public final boolean permanent;

        private SyncException(String message, boolean permanent, Throwable cause) {
            super(message, cause);
            this.permanent = permanent;
        }

        public static SyncException permanent(String message) {
            return new SyncException(message, true, null);
        }

        public static SyncException permanent(String message, Throwable cause) {
            return new SyncException(message, true, cause);
        }

        public static SyncException retryable(String message, Throwable cause) {
            return new SyncException(message, false, cause);
        }

        public static SyncException retryable(String message) {
            return new SyncException(message, false, null);
        }
    }
}
