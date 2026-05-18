package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.OperationCanceledException;
import android.util.Log;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.RequiresApi;

import dev.bee.kanjianki.core.SyncValidator;
import dev.bee.kanjianki.syncdomain.ProviderArchiveCleanupPolicy;
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy;
import dev.bee.kanjianki.syncdomain.SyncMirrorPolicy;
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
import java.util.regex.Pattern;

public final class AnkiDroidGateway implements CollectionGateway {
    private static final String TAG = "AnkiDroidGateway";
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final String CONTENT_SCHEME = "content";
    private static final String READ_WRITE_DATABASE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE";
    private static final String DEBUG_READ_WRITE_DATABASE_PERMISSION = "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_FIELD_NAMES = "field_names";
    private static final String COLUMN_FIELDS = "flds";
    private static final String COLUMN_TAGS = "tags";
    private static final String COLUMN_MODEL_ID = "mid";
    private static final String URI_SEGMENT_NOTES = "notes";
    private static final Pattern NOTES_WHITESPACE_SEPARATOR = Pattern.compile("\\s+");

    private final Context context;
    private final ContentResolver resolver;
    private final AnkiDroidCardReader cardReader;
    private final List<ProviderTarget> providerTargets;

    public AnkiDroidGateway(Context context) {
        this(context, ProviderTarget.TARGETS);
    }

