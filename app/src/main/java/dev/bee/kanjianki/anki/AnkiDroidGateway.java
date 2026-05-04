package dev.bee.kanjianki.anki;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.OperationCanceledException;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SyncValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnkiDroidGateway implements CollectionGateway {
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final String ARCHIVED_TAG = "kanji_anki_archived";
    private static final String[] CARD_COLUMNS_WITH_SCHEDULER = {
            "note_id",
            "ord",
            "deck_id",
            "queue",
            "type",
            "due",
            "interval",
            "reps",
            "lapses"
    };
    private static final String[] CARD_COLUMNS_MINIMAL = {"note_id", "ord", "deck_id"};

    private final Context context;
    private final ContentResolver resolver;
    private final List<ProviderTarget> providerTargets;

    public AnkiDroidGateway(Context context) {
        this(context, ProviderTarget.TARGETS);
    }

    private AnkiDroidGateway(Context context, List<ProviderTarget> providerTargets) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        this.providerTargets = providerTargets;
    }

    public static AnkiDroidGateway testProvider(Context context, String authority) {
        return new AnkiDroidGateway(context, Collections.singletonList(new ProviderTarget(authority, null)));
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
            Map<Long, Records.Note> notes = queryNotes(target, mapping, settings);
            List<Records.Card> cards = queryCards(target, settings, notes.keySet());
            validateTemplateCards(cards, settings);
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

        int tagged = 0;
        int failed = 0;
        Set<Long> notesToTag = new LinkedHashSet<>();
        for (Records.Card card : suspendedCards) {
            if (cardsByNote.get(card.noteId).equals(suspendedByNote.get(card.noteId))) {
                notesToTag.add(card.noteId);
            } else {
                failed++;
            }
        }

        for (Long noteId : notesToTag) {
            if (tagNoteArchived(target, noteId)) {
                tagged++;
            } else {
                failed++;
            }
        }
        String message;
        if (tagged > 0 && failed == 0) {
            message = "Archived suspended notes were tagged in AnkiDroid and hidden from future syncs.";
        } else if (tagged > 0) {
            message = "Archived suspended notes were partly tagged in AnkiDroid; any leftovers stay in the local archive.";
        } else {
            message = "Archived suspended cards were kept in the local archive; AnkiDroid did not allow provider tagging.";
        }
        return new RemovalSummary(suspendedCards.size(), 0, tagged, message);
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
        for (ProviderTarget target : providerTargets) {
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
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private ModelMapping findKikuModel(ProviderTarget target, Records.Settings settings) throws SyncException {
        Cursor cursor = resolver.query(uriFor(target.authority, "models"), null, null, null, null);
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

    private Map<Long, Records.Note> queryNotes(ProviderTarget target, ModelMapping mapping, Records.Settings settings) throws SyncException {
        try {
            return queryNotesBySearch(target, mapping, settings, "note:\"" + settings.modelName + "\"");
        } catch (Throwable ignored) {
            return queryNotesBySql(target, mapping, settings);
        }
    }

    private Map<Long, Records.Note> queryNotesBySearch(ProviderTarget target, ModelMapping mapping, Records.Settings settings, String search) throws SyncException {
        Map<Long, Records.Note> notes = new LinkedHashMap<>();
        Cursor cursor = resolver.query(
                uriFor(target.authority, "notes"),
                null,
                search,
                null,
                null
        );
        if (cursor == null) {
            throw SyncException.retryable("AnkiDroid returned no Kiku note cursor.");
        }
        try {
            while (cursor.moveToNext()) {
                long noteId = longValue(cursor, "_id", 0);
                long modelId = longValue(cursor, "mid", mapping.modelId);
                if (modelId != mapping.modelId) {
                    continue;
                }
                addNoteFromCursor(notes, noteId, cursor, mapping, settings);
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    private Map<Long, Records.Note> queryNotesBySql(ProviderTarget target, ModelMapping mapping, Records.Settings settings) throws SyncException {
        Map<Long, Records.Note> notes = new LinkedHashMap<>();
        Cursor cursor = resolver.query(
                uriFor(target.authority, "notes_v2"),
                null,
                "mid=?",
                new String[]{Long.toString(mapping.modelId)},
                null
        );
        if (cursor == null) {
            throw SyncException.retryable("AnkiDroid returned no Kiku note cursor.");
        }
        try {
            while (cursor.moveToNext()) {
                addNoteFromCursor(notes, longValue(cursor, "_id", 0), cursor, mapping, settings);
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    private void addNoteFromCursor(Map<Long, Records.Note> notes, long noteId, Cursor cursor, ModelMapping mapping, Records.Settings settings) {
        List<String> values = splitFields(value(cursor, "flds"));
        Map<String, String> fieldMap = selectedFields(mapping, values, settings);
        List<String> tags = splitTags(value(cursor, "tags"));
        if (!tags.contains(ARCHIVED_TAG)) {
            notes.put(noteId, new Records.Note(noteId, mapping.name, fieldMap, tags));
        }
    }

    private List<Records.Card> queryCards(ProviderTarget target, Records.Settings settings, Set<Long> noteIds) throws SyncException {
        return queryCardsByNote(target, settings, noteIds);
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

    private List<Records.Card> queryCardsByNote(ProviderTarget target, Records.Settings settings, Set<Long> noteIds) throws SyncException {
        Set<Long> suspendedNoteIds = querySuspendedNoteIds(target, settings);
        List<Records.Card> cards = new ArrayList<>();
        for (Long noteId : noteIds) {
            try {
                cards.addAll(queryCardsForNote(target, noteId, suspendedNoteIds, CARD_COLUMNS_WITH_SCHEDULER));
            } catch (Throwable unsupportedSchedulerColumns) {
                cards.addAll(queryCardsForNote(target, noteId, suspendedNoteIds, CARD_COLUMNS_MINIMAL));
            }
        }
        return cards;
    }

    private List<Records.Card> queryCardsForNote(ProviderTarget target, long noteId, Set<Long> suspendedNoteIds, String[] columns) throws SyncException {
        Cursor cursor = resolver.query(uriFor(target.authority, "notes", Long.toString(noteId), "cards"), columns, null, null, null);
        if (cursor == null) {
            throw SyncException.retryable("AnkiDroid returned no per-note card cursor.");
        }
        List<Records.Card> cards = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                int ord = intValue(cursor, "ord", 0);
                boolean suspendedFromSearch = suspendedNoteIds.contains(noteId);
                int queue = intValue(cursor, "queue", suspendedFromSearch ? -1 : 0);
                boolean suspended = suspendedFromSearch || queue < 0;
                cards.add(new Records.Card(
                        longValue(cursor, "_id", noteId * 1000L + ord),
                        longValue(cursor, "note_id", noteId),
                        ord,
                        value(cursor, "deck_id"),
                        queue,
                        intValue(cursor, "type", suspended ? 3 : 0),
                        intValue(cursor, "due", 0),
                        intValue(cursor, "interval", 0),
                        intValue(cursor, "reps", 0),
                        intValue(cursor, "lapses", 0),
                        suspended
                ));
            }
            return cards;
        } finally {
            cursor.close();
        }
    }

    private Set<Long> querySuspendedNoteIds(ProviderTarget target, Records.Settings settings) {
        Set<Long> ids = new LinkedHashSet<>();
        Cursor cursor;
        try {
            cursor = resolver.query(
                    uriFor(target.authority, "notes"),
                    null,
                    "note:\"" + settings.modelName + "\" is:suspended",
                    null,
                    null
            );
        } catch (Throwable ignored) {
            return ids;
        }
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
                new ProviderTarget("com.ichi2.anki.api.provider", "com.ichi2.anki.permission.READ_WRITE_DATABASE"),
                new ProviderTarget("com.ichi2.anki.flashcards", "com.ichi2.anki.permission.READ_WRITE_DATABASE"),
                new ProviderTarget("com.ichi2.anki.debug.api.provider", "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE"),
                new ProviderTarget("com.ichi2.anki.debug.flashcards", "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE")
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
