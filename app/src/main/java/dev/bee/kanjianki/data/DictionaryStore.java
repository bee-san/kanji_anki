package dev.bee.kanjianki.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.JitenKanjiRanks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class DictionaryStore extends DictionaryLookup {
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern CHECKSUM_PART_SEPARATOR = Pattern.compile("\\s+");

    private static final String COLUMN_LITERAL = "literal";
    private static final String INSTALLING_SUFFIX = ".installing";
    private static final String BUNDLED_SUFFIX = ".bundled";
    private static final String PRIVATE_DIR = "dictionaries";
    private static final String PRIVATE_DB = "kanji_dictionary.db";
    private static final String PRIVATE_MANIFEST = "dictionary_sources.json";
    private static final String PRIVATE_CHECKSUM = "kanji_dictionary.db.sha256";
    private static final String BUNDLE_MARKER = "kanji_dictionary.bundle.sha256";
    private static final String SUPPORTED_SCHEMA_VERSION = "1";
    private static final String LIST_SEPARATOR = "\u001f";
    private static final Set<String> REQUIRED_KANJI_COLUMNS = new HashSet<>(Arrays.asList(
            COLUMN_LITERAL,
            "meanings",
            "on_readings",
            "kun_readings",
            "nanori_readings",
            "stroke_count",
            "grade",
            "radical",
            "kanjidic_frequency",
            "jiten_rank"
    ));
    private static final Set<String> REQUIRED_JITEN_RANK_COLUMNS = new HashSet<>(Arrays.asList(
            COLUMN_LITERAL,
            "rank"
    ));
    private static final Set<String> REQUIRED_META_KEYS = new HashSet<>(Arrays.asList(
            "schema_version",
            "generated_at",
            "kanjidic2_source_sha256",
            "kanjidic2_database_version",
            "jiten_rank_source_sha256"
    ));

    private final File databaseFile;
    private final File manifestFile;
    private final File checksumFile;

    private DictionaryStore(File databaseFile) {
        this.databaseFile = databaseFile;
        this.manifestFile = new File(databaseFile.getParentFile(), PRIVATE_MANIFEST);
        this.checksumFile = new File(databaseFile.getParentFile(), PRIVATE_CHECKSUM);
    }

    public static DictionaryStore open(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        File directory = new File(appContext.getFilesDir(), PRIVATE_DIR);
        File database = new File(directory, PRIVATE_DB);
        ensureBundledDictionaryInstalled(appContext, directory, database);
        return new DictionaryStore(database);
    }

    public static String activeManifestText(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        File directory = new File(appContext.getFilesDir(), PRIVATE_DIR);
        File database = new File(directory, PRIVATE_DB);
        ensureBundledDictionaryInstalled(appContext, directory, database);
        return readText(new File(directory, PRIVATE_MANIFEST));
    }

    public InstallResult installVerifiedDictionary(File database, File manifest, File checksum) {
        File temp = new File(databaseFile.getParentFile(), PRIVATE_DB + INSTALLING_SUFFIX);
        File tempManifest = new File(databaseFile.getParentFile(), PRIVATE_MANIFEST + INSTALLING_SUFFIX);
        File tempChecksum = new File(databaseFile.getParentFile(), PRIVATE_CHECKSUM + INSTALLING_SUFFIX);
        try {
            copy(database, temp);
            String manifestText = readText(manifest);
            String checksumText = readText(checksum);
            String expectedHash = firstSha256(checksumText);
            ValidationResult validation = validateDictionary(temp, expectedHash, manifestText);
            if (!validation.ok) {
                deleteQuietly(temp);
                return InstallResult.rejected(validation.message);
            }
            writeText(tempManifest, manifestText);
            writeText(tempChecksum, checksumText);
            atomicReplace(temp, databaseFile);
            atomicReplace(tempManifest, manifestFile);
            atomicReplace(tempChecksum, checksumFile);
            return InstallResult.installed("Dictionary installed.");
        } catch (IOException error) {
            deleteQuietly(temp);
            deleteQuietly(tempManifest);
            deleteQuietly(tempChecksum);
            return InstallResult.rejected("Dictionary install failed: " + readableMessage(error));
        }
    }

    @Override
    public KanjiEntry lookupKanji(String literal) {
        String normalized = normalize(literal);
        if (normalized.isEmpty()) {
            return null;
        }
        try (SQLiteDatabase db = openReadOnly()) {
            Cursor cursor = db.query(
                    "kanji",
                    null,
                    "literal=?",
                    new String[]{normalized},
                    null,
                    null,
                    null,
                    "1"
            );
            try {
                if (!cursor.moveToFirst()) {
                    return null;
                }
                return new KanjiEntry(new KanjiEntryFields(
                        string(cursor, COLUMN_LITERAL),
                        splitList(string(cursor, "meanings")),
                        splitList(string(cursor, "on_readings")),
                        splitList(string(cursor, "kun_readings")),
                        splitList(string(cursor, "nanori_readings")),
                        integer(cursor, "stroke_count"),
                        integer(cursor, "grade"),
                        integer(cursor, "radical"),
                        integer(cursor, "kanjidic_frequency"),
                        nullableInteger(cursor, "jiten_rank")
                ));
            } finally {
                cursor.close();
            }
        } catch (SQLiteException error) {
            return null;
        }
    }

    @Override
    public int kanjiCount() {
        try (SQLiteDatabase db = openReadOnly()) {
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM kanji", null);
            try {
                return cursor.moveToFirst() ? cursor.getInt(0) : 0;
            } finally {
                cursor.close();
            }
        } catch (SQLiteException error) {
            return 0;
        }
    }

    @Override
    public JitenKanjiRanks jitenRanks() {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        try (SQLiteDatabase db = openReadOnly()) {
            Cursor cursor = db.rawQuery(
                    "SELECT literal, rank FROM jiten_ranks ORDER BY rank ASC, literal ASC",
                    null
            );
            try {
                while (cursor.moveToNext()) {
                    ranks.put(cursor.getString(0), cursor.getInt(1));
                }
            } finally {
                cursor.close();
            }
        } catch (SQLiteException error) {
            return JitenKanjiRanks.empty();
        }
        return new JitenKanjiRanks(ranks);
    }

    private SQLiteDatabase openReadOnly() {
        return SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    private static void ensureBundledDictionaryInstalled(Context context, File directory, File database) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create dictionary directory.");
        }
        String bundledHash = readExpectedHash(context.getAssets().open(DictionaryAssets.DATABASE_SHA256_ASSET));
        String bundledManifest = readAssetText(context, DictionaryAssets.SOURCES_ASSET);
        String bundledChecksum = readAssetText(context, DictionaryAssets.DATABASE_SHA256_ASSET);
        File marker = new File(directory, BUNDLE_MARKER);
        File manifest = new File(directory, PRIVATE_MANIFEST);
        File checksum = new File(directory, PRIVATE_CHECKSUM);
        if (database.exists()
                && manifest.exists()
                && checksum.exists()
                && bundledHash.equals(readMarker(marker))) {
            return;
        }
        File temp = new File(directory, PRIVATE_DB + BUNDLED_SUFFIX);
        File tempManifest = new File(directory, PRIVATE_MANIFEST + BUNDLED_SUFFIX);
        File tempChecksum = new File(directory, PRIVATE_CHECKSUM + BUNDLED_SUFFIX);
        try (InputStream source = context.getAssets().open(DictionaryAssets.DATABASE_ASSET)) {
            copy(source, temp);
        }
        ValidationResult validation = validateDictionary(
                temp,
                bundledHash,
                bundledManifest
        );
        if (!validation.ok) {
            deleteQuietly(temp);
            throw new IOException(validation.message);
        }
        writeText(tempManifest, bundledManifest);
        writeText(tempChecksum, bundledChecksum);
        atomicReplace(temp, database);
        atomicReplace(tempManifest, manifest);
        atomicReplace(tempChecksum, checksum);
        writeMarker(marker, bundledHash);
    }

    private static ValidationResult validateDictionary(File database, String expectedHash, String manifest) {
        if (expectedHash == null || !SHA256_HEX_PATTERN.matcher(expectedHash).matches()) {
            return ValidationResult.rejected("Dictionary checksum is missing or invalid.");
        }
        try {
            String actualHash = sha256(database);
            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                return ValidationResult.rejected("Dictionary checksum mismatch.");
            }
            ValidationResult manifestResult = validateManifest(manifest, expectedHash);
            if (!manifestResult.ok) {
                return manifestResult;
            }
            return validateSqlite(database);
        } catch (IOException error) {
            return ValidationResult.rejected("Dictionary validation failed: " + readableMessage(error));
        }
    }

    private static ValidationResult validateManifest(String manifest, String expectedHash) {
        try {
            JSONObject root = new JSONObject(manifest);
            if (!hasMatchingDatabaseAsset(root.optJSONArray("assets"), expectedHash)) {
                return ValidationResult.rejected("Dictionary manifest does not match the database checksum.");
            }
            return hasKanjidic2Source(root.optJSONArray("sources"))
                    ? ValidationResult.ok()
                    : ValidationResult.rejected("Dictionary manifest is missing KANJIDIC2 metadata.");
        } catch (JSONException error) {
            return ValidationResult.rejected("Dictionary manifest is invalid JSON.");
        }
    }

    private static boolean hasMatchingDatabaseAsset(JSONArray assets, String expectedHash) throws JSONException {
        if (assets == null) {
            return false;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (DictionaryAssets.DATABASE_ASSET_NAME.equals(asset.optString("path"))
                    && expectedHash.equalsIgnoreCase(asset.optString("sha256"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasKanjidic2Source(JSONArray sources) throws JSONException {
        if (sources == null) {
            return false;
        }
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.getJSONObject(i);
            if ("kanjidic2".equals(source.optString("id"))
                    && !source.optString("source_sha256").isEmpty()
                    && !source.optString("database_version").isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static ValidationResult validateSqlite(File database) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            ValidationResult tableResult = validateColumns(db, "kanji", REQUIRED_KANJI_COLUMNS);
            if (!tableResult.ok) {
                return tableResult;
            }
            ValidationResult rankTableResult = validateColumns(db, "jiten_ranks", REQUIRED_JITEN_RANK_COLUMNS);
            if (!rankTableResult.ok) {
                return rankTableResult;
            }
            ValidationResult metaTableResult = validateColumns(db, "dictionary_meta", new HashSet<>(Arrays.asList("key", "value")));
            if (!metaTableResult.ok) {
                return metaTableResult;
            }
            Map<String, String> meta = readMeta(db);
            for (String key : REQUIRED_META_KEYS) {
                if (!meta.containsKey(key) || meta.get(key).isEmpty()) {
                    return ValidationResult.rejected("Dictionary metadata is missing " + key + ".");
                }
            }
            if (!SUPPORTED_SCHEMA_VERSION.equals(meta.get("schema_version"))) {
                return ValidationResult.rejected("Dictionary schema version is unsupported.");
            }
            Cursor cursor = db.rawQuery("SELECT literal FROM kanji LIMIT 1", null);
            try {
                return cursor.moveToFirst()
                        ? ValidationResult.ok()
                        : ValidationResult.rejected("Dictionary has no kanji rows.");
            } finally {
                cursor.close();
            }
        } catch (SQLiteException error) {
            return ValidationResult.rejected("Dictionary database is invalid.");
        }
    }

    private static ValidationResult validateColumns(SQLiteDatabase db, String table, Set<String> requiredColumns) {
        Set<String> columns = new HashSet<>();
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (cursor.moveToNext()) {
                columns.add(string(cursor, "name"));
            }
        } finally {
            cursor.close();
        }
        if (columns.isEmpty()) {
            return ValidationResult.rejected("Dictionary is missing " + table + " table.");
        }
        for (String required : requiredColumns) {
            if (!columns.contains(required)) {
                return ValidationResult.rejected("Dictionary " + table + " table is missing " + required + ".");
            }
        }
        return ValidationResult.ok();
    }

    private static Map<String, String> readMeta(SQLiteDatabase db) {
        Map<String, String> meta = new LinkedHashMap<>();
        Cursor cursor = db.query("dictionary_meta", new String[]{"key", "value"}, null, null, null, null, "key ASC");
        try {
            while (cursor.moveToNext()) {
                meta.put(cursor.getString(0), cursor.getString(1));
            }
        } finally {
            cursor.close();
        }
        return meta;
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String cell : value.split(LIST_SEPARATOR, -1)) {
            String trimmed = cell.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static String string(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static int integer(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private static Integer nullableInteger(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getInt(index);
    }

    private static void copy(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source)) {
            copy(input, target);
        }
    }

    private static void copy(InputStream source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent + ".");
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = source.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void atomicReplace(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readExpectedHash(File checksum) throws IOException {
        return firstSha256(readText(checksum));
    }

    private static String readExpectedHash(InputStream checksum) throws IOException {
        try (InputStream input = checksum) {
            return firstSha256(readText(input));
        }
    }

    private static String firstSha256(String text) {
        String value = text == null ? "" : text.trim();
        String[] parts = CHECKSUM_PART_SEPARATOR.split(value);
        return parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
    }

    private static String readAssetText(Context context, String asset) throws IOException {
        try (InputStream input = context.getAssets().open(asset)) {
            return readText(input);
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return readText(input);
        }
    }

    private static void writeText(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readText(InputStream input) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String readMarker(File marker) {
        try {
            return marker.exists() ? readText(marker).trim() : "";
        } catch (IOException error) {
            return "";
        }
    }

    private static void writeMarker(File marker, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write((value + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream source = new FileInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = source.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static String readableMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    public static final class InstallResult {
        public final boolean ok;
        public final String message;

        private InstallResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
        }

        private static InstallResult installed(String message) {
            return new InstallResult(true, message);
        }

        private static InstallResult rejected(String message) {
            return new InstallResult(false, message);
        }
    }

    private static final class ValidationResult {
        private final boolean ok;
        private final String message;

        private ValidationResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
        }

        private static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        private static ValidationResult rejected(String message) {
            return new ValidationResult(false, message);
        }
    }
}
