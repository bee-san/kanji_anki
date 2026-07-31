# Desktop support resume checkpoint (2026-07-31)

> **Status:** Active. Supersedes `desktop-support-resume-2026-07-30.md`.
> Continues the same objective: complete the desktop roadmap through Goal 205
> in `plans/desktop-support-goals-2026-07-26.md`.

## Workspace

- Worktree: `/local/home/skerraut/work/kani-desktop-integration`
- Push target: `origin/desktop/support` (HEAD in sync; latest push `3b8c9ebe`)
- **Do not use `/local/home/skerraut/kanji_anki`.** It is stale.
- Android SDK on this machine: `/tmp/android-sdk` (root `CLAUDE.md` path). The
  pure-JVM `:data-desktop`/`:backup-core`/`:core`/`:data-sql` checks do not need
  the Android SDK, but setting `ANDROID_HOME`/`ANDROID_SDK_ROOT` is harmless.

## Done and pushed since the 2026-07-30 checkpoint

Goals 179–183 remain done (see prior checkpoint + roadmap evidence). New this
session:

### Goal 185 (backup core) — commits 1–2 done, commit 3 now unblocked

- `:backup-core` module extracted: `RestoreMarkerCodec`, `BackupSpaceBudget`,
  `PortableBackupSanitizer`, and (new) `CrossPlatformRestorePlanner`.
- `DeviceSettingKeys.portableExclusionStorageNames` added in
  `:platform-contracts`; `BackupRestoreStager.markerState` delegates to the
  shared codec.
- Commit 3 (cross-platform compatibility fixtures + instrumented restore) can
  now build on the `:data-desktop` host below.

### Goal 186 (desktop profile storage) — data host substantially complete

All in `:data-desktop` (pure JVM, 100% class coverage, proven end-to-end on the
production bundled SQLite driver), plus one planner in `:backup-core`:

```text
ad4fb71a DesktopStorageLayout + DesktopProfileRegistry
0d92a4b4 BundledSqlDriver + DesktopDatabaseFactory
05450be5 DesktopProfilePreflightPolicy
c5438b85 DesktopProfileLock (exclusive FileLock)
fe8e9adc DesktopProfileProvisioner (0700/0600 hardening)
60263186 DesktopProfileOpener (probe→preflight→provision→lock→open)
6decd3d3 DesktopBackupSnapshotter (VACUUM INTO + gzip, atomic publish)
78b47e9e DesktopBackupManager (tiered 7-daily/4-weekly retention)
e21fc5ea DesktopBackupRestoreValidator (bounded gzip + read-only checks)
7ffa2a5f DesktopStagedRestoreApplier (safety backup, marker, atomic replace)
0e8c8f9b CrossPlatformRestorePlanner + DesktopCrossPlatformRestoreFinalizer
3b8c9ebe docs: Goal 186 evidence
```

Backup/restore is proven round-trip: populated v34 DB → gzip snapshot → restore
swaps the live DB while preserving a pre-restore safety backup; the finalizer
drops a foreign host's device-local `settings` rows and keeps portable ones.

### Goal 187 (AnkiConnect transport) — security core + protocol layer started

New `:provider-ankiconnect` module (pure JVM, deps `{platform-contracts,
sync-api}`, 100% class coverage), wired into `ciDesktop` + boundary contracts:

```text
86c61571 AnkiConnectEndpoint (loopback-only fail-closed URL validation) + AnkiConnectActions (outbound allowlist)
5143b80a AnkiConnectJson (bounded dependency-free codec) + AnkiConnectEnvelope (v6 request/response, multi, redaction)
```

Remaining for Goal 187: the bounded JDK `HttpClient` transport (redirects
disabled, post-resolution loopback enforcement, connect/request deadlines,
bounded bodies, cancellation, redacted diagnostics), the `requestPermission`/
`version`/`apiReflect`/`getActiveProfile` handshake + `SecretStore` wiring, the
in-process fake server test matrix, and the read-only live handshake against a
local Anki session.

Also updated the fast/desktop CI gates and `tools/test_module_boundaries.py` /
`tools/test_desktop_ci_gates.py` for every new module; `ciFast` and `ciDesktop`
both pass with SDK `ANDROID_HOME=/home/skerraut/android-sdk`.

## What remains

- **Goal 186 tail (product wiring, not core logic):** call
  `DesktopProfileOpener`/`DesktopStagedRestoreApplier` from desktop app startup;
  expose export/import through the desktop file picker; Windows ACL hardening
  (POSIX path is done). These need the `:desktop-app` composition root
  (Goal 200) or a thin host harness to validate.
- **Goal 185 commit 3:** cross-platform compatibility fixtures + instrumented
  restore, now buildable on `:data-desktop`.
- **Goal 187 tail + Goals 188–205:** AnkiConnect transport HTTP client +
  handshake (rest of 187), reads/writes/equivalence (188–191), shared
  presentation + Android host (192–199), desktop composition root (200),
  reminders/tray (201), update handoff (202), Study keybindings (203), native
  distributions (204), CI + live integration coverage (205).

## Genuinely blocked (need the user)

- **Goal 183 tail:** real licensed reference-asset binaries (dictionary/rank/
  stroke/font) to replace placeholder hashes in `ReferenceAssetManifest.bundled()`,
  plus host wiring. Blocked on the user supplying binaries + licensing.
- **Goal 184 (flip Android production to shared SQL):** requires porting ALL
  shared-table writers first (FSRS-fit, theme, missing-kanji-publish, backup/
  restore) to avoid dual-write, then the strict live AnkiDroid gate on the
  user's ~7,000-note collection (authorization + emulator/collection setup).

## Standing constraints (verbatim)

- Keep Android production on `LocalStore` until Goal 184; do not switch runtime
  composition yet.
- Do not cut a release for provider/sync changes unless the live AnkiDroid
  instrumentation suite and local production gate both pass.
- Kani never writes Anki scheduling state; supported provider write surface is
  note-tags-only + the additive Missing Kanji flow.
- Use a throwaway emulator copy of the user's collection; never modify the
  desktop collection directly.

## Pre-existing unrelated failure

`:data-android:jacocoDebugUnitTestCoverageVerification` fails at 0.82 in a plain
JVM `check` (its framework driver needs `androidTest`); pre-existing, module
untouched this session.
