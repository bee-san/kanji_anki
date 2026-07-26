# Desktop Support Goals

Status: execution plan

Prepared: 2026-07-26

Planning baseline: `main` at `b0d9e5d3` / `v0.4.232`

Database baseline: schema v33, integrity-first scheduler introduced in v31

Local integration availability: Linux host with the user's live Anki Desktop
session available for read-only connection/sync checks

Primary objective: make Kani a supported Android, Windows, Linux, and macOS
application without weakening its scheduler, sync, persistence, backup, or
release guarantees.

This file is intended to be usable as a long-running Codex `/goal`. It makes
the product decisions, dependency order, commit boundaries, validation gates,
and stop conditions explicit so an implementation agent does not have to
invent them while changing the repository.

## Definition of done

Desktop support is complete only when all of the following are true:

1. The existing Android app remains supported on its current API range, keeps
   package identity `dev.bee.kanjianki`, and passes all current local, CI,
   release, and live AnkiDroid gates.
2. Windows 10/11 x64, Ubuntu 20.04-or-newer x64, and macOS 13-or-newer arm64
   have native, self-contained Kani packages built on their respective
   operating systems. Refresh this matrix against the pinned Compose
   Multiplatform release in Goal 166 before treating it as final.
3. A desktop user can install Kani, connect to a running Anki Desktop through
   AnkiConnect, configure a note type and fields, sync, see the dashboard,
   study, persist and undo reviews, browse stats, use Missing Kanji, export and
   restore backups, and restart without data loss or duplicate review commits.
4. Android and desktop use the same scheduler/policy modules, the same
   repository contracts, the same SQL operation and migration implementation,
   and shared Compose feature UI. Only platform adapters differ.
5. Desktop never reads or writes Anki's live `collection.anki2` directly.
   AnkiConnect is the only supported desktop Anki boundary.
6. Kani never writes Anki scheduling state. Existing note-tag writes remain
   idempotent and failure-isolated. The accepted Missing Kanji exception
   remains limited to additive notes in Kani's dedicated model/deck with CSV
   as a complete fallback.
7. Stock AnkiConnect's missing FSRS memory fields are represented as an
   explicit unavailable capability. Kani preserves its documented
   interval/lapse fallback without labeling those derived values as provider
   FSRS fields, and never fabricates retrievability or a provider-supplied
   stability/difficulty value.
8. Kani state is local to each installation in the first desktop release.
   Portable whole-database backup transfer is supported, but simultaneous file
   sync and seamless Android/desktop progress merge are not claimed.
9. Every published desktop artifact is built from the release tag's exact
   commit, independently smoke-tested after installation, checksummed, covered
   by an authenticated release manifest, and signed where the operating system
   provides a production signing chain.
10. User and operator documentation describes installation, AnkiConnect,
    state ownership, backup transfer, capability differences, recovery,
    updates, and the tested support matrix honestly.

## Plan relationship and supersession

The completed work in
`docs/plans/2026-07-20-architecture-modernization-goals.md` remains valid:

- Goal 145: Android module conventions and architecture guardrails.
- Goal 146: persistence contract dependency cleanup.
- Goal 147: feature-oriented repository APIs.

Goals 148-163 in that file have not been implemented at this baseline. This
plan supersedes their Android-only target shape while retaining their intent:

| Previous goals | Status in this plan |
| --- | --- |
| 148, process-owned `KaniContainer` | Reworked as Goal 170 with Android and desktop composition roots. |
| 149-150, `:data` extraction and store composition | Reworked as Goals 169 and 178-185 so Android and desktop share SQL operations behind platform drivers. |
| 151-154, feature state ownership | Reworked as Goals 192-200 with portable reducers, immutable UI state, and host adapters. |
| 155-160, Android `:ui-common`, navigation, and feature extraction | Superseded by Compose Multiplatform Goals 192-199. |
| 161, `:sync-android` | Superseded by `:sync-api`, `:sync-engine`, `:provider-ankidroid`, and `:provider-ankiconnect` in Goals 174-191. |
| 162, `:automation` | Split into Android and desktop platform services in Goals 177 and 201. |
| 163, `:widget` | Retained as an Android-only module cleanup in Goal 199; desktop uses a tray adapter, not an Android-style widget. |

The statement in `plans/android_rewrite.md` that desktop is a non-goal is
superseded by this file. Its older Room/Hilt rewrite ideas are not revived.
The existing manual dependency injection, pure JVM scheduler foundation, and
release invariants remain the starting point.

Goal numbering starts at 164 so it does not collide with completed or reserved
architecture work.

## Current-state evidence

This plan was prepared against the live repository, not from a hypothetical
greenfield architecture:

- Included modules are `:fsrs-java`, `:core`, `:domain`, `:sync-domain`,
  `:writing-core`, `:dictionary-core`, `:update-core`, and `:app`.
- The seven non-app modules contain roughly 252 portable JVM source files.
  They are already usable by both an Android JVM host and a desktop JVM host.
- `:app` contains 378 Kotlin files. About 251 import Android or AndroidX APIs,
  while roughly 85 Compose-oriented files are plausible shared-UI candidates
  after Activity, resource, and platform-effect dependencies are removed.
- The Activity inheritance chain still owns major product behavior:

  ```text
  MainActivity
    -> MainActivitySettings
    -> MainActivityStudy
    -> MainActivityStats
    -> MainActivityGames
    -> MainActivityMissingKanji
    -> MainActivityHome
    -> MainActivityBase
    -> MainActivityUiSupport
    -> ComponentActivity
  ```

- The five repository interfaces created in Goal 147 exist, but production
  paths still commonly call the `LocalStore` inheritance chain directly.
- `CollectionGateway` looks portable but exposes
  `AnkiDroidGateway.SyncFailure`, `AnkiDroidGateway.RemovalSummary`, and
  app-local progress types.
- `ManualSyncEngine` still owns Android `Context`, resources, logging,
  `LocalStore`, widgets, reminders, and provider-specific failures.
- `LocalStoreBase` extends `SQLiteOpenHelper`; data code directly uses
  `SQLiteDatabase`, `Cursor`, `ContentValues`, and Android transaction
  behavior.
- `LocalStoreSchema.DB_VERSION` is 33. V31 carries the adaptive scheduler
  contract, v32 mnemonic state, and v33 Missing Kanji state. Desktop must
  preserve all of them.
- The build currently uses Gradle 9.4.1, AGP 9.1.0, Kotlin 2.0.21, Java 17,
  and the Android Compose toolchain. The app module uses AGP's built-in
  kotlinc, while the catalog Kotlin version owns the JVM and Compose compiler
  plugins.
- Successful `Android CI` on `main` automatically starts Android release
  publication. Incomplete desktop work must stay on a feature branch/worktree
  unless the user explicitly authorizes a merge.
- On 2026-07-26, a no-key, read-only loopback probe to the user's running Linux
  AnkiConnect returned `{"result": 6, "error": null}` for the `version`
  action. No profile name, note/card content, or write action was queried. This
  proves only current API-v6 reachability, not the Goal 187 capability/auth gate
  or the isolated-profile Goal 191 live qualification.
- At plan creation, `loop.md` and `plans/learning.md` are untracked
  user-owned files. They are out of scope and must never be staged, edited, or
  deleted by this plan.

## Product decisions

### Supported platforms

The initial supported matrix is deliberately narrower than "anything with a
JVM":

| Platform | Initial supported target | Package |
| --- | --- | --- |
| Android | Existing API 26+ contract | Signed APK through the existing release flow |
| Windows | Windows 10/11, x64 | Signed MSI |
| Linux | Ubuntu 20.04+, x64 | DEB plus a portable tar archive if its installed-image smoke test is reliable |
| macOS | macOS 13+, Apple silicon | Developer-ID-signed, notarized, stapled DMG |

Windows arm64, Linux arm64, RPM/AppImage/Flatpak, macOS x64, iOS, and web are
future targets. Do not advertise them until they have native runners,
dependency verification, installation/upgrade tests, and a support owner.

### Desktop Anki boundary

Desktop Kani talks to Anki Desktop through AnkiConnect API v6 on loopback.

- Default endpoint: `http://127.0.0.1:8765`.
- Allow loopback literals and `localhost` only, resolve every hostname to
  loopback, and bypass system HTTP proxies. Do not follow redirects.
- Do not support a remote or LAN AnkiConnect endpoint in the first release.
- Support an optional API key. It must never enter Kani-controlled logs, the
  Kani database, diagnostics, crash output, backups, command-line arguments,
  or process titles. Kani cannot govern AnkiConnect's own add-on/Anki logs, so
  setup documentation must disclose that boundary and recommend checking the
  pinned add-on's logging behavior before using a sensitive key.
- Use bounded requests, response-size limits, connect/read deadlines,
  cooperative cancellation, retry classification, and progress reporting.
- Treat the JSON response's `error` field as failure even when HTTP status is
  200.
- Call `requestPermission` without a key to discover permission/API-key
  requirements, then probe `version` and `apiReflect` after authentication is
  established. Validate the response shape of every required action instead of
  assuming every AnkiConnect installation is compatible.
- Pin the tested Anki Desktop version, AnkiConnect commit/archive SHA, API
  version, and required action list in the live-test fixture.

The planning audit used current AnkiConnect source at commit
`de6e6e1b8aaf4ae195eb1d1ff6db5409b99b2a3e` as a reference. Goal 187 must
refresh that evidence before implementation.

One Kani database is bound to one Anki source identity:

- A shared `SourceBindingPolicy` evaluates candidates from every provider
  before mirror publication or writes; this is not an AnkiConnect-only check.
- The AnkiConnect adapter calls `getActiveProfile` before sync.
- The AnkiDroid adapter includes provider kind and selected provider authority
  in its candidate, then performs a read-only stable-ID overlap probe. Provider
  authority alone is never sufficient evidence after a cross-platform restore
  or explicit provider change.
- Store only an opaque binding derived from provider kind, the active profile,
  a database-local random binding salt, and stable collection ID-overlap
  evidence; do not expose the raw profile name in diagnostics.
- A changed/renamed active profile blocks sync before mirror publication or
  provider writes. The UI offers returning to the bound Anki profile, creating
  a new local Kani profile, or an explicit rebind flow with backup and overlap
  validation. Desktop uses its profile registry; Android first creates a
  durable safety backup, archives the current active database, and initializes
  a fresh active database rather than silently overwriting the old state. That
  Android new-profile/rebind path is available only on API 30+, where the
  required snapshot contract exists; API 26-29 fails closed and offers no
  destructive in-app fallback.
- Switching provider kind after a cross-platform restore requires explicit
  revalidation. Matching note/card ID overlap may confirm the source; provider
  kind or model ID alone may not.
- Missing Kanji destination keys and write receipts include the provider
  binding. After a binding/platform change, reconcile stable external
  `SourceId` values instead of trusting a local receipt.

### FSRS capability policy

Stock AnkiConnect's `cardsInfo` supplies card/note identity, fields, deck,
queue/type, due, interval, factor, repetitions, lapses, and related metadata,
but not the FSRS stability, difficulty, and retrievability values consumed by
Kani's richest AnkiDroid seeding path.

The desktop contract is:

1. Provider snapshot fields for FSRS memory are nullable and accompanied by
   `FSRS_MEMORY_STATE`.
2. The AnkiDroid adapter declares that capability only when the real provider
   supplied the values. The AnkiConnect adapter does not declare it.
3. Missing values are never mapped to zero, defaults, or inferred "real" FSRS
   memory.
4. Admission evidence that only needs interval, lapses, suspension, or mature
   support may continue to use those real fields.
5. Preserve the current `AdmissionEvidencePolicy` fallback: mature active
   evidence may still seed directly into review, stability may fall back from
   provider FSRS stability to the real Anki interval, and difficulty may use
   the existing lapse-adjusted fallback. These are Kani policy outputs, not
   provider FSRS values.
6. Provider-FSRS-specific weak filtering and prioritization are unavailable;
   lapse/interval evidence and existing Kani scheduler state remain usable.
7. UI and diagnostics describe this as reduced import precision, not a sync
   failure.
8. An optional Kani companion add-on that exposes read-only FSRS fields is a
   separate post-GA project. It is not silently bundled into the first desktop
   scope and may not write Anki scheduling state.

Golden policy tests must cover both capability-present and capability-absent
timelines.

### Kani state and multi-device behavior

Anki is the source collection, but Kani's review history, adaptive repair
state, revision tokens, scheduler memories, local suspensions, settings, and
analytics are Kani-owned.

For the first desktop release:

- Android and desktop installations are independent Kani profiles.
- A whole-database backup can replace another installation's Kani state after
  both writers are closed and the destination validates the backup.
- The UI warns before replacement and identifies the schema and source
  filename. A timestamp inferred only from a filename is labelled
  informational, not authenticated backup metadata.
- Cross-platform product settings live in the portable database. Secrets,
  window bounds, run-at-login registration, package/update staging, and other
  host integrations live in platform configuration outside that database and
  are recreated or reconfigured on the destination.
- A cross-platform restore clears/reinitializes device-local keys, marks the
  current provider mirror/dashboard stale, and blocks provider writes until
  the destination Anki profile binding is validated and a successful sync
  completes. Kani-owned review state is preserved.
- A live SQLite database, WAL, or application-data directory must never be
  synchronized with Dropbox, OneDrive, Syncthing, Anki media, or another
  file-sync service.
- No UI may imply that AnkiWeb sync also merges Kani progress.
- True concurrent progress sync is deferred to Goal 208 and requires an event
  protocol, not file copying.

### Handwriting

`writing-core` remains shared. Android continues to use the existing ML Kit
digital-ink recognizer.

The first desktop release does not invent recognition results and does not
turn recognizer failure into a pass. Until a licensed, offline Japanese
recognizer passes a separate quality gate:

- Desktop provides the shared ink canvas for guides and practice.
- `WRITE_RECOGNITION` is false for scheduled desktop study.
- A pure scheduler capability policy filters `write_kanji` before task
  selection and deterministically selects the next enabled compatible repair
  or core revalidation. The returned selection/debug trace records
  `write_kanji_unavailable_on_platform`.
- It does not mark writing as passed, change the user's stored repair
  enablement/order, create a review/timeline token, mutate scheduler state, or
  discard the underlying failure cause.
- Moving a backup to Android makes the repair available again.

This is a deliberate scheduler parity decision. Goal 196 must update the
adaptive scheduler documentation and regenerate all affected golden
timelines.

### Platform-specific features

- Android widgets remain Android-only.
- Desktop provides an optional system tray due count and actions. If the
  platform/session has no tray, the app remains fully usable without it.
- Desktop reminders and auto-sync run while Kani is active or when the user
  explicitly enables run-at-login. The app must not claim closed-app
  scheduling when it has not installed an OS integration.
