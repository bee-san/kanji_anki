package dev.bee.kanjianki.backup;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public final class DatabaseBackupWorker extends Worker {
    private static final String TAG = "DatabaseBackupWorker";
    private static final String[] DB_NAMES = {"kanji_anki_simple.db", "kanji_anki_room.db"};
    private static final String[] SQLITE_SIDECAR_SUFFIXES = {"-wal", "-shm"};
    private static final String BACKUP_DIR = "backups";
    private static final int MAX_BACKUPS = 31;

    public DatabaseBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        return doWork(new AndroidBackupEnvironment(getApplicationContext()), System.currentTimeMillis());
    }

    static Result doWork(BackupEnvironment environment, long nowMillis) {
        File[] databases = new File[DB_NAMES.length];
        for (int i = 0; i < DB_NAMES.length; i++) {
            databases[i] = environment.databasePath(DB_NAMES[i]);
        }
        return backupDatabases(
                databases,
                environment.filesDir(),
                nowMillis,
                DatabaseBackupWorker::checkpoint,
                DatabaseBackupWorker::copyFile);
    }

    static Result backupDatabases(
            File[] dbFiles,
            File filesDir,
            long nowMillis,
            Checkpointer checkpointer,
            FileCopier copier) {
        boolean backedUpAny = false;
        for (File dbFile : dbFiles) {
            if (!dbFile.exists()) {
                continue;
            }
            if (!backupDatabaseFile(dbFile, filesDir, nowMillis, checkpointer, copier, true)) {
                return Result.failure();
            }
            backedUpAny = true;
        }
        return backedUpAny ? Result.success() : Result.failure();
    }

    static Result backupDatabase(
            File dbFile,
            File filesDir,
            long nowMillis,
            Checkpointer checkpointer,
            FileCopier copier) {
        return backupDatabaseFile(
                dbFile,
                filesDir,
                nowMillis,
                checkpointer,
                copier,
                true)
                ? Result.success()
                : Result.failure();
    }

    private static boolean backupDatabaseFile(
            File dbFile,
            File filesDir,
            long nowMillis,
            Checkpointer checkpointer,
            FileCopier copier,
            boolean pruneAfterBackup) {
        if (!dbFile.exists()) {
            return false;
        }

        File backupDir = new File(filesDir, BACKUP_DIR);
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            return false;
        }

        try {
            checkpointer.checkpoint(dbFile);
        } catch (RuntimeException error) {
            warn("Backup checkpoint failed; copying database without a fresh WAL checkpoint.", error);
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(nowMillis));
        File dest = new File(backupDir, backupPrefix(dbFile) + timestamp + ".db");

        try {
            copier.copy(dbFile, dest);
            copySidecars(dbFile, dest, copier);
        } catch (IOException e) {
            deleteIncompleteBackup(dest);
            warn("Database backup failed.", e);
            return false;
        }

        if (pruneAfterBackup) {
            pruneOldBackups(backupDir);
        }
        return true;
    }

    private static void copySidecars(File dbFile, File dest, FileCopier copier) throws IOException {
        for (String suffix : SQLITE_SIDECAR_SUFFIXES) {
            File sidecar = new File(dbFile.getPath() + suffix);
            if (!sidecar.exists()) {
                continue;
            }
            copier.copy(sidecar, new File(dest.getPath() + suffix));
        }
    }

    private static void deleteIncompleteBackup(File dest) {
        deleteBackupFile(dest);
        for (String suffix : SQLITE_SIDECAR_SUFFIXES) {
            deleteBackupFile(new File(dest.getPath() + suffix));
        }
    }

    private static void deleteBackupFile(File file) {
        if (file.exists() && !file.delete()) {
            warn("Failed to delete incomplete backup: " + file.getName());
        }
    }

    private static String backupPrefix(File dbFile) {
        String name = dbFile.getName();
        if (name.endsWith(".db")) {
            name = name.substring(0, name.length() - 3);
        }
        return name + "_";
    }

    interface Checkpointer {
        void checkpoint(File dbFile);
    }

    interface FileCopier {
        void copy(File src, File dst) throws IOException;
    }

    interface BackupEnvironment {
        File databasePath(String name);

        File filesDir();
    }

    static BackupEnvironment androidEnvironment(Context context) {
        return new AndroidBackupEnvironment(context);
    }

    private static final class AndroidBackupEnvironment implements BackupEnvironment {
        private final Context context;

        AndroidBackupEnvironment(Context context) {
            this.context = context;
        }

        @Override
        public File databasePath(String name) {
            return context.getDatabasePath(name);
        }

        @Override
        public File filesDir() {
            return context.getFilesDir();
        }
    }

    static void checkpoint(File dbFile) {
        checkpoint(dbFile, DatabaseBackupWorker::openCheckpointDatabase);
    }

    static void checkpoint(File dbFile, CheckpointDatabaseOpener opener) {
        CheckpointDatabase db = null;
        try {
            db = opener.open(dbFile);
            db.checkpoint();
        } catch (IOException | RuntimeException error) {
            warn("Backup checkpoint failed; copying database without a fresh WAL checkpoint.", error);
        } finally {
            closeCheckpointDatabase(db);
        }
    }

    private static CheckpointDatabase openCheckpointDatabase(File dbFile) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        return new SQLiteCheckpointDatabase(db);
    }

    private static void closeCheckpointDatabase(CheckpointDatabase db) {
        if (db == null) {
            return;
        }
        try {
            db.close();
        } catch (IOException | RuntimeException e) {
            warn("Failed to close database after backup checkpoint.", e);
        }
    }

    interface CheckpointDatabaseOpener {
        CheckpointDatabase open(File dbFile) throws IOException;
    }

    interface CheckpointDatabase {
        void checkpoint() throws IOException;

        void close() throws IOException;
    }

    private static final class SQLiteCheckpointDatabase implements CheckpointDatabase {
        private final SQLiteDatabase db;

        SQLiteCheckpointDatabase(SQLiteDatabase db) {
            this.db = db;
        }

        @Override
        public void checkpoint() {
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close();
        }

        @Override
        public void close() {
            db.close();
        }
    }

    static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream inStream = new FileInputStream(src);
             FileOutputStream outStream = new FileOutputStream(dst);
             FileChannel inChannel = inStream.getChannel();
             FileChannel outChannel = outStream.getChannel()) {
            long size = inChannel.size();
            long transferred = 0;
            while (transferred < size) {
                transferred += inChannel.transferTo(transferred, size - transferred, outChannel);
            }
            outChannel.force(true);
        }
    }

    static void pruneOldBackups(File backupDir) {
        for (String dbName : DB_NAMES) {
            pruneOldBackups(backupDir, dbName);
        }
    }

    private static void pruneOldBackups(File backupDir, String dbName) {
        String prefix = backupPrefix(new File(dbName));
        File[] files = backupDir.listFiles((dir, name) ->
                name.startsWith(prefix) && name.endsWith(".db"));
        if (files == null || files.length <= MAX_BACKUPS) {
            return;
        }
        Arrays.sort(files);
        int toDelete = files.length - MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            deletePrunedBackup(files[i]);
        }
    }

    private static void deletePrunedBackup(File backup) {
        if (!backup.delete()) {
            warn("Failed to prune old backup: " + backup.getName());
            return;
        }
        for (String suffix : SQLITE_SIDECAR_SUFFIXES) {
            File sidecar = new File(backup.getPath() + suffix);
            if (sidecar.exists() && !sidecar.delete()) {
                warn("Failed to prune old backup sidecar: " + sidecar.getName());
            }
        }
    }

    private static void warn(String message) {
        try {
            Log.w(TAG, message);
        } catch (RuntimeException ignored) {
            // Android Log is unavailable in local JVM tests.
        }
    }

    private static void warn(String message, Throwable error) {
        try {
            Log.w(TAG, message, error);
        } catch (RuntimeException ignored) {
            // Android Log is unavailable in local JVM tests.
        }
    }
}
