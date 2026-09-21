# Desktop support resume checkpoint (2026-07-29)

> **Status:** Completed and superseded on 2026-07-29. Goals 175-177 passed
> their aggregate, foreground, and strict copied-collection provider gates.
> Canonical completion evidence now lives in
> `plans/desktop-support-goals-2026-07-26.md`.

## Branch and scope

- PR: `https://github.com/bee-san/kanji_anki/pull/592`
- Push target: `origin/desktop/support`
- This checkpoint consolidates Goals 175-177, the live-test harness fix, the
  concurrent `bee-fsrs` vendoring work, and the FSRS-7 scheduler commit.
- The original instruction not to mark Goals 175-177 complete is superseded:
  the resumed combined tree completed all required gates and the implementation
  was committed at `9cd7fb70`.
- The old `/local/home/skerraut/kanji_anki` worktree has a duplicate,
  uncommitted copy of the Goal 175 harness change. Resume from a fresh
  worktree based on `origin/desktop/support`, not from that stale worktree.

## Evidence already obtained

- Goal 175 focused progress test:
  `testManualSyncShowsLiveCardProgress` passed as `OK (1 test)` in 221.864s.
- Goal 175 deterministic gate before later commit consolidation:
  `ciFast ciQuality` completed `BUILD SUCCESSFUL` in 57s with 195 tasks.
- Strict standalone provider-host gate against AnkiDroid 2.24.0 and the copied
  user collection passed `OK (54 tests)` in 4,122.632s.
- Live inventory result: 12,480 notes, 34 models, 3,463 unique kanji, zero
  skipped notes.
- The disposable Missing Kanji create/render/retry/delete case passed.
- The non-destructive queue probe was rejected with
  `IllegalArgumentException`; the reread queue remained unchanged.
- Desktop source database:
  `/home/skerraut/anki/collection.anki2`
  SHA-256
  `b7679490a8313e37d1dc14d48f05593bcd99252c79b20d18be9d8bfee568d143`.
  It was never modified; all live work used the emulator copy.

## Interrupted foreground gate

- The first foreground attempt hit an Android startup watchdog ANR while ART
  verified the fresh debug APK. It was not an app exception.
- Running `cmd package compile -m speed -f` for the app and test packages
  produced full verified VDEX artifacts on this image and fixed startup.
- The second attempt reached the real Sync screen, clicked `Sync cards`,
  persisted `source_binding_first_bind_required`, found and clicked
  `Use this collection`, and began the validated post-bind full import.
- At the user's stop request, the post-bind card scan was still active. The
  app was force-stopped, so this attempt is not passing evidence.
- Output files:
  `/tmp/goal175-private-provider-live-final.txt` and
  `/tmp/goal175-foreground-live-final.txt`.

## Resume steps

1. Create a clean sibling worktree from `origin/desktop/support`.
2. Run `git diff --check`, module-boundary tests, and the full combined
   `ciFast ciQuality ciDesktop sonarPreflight` gate. The consolidated
   FSRS/Goals 176-177 tree has not yet run that aggregate gate.
3. Rebuild and install the app plus instrumentation APK, clear
   `dev.bee.kanjianki`, re-grant
   `com.ichi2.anki.permission.READ_WRITE_DATABASE`, and preverify both
   packages before instrumentation:

   ```sh
   adb -P 5040 -s 127.0.0.1:16001 shell cmd package compile -m speed -f dev.bee.kanjianki
   adb -P 5040 -s 127.0.0.1:16001 shell cmd package compile -m speed -f dev.bee.kanjianki.test
   ```

4. Rerun only
   `MainActivityInstrumentedTest#testManualSyncButtonWorksAgainstLiveAnkiDroid`
   with `kanjiLiveAnkiDroid=true`. Wait for `OK (1 test)`.
5. Query the debuggable app database with on-device `sqlite3` and verify a
   persisted opaque source binding, latest successful sync row, nonempty
   dashboard, and nonempty study queue. Do not print raw binding values.
6. Because Goal 176 changes production sync orchestration, rerun the strict
   copied-collection provider/live gate on the consolidated tree, even though
   the Goal 175-only provider gate above passed.
7. Finish Goal 177 before recording completion evidence. The prepared commits
   create `:platform-android` and `:automation-android`, but the remaining
   notification, lifecycle, SAF/share, reminder, updater, and media adapter
   ownership must be checked against every Goal 177 work item.
8. Add Goal 175-177 completion evidence to
   `plans/desktop-support-goals-2026-07-26.md`, then continue sequentially at
   Goal 178.

## Emulator

- Dedicated ADB server: port `5040`.
- Device: `127.0.0.1:16001`.
- AVD: `kanji_anki_api35_private`, Android 15, software acceleration.
- AnkiDroid: `2.24.0`.
- The provider-host test package was uninstalled before the foreground run.
  App and app-test packages were installed when execution stopped.