- Android WorkManager behavior remains unchanged.
- Desktop update v1 checks authenticated release metadata, asks for
  confirmation, downloads/verifies the correct installer, and opens it. It
  does not silently replace the running application.
- Missing Kanji keeps CSV as a complete output. Direct Anki creation remains
  additive, idempotent, and restricted to the accepted `Kani Missing Kanji`
  model and `Kani::Missing Kanji` deck.

### Out of scope for desktop GA

- iOS, web, ChromeOS-specific, Windows arm64, Linux arm64, and untested Linux
  package formats.
- A Kani cloud backend, accounts, telemetry, remote control, or new collection
  content upload.
- Remote/LAN AnkiConnect, direct Anki database access, embedding Anki Desktop,
  or bundling an unreviewed AnkiConnect copy.
- A Room/Hilt rewrite, wholesale conversion of the existing JVM core to
  `commonMain`, scheduler tuning unrelated to platform capability, or a visual
  redesign.
- Silent self-replacement, differential patching, or background OS services
  that continue after the user has disabled run-at-login.

### Required parity/capability matrix

This is the product contract the implementation and final documentation must
encode:

| Capability | Android | Desktop GA |
| --- | --- | --- |
| Collection transport | AnkiDroid content provider | Loopback AnkiConnect v6 |
| Configured sync/browser query | Supported | Supported |
| Provider FSRS memory import | When supplied by provider | Explicitly unavailable in stock AnkiConnect; existing interval/lapse fallback with reduced weak-card precision |
| Archive/repaired tag write | Supported under current confirmation rules | Supported under the same rules |
| Missing Kanji inventory/CSV | Supported | Supported |
| Missing Kanji direct creation | Provider spec/capability gated | AnkiConnect action/capability gated |
| Scheduler and Study | Canonical shared policy | Canonical shared policy |
| Automated handwriting recognition | ML Kit Japanese model | Unavailable; capability-routed, practice canvas only |
| Backup/restore | Existing API-gated durable path | Durable platform adapter on supported filesystems |
| Widget | Glance widget | Not applicable |
| Tray | Not applicable | Optional when desktop session supports it |
| Reminders/auto-sync | WorkManager/Android services | In process; closed-app behavior only with opt-in run-at-login |
| Updates | Verified APK flow | Verified native-installer handoff |
| Kani state across devices | Local Android profile | Independent local desktop profile; manual whole-backup replacement only |

## Non-negotiable invariants

### Scheduler and review persistence

- Preserve the integrity-first adaptive two-core design and all rules in
  `AGENTS.md` and `docs/adaptive-two-core-scheduler.md`.
- Keep exactly two long-term core memories: recognition at `kanji_meaning` and
  contextual reading at `word_reading`.
- Repairs remain inline practice on the owning item. Never create a second
  scheduler queue or enqueue new `similar_kanji_repair_queue` rows.
- `type_reading` remains repair-only with no FSRS memory.
- A real-due core Fail invokes FSRS Again once; repair never changes long-term
  stability/lapses; revalidation stays bounded to one day.
- Review persistence remains token-first and revision-CAS.
- Item state, review evidence, timeline, task timing, choice state/log, and
  stats dirtiness remain in one transaction.
- Only `APPLIED` may advance session/UI state. `DUPLICATE` and `STALE` must
  retain their exact semantics.
- Undo remains token deletion plus revision-CAS restoration.
- Stats cache format 11 remains the live analytics contract.

### SQL and publication

- Preserve schema v33, `user_version`, table/index shape, migration order,
  downgrade behavior, and backup compatibility.
- Use one serialized writer initially. Read concurrency is allowed only after
  deterministic busy/locking tests.
- The shared transaction manager must implement nested work through
  `SAVEPOINT`, `ROLLBACK TO`, and `RELEASE`; nested plain `BEGIN` is not an
  acceptable substitute for Android's current nested transaction behavior.
- Translate `ContentValues` conflict rules to explicit SQL. In particular,
  review-token insertion must distinguish a duplicate token from another
  constraint failure.
- Provider mirror publication, derived inventory/dashboard state, queue
  reconciliation, and successful sync history remain atomic.
- A review committed during sync must win over an older staged scheduler
  revision.
- Only successful sync runs feed historical consumers.
- Cache invalidation and events happen after commit, never from unpublished
  state.

### Provider writes

- Archive and repaired writes are note-tag-only, idempotent, per-note,
  failure-isolated, and retryable.
- Repaired tagging stays opt-in and manual-confirm-only.
- Kani never writes queue, due, interval, ease, deck options, suspension,
  FSRS state, or another scheduling field.
- The Missing Kanji writer may create only Kani-owned additive deck/model/note
  content under the accepted capability contract. It never rewrites a user's
  model and reconciles partial writes by stable `SourceId`.
- A write-back failure may not roll back or fail an otherwise committed
  collection sync.

### Backup and restore

- Preserve the Android platform gate: API 30+ supports fresh automatic
  snapshots/export/restore; API 26-29 preserves existing archives but disables
  fresh snapshot/export/restore. Never restore the removed main-file copy
  fallback.
- Preserve gzip whole-database portability, the 512 MiB decompression cap,
  64 MiB free-space reserve, SQLite magic and schema validation,
  `PRAGMA quick_check`, safety snapshot, versioned `SAFETY_READY` marker,
  stale WAL/SHM handling, and fail-closed startup.
- Preserve exactly seven daily and four weekly automatic archives.
- `VACUUM INTO` runs outside a transaction under the maintenance/writer gate
  and targets a path that does not already exist.
- Backups publish through a same-directory `.partial`, data flush, and atomic
  replacement. A failed snapshot/publication preserves the prior final file.
- Picker input is streamed into private same-filesystem staging; restoration
  never attempts to atomically move an arbitrary external picker path directly
  over the live database.
- Restore closes every database connection and holds the process profile lock.
- POSIX adapters fsync the file and both relevant parent directories around
  atomic replacement.
- Windows uses a tested atomic replacement/write-through mechanism. If the
  required durability cannot be proven on supported NTFS systems, restore is
  disabled on Windows rather than falling back to a copy-over-live-database
  implementation.

## Target architecture

The existing scheduler/policy modules remain ordinary JVM libraries. Do not
turn the 31,000-line `:core` module into `commonMain` just to claim a pure KMP
architecture.

```text
Existing shared JVM policy
  :fsrs-java
  :core
  :domain
  :sync-domain
  :writing-core
  :dictionary-core
  :update-core

New shared JVM application/data/sync
  :data-api
  :data-sql
  :backup-core
  :reference-assets
  :sync-api
  :sync-engine
  :platform-contracts
  :application

Shared Compose Multiplatform presentation
  :presentation-api
  :ui-common
  :feature-shell
  :feature-home
  :feature-study
  :feature-stats
  :feature-settings
  :feature-games
  :feature-missing-kanji

Android adapters and host
  :data-android
  :provider-ankidroid
  :platform-android
  :automation-android
  :widget
  :app

Desktop adapters and host
  :data-desktop
  :provider-ankiconnect
  :platform-desktop
  :desktop-app
```

Dependency direction:

```text
:feature-* -> :ui-common, :presentation-api
:feature-shell -> all feature modules, :ui-common, :presentation-api

:app -> :feature-shell, :application, :data-android,
        :provider-ankidroid, :platform-android, :automation-android, :widget

:desktop-app -> :feature-shell, :application, :data-desktop,
                :provider-ankiconnect, :platform-desktop

:data-api -> :core, :sync-domain
:sync-api -> :core, :sync-domain
:application -> :core, :data-api, :sync-engine, :platform-contracts,
                :reference-assets, :update-core
:sync-engine -> :sync-api, :data-api, :core, :sync-domain, :dictionary-core,
                :platform-contracts
:data-sql -> :data-api, :core, :sync-domain, :dictionary-core
:backup-core -> :data-api, :platform-contracts
:reference-assets -> :dictionary-core, :writing-core
:data-android -> :data-sql, :backup-core
:data-desktop -> :data-sql, :backup-core
:provider-ankidroid -> :sync-api, Android APIs
:provider-ankiconnect -> :sync-api, :platform-contracts, JVM HTTP/JSON APIs
```

Rules:

- KMP `commonMain` UI does not depend on the current JVM policy modules.
  Shared composables and reducers consume presentation DTOs and capability/use
  case ports; Android and desktop hosts implement those ports using the shared
  JVM application layer.
- Keep `RecordsSyncModels` in `:core` as the canonical sync settings,
  note/card, and collection snapshot model during this conversion.
  `:sync-api` depends on `:core` and adds provider capabilities, failures,
  progress, inventory, and write summaries around those models. Do not create
  a second provider snapshot hierarchy. A later core-model cleanup would be a
  separate mechanical goal.
- `:application` owns the authoritative Study session state machine, item
  index, reveal/task phase, commit/recovery state, and calls into the
  scheduler/repositories. KMP presentation reducers may own only ephemeral UI
  state and one-shot effect consumption; they may not duplicate session
  progress, tokens, revisions, or commit authority.
- `:data-sql` owns schema, migrations, prepared SQL, repository behavior, and
  transaction semantics. `:data-android` and `:data-desktop` provide narrow
  connection, path, locking, and durability adapters.
- No feature module depends on another feature module. `:feature-shell` is the
  only feature aggregator.
- No shared module imports `Activity`, `Context`, `ContentResolver`,
  `SQLiteDatabase`, WorkManager, Glance, ML Kit, SAF, Android resource IDs,
  AWT/Swing, system tray APIs, or installer APIs.
- Platform effects return typed results and are invoked through ports. Shared
  UI never receives a platform context or raw exception.
- `:data-api`/`:data-sql` own portable product settings only. Updater/install
  state, OS scheduler timestamps, notification delivery history, window/tray/
  run-at-login state, provider endpoint/auth, and other host integrations use
  a `DeviceSettingsStore` port implemented by each platform. Shared data
  modules do not depend on `:update-core`.

## Data ownership

Current provider-derived/rebuildable state may be regenerated after a
successful source validation and is excluded from any future multi-device
merge protocol:

- current provider source notes/cards and derived current fingerprint;
- dashboard/example/inventory derivations;
- similar/reading content pools;
- derived stats caches.

Local provider history and write-back coordination survive backup/restore but
remain source-specific and are excluded from a future cross-device merge
unless that protocol defines explicit semantics:

- successful `sync_runs` and historical sync snapshots;
- import audits/decisions and source-binding evidence;
- `suspended_archive`, archive/repaired receipts, destination keys, and
  handoff state;
- Missing Kanji export receipts, reconciled against external `SourceId` after
  a provider/platform change.

Kani-authoritative state must survive backup/restore and would need explicit
semantics in any future event-sync protocol:

- `study_items`, review logs, task logs, timeline events, and revision tokens;
- learning repeats and compatibility choice/practice state;
- local suspensions, mnemonic notes, and manual kanji sources;
- portable scheduler/import/study/appearance settings.

Device-local state is never imported as active state on another platform:

- pending APK/installer/install-permission state and update staging paths;
- next-run/retry timestamps tied to an OS scheduler;
- notification delivery/throttle history;
- run-at-login, tray, window, and platform capability state;
- provider endpoint, provider permission/authority, and authentication secret;
- cache/log/temp paths and file-picker grants.

## External baselines to refresh

Goal 166 or the first goal that consumes each dependency must refresh and
record the official evidence. Do not silently bump versions while executing a
later feature goal.

- Kotlin/Gradle/AGP compatibility:
  <https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html>
- Compose Multiplatform versions and target support:
  <https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html>
- Compose compiler setup:
  <https://kotlinlang.org/docs/multiplatform/compose-compiler.html>
- AGP built-in Kotlin migration/coupling:
  <https://developer.android.com/build/migrate-to-built-in-kotlin>
- Android Kotlin version support:
  <https://developer.android.com/build/kotlin-support>
- AGP 9 Kotlin Multiplatform Android library plugin:
  <https://developer.android.com/kotlin/multiplatform/plugin>
- AndroidX bundled SQLite multiplatform support:
  <https://developer.android.com/kotlin/multiplatform/sqlite>
- Compose native distributions and `jpackage`:
  <https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html>
- JDK 17 `jpackage` prerequisites:
  <https://docs.oracle.com/en/java/javase/17/jpackage/packaging-tool-user-guide.pdf>
- Compose Desktop accessibility:
  <https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html>
- Current AnkiConnect source:
  <https://git.sr.ht/~foosoft/anki-connect>
- Windows package verification:
  <https://learn.microsoft.com/en-us/windows/win32/seccrypto/signtool>
- Apple Developer ID/notarization:
  <https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution>
- GitHub hosted runner images:
  <https://docs.github.com/en/actions/reference/runners/github-hosted-runners>
- GitHub downstream workflow trigger rules:
  <https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow>

The planning target is Kotlin 2.4.10 and Compose Multiplatform 1.11.1 because
the current Compose release requires Kotlin 2.1 or newer and that pair is
compatible with the current Gradle/AGP range at plan time. This is a
time-sensitive implementation baseline, not permission to skip the refresh.

## `/goal` execution contract

1. Work in a clean sibling worktree such as
   `/home/bee/Documents/src/github/kanji_anki-desktop-support`, based on the
   current `origin/main`. Do not mutate the user's dirty primary checkout.
   If this plan is not yet in `origin/main`, copy it into the worktree and make
   `docs: add the desktop support execution plan` the first branch commit
   before Goal 164 work.
2. Preserve `loop.md`, `plans/learning.md`, and every unrelated user artifact.
3. Use a branch named `desktop/support` unless the user names another branch.
4. Execute goals in numerical order. A later goal may start only after the
   current goal's required gates pass and its completion evidence is appended
   to this file.
5. Keep Android releasable after every goal. Compatibility adapters may remain
   temporarily, but there may be no knowingly broken intermediate commit.
6. Use the exact planned commit subjects in order where the described split
   remains accurate. If review forces a split, retain the prefix and record
   the revised subjects in completion evidence.
7. Do not combine toolchain, schema, scheduler behavior, provider behavior,
   UI migration, and release workflow changes in one commit.
8. Never use `--no-verify`, disable dependency verification, weaken lint,
   lower coverage gates, skip workflow contract tests, or replace the live
   gates with mocks.
9. Do not merge, tag, publish, release, enable a remote service, or rotate/add
   signing secrets unless the user explicitly authorizes that external state
   change. Local commits and focused branch pushes are allowed only when the
   active user request authorizes them.
