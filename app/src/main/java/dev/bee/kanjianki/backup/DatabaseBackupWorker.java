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
    private static final String DB_NAME = "kanji_anki_simple.db";
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
        return backupDatabase(
                environment.databasePath(DB_NAME),
                environment.filesDir(),
                nowMillis,
                DatabaseBackupWorker::checkpoint,
                DatabaseBackupWorker::copyFile);
    }

    static Result backupDatabase(
            File dbFile,
            File filesDir,
            long nowMillis,
            Checkpointer checkpointer,
            FileCopier copier) {
        if (!dbFile.exists()) {
            return Result.failure();
        }

        File backupDir = new File(filesDir, BACKUP_DIR);
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            return Result.failure();
        }

        try {
            checkpointer.checkpoint(dbFile);
        } catch (RuntimeException error) {
            warn("Backup checkpoint failed; copying database without a fresh WAL checkpoint.", error);
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(nowMillis));
        File dest = new File(backupDir, "kanji_anki_simple_" + timestamp + ".db");

        try {
            copier.copy(dbFile, dest);
        } catch (IOException e) {
            if (!dest.delete()) {
                warn("Failed to delete incomplete backup: " + dest.getName());
            }
            warn("Database backup failed.", e);
            return Result.failure();
        }

        pruneOldBackups(backupDir);
        return Result.success();
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
        } catch (Exception error) {
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
        } catch (Exception e) {
            warn("Failed to close database after backup checkpoint.", e);
        }
    }

    interface CheckpointDatabaseOpener {
        CheckpointDatabase open(File dbFile) throws Exception;
    }

    interface CheckpointDatabase {
        void checkpoint() throws Exception;

        void close() throws Exception;
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
        File[] files = backupDir.listFiles((dir, name) ->
                name.startsWith("kanji_anki_simple_") && name.endsWith(".db"));
        if (files == null || files.length <= MAX_BACKUPS) {
            return;
        }
        Arrays.sort(files);
        int toDelete = files.length - MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            if (!files[i].delete()) {
                warn("Failed to prune old backup: " + files[i].getName());
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
