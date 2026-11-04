# Desktop support resume checkpoint (2026-07-31)

> **Status:** Active. Supersedes `desktop-support-resume-2026-07-30.md`.
> Continues the same objective: complete the desktop roadmap through Goal 205
> in `plans/desktop-support-goals-2026-07-26.md`.

## Workspace

- Worktree: `/local/home/skerraut/work/kani-desktop-integration`
- Push target: `origin/desktop/support` (HEAD in sync; latest push `3b8c9ebe`)
- **Do not use `/local/home/skerraut/kanji_anki`.** It is stale.
- Android SDK on this machine: `/home/skerraut/android-sdk`. Root `CLAUDE.md`
  names `/tmp/android-sdk`, which is not what this worktree uses — set
  `ANDROID_HOME=ANDROID_SDK_ROOT=/home/skerraut/android-sdk`. The pure-JVM
  `:data-desktop`/`:backup-core`/`:core`/`:data-sql` checks do not need the
  Android SDK at all.

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

### Goal 187 (AnkiConnect transport) — DONE except the live handshake

New `:provider-ankiconnect` module (pure JVM, deps `{platform-contracts,
sync-api}`, 100% class coverage), wired into `ciDesktop` + boundary contracts:

```text
86c61571 AnkiConnectEndpoint (loopback-only fail-closed URL validation) + AnkiConnectActions (outbound allowlist)
5143b80a AnkiConnectJson (bounded dependency-free codec) + AnkiConnectEnvelope (v6 request/response, multi, redaction)
d2b54507 AnkiConnectTransport + JdkHttpExchange (bounded loopback HTTP, redirects off, deadlines, byte cap)
7aaba4e5 AnkiConnectHandshake (keyless-first requestPermission -> version -> apiReflect -> getActiveProfile)
4dd096a5 AnkiConnectKeyStore (API-key lifecycle over SecretStore, never plaintext/DB)
3ea8ba87 AnkiConnectRequests + AnkiConnectReads (typed read requests + fail-closed result parsers)
34a01817 FakeAnkiConnectServer + AnkiConnectFailureMatrixTest (malformed/oversize/protocol/auth/version/missing over a real loopback socket)
```

Only outstanding Goal 187 item: the read-only live handshake against a running
Anki/AnkiConnect session (needs Anki desktop open).

### Goal 188 (collection reads / normalization) — core started

```text
27b6696c AnkiConnectReadPlanner (250k id cap, clamped/adaptive detail batches) + AnkiConnectCardNormalization (queue<0 suspension, ord==0)
```

Remaining for Goal 188: assemble the reads into the provider-neutral snapshot
shape.

**Resolved, and the concern recorded here was wrong.** This section previously
claimed the snapshot mapping could not happen inside `:provider-ankiconnect`
because `RecordsSyncModels.CollectionSnapshot` lives in `:core`. It can:
`:sync-api` depends on `:sync-domain`, which depends on `:core`, so `:core`'s
types are transitively visible to `:provider-ankiconnect` through its allowed
`{platform-contracts, sync-api}` dependencies. The mapping now lives in
`AnkiConnectCollectionReader`, and shared cross-provider normalization lives in
`ProviderCardPolicy` in `:sync-domain` — where both providers can reach it.
`tools/test_module_boundaries.py` passes.

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