10. Every completion evidence entry records:
    - starting commit and branch;
    - commits created;
    - files/modules moved;
    - commands and outcomes;
    - Android emulator/desktop live-test evidence when required;
    - behavior/schema/provider decisions changed or confirmed;
    - rollback path;
    - validation gaps or external blockers.
    Commit that append as `docs: record Goal N completion evidence`, naming the
    exact preceding implementation SHA that passed the gates. End every goal
    with a clean worktree.
11. If a goal uncovers a necessary product decision not made here, stop that
    goal, write the alternatives and evidence into this file, and ask the
    user. Do not choose a materially broader product on their behalf.
12. Goal 207 is the end of the desktop-support objective. Goal 208 is a
    separately authorized future epic and must not be started automatically.
13. Goal 168 is an external-authorization checkpoint because Windows/macOS
    verification metadata and CI require pushing the feature branch. If the
    `/goal` invocation does not explicitly authorize that push, stop at the
    checkpoint and request it; do not weaken or fake cross-OS validation.

## Milestones and critical path

| Milestone | Goals | Exit evidence |
| --- | --- | --- |
| Rebased foundation | 164-168 | Architecture decisions, goldens, compatible toolchain, desktop smoke app, and three-OS CI. |
| Android behind portable boundaries | 169-177 | Production Android uses repository/provider/sync/platform ports and still passes the strict live provider gate. |
| Shared persistence | 178-186 | Android and desktop drivers pass the same schema, transaction, backup, and restore corpus; Android has already switched to shared SQL. |
| Supported desktop provider | 187-191 | Fake and real AnkiConnect gates pass with explicit capability differences and safe writes. |
| Shared product UI and hosts | 192-200 | Android and desktop use shared features; desktop completes the end-to-end product journey. |
| Desktop platform quality | 201-205 | Automation, updates, accessibility/input/performance, native packages, CI, and live integration are qualified. |
| Production release | 206-207 | Signed/notarized assets, independent verification, full release journeys, and support documentation. |

The critical path is sequential through Goal 200. Documentation, test fixture
hardening, and platform package experiments may run in parallel only when they
do not edit the same files or allow a later goal to bypass an unmet exit gate.

## Standard validation

Run the smallest focused tests while editing, then the applicable aggregate
gate before committing.

Every goal:

```sh
git diff --check
python3 -m unittest tools.test_module_boundaries
```

Every Android or shared JVM/data/sync goal:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast ciQuality
```

After Goal 168 creates them, every desktop/shared UI goal:

```sh
./gradlew ciDesktop
```

Before a release candidate:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast ciQuality
./gradlew ciDesktop
./gradlew ciAll
python3 -m unittest tools.test_release_workflows
python3 -m unittest discover -s ci/tests -p 'test_*.py'
```

Provider/sync behavior, shared sync normalization/publication, or Android data
changes used by sync require the strict live AnkiDroid gate from `AGENTS.md`
against AnkiDroid 2.24.0 and the copied real collection with the default
7,000-note threshold. The baseline targeted result is `OK (62 tests)`; if the
suite intentionally gains tests, update the expected count with evidence.

Desktop provider/sync changes require the deterministic fake AnkiConnect suite.
Goals 191, 205, 206, and 207 also require a real Anki Desktop/AnkiConnect run
against a throwaway copied or sanitized profile.

The local running Linux Anki instance may be used from Goal 187 onward for
read-only version/capability/configured-sync checks with redacted aggregate
evidence. Do not issue `addTags`, `createDeck`, `createModel`, `addNotes`, or
another write action against the user's active profile. Goal 190 write tests
and the full Goal 191 gate use a throwaway copied/sanitized profile, preferably
on an isolated AnkiConnect port, without stopping or reconfiguring the user's
live session unless they separately authorize that interruption.
The read-only live harness uses a transport-level action allowlist that omits
every write action, so automatic archive/repaired post-sync behavior cannot
accidentally reach the personal profile.

---

## Part A: Rebaseline and build foundations

### Goal 164: Adopt the desktop product and architecture contract

**Depends on:** completed Goals 145-147.

**Outcome:** The repository has one authoritative desktop decision record and
no active plan tells an implementation agent to build an incompatible
Android-only architecture.

**Work:**

- Add this plan to the architecture index/roadmap.
- Update the status header of
  `docs/plans/2026-07-20-architecture-modernization-goals.md` so Goals 148-163
  point here without deleting their historical content.
- Mark only the desktop non-goal in `plans/android_rewrite.md` as superseded.
- Add ADRs for AnkiConnect-only desktop access, independent Kani profiles,
  FSRS capability absence, shared SQL/platform drivers, handwriting
  capability routing, and independent Android/desktop release publication.
- Reconcile `AGENTS.md`, `docs/adaptive-two-core-scheduler.md`, and
  `docs/anki-manual-parity-checklist.md`: normal sync/write-back remains
  note-tag-only, while the already-accepted Missing Kanji flow is a narrowly
  scoped additive writer for Kani's dedicated model/deck. Keep the absolute
  prohibition on Anki scheduling writes.
- Extend `tools/test_module_boundaries.py` with the target graph and explicit
  migration edges. It must fail closed on unknown project dependencies.

**Planned commits:**

1. `docs: define the desktop product and parity contract`
2. `docs: reconcile the pending architecture goals`
3. `test: encode the Android and desktop module graph`

**Done when:** Every old pending goal is retained, amended, or superseded in a
table; the boundary test distinguishes migration and final graphs; no runtime
behavior changes.

### Goal 165: Freeze conversion baselines and golden fixtures

**Depends on:** Goal 164.

**Outcome:** Later moves can prove behavior preservation instead of comparing
against memory.

**Work:**

- Record current module/source counts, Gradle configuration, clean and
  incremental compile timings, startup timing, and current aggregate test
  counts.
- Capture schema v33 fingerprints: tables, columns, indexes, triggers,
  `user_version`, representative constraints, and settings/cache versions.
- Add deterministic generated fixtures for fresh v33 and migrations from the
  oldest retained fixture plus v30, v31, and v32.
- Freeze review `APPLIED`/`DUPLICATE`/`STALE`, undo, mid-sync review,
  successful-history-only, WAL snapshot, restore fault, and scheduler golden
  timelines.
- Capture representative Android screenshots and semantics for every route,
  including loading/error/empty/data states and font-scale variants.
- Capture sanitized fake-provider snapshots covering configured sync, browser
  query, suspension, archive/repaired tags, collection inventory, Missing
  Kanji, nullable FSRS fields, malformed rows, and cancellation.

**Planned commits:**

1. `test: freeze schema and transaction baselines`
2. `test: freeze provider scheduler and UI baselines`
3. `docs: record desktop conversion performance baselines`

**Done when:** Fixtures are generated without personal collection content,
fail when an invariant is perturbed, and pass on the untouched production
implementation.

### Goal 166: Align Kotlin and Compose compiler ownership

**Depends on:** Goal 165.

**Outcome:** The repository has an officially supported Kotlin/AGP/Gradle/
Compose combination before Compose Multiplatform code is added.

**Work:**

- Refresh the official compatibility links in this plan.
- Target Kotlin/KGP and `org.jetbrains.kotlin.plugin.compose` 2.4.10,
  `org.jetbrains.compose` 1.11.1, and the latest compatible AGP 9.1 patch
  (planning target 9.1.1) unless the refreshed official matrix requires a
  newer compatible patch.
- Keep AGP built-in Kotlin in `:app`; do not apply
  `org.jetbrains.kotlin.android`. Apply the Kotlin Compose compiler plugin to
  every Compose module, including `:app`, so compiler ownership is explicit
  and tested.
- Keep the shared `kotlin-jvm`/`kotlin-compose` version reference coupled.
- Resolve one Compose runtime graph deliberately. Compose Multiplatform 1.11.1
  maps to a specific Jetpack Compose line that may differ from the current
  Android BOM; record dependency insight for compiler, runtime, UI, Material,
  and Skiko and remove incompatible mixed resolutions.
- Use one shared Compose Multiplatform Navigation graph for both hosts. At plan
  time its 2.9.2 line maps to Jetpack Navigation 2.9.7, while the unused
  Android architecture pin is 2.9.8. Align/remove the old pin rather than
  running two navigation graphs or silently resolving a mixed version.
- Update Gradle dependency verification only for reviewed artifacts.
- Expand build-logic TestKit fixtures and warning-as-error compilation.

**Planned commits:**

1. `build: align Kotlin and Compose Multiplatform versions`
2. `build: make Android compiler ownership explicit`
3. `test: cover the aligned build toolchain`

**Required validation:** `testBuildLogic`, `ciFast`, `ciQuality`, wrapper
validation, strict dependency verification, and clean no-build-cache Android
Compose compilation.

**Done when:** The refreshed compatibility decision is recorded, every
compiler/plugin owner is explicit, the clean Android and build-logic gates
pass, and strict verification contains only reviewed artifacts.

**Stop condition:** If the refreshed official matrix does not support the
selected Gradle/AGP/Kotlin pair, stop and record the smallest supported upgrade
set. Do not force the build with compatibility flags.

### Goal 167: Add desktop and multiplatform build conventions

**Depends on:** Goal 166.

**Outcome:** Empty desktop and shared-UI fixtures compile through reusable
conventions without rewiring production.

**Work:**

- Add `kani.desktop-application-conventions`.
- Add `kani.multiplatform-compose-library-conventions`.
- The shared convention applies `org.jetbrains.kotlin.multiplatform`,
  `com.android.kotlin.multiplatform.library`, `org.jetbrains.compose`, and
  `org.jetbrains.kotlin.plugin.compose`.
- Use the AGP 9.1 `kotlin { android { ... } }` DSL. Do not combine KMP with
  `com.android.library`/`com.android.application`, and do not use the
  superseded `androidTarget` setup.
- Explicitly enable/configure the Android host/device test source sets needed
  by shared UI modules because the Android-KMP plugin does not enable the old
  Android library test surface implicitly.
- Keep `:desktop-app` a normal Kotlin/JVM Compose application; only shared UI
  libraries use KMP.
- Centralize Java 17, warning policy, test framework, Compose resources,
  desktop target name, coverage, package metadata, and reproducible archive
  settings.
- Register a minimal `:desktop-app` that opens a "Kani desktop foundation"
  window and supports `--smoke-test --temporary-data` without touching the
  user's real profile.
- Set stable identifiers:
  - application/package name `Kani`;
  - desktop ID/bundle ID `dev.bee.kanjianki.desktop`;
  - one generated-and-committed Windows upgrade UUID that never changes.
- Add canonical vector/icon sources and deterministic `.png`, `.ico`, and
  `.icns` generation/verification. Do not manually maintain divergent icons.
- Add a fixture KMP Compose library with Android and desktop targets.

**Planned commits:**

1. `build: add desktop and multiplatform conventions`
2. `build: register the desktop application foundation`
3. `feat: add the Kani desktop smoke launcher`
4. `build: fix desktop package identity and icon sources`

**Done when:** The empty application compiles and launches on Linux; Android
remains unchanged; build conventions are covered by TestKit; package identity
is pinned by tests.

### Goal 168: Establish desktop CI and aggregate tasks early

**Depends on:** Goal 167.

**Outcome:** Every later desktop commit receives fast feedback on all supported
host operating systems.

**Work:**

- Add root `ciDesktop`, `ciDesktopPackage`, and `ciAll` tasks. Keep `ciFast`
  and `ciRelease` Android-specific. `ciAll` is only the aggregate for the
  current host; it never claims that a Linux invocation validated Windows or
  macOS packages.
- Add `.github/workflows/desktop-ci.yml` with:
  - Ubuntu 24.04 x64;
  - Windows 2025 x64;
  - macOS 15 arm64;
  - JVM/shared tests on all hosts;
  - current-OS installed-image/package smoke tests where available.
- Extend the internal changed-path classifier for every existing/new shared
  module, build logic, version catalog, wrapper, CI script, resource, and
  workflow input.
- Use an always-present lightweight `Desktop confidence gate`; conditionally
  skip expensive matrix jobs inside the workflow rather than relying on a
  workflow-level path filter that can leave a required check pending.
- Add all desktop/shared/build/release paths to Android CI's `main` push
  filter. This ensures every globally versioned releasable commit still enters
  the existing `Android CI -> Android Release -> Desktop Release` chain.
  For any such `main` push, the full existing deterministic Android CI surface
  must run before auto-release; do not satisfy the release trigger with a
  no-op/skipped Android aggregate. Android validation/publication remains
  independent of desktop signing.
- Keep all actions full-SHA pinned and validate the wrapper before Gradle.
- Bootstrap verification metadata in an explicitly authorized temporary
  matrix: run `--write-verification-metadata sha256` independently on all
  three hosts, upload the three results, merge/review them deterministically,
  commit the combined metadata, remove every write flag/bootstrap path, then
  run the permanent strict matrix. Assert
  `git diff --exit-code -- gradle/verification-metadata.xml`.
- Document that three-OS procedure in `docs/dependency-updates.md`. Do not
  weaken verification to make a matrix pass.
- Add workflow contract tests for runner labels, task names, action pins,
  cache boundaries, untrusted PR behavior, and artifact retention.

**Planned commits:**

1. `build: add desktop and aggregate confidence gates`
2. `ci: add the cross-platform desktop matrix`
3. `test: lock desktop CI and dependency verification invariants`

**Done when:** All three hosts compile the smoke app and run its temporary-data
mode; strict verification succeeds without ignored artifacts; Android release
semantics are unchanged and its expanded push-path coverage is pinned by
workflow tests.

---

## Part B: Put Android behind shared application boundaries

### Goal 169: Extract `:data-api`

**Depends on:** Goal 168 and completed Goal 147.

**Outcome:** Both hosts can depend on repository contracts without depending
on `:app` or Android SQLite.

**Work:**

- Move `HomeRepository`, `StudyRepository`, `StatsRepository`,
  `SettingsRepository`, `SyncRepository`, repository snapshots, commands,
  `StoreResult`, review commit models, and focused fakes into `:data-api`.
- Create the minimal pure JVM `:platform-contracts` module now with
  `DeviceSettingsStore` and its typed device-setting keys. Keep a temporary
  Android implementation in the current app host; Goal 177 moves that
  implementation without changing values.
- Split settings before moving them: `SettingsRepository` exposes only
  portable scheduler/import/study/appearance state; use
  `DeviceSettingsStore` for updater/install state, OS scheduler timestamps,
  notification history, provider endpoint/permission/auth references,
  window/tray/run-at-login, and host paths.
- Add a compatibility migration that reads current device-local keys from the
  legacy SQLite settings surface once, writes them to the Android device
  store, then removes/ignores them from portable backup state without changing
  user-visible Android behavior.
- Remove Android, database implementation, Activity, resource, and provider
  types from their public signatures.
