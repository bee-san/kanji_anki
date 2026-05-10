package dev.bee.kanjianki.anki;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.OperationCanceledException;
import android.util.Log;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SyncValidator;
import dev.bee.kanjianki.sync.SyncProgress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnkiDroidGateway implements CollectionGateway {
    private static final String TAG = "AnkiDroidGateway";
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final String CONTENT_SCHEME = "content";
    private static final String ARCHIVED_TAG = "kani_archived";
    private static final String LEGACY_ARCHIVED_TAG = "kanji_anki_archived";
    private static final String READ_WRITE_DATABASE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE";
    private static final String DEBUG_READ_WRITE_DATABASE_PERMISSION = "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_FIELD_NAMES = "field_names";
    private static final String COLUMN_FIELDS = "flds";
    private static final String COLUMN_TAGS = "tags";
    private static final String COLUMN_MODEL_ID = "mid";
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
                        ? "AnkiDroid is ready for live note sync."
                        : "Allow AnkiDroid access so Kani can read your live collection.");
    }

    public List<NoteType> noteTypes() throws SyncFailure {
        ProviderTarget target = requireProvider();
        if (!hasPermission(target.permission)) {
            throw SyncFailure.permanent("AnkiDroid permission is missing: " + target.permission);
        }
        return queryNoteTypes(target);
    }

    @Override
    public Records.CollectionSnapshot readCollection(Records.Settings settings) throws SyncFailure {
        return readCollection(settings, SyncProgress.NONE);
    }

    @Override
    public Records.CollectionSnapshot readCollection(Records.Settings settings, SyncProgress.Listener progress) throws SyncFailure {
        SyncProgress.Listener reporter = progress == null ? SyncProgress.NONE : progress;
        ProviderTarget target = requireProvider();
        if (!hasPermission(target.permission)) {
            throw SyncFailure.permanent("AnkiDroid permission is missing: " + target.permission);
        }
        try {
            reporter.onSyncProgress(SyncProgress.stage(SyncProgress.Stage.FINDING_NOTE_TYPE));
            ModelMapping mapping = findConfiguredModel(target, settings);
            reporter.onSyncProgress(SyncProgress.stage(SyncProgress.Stage.READING_NOTES));
            Map<Long, Records.Note> notes = queryNotes(target, mapping, settings);
            List<Records.Card> cards = queryCardsByNote(target, settings, notes.keySet(), reporter);
            validateTemplateCards(cards, settings);
            cards = cardsWithNotes(cards, notes.keySet());
            return new Records.CollectionSnapshot(new ArrayList<>(notes.values()), cards);
        } catch (SyncFailure error) {
            throw error;
        } catch (OperationCanceledException error) {
            throw SyncFailure.retryable("Timed out while reading AnkiDroid.", error);
        } catch (SecurityException error) {
            throw SyncFailure.permanent("AnkiDroid denied database access.", error);
        } catch (Throwable error) {
            String kind = SyncValidator.classifyProviderFailure(error);
            if (kind.startsWith("permanent")) {
                throw SyncFailure.permanent(error.getMessage() == null ? "Permanent AnkiDroid sync error." : error.getMessage(), error);
            }
            throw SyncFailure.retryable("AnkiDroid provider read failed: " + error.getMessage(), error);
        }
    }

    @Override
    public RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot) {
        return removeArchivedSuspendedCards(snapshot, SyncProgress.NONE);
    }

    @Override
    public RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot, SyncProgress.Listener progress) {
        SyncProgress.Listener reporter = progress == null ? SyncProgress.NONE : progress;
        reporter.onSyncProgress(SyncProgress.stage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
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
        Uri noteUri = uriFor(target.authority, URI_SEGMENT_NOTES, String.valueOf(noteId));
        String tags = "";
        Cursor rawCursor = resolver.query(noteUri, new String[]{COLUMN_TAGS}, null, null, null);
        if (rawCursor != null) {
            try (Cursor cursor = rawCursor) {
                if (cursor.moveToFirst()) {
                    tags = value(cursor, COLUMN_TAGS);
                }
            }
        }
        if (!isArchivedTagPresent(Arrays.asList(tags.split("\\s+")))) {
            tags = (tags + " " + ARCHIVED_TAG).trim();
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_TAGS, tags);
        return resolver.update(noteUri, values, null, null) > 0;
    }

    private ProviderTarget requireProvider() throws SyncFailure {
        ProviderTarget target = resolveProviderTarget();
        if (target == null) {
            throw SyncFailure.permanent("AnkiDroid's flashcard provider is not installed.");
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

    private ModelMapping findConfiguredModel(ProviderTarget target, Records.Settings settings) throws SyncFailure {
        for (NoteType noteType : queryNoteTypes(target)) {
            if (!noteType.name.equalsIgnoreCase(settings.modelName)) {
                continue;
            }
            List<String> errors = SyncValidator.validateModelFields(noteType.name, noteType.fields, settings);
            if (!errors.isEmpty()) {
                throw SyncFailure.permanent(String.join("\n", errors));
            }
            return new ModelMapping(noteType.modelId, noteType.name, noteType.fields);
        }
        throw SyncFailure.permanent(settings.modelName + " note type was not found in AnkiDroid.");
    }

    private List<NoteType> queryNoteTypes(ProviderTarget target) throws SyncFailure {
        Cursor cursor = resolver.query(uriFor(target.authority, "models"), null, null, null, null);
        if (cursor == null) {
            throw SyncFailure.retryable("AnkiDroid returned no note model cursor.");
        }
        List<NoteType> noteTypes = new ArrayList<>();
        try (Cursor noteTypeCursor = cursor) {
            while (noteTypeCursor.moveToNext()) {
                String name = value(noteTypeCursor, COLUMN_NAME);
                long id = longValue(noteTypeCursor, COLUMN_ID, 0);
                List<String> fields = splitFields(value(noteTypeCursor, COLUMN_FIELD_NAMES));
                noteTypes.add(new NoteType(id, name, fields));
            }
        }
        noteTypes.sort(Comparator
                .comparing((NoteType noteType) -> !noteType.name.equalsIgnoreCase(Records.Settings.kikuDefaults().modelName))
                .thenComparing(noteType -> noteType.name.toLowerCase(Locale.ROOT)));
        return noteTypes;
    }

    private Map<Long, Records.Note> queryNotes(ProviderTarget target, ModelMapping mapping, Records.Settings settings) throws SyncFailure {
        Exception searchFailure = null;
        try {
            return queryNotesBySearch(target, mapping, settings, "note:\"" + settings.modelName + "\"");
        } catch (Exception error) {
            searchFailure = error;
        }
        try {
            return queryNotesBySql(target, mapping, settings);
        } catch (SyncFailure sqlFailure) {
            sqlFailure.addSuppressed(searchFailure);
            throw sqlFailure;
        }
    }

    private Map<Long, Records.Note> queryNotesBySearch(ProviderTarget target, ModelMapping mapping, Records.Settings settings, String search) throws SyncFailure {
        Map<Long, Records.Note> notes = new LinkedHashMap<>();
        Cursor cursor = resolver.query(
                uriFor(target.authority, URI_SEGMENT_NOTES),
                null,
                search,
                null,
                null
        );
        if (cursor == null) {
            throw SyncFailure.retryable("AnkiDroid returned no configured note cursor.");
        }
        try (Cursor noteCursor = cursor) {
            while (noteCursor.moveToNext()) {
                long noteId = longValue(noteCursor, COLUMN_ID, 0);
                long modelId = longValue(noteCursor, COLUMN_MODEL_ID, mapping.modelId);
                if (modelId != mapping.modelId) {
                    continue;
                }
                addNoteFromCursor(notes, noteId, noteCursor, mapping, settings);
            }
        }
        return notes;
    }

    private Map<Long, Records.Note> queryNotesBySql(ProviderTarget target, ModelMapping mapping, Records.Settings settings) throws SyncFailure {
        Map<Long, Records.Note> notes = new LinkedHashMap<>();
        Cursor cursor = resolver.query(
                uriFor(target.authority, "notes_v2"),
                null,
                "mid=?",
                new String[]{Long.toString(mapping.modelId)},
                null
        );
        if (cursor == null) {
            throw SyncFailure.retryable("AnkiDroid returned no configured note cursor.");
        }
        try (Cursor noteCursor = cursor) {
            while (noteCursor.moveToNext()) {
                addNoteFromCursor(notes, longValue(noteCursor, COLUMN_ID, 0), noteCursor, mapping, settings);
            }
        }
        return notes;
    }

    private void addNoteFromCursor(Map<Long, Records.Note> notes, long noteId, Cursor cursor, ModelMapping mapping, Records.Settings settings) {
        List<String> values = splitFields(value(cursor, COLUMN_FIELDS));
        Map<String, String> fieldMap = selectRequiredFields(mapping.fields, values, settings);
        List<String> tags = splitTags(value(cursor, COLUMN_TAGS));
        if (!isArchivedTagPresent(tags)) {
            notes.put(noteId, new Records.Note(noteId, mapping.modelId, mapping.name, fieldMap, tags));
        }
    }

    private static boolean isArchivedTagPresent(List<String> tags) {
        return tags.contains(ARCHIVED_TAG) || tags.contains(LEGACY_ARCHIVED_TAG);
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

    private void validateTemplateCards(List<Records.Card> cards, Records.Settings settings) throws SyncFailure {
        for (Records.Card card : cards) {
            if (card.ord != 0) {
                throw SyncFailure.permanent(settings.modelName + " has card template ord " + card.ord + ". This app supports only the first card template at ord 0.");
            }
        }
    }

    private List<Records.Card> queryCardsByNote(ProviderTarget target, Records.Settings settings, Set<Long> noteIds, SyncProgress.Listener progress) throws SyncFailure {
        int total = noteIds.size();
        int scanned = 0;
        progress.onSyncProgress(SyncProgress.cardsScanned(scanned, total));
        Set<Long> suspendedNoteIds = querySuspendedNoteIds(target, settings);
        List<Records.Card> cards = new ArrayList<>();
        String[][] projections = new String[][]{
                CARD_COLUMNS_WITH_FSRS,
                CARD_COLUMNS_WITH_SCHEDULER,
                CARD_COLUMNS_MINIMAL
        };
        int projectionIndex = 0;
        for (Long noteId : noteIds) {
            boolean read = false;
            while (!read && projectionIndex < projections.length) {
                try {
                    cards.addAll(queryCardsForNote(target, noteId, suspendedNoteIds, projections[projectionIndex]));
                    scanned++;
                    if (shouldReportCardProgress(scanned, total)) {
                        progress.onSyncProgress(SyncProgress.cardsScanned(scanned, total));
                    }
                    read = true;
                } catch (Throwable unsupportedColumns) {
                    projectionIndex++;
                    if (projectionIndex >= projections.length) {
                        if (unsupportedColumns instanceof SyncFailure syncFailure) {
                            throw syncFailure;
                        }
                        throw SyncFailure.retryable("AnkiDroid card projection failed: " + unsupportedColumns.getMessage(), unsupportedColumns);
                    }
                }
            }
        }
        return cards;
    }

    private boolean shouldReportCardProgress(int scanned, int total) {
        if (scanned <= 0 || scanned == total || total <= 100) {
            return true;
        }
        if (scanned <= 10) {
            return true;
        }
        return scanned % (total <= 1000 ? 10 : 50) == 0;
    }

    private List<Records.Card> queryCardsForNote(ProviderTarget target, long noteId, Set<Long> suspendedNoteIds, String[] columns) throws SyncFailure {
        Cursor cursor = resolver.query(uriFor(target.authority, URI_SEGMENT_NOTES, Long.toString(noteId), "cards"), columns, null, null, null);
        if (cursor == null) {
            throw SyncFailure.retryable("AnkiDroid returned no per-note card cursor.");
        }
        List<Records.Card> cards = new ArrayList<>();
        try (Cursor cardCursor = cursor) {
            while (cardCursor.moveToNext()) {
                int ord = intValue(cardCursor, COLUMN_ORD, 0);
                boolean suspendedFromSearch = suspendedNoteIds.contains(noteId);
                int queue = intValue(cardCursor, COLUMN_QUEUE, suspendedFromSearch ? -1 : 0);
                boolean suspended = suspendedFromSearch || queue < 0;
                FsrsMemoryState fsrs = fsrsMemoryState(cardCursor);
                String deckId = value(cardCursor, COLUMN_DECK_ID);
                cards.add(new Records.Card(
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
                        fsrs.stability,
                        fsrs.difficulty,
                        fsrs.retrievability
                ));
            }
            return cards;
        }
    }

    private Set<Long> querySuspendedNoteIds(ProviderTarget target, Records.Settings settings) {
        Set<Long> ids = new LinkedHashSet<>();
        Cursor cursor;
        try {
            cursor = resolver.query(
                    uriFor(target.authority, URI_SEGMENT_NOTES),
                    null,
                    "note:\"" + settings.modelName + "\" is:suspended",
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
            } else if (COLUMN_RETRIEVABILITY.equals(key) || "r".equals(key)) {
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
        return Double.isNaN(parsed) || Double.isInfinite(parsed) ? null : parsed;
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
                new ProviderTarget("com.ichi2.anki.api.provider", READ_WRITE_DATABASE_PERMISSION),
                new ProviderTarget("com.ichi2.anki.flashcards", READ_WRITE_DATABASE_PERMISSION),
                new ProviderTarget("com.ichi2.anki.debug.api.provider", DEBUG_READ_WRITE_DATABASE_PERMISSION),
                new ProviderTarget("com.ichi2.anki.debug.flashcards", DEBUG_READ_WRITE_DATABASE_PERMISSION)
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

    public static final class NoteType {
        public final long modelId;
        public final String name;
        public final List<String> fields;

        private NoteType(long modelId, String name, List<String> fields) {
            this.modelId = modelId;
            this.name = name == null ? "" : name;
            this.fields = Collections.unmodifiableList(new ArrayList<>(fields == null ? Collections.emptyList() : fields));
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

    public static final class SyncFailure extends Exception {
        private static final long serialVersionUID = 1L;

        public final boolean permanentFailure;

        private SyncFailure(String message, boolean permanent, Throwable cause) {
            super(message, cause);
            this.permanentFailure = permanent;
        }

        public static SyncFailure permanent(String message) {
            return new SyncFailure(message, true, null);
        }

        public static SyncFailure permanent(String message, Throwable cause) {
            return new SyncFailure(message, true, cause);
        }

        public static SyncFailure retryable(String message, Throwable cause) {
            return new SyncFailure(message, false, cause);
        }

        public static SyncFailure retryable(String message) {
            return new SyncFailure(message, false, null);
        }
    }
}
