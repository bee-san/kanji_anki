# Database Backups, Export & Restore

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

## Export a backup from the app

Automatic archives are app-private and disappear if the device or app data is
lost. To keep a copy elsewhere:

1. Open **Settings → Automation → Backup & restore**.
2. Tap **Export now**. Kani first makes a fresh, transactionally consistent
   snapshot on its background I/O executor; it does not merely copy an open WAL
   database.
3. Choose a location in Android's system document picker. The exported file is
   named `kanji_anki_simple_YYYYMMDD_HHmmss.db.gz` and can be saved to any
   document provider offered by the device.

The panel also shows the latest automatic-backup time and how many private
archives are currently retained. Android cloud backup remains deliberately
disabled; an SAF export is the supported way to take a copy outside Kani.

## Restore a backup in the app

The primary restore path is **Settings → Automation → Backup & restore → Restore
from backup…**. Pick a `.db.gz` file, review the destructive-action confirmation,
and tap **Restore and close Kani**. Restore is unavailable while a manual
AnkiDroid sync is running.

Kani validates a selected file before offering the confirmation. Validation is
fail-closed and requires all of the following:

- the file is readable gzip data whose decompressed bytes start with the
  16-byte `SQLite format 3\0` header;
- the SQLite database opens read-only and `PRAGMA quick_check` returns `ok`;
- the `settings` table is present (the Kani database sentinel); and
- `PRAGMA user_version` is no newer than the schema supported by the installed
  Kani build. Older backups are allowed and migrate normally on first open.

After confirmation, Kani atomically stages the validated, decompressed database
under private `files/restore/`, closes, and exits. At the next process start —
before an activity, receiver, worker, or diagnostic helper can open the live
database — Kani:

1. writes a normal timestamped pre-restore safety snapshot to `files/backups/`;
2. atomically moves the staged database over `databases/kanji_anki_simple.db`;
3. deletes stale `kanji_anki_simple.db-wal` and `kanji_anki_simple.db-shm`
   sidecars; and
4. removes the restore marker.

The marker makes this sequence idempotent. If the process stops after any step,
the next process start safely retries or completes the remaining cleanup. If the
pre-restore safety snapshot cannot be written, Kani leaves the current database
in place and retries the staged restore on a later start.

## Recovery restore with adb

If Kani cannot reach its settings screen, the adb recovery path remains
available for a device/emulator where `run-as` (or root) is permitted:

```sh
# 1. Pull a backup off the device (path is app-private; requires run-as/root).
adb exec-out run-as dev.bee.kanjianki \
  cat files/backups/kanji_anki_simple_YYYYMMDD_HHmmss.db.gz > backup.db.gz

# 2. Decompress locally.
gunzip backup.db.gz   # -> backup.db

# 3. Stop the app so no process is holding the database open.
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
Do not skip the force-stop or sidecar removal: both are required for a safe
manual swap.