- Keep atomic review and sync publication as one repository call each.
- Make model ownership explicit when an existing type belongs in `:core`,
  `:sync-domain`, or `:data-api`.
- Adapt `:app` imports without changing production call paths yet.

**Planned commits:**

1. `refactor: introduce the device settings platform contract`
2. `refactor: split portable and device-local settings`
3. `refactor: extract the portable data repository API`
4. `test: enforce data API implementation boundaries`

**Done when:** `:data-api` is a pure JVM module, public APIs expose no raw SQL
or Android type, the app-host `DeviceSettingsStore` has completed the
compatibility migration, fakes remain usable, and `ciFast ciQuality ciDesktop`
pass.

### Goal 170: Introduce process-owned composition roots

**Depends on:** Goal 169.

**Outcome:** Activity recreation no longer owns database/executor lifetime, and
desktop has a matching startup model.

**Work:**

- Add a pure JVM `:application` module for cross-host use cases,
  coordinators, and portable state machines. It may depend on shared policy
  and API modules but not either host.
- Introduce `KaniContainer` and narrow dependency-owner interfaces.
- On Android, construct the container only after `StagedRestoreApplier`
  permits database-capable startup.
- Move user-I/O and maintenance executors/dispatchers from
  `MainActivityBase` into the process container.
- Make `KaniApplication` own the Android container.
- Add a WorkerFactory and narrow receiver/service access without new global
  mutable test overrides.
- Treat AndroidX Startup/WorkManager configuration as pre-`onCreate` entry
  points: configuration getters and initializer discovery must not construct
  the container or open SQLite. Worker dependencies resolve lazily only after
  the staged-restore gate.
- Define a desktop container lifecycle contract: acquire profile lock, apply
  staged restore, open data, start services, build presentation, then release
  in reverse order.
- Add deterministic test containers and lifecycle/failure-order tests.
- Add instrumentation that launches initializers/work requests during cold
  start and proves no provider, initializer, worker, or receiver opens the
  database before restore is allowed.

**Planned commits:**

1. `refactor: add the shared application module`
2. `refactor: add the process-owned Kani container`
3. `refactor: move Android dependency lifetime out of the activity`
4. `test: cover startup restore and shutdown ordering`

**Done when:** Rotation/recreation does not close process resources; Android
components share one graph; no database opens before the restore gate.

### Goal 171: Route Home, Settings, Stats, Browse, and Games through repositories

**Depends on:** Goal 170.

**Outcome:** Non-study UI no longer calls `LocalStore` directly.

**Work:**

- Replace direct reads/writes with `HomeRepository`, `SettingsRepository`, and
  `StatsRepository`.
- Move loading, cancellation, error mapping, and immutable snapshots into
  application use cases.
- Keep platform effects such as clipboard, file picker, intents, and
  notifications outside repositories.
- Add fake-repository state tests for empty/loading/data/retry/recreation.
- Remove compatibility methods only after all callers migrate.

**Planned commits:**

1. `refactor: route Home and Browse through repositories`
2. `refactor: route Settings Stats and Games through repositories`
3. `test: cover repository-backed non-study state`

**Done when:** Those routes contain no `LocalStore`, raw SQL, cursor, or
database imports and retain screenshot/golden behavior.

### Goal 172: Route Study through `StudyRepository`

**Depends on:** Goal 171.

**Outcome:** Study orchestration consumes a narrow atomic persistence port.

**Work:**

- Route queue loading, plan creation, review commit, undo, token recovery,
  repair state, and session refresh through `StudyRepository`.
- Preserve the one-call transaction boundary and only advance on `APPLIED`.
- Move the useful reducer/state from `StudySessionViewModel` into a portable
  authoritative `:application` state machine; keep a thin Android lifecycle
  wrapper. This state machine alone owns task/session progress, reveal state,
  review tokens/revisions, commit/recovery, and undo authority.
- Remove Activity ownership of authoritative progress, choice, writing,
  reveal, recovery, and plan state in independently reviewable slices.
- Add process-death, duplicate callback, stale revision, undo, and rapid-input
  tests.

**Planned commits:**

1. `refactor: route Study persistence through StudyRepository`
2. `refactor: extract the Study session state machine`
3. `test: prove token revision and recovery behavior`

**Done when:** Study production code does not call `LocalStore`; all golden
timelines and review transaction tests pass; rotation/process recreation
cannot double-commit.

### Goal 173: Route sync publication through `SyncRepository`

**Depends on:** Goal 172.

**Outcome:** Sync orchestration no longer reaches broad store internals.

**Work:**

- Make production use the existing atomic staged-publication repository path.
- Inject settings, clock, dictionary/media readers, queue planner, and
  post-commit effects.
- Preserve pending-run rollback, successful-history-only publication,
  mid-sync review reconciliation, provider failure isolation, and retry
  scheduling.
- Publish widget/reminder/UI events only after the repository returns a
  committed success.

**Planned commits:**

1. `refactor: route sync through SyncRepository`
2. `test: prove atomic sync publication through the repository`

**Required validation:** Full Android gates plus fake-provider tests. Run the
strict live AnkiDroid gate because the production sync path changes.

**Done when:** Every production sync publication flows through one
`SyncRepository` call, effects fire only after commit, and fake/live Android
results match the frozen baseline.

### Goal 174: Extract provider-neutral collection contracts

**Depends on:** Goal 173.

**Outcome:** No shared provider interface names or exposes AnkiDroid.

**Work:**

- Add `:sync-api`.
- Move/define:
  - `CollectionGateway`;
  - `CollectionInventoryGateway`;
  - `MissingKanjiWriter`;
  - `CollectionSourceStatus`;
  - `CollectionSourceIdentity` and the pure `SourceBindingPolicy`;
  - `SourceBindingStore`, `PersistedSourceBinding`, and binding decision/state
    types;
  - note-type/model descriptors;
  - a provider result envelope around canonical
    `RecordsSyncModels.CollectionSnapshot`, with nullable provider fields
    retained in that canonical model;
  - `CollectionCapability`;
  - progress/cancellation;
  - archive/repaired tag summaries;
  - typed failure kind and retryability.
- Use failure kinds `NOT_AVAILABLE`, `AUTH_REQUIRED`,
  `INVALID_CONFIGURATION`, `UNSUPPORTED_CAPABILITY`, `TRANSIENT`, and
  `CANCELLED`.
- Remove every `AnkiDroidGateway.*`, `Context`, provider URI, Activity, and
  app-local listener type from public contracts.
- Persist only a versioned opaque binding record in the portable database
  metadata/settings surface: provider-kind digest, active-profile/authority
  digest, database-local random binding salt, deterministic salted stable-ID
  sample digests, validation state, and last validation time. Never persist a
  raw desktop profile name merely for binding.
- Freeze the overlap algorithm in fixtures: sample the lowest 64 stable note
  IDs and lowest 64 stable card IDs after deterministic unsigned ordering. If
  at least 16 prior IDs exist, require at least 16 matches and at least 90%
  overlap; with 1-15 prior IDs, require every prior ID to match. No-ID,
  contradictory-profile, provider-kind-change, or below-threshold cases
  require an explicit backup-backed bind/rebind decision and may not auto-bind.
- Permit a genuinely empty Kani database to enter the explicit first-bind flow
  after a read-only provider probe. Never infer identity from provider kind,
  authority, profile display name, model ID, or deck ID alone.
- Add a canonical provider fixture and a reusable contract-test kit.
- Add provider-neutral binding tests for first bind, unchanged source, renamed
  display profile, provider-kind change, insufficient/contradictory overlap,
  explicit rebind, and unknown-origin restore.

**Planned commits:**

1. `refactor: introduce provider-neutral collection contracts`
2. `sync: add the provider-neutral source binding policy`
3. `data: persist opaque collection source bindings`
4. `test: add the collection gateway contract kit`

**Done when:** `:sync-api` is pure JVM, cancellation and retryability are
explicit, bindings round-trip without raw source names/IDs, and the Android
adapter still produces the baseline snapshot.

### Goal 175: Extract `:provider-ankidroid`

**Depends on:** Goal 174.

**Outcome:** Content-provider access has a compile-time Android boundary.

**Work:**

- Move `AnkiDroidGateway`, card reader, archive cleanup, repaired tagging,
  collection inventory, Missing Kanji writer, provider discovery/permission,
  and their fake-provider tests into `:provider-ankidroid`.
- Retain 512-card batching, projection/URI fallback, synthetic card-ID
  fallback, suspension-query behavior, nullable FSRS parsing, provider
  authority order, and provider-spec capability rules.
- Report an Android source-identity candidate from provider kind, selected
  authority, and read-only stable note/card ID evidence. After a restored or
  unknown binding, collect overlap evidence without writes and fail closed
  before publication if the shared binding policy cannot validate it.
- Before Goal 176 enables enforcement, migrate existing Android installations:
  a migration marker plus an existing successful Android mirror establishes
  the prior provider kind; use that mirror and a read-only live candidate to
  auto-bind only when the frozen overlap policy agrees, recording the currently
  selected authority as part of the new candidate. Authority alone never
  qualifies a migration. An empty database enters first-bind; insufficient or
  contradictory evidence enters `REVALIDATION_REQUIRED` without changing the
  mirror.
- Add the temporary current-Activity Home flow for first bind, mismatch, new
  local Kani profile, and backup-backed explicit rebind. A rebind requires a
  fresh safety backup, displays redacted source evidence, clears provider
  projections/write receipts only as specified by the binding policy, and
  never discards Kani scheduler state silently. Disable new-profile/rebind on
  Android API 26-29 because those releases cannot create the required safe
  snapshot; do not restore a main-file-copy fallback. Goal 194 ports this flow
  to the shared UI.
- Enable binding enforcement only after the compatibility migration and
  recovery UI are installed and tested in the same release.
- Keep per-note tag failures isolated and Missing Kanji reconciliation exact.
- Reject imports from this module in shared, desktop, or feature code.

**Planned commits:**

1. `refactor: extract the AnkiDroid provider adapter`
2. `sync: migrate and validate existing Android source bindings`
3. `ui: add Android source binding recovery`
4. `test: pin AnkiDroid compatibility behavior at the module boundary`

**Required validation:** `ciFast ciQuality`, fake-provider instrumentation, and
the strict copied-collection live AnkiDroid gate.

**Done when:** All Android provider production code and tests live behind
`:provider-ankidroid`, shared/feature/desktop modules cannot import it, and the
fake and live provider suites pass unchanged. Restored/unknown bindings also
fail closed before publication or writes when read-only overlap is insufficient,
while qualifying existing installs auto-migrate without a user-visible sync
regression.

### Goal 176: Define platform contracts and extract `:sync-engine`

**Depends on:** Goal 175.

**Outcome:** One shared sync orchestration runs against either provider.

**Work:**

- Extend the Goal 169 `:platform-contracts` module with ports for clock, logger,
  app directories, file picker, clipboard, share, external URL/browser,
  notification, lifecycle, database snapshot, secret storage, background
  scheduling, app events, reading media, and update delivery.
- Move read/analyze/normalize/publish/cleanup orchestration out of
  `ManualSyncEngine`.
- Inject `CollectionGateway`, repositories, clock, dictionary/media inputs,
  logger, cancellation, progress, and post-commit event hooks.
- Keep Android resource copy and effect delivery in host adapters.
- Make capability-aware provider-FSRS handling and the existing interval/lapse
  fallback explicit and pure.
- Run the shared `SourceBindingPolicy` before mirror publication and again
  before any provider write. An unbound source may enter an explicit first-bind
  flow; changed, unknown, or contradictory identity must never be silently
  accepted.
- Make provider write-back an isolated post-publication stage.
- Add deterministic tests for all failure boundaries, cancellation points,
  batching, retry classifications, staged rollback, and post-commit effects.

**Planned commits:**

1. `refactor: define platform service contracts`
2. `refactor: extract the platform-neutral sync engine`
3. `refactor: make sync provider capability fallbacks explicit`
4. `test: cover shared sync publication and failure isolation`

**Required validation:** All shared/Android gates and the strict live
AnkiDroid gate. Android sync output, queue, and history must match Goal 165
fixtures byte-for-byte or by an explicitly normalized equivalent.

**Done when:** Android uses `:sync-engine` in production, platform effects are
injected, both FSRS capability paths pass golden tests, every provider crosses
the same pre-publication/pre-write binding gate, and live Android output has no
unexplained difference.

### Goal 177: Extract Android platform adapters

**Depends on:** Goal 176.

**Outcome:** Shared application code has no hidden Android effect.

**Work:**

- Add `:platform-android` and `:automation-android`.
- Move the Goal 169 app-host `DeviceSettingsStore` compatibility
  implementation into `:platform-android`; retain its already migrated
  update/install state, WorkManager/retry timestamps, notification throttles,
  provider permission, and host integration state byte-for-byte.
- Move Android notification, SAF, clipboard/share, media, WorkManager,
  reminders, updater integration, and lifecycle adapters behind the ports.
- Keep all existing intent actions/extras, notification channels, PendingIntent
  compatibility, receiver behavior, and restore-before-open ordering.
- Keep widget code in `:app` temporarily until Goal 199.

**Planned commits:**

1. `refactor: extract Android platform and automation adapters`
2. `test: prove Android effect and lifecycle compatibility`

**Done when:** `:application`/`:sync-engine` can run under fakes with no Android
runtime; Android behavior and intent compatibility remain unchanged.

---

## Part C: Share persistence without rewriting the schema

### Goal 178: Freeze the canonical schema/migration corpus

**Depends on:** Goal 177 and Goal 165 fixtures.

**Outcome:** Android and desktop drivers have an executable compatibility
contract.

**Work:**

- Freeze a semantic schema manifest, settings/cache format constants, and
  schema fingerprint tools without porting production migrations yet.
- Keep v1-v33 migration history intact; do not squash it.
- Generate genuine databases from fresh schema and representative historical
  versions. A historical fixture contains the real old table/index shapes and
  data; changing only `PRAGMA user_version` on a v33 database is forbidden.
- Verify table/index/trigger SQL after normalization, `user_version`,
  representative row values, downgrade metadata, and compatibility tables.
- Inventory every time/default/platform dependency in existing migrations
  (including v9/v11 wall-clock use) for injection in Goal 179.
- Add cross-engine handling tests for SQLite features used by production.

**Planned commits:**

1. `test: publish the canonical v33 schema corpus`
2. `test: freeze genuine historical migration fixtures`

**Done when:** The existing Android implementation reproduces every corpus
fingerprint and a deliberate SQL drift fails with a useful diff.

### Goal 179: Add the shared SQL driver and transaction boundary

**Depends on:** Goal 178.

**Outcome:** Repository SQL can be shared while platform connection mechanics
remain replaceable.

**Work:**

