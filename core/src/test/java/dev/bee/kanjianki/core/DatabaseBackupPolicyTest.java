package dev.bee.kanjianki.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DatabaseBackupPolicyTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void backupFileUsesStableDirectoryPrefixAndTimestamp() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            File filesDir = temp.newFolder("files");
            long now = 1_778_832_000_000L;

            assertEquals(new File(filesDir, "backups"), DatabaseBackupPolicy.backupDir(filesDir));
            assertEquals("20260515_080000", DatabaseBackupPolicy.timestamp(now));
            assertEquals(
                    new File(new File(filesDir, "backups"), "kanji_anki_simple_20260515_080000.db"),
                    DatabaseBackupPolicy.backupFile(filesDir, now)
            );
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void oldBackupsToPruneKeepsNewestThirtyOneMatchingDatabaseFiles() throws Exception {
        File dir = temp.newFolder("backups");
        for (int i = 1; i <= 35; i++) {
            assertTrue(new File(dir, String.format("kanji_anki_simple_20260515_%06d.db", i)).createNewFile());
        }
        assertTrue(new File(dir, "notes.txt").createNewFile());
        assertTrue(new File(dir, "kanji_anki_simple_20260515_999999.tmp").createNewFile());

        Set<String> names = new HashSet<>();
        for (File file : DatabaseBackupPolicy.oldBackupsToPrune(dir)) {
            names.add(file.getName());
        }

        assertEquals(4, names.size());
        assertTrue(names.contains("kanji_anki_simple_20260515_000001.db"));
        assertTrue(names.contains("kanji_anki_simple_20260515_000004.db"));
    }

    @Test
    public void oldBackupsToPruneHandlesMissingDirectoryAndShortLists() throws Exception {
        assertTrue(DatabaseBackupPolicy.oldBackupsToPrune(new File(temp.getRoot(), "missing")).isEmpty());

        File dir = temp.newFolder("short");
        assertTrue(new File(dir, "kanji_anki_simple_20260515_000001.db").createNewFile());
        assertTrue(DatabaseBackupPolicy.oldBackupsToPrune(dir).isEmpty());
    }

    @Test
    public void diagnosticLineDoesNotExposePathsOrExceptionMessages() {
        IOExceptionWithPath error = new IOExceptionWithPath(
                "open failed: /data/user/0/dev.bee.kanjianki/databases/kanji_anki_simple.db"
        );

        assertEquals(
                "Database backup failed. Diagnostic: IOExceptionWithPath",
                DatabaseBackupPolicy.sanitizedDiagnosticLine("Database backup failed.", error)
        );
    }

    private static final class IOExceptionWithPath extends java.io.IOException {
        IOExceptionWithPath(String message) {
            super(message);
        }
    }
}
