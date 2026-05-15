package dev.bee.kanjianki.backup;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.taskexecutor.SerialExecutor;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class DatabaseBackupWorkerInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        deleteDatabaseFiles();
        deleteRecursively(new File(context.getFilesDir(), "backups"));
        deleteRecursively(new File(context.getFilesDir(), "copy-failure"));
    }

    @After
    public void tearDown() {
        deleteDatabaseFiles();
        deleteRecursively(new File(context.getFilesDir(), "backups"));
        deleteRecursively(new File(context.getFilesDir(), "copy-failure"));
    }

    @Test
    public void androidEnvironmentUsesApplicationDatabaseAndFilesDirectories() {
        DatabaseBackupWorker.BackupEnvironment environment = DatabaseBackupWorker.androidEnvironment(context);

        assertEquals(
                context.getDatabasePath("kanji_anki_simple.db").getAbsolutePath(),
                environment.databasePath("kanji_anki_simple.db").getAbsolutePath()
        );
        assertEquals(context.getFilesDir().getAbsolutePath(), environment.filesDir().getAbsolutePath());
    }

    @Test
    public void checkpointRunsWalCheckpointAndClosesValidDatabase() {
        SQLiteDatabase db = context.openOrCreateDatabase("kanji_anki_simple.db", Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS probe(id INTEGER PRIMARY KEY)");
        db.close();
        File dbFile = context.getDatabasePath("kanji_anki_simple.db");

        DatabaseBackupWorker.checkpoint(dbFile);

        SQLiteDatabase reopened = SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            reopened.execSQL("INSERT INTO probe(id) VALUES(1)");
            assertTrue(dbFile.isFile());
        } finally {
            reopened.close();
        }
    }

    @Test
    public void workerConstructorAndDoWorkUseAndroidEnvironment() {
        SQLiteDatabase db = context.openOrCreateDatabase("kanji_anki_simple.db", Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS probe(id INTEGER PRIMARY KEY)");
        db.execSQL("INSERT INTO probe(id) VALUES(1)");
        db.close();

        DatabaseBackupWorker worker = new DatabaseBackupWorker(context, workerParameters());

        ListenableWorker.Result result = worker.doWork();

        assertTrue(result instanceof ListenableWorker.Result.Success);
        File backupDir = new File(context.getFilesDir(), "backups");
        File[] backups = backupDir.listFiles((dir, name) ->
                name.startsWith("kanji_anki_simple_") && name.endsWith(".db"));
        assertTrue(backups != null && backups.length == 1);
        assertTrue(backups[0].isFile());
    }

    @Test
    public void backupDatabaseLogsAndroidWarningsWhenFailedCopyCannotBeDeleted() throws Exception {
        File source = context.getDatabasePath("kanji_anki_simple.db");
        try (FileOutputStream output = new FileOutputStream(source)) {
            output.write(new byte[]{1, 2, 3});
        }
        File filesDir = new File(context.getFilesDir(), "copy-failure");
        assertTrue(filesDir.mkdirs());

        ListenableWorker.Result result = DatabaseBackupWorker.backupDatabase(
                source,
                filesDir,
                1_778_832_000_000L,
                ignored -> {
                },
                (src, dst) -> {
                    assertTrue(dst.mkdirs());
                    try (FileOutputStream output = new FileOutputStream(new File(dst, "partial"))) {
                        output.write(new byte[]{4, 5, 6});
                    }
                    throw new IOException("copy failed");
                });

        assertTrue(result instanceof ListenableWorker.Result.Failure);
        File[] incomplete = new File(filesDir, "backups").listFiles((dir, name) ->
                name.startsWith("kanji_anki_simple_") && name.endsWith(".db"));
        assertTrue(incomplete != null && incomplete.length == 1);
        assertTrue(incomplete[0].isDirectory());
    }

    private void deleteDatabaseFiles() {
        context.deleteDatabase("kanji_anki_simple.db");
        deleteIfExists(context.getDatabasePath("kanji_anki_simple.db-wal"));
        deleteIfExists(context.getDatabasePath("kanji_anki_simple.db-shm"));
    }

    private static void deleteIfExists(File file) {
        if (file.exists()) {
            assertTrue(file.delete());
        }
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        assertTrue(file.delete());
    }

    private static WorkerParameters workerParameters() {
        Executor directExecutor = Runnable::run;
        return new WorkerParameters(
                UUID.randomUUID(),
                new Data.Builder().build(),
                Collections.emptySet(),
                new WorkerParameters.RuntimeExtras(),
                0,
                0,
                directExecutor,
                null,
                taskExecutor(directExecutor),
                new WorkerFactory() {
                    @Override
                    public ListenableWorker createWorker(Context appContext, String workerClassName, WorkerParameters workerParameters) {
                        return null;
                    }
                },
                null,
                null
        );
    }

    private static TaskExecutor taskExecutor(Executor directExecutor) {
        SerialExecutor serialExecutor = new SerialExecutor() {
            @Override
            public void execute(Runnable command) {
                directExecutor.execute(command);
            }

            @Override
            public boolean hasPendingTasks() {
                return false;
            }
        };
        return new TaskExecutor() {
            @Override
            public Executor getMainThreadExecutor() {
                return directExecutor;
            }

            @Override
            public SerialExecutor getSerialTaskExecutor() {
                return serialExecutor;
            }
        };
    }
}