- Add pure JVM `:data-sql` and define the driver/transaction abstractions
  before moving schema or migration execution into it.
- Add a non-production `:data-android` adapter skeleton so parity tests use
  the same module boundary that Goal 184 will switch into production.
- Define narrow prepared statement, row reader, connection, transaction,
  pragma, and snapshot interfaces using stable JVM types.
- Implement explicit binding for null, text, integer, real, and blob.
- Repository APIs remain suspendable at their outer boundary, but transaction
  callbacks are non-suspending and run on one dedicated writer
  dispatcher/thread and one physical connection.
- Outer writes use the Android-equivalent `BEGIN IMMEDIATE`; consistent read
  snapshots use `BEGIN DEFERRED`. Nested failure performs
  `ROLLBACK TO <savepoint>` followed by `RELEASE <savepoint>`.
- Every bind, statement, `changes()`, `last_insert_rowid()`, savepoint, and
  commit for one operation uses that physical connection. No
  provider/network/progress callback runs inside a database transaction.
- Implement serialized writes, busy timeout, WAL configuration, read
  snapshots, cancellation semantics, and nested savepoints.
- Add driver-neutral `SchemaManager` only after those primitives pass.
  Port migrations with injected `MigrationContext(clock, defaults)`. Run the
  complete upgrade and final `user_version` update in one transaction; change
  `user_version` only after all DDL/backfills succeed.
- Pin downgrade behavior: preserve tables/data, record
  `downgraded_from_version`, and test the resulting version semantics.
- Add Android adapter coverage against the current `SQLiteDatabase`.
- Qualify AndroidX `sqlite-bundled` as the preferred desktop candidate on
  Windows x64, macOS arm64, and the oldest supported Linux/glibc baseline,
  including `VACUUM INTO`, locking, and durability behavior. If it fails that
  matrix, retain the same SQL contract and select a pinned, licensed,
  vulnerability-reviewed JVM SQLite driver in an ADR rather than weakening
  semantics.
- Test constraint classification, `INSERT OR IGNORE` token handling, rollback
  at every statement, nested savepoint failure, busy/lock behavior, and
  resource closure. Include one-based bind indices, re-entrancy, cancellation
  before versus after commit, and writer-thread loss.

**Planned commits:**

1. `data: add the shared SQL driver contract`
2. `data: implement dedicated-writer transaction semantics`
3. `data: port schema and migrations through SchemaManager`
4. `test: prove Android and desktop SQLite driver parity`

**Stop condition:** If a required Android transaction/locking guarantee cannot
be represented, stop and revise the driver contract; do not paper over it
inside one repository.

**Done when:** Both driver adapters pass the same binding, savepoint,
constraint, rollback, locking, and closure contract without repository-specific
driver branches.

### Goal 180: Port settings, Home, and read-only repositories to `:data-sql`

**Depends on:** Goal 179.

**Outcome:** Low-risk repository slices validate the shared SQL approach.

**Work:**

- Port portable typed settings, dashboard/home, browse/detail, examples, local
  suspension reads, and other read-only projections.
- Preserve settings defaults, malformed-value fail-open rules, cache
  invalidation timing, sort order, and paging.
- Run every repository contract against the legacy Android implementation and
  shared SQL implementation using the same fixture.
- Keep production Android on the compatibility facade until Goal 184.

**Planned commits:**

1. `data: port settings and Home repositories to shared SQL`
2. `test: add cross-driver read and settings conformance`

**Done when:** Both implementations return identical typed snapshots and
settings round trips.

### Goal 181: Port Study persistence to `:data-sql`

**Depends on:** Goal 180.

**Outcome:** The highest-risk transaction is shared and exhaustively pinned.

**Work:**

- Port queue load, item state, learning repeats, review token/revision CAS,
  evidence, timeline, task timing, choice state/log, repair state, stats
  dirtiness, recovery, and undo.
- Preserve one row per `(kanji, answer_signature)`.
- Preserve exact `APPLIED`, `DUPLICATE`, and `STALE` classification.
- Fault-inject before/after token insertion, every state mutation, evidence
  insertion, cache invalidation marker, and commit.
- Prove duplicate tokens cannot mask another constraint violation.
- Compare representative database rows, not only repository return values.

**Planned commits:**

1. `data: port Study queue reads and item writes`
2. `data: port token-first review revision CAS`
3. `data: port Study undo recovery and compatibility state`
4. `test: fault-inject Study transaction atomicity`

**Done when:** The shared implementation passes all Goal 165 transaction and
scheduler fixtures on Android and bundled desktop SQLite.

### Goal 182: Port sync publication/history to `:data-sql`

**Depends on:** Goal 181.

**Outcome:** Desktop and Android use one atomic collection publication path.

**Work:**

- Port provider mirror staging, inventory/dashboard derivation, queue
  reconciliation, import decisions, pending run, and successful history.
- Port `SourceBindingStore` and its migration marker/validation state through
  shared SQL without changing the versioned opaque record or overlap policy.
- Keep mirror/dashboard/queue and pending-to-success history publication in one
  atomic transaction. Archive/repaired provider writes happen only afterward;
  their receipts and `suspended_archive.restored_at` updates use separate
  idempotent transactions after each confirmed external success.
- Preserve mid-sync review reconciliation by scheduler revision.
- Preserve dormant/parked item retention and successful-run-only history.
- Fault-inject staging, planner, publication, history, and post-commit
  boundaries.
- Prove a write-back failure cannot invalidate a committed sync.

**Planned commits:**

1. `data: port sync mirror staging to shared SQL`
2. `data: port atomic queue publication and review reconciliation`
3. `data: port successful sync history`
4. `data: port post-commit writeback receipt persistence`
5. `test: prove sync rollback history and review merge semantics`

**Required validation:** Full shared/Android gates and strict live AnkiDroid
gate because the database path used by sync changes.

**Done when:** Shared SQL produces the frozen sync database state on both
drivers, including rollback, history filtering, source-binding decisions, and
mid-sync review behavior.

### Goal 183: Port remaining persistence and reference assets

**Depends on:** Goal 182.

**Outcome:** All remaining production repositories use shared SQL operations,
and both packaged hosts load one verified reference-asset set.

**Work:**

- Port stats cache format 11, progress analytics inputs, source inventory,
  similar/reading pools, mnemonic notes, manual sources, Missing Kanji scans
  and export receipts, archive/repaired coordination, and compatibility
  tables.
- Keep rebuildable versus Kani-owned state classifications explicit.
- Preserve streaming/bounded-memory paths; do not materialize full raw note
  fields in persistent state.
- Add `:reference-assets` for dictionary SQLite assets, Jiten ranks,
  KanjiVG/stroke guides, study fonts, and any checked-in reference/media
  manifest required by sync or Study.
- Give every packaged asset an expected hash, format/cache version, license/
  attribution record, extraction target, and atomic cache-upgrade policy.
- Keep pure lookup/analysis in `:dictionary-core`/`:writing-core`; platform
  classpath/file extraction and verification live behind the new asset loader.
- Test corrupt/missing/old cache recovery, read-only install locations,
  concurrent startup, Unicode filenames, and installed-package resource
  lookup.
- Add large fixture and cancellation tests.

**Planned commits:**

1. `data: port analytics inventory and Kani-owned state`
2. `assets: add the cross-platform reference asset manifest`
3. `assets: add verified extraction and cache upgrades`
4. `test: complete repository and reference asset conformance`

**Done when:** No repository operation remains implemented only in the
`LocalStore` inheritance chain, and Android plus a desktop installed-image
fixture load the same verified dictionary/rank/stroke/font assets with required
notices.

### Goal 184: Switch Android production to shared SQL

**Depends on:** Goal 183.

**Outcome:** Android proves the shared data implementation before desktop
depends on it.

**Work:**

- Make `:data-android` the production connection/lifecycle adapter.
- Reuse the exact `kanji_anki_simple.db` filename and existing user database.
- Delegate all repositories to `:data-sql`.
- Remove the `LocalStoreBase -> History -> Study -> SimilarKanji -> Inventory
  -> Sync -> LocalStore` inheritance chain from production after no caller
  remains, but retain a test-only legacy conformance oracle through at least
  Goal 207.
- Do not dual-write in production.
- Retain WAL, cache invalidation, downgrade handling, snapshot behavior, and
  restore-before-open gate.

**Planned commits:**

1. `data: switch Android production to shared repositories`
2. `refactor: move the LocalStore facade to a test-only oracle`
3. `test: qualify the shared Android database path`

**Required validation:** Fresh install, every migration corpus fixture,
downgrade, WAL, backup/restore instrumentation, process recreation,
`ciFast ciQuality`, and the strict real-collection AnkiDroid gate.

**Done when:** No production source uses the legacy `LocalStore` inheritance
facade, existing user databases open without transformation beyond normal
migrations, and every required Android/live gate passes.

**Rollback:** Revert the production-switch commits and ship a new build using
the test-retained legacy adapter against the unchanged schema. This is a source
rollback, not a runtime toggle. There must be no data-transform migration
solely for this switch.

### Goal 185: Extract cross-platform backup core

**Depends on:** Goal 184.

**Outcome:** Validation, retention, snapshot naming, and restore state machine
are shared; durability mechanisms remain platform adapters.

**Work:**

- Add the pure JVM `:backup-core` module.
- Move pure gzip stream, cap/reserve calculation, header/schema validation,
  `quick_check` policy, retention selection, marker state machine, and fault
  model into shared JVM code.
- Store backward-compatible portable metadata in the existing database
  settings/metadata surface: backup origin platform, format/schema version,
  and last validated opaque provider binding. Older backups without it remain
  valid but require an unknown-origin/source revalidation path.
- Sanitize legacy pre-split backups that still contain device-local settings:
  enumerate the versioned allowlist, exclude those values from portable
  publication, and request a destination `DeviceSettingsStore` reset. A
  post-split backup must contain none of those keys.
- Make paths and durability calls injectable.
- Preserve same-directory `.partial`, safety snapshot, marker publication,
  stale sidecar cleanup, and startup-blocking ambiguity behavior.
- Preserve the API 30+ enablement/API 26-29 fail-closed behavior, exact
  seven-daily/four-weekly retention tiers, nonexistent `VACUUM INTO`
  destination, and maintenance-gate ordering.
- Add Android-to-desktop and desktop-to-Android backup compatibility fixtures.
- Run cross-platform restore instrumentation on API 30 and a current Android
  API.
- Verify WAL-only committed rows appear in snapshots.

**Planned commits:**

1. `backup: extract the portable backup and restore core`
2. `backup: exclude legacy device settings from portable restores`
3. `test: prove cross-platform backup format compatibility`

**Done when:** Android backup behavior is unchanged and pure restore state
tests run without Android. Post-split fixtures contain no device-local keys,
and legacy fixtures prove destination device state is reset rather than
imported.

### Goal 186: Implement desktop profile storage, locking, backup, and restore

**Depends on:** Goal 185.

**Outcome:** Desktop has durable per-user Kani state and safe single-writer
recovery.

**Work:**

- Add `:data-desktop` using the desktop SQLite driver qualified/selected in
  Goal 179 and shared `:data-sql`.
- Keep the portable database filename `kanji_anki_simple.db`.
- Use OS application-data/config/cache locations:
  - Windows: `%LOCALAPPDATA%\\Kani` for data/cache and a documented config
    location;
  - Linux: XDG data/config/cache locations with standard fallbacks;
  - macOS: `~/Library/Application Support/Kani` and standard cache/preferences
    locations.
- Keep secrets outside the database and backups.
- Add an opaque local profile registry outside the portable database. Each
  Kani profile has its own UUID directory, lock, database, and backups; the
  registry stores selection/display state only. The opaque provider binding
  lives inside the portable database so backup transfer can validate it. The
  default profile preserves the single-profile first-run experience.
- Create private application directories/files (`0700`/`0600` on POSIX and
  current-user-only ACLs on Windows) and test that diagnostics/backups do not
  accidentally inherit an unsafe temporary-directory permission.
- Acquire an exclusive profile lock before restore/database open. A second
  process exits with a clear, non-destructive message; activation IPC is
  optional later.
- Refuse to open an internal profile through an unsafe symlink, world-writable
  directory, or filesystem that cannot satisfy the required lock/atomic-move
  preflight. Network-share profiles are unsupported.
- Configure WAL, busy timeout, serialized writes, clean checkpoint/close, and
  crash recovery.
- Implement fresh export, tiered automatic backup, picker import, validation,
  startup staging, and atomic restore.
- Copy picker input into private same-filesystem staging before validation and
  publication; never rename directly from a user-selected external path.
- During cross-platform restore, preserve Kani-authoritative and retained
  history state, discard any legacy device-local keys through the explicit
  versioned allowlist, reset the destination `DeviceSettingsStore` through an
  injected port, mark current provider-derived projections stale, and require
  source revalidation before any provider write.
- Implement/test POSIX directory fsync and Windows atomic
  replace-with-write-through. Disable restore on a platform if the strict
  contract is unavailable.
- Add `--temporary-data` and `--profile-dir` only for tests; production rejects
  unsafe world-writable profile locations.

**Planned commits:**

1. `feat: add desktop profile paths and single-instance locking`
2. `feat: add desktop SQLite persistence`
3. `backup: implement durable desktop export and restore`
4. `test: fault-inject desktop startup backup and recovery`

**Done when:** Fresh, restart, crash, lock contention, backup round-trip, both
cross-platform transfer directions, and every restore fault point pass on all
supported OS runners.

---

## Part D: Implement the AnkiConnect provider

### Goal 187: Add a typed, bounded AnkiConnect transport

**Depends on:** Goals 176 and 186.

**Outcome:** Desktop can safely determine whether a compatible AnkiConnect is
available without reading collection data.

**Work:**

- Add `:provider-ankiconnect`.
- Refresh/pin Anki Desktop, AnkiConnect source commit/archive checksum, API v6
  behavior, and action list.
- Use JDK `HttpClient` with redirects disabled, loopback endpoint validation,
  post-resolution loopback enforcement, no proxy, bounded request/response
  bodies, connect/request deadlines, cancellation, and redacted diagnostics.
- Reject userinfo, non-HTTP schemes, non-loopback resolution, unexpected
  path/query/fragment, redirects, and proxy routing before an API key can be
  attached.
- Add typed JSON envelopes/DTOs. Kotlin serialization may be added here for
  protocol safety; the navigation contract still does not require it.
- Implement `requestPermission`, `version`, `apiReflect`, `getActiveProfile`,
  connection status, configuration validation, API-key authentication, and
  actionable failure mapping.
- Make the initial `requestPermission` call without a key. Handle only pinned,
  fixture-backed permission/key response variants; prompt or consult the
  secret store only after this result. Once authentication is established,
  send API `"version": 6` and the optional `"key"` on every other request. A
  `multi` request repeats them in every nested action, validates every nested
  envelope, and redacts nested payloads too.
