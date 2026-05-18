package dev.bee.kanjianki.core;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DatabaseBackupPolicy {
    public static final String DB_NAME = "kanji_anki_simple.db";
    public static final String BACKUP_DIR = "backups";
    public static final int MAX_BACKUPS = 31;
    private static final String BACKUP_PREFIX = "kanji_anki_simple_";
    private static final String BACKUP_SUFFIX = ".db";

    private DatabaseBackupPolicy() {
    }

    public static File backupDir(File filesDir) {
        return new File(filesDir, BACKUP_DIR);
    }

    public static File backupFile(File filesDir, long nowMillis) {
        return new File(backupDir(filesDir), BACKUP_PREFIX + timestamp(nowMillis) + BACKUP_SUFFIX);
    }

    public static String timestamp(long nowMillis) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(nowMillis));
    }

    public static List<File> oldBackupsToPrune(File backupDir) {
        File[] files = matchingBackups(backupDir);
        if (files == null || files.length <= MAX_BACKUPS) {
            return List.of();
        }
        Arrays.sort(files);
        int toDelete = files.length - MAX_BACKUPS;
        List<File> old = new ArrayList<>(toDelete);
        for (int i = 0; i < toDelete; i++) {
            old.add(files[i]);
        }
        return old;
    }

    private static File[] matchingBackups(File backupDir) {
        return backupDir.listFiles((dir, name) -> name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX));
    }
}
