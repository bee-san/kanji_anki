# Database Backups, Export & Restore

The app keeps periodic local backups of its SQLite database
(`kanji_anki_simple.db`) via `DatabaseBackupWorker`.

Live backup, fresh export, and starting an in-app restore require Android 11
(API 30) or later. Stock Android 8–10 bundles SQLite versions older than 3.27,
where `VACUUM INTO` is unavailable. Kani therefore fails closed on API 26–29:
it cancels periodic backup work, disables both Settings actions, and leaves the
current database and every existing archive unchanged. It never falls back to
copying the main file of an open WAL database.

## What gets written

- Backups live in the app's private files directory under `backups/`.
- Each new backup is a gzip-compressed, transactionally consistent standalone
  snapshot produced only with `VACUUM INTO`. Filenames look like
  `kanji_anki_simple_YYYYMMDD_HHmmss.db.gz`.
- Compression writes and fsyncs a same-directory `.partial` file before a
  strict same-filesystem atomic replace publishes the final name. If snapshot,
  compression, or publication
  fails, the partial is removed and any prior final archive is preserved.
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
disabled; an SAF export is the supported way to take a copy outside Kani. Kani
finishes and fsyncs its private gzip before opening the picker. Android document
providers do not expose a portable atomic-write contract, so a provider failure
can still leave an empty or partial document at the user-selected external
location; Kani reports the failure and never changes its database or completed
private archives.

## Restore a backup in the app

The primary restore path is **Settings → Automation → Backup & restore → Restore
from backup…**. Pick a `.db.gz` file, review the destructive-action confirmation,
and tap **Restore and close Kani**. Restore is unavailable while a manual
AnkiDroid sync is running, and new restores cannot be staged on API 26–29.

Kani validates a selected file before offering the confirmation. Validation is
fail-closed and requires all of the following:

- gzip decompression is streamed rather than buffered, produces at most
  512 MiB, and leaves a 64 MiB free-space reserve; oversize and insufficient
  space are reported separately;
- the file is readable gzip data whose decompressed bytes start with the
  16-byte `SQLite format 3\0` header;
- the SQLite database opens read-only and `PRAGMA quick_check` returns `ok`;
- the `settings` table is present (the Kani database sentinel); and
- `PRAGMA user_version` is no newer than the schema supported by the installed
  Kani build. Older backups are allowed and migrate normally on first open.

After confirmation, Kani atomically stages only the validated, decompressed
database under private `files/restore/`, closes, and exits. At the next process start —
before an activity, receiver, worker, or diagnostic helper can open the live
database — Kani:

1. re-syncs the staged-file directory and writes a durably published,
   timestamped pre-restore safety snapshot to `files/backups/`;
2. writes, fsyncs, and atomically publishes a versioned `SAFETY_READY` recovery
   marker, then fsyncs the restore directory;
3. strictly atomically moves the staged database over
   `databases/kanji_anki_simple.db`;
4. fsyncs the database directory and then the restore directory so both sides
   of that cross-directory rename are durable;
5. deletes stale `kanji_anki_simple.db-wal` and `kanji_anki_simple.db-shm`
   sidecars, then fsyncs the database directory; and
6. removes the restore marker, then fsyncs the restore directory.

The durable state machine is `none → staged only (before/while safety backup) →
SAFETY_READY marker + staged → SAFETY_READY marker only → none`. The ready
marker certifies that the safety archive is durable. A ready marker plus a
staged file retries the replacement without opening SQLite; ready marker-only
uniquely follows the strict staged-to-live rename and performs only sidecar
cleanup. Any marker-bearing failure blocks startup so the learner cannot create
new progress that a later restore would discard. Marker-missing failures remain
pre-replacement and may safely leave the current database open. There is
deliberately no non-atomic move/copy fallback.

An already-staged marker-free restore on API 26–29 is preserved but not applied,
because Kani cannot create its mandatory safety snapshot there. Any
marker-bearing state blocks startup on those APIs because it cannot safely be
classified. A versioned ready marker-only state can finish sidecar cleanup on
every supported app API. An unversioned legacy marker-only state is ambiguous:
older builds published that marker before replacement and did not fsync parent
directories, so Kani preserves the live database and sidecars and blocks for
manual recovery instead of guessing.

## Device verification (2026-07-10; API 35)

The production UI path was exercised end to end on the release emulator, not
only through the file-operation seams. **Export now** opened Android
DocumentsUI through `CreateDocument`, saved an 8,520-byte gzip in Downloads,
and the pulled artifact passed `gzip -t`, had the exact
`SQLite format 3\0` header, returned `ok` from `PRAGMA quick_check`, and
contained the Kani `settings` table.

For restore, a copy with a sentinel setting and `user_version = 28` was selected
through the production `OpenDocument` picker. Under the then-current protocol,
**Restore and close Kani** ended the app process with both the staged database
and marker present.
A fresh
ordinary activity launch applied the swap before opening the activity, removed
the staged artifacts, created the timestamped pre-restore safety gzip, migrated
the database to version 29, passed `quick_check`, and exposed the sentinel. This
shell-observed process boundary complements the instrumented startup-hook test,
which cannot kill its own instrumentation process. This evidence was collected
on API 35 and does not validate stock API 26–29 SQLite. The later fail-closed
API boundary, safety-before-marker state machine, directory durability, and
strict-atomic failure paths are covered by
deterministic JVM/Robolectric tests and Android-test compilation; they still
need device validation before a release containing those changes.

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

Current API-30+ backups are produced with `VACUUM INTO`, so the decompressed
file is a fully checkpointed standalone database and needs no WAL sidecars of
its own. Archives created by older builds through the removed checkpoint/copy
fallback are unverified; create a fresh API-30+ export before relying on one for
recovery.
Do not skip the force-stop or sidecar removal: both are required for a safe
manual swap.

## Platform basis

- AOSP's bundled-version table lists SQLite 3.18/3.19/3.22 for API 26/27/28,
  and Android 10 ships 3.22: [AOSP table](https://android.googlesource.com/platform/frameworks/base/+/41d294845b9bc58bddaf8798cc841c03e7f7367f/core/java/android/database/sqlite/package.html),
  [Android 10 version](https://android.googlesource.com/platform/external/sqlite/+/refs/tags/android-10.0.0_r1/README.version).
- SQLite added `VACUUM INTO` in 3.27, while stock API 30 carries 3.28:
  [SQLite 3.27 release](https://www.sqlite.org/releaselog/3_27_0.html).
- Android's ordinary replacing move may unlink the destination before a
  rename/copy, so restore accepts only `ATOMIC_MOVE`:
  [Android libcore implementation](https://android.googlesource.com/platform/libcore/+/refs/tags/android-8.0.0_r1/ojluni/src/main/java/sun/nio/fs/UnixCopyFile.java).