- Call `apiReflect` with `scopes: ["actions"]`; classify exact required versus
  optional actions and enforce a positive outbound action allowlist. The
  planned surface includes `requestPermission`, `version`, `apiReflect`,
  `getActiveProfile`, `modelNamesAndIds`, `modelFieldNames`, `modelTemplates`,
  `modelFieldsOnTemplates`, `deckNamesAndIds`, `findNotes`, `notesInfo`,
  `findCards`, `cardsInfo`, `multi`, `retrieveMediaFile`, `guiBrowse`,
  `addTags`, `createDeck`, `createModel`, and `addNotes`.
- Tolerate known response-key spelling variants only when covered by pinned
  fixtures.
- Consume the `SecretStore` platform port: use a supported OS credential vault
  only where its adapter is tested and wired; otherwise retain the API key for
  the process session only. Never fall back to plaintext persistence.
- Add a deterministic in-process fake server for malformed JSON, oversize
  body, timeout, cancellation, HTTP failure, HTTP-200 protocol error,
  unauthorized key, wrong version, missing action, and retry tests.
- Run the handshake/status path read-only against the user's available Linux
  Anki session and record only versions, capabilities, timings, and aggregate
  counts.

**Planned commits:**

1. `anki-connect: add the typed loopback transport`
2. `anki-connect: implement version capability and auth handshake`
3. `security: keep AnkiConnect credentials out of persistent state`
4. `test: add the deterministic AnkiConnect protocol server`

**Done when:** Status distinguishes not running, auth required, incompatible,
malformed, transient, and ready; the unauthenticated permission probe and
positive action allowlist are fixture-pinned; no secret appears in captured
Kani logs or backup bytes.

### Goal 188: Implement collection reads and capability-aware normalization

**Depends on:** Goal 187.

**Outcome:** AnkiConnect produces the same provider-neutral snapshot shape as
AnkiDroid where the APIs overlap.

**Work:**

- Implement model discovery/field names, configured note population, browser
  query population, note IDs/details, card IDs/details, deck/template,
  suspension, intervals, repetitions, lapses, and tags.
- Use `findNotes`/`notesInfo`, `findCards`/`cardsInfo`, and bounded `multi`
  calls. Start detail batches at 50-100 and adapt downward by encoded byte
  size; 500 is only a hard ceiling because `cardsInfo` repeats rendered
  content/CSS.
- `findNotes` and `findCards` return complete ID arrays. Enforce an explicit
  full-response and ID-count cap (planning bounds: 32 MiB and 250,000 IDs),
  fail with an actionable oversize error, then process accepted IDs in bounded
  detail batches.
- Preserve configured-sync versus browser-query identity and deterministic
  ordering.
- Intersect raw browser-query matches with the configured model before merging
  them. Preserve model/deck IDs and template ord; retain the current rejection
  of configured cards whose ord is not `0`.
- Preserve Android normalization for suspension (`queue < 0`), not
  AnkiConnect `areSuspended`'s narrower `queue == -1` behavior.
- Bind the open Kani database to the active Anki profile/source identity before
  reading details or publishing a mirror. A mismatch blocks sync and offers a
  new-profile/rebind flow; it never silently replaces the source.
- Validate required fields in every response and isolate malformed rows under
  the shared warning threshold.
- Declare provider capabilities precisely. `FSRS_MEMORY_STATE` is a
  source/snapshot capability, not a claim that every card has non-null fields.
  Emit nullable FSRS memory fields and do not declare the capability for stock
  AnkiConnect.
- Run capability-present/absent admission and seeding goldens.
- Add cancellation and progress checks before metadata, every batch, and every
  row transformation.
- Run a read-only configured-sync snapshot against the user's available Linux
  Anki session; compare only redacted aggregate outcomes and never invoke a
  write action.

**Planned commits:**

1. `anki-connect: implement model note and card reads`
2. `sync: normalize missing FSRS memory without fabrication`
3. `test: cover bounded collection reads and interval-lapse fallback`

**Done when:** The fake AnkiConnect adapter passes the shared collection
contract with only documented capability differences.

### Goal 189: Implement inventory, browser handoff, and media reads

**Depends on:** Goal 188.

**Outcome:** Desktop supports collection-wide Missing Kanji analysis and the
read-side utilities used by Home/Study.

**Work:**

- After the bounded full note-ID response, process collection-wide note/model
  details in bounded batches through the aggregate-only Missing Kanji
  analyzer; never persist raw note text. Do not claim the initial ID discovery
  itself is streaming.
- Preserve skipped/malformed counts, cancellation, and warning thresholds.
- Implement browser handoff with `guiBrowse` and exact queries.
- Implement bounded media retrieval only for media the current Study flow
  needs, with filename validation, byte caps, cache limits, and no arbitrary
  path writes.
- Keep inventory separate from configured-model sync and historical snapshots.

**Planned commits:**

1. `anki-connect: implement collection inventory scanning`
2. `anki-connect: add browser handoff and bounded media reads`
3. `test: prove inventory isolation and media safety`

**Done when:** Large sanitized collections remain bounded in memory and raw
fields/media bytes do not enter logs or persistent inventory state.

### Goal 190: Implement safe AnkiConnect writes

**Depends on:** Goal 189.

**Outcome:** Desktop supports the same deliberate write surfaces without
touching Anki scheduling state.

**Work:**

- Implement archive and repaired tag writes with `addTags`, per-note
  read/modify/reconcile behavior where required, failure isolation, retry, and
  manual confirmation for repaired tagging.
- Send one-note `addTags` actions inside bounded `multi` batches and inspect
  every nested envelope so one failure cannot hide another. Write
  `kani_archived`/`kani_repaired` and continue recognizing legacy
  `kanji_anki_archived`.
- Implement Missing Kanji deck/model discovery and shape validation,
  `createDeck`, `createModel`, `addNotes`, stable `SourceId`, batches of at
  most 100, and reconciliation after partial failure.
- Reuse an existing model only when its exact field/template contract matches.
  Never rewrite a collision.
- Do not assume `addNotes` is batch-atomic. Inspect every returned list entry;
  mixed note IDs and null/failure entries are a partial result. After every
  response, protocol error, timeout, cancellation, or connection loss,
  reconcile the entire intended batch by stable external `SourceId` before
  recording receipts or retrying, because the client may not know which notes
  the server completed.
- If standard actions cannot prove an existing `Kani::Missing Kanji` deck is
  an acceptable ordinary deck, fail closed to CSV instead of assuming
  compatibility.
- Keep CSV fully usable for unsupported capabilities, collision, auth loss,
  or unfinished writes.
- Include provider/source binding in `destination_key`; after restore/rebind,
  reconcile external `SourceId` before a local receipt may suppress a write.
- Add positive action-allowlist and scheduling-write deny-list tests proving
  no adapter action can change scheduling fields.

**Planned commits:**

1. `anki-connect: add isolated archive and repaired tag writes`
2. `anki-connect: add idempotent Missing Kanji creation`
3. `test: enforce the desktop provider write surface`

**Done when:** First write, retry, partial failure, cancellation, collision,
auth loss, mixed-ID/null `addNotes`, and zero-duplicate reconciliation pass;
sync success survives all write failures.

### Goal 191: Qualify provider equivalence with real Anki Desktop

**Depends on:** Goal 190.

**Outcome:** Desktop sync is supported against a real pinned host, not only a
mock HTTP server.

**Work:**

- Add a Linux Xvfb/manual fixture that starts pinned Anki Desktop with a
  throwaway profile and pinned AnkiConnect.
- Keep the user's already-running Linux Anki session available for read-only
  smoke checks, but run tag/note creation, retry, and cleanup only against the
  isolated throwaway fixture.
- Use a small sanitized Kiku collection for repeatable CI/manual tests.
- Add a stricter local copied-user-profile gate with at least the same
  7,000-note threshold as the Android gate. Never modify the user's live
  profile.
- Exercise connection/auth, configured sync, browser query, dashboard/study
  creation, FSRS-absent interval/lapse fallback, inventory, tag writes, Missing
  Kanji create/retry, browser handoff, cancellation, and cleanup.
- Record versions, checksums, action/capability list, aggregate counts,
  durations, and sanitized outcomes only.
- Compare normalized AnkiDroid and AnkiConnect snapshots for shared fields.
  Every allowed difference needs an explicit capability/reason code.
- Define the same throwaway-profile smoke for Windows and macOS and execute it
  before Goal 207 completes. Linux is the early development/live gate; Linux
  success alone is not evidence for Windows/macOS loopback, credential vault,
  `guiBrowse`, or Anki host integration.

**Planned commits:**

1. `test: add the pinned live Anki Desktop fixture`
2. `test: add cross-provider normalized snapshot conformance`
3. `docs: record the desktop provider qualification runbook`

**Done when:** The sanitized live suite is repeatable; the copied-collection
gate completes; no personal field/deck/model content appears in evidence.

---

## Part E: Share presentation and complete the hosts

### Goal 192: Add portable presentation contracts and capability state

**Depends on:** Goals 177 and 191.

**Outcome:** Shared UI can express the whole product without receiving JVM
domain, Activity, AWT, or provider implementation types.

**Work:**

- Add `:presentation-api` as a KMP Android/desktop module.
- Define immutable route/screen states, one-shot effects, action interfaces,
  loading/error models, `UiText`, navigation destinations, and
  `PlatformCapabilities`.
- Include capabilities for provider connectivity, FSRS memory, writing
  recognition, tray, notifications, closed-app scheduling, secret persistence,
  backup/restore, direct Missing Kanji creation, browser handoff, and update
  delivery.
- Define mapper/use-case ports implemented by each host's JVM application
  adapters.
- Limit common presentation reducers to ephemeral UI state and one-shot
  effect consumption. They render the authoritative `:application` Study
  snapshot and dispatch actions back to it; they do not reimplement session
  selection, reveal progression, commit, recovery, or undo.
- Replace `Runnable`, Android resource IDs, Activity constants, contexts,
  intents, and raw exceptions in otherwise portable screen models.
- Add reducer and effect-consumption tests that run for both targets.

**Planned commits:**

1. `refactor: define portable presentation state and actions`
2. `feat: expose explicit platform capability state`
3. `test: cover shared reducers and one-shot effects`

**Done when:** A fake host can drive every route through immutable state and
actions in common tests.

### Goal 193: Add shared resources, theme, shell, and navigation

**Depends on:** Goal 192.

**Outcome:** Both apps render one product shell and route model.

**Work:**

- Add `:ui-common` and `:feature-shell`.
- Add the foundational `:platform-desktop` module and the minimum production
  adapters needed by the real host: app lifecycle/directories, logger,
  `DeviceSettingsStore`, `SecretStore` (OS vault where qualified, session-only
  otherwise), file/open-URL/clipboard services, and controlled shutdown.
- Move portable colors, typography, spacing, components, charts, icons, and
  copy into Compose Multiplatform/resources.
- Preserve stored theme values and Android visual behavior.
- Implement typed destinations and one centralized route/deep-link codec.
- Keep Study card/reveal/repair/Done phases inside Study state, not navigation.
- Add responsive desktop window layouts and a minimum usable size without
  breaking phone/tablet layouts.
- Add common error boundary, loading, snackbar/dialog effect host, keyboard
  focus policy, and capability explanation UI.
- Replace the desktop smoke window with a minimal real composition root:
  profile/restore gate, data, AnkiConnect provider, application adapters,
  presentation ports, and shared shell. Feature routes may still be
  placeholders until their following goals, but Goal 194 must not invent a
  second host harness.
- Add screenshot tests at phone, tablet, 1280x800, 1440x900, high-DPI, dark,
  light, and large-font configurations.

**Planned commits:**

1. `ui: add shared Compose resources and theme`
2. `ui: add the cross-platform Kani shell`
3. `ui: centralize typed navigation and deep links`
4. `feat: add foundational desktop platform adapters`
5. `feat: wire the minimal desktop feature host`
6. `test: add shared shell screenshot and semantics coverage`

**Done when:** Android and the real desktop composition root render the same
shell states; resource lookup has no Android-only fallback in common code; the
desktop host reaches a provider-status placeholder through the actual startup
and shutdown lifecycle using the production `DeviceSettingsStore` and
qualified-vault-or-session-only `SecretStore`.

### Goal 194: Share onboarding, Home, Browse, and sync UI

**Depends on:** Goal 193.

**Outcome:** The first complete desktop vertical slice can configure and run
sync, then browse its result.

**Work:**

- Add/migrate `:feature-home`, including onboarding, provider status,
  note-type/field configuration, Home dashboard, focus queue, browse/detail,
  manual sync confirmation/progress/cancellation, and repaired handoff.
- Replace permission-specific language with capability-specific host copy
  while retaining Android permission flows.
- Add AnkiConnect install/start/auth/incompatible guidance and reduced-FSRS
  precision explanation.
- Keep browser queries and tag write confirmation exact.
- Add keyboard/mouse/focus behavior without triggering actions while editing
  text.
- Wire both hosts through the same presentation ports.

**Planned commits:**

1. `ui: share onboarding and provider configuration`
2. `ui: share Home Browse and sync progress`
3. `test: prove Android and desktop Home state parity`

**Done when:** A desktop user can launch, configure fake/real AnkiConnect,
sync, cancel/retry, and inspect non-empty Home/Browse state; Android
screenshots and behavior remain accepted.

### Goal 195: Share the core Study experience

**Depends on:** Goal 194.

**Outcome:** Desktop can complete non-writing study with the exact scheduler
and transaction semantics.

**Work:**

- Add/migrate `:feature-study`.
- Share queue/start/empty/done, recognition, reading, typing, multiple choice,
  similar kanji, reading kanji, sentence reading, repair feedback,
  revalidation, recovery, undo, and error states.
- Keep Pass/Fail UI mapping to `good`/`again`; preserve the existing writing
  exception for `Save hard`.
- Add keyboard shortcuts with explicit focus guards:
  - Space/Enter for the visible primary action;
  - P/F only where Pass/Fail is visible;
  - Ctrl/Cmd+Z for supported undo;
  - Escape for dismiss/back, never for committing a grade.
- Prevent key repeat, double click, and duplicate callbacks from double
  committing.
- Persist a review, restart the desktop process, and prove exact state.

**Planned commits:**

1. `ui: share the Study session and card surfaces`
2. `ui: add guarded desktop Study controls`
3. `test: prove shared Study state and commit parity`

**Done when:** Every non-writing task and repair has common screenshot,
semantics, reducer, and repository integration tests on both hosts.

### Goal 196: Make handwriting platform-capability safe

