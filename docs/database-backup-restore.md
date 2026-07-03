# Database Backups & Manual Restore

The app keeps periodic local backups of its SQLite database
(`kanji_anki_simple.db`) via `DatabaseBackupWorker`.

## What gets written

- Backups live in the app's private files directory under `backups/`.
- Each backup is a gzip-compressed, transactionally consistent snapshot produced
  with `VACUUM INTO` (falling back to a WAL-checkpoint + file copy on older
  SQLite). Filenames look like `kanji_anki_simple_YYYYMMDD_HHmmss.db.gz`.
- Compression typically shrinks a SQLite DB ~4-10x.

## Retention

Tiered retention (`DatabaseBackupPolicy.oldBackupsToPrune`):

- The newest `KEEP_DAILY` (7) backups are always kept.
- Beyond that, one backup per calendar week is kept, up to `KEEP_WEEKLY` (4)
  additional weeks.
- Everything else is pruned. This bounds storage to roughly 7 + 4 compressed
  archives instead of 31 full daily copies of a growing database.

Legacy uncompressed `*.db` backups from older builds are still recognized and
pruned by the same policy.

## Manual restore

There is no in-app restore path. To restore a backup onto a device/emulator with
`adb`:

```sh
# 1. Pull a backup off the device (path is app-private; requires run-as/root).
adb exec-out run-as dev.bee.kanjianki \
  cat files/backups/kanji_anki_simple_YYYYMMDD_HHmmss.db.gz > backup.db.gz

# 2. Decompress locally.
gunzip backup.db.gz   # -> backup.db

# 3. Stop the app so it is not holding the database open.
adb shell am force-stop dev.bee.kanjianki

# 4. Push the decompressed DB back over the live database.
adb push backup.db /data/local/tmp/kanji_anki_simple.db
adb shell run-as dev.bee.kanjianki cp /data/local/tmp/kanji_anki_simple.db \
  databases/kanji_anki_simple.db
# Remove stale WAL/SHM sidecars so SQLite reopens cleanly.
adb shell run-as dev.bee.kanjianki rm -f \
  databases/kanji_anki_simple.db-wal databases/kanji_anki_simple.db-shm
```

Because backups are produced with `VACUUM INTO`, the decompressed file is a
fully checkpointed standalone database and needs no WAL sidecars of its own.
