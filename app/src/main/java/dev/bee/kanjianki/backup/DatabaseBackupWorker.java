package dev.bee.kanjianki.backup;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

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
    private static final String DB_NAME = "kanji_anki_simple.db";
    private static final String BACKUP_DIR = "backups";
    private static final int MAX_BACKUPS = 31;

    public DatabaseBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        File dbFile = context.getDatabasePath(DB_NAME);
        if (!dbFile.exists()) {
            return Result.failure();
        }

        File backupDir = new File(context.getFilesDir(), BACKUP_DIR);
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            return Result.failure();
        }

        checkpoint(dbFile);

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dest = new File(backupDir, "kanji_anki_simple_" + timestamp + ".db");

        try {
            copyFile(dbFile, dest);
        } catch (IOException e) {
            dest.delete();
            return Result.failure();
        }

        pruneOldBackups(backupDir);
        return Result.success();
    }

    private void checkpoint(File dbFile) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close();
        } catch (Exception ignored) {
            // Best-effort; copy will still work even without checkpoint
        } finally {
            if (db != null) {
                try { db.close(); } catch (Exception ignored) { }
            }
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
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

    private static void pruneOldBackups(File backupDir) {
        File[] files = backupDir.listFiles((dir, name) ->
                name.startsWith("kanji_anki_simple_") && name.endsWith(".db"));
        if (files == null || files.length <= MAX_BACKUPS) {
            return;
        }
        Arrays.sort(files);
        int toDelete = files.length - MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            files[i].delete();
        }
    }
}