**Depends on:** Goal 195.

**Outcome:** Desktop never gets stuck on or falsely passes an unavailable
writing repair.

**Work:**

- Move captured ink primitives and rendering state behind shared presentation
  models while retaining `writing-core` evaluation.
- Implement a Compose Canvas ink surface for mouse, stylus, normalized
  coordinates, timing, undo/clear, guide overlay, and high-DPI scaling.
- Keep ML Kit in the Android adapter.
- Add `StudyCapabilityPolicy` to pure scheduler/application code.
- Pass capabilities into deterministic task selection. When
  `WRITE_RECOGNITION` is absent, filter writing before selecting a task,
  retain the underlying cause, and return
  `write_kanji_unavailable_on_platform` in the non-review selection trace.
- Reloading the same state/capabilities must choose the same non-writing task;
  it may not repeatedly select then skip writing.
- Never create a review/timeline token, submit a rating, mutate scheduler
  state, increment writing level, or change FSRS memory for unavailability.
- Allow desktop guide practice only as explicitly practice-only UI.
- Update `docs/adaptive-two-core-scheduler.md`, capability/debug reason copy,
  and all golden timelines.

**Planned commits:**

1. `ui: add the shared handwriting canvas`
2. `scheduler: route unavailable writing repair by capability`
3. `test: regenerate capability-aware scheduler timelines`
4. `docs: document cross-platform writing behavior`

**Required validation:** Full scheduler tests, Android Study instrumentation,
screenshots, `ciFast ciQuality ciDesktop`. Verify Android with the ML model
available produces no routing change.

**Done when:** Desktop cannot receive or submit a scheduled writing grade
without a real recognizer, Android writing behavior is unchanged, and all
capability-present/absent golden timelines and selection traces pass.

### Goal 197: Share Stats, Games, and Missing Kanji

**Depends on:** Goal 196.

**Outcome:** Secondary product surfaces have supported desktop behavior.

**Work:**

- Add/migrate `:feature-stats`, `:feature-games`, and
  `:feature-missing-kanji`.
- Share charts, filters, core/repair analytics, revalidation/escalation/stuck
  states, progress, game state, collection scan, search/selection, Add to Kani,
  direct Anki creation, and CSV output.
- Use host file picker/share implementations; do not use Android intents in
  common code.
- Preserve bounded inventory analysis and direct-write confirmation/results.
- Add large-data rendering, cancellation, empty/error, and partial-write
  screenshots/tests.

**Planned commits:**

1. `ui: share Stats and progress analytics`
2. `ui: share Games and Missing Kanji`
3. `test: cover cross-platform analytics and export states`

**Done when:** Desktop can scan, select, add locally, export CSV, perform
supported direct creation, and render stats without Android imports.

### Goal 198: Share Settings, backup, update, and diagnostics UI

**Depends on:** Goal 197.

**Outcome:** Users can operate and recover the desktop app without hidden
configuration files.

**Work:**

- Add/migrate `:feature-settings`.
- Share scheduler/repair, deck limits, sync source, automation, appearance,
  backup/restore, diagnostics, update, and about/legal states.
- Make platform limitations/capabilities visible and truthful.
- Add desktop pickers, confirmation, progress, restart-required restore
  handoff, and safe diagnostic export.
- Redact API keys, paths that reveal usernames where unnecessary, raw note
  fields, browser queries, deck/model names, and personal content.
- Explain independent-device state and destructive replacement before restore.

**Planned commits:**

1. `ui: share Settings and capability controls`
2. `ui: share backup update and diagnostics flows`
3. `test: prove redaction restore confirmation and settings parity`

**Done when:** All user-operable desktop settings have UI and tests; no secret
or collection content is present in exported diagnostics.

### Goal 199: Finish the Android host and remove Activity inheritance

**Depends on:** Goal 198.

**Outcome:** Android consumes the same shared product UI through a thin host.

**Work:**

- Make one `MainActivity` own lifecycle, permission/result launchers, stable
  intent/deep-link translation, and `setContent`.
- Use the shared typed navigation graph.
- Delete the `MainActivity*` inheritance chain and Activity-typed helper
  parameters after all consumers migrate.
- Extract Android-only `:widget`; keep Glance and the exact reminder
  eligibility/event-refresh contract.
- Preserve notification/widget/shortcut/update PendingIntent actions and
  extras.
- Keep `:app` as the Android application module so release scripts/package
  identity do not churn.
- Tighten boundary tests so Android platform classes cannot re-enter shared
  features.

**Planned commits:**

1. `refactor: switch Android to the shared feature graph`
2. `refactor: replace Activity inheritance with one host`
3. `refactor: extract the Android widget module`
4. `test: enforce the final Android presentation boundary`

**Required validation:** API 26/35 navigation, deep-link, process recreation,
permission, notification, worker, widget, screenshot, minified-risk, and full
Android gates. Provider behavior is unchanged, so live provider testing is
required only if implementation touched provider/sync code.

**Done when:** One thin `MainActivity` hosts the shared graph, no production
helper accepts an Activity subclass, widget/intent compatibility tests pass,
and final boundary rules prohibit platform leakage.

### Goal 200: Complete the desktop composition root and end-to-end flow

**Depends on:** Goal 199.

**Outcome:** `:desktop-app` is a real Kani host rather than a smoke window.

**Work:**

- Complete the remaining desktop variants of the Goal 176 ports, including
  notifications, diagnostics, update delivery, background scheduling, richer
  browser handoff, and package integration; retain the foundational module
  created in Goal 193.
- Wire profile lock -> staged restore -> data -> provider -> sync/application
  -> presentation -> window in strict startup order.
- Implement menu, window state, close behavior, crash boundary, controlled
  shutdown, and `--smoke-test --temporary-data`.
- Save window size/position only after bounds validation; recover when monitors
  change.
- Wire platform clipboard, file picker, external URL, browser handoff,
  notifications capability, diagnostics, and update port.
- Prove first launch, configure, sync, Home, Study commit/undo, Stats, Missing
  Kanji, backup, restart, restore, and clean exit.
- Add a test clock/fake provider mode that never touches a real profile.

**Planned commits:**

1. `feat: complete desktop platform service adapters`
2. `feat: assemble the Kani desktop composition root`
3. `feat: add desktop window lifecycle and platform effects`
4. `test: add the desktop end-to-end smoke journey`

**Done when:** The full journey passes with fake AnkiConnect on all OS runners
and with real Anki Desktop under the Goal 191 fixture.

---

## Part F: Desktop platform services, quality, and packaging

### Goal 201: Add desktop reminders, auto-sync, notifications, and tray

**Depends on:** Goal 200.

**Outcome:** Desktop automation is useful and truthful about process lifetime.

**Work:**

- Reuse pure reminder eligibility, quiet-time, anti-spam, and schedule policy.
- Add an in-process scheduler for reminders, periodic sync, weekly FSRS fitter
  where supported, and update checks.
- Add optional run-at-login adapters per supported OS. Installation/removal
  must be idempotent and user-controlled.
- Add system notifications with in-app fallback.
- Add tray due count, open, sync, study, and quit where `SystemTray` is
  supported. Do not make tray availability a startup requirement.
- Define close-to-tray versus exit behavior explicitly and default safely.
- Refresh after committed sync/study/reminder events; do not poll the database
  unnecessarily.

**Planned commits:**

1. `feat: add the desktop automation scheduler`
2. `feat: add desktop notifications and tray actions`
3. `feat: add opt-in run-at-login integration`
4. `test: cover automation lifecycle and capability fallback`

**Done when:** Fake-clock tests cover suspend/resume, sleep/wake, clock jumps,
duplicate prevention, quiet periods, app exit, no-tray sessions, and
run-at-login cleanup.

### Goal 202: Add authenticated desktop update handoff

**Depends on:** Goal 201.

**Outcome:** Desktop selects and verifies the correct native artifact without
reusing Android installation code.

**Work:**

- Extend `build-logic` versioning and `ci/scripts/kani_version.py` so the
  release tag is the single source for Android version name/code, desktop
  application version, installer-safe numeric versions, and artifact names.
  Pin the conversion and platform bounds in `ci/tests`.
- Extend `:update-core` from APK-only selection to exact OS/architecture/
  package matching.
- Define canonical asset names:
  - `kani-desktop-windows-x64-<version>.msi`;
  - `kani-desktop-linux-x64-<version>.deb`;
  - `kani-desktop-linux-x64-<version>.tar.gz` when supported;
  - `kani-desktop-macos-arm64-<version>.dmg`.
- Define two separate release artifacts:
  - conventional `SHA256SUMS.txt`;
  - deterministic `release-manifest-v1.json` plus a detached Ed25519
    signature.
- Define/test the manifest schema and generator with a fixture key in this
  goal. Goal 206 alone generates the production manifest from final
  post-signing/notarization bytes. The manifest binds schema version, release
  tag/semantic version, exact build SHA, key ID, and every asset's filename,
  byte size, SHA-256, OS, architecture, and package type.
- Canonicalize manifest bytes (UTF-8, fixed field/asset order, LF newline, no
  wall-clock field) and sort `SHA256SUMS.txt` by filename so verification is
  deterministic.
- Define key generation, offline backup, Actions secret custody, rotation, and
  emergency revocation. A rotation release trusts both old and new public keys
  before a later release removes the old key.
- Verify release tag/version, manifest signature, asset hash, OS/arch, package
  identity, and downgrade policy before offering installation.
- Pin platform version mappings:
  - MSI uses `major.minor.patch` and fails closed if major/minor exceed 255 or
    patch exceeds 65535;
  - macOS short version is semantic version and bundle build version is the
    monotonic Kani version code;
  - DEB uses semantic version plus an explicit Debian revision.
- Download to a private partial file, enforce size limits, atomically publish,
  ask for confirmation, and open the native installer.
- Treat an Android-only newest release or a temporarily absent valid desktop
  manifest as a normal staged rollout. Search at most the 10 newest releases
  for the newest compatible valid signed desktop manifest, never downgrade,
  and never fall back to raw unsigned assets.
- Record Linux installation channel. Only DEB participates in automatic update
  handoff; portable tar updates remain manual.
- Keep the Android updater behavior and asset selection unchanged.

**Planned commits:**

1. `release: extend the canonical version and asset contracts`
2. `refactor: select release assets by platform and architecture`
3. `release: define and authenticate the desktop manifest`
4. `feat: add confirmed desktop update downloads`
5. `test: reject downgrade corruption wrong-arch and key failures`

**Done when:** A co-hosted checksum alone is insufficient, failed verification
never opens a package, and Android still selects only its verified APK.

### Goal 203: Qualify accessibility, input, localization, and performance

**Depends on:** Goal 202.

**Outcome:** Desktop support is usable beyond the happy-path developer machine.

**Work:**

- Audit keyboard-only navigation, focus order/visibility, shortcuts, dialogs,
  menus, tables/charts alternatives, and screen-reader semantics.
- Test Windows and macOS accessibility bridges with the current Compose
  support. Record Linux screen-reader limitations from official Compose
  documentation rather than claiming parity.
- Test mouse, trackpad, stylus where available, key repeat, IME/Japanese input,
  clipboard, multiple monitors, high DPI, fractional scaling, and large fonts.
- Migrate/localize all user-visible desktop strings through shared resources;
  test English and Japanese plus long-string pseudo-localization.
- Measure cold/warm start, first sync, 7,000+ note sync, dashboard load, Study
  action latency, chart rendering, memory, and backup/restore.
- At the start of this goal, record desktop-specific baselines from the real
  Goal 200 composition root and Goal 191 live provider on named hardware/OS.
  Set desktop budgets from those measurements. Goal 165 supplies Android
  regression budgets only; do not project Android timings onto desktop.
- Test offline/provider-down operation and recovery without data corruption.

**Planned commits:**

1. `test: add desktop keyboard scaling and locale matrices`
2. `fix: close desktop accessibility and input gaps`
3. `perf: enforce desktop startup sync and Study budgets`
4. `docs: record supported desktop accessibility behavior`

**Done when:** No critical route is mouse-only; focus and grade actions are
safe; supported platform accessibility claims match evidence; performance
budgets pass on release hardware/runners.

### Goal 204: Build native distributions and installed-package smoke tests

**Depends on:** Goal 203.

**Outcome:** Each supported OS has a self-contained package built from
reproducible inputs and deterministic metadata, with immutable published bytes
and recorded provenance. Timestamped/notarized packages are not expected to be
bit-for-bit reproducible across a fresh signing run.

**Work:**

- Pin the release-packaging JDK by vendor, full Java 17 patch, architecture,
  download URL, and checksum on each host (preferred vendor: Eclipse Temurin
  unless the refreshed support review selects another). That runtime is
  shipped inside the application and is part of release evidence.
- Configure Compose native distributions/`jpackage` with the pinned JDK.
- Start with non-ProGuard `createDistributable` and
  `packageDistributionForCurrentOS`. Use release/ProGuard tasks only in a
  separate commit after keep rules, reflection/resource behavior, stack
  traces, startup, and installed-package journeys pass.
- Run `suggestModules` as a hint, then explicitly test the packaged runtime;
  ensure modules such as `java.net.http`, `java.sql`, desktop/accessibility,
  image, logging, and crypto requirements are present.
- Windows:
  - MSI x64;
  - stable upgrade UUID;
  - valid numeric installer version mapping;
  - per-user install unless an approved reason requires elevation;
  - WiX 3+ and Windows SDK SignTool prerequisites;
  - install/upgrade/uninstall/data-retention tests using a synthetic
    lower-version package with the same upgrade UUID for the first release.
- Linux:
  - DEB x64 with desktop entry, icon, MIME/link behavior where supported;
  - portable tar only if its launch/update expectations are documented;
  - `fakeroot` and required DEB tooling;
  - build release natives on the oldest supported glibc baseline or raise the
    minimum;
  - dependency and clean-container tests on Ubuntu 20.04 and current Ubuntu.
- macOS:
  - arm64 app/DMG;
  - stable bundle ID;
  - hardened runtime-ready entitlements;
  - Xcode command-line packaging/signing prerequisites;
  - deployment target pinned to macOS 13 or the minimum actually qualified;
  - install/upgrade/quarantine/data-retention tests using a synthetic
    lower-version package with the same bundle/package identity.
- Build each format only on its target OS; Compose packaging is not
  cross-compilation.
- Current hosted runners prove current-host builds, not the advertised
  minimums. Qualify Windows 10/11 on real VMs, Ubuntu 20.04 in a compatible
  build/test environment, and macOS 13 on Apple-silicon hardware. Raise the
  support floor to the oldest actually tested version if those gates are not
  available.