    private AnkiDroidGateway(Context context, List<ProviderTarget> providerTargets) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        this.cardReader = new AnkiDroidCardReader(this.resolver);
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
    public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) throws SyncFailure {
        return readCollection(settings, SyncProgress.NONE);
    }

    @Override
    public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings, SyncProgress.Listener progress) throws SyncFailure {
        SyncProgress.Listener reporter = progress == null ? SyncProgress.NONE : progress;
        ProviderTarget target = requireProvider();
        if (!hasPermission(target.permission)) {
            throw SyncFailure.permanent("AnkiDroid permission is missing: " + target.permission);
        }
        try {
            reporter.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE));
            ModelMapping mapping = findConfiguredModel(target, settings);
            reporter.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES));
            Map<Long, RecordsSyncModels.Note> notes = queryNotes(target, mapping, settings);
            Set<Long> browserQueryNoteIds = queryBrowserQueryNoteIds(target, mapping, settings);
            mergeMissingBrowserQueryNotes(target, mapping, settings, notes, browserQueryNoteIds);
            List<RecordsSyncModels.Card> cards = cardReader.queryCardsByNote(target.authority, settings, notes.keySet(), reporter);
            validateTemplateCards(cards, settings);
            cards = cardsWithNotes(cards, notes.keySet());
            cards = markBrowserQueryMatchedCards(cards, browserQueryNoteIds);
            return new RecordsSyncModels.CollectionSnapshot(new ArrayList<>(notes.values()), cards);
        } catch (SyncFailure error) {
            throw error;
        } catch (OperationCanceledException error) {
            throw SyncFailure.retryable("Timed out while reading AnkiDroid.", error);
        } catch (SecurityException error) {
            throw SyncFailure.permanent("AnkiDroid denied database access.", error);
        } catch (Throwable error) {
            String kind = SyncValidator.classifyProviderFailure(error);
            if (kind.startsWith("permanent")) {
                throw SyncFailure.permanent(error.getMessage(), error);
            }
            throw SyncFailure.retryable("AnkiDroid provider read failed: " + error.getMessage(), error);
        }
    }

    private void mergeMissingBrowserQueryNotes(
            ProviderTarget target,
            ModelMapping mapping,
            RecordsSyncModels.Settings settings,
            Map<Long, RecordsSyncModels.Note> notes,
            Set<Long> browserQueryNoteIds
    ) throws SyncFailure {
        for (Long noteId : browserQueryNoteIds) {
            if (!notes.containsKey(noteId)) {
                rereadBrowserQueryNotes(target, mapping, settings, notes);
                return;
            }
        }
    }

    private void rereadBrowserQueryNotes(
            ProviderTarget target,
            ModelMapping mapping,
            RecordsSyncModels.Settings settings,
            Map<Long, RecordsSyncModels.Note> notes
    ) throws SyncFailure {
        try {
            Map<Long, RecordsSyncModels.Note> extraNotes = queryNotesBySearch(
                    target, mapping, settings,
                    ProviderNotePolicy.configuredBrowserQuerySearch(settings.modelName, settings.normalizedBrowserQuery())
            );
            for (Map.Entry<Long, RecordsSyncModels.Note> entry : extraNotes.entrySet()) {
                notes.putIfAbsent(entry.getKey(), entry.getValue());
            }
        } catch (Exception error) {
            Log.w(TAG, "Browser query note re-read failed; using tracked IDs only.", error);
        }
    }

    @Override
    public RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
        return removeArchivedSuspendedCards(snapshot, SyncProgress.NONE);
    }

    @Override
    public RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot, SyncProgress.Listener progress) {
        return removeArchivedSuspendedCards(snapshot, null, progress);
    }

    @Override
    public RemovalSummary removeArchivedSuspendedCards(
            RecordsSyncModels.CollectionSnapshot snapshot,
            List<RecordsImportModels.SuspendedImport> selectedSuspendedImports,
            SyncProgress.Listener progress
    ) {
        SyncProgress.Listener reporter = progress == null ? SyncProgress.NONE : progress;
        reporter.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
        ProviderTarget target = resolveProviderTarget();
        if (target == null || snapshot.cards.isEmpty()) {
            return new RemovalSummary(0, 0, 0, "No provider removal attempted.");
        }
        ProviderArchiveCleanupPolicy.CleanupPlan cleanup = ProviderArchiveCleanupPolicy.plan(
                archiveCleanupCards(snapshot.cards),
                selectedSuspendedCardIds(selectedSuspendedImports)
        );
        if (!cleanup.hasSuspendedCards()) {
            return new RemovalSummary(0, 0, 0, "No suspended cards needed provider cleanup.");
        }

        int tagged = 0;
        int failed = cleanup.alreadyFailedCards();
        for (Long noteId : cleanup.notesToTag()) {
            if (tagNoteArchived(target, noteId)) {
                tagged++;
            } else {
                failed++;
            }
        }
        String message = ProviderArchiveCleanupPolicy.removalMessage(tagged, failed);
        return new RemovalSummary(cleanup.sourceCards(), 0, tagged, message);
    }

    private List<ProviderArchiveCleanupPolicy.Card> archiveCleanupCards(List<RecordsSyncModels.Card> cards) {
        List<ProviderArchiveCleanupPolicy.Card> cleanupCards = new ArrayList<>();
        for (RecordsSyncModels.Card card : cards) {
            cleanupCards.add(new ProviderArchiveCleanupPolicy.Card(card.cardId, card.noteId, card.suspended));
        }
        return cleanupCards;
    }

    private Set<Long> selectedSuspendedCardIds(List<RecordsImportModels.SuspendedImport> imports) {
        if (imports == null) {
            return null;
        }
        List<SyncMirrorPolicy.SelectedSource> sources = new ArrayList<>();
        for (RecordsImportModels.SuspendedImport imported : imports) {
            for (RecordsImportModels.SuspendedSource source : imported.sources) {
                sources.add(new SyncMirrorPolicy.SelectedSource(source.cardId, source.suspended));
            }
        }
        return SyncMirrorPolicy.selectedSuspendedCardIds(sources);
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
        if (!ProviderNotePolicy.isArchivedTagPresent(splitTags(tags))) {
            tags = (tags + " " + ProviderNotePolicy.ARCHIVED_TAG).trim();
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
        PackageManager packageManager = context.getPackageManager();
        for (ProviderTarget target : providerTargets) {
            if (providerInstalled(packageManager, target.authority)) {
                return target;
            }
        }
        return null;
    }

    static boolean providerInstalled(PackageManager packageManager, String authority) {
        return providerInstalled(packageManager, authority, Build.VERSION.SDK_INT);
    }

    static boolean providerInstalled(PackageManager packageManager, String authority, int sdkInt) {
        if (isAtLeastTiramisu(sdkInt)) {
            return providerInstalledOnTiramisuAndAbove(packageManager, authority);
        }
        return providerInstalledBeforeTiramisu(packageManager, authority);
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU, parameter = 0)
    private static boolean isAtLeastTiramisu(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.TIRAMISU;
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static boolean providerInstalledOnTiramisuAndAbove(PackageManager packageManager, String authority) {
        return packageManager.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0)) != null;
    }

    static boolean providerInstalledBeforeTiramisu(PackageManager packageManager, String authority) {
        return packageManager.resolveContentProvider(authority, 0) != null;
    }

    private boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private ModelMapping findConfiguredModel(ProviderTarget target, RecordsSyncModels.Settings settings) throws SyncFailure {
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
                .comparing((NoteType noteType) -> !noteType.name.equalsIgnoreCase(RecordsSyncModels.Settings.kikuDefaults().modelName))
                .thenComparing(noteType -> noteType.name.toLowerCase(Locale.ROOT)));
        return noteTypes;
    }

    private Map<Long, RecordsSyncModels.Note> queryNotes(ProviderTarget target, ModelMapping mapping, RecordsSyncModels.Settings settings) throws SyncFailure {
        Exception searchFailure = null;
        try {
            return queryNotesBySearch(target, mapping, settings, ProviderNotePolicy.modelSearch(settings.modelName));
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

    private Map<Long, RecordsSyncModels.Note> queryNotesBySearch(ProviderTarget target, ModelMapping mapping, RecordsSyncModels.Settings settings, String search) throws SyncFailure {
        Map<Long, RecordsSyncModels.Note> notes = new LinkedHashMap<>();
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

    private Map<Long, RecordsSyncModels.Note> queryNotesBySql(ProviderTarget target, ModelMapping mapping, RecordsSyncModels.Settings settings) throws SyncFailure {
        Map<Long, RecordsSyncModels.Note> notes = new LinkedHashMap<>();
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

    private void addNoteFromCursor(Map<Long, RecordsSyncModels.Note> notes, long noteId, Cursor cursor, ModelMapping mapping, RecordsSyncModels.Settings settings) {
        List<String> values = splitFields(value(cursor, COLUMN_FIELDS));
        Map<String, String> fieldMap = ProviderNotePolicy.selectRequiredFields(mapping.fields, values, settings.requiredFields());
        List<String> tags = splitTags(value(cursor, COLUMN_TAGS));
        if (!ProviderNotePolicy.isArchivedTagPresent(tags)) {
            notes.put(noteId, new RecordsSyncModels.Note(noteId, mapping.modelId, mapping.name, fieldMap, tags));
        }
    }

    private List<RecordsSyncModels.Card> cardsWithNotes(List<RecordsSyncModels.Card> cards, Set<Long> noteIds) {
        List<RecordsSyncModels.Card> out = new ArrayList<>();
        for (RecordsSyncModels.Card card : cards) {
            if (noteIds.contains(card.noteId)) {
                out.add(card);
            }
        }
        return out;
    }

    private void validateTemplateCards(List<RecordsSyncModels.Card> cards, RecordsSyncModels.Settings settings) throws SyncFailure {
        for (RecordsSyncModels.Card card : cards) {
            if (card.ord != 0) {
                throw SyncFailure.permanent(settings.modelName + " has card template ord " + card.ord + ". This app supports only the first card template at ord 0.");
            }
        }
    }

    private Set<Long> queryBrowserQueryNoteIds(ProviderTarget target, ModelMapping mapping, RecordsSyncModels.Settings settings) throws SyncFailure {
        if (!settings.browserQueryImportEnabled()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        String search = ProviderNotePolicy.configuredBrowserQuerySearch(settings.modelName, settings.normalizedBrowserQuery());
        Cursor cursor;
        try {
            cursor = resolver.query(
                    uriFor(target.authority, URI_SEGMENT_NOTES),
                    null,
                    search,
                    null,
                    null
            );
        } catch (Exception error) {
            throw SyncFailure.permanent("AnkiDroid could not run the browser query. Check the query in Import filters.", error);
        }
        if (cursor == null) {
            return ids;
        }
        try (Cursor queryCursor = cursor) {
            while (queryCursor.moveToNext()) {
                long modelId = longValue(queryCursor, COLUMN_MODEL_ID, mapping.modelId);
                if (modelId == mapping.modelId) {
                    ids.add(longValue(queryCursor, COLUMN_ID, 0));
                }
            }
        }
        return ids;
    }

    private static List<RecordsSyncModels.Card> markBrowserQueryMatchedCards(List<RecordsSyncModels.Card> cards, Set<Long> browserQueryNoteIds) {
        if (browserQueryNoteIds.isEmpty()) {
            return cards;
        }
        List<RecordsSyncModels.Card> result = new ArrayList<>(cards.size());
        for (RecordsSyncModels.Card card : cards) {
            if (browserQueryNoteIds.contains(card.noteId)) {
                result.add(card.withBrowserQueryMatched(true));
            } else {
                result.add(card);
            }
        }
        return result;
    }

    private static List<String> splitFields(String value) {
        return Arrays.asList(value.split(String.valueOf(FIELD_SEPARATOR), -1));
    }

    private static List<String> splitTags(String value) {
        List<String> tags = new ArrayList<>();
        for (String tag : NOTES_WHITESPACE_SEPARATOR.split(value)) {
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
