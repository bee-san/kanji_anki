package dev.bee.kanjianki.backup;

import androidx.work.ListenableWorker;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DatabaseBackupWorkerTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void backupDatabaseCopiesTimestampedBackupAndPrunesOldFiles() throws Exception {
        File db = temp.newFile("kanji_anki_simple.db");
        byte[] content = "durable progress".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(db)) {
            output.write(content);
        }
        File filesDir = temp.newFolder("files");
        File backupDir = new File(filesDir, "backups");
        assertTrue(backupDir.mkdirs());
        for (int i = 1; i <= 31; i++) {
            write(new File(backupDir, String.format("kanji_anki_simple_20200101_%06d.db", i)), "old-" + i);
        }
        long now = 1_778_832_000_000L;
        boolean[] checkpointed = {false};

        ListenableWorker.Result result = DatabaseBackupWorker.backupDatabase(
                db,
                filesDir,
                now,
                ignored -> checkpointed[0] = true,
                DatabaseBackupWorker::copyFile);

        assertSuccess(result);
        assertTrue(checkpointed[0]);
        File backup = new File(backupDir, "kanji_anki_simple_" + timestamp(now) + ".db");
        assertArrayEquals(content, read(backup));
        assertFalse(new File(backupDir, "kanji_anki_simple_20200101_000001.db").exists());
        assertTrue(new File(backupDir, "kanji_anki_simple_20200101_000002.db").exists());
    }

    @Test
    public void doWorkUsesRealBackupFlowAndCheckpointFallback() throws Exception {
        File db = temp.newFile("kanji_anki_simple.db");
        byte[] content = "not a sqlite database but still backup-worthy".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(db)) {
            output.write(content);
        }
        File filesDir = temp.newFolder("files");
        long now = 1_778_832_000_000L;

        ListenableWorker.Result result = DatabaseBackupWorker.doWork(new DatabaseBackupWorker.BackupEnvironment() {
            @Override
            public File databasePath(String name) {
                assertEquals("kanji_anki_simple.db", name);
                return db;
            }

            @Override
            public File filesDir() {
                return filesDir;
            }
        }, now);

        assertSuccess(result);
        File backup = new File(new File(filesDir, "backups"), "kanji_anki_simple_" + timestamp(now) + ".db");
        assertArrayEquals(content, read(backup));
    }

    @Test
    public void backupDatabaseFailsWhenSourceDatabaseIsMissing() throws Exception {
        File filesDir = temp.newFolder("files");

        ListenableWorker.Result result = DatabaseBackupWorker.backupDatabase(
                new File(temp.getRoot(), "missing.db"),
                filesDir,
                1_778_832_000_000L,
                ignored -> {
                    throw new AssertionError("missing databases must not be checkpointed");
                },
                (src, dst) -> {
                    throw new AssertionError("missing databases must not be copied");
                });

        assertFailure(result);
        assertFalse(new File(filesDir, "backups").exists());
    }

    @Test
    public void backupDatabaseFailsWhenBackupDirectoryCannotBeCreated() throws Exception {
        File db = temp.newFile("kanji_anki_simple.db");
        write(db, "db");
        File filesDir = temp.newFile("files-is-not-a-directory");

        ListenableWorker.Result result = DatabaseBackupWorker.backupDatabase(
                db,
                filesDir,
                1_778_832_000_000L,
                ignored -> {
                    throw new AssertionError("unwritable backup directories must not checkpoint");
                },
                (src, dst) -> {
                    throw new AssertionError("unwritable backup directories must not copy");
                });

        assertFailure(result);
    }

    @Test
    public void backupDatabaseDeletesIncompleteBackupWhenCopyFails() throws Exception {
        File db = temp.newFile("kanji_anki_simple.db");
        write(db, "db");
        File filesDir = temp.newFolder("files");
        long now = 1_778_832_000_000L;

        ListenableWorker.Result result = DatabaseBackupWorker.backupDatabase(
                db,
                filesDir,
                now,
                ignored -> {
                },
                (src, dst) -> {
                    try (FileOutputStream output = new FileOutputStream(dst)) {
                        output.write("partial".getBytes(StandardCharsets.UTF_8));
                    }
                    throw new java.io.IOException("disk full");
                });

        assertFailure(result);
        assertFalse(new File(new File(filesDir, "backups"), "kanji_anki_simple_" + timestamp(now) + ".db").exists());
    }

    @Test
    public void backupDatabaseLeavesIncompleteBackupWhenFailedCopyCannotBeDeleted() throws Exception {
        File db = temp.newFile("kanji_anki_simple.db");
        write(db, "db");
        File filesDir = temp.newFolder("files");
        long now = 1_778_832_000_000L;

        ListenableWorker.Result result = DatabaseBackupWorker.backupDatabase(
                db,
                filesDir,
                now,
                ignored -> {
                },
                (src, dst) -> {
                    assertTrue(dst.mkdirs());
                    try (FileOutputStream output = new FileOutputStream(new File(dst, "partial"))) {
                        output.write("partial".getBytes(StandardCharsets.UTF_8));
                    }
                    throw new IOException("copy died after creating a non-empty destination");
                });

        assertFailure(result);
        File incomplete = new File(new File(filesDir, "backups"), "kanji_anki_simple_" + timestamp(now) + ".db");
        assertTrue(incomplete.isDirectory());
        assertTrue(new File(incomplete, "partial").isFile());
    }

    @Test
    public void backupDatabaseContinuesWhenCheckpointFails() throws Exception {
        File db = temp.newFile("kanji_anki_simple.db");
        write(db, "db");
        File filesDir = temp.newFolder("files");

        ListenableWorker.Result result = DatabaseBackupWorker.backupDatabase(
                db,
                filesDir,
                1_778_832_000_000L,
                ignored -> {
                    throw new IllegalStateException("locked");
                },
                DatabaseBackupWorker::copyFile);

        assertSuccess(result);
        assertTrue(new File(new File(filesDir, "backups"), "kanji_anki_simple_" + timestamp(1_778_832_000_000L) + ".db").isFile());
    }

    @Test
    public void checkpointSkipsCloseWhenDatabaseCannotBeOpened() {
        boolean[] opened = {false};

        DatabaseBackupWorker.checkpoint(new File(temp.getRoot(), "missing.db"), dbFile -> {
            opened[0] = true;
            throw new IOException("cannot open");
        });

        assertTrue(opened[0]);
    }

    @Test
    public void checkpointClosesDatabaseAndToleratesCloseFailure() {
        FakeCheckpointDatabase database = new FakeCheckpointDatabase(true);

        DatabaseBackupWorker.checkpoint(temp.getRoot(), ignored -> database);

        assertEquals(1, database.checkpointCount);
        assertEquals(1, database.closeCount);
    }

    @Test
    public void copyFileWritesCompleteBytesAndFlushesDestination() throws Exception {
        File src = temp.newFile("source.db");
        File dst = new File(temp.getRoot(), "copy.db");
        byte[] content = new byte[96_000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 17 + 3);
        }
        try (FileOutputStream output = new FileOutputStream(src)) {
            output.write(content);
        }

        DatabaseBackupWorker.copyFile(src, dst);

        assertTrue(dst.isFile());
        assertArrayEquals(content, read(dst));
    }

    @Test
    public void pruneOldBackupsKeepsNewestThirtyOneMatchingDatabaseFilesOnly() throws Exception {
        File dir = temp.newFolder("backups");
        for (int i = 1; i <= 35; i++) {
            write(new File(dir, String.format("kanji_anki_simple_20260515_%06d.db", i)), "db-" + i);
        }
        File ignored = new File(dir, "notes.txt");
        write(ignored, "keep");
        File wrongSuffix = new File(dir, "kanji_anki_simple_20260515_999999.tmp");
        write(wrongSuffix, "keep");

        DatabaseBackupWorker.pruneOldBackups(dir);

        Set<String> names = new HashSet<>();
        File[] files = dir.listFiles();
        assertTrue(files != null);
        Arrays.stream(files).map(File::getName).forEach(names::add);
        assertTrue(names.contains("notes.txt"));
        assertTrue(names.contains("kanji_anki_simple_20260515_999999.tmp"));
        assertFalse(names.contains("kanji_anki_simple_20260515_000001.db"));
        assertFalse(names.contains("kanji_anki_simple_20260515_000004.db"));
        assertTrue(names.contains("kanji_anki_simple_20260515_000005.db"));
        assertTrue(names.contains("kanji_anki_simple_20260515_000035.db"));
    }

    @Test
    public void pruneOldBackupsContinuesWhenOldestMatchingEntryCannotBeDeleted() throws Exception {
        File dir = temp.newFolder("backups");
        File stubborn = new File(dir, "kanji_anki_simple_20200101_000000.db");
        assertTrue(stubborn.mkdirs());
        write(new File(stubborn, "partial"), "partial");
        for (int i = 1; i <= 31; i++) {
            write(new File(dir, String.format("kanji_anki_simple_20260515_%06d.db", i)), "db-" + i);
        }
        write(new File(dir, "other_20200101_000000.db"), "not a Kani backup");

        DatabaseBackupWorker.pruneOldBackups(dir);

        assertTrue(stubborn.isDirectory());
        assertTrue(new File(stubborn, "partial").isFile());
        assertTrue(new File(dir, "other_20200101_000000.db").isFile());
    }

    @Test
    public void pruneOldBackupsAllowsMissingOrSmallDirectories() throws Exception {
        File missing = new File(temp.getRoot(), "missing");
        File small = temp.newFolder("small");
        write(new File(small, "kanji_anki_simple_20260515_000001.db"), "one");

        DatabaseBackupWorker.pruneOldBackups(missing);
        DatabaseBackupWorker.pruneOldBackups(small);

        assertFalse(missing.exists());
        assertTrue(new File(small, "kanji_anki_simple_20260515_000001.db").exists());
    }

    private static void write(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String timestamp(long millis) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(millis);
    }

    private static void assertSuccess(ListenableWorker.Result result) {
        assertTrue(result instanceof ListenableWorker.Result.Success);
    }

    private static void assertFailure(ListenableWorker.Result result) {
        assertTrue(result instanceof ListenableWorker.Result.Failure);
    }

    private static byte[] read(File file) throws Exception {
        byte[] out = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < out.length) {
                int read = input.read(out, offset, out.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        return out;
    }

    private static final class FakeCheckpointDatabase implements DatabaseBackupWorker.CheckpointDatabase {
        final boolean failClose;
        int checkpointCount;
        int closeCount;

        FakeCheckpointDatabase(boolean failClose) {
            this.failClose = failClose;
        }

        @Override
        public void checkpoint() {
            checkpointCount++;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            if (failClose) {
                throw new IOException("close failed");
            }
        }
    }
}