- Audit every bundled JVM/native/resource/font/dictionary dependency and ship
  the required licenses/attributions. AnkiConnect remains a separately
  installed user prerequisite unless a later license/security review
  explicitly permits bundling.
- Add custom stable tasks `createDesktopDistributable`,
  `packageDesktopCurrentOs`, and `verifyDesktopPackage` so CI does not depend
  directly on plugin task-name churn. Each wrapper delegates explicitly to the
  selected non-ProGuard or qualified ProGuard variant.
- Installed smoke mode must initialize the packaged runtime and Compose/Skiko,
  create a visible/offscreen test window as appropriate, publish a readiness
  marker, and exit under test control. Merely parsing
  `--smoke-test` headlessly is insufficient.

**Planned commits:**

1. `build: configure Windows MSI packaging`
2. `build: configure Linux desktop packaging`
3. `build: configure macOS DMG packaging`
4. `test: smoke installed desktop distributions`

**Done when:** Current and minimum-supported clean environments install,
initialize Compose/Skiko, launch temporary smoke mode, upgrade from the
synthetic lower package, retain user data, uninstall binaries without deleting
user data, and verify package identity/version/runtime provenance.

### Goal 205: Complete CI, static analysis, and live integration coverage

**Depends on:** Goal 204.

**Outcome:** The final module/release surface is enforced continuously.

**Work:**

- Finalize `ciDesktop`, `ciDesktopPackage`, and `ciAll` task membership.
- Keep Android `ciFast`, `ciQuality`, and `ciRelease` semantics intact.
- Extend CodeQL's forced clean compile after `init` to compile every relevant
  shared/desktop module with no build cache; retain real compiler activity.
- Extend Sonar bytecode/test/coverage inputs without making Sonar a substitute
  for deterministic CI.
- Add `.github/workflows/desktop-live-anki.yml` as nightly/manual, not an
  Android release dependency.
- Update device-risk classifiers: desktop-only paths may skip Android
  emulator work; shared data/sync/scheduler/provider-contract paths may not.
- Extend `tools/test_release_workflows.py`, CI tests, module boundaries,
  dependency verification, and action-pin checks.
- Make package artifacts and logs content-safe and bounded.
- Generate and validate an SPDX or CycloneDX SBOM from the exact resolved
  release dependency graph and a deterministic third-party notices file.

**Planned commits:**

1. `ci: finalize desktop shared and package confidence gates`
2. `ci: add desktop code scanning and coverage inputs`
3. `ci: add the live Anki Desktop fixture workflow`
4. `release: generate desktop SBOM and third-party notices`
5. `test: enforce cross-platform workflow invariants`

**Done when:** PR CI is deterministic; nightly/manual live tests are isolated;
Android release cannot be blocked by desktop/Anki service flakiness; all
workflow contract tests pass.

---

## Part G: Signing, publication, and support

### Goal 206: Sign, publish, and independently verify desktop artifacts

**Depends on:** Goal 205 and explicit user authorization to change release
workflows/use signing secrets.

**Outcome:** Desktop artifacts can be attached safely to the same Kani release
as Android without weakening Android publication.

**Work:**

- Add `.github/workflows/desktop-release.yml`.
- Trigger production with `workflow_run` on the exact workflow name
  `Android Release` and require `conclusion == success`. Do not depend on a
  `release: published` event from a `GITHUB_TOKEN` publication.
- Extend Android Release to upload a small `release-metadata.json` artifact
  containing `release_tag`, `build_sha`, semantic/version-code values, and
  Android asset names. Desktop Release downloads it by upstream run ID,
  verifies trusted repository/event/main ancestry, and proves the tag resolves
  exactly to `build_sha` before detached checkout.
- Manual preview runs produce unsigned workflow artifacts only. Production
  signing/publication requires a canonical tag and protected-environment
  approval.
- Start downstream permissions at `actions: read`; only the final publisher
  job receives `contents: write`.
- Keep Android APK publication dependent only on the existing Android
  build/verification path. Desktop signing/notarization failure may delay
  desktop assets but may not block or invalidate an already-valid APK release.
- Before exposing signing secrets, rerun deterministic desktop tests,
  installed-image/package smoke, workflow contracts, and source/dependency
  verification inline for the exact `build_sha` (or prove an exact-SHA
  Desktop CI success and still rerun the small trusted preflight).
- Use this job DAG:

  ```text
  trusted metadata/preflight
    -> unsigned build and tests on each OS
    -> platform signing/notarization
    -> pre-publication native verification
    -> manifest/checksum/SBOM aggregation and Ed25519 signing
    -> asset upload
    -> detached manifest signature upload last as the readiness marker
    -> post-publication download and native verification on matching OS
  ```

- Import signing material only after untrusted compilation/tests, use protected
  environments and minimal permissions, disable caches in signing jobs, and
  delete temporary PFX, keychain, API-key, and manifest private-key material.
- Windows:
  - sign generated launcher/PE files before MSI construction, then sign the
    final MSI;
  - use Authenticode SHA-256 and RFC 3161 timestamping;
  - use `signtool sign /fd SHA256 /tr ... /td SHA256`;
  - verify the MSI and installed `Kani.exe` with
    `signtool verify /pa /all /tw` after download.
- macOS:
  - sign nested native code/runtime and the `.app` with Developer ID and
    hardened runtime;
  - create and notarize the DMG with `notarytool`;
  - staple and run `stapler validate`;
  - run `codesign --verify --deep --strict` and `spctl --assess` after
    download.
- Linux:
  - deterministic package metadata;
  - final hash in the authenticated manifest;
  - optional repository signing only when a package repository exists.
- Assemble final `SHA256SUMS.txt` and `release-manifest-v1.json` only after
  platform signing/notarization. Sign the exact manifest bytes with the
  protected Ed25519 key and upload without overwrite; upload the detached
  signature last.
- Upload the release SBOM, notices, and trusted-build provenance/attestation
  for the exact tag commit without treating attestation as a substitute for
  native signatures or the signed manifest.
- If an asset name already exists:
  - identical bytes: treat as idempotent success;
  - different bytes: fail closed unless the user explicitly authorizes a new
    tag. Never silently replace a published binary.
- Independently download every asset and verify signature, notarization,
  manifest signature, hash, identity, version, launch, and data retention.
- The desktop updater treats a missing detached manifest signature as "desktop
  assets not ready", retries/searches the bounded recent-release window from
  Goal 202, and never selects unsigned raw assets.

**Planned commits:**

1. `release: export trusted Android release metadata`
2. `release: add the exact-SHA desktop release preflight`
3. `release: sign and verify Windows desktop packages`
4. `release: sign notarize and staple macOS packages`
5. `release: publish authenticated desktop assets independently`
6. `release: publish desktop SBOM and build provenance`
7. `test: enforce trusted checkout signing and publication order`

**External prerequisites:** Apple Developer ID/notarization credentials, a
trusted Windows signing certificate/timestamp service, and the protected
Ed25519 release-manifest private key defined in Goal 202. Their absence blocks
production signing/publication, not prior implementation goals. Do not claim
this goal complete without them.

**Done when:** Every final asset is natively signed where applicable, attached
to the exact tag without overwrite, represented by the authenticated manifest
and SBOM, downloaded independently, and passes native verification plus launch
smoke tests.

### Goal 207: Run final qualification and publish support documentation

**Depends on:** Goal 206.

**Outcome:** The first desktop release is supportable, recoverable, and
truthfully documented.

**Work:**

- Run the full Android release gate, including the strict real AnkiDroid copied
  collection test when any shared provider/sync/data behavior changed.
- Run fake and real desktop provider gates, including the strict copied
  throwaway profile, on the release commit.
- Run at least one real Anki Desktop/AnkiConnect gate on Linux, Windows, and
  macOS. Each uses a throwaway profile for writes and covers loopback/auth,
  active-profile binding, configured sync, `guiBrowse`, credential storage or
  session-only fallback, tag write, additive-note retry, and cleanup. The
  user's available live Linux session remains read-only.
- Run package install -> first launch -> sync -> study/undo -> restart ->
  backup -> upgrade -> restore -> uninstall/data-retention journeys on every
  supported OS.
- Run those package/provider journeys on both current targets and the claimed
  minimum Windows 10, Ubuntu 20.04, and macOS 13 environments. If a minimum or
  platform cannot be tested, narrow/raise the published support matrix before
  release.
- Download and independently verify every published artifact.
- Add/update:
  - README platform/install section;
  - desktop AnkiConnect install/auth/troubleshooting guide;
  - independent Kani state and safe backup-transfer guide;
  - backup/restore/recovery runbook;
  - keyboard/accessibility guide;
  - provider capability/parity matrix;
  - desktop build/release/signing runbook;
  - privacy/security statement;
  - known limitations and support matrix;
  - release notes.
- State clearly:
  - Anki must be running for AnkiConnect sync;
  - stock AnkiConnect uses Kani's existing interval/lapse fallback and has
    reduced weak-card precision because provider FSRS memory fields are
    unavailable;
  - desktop writing recognition is unavailable and safely routed;
  - Kani progress does not merge through AnkiWeb;
  - Linux screen-reader limitations where still applicable;
  - automation behavior when Kani is closed.
- Record final versions, commit/tag, artifact hashes, signature/notarization
  evidence, test counts, durations, and validation gaps.

**Planned commits:**

1. `docs: add desktop installation backup and recovery guides`
2. `docs: publish the desktop parity and support matrix`
3. `release: record cross-platform qualification evidence`

**Done when:** All Definition of Done items at the top of this file are
satisfied, the release is independently verified, and no unsupported behavior
is advertised.

---

## Part H: Explicitly deferred work

### Goal 208: Design true multi-device Kani progress sync

**Status:** Deferred; not part of desktop GA; do not execute without separate
user authorization.

**Reason:** Android and desktop can both be correct while their Kani-owned
review histories diverge. Solving that is a distributed-data product, not a
desktop packaging task.

**Required design before implementation:**

- stable random device IDs and globally unique operation/review tokens;
- schema and protocol versioning;
- encrypted authenticated transport and account/key recovery model;
- outbox/inbox with idempotent acknowledgement;
- per-item base revision, causal metadata, tombstones, and bounded retention;
- deterministic conflict policy for concurrent reviews of the same item;
- explicit treatment of undo, settings, local suspension, mnemonic edits,
  repaired handoff, and restore;
- server trust, privacy, export/delete, abuse, availability, and operating-cost
  model;
- rebuildable provider/cache state excluded from synchronization;
- migration from independent profiles and a rollback/export story;
- offline, duplicate, reorder, clock-skew, long-partition, key-loss, and
  concurrent-review simulations.

**Forbidden shortcuts:**

- synchronizing a live SQLite database/WAL directory;
- placing Kani state in Anki media;
- treating last-writer-wins review rows as safe;
- inventing server/account scope without explicit product approval.

**Planned commits:**

Only after separate user authorization:

1. `docs: define the Kani multi-device event model`
2. `test: simulate concurrent review and restore conflicts`
3. `docs: decide encrypted transport and account ownership`

**Done when:** The user has explicitly approved a complete threat/product
model, conflict simulations pass for every Kani-owned state class, and a
separate implementation/release plan exists. Design completion alone does not
authorize a backend or production sync.

## Final risk register

| Risk | Required mitigation |
| --- | --- |
| Kotlin/AGP/Compose compiler mismatch | Isolated Goal 166, official matrix refresh, clean compiler tests, no compatibility bypass. |
| Cross-OS native dependency verification gaps | Resolve/review verification metadata on Linux, Windows, and macOS in Goal 168. |
| Android behavior drift during extraction | Android consumes each shared contract/implementation first; fixtures and live gates precede desktop reliance. |
| AnkiConnect lacks FSRS memory fields | Explicit capability, nullable values, existing interval/lapse fallback, reduced weak-card precision, and no fabricated provider state. |
| Anki Desktop profile changes under one Kani DB | Opaque source binding, active-profile check, ID-overlap validation, and explicit new-profile/rebind flow before publication or writes. |
| Direct Anki database corruption | Forbid live DB access in code, docs, boundary tests, and provider design. |
| Review/sync atomicity regression | Shared transaction manager, savepoints, fault injection, cross-driver row-level conformance. |
| Restore not truly atomic on Windows | Native write-through replacement test; disable restore instead of copy fallback. |
| Desktop and Android progress divergence | Independent-profile disclosure and safe whole-backup transfer; defer event sync. |
| No desktop handwriting recognizer | Runtime capability routing with no rating or memory mutation; Android remains unchanged. |
| Activity/platform APIs leak back into shared UI | Final module/import boundary tests and host-only effect adapters. |
| Tray/background behavior differs by desktop session | Capability detection, in-app fallback, explicit run-at-login semantics. |
| `jpackage` runtime misses modules | Installed-image launch tests on each target OS; do not trust `suggestModules` alone. |
| Packaged dictionary/font/stroke assets drift or disappear | Hashed reference manifest, atomic cache upgrade, license inventory, and installed-image lookup tests. |
| Current CI runners hide minimum-OS breakage | Real/VM qualification on Windows 10, Ubuntu 20.04, and macOS 13 or raise the published minimum. |
| Windows upgrade identity changes | Generate once, commit, and pin the upgrade UUID in tests. |
| macOS/Windows signing credentials unavailable | Treat as external blocker for Goal 206; never publish "production" unsigned packages. |
| Checksum hosted beside a compromised binary | Authenticate the exact final manifest with an embedded Ed25519 public key. |
| Desktop release blocks Android | Publish Android through its existing independent gate; attach desktop assets afterward. |
| Downstream release builds the wrong commit | Trusted Android release metadata artifact, exact tag/SHA proof, inline desktop gate, detached checkout, and minimal permissions. |
| Auto-release fires on incomplete main work | Keep implementation in the clean feature worktree/branch and merge only with explicit authorization. |
| Personal Anki content leaks through tests/logs | Sanitized fixtures, aggregate-only evidence, redaction tests, bounded diagnostics. |
| User scratch files enter commits | Explicitly exclude `loop.md` and `plans/learning.md`; inspect every staged diff. |

## Completion evidence

Append one dated subsection per completed goal. Do not mark a goal complete
from a plan, mock-only success, or a nearly exhausted execution budget.

Template:

```md
### Goal N completion evidence (YYYY-MM-DD)

- Started from: `<commit>` on `<branch/worktree>`.
- Commits: `<sha subject>`.
- Implemented: `<concise module/file and behavior summary>`.
- Validation: `<exact commands, test counts, and outcomes>`.
- Live gates: `<AnkiDroid/Anki Desktop versions and sanitized result, or why not required>`.
- Decisions: `<confirmed/revised decisions>`.
- Rollback: `<safe adapter/source rollback>`.
- Gaps/blockers: `<none, or exact unresolved external dependency>`.
```
