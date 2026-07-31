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
- The build currently uses Gradle 9.4.1, AGP 9.1.0, Kotlin 2.4.10, Java 17,
  and Compose Multiplatform 1.11.1's aligned Android dependency line. The app
  module uses AGP's built-in kotlinc, while the catalog Kotlin version owns the
  JVM and Compose compiler plugins.
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
- The AnkiConnect adapter identifies the loaded profile before sync. Implemented
  (Goal 187) as `getMediaDirPath`, not the `getActiveProfile` this plan
  originally named: `getActiveProfile` is not an AnkiConnect action at all, and
  `getProfiles` lists every profile on the machine regardless of which is open,
  so neither can answer "which collection am I bound to". `getMediaDirPath`
  returns the *loaded* profile's media directory and fails when no collection is
  open, which makes it both the identity probe and the availability check. The
  mistake was only findable against a real host: the original probe reported
  every real Anki as unavailable while passing against a mock that implemented
  the name.
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
:data-sql -> :data-api, :core, :sync-api, :sync-domain, :dictionary-core
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
  <https://kotlinlang.org/docs/gradle-configure-project.html>
- Compose Multiplatform versions and target support:
  <https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html>
- Compose Multiplatform 1.11.1 dependency mapping:
  <https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html>
- Compose compiler setup:
  <https://kotlinlang.org/docs/multiplatform/compose-compiler.html>
- AGP built-in Kotlin migration/coupling:
  <https://developer.android.com/build/migrate-to-built-in-kotlin>
- Android Kotlin version support:
  <https://developer.android.com/build/kotlin-support>
- AGP 9.1 compatibility and patch notes:
  <https://developer.android.com/build/releases/agp-9-1-0-release-notes>
- Android Compose BOM mapping:
  <https://developer.android.com/develop/ui/compose/bom/bom-mapping>
- Compose Multiplatform Navigation:
  <https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html>
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

Goal 166 refreshed this evidence on 2026-07-26. Kotlin/KGP 2.4.10 officially
supports Gradle 7.6.3 through 9.5.0 and AGP 8.5.2 through 9.1.0. Therefore the
selected fully supported tuple is Gradle 9.4.1, AGP 9.1.0, JDK 17, and
Kotlin/Compose compiler 2.4.10. AGP 9.1.1 exists, but it is one patch beyond
Kotlin's published compatibility ceiling; the Goal 166 stop condition keeps
this branch on 9.1.0 rather than forcing an unqualified combination.

Compose Multiplatform 1.11.1 explicitly pairs with Kotlin/Compose compiler
2.4.10. Its Android variants map runtime, UI, and foundation to Jetpack
Compose 1.11.2, Material3 `1.11.0-alpha07` to Jetpack Material3
`1.5.0-alpha17`, and Navigation `2.9.2` to Jetpack Navigation `2.9.7`; its
desktop renderer uses Skiko `0.144.6` / Skia milestone 144. Android therefore
uses BOM `2026.05.01` for the 1.11.2 core line, pins the mapped Material3
version explicitly, removes the unused Android-only Navigation 2.9.8 pin, and
reserves the multiplatform Navigation coordinate for both hosts. This is a
time-sensitive implementation baseline and must be refreshed again before a
future dependency upgrade.

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
- Implement `requestPermission`, `version`, `apiReflect`, the loaded-profile
  identity probe, connection status, configuration validation, API-key
  authentication, and actionable failure mapping. The identity probe shipped as
  `getMediaDirPath`; `getActiveProfile`, named earlier in this plan, is not an
  AnkiConnect action.
- Make the initial `requestPermission` call without a key. Handle only pinned,
  fixture-backed permission/key response variants; prompt or consult the
  secret store only after this result. Once authentication is established,
  send API `"version": 6` and the optional `"key"` on every other request. A
  `multi` request repeats them in every nested action, validates every nested
  envelope, and redacts nested payloads too.
- Call `apiReflect` with `scopes: ["actions"]`; classify exact required versus
  optional actions and enforce a positive outbound action allowlist. The
  planned surface includes `requestPermission`, `version`, `apiReflect`,
  `getMediaDirPath`, `modelNamesAndIds`, `modelFieldNames`, `modelTemplates`,
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

### Goal 203: Add Anki-style Study keybindings and qualify desktop usability

**Depends on:** Goal 202.

**Outcome:** Desktop Study has a deliberate, configurable keyboard workflow
that is familiar to Anki users, and desktop support is usable beyond the
happy-path developer machine.

**Work:**

- Add a portable Study command/keybinding model owned by shared presentation
  code. Platform hosts translate native key events into commands; they never
  call scheduler or repository operations directly.
- Ship Anki-compatible defaults where Kani has the same semantic action:
  `Space`/`Enter` reveals a self-graded card, `1` submits Fail/Again, `3`
  submits Pass/Good, and `Space`/`Enter` invokes the single safe default action
  after reveal or persisted feedback. Do not invent selectable Hard/Easy
  ratings: `2` and `4` remain unbound unless the current task genuinely exposes
  those actions. `Ctrl+Z` on Windows/Linux and `Cmd+Z` on macOS request the
  existing guarded review undo.
- Define task-specific bindings instead of forcing flashcard grading shortcuts
  onto every surface. Number keys select visible multiple-choice options;
  `Enter` submits typed answers only after IME composition completes; writing
  shortcuts cannot bypass ink evaluation, manufacture recognition, or expose
  ratings forbidden by the writing contract. Continue and undo remain subject
  to the same APPLIED/token/revision authority as pointer input.
- Add a Settings keybinding editor with per-command remapping, conflict and
  reserved-OS-shortcut validation, platform-labelled modifiers, and reset to
  defaults. Store bindings as device-local settings so backup transfer does
  not impose macOS modifiers on Windows/Linux. Unknown commands and malformed
  stored bindings fail open to the reviewed defaults.
- Centralize dispatch precedence. Text fields, IME composition, menus, and
  modal dialogs consume their own keys first; global Study bindings do not
  capture printable input from an editor. Ignore key-up duplicates and
  auto-repeat for commit, continue, and undo commands. Match number-row and
  numpad keys consistently without depending on keyboard layout.
- Expose active accelerators through native menus and accessible action
  semantics, and show current bindings in the keybinding editor. Keep pointer
  and touch actions fully supported.
- Add reducer, host-adapter, focus/IME, duplicate-event, rapid-input,
  process-recreation, and end-to-end tests proving keyboard and pointer paths
  dispatch the same command exactly once.
- Audit all remaining keyboard-only navigation, focus order/visibility,
  dialogs, menus, tables/charts alternatives, and screen-reader semantics.
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

1. `feat: add portable Study commands and default keybindings`
2. `feat: add desktop keybinding settings and host dispatch`
3. `test: prove Study keyboard and pointer parity`
4. `test: add desktop scaling accessibility and locale matrices`
5. `fix: close remaining desktop accessibility and input gaps`
6. `perf: enforce desktop startup sync and Study budgets`
7. `docs: record supported desktop accessibility and keybinding behavior`

**Done when:** A user can complete and undo every compatible Study task without
a pointer; default and remapped bindings dispatch the same guarded commands as
visible controls exactly once; typing, IME, writing, dialogs, and stale
callbacks cannot trigger an unintended grade; no critical route is mouse-only;
supported platform accessibility claims match evidence; performance budgets
pass on release hardware/runners.

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

### Goal 164 completion evidence (2026-07-26)

- Started from: `bd5d2dd0` (`origin/main`) on `desktop/support` in
  `/home/bee/Documents/src/github/kanji_anki-desktop-support`; the required
  plan-only baseline commit is `845569d4`.
- Commits: `70b20a23 docs: define the desktop product and parity contract`;
  `9dfd570b docs: reconcile the pending architecture goals`;
  `a45c5ada test: encode the Android and desktop module graph`.
- Implemented: added the architecture index and six accepted ADRs; reconciled
  normal tag-only sync/write-back with the capability-gated additive Missing
  Kanji exception; mapped every pending Goal 148-163 to this plan; marked only
  the old desktop non-goal as superseded; and encoded the 34-module final DAG,
  reviewed migration-only edges, rationales, platform separation, import
  guards, and fail-closed unknown dependency handling. No source module moved
  and no runtime code changed.
- Validation: `git diff --check` passed;
  `python3 -m unittest tools.test_module_boundaries` passed 17 tests; and
  `ANDROID_HOME=/home/bee/.cache/codex-android-sdk
  ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk ./gradlew ciFast
  ciQuality --no-daemon --console=plain` completed `BUILD SUCCESSFUL` in
  12m03s with 110 actionable tasks (99 executed, two from cache, nine
  up-to-date). The documented `/tmp/android-sdk` was absent, so the successful
  rerun used the installed cached SDK containing platform/build-tools 36.
- Live gates: not required. This goal changes documentation and architecture
  tests only; it does not change Android/desktop runtime, schema, scheduler,
  provider, sync, persistence, or release behavior.
- Decisions: accepted loopback AnkiConnect-only desktop access, independent
  installation-local Kani profiles, explicit nullable provider-FSRS
  capability, shared SQL behind platform drivers, capability-routed
  handwriting, and exact-commit but failure-independent platform publication.
  Confirmed the absolute prohibition on Anki scheduling writes. Clarified that
  `:provider-ankiconnect` consumes the platform `SecretStore` contract and that
  widget refresh consumes committed app-event contracts.
- Rollback: revert the three Goal 164 commits (and the plan-only baseline if
  abandoning the epic). This removes only documentation and Python boundary
  policy; it requires no schema migration, data conversion, provider action,
  or user-state rollback.
- Gaps/blockers: none for Goal 164. The branch remains local and unpushed;
  Goal 168's explicit cross-OS push/CI authorization checkpoint remains in
  force.

### Goal 165 completion evidence (2026-07-26)

- Started from: `6459eafc` on `desktop/support` in
  `/home/bee/Documents/src/github/kanji_anki-desktop-support`; the final
  implementation commit that passed the gates is `ed089883`.
- Commits: `6593a4b8 test: freeze schema and transaction baselines`;
  `bed0d80f test: freeze provider scheduler and UI baselines`;
  `ed089883 docs: record desktop conversion performance baselines`.
- Implemented: added sanitized, provenance-pinned schema fixtures for fresh
  v33 and v1/v30/v31/v32 migration paths; deterministic schema fingerprints
  and transaction/storage goldens; fake-provider and adaptive-scheduler
  snapshots; 63 paired Android PNG/semantics route-state goldens; rollback-safe
  sharded record/compare tooling; and a reproducible source, toolchain, test,
  compile, and cold-start performance baseline. No production source module
  moved or changed.
- Validation: the focused schema/transaction regression selection passed 110
  tests across 18 suites; the forced scheduler run
  `./gradlew :core:test --tests
  dev.bee.kanjianki.core.AdaptiveSchedulerGoldenSnapshotTest --rerun-tasks
  --no-daemon --console=plain` completed `BUILD SUCCESSFUL`; the static UI
  validator reported exactly 63 PNGs and 63 semantics snapshots; and
  `python3 -m unittest ci.tests.test_classify_device_smoke` passed 21 tests.
  The final
  `ANDROID_HOME=/home/bee/.cache/codex-android-sdk
  ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk ./gradlew ciFast
  ciQuality --no-daemon --console=plain` completed `BUILD SUCCESSFUL` in
  5m28s with 110 actionable tasks (21 executed, 27 from cache, 62 up-to-date).
  Its result tree contains 3,247 passing Gradle/JUnit tests, while the
  independently rerun `tools`, `scripts/tests`, and `ci/tests` suites passed
  73, 94, and 77 tests respectively: 3,491 deterministic checks in total.
  `git diff --check` passed.
- Live gates: on the API 35 Android emulator, the fake-provider production-path
  comparison completed `OK (1 test)` with terminal instrumentation code `-1`,
  zero fatal logcat matches, and byte-for-byte record regeneration. The UI
  recorder produced the exact 63/63 set in six bounded shards at font scales
  1.0 and 2.0; two subsequent clean comparison runs each reported seven
  passing shard/contract invocations, seven terminal `-1` codes, and no
  fatal, failure, dead-object, UiAutomation, or SIGKILL matches. A real
  AnkiDroid collection run is not required because this goal adds only
  test/debug assets, scripts, and documentation and changes no production
  provider or sync behavior.
- Decisions: confirmed schema v33 and stats cache format 11 as the conversion
  baseline; froze token-first review persistence, successful-run-only sync
  history, WAL-safe snapshot/restore failure behavior, the v31 two-core
  adaptive routes, the provider's capability differences and note-tag-only
  normal write surface, and the Android UI shell/route semantics. Compile
  medians are 78.459s clean and 18.618s incremental; the representative
  Android cold-start median is 1,852ms under the documented host-load caveat.
- Rollback: revert `ed089883`, `bed0d80f`, and `6593a4b8` in that order. This
  removes only documentation, tests, debug fixtures, assets, and CI helper
  scripts; it requires no database migration, provider write, user-data
  conversion, or runtime rollback.
- Gaps/blockers: none for Goal 165. Three independent read-only audits found no
  blocker and confirmed deterministic, mutation-sensitive fixtures with no
  personal collection content or production-source change. The branch remains
  local and unpushed; Goal 168's explicit cross-OS push/CI authorization
  checkpoint remains in force.

### Goal 166 completion evidence (2026-07-26)

- Started from: `2870666a` on `desktop/support` in
  `/home/bee/Documents/src/github/kanji_anki-desktop-support`.
- Commits: `e2fe8a71 build: align Kotlin and Compose Multiplatform versions`;
  `4f84cd66 build: make Android compiler ownership explicit`;
  `20d4734e test: cover the aligned build toolchain`; plus the two
  validation-forced follow-ups `9a3ad349 build: refresh Gradle wrapper
  artifacts` and `5b8b4cfa test: clear writing-core Kotlin warnings`, and the
  audit-forced `e0689757 test: reject mixed Compose JVM graphs`.
- Implemented: aligned the supported tuple on Gradle 9.4.1, AGP 9.1.0,
  JDK 17, Kotlin/KGP/Compose compiler 2.4.10, and Compose Multiplatform
  1.11.1; kept `:app` on AGP built-in Kotlin with no
  `org.jetbrains.kotlin.android`; made Kotlin warnings fatal across the app,
  JVM libraries, Android library conventions, and build logic; replaced the
  deprecated JVM-default flag with `JvmDefaultMode.NO_COMPATIBILITY`; and
  regenerated the matching official Gradle wrapper. Added static ownership
  contracts, positive and negative Android/JVM TestKit fixtures, and a
  compiled Compose JVM fixture that resolves runtime/UI 1.11.1, explicit
  Material3 `1.11.0-alpha07`, Navigation 2.9.2, and Skiko 0.144.6. The shared
  catalog deliberately bypasses the Compose plugin's older
  `compose.material3` convenience version and pins the release-table
  Material3 coordinate. The fixture compares the entire selected
  Compose/Navigation/Skiko/compiler coordinate set to an exact allowlist, so
  a stale or parallel version cannot hide beside the required coordinates.
  Strict verification gained 212 reviewed components and 341 artifact
  checksums across the Kotlin/Compose plugin and Linux JVM runtime graphs.
- Validation: `./gradlew testBuildLogic --no-build-cache --no-daemon
  --dependency-verification=strict --console=plain` completed
  `BUILD SUCCESSFUL` in 4m09s with 18 passing tests, including intentional
  warning failures for AGP built-in Kotlin and JVM KGP. The clean command
  `ANDROID_HOME=/home/bee/.cache/codex-android-sdk
  ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk ./gradlew clean
  :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
  --no-build-cache --no-daemon --dependency-verification=strict
  --console=plain` completed `BUILD SUCCESSFUL` in 3m27s with 58 actionable
  tasks (49 executed, nine up-to-date). The initial complete
  `ANDROID_HOME=/home/bee/.cache/codex-android-sdk
  ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk ./gradlew ciFast
  ciQuality --no-daemon --dependency-verification=strict --console=plain`
  completed `BUILD SUCCESSFUL` in 5m30s with 101 actionable tasks (16
  executed, 85 up-to-date). After independent audit strengthened the mixed
  graph assertion, `./gradlew -p build-logic test --tests
  dev.bee.kanjianki.buildlogic.ComposeMultiplatformToolchainFunctionalTest
  --no-build-cache --no-daemon --dependency-verification=strict
  --console=plain` passed in 1m04s, and the final `ciFast ciQuality` command
  above passed again in 1m34s with 101 actionable tasks (five executed, 96
  up-to-date). The result tree contains 3,254 passing
  Gradle/JUnit tests; the independently invoked `tools`, `scripts/tests`, and
  `ci/tests` suites passed 73, 94, and 77 tests respectively, for 3,498
  deterministic checks and zero failures.
- Wrapper/dependency evidence: `./gradlew wrapper --gradle-version 9.4.1
  --distribution-type bin --gradle-distribution-sha256-sum
  2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb
  --validate-url --no-daemon --dependency-verification=strict
  --console=plain` passed in 25s. The regenerated wrapper JAR SHA-256 is
  `55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c`,
  matching Gradle's published 9.4.1 checksum. The Compose JVM fixture passed
  with strict verification and printed the exact selected compiler, runtime,
  UI, Material3, Navigation, desktop, and Skiko coordinates; the losing
  Material3/Navigation transitive requests resolved upward to the selected
  1.11.1 graph rather than creating parallel runtime versions.
- Live gates: not required. Goal 166 changes build ownership, dependency
  metadata, generated wrapper files, compiler diagnostics, and
  behavior-neutral nullability/interoperability expressions only; it changes
  no schema, provider query/write, sync transaction, scheduler transition, or
  user-facing Android behavior.
- Decisions: retained AGP 9.1.0 because Kotlin 2.4.10's published support
  matrix stops at 9.1.0, rather than forcing the available but unsupported
  AGP 9.1.1 patch. Confirmed the Android mappings of Compose runtime/UI
  1.11.2 and Material3 `1.5.0-alpha17`, while the desktop artifact graph uses
  Compose runtime/UI 1.11.1 and Material3 `1.11.0-alpha07`; both hosts share
  JetBrains Navigation 2.9.2. Confirmed that every Compose module must apply
  the Kotlin Compose compiler plugin and that no compatibility or dependency
  verification opt-out is present.
- Rollback: revert `e0689757`, `5b8b4cfa`, `9a3ad349`, `20d4734e`,
  `4f84cd66`, and `e2fe8a71` in that order. This restores only build files,
  generated wrapper artifacts, verification metadata, tests, and
  behavior-neutral compiler cleanups; it requires no schema migration,
  provider action, collection repair, or user-state rollback.
- Gaps/blockers: none for Goal 166. The strict native verification additions
  in this goal cover the deterministic Linux JVM fixture; Goal 168 still owns
  resolving and reviewing Windows and macOS native artifacts on their actual
  runners. Independent audits found no production-behavior blocker; one audit
  found that the original graph test asserted presence without excluding a
  parallel stale version, and `e0689757` closed that test-contract gap with
  an exact selected-coordinate allowlist before the final green gate. The
  branch remains local and unpushed, and the Goal 168 cross-OS push/CI
  authorization checkpoint remains in force.

### Goal 167 completion evidence (2026-07-26)

- Started from: `a4dc7937` on `desktop/support` in
  `/home/bee/Documents/src/github/kanji_anki-desktop-support`.
- Commits: `b293c4ed build: add desktop and multiplatform conventions`;
  `d8640e09 build: register the desktop application foundation`;
  `65f28879 feat: add the Kani desktop smoke launcher`; and
  `ec05da55 build: fix desktop package identity and icon sources`;
  `3c7c3074 fix: enforce desktop package and smoke contracts`; and
  `e517c9fb test: verify icons against the canonical render`.
- Implemented: added reusable normal-JVM desktop-application and
  Android-KMP/desktop shared-Compose library conventions. The shared
  convention is the sole owner of the AGP 9.1 `kotlin { android { ... } }`
  target, explicitly enables Android host and device tests, connects the
  device test to the shared test tree, and configures Java 17, fatal
  warnings, resources, lint, coverage, and reproducible archives. Registered
  the project-dependency-empty `:desktop-app`, which renders the
  `Kani desktop foundation` window and supports the paired
  `--smoke-test --temporary-data` mode without resolving or retaining the
  normal profile. The smoke lifecycle removes the Compose window, returns
  from `application(exitProcessOnExit = false)`, deletes its temporary root,
  and only then emits and flushes the readiness marker. Centralized and
  pinned `Kani`, `dev.bee.kanjianki.desktop`, main class, description,
  vendor, and Windows upgrade UUID
  `C972670E-BCCD-4D5E-9ACC-2C8877ABA799`. Added one hand-authored vector
  source plus deterministic PNG/ICO/ICNS generation and a
  regeneration-and-byte-comparison verification path. The committed manifest
  pins the 512px PNG, seven ICO frames, eleven ICNS chunks, source/output
  SHA-256 values, and the reviewed generator tool versions. Native package
  tasks are restricted to the three supported formats: DMG, MSI, and DEB.
- Validation:
  `ANDROID_HOME=/home/bee/.cache/codex-android-sdk
  ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk ./gradlew
  testBuildLogic :desktop-app:check verifyDesktopIcons --no-build-cache
  --no-daemon --dependency-verification=strict --console=plain` completed
  `BUILD SUCCESSFUL` in 2m44s with 22 actionable tasks. Its result tree has
  26 passing build-logic tests and four passing desktop-launcher tests,
  including compiled common/desktop/Android-main/host/device KMP fixtures,
  shared resource access, common-test inheritance, Robolectric resources,
  coverage, task registration, archive policy, package identity, and
  intentional warning-as-error failures. After the independent audit fixes,
  `./gradlew testBuildLogic :desktop-app:test --no-build-cache --no-daemon
  --dependency-verification=strict --console=plain` passed in 2m10s with all
  26 build-logic tests and five desktop-launcher tests, including early-window
  close rejection before readiness. `python3 -m unittest discover -s tools -p
  'test_*.py'` passed all 75 tests, including the icon-source mutation test;
  direct `python3 tools/generate_desktop_icons.py --check` and
  `git diff --check` passed. A checksum-verified Eclipse Temurin 17.0.20+8 control
  (`OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz`, SHA-256
  `be7668bc030d578b83d6d5ef9221d6d6729bbbca8cf94a7d52e16ac68b5a5a35`)
  rebuilt `:desktop-app:createDistributable` with all 17 tasks executed in
  1m13s under no-build-cache, rerun-tasks, and strict verification. A
  `packageDistributionForCurrentOS --dry-run` under that JDK exposed only
  `packageDeb`, `packageDmg`, and `packageMsi`, with no RPM, PKG, or EXE task.
  The installed-image runtime reports Java 17.0.20, and its launcher has no
  dynamic `libstdc++.so.6` dependency. Five consecutive packaged launches
  each exited zero, emitted exactly one readiness marker with no native
  warning, and left zero `kani-desktop-smoke-*` roots. Finally,
  `ANDROID_HOME=/home/bee/.cache/codex-android-sdk
  ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk ./gradlew ciFast
  ciQuality --no-build-cache --no-daemon --dependency-verification=strict
  --console=plain` completed a clean `BUILD SUCCESSFUL` in 12m04s with 110
  actionable tasks (68 executed, 42 up-to-date), then passed again after all
  audit fixes in 34s with 110 actionable tasks (4 executed, 106 up-to-date).
- Live gates: a real AnkiDroid emulator run is not required. Goal 167 adds
  build conventions, an isolated project-dependency-empty desktop host,
  package metadata/icons, and desktop-only launcher behavior; it changes no
  Android production dependency, schema, provider query/write, sync transaction,
  scheduler transition, persistence, or release behavior. The unchanged
  deterministic Android surface passed `ciFast` and `ciQuality`.
- Decisions: kept `:desktop-app` on ordinary Kotlin/JVM and reserved KMP for
  shared libraries; used the new Android-KMP plugin instead of classic
  Android plugins or `androidTarget`; made the convention the single owner of
  both Android test builders; kept `compose.desktop.currentOs` in the app
  rather than shared production code; restricted declared package formats to
  the supported DMG/MSI/DEB set; and made readiness certify renderer startup,
  an explicit smoke-render result, orderly window removal, and
  temporary-profile deletion. The local
  Arch/CachyOS OpenJDK 17.0.19 `jpackage` launcher was independently traced
  to a pre-JVM helper crash caused by dynamic `libstdc++` linkage, matching
  OpenJDK [JDK-8348560](https://bugs.openjdk.org/browse/JDK-8348560) and the
  Java 17 report
  [JDK-8297026](https://bugs.openjdk.org/browse/JDK-8297026). No app-side
  stderr filter or renderer workaround was added; the clean Temurin Java 17
  control proves the Kani image and smoke contract. Goal 204 remains the
  owner of the formal per-host packaging-JDK vendor, patch, URL, checksum,
  and launcher-linkage pin.
- Rollback: revert `e517c9fb`, `3c7c3074`, `ec05da55`, `65f28879`,
  `d8640e09`, and `b293c4ed` in that order. This removes only the isolated
  desktop module, convention plugins/fixtures, package identity/icons,
  reviewed verification metadata, and settings/boundary registration; it
  requires no schema migration, provider action, data conversion, or
  user-state rollback.
- Gaps/blockers: none in the Goal 167 implementation or local Linux/Android
  gates. Initial independent audits found three contract gaps: unsupported
  native formats were declared, stale icon bytes could be re-manifested
  without regenerating from the SVG, and an early normal window close could
  qualify as smoke readiness. `3c7c3074` and `e517c9fb` close those gaps and
  add mutation-sensitive coverage. Three independent final audits approved
  `ed067aaf`: the scope audit found no Android-production or Goal 168 leakage;
  the build-state audit repeated the strict package dry-run, canonical icon
  check, and boundary tests; and the smoke/shutdown audit repeated the former
  icon exploit, all five launcher tests, and a clean Temurin installed-image
  launch. No Goal 167 blocker remains. Goal 168 still owns the three-host
  strict dependency/bootstrap matrix and CI smoke coverage; Goal 204 owns the
  release-packaging JDK pin and installed-package qualification. The branch
  remains local and unpushed, and Goal 168's explicit cross-OS push/CI
  authorization checkpoint remains in force.

### Goal 168 completion evidence (2026-07-26)

- Started from: `13f77e69` on `desktop/support` in
  `/home/bee/Documents/src/github/kanji_anki-desktop-support`. The authorized
  cross-host bootstrap began from that clean Goal 167 evidence commit; the
  permanent draft PR targets `bd5d2dd0` on `main`.
- Commits: `39dcc03d ci: bootstrap cross-platform desktop verification`;
  `704d08e4 fix: harden the desktop bootstrap across hosts`; `01ec51b1 fix:
  make desktop packaging host-portable`; `6fcf44a0 fix: normalize desktop
  smoke behavior across hosts`; `a97324d3 fix: recognize the macOS software
  renderer notice`; `e2129c69 ci: capture host AAPT2 bootstrap metadata`;
  `2adabe39 build: trust reviewed desktop host artifacts`; `a86e46e5 ci:
  enforce the permanent desktop matrix`; `090c1f92 fix: include desktop
  coverage in quality analysis`; and `7b08d133 ci: scope desktop workflow
  permissions`. The exact preceding implementation SHA that passed every
  final gate is `7b08d133`.
- Implemented: added root `ciDesktop`, `ciDesktopPackage`, and current-host
  `ciAll` aggregates while keeping `ciFast` and `ciRelease` Android-only;
  added an always-present classified desktop workflow for Ubuntu 24.04 X64,
  Windows 2025 X64, and macOS 15 ARM64; and pinned wrapper validation,
  Java/action versions, cache isolation, architecture assertions, failure
  diagnostics, package/image smoke, and verification-metadata mutation
  guards. Android CI now treats every planned desktop/shared/build/release
  path as a full Android release input. The explicitly authorized temporary
  workflow generated host metadata independently, retained the three host
  manifests/artifacts, validated their provenance, and deterministically
  merged them. The reviewed delta is additive only: four components, seven
  artifacts, seven SHA-256 values, no deletion, with combined metadata
  SHA-256
  `f4c63cf5ae1791d764d1b4a84d3c22afe9e595058d7721f74a4f2f8df2772266`.
  The permanent workflow contains no bootstrap/write/merge/download path.
  Final audit work also imports `:desktop-app` bytecode and JaCoCo XML into
  Sonar, covers launcher/error behavior, isolates only the three-host
  smoke-tested Compose/native-window boundary from line coverage, and moves
  repository read permission to the two checkout jobs.
- Validation:
  `python -m unittest tools.test_desktop_ci_workflow
  tools.test_release_workflows` passed 44 tests; the full Python surfaces run
  by the Gradle gates passed 124 tool tests and 96 CI tests.
  `./gradlew :desktop-app:test :desktop-app:jacocoTestReport --no-daemon
  --no-build-cache --dependency-verification=strict --console=plain` passed
  all 13 launcher tests. Its included launcher report records 79/98 covered
  lines and 42/42 covered branches. The focused
  `:app:testDebugUnitTest --tests
  dev.bee.kanjianki.MissingKanjiScreenModelTest` passed and covers the only
  incidental changed Android line. `./gradlew ciQuality sonarPreflight
  --no-daemon --no-build-cache --dependency-verification=strict
  --console=plain` completed `BUILD SUCCESSFUL` in 6m23s with 120 actionable
  tasks. `./gradlew smokeDesktopInstalledImage --no-daemon --no-build-cache
  --dependency-verification=strict --console=plain` completed `BUILD
  SUCCESSFUL` in 21s and emitted exactly
  `KANI_DESKTOP_SMOKE_READY temporary_data=true`. YAML parsing,
  `git diff --check`, strict metadata scope/diff checks, deterministic icon
  checks, and independent coverage/security review all passed.
- Live gates: authorized bootstrap run
  [30209692669](https://github.com/bee-san/kanji_anki/actions/runs/30209692669)
  passed independently on all three hosts at `e2129c69`; the downloaded
  combined metadata and all four generated audit files were byte-identical
  to an independent local revalidation. Permanent run
  [30212024639](https://github.com/bee-san/kanji_anki/actions/runs/30212024639)
  passed at `7b08d133`: Ubuntu 24.04 X64 built
  `kani_0.4.33-1_amd64.deb` and finished its strict gate in 7m59s; Windows
  2025 X64 (current image `windows-2025-vs2026`) built `Kani-0.4.33.msi` and
  finished in 10m30s; macOS 15 ARM64 built `Kani-1.4.33.dmg` and finished in
  9m15s. Every host ran 18 CI-script tests, 68 desktop-tooling tests (one
  intentional Windows skip), emitted the exact temporary-data readiness
  marker, passed tracked-scope validation, and left
  `gradle/verification-metadata.xml` unchanged. The always-present confidence
  gate passed. Android CI run
  [30212024636](https://github.com/bee-san/kanji_anki/actions/runs/30212024636)
  passed its full deterministic surface and aggregate; Android device-smoke
  run
  [30212024635](https://github.com/bee-san/kanji_anki/actions/runs/30212024635)
  passed API 26, API 35, and the API 35 risk suite. Sonar run
  [30212024632](https://github.com/bee-san/kanji_anki/actions/runs/30212024632)
  passed in 15m04s; its external quality gate is `OK` with 86.6% new-code
  coverage, A ratings for reliability/security/maintainability, zero
  duplication, 100% reviewed hotspots, and zero open issues. A copied
  real-collection AnkiDroid gate is not required because Goal 168 changes no
  provider, sync, persistence, scheduler, or Android product behavior.
- Decisions: kept `ciAll` explicitly current-host-only and preserved the
  `Android CI -> Android Release -> Desktop Release` trigger semantics without
  coupling Android validation to desktop signing. Kept untrusted PR jobs
  secret-free, cache-write-free, and token-write-free. Trusted only
  independently downloaded Google Maven/Maven Central bytes: Windows adds
  PE X86-64 AAPT2 plus Compose/Skiko X64 artifacts, macOS adds Mach-O ARM64
  Compose/Skiko artifacts, and Linux required no new host artifact. Kept
  normal verification permanently strict and treated installed-image smoke,
  rather than JVM line instrumentation, as the authoritative renderer/window
  boundary test on each OS. The feature PR remains draft and unmerged.
- Rollback: revert `7b08d133`, `090c1f92`, `a86e46e5`, `2adabe39`,
  `e2129c69`, `a97324d3`, `6fcf44a0`, `01ec51b1`, `704d08e4`, and
  `39dcc03d` in that reverse chronological order. This removes only CI,
  verification metadata, host-portable package/smoke adjustments, coverage
  wiring, and their tests/docs; it requires no database migration, provider
  rollback, or user-state conversion.
- Gaps/blockers: none. The local workstation lacks `dpkg-deb`, so its direct
  native-DEB attempt stopped at the host-tool prerequisite after `ciDesktop`
  had passed; the authoritative Ubuntu 24.04 strict matrix built and smoked
  the DEB successfully. Two independent final audits approved the metadata
  provenance and the Sonar/least-privilege closure. Draft PR
  [#592](https://github.com/bee-san/kanji_anki/pull/592) is open, mergeable,
  has a fully green implementation head at `7b08d133`, and is intentionally
  not merged.

### Goal 169 completion evidence (2026-07-27)

- Started from: `291c66e7` on `desktop/support` in
  `/Users/skerraut/kanji_anki`. The exact preceding implementation SHA that
  passed the final gates is `085958e2`.
- Commits: `ef76a43d refactor: introduce the device settings platform
  contract`; `e6217184 refactor: split portable and device-local settings`;
  `77790f48 refactor: extract the portable data repository API`; and
  `085958e2 test: enforce data API implementation boundaries`.
- Implemented: added pure-JVM `:platform-contracts` typed reader, snapshot,
  atomic editor/store, value types, and stable device-local key namespace.
  The temporary Android host implementation uses synchronous
  `SharedPreferences.commit`, snapshots grouped reads, serializes edits, and
  fails closed after a durability failure. Portable scheduler, import, study,
  appearance, repository snapshots, commands, review commit values,
  `StoreResult`, and focused repository fakes now live in pure-JVM
  `:data-api`, which depends only on `:core` and `:sync-domain`. The five
  repository interfaces expose no Android, SQLite, Activity, provider
  implementation, or raw database type. Atomic review commit and sync
  publication remain one repository operation each; the transaction-only
  `StaleReviewCommitException` remains internal to the Android app host.
- Migration: reminder history/configuration, automatic sync/update state, and
  debug logging moved out of portable SQLite settings without changing their
  stored values. `LocalStore` construction still does not open SQLite.
  `LocalStoreBase.onOpen` performs one direct legacy query on first actual
  database use, durably commits missing values to the device store before
  deleting all legacy keys, records a durable completion marker, invalidates
  settings caches, and retries safely after interruption. Portable backups no
  longer contain those host-local values. No database schema version or
  scheduler record changed.
- Validation: focused extraction checks passed
  `./gradlew :data-api:check :app:testDebugUnitTest
  :app:compileDebugAndroidTestKotlin
  :app:compileDebugAndroidTestJavaWithJavac --no-daemon
  --dependency-verification=strict --console=plain` with 1,538 app tests and
  17 `:data-api` tests, all with zero failures or skips.
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew
  ciFast ciQuality ciDesktop --no-daemon --dependency-verification=strict
  --console=plain` completed `BUILD SUCCESSFUL` in 55s with 133 actionable
  tasks (51 executed, 82 up-to-date). The result trees contain 3,300 passing
  Gradle/JUnit tests and no failures or skips. The independently invoked
  `tools`, `scripts/tests`, and `ci/tests` suites passed 126, 94, and 96 tests
  respectively; the tools total includes all 19 module-boundary tests.
  `./gradlew sonarPreflight --no-daemon --dependency-verification=strict
  --console=plain` then passed with every new Kotlin/Java/test-fixture
  bytecode directory and JaCoCo XML present. `git diff --check` passed. The
  first aggregate attempt exposed one stale exact-list expectation in the
  desktop gate; the boundary commit added both `:platform-contracts` and
  `:data-api` to that contract before the final green run.
- Live gates: a copied-collection AnkiDroid run is not required. Goal 169
  moves contracts and host-local settings ownership, but changes no provider
  query/write, sync publication behavior, card/note interpretation, scheduler
  transition, database schema, or user-facing study behavior. Android unit,
  instrumentation compilation, lint, and all shared/desktop deterministic
  surfaces passed.
- Decisions: device-local settings are not portable backup state; secret
  values remain outside `DeviceSettingsStore`, which may hold only host secret
  references. Migration is lazy on first database/device-settings access so
  startup object construction remains side-effect free. Existing core and
  sync-domain models remain canonical rather than being copied into
  `:data-api`. Generated Java/Kotlin default-argument compatibility and every
  exported payload family have focused API tests with 100% class coverage.
  Root `ciFast`, `ciQuality`, `ciDesktop`, and deterministic Sonar inputs now
  all include `:data-api`.
- Rollback: because these commits are unmerged and unreleased, revert
  `085958e2`, `77790f48`, `e6217184`, and `ef76a43d` in that order before any
  distribution. This restores source/module ownership with no schema or
  provider rollback. If the migration were ever shipped before rollback, add
  a forward compatibility step that copies the typed device values back to
  their legacy SQLite keys before reverting `e6217184`; reverting directly
  after users have migrated would otherwise reset host-local automation and
  notification preferences to defaults.
- Gaps/blockers: none for Goal 169. The Android implementation intentionally
  remains in the app host until Goal 177, and shared SQL implementation work
  remains owned by Goals 178-185. The branch is four commits ahead of the
  last pushed `origin/desktop/support` head and remains unpushed; the existing
  draft PR is intentionally not updated without explicit authorization.

### Goal 170 completion evidence (2026-07-27)

- Started from: `3f2b4655` on `desktop/support` in
  `/Users/skerraut/kanji_anki`. The exact preceding implementation SHA that
  passed the final gates is `b1bc65f7`.
- Commits: `b92b4573 refactor: add the shared application module`;
  `9b3bafdc refactor: add the process-owned Kani container`;
  `a13594bb refactor: move Android dependency lifetime out of the activity`;
  and `b1bc65f7 test: cover startup restore and shutdown ordering`.
- Implemented: added pure-JVM `:application` with narrow repository,
  device-settings, and task-executor ownership contracts plus a deterministic
  desktop lifecycle that acquires the profile lock, applies restore, opens
  data, starts services, builds presentation, and releases acquired resources
  in reverse order. `KaniApplication` now creates one `AndroidKaniContainer`
  only after the staged-restore result permits database-capable startup. That
  process graph owns the primary `LocalStore`, repositories, AnkiDroid
  gateway, user-I/O and maintenance executors, and coroutine dispatchers;
  Activity recreation borrows them and no longer closes them. Workers,
  receivers, services, widgets, schedulers, and process diagnostics resolve
  the graph lazily, while short-lived concurrent database helpers remain
  graph-owned factory products and close after each operation. WorkManager's
  configuration and worker construction do not resolve the graph or open
  SQLite.
- Validation: `./gradlew :app:testDebugUnitTest :application:check --no-daemon
  --dependency-verification=strict --console=plain --no-parallel` passed with
  1,545 app tests and 12 application lifecycle tests, with zero failures or
  skips. `python3 -m unittest tools.test_module_boundaries` passed all 19
  tests, and `git diff --check` passed. The final aggregate command
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew
  ciFast ciQuality ciDesktop --no-daemon --dependency-verification=strict
  --console=plain` completed `BUILD SUCCESSFUL` in 1m50s with 142 actionable
  tasks (16 executed, 126 up-to-date). This checkout's `local.properties`
  selects the prepared Homebrew SDK at
  `/opt/homebrew/share/android-commandlinetools`; the persisted Gradle/JUnit
  result trees contain 3,319 passing tests and no failures or skips. The
  aggregate Python surfaces passed 126 tooling, 94 asset/script, 96 CI, and
  70 desktop-tooling tests.
- Live gates: on the API 35 arm64 emulator, the exact targeted command
  `adb shell am instrument -w -e class
  'dev.bee.kanjianki.ProcessContainerLifecycleInstrumentedTest,dev.bee.kanjianki.ColdStartRestoreBoundaryInstrumentedTest'
  dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner` completed
  `OK (2 tests)` in 2.387s. Recreation retained the exact process store,
  gateway, executors, and dispatchers after Activity close. The cold-process
  probe installed an invalid restore marker, preserved the database and all
  sidecar fingerprints, and observed no receiver signal; logcat showed the
  probe process fail in `KaniApplication.onCreate` with `Restore cleanup must
  finish before Kani can start`, then start and run the receiver only after
  marker cleanup. A copied-collection AnkiDroid run is not required because
  this goal changes dependency lifetime and startup ordering, not provider
  queries/writes, sync publication, scheduler behavior, or schema.
- Decisions: process lifetime, rather than Activity lifetime, owns Android
  database/executor resources. The restore gate is a one-attempt process
  boundary; a blocked or failed attempt cannot expose a partial container.
  AndroidX Startup and WorkManager may inspect configuration before
  `Application.onCreate`, but dependency resolution remains lazy until a
  worker executes after successful application startup. Desktop keeps the
  profile lock through presentation shutdown and preserves the first failure
  while suppressing later reverse-order close failures. No new mutable global
  test override was introduced.
- Rollback: because these commits are unmerged and unreleased, revert
  `b1bc65f7`, `a13594bb`, `9b3bafdc`, and `b92b4573` in that order. This
  restores Activity-owned lifetime and removes only composition/lifecycle
  code and tests; it requires no schema migration, provider action, backup
  conversion, or user-state rollback.
- Gaps/blockers: none for Goal 170. Android platform implementations remain in
  `:app` until Goal 177, and route-level direct `LocalStore` calls remain for
  the sequential repository migrations in Goals 171-173. The branch remains
  local and unpushed; the existing draft PR is intentionally not updated
  without explicit authorization.

### Goal 171 completion evidence (2026-07-27)

- Started from: `4cf9b77b` on `desktop/support` in
  `/Users/skerraut/kanji_anki`. The exact preceding implementation SHA that
  passed the final gates is `2fee18ed`.
- Commits: `9b775bf0 refactor: route Home and Browse through repositories`;
  `748789e3 refactor: route Settings Stats and Games through repositories`;
  and `2fee18ed test: cover repository-backed non-study state`.
- Implemented: Home, Browse, Settings, Stats, and Games route code now loads
  immutable `:data-api` snapshots through `HomeUseCases`, `SettingsUseCases`,
  and `StatsUseCases` instead of issuing direct `LocalStore`, raw SQL, cursor,
  or database calls. Home/Browse reads, local suspension, mnemonic writes,
  game data, new-card-sort preview data, and Stats cache refreshes have narrow
  repository operations. Settings route construction reads one portable
  snapshot per screen and one atomic device-settings snapshot; grouped note
  fields, import filters, frequency bounds, deck limits, ladder thresholds,
  workload, learning, retention, ladder, sort, theme, and personalization
  writes use typed commands, with grouped SQLite writes committed atomically.
  Reminder, auto-sync, update, and debug-log values remain host-local through
  `DeviceSettingsStore`. The workload status reads only Home and Study
  snapshots, deliberately bypassing the full Home route and repaired-tag
  consent preview. Stats presentation consumes repository DTOs, while a
  test-source adapter retains focused legacy-store projection tests without
  reintroducing production coupling.
- Validation: focused Settings, Stats, Games, repository-adapter, and Android
  instrumentation-compilation checks passed
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew
  :app:testDebugUnitTest --tests
  'dev.bee.kanjianki.MainActivitySettingsStudyBehaviorAsyncTest' --tests
  'dev.bee.kanjianki.MainActivitySettingsScreenComposeTest' --tests
  'dev.bee.kanjianki.SettingsChromeLocaleTest' --tests
  'dev.bee.kanjianki.MainActivityGamesCacheTest' --tests
  'dev.bee.kanjianki.MainActivityGamesCopyComposeTest' --tests
  'dev.bee.kanjianki.MainActivityGamesRoundComposeTest' --tests
  'dev.bee.kanjianki.data.RepositoryAdaptersTest' --tests
  'dev.bee.kanjianki.progress.*'
  :app:compileDebugAndroidTestJavaWithJavac`. The fake-repository lifecycle
  and typed-command checks then passed their focused `:application:test`,
  `:data-api:test`, and `:app:testDebugUnitTest` invocation.
  `python3 -m unittest tools.test_module_boundaries` passed all 19 tests, and
  `git diff --check` passed. The final aggregate command
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew
  ciFast ciQuality ciDesktop` completed `BUILD SUCCESSFUL` in 3m32s with 142
  actionable tasks (21 executed, 121 up-to-date). The result trees contain
  3,296 passing Gradle/JUnit tests and zero failures, errors, or skips. The
  aggregate Python tooling, asset/script, CI, and desktop-tooling surfaces
  passed their established 126, 94, 96, and 70 tests respectively. Android
  lint, instrumentation compilation, deterministic coverage verification,
  shared checks, and desktop checks all passed.
- Live gates: a copied-collection AnkiDroid run is not required. Goal 171
  changes UI-to-repository orchestration and immutable projections, but no
  provider query/write, sync normalization/publication, database schema,
  scheduler transition, backup format, or Anki scheduling state. Existing
  route/Compose tests, baseline fixture compilation, Android lint, and the
  deterministic aggregate gates passed.
- Decisions: portable database state crosses route boundaries only as
  immutable repository DTOs; host-local automation and update state stays in
  `DeviceSettingsStore`. Repository failures are normalized to typed
  transient/permanent application exceptions so the existing cancellable
  route loader can retain loading, retry, stale-result, and recreation
  behavior. Atomic Settings commands preserve the legacy
  `real_due_reviews_to_move` demotion fallback. Fresh installs preserve the
  established automatic-update status copy. Compatibility methods shared
  with Study and sync remain temporarily in the Activity base for Goals 172
  and 173 rather than broadening this migration.
- Rollback: because these commits are unmerged and unreleased, revert
  `2fee18ed`, `748789e3`, and `9b775bf0` in that order. This restores direct
  Android-host reads and test wiring without a schema migration, provider
  action, backup conversion, or user-state rollback.
- Gaps/blockers: none for Goal 171. Direct persistence calls remain only in
  the sequential Study, sync, and Android platform compatibility surfaces
  assigned to Goals 172, 173, and 177. The branch remains local and unpushed;
  the existing draft PR is intentionally not updated without explicit
  authorization.

### Goal 172 completion evidence (2026-07-27)

- Started from: `10a45ad0` on `desktop/support` in
  `/local/home/skerraut/kanji_anki`. The exact preceding implementation SHA
  that passed the final gates is `76c3a14b`.
- Commits: `c6106199 refactor: route Study persistence through
  StudyRepository`; `ed2eb13f refactor: extract the Study session state
  machine`; and `76c3a14b test: prove token revision and recovery behavior`.
  Support commit `81b90147 fix: support verification metadata capture on
  Python 3.9` replaces the Python-3.10-only `Path.write_text(newline=...)`
  call exposed by this host's standard gate. The intervening `6779d1fb docs:
  define desktop Study keybinding support` is separate Goal 203 planning work
  and is not part of Goal 172.
- Implemented: every Study queue, plan, review commit, undo, token-recovery,
  repair, mnemonic, task-timing, and refresh persistence path now crosses
  `StudyUseCases` and the narrow atomic `StudyRepository`; Study production
  code has no `LocalStore`, raw SQL, cursor, or database import. The
  authoritative session reducer, tracker, immutable route state, feedback,
  token/revision claim, recovery, runtime interaction state, and undo
  authority now live in pure-JVM `:application`; `StudySessionViewModel` is a
  thin Android lifecycle/`StateFlow` wrapper. Portable state tests moved from
  `:app` to `:application`, while Android lifecycle tests remain in `:app`.
  New tests cover durable process-death restoration, duplicate applied
  callbacks, stale scheduler revisions, undo authority, and concurrent rapid
  input. Duplicate applied callbacks cannot replace the accepted undo
  snapshot or publish a second runtime revision, and only an accepted
  `APPLIED` commit advances Study state.
- Validation: focused `:application:test` and
  `MainActivityStudyFlashcardComposeUnitTest` runs passed after the component
  test was corrected to publish immutable feedback through Compose-observed
  state. `python3 -m unittest tools.test_module_boundaries` passed all 19
  tests; the Python 3.9 metadata capture/validation checks passed all 23
  focused tests; `git diff --check` and both implementation-commit
  `git show --check` runs passed. The final committed-tree command
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew
  ciFast ciQuality ciDesktop sonarPreflight --no-daemon
  --dependency-verification=strict --console=plain` completed `BUILD
  SUCCESSFUL` in 18s with 143 actionable tasks (7 executed, 136 up-to-date).
  The result trees contain 3,305 passing module/app tests plus 27 passing
  build-logic tests, with zero failures, errors, or skips. The aggregate
  Python surfaces passed 126 dictionary/tooling, 94 Ralph-script, 96 CI, 18
  desktop-CI-script, and 70 desktop-tooling tests. Android lint,
  instrumentation compilation, deterministic coverage verification,
  shared/desktop checks, and Sonar input preflight all passed.
- Live gates: a copied-collection AnkiDroid run is not required. Goal 172
  changes Study-to-repository orchestration and in-process state ownership,
  but does not change provider queries/writes, sync normalization/publication,
  database schema, scheduler transitions, backup format, or Anki scheduling
  state. The required deterministic review transaction, recovery, lifecycle,
  Compose, instrumentation-compilation, and golden-timeline surfaces passed;
  no emulator was attached for an optional runtime replay.
- Decisions: `:application` is the sole portable owner of authoritative Study
  session progress, feedback, revisions, commit/recovery, and undo state.
  Android observes immutable snapshots and cannot bypass the state machine by
  mutating UI-local authority. Review-token release remains limited to
  retryable persistence failures and successful undo, preserving the ability
  to answer an explicitly restored pre-review token while suppressing late
  duplicate callbacks.
- Rollback: because these commits are unmerged and unreleased, revert
  `76c3a14b`, `ed2eb13f`, and `c6106199` in that order to restore Android-owned
  Study state and direct host persistence adapters. This requires no schema,
  provider, scheduler-record, or user-data rollback. Keep `81b90147` unless
  Python 3.9 support is deliberately dropped; it is independent of Study.
- Gaps/blockers: none for Goal 172. Sync publication remains the next direct
  persistence surface and is assigned to Goal 173. The branch remains local
  and unpushed; no PR, remote branch, tag, release, or external service was
  changed.

### Goal 173 completion evidence (2026-07-28)

- Started from: `7b84614b` on `desktop/support` in
  `/local/home/skerraut/kanji_anki`.
- Commits: `fcd4d42a refactor: route sync through SyncRepository` and
  `4c54883b test: prove atomic sync publication through the repository`.
- Implemented: `ManualSyncEngine` and `AutoSyncRunner` no longer reference
  `LocalStore`; every production sync publication crosses one
  `SyncUseCases.publish`/`SyncRepository` boundary. The pure-JVM
  `ManualSyncQueuePlanner` owns queue planning, while
  `ManualSyncComposition` and `AutoSyncComposition` isolate Android/store
  construction. Provider-only evidence classification, sync-start timing,
  atomic staged publication, pending-run rollback, successful-history-only
  publication, mid-sync review reconciliation, automatic retry behavior, and
  post-commit provider failure isolation remain intact. Reminder, widget,
  provider-tagging, removal-message, summary, and logging effects run only
  after committed publication succeeds.
- Validation: fake-repository tests prove exactly one publication call and no
  effects after failed publication; direct `:application` tests cover
  `ManualSyncQueuePlanner`, its companion evidence helper, and
  `SyncUseCases`. `python3 -m unittest tools.test_module_boundaries` passed all
  19 tests. `./gradlew :application:test
  :application:jacocoTestCoverageVerification --no-daemon
  --dependency-verification=strict --console=plain` completed `BUILD
  SUCCESSFUL` in 16s with 32 actionable tasks. The final deterministic command
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew
  ciFast ciQuality ciDesktop sonarPreflight --no-daemon
  --dependency-verification=strict --console=plain` completed `BUILD
  SUCCESSFUL` in 2m10s with 143 actionable tasks (11 executed, 132
  up-to-date). This includes app/unit tests, fake-provider coverage,
  instrumentation compilation, lint, deterministic coverage, desktop checks,
  Python asset/tooling tests, and Sonar input preflight. Fresh debug and
  instrumentation APK assembly also completed `BUILD SUCCESSFUL` in 51s with
  101 actionable tasks.
- Live gates: Android 15 on the API 35 `google_atd` x86_64 emulator ran real
  AnkiDroid `2.24.0` (`versionCode=422400300`); the pinned APK SHA-256 was
  `b8aaef8c8ed13e96b7bbafbc46e690490684192147ab445db8a193c4ef6989b0`.
  The four-note sanitized fixture passed the foreground sync, 62 provider
  contract tests, and four live-provider tests as `OK (67 tests)` in
  338.326s. A second private-data-free fixture contained 7,020 Kiku
  notes/cards (7,019 active, zero orphan cards, SQLite integrity `ok`) and ran
  the same command without `kanjiLiveMinimumNotes`, exercising the built-in
  7,000-note threshold. It completed `OK (67 tests)` in 1,631.187s after the
  foreground sync archived the one suspended fixture note and left 7,019
  eligible notes. The live suite also covered collection-wide inventory,
  idempotent create/render/delete of two disposable Missing Kanji notes, and
  the non-destructive card-queue write probe. Full AOT compilation was needed
  only because this host lacks KVM; no production artifact was changed.
- Decisions: sync orchestration may prepare data and effects outside the
  repository, but publication remains one atomic repository operation and no
  externally visible effect may precede its committed success. The synthetic
  default-threshold run is sufficient to close Goal 173's implementation and
  real-provider compatibility work, but it is not represented as a copied
  user-collection release gate.
- Rollback: because these commits are unmerged and unreleased, revert
  `4c54883b` and then `fcd4d42a` to restore direct sync/store orchestration.
  This requires no schema migration, provider repair, scheduler-state
  conversion, or user-data rollback.
- Gaps/blockers: none for Goal 173 implementation or sequential Goal 174 work.
  The runbook's stricter release restriction remains: no provider/sync release
  may be cut until a copied real user collection passes the default 7,000-note
  gate. The branch remains local and unpushed; no PR, tag, release, or external
  service was changed.

### Goal 174 completion evidence (2026-07-28)

- Started from: `19609ee9` on `desktop/support` in
  `/local/home/skerraut/kanji_anki`.
- Commits: `58d4b36c refactor: introduce provider-neutral collection
  contracts`, `c028889f sync: add the provider-neutral source binding policy`,
  `a4584903 data: persist opaque collection source bindings`, and
  `2d3dab3b test: add the collection gateway contract kit`.
- Implemented: the pure-JVM `:sync-api` module now owns provider-neutral
  collection, inventory, Missing Kanji, capability, progress, cancellation,
  retryability, typed failure, source-identity, and source-binding contracts
  around the canonical `RecordsSyncModels.CollectionSnapshot`. Android sync
  orchestration and `AnkiDroidGateway` consume those contracts without
  exposing Android types through the shared API. The source-binding policy
  salts and hashes provider/source identity plus the unsigned-lowest 64 note
  and card IDs, enforces the frozen small/large overlap thresholds, and
  requires explicit first-bind or backup-backed rebind decisions. SQLite
  persists the versioned opaque record atomically in settings, and the
  canonical fixture/contract kit exercises adapters without provider-specific
  types.
- Validation: the first aggregate run exposed two `UseKtx` lint findings in
  `SqliteSourceBindingStore`; both manual transaction blocks were replaced
  with the existing KTX transaction helper. The final command
  `ANDROID_HOME=/home/skerraut/android-sdk
  ANDROID_SDK_ROOT=/home/skerraut/android-sdk ./gradlew ciFast ciQuality
  ciDesktop sonarPreflight --no-daemon --dependency-verification=strict
  --console=plain` completed `BUILD SUCCESSFUL` in 2m25s with 138 actionable
  tasks (19 executed, 119 up-to-date). Result XML contains 3,330 passing
  module/app tests plus 27 passing build-logic tests, with zero failures,
  errors, or skips. The aggregate Python surfaces passed 126
  dictionary/tooling, 94 Ralph-script, 96 CI, 18 desktop-CI-script, and 70
  desktop-tooling tests. Android lint, deterministic coverage, instrumentation
  compilation, desktop checks, Sonar input preflight, `git diff --check`, and
  all four implementation-commit `git show --check` runs passed.
- Live gates: Android 15 on the API 35 `kanji_anki_api35_atd` x86_64 emulator
  ran real AnkiDroid `2.24.0` (`versionCode=422400300`); the pinned APK
  SHA-256 was
  `b8aaef8c8ed13e96b7bbafbc46e690490684192147ab445db8a193c4ef6989b0`.
  A throwaway emulator copy of `~/anki/collection.anki2` contained 12,480
  notes and 12,705 cards; the desktop source remained untouched. The
  foreground sync, 63 fake-provider/contract tests, and four real-provider
  tests completed `OK (68 tests)` in 9,743.722s with the default 7,000-note
  threshold. The live inventory reported 12,480 notes, 34 models, 3,463
  unique kanji, and zero skipped notes. Two disposable Missing Kanji notes
  were created, rendered, retried idempotently, and deleted. The
  non-destructive card update probe was rejected with
  `IllegalArgumentException` (`updatedRows=-1`) and queue remained `2`.
- Decisions: a changed provider kind or source key never auto-binds even with
  perfect stable-ID overlap. Explicit rebind requires a durable backup,
  qualifying overlap against the prior salt, and a fresh replacement salt.
  A genuinely empty Kani database may enter explicit first bind only after a
  read-only candidate supplies stable IDs. Raw profile/source names and raw
  stable IDs are transient and never persisted or printed by binding DTOs.
- Rollback: because these commits are unmerged and unreleased, revert
  `2d3dab3b`, `a4584903`, `c028889f`, and `58d4b36c` in that order. Goal 174
  adds no schema migration or provider write beyond the pre-existing live
  probes; source-binding enforcement is not enabled yet, so rollback requires
  no scheduler, mirror, or Anki collection conversion.
- Gaps/blockers: none for Goal 174. Existing Android install migration,
  mismatch recovery UI, and binding enforcement remain deliberately assigned
  to Goal 175. The branch remains local and unpushed; no PR, tag, release, or
  external service was changed.

### Goal 175 completion evidence (2026-07-29)

- Started from: `91df9d55` on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Commits: `5601c9c3 refactor: extract the AnkiDroid provider adapter`;
  `560d2f3a sync: migrate and validate existing Android source bindings`;
  `d72b172b ui: add Android source binding recovery`; `44697bc0 test: pin
  AnkiDroid compatibility behavior at the module boundary`; and `485cde35
  fix: restore AnkiDroid fixture ownership from package uid`.
- Implemented: all production provider access, fake-provider infrastructure,
  provider baselines, and real-provider probes now live in
  `:provider-ankidroid`. Database v34 migrates existing successful Android
  mirrors through read-only overlap evidence, records only the opaque source
  binding, and fails closed before publication or provider writes when
  evidence is insufficient. Home now handles explicit first bind, mismatch,
  safe new-profile creation, and backup-backed rebind without silently
  discarding scheduler state; unsafe API 26-29 recovery paths remain disabled.
  Module rules prevent shared, feature, and desktop code from importing the
  Android provider implementation.
- Validation: the final combined-tree command
  `ANDROID_HOME=/home/skerraut/android-sdk
  ANDROID_SDK_ROOT=/home/skerraut/android-sdk ./gradlew ciFast ciQuality
  ciDesktop sonarPreflight --no-daemon --console=plain` completed `BUILD
  SUCCESSFUL` in 2m34s with 332 tasks. It covered deterministic JVM/app tests,
  provider fakes, instrumentation compilation, lint, coverage, shared and
  desktop checks, Python assets/tooling, and Sonar input preflight.
  `git diff --check` also passed.
- Live gates: AnkiDroid `2.24.0` (`versionCode=422400300`) ran against a
  throwaway emulator copy of the user's collection with the default
  7,000-note threshold. The real Home flow required first-bind confirmation,
  then completed a successful foreground sync with nonempty dashboard and
  Study data as `OK (1 test)` in 4,188.415s. The standalone provider host then
  passed 50 fake-provider and four real-provider tests as `OK (54 tests)` in
  3,354.171s. The non-destructive queue probe was rejected with
  `IllegalArgumentException` (`updatedRows=-1`) and reread queue `2`.
- Decisions: authority alone never establishes trust; migration and rebind
  require the frozen overlap policy, and explicit rebind additionally requires
  a durable safety backup. Raw authority/profile values and stable IDs remain
  transient and are neither persisted nor printed. The foreground test's
  internal assertions are the durable success evidence; its teardown deletes
  the app database, so no post-run SQLite query is claimed.
- Rollback: while this work remains unreleased, first revert dependent Goals
  177 and 176, then revert `485cde35`, `44697bc0`, `d72b172b`, `560d2f3a`,
  and `5601c9c3`. If v34 is ever distributed, do not downgrade
  `user_version`; use a forward compatibility migration that preserves the
  opaque binding and recovery metadata before removing the adapter/UI.
- Gaps/blockers: none. The copied desktop collection was never modified.

### Goal 176 completion evidence (2026-07-29)

- Started from: `51a3874a` on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Commits: `76271d2f refactor: define platform service contracts`;
  `dc2fcdac refactor: extract the platform-neutral sync engine`; `20840f46
  refactor: make sync provider capability fallbacks explicit`; and `794f3eae
  test: cover shared sync publication and failure isolation`.
- Implemented: pure-JVM `:platform-contracts` now defines the clock, logging,
  file, user interaction, notification, lifecycle, scheduling, media, secret,
  snapshot, event, and update-delivery ports. `:sync-engine` owns the canonical
  read, analyze, normalize, queue, publish, and cleanup orchestration against
  `CollectionGateway` and repository contracts. Android composition supplies
  resources and effects. Source binding runs before publication and again
  before provider writes; capability-aware FSRS state and interval/lapse
  fallback are explicit pure policies; provider write-back remains isolated
  after committed publication.
- Validation: the same final 332-task aggregate
  `ciFast ciQuality ciDesktop sonarPreflight` command passed in 2m34s.
  Focused repository-boundary tests cover cancellation, retry
  classification, staged rollback, exactly-once publication, successful
  history, binding rejection, both provider-FSRS capability paths, and
  post-commit effect isolation. Module-boundary checks confirm that
  `:sync-engine` has no Android dependency.
- Live gates: the combined foreground and standalone copied-collection runs
  described under Goal 175 exercised the production Android composition over
  `:sync-engine`. They passed `OK (1 test)` and `OK (54 tests)` respectively,
  with successful sync output, dashboard/Study queue publication, provider
  reads, Missing Kanji reconciliation, and the rejected queue-write probe.
- Decisions: publication is one repository transaction; no reminder, widget,
  logging, provider-tagging, or other externally visible post-commit effect
  may run before it succeeds. Provider write failure cannot roll back a
  committed local sync, but binding rejection prevents both publication and
  writes.
- Rollback: after reverting Goal 177, revert `794f3eae`, `20840f46`,
  `dc2fcdac`, and `76271d2f` in that order. This restores Android-host
  orchestration without changing schema, provider data, or scheduler records;
  Goal 175's binding enforcement must remain intact.
- Gaps/blockers: none.

### Goal 177 completion evidence (2026-07-29)

- Started from: `794f3eae` on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Commits: `23519307 refactor: extract core Android platform adapters`;
  `8abbfbf4 refactor: extract Android automation infrastructure`; and
  `9cd7fb70 refactor: complete Android platform adapter extraction`.
- Implemented: `:platform-android` now owns the byte-compatible
  `AndroidDeviceSettingsStore`, notification delivery, ML Kit handwriting,
  app directories, SAF/file access, clipboard/share, external navigation,
  reading media, package installation, and process lifecycle adapters.
  `:automation-android` owns WorkManager persistence, receiver async work, and
  alarm scheduling. Existing app composition routes backup/restore, debug and
  CSV sharing, clipboard handoff, reading exposure, updater installation,
  reminders, notifications, retries, backup, and FSRS fitting through those
  boundaries. Intent actions/extras, MIME types, notification channels,
  `PendingIntent` flags, receiver behavior, and restore-before-open ordering
  remain unchanged; widget ownership intentionally stays in `:app`.
- Validation: the exact aggregate command
  `ANDROID_HOME=/home/skerraut/android-sdk
  ANDROID_SDK_ROOT=/home/skerraut/android-sdk ./gradlew ciFast ciQuality
  ciDesktop sonarPreflight --no-daemon --console=plain` completed `BUILD
  SUCCESSFUL` in 2m34s with 332 tasks. Adapter tests cover SAF result
  restoration, opaque URI handling, read-only shares, media containment and
  limits, lifecycle transitions, installer fsync/commit/abandon ordering,
  notification compatibility, WorkManager persistence, alarms, and missing
  Android services. `:application` and `:sync-engine` run under fakes with no
  Android imports. `git diff --check` passed.
- Live gates: the final Goal 177 tree passed the same real AnkiDroid
  `2.24.0` foreground run as `OK (1 test)` in 4,188.415s and the strict
  copied-collection provider gate as `OK (54 tests)` in 3,354.171s, using the
  default 7,000-note threshold. This validates both the user-visible sync path
  and provider compatibility after all adapter ownership changes.
- Decisions: portable contracts expose opaque references and stable host
  behavior, never Android `Uri`, `Intent`, `Context`, or WorkManager types.
  Android activity-result launcher registration order remains positional and
  unchanged. Process lifecycle registration is owned and closed by the
  process container before its executors and database.
- Rollback: revert `9cd7fb70`, `8abbfbf4`, and `23519307` in that order.
  These commits move effect ownership without changing persisted formats,
  database schema, provider state, or scheduler records.
- Gaps/blockers: none. Draft PR
  [#592](https://github.com/bee-san/kanji_anki/pull/592) remains intentionally
  unmerged.

### Goal 178 completion evidence (2026-07-29)

- Started from: `bde3e3b0` on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Commit: `e1dc8e98 test: freeze executable v34 schema corpus`.
- Implemented: `goal178` now contains an executable canonical v34 SQL schema,
  a semantic manifest with persisted format constants and provenance hashes,
  a complete migration dependency inventory, and deterministic compressed
  v1/v30/v31/v32/v33 SQLite databases. Each historical database was built
  from the provenance-pinned old DDL and populated with synthetic
  representative rows while still at that version; none is a current database
  with only a lowered `user_version`. Schema fingerprints now separate
  schema-only and seed/contract digests without changing the existing
  fingerprint format. Robolectric opens each binary before migration, compares
  it with the pinned source DDL, runs the real `LocalStore` upgrade, checks
  schema/row/default/source-binding/compatibility state, and exercises the real
  downgrade callback. Python's independent SQLite path checks the manifest,
  old shapes, production SQL features, and byte-reproducible regeneration.
- Validation: `python3 -m unittest tools.test_schema_corpus` passed six tests,
  and the complete tools discovery passed 134 tests. The focused
  `SchemaBaselineTest` plus `SchemaMigrationCorpusTest` run passed all seven
  tests. The exact aggregate command
  `ANDROID_HOME=/home/skerraut/android-sdk
  ANDROID_SDK_ROOT=/home/skerraut/android-sdk ./gradlew ciFast ciQuality
  ciDesktop sonarPreflight --no-daemon --console=plain` completed `BUILD
  SUCCESSFUL` in 2m14s with 323 tasks (15 executed, 308 up-to-date).
  `git diff --check` and `git show --check e1dc8e98` passed. The independent
  host oracle was Python 3.9.25 with SQLite 3.40.0.
- Live gates: not required for this goal. It adds tests, generated fixtures,
  and test-only fingerprint tooling; production schema creation, migrations,
  repositories, provider access, and sync behavior are unchanged. The final
  runtime tree is therefore the same one that passed the Goal 175-177 live
  AnkiDroid gates above.
- Decisions: v34 remains the canonical version; its fresh schema shape is v33
  because v34 adds only a conditional migration settings row. Android/
  Robolectric executes the current production migrations, while host Python
  SQLite is an independent compatibility oracle. Goal 178 does not select the
  desktop driver, introduce a shared SQL abstraction, squash history, or
  rewrite any production migration; those decisions remain in Goal 179.
- Rollback: revert `e1dc8e98`. This removes only tests and frozen corpus
  resources, with no database, scheduler, provider, or user-data conversion.
- Gaps/blockers: none. Goal 179 is next. Draft PR
  [#592](https://github.com/bee-san/kanji_anki/pull/592) remains intentionally
  unmerged.

### Goal 179 completion evidence (2026-07-30)

- Started from: `e1dc8e98` (Goal 178) on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Implemented: the pure-JVM `:data-sql` module with the driver/transaction
  contract (`SqlContracts.kt`: `SqlDriver`/`SqlConnection`/`SqlSession`/
  `SqlStatement`/`SqlRows`/`SqlRow`/`SqlPragmaAccess`/`SqlDatabase` with
  one-based binds, zero-based columns, explicit null/text/integer/real/blob
  binding, `IMMEDIATE` writes, `DEFERRED` read snapshots, and
  `ROLLBACK TO`/`RELEASE` savepoints). `DedicatedWriterSqlDatabase` owns one
  physical writer connection on one dispatcher/thread with serialized writes,
  busy timeout, WAL, cancellation, and writer-thread-loss handling; reads use an
  independent snapshot connection. `SchemaManager(MigrationContext(clock,
  defaults))` runs the whole upgrade and the final `user_version` bump in one
  transaction and records `downgraded_from_version`. AndroidX
  `sqlite-bundled` is the qualified desktop driver, exercised through
  `BundledTestSqlDriver`.
- Validation: `SqlDriverContractSuite` runs through the thin
  `BundledSqlDriverContractTest`; `SchemaManagerBundledTest` (fresh create →
  `SchemaTransition(0, 34, CREATED)` seeding `stats_source_version = 1`, plus
  v1/v30/v31/v32/v33 historical upgrades), `SchemaManagerFaultInjectionTest`,
  `DedicatedWriterSqlDatabaseTest`, `BundledTestSqlDriverTest`, and
  `SqlContractsTest` all pass under `:data-sql:check` with the module's 100%
  class-coverage gate.
- Live gates: not required. `:data-sql` is not yet on any production runtime
  path; Android stays on `LocalStore`.
- Decisions: bundled SQLite passes the driver matrix, so no ADR fallback driver
  was needed. Transaction callbacks stay non-suspending by contract.
- Rollback: the module is additive and unreferenced by production composition;
  removing it changes no runtime behavior.
- Gaps/blockers: none. Goal 180 followed.

### Goal 180 completion evidence (2026-07-30)

- Started from: `8d8c37ce` on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Commits: `5c3b8465 data: port settings and Home repositories to shared SQL`;
  `512cd7c4 test: add cross-driver read and settings conformance`.
- Implemented: ported the portable typed settings surface
  (`SqlSettingsRepository` covering every `SettingsSaveCommand` family plus
  `commitFsrsFit`, with defaults, malformed-value fail-open, on-write clamping,
  and byte-parity `putDouble`/`"%.4f"`), and the Home/browse/detail/examples/
  local-suspension read projections (`SqlHomeRepository`, `SqlHomeData`,
  `SqlStudyItemMapper`) with the legacy dashboard sort
  (`weakness_score DESC, suspended_example_count DESC, kanji ASC`), inventory
  `search_text` LIKE with `ESCAPE '\'`, the study-queue scopes, and
  `SqlProjectionInvalidation` for new-card-sort preview versioning. Added
  `StringListJsonCodec` in `:core` for manual-source decoding. Production
  Android stays on the `LocalStore` facade.
- Validation: a shared `RepositoryConformanceSuite` in `:data-api` testFixtures,
  parameterized by a `RepositoryConformanceHost`, is driven from one fixture by
  two thin consumers — `SqlRepositoryConformanceTest` (bundled SQLite via
  `DedicatedWriterSqlDatabase` + `SchemaManager`) and
  `LocalStoreRepositoryConformanceTest` (Robolectric `@Config(sdk=[35])` over
  `LocalStore`). Both pass. `SqlHomeReadPathCoverageTest` covers the study-item/
  streak/manual-source read paths the fixture does not seed. Module gates:
  `:core:check`, `:data-api:check`, and `:data-sql:check` all `BUILD SUCCESSFUL`
  with 100% class coverage; `:app:testDebugUnitTest` and
  `:app:compileDebugAndroidTestJavaWithJavac` `BUILD SUCCESSFUL`. Commands run
  with `ANDROID_HOME=/home/skerraut/android-sdk
  ANDROID_SDK_ROOT=/home/skerraut/android-sdk`.
- Live gates: not required. No production runtime composition switched;
  `:data-sql` is validated only through tests, and Android production still uses
  `LocalStore`. The live AnkiDroid provider/sync surface is unchanged.
- Decisions: the conformance suite pins concrete expected literals rather than
  comparing the two implementations to each other, so a shared misunderstanding
  cannot pass. The new-card-sort preview-version check asserts a monotonic
  non-decrease on reads (the legacy read path can lazily bump a process-static
  cache epoch) and a strict increase only after a suspension write. Ladder
  round trips compare normalized-to-normalized. `ResetFsrsPersonalization`
  leaves the enabled flag untouched on both hosts; `FsrsPersonalizationEnabled`
  default is on (`ENABLED_SETTING_DEFAULT = 1`).
- Rollback: revert `512cd7c4` then `5c3b8465`; both are additive (new
  `:data-sql` repositories, testFixtures suite, and tests) and unreferenced by
  production composition.
- Gaps/blockers: none. Goal 181 (port Study persistence to `:data-sql`) is next.

### Goal 181 completion evidence (2026-07-30)

- Started from: `51e14085` on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Commits: `6b5b1fbe data: port Study queue reads and token-first review writes`;
  `d6dabc93 test: conform and fault-inject the Study transaction across drivers`.
- Implemented: driver-neutral Study persistence in `:data-sql`. `SqlStudyData`
  provides the queue read (reusing `SqlHomeData` for dashboard/inventory/
  similar/streak and deriving settings through the shared
  `SqlSettingsRepository.readSnapshot`), the choice-data read (kanji/reading
  usage and pool, reading-kanji candidates), and the token/recovery reads.
  `SqlStudyRepository` owns queue reconciliation (`MidSyncReviewMergePolicy` +
  `DurableStudyItemRetentionPolicy` + lineage/versioning + a keyed upsert/delete
  diff), item upserts guarded by revision, and the token-first, revision-CAS
  review commit: `INSERT OR IGNORE` the review row (a token conflict is the only
  `DUPLICATE`; any other ignored insert throws a constraint error rather than
  advancing state), a `scheduler_revision`-predicated `UPDATE` (`STALE` when it
  matches zero rows), the similar-choice side effect, the timeline event, task
  timing, choice log, and stats dirtiness — all in one `database.write`. Undo
  deletes the review row, re-CAS-updates the item, and removes the timeline
  event. `SqlStudyItemMapper` gained a positional upsert binder over the
  canonical column order. Android production stays on `LocalStore`.
- Validation: `StudyRepositoryConformanceSuite` (in `:data-api` testFixtures)
  runs from one fixture against both the legacy Android `SqliteStudyRepository`
  (Robolectric `@Config(sdk=[35])`) and the shared `SqlStudyRepository` (bundled
  SQLite), pinning queue reads, the APPLIED/DUPLICATE/STALE commit dispositions,
  undo, task-timing idempotency, token/recovery status, choice-data reads, and
  legacy-repair no-ops. `SqlStudyReviewFaultInjectionTest` injects a failure on
  the `study_items` CAS update after the `review_log` insert and asserts both
  roll back (token not consumed, revision unchanged). Gates:
  `:data-sql:check`, `:data-api:check`, and the full `:app:testDebugUnitTest`
  all `BUILD SUCCESSFUL`, with `:data-sql`'s 100% class-coverage gate met.
  Commands run with `ANDROID_HOME=/home/skerraut/android-sdk
  ANDROID_SDK_ROOT=/home/skerraut/android-sdk`.
- Live gates: not required. No production composition switched; `:data-sql` is
  validated only through tests and Android production still uses `LocalStore`.
- Decisions: the commit boundary matches the legacy `commitReview` exactly —
  token-first insert, revision CAS, side effects, then stats dirtiness — so the
  two implementations are behaviourally identical through the typed surface. The
  suite pins concrete dispositions rather than comparing the implementations to
  each other. The similar-choice/writing-repair state remains
  compatibility-only: no path enqueues a new `similar_kanji_repair_queue` row.
- Rollback: revert `d6dabc93` then `6b5b1fbe`; both are additive (new
  `:data-sql` Study repository plus testFixtures suite and tests) and
  unreferenced by production composition.
- Gaps/blockers: none. Goal 182 (port sync publication/history to `:data-sql`)
  is next.

### Goal 182 completion evidence (2026-07-30)

- Started from: `9ff5a90b` on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Commits: `7a81ec15 data: port sync publication, history, and write-back to
  shared SQL`; `7fa78605 test: prove sync publication parity and rollback across
  drivers`.
- Implemented: driver-neutral `SyncRepository` in `:data-sql`. `SqlSyncPublisher`
  stages the full provider mirror (source notes/cards + suspended archive),
  suspended imports, import rule/decision audit, historical card/note/kanji
  snapshots, dashboard rows + examples, `KanjiInventoryBuilder` inventory,
  similar-pair and similar-choice rebuilds, and the reading-usage/pool rebuild,
  then reconciles and finalizes the pending study queue — all inside one
  transaction. `SqlSyncRepository` orchestrates `publish` (invoking the caller's
  `SyncQueuePlanner` on a staged snapshot exactly once), `loadStoredState`,
  `recordFailure`, `updateRemovalMessage`, and the post-commit repaired
  write-back/handoff. `SqlRepairEvidenceReader` ports the repair-evidence input
  pipeline shared by the queue-planning snapshot and the write-back proposal;
  `SqlRepairedWriteBackData` ports the proposal/preview/record. Timeline inserts
  use `INSERT OR IGNORE` on the `dedupe_key` unique index for parity with the
  legacy `CONFLICT_IGNORE`. Android production stays on `LocalStore`.
- Validation: `SyncRepositoryConformanceSuite` (in `:data-api` testFixtures) runs
  from one fixture against both the legacy Android `SqliteSyncRepository`
  (Robolectric) and the shared `SqlSyncRepository` (bundled SQLite), pinning
  empty stored state, an atomic seeded publish that finalizes exactly one
  successful run, single-planner-invocation, failure/removal history, and the
  empty-store write-back/handoff surface. `SqlSyncPublicationFaultInjectionTest`
  proves a planner failure rolls back the staged mirror and pending history
  (no mirror, no items, no successful run), and that a repeated rich publish
  (suspended import + similar index + two similar-pair endpoints) reloads
  suspended imports and similar-choice state. Gates: `:data-sql:check`,
  `:data-api:check`, `:core:check`, and the full `:app:testDebugUnitTest` all
  `BUILD SUCCESSFUL`, with `:data-sql`'s 100% class-coverage gate met. Commands
  run with `ANDROID_HOME=/home/skerraut/android-sdk
  ANDROID_SDK_ROOT=/home/skerraut/android-sdk`.
- Live gates: deferred to Goal 184, consistent with Goals 180–181. The strict
  live AnkiDroid gate validates the *runtime* sync/provider path; that path is
  unchanged here because no production composition switched — `:data-sql`'s
  `SqlSyncRepository` is exercised only through tests and Android production
  still publishes through `LocalStore`. The live gate is required at Goal 184
  when composition flips to the shared SQL sync path.
- Decisions: the publish transaction mirrors the legacy `saveSuccessfulSync` →
  `commitPendingSyncStudyItems` chain step-for-step (pending run, mirror,
  history, rebuilds, then queue commit that flips pending→success), so a
  queue-build failure cannot expose a new mirror on stale scheduler state. The
  suite pins concrete outcomes rather than comparing implementations to each
  other. Similar-choice/repair-queue state stays compatibility-only.
- Rollback: revert `7fa78605` then `7a81ec15`; both are additive (new
  `:data-sql` sync repository plus testFixtures suite and tests) and
  unreferenced by production composition.
- Gaps/blockers: the Goal 184 composition switch must run the strict live
  AnkiDroid gate before shipping the shared sync path. Goal 183 (port remaining
  persistence and reference assets) is next.

### Goal 183 partial completion evidence (2026-07-31)

- Started from: `92343983` on `desktop/integration` in
  `/local/home/skerraut/work/kani-desktop-integration`.
- Commit: `b5131e34 assets: add cross-platform reference asset manifest, loader,
  and cache upgrades`.
- Implemented (reference-assets half, commits 2–3): the pure-JVM
  `:reference-assets` module. `ReferenceAssetManifest.bundled()` is a
  code-defined manifest with one entry per kind (dictionary database, Jiten
  frequency ranks, KanjiVG stroke guides, study font), each carrying an expected
  SHA-256, format/cache version, extraction target, and license/attribution
  record. `ReferenceAssetVerifier` streams SHA-256 without buffering the whole
  asset; `ReferenceAssetCachePolicy` decides EXTRACT/UPGRADE/REUSE from the
  cached format version and recorded hash; `ReferenceAssetLoader` orchestrates
  verify-then-install behind the platform-neutral `PackagedAssetSource` /
  `ReferenceAssetCache` seams so Android (`AssetManager`) and the desktop
  installed image share one contract. Asset hashes are the all-zero placeholder
  until the licensed binaries are sourced (user decision): a placeholder accepts
  any non-empty content while a real 64-hex hash is enforced exactly.
- Validation: `:reference-assets:check` `BUILD SUCCESSFUL` with the module's
  100% class-coverage gate. Tests cover corrupt/missing/old-format-version cache
  recovery, a hash mismatch rejecting a real asset, an isolated missing packaged
  file, Unicode file names, concurrent loads across 8 threads, ~8 MiB streamed
  hashing, and mid-extraction cancellation leaving no partial cache entry, plus
  the known SHA-256("abc") vector.
- Decisions (from the user, 2026-07-31): (1) **stats port deferred** — all three
  `StatsRepository` methods route through the format-11 `StatsCacheCodec`, which
  depends on Android `org.json`; `:data-sql` is deliberately dependency-free, so
  porting stats needs a JSON-dependency decision and is postponed. (2) **build
  reference-asset infra with placeholder assets now**; real licensed binaries
  and their attribution records will be supplied later.
- Live gates: not required. `:reference-assets` is additive and not yet wired
  into either host's production asset load.
- Rollback: revert `b5131e34`; the module is additive and unreferenced.
- Gaps/blockers (Goal 183 NOT fully complete):
  1. **Stats/analytics repository port** (commit 1: stats cache format 11,
     progress analytics inputs) is deferred pending the `:data-sql` JSON
     decision. Until then, `StatsRepository` remains implemented only in the
     `LocalStore` chain, so Goal 183's "Done when" (no repository operation
     remains `LocalStore`-only) is unmet.
  2. **Real licensed binaries** must replace the placeholder hashes, and the
     Android/desktop hosts must be wired to load through `:reference-assets`.
  3. The remaining Kani-owned/compatibility persistence (source inventory,
     similar/reading pools, mnemonic notes, manual sources, Missing Kanji scans
     and export receipts, archive/repaired coordination) still needs its shared
     SQL port + conformance. Goal 184 depends on Goal 183 being complete.

### Goal 183 stats-port completion evidence (2026-07-31)

- Started from: `cd827a41` on `desktop/integration`. Supersedes blocker (1)
  above: the user chose to proceed rather than keep the stats port deferred.
- Commits: `8555fe2b data: port analytics stats cache and inventory to shared
  SQL`; `4e13aa04 test: complete analytics repository conformance across
  drivers`.
- Implemented: a dependency-free `KaniJson` facility in `:core` (adds `Double`
  support and an 8 MiB bound over the private integer-only `CompactJson`), so
  `:data-sql` serializes the format-11 stats cache without Android `org.json`.
  `SqlStatsData` computes every metric the app's `StudyStatsQueries` did —
  outcome (weak-improved / mature-support / ladder health / adaptive health),
  study impact, streak, task timing, review-day and task-type-day summaries,
  cumulative kanji, wrong-pick counts + confusion meanings, repair evidence, and
  the ladder-completion forecast — by running read-only SQL through the same
  `:core` policies and mapping straight to the data-api `StatsSnapshot`.
  `SqlKanjiImpactReport` ports the before/after impact analysis (including the
  same-card cross-sync join). `SqlStatsCodec` round-trips the snapshot via
  `KaniJson`; `SqlStatsRepository` reads/writes `stats_screen_cache` with the
  legacy freshness rule (source version, cache format 11, time zone, local day).
- Validation: `StatsRepositoryConformanceSuite` runs from one fixture against
  both the legacy `SqliteStatsRepository` (Robolectric) and `SqlStatsRepository`
  (bundled SQLite): empty-store refresh+cache, review-driven metrics, and stale-
  cache invalidation across a source-version bump. `SqlStatsImpactReportTest`
  drives the impact report's two-sync same-card path and the single-sync empty
  branch. `KaniJsonTest` pins the JSON round trip. Gates: `:core:check`,
  `:data-api:check`, `:data-sql:check` (100% class coverage), and the full
  `:app:testDebugUnitTest` (`BUILD SUCCESSFUL in 2m 23s`) all pass.
- Live gates: not required; no production composition switched.
- Decisions: chose KaniJson-on-`:core` over adding a JSON dependency to
  `:data-sql`, keeping the module dependency-free. The cache JSON layout is
  self-consistent within `:data-sql` (not byte-matched to the app's `org.json`
  cache); the conformance contract is the repository return value, which both
  hosts match.
- Rollback: revert `4e13aa04` then `8555fe2b`; both additive and unreferenced by
  production composition.
- Gaps/blockers: Goal 183 blockers (2) real licensed binaries + host wiring for
  `:reference-assets`, and (3) the remaining Kani-owned/compatibility
  persistence port, are still open. `StatsRepository` is now available in
  `:data-sql`, closing the analytics part of blocker (1).

### Goal 183 Missing-Kanji-port completion evidence (2026-07-31)

- Started from: `2ea9b147` on `desktop/integration`. Closes blocker (3): the
  last Kani-owned persistence surface without a shared-SQL implementation.
- Commits: `5a8036c0 data: port Missing Kanji persistence to shared SQL`;
  `8e97803e test: add cross-driver Missing Kanji repository conformance`.
- Implemented: promoted the Missing Kanji DTOs (`MissingKanjiScanRecord`/
  `MissingKanjiScanStatus`, `MissingKanjiInventoryState`,
  `StoredAnkiKanjiInventory`, `MissingKanjiPreferences`, `ManualKanjiSource`,
  `ManualKanjiSourceWriteResult`/`RemovalResult`, `MissingKanjiExportReceipt`)
  from the app store into `:core` so one type set backs the new
  `MissingKanjiRepository` contract in `:data-api` and both implementations.
  `SqlMissingKanjiRepository` ports the aggregate-only surface — atomic scan
  publication + inventory swap with scan-history pruning, frequency-range
  preferences, manual dictionary source add/remove/deactivate through
  `ManualKanjiAdmissionPolicy`, and export receipts — using the dependency-free
  `StringListJsonCodec`. `SqliteMissingKanjiRepository` delegates to the
  existing `LocalStore` `MissingKanjiStore`; the `MainActivityMissingKanji` UI
  is unchanged (production stays on `LocalStore` until Goal 184).
- Validation: `MissingKanjiRepositoryConformanceSuite` runs from one fixture
  against both hosts (empty state, inventory publish + failed-scan staleness,
  preferences round trip, manual-source lifecycle, export receipts). Gates:
  `:core:check`, `:data-api:check`, `:data-sql:check` (100% class coverage), and
  the full `:app:testDebugUnitTest` (`BUILD SUCCESSFUL in 2m 10s`, existing
  `MissingKanjiStoreTest`/`LocalStoreDowngradeTest` still green) all pass.
- Live gates: not required; no production composition switched.
- Decisions: kept the UI on `MissingKanjiStore` and had `:app`'s repository
  delegate to it, matching the 180–182 pattern of parallel `:data-sql`
  implementation without switching production before Goal 184.
- Rollback: revert `8e97803e` then `5a8036c0` (DTO promotion is additive; the
  app store keeps working via the `:core` types).
- Gaps/blockers: **all five original repository contracts plus Missing Kanji are
  now implemented in `:data-sql` with cross-driver conformance**, so no
  repository operation remains `LocalStore`-only. Goal 183's ONE remaining item
  is the real licensed `:reference-assets` binaries + host wiring (blocker 2),
  which needs the user to supply assets/licensing. Goal 184 (switch Android
  production to shared SQL) can then proceed, gated by the strict live AnkiDroid
  emulator run.

### Goal 185 partial completion evidence (2026-07-31)

- Started from: `07edd72a` on `desktop/integration`. Extracted the backup/
  restore core ahead of the Goal 184 production switch (the shared primitives
  are needed by both the Android switch and the Goal 186 desktop host).
- Commits: `deb161f7 backup: extract portable backup core (marker state machine
  + space budget)`; `29362597 backup: exclude legacy device settings from
  portable restores`; plus `SqlSourceBindingStore` (`b1e4f84c`) as a Goal 184
  prerequisite.
- Implemented (commits 1–2 of Goal 185): the pure-JVM `:backup-core` module.
  `RestoreMarkerCodec` holds the restore-marker content codec and the
  MISSING/LEGACY/SAFETY_READY/INVALID classification state machine (I/O
  injected); `BackupRestoreStager.markerState` now delegates to it, behaviour
  unchanged. `BackupSpaceBudget` holds the 512 MiB decompressed cap and 64 MiB
  free-space reserve arithmetic plus the post-write storage-exhaustion recheck.
  `PortableBackupSanitizer` holds the device-local exclusion boundary, backed by
  the new single-source-of-truth
  `DeviceSettingKeys.portableExclusionStorageNames` allowlist (reminders,
  auto-sync, auto-update, debug, gesture, and host-only desktop keys). The gzip
  stream, tiered retention, and validation facts→result policy were already pure
  in `:core` (`DatabaseBackupPolicy`, `BackupRestorePolicy`).
- Validation: `:backup-core:check` and `:platform-contracts:check` pass at 100%
  class coverage; the existing `:app` backup suite
  (`BackupRestoreStagerTest`/`BackupRestoreValidatorTest`/…) and
  `LegacyDeviceSettingsMigrationTest` still pass, confirming the marker
  delegation and allowlist addition are behaviour-neutral.
- Live gates: not required; additive extraction, no runtime behaviour change.
- Rollback: revert `29362597` then `deb161f7`; the app retains its own marker
  logic path only if the delegation is reverted (both are behaviour-identical).
- Gaps/blockers: Goal 185 commit 3 is now delivered — see the continuation
  entry immediately below. The one remaining Goal 185 item is the *Android-side*
  cross-platform restore instrumentation on API 30 + a current API, which needs
  the live emulator gate.

### Goal 185 commit 3 completion evidence (2026-07-31)

- Started from: `0e8c8f9b` on `desktop/support` (after the `:data-desktop`
  host matured). Delivers the cross-platform backup format compatibility proof.
- Commits: `09ab35b1 backup: add portable backup metadata and cross-platform
  compatibility proof` (builds on `0e8c8f9b backup: add cross-platform restore
  planner and desktop device-local reset finalizer`).
- Implemented:
  - `PortableBackupMetadata` (`:backup-core`) — the backward-compatible
    portable metadata carried in the backup's own `settings` table (origin
    platform, format version, schema version) under reserved keys, with
    legacy-backup → UNKNOWN fall-open and a mapping to
    `CrossPlatformRestorePlanner.Host`.
  - `DesktopPortableBackupStamper` (`:data-desktop`) — stamps `origin=desktop`
    + version rows before a desktop export and reads them back on restore.
  - `CrossPlatformBackupCompatibilityTest` — end-to-end on real bundled SQLite:
    a stamped desktop backup restores as a same-host clean restore (no reset,
    no revalidation, portable setting preserved), and a legacy unstamped backup
    carrying a foreign device-local key takes the UNKNOWN-origin path (key
    reset, provider revalidation required).
- Validation: `:backup-core:check` and `:data-desktop:check` pass at 100% class
  coverage. The desktop↔Android *format* compatibility is proven through the
  shared pure `PortableBackupMetadata`/`CrossPlatformRestorePlanner` (both
  covered in `:backup-core`) plus the desktop round-trip above.
- Live gates: not required for the shared/desktop surface. The Android-side
  instrumented cross-platform restore (API 30 + current API) remains, gated on
  the live emulator run.
- Rollback: revert `09ab35b1`; additive, not wired into `:desktop-app`.
- Gaps/blockers: Android instrumented cross-platform restore only.

### Goal 186 partial completion evidence (2026-07-31)

- Started from: `7e78da93` on `desktop/integration`. Establishes the desktop
  data host foundation.
- Commits: `ad4fb71a data: add desktop storage layout and profile registry`;
  `0d92a4b4 data: add desktop bundled SQLite driver and database factory`.
- Implemented: the `:data-desktop` module. `DesktopStorageLayout` resolves the
  per-OS data/config/cache directories (Windows `LOCALAPPDATA`/`APPDATA`, macOS
  `Library/{Application Support,Preferences,Caches}`, Linux XDG with `~/.local`
  fallbacks) and the profile UUID directory / database / lock / backups paths,
  with traversal-safe profile-id validation. `DesktopProfileRegistry` is the
  opaque, selection/display-only registry that lives outside the portable
  database (add/select/remove + `KaniJson` codec with malformed fall-open and
  invalid-id rejection). `BundledSqlDriver` is the production desktop SQLite
  driver (the Goal 179-qualified `androidx sqlite-bundled`, adapted from the
  proven `:data-sql` bundled test driver with identical failure translation).
  `DesktopDatabaseFactory.open` composes it with `DedicatedWriterSqlDatabase`
  (WAL/busy-timeout/serialized writes) and `SchemaManager`.
- Validation: `:data-desktop:check` passes at 100% class coverage. An
  end-to-end test opens a fresh profile DB on the bundled driver, asserts the
  `CREATED` v34 transition, drives a real `SqlSettingsRepository` round trip,
  and confirms reopening is `UNCHANGED`.
- Live gates: not required; unit-tested on the bundled driver.
- Rollback: revert `0d92a4b4` then `ad4fb71a`; `:data-desktop` is additive and
  not yet wired into `:desktop-app`.
- Gaps/blockers (at that checkpoint): profile-lock acquisition, permission
  hardening, refusal preflight, picker staging, cross-platform restore state.
  These are addressed by the follow-on commits below.

### Goal 186 continuation evidence (2026-07-31)

- Started from: `0d92a4b4` on `desktop/support`. Completes the desktop profile
  storage, backup, and restore surface begun above.
- Commits (in order):
  - `05450be5 data: add desktop profile safety preflight policy`
  - `c5438b85 data: add exclusive desktop profile lock`
  - `fe8e9adc data: harden desktop profile directory and file permissions`
  - `60263186 data: add desktop profile open orchestrator (preflight, provision, lock, open)`
  - `6decd3d3 data: add desktop WAL-safe backup snapshotter (VACUUM INTO + gzip)`
  - `78b47e9e data: add desktop tiered backup manager with retention pruning`
  - `e21fc5ea data: add desktop backup restore validator (bounded gzip + read-only checks)`
  - `7ffa2a5f data: add desktop atomic staged-restore applier (safety backup, marker, atomic replace)`
  - `0e8c8f9b backup: add cross-platform restore planner and desktop device-local reset finalizer`
- Implemented (all in `:data-desktop`, plus one pure planner in `:backup-core`):
  - `DesktopProfilePreflightPolicy` — pure allow/refuse over probed filesystem
    facts (non-directory, symlink, world-writable, network-share, no atomic
    move, no exclusive lock), with stable refusal messages.
  - `DesktopProfileLock` — real non-blocking exclusive `FileLock` on the
    profile `.lock`; a second holder is rejected, not queued.
  - `DesktopProfileProvisioner` — creates the private profile tree owner-only
    (`0700` dirs / `0600` files) on POSIX, re-tightening a loosened directory;
    a documented no-op on non-POSIX (Windows relies on the per-user data root).
  - `DesktopProfileOpener` — the single entry point: probe → preflight →
    provision → lock → open, with a `RealFilesystemProbe` (NOFOLLOW stat,
    POSIX world-writable check, network-fs-type detection) and refusal/
    lock-unavailable/IO-failure outcomes.
  - `DesktopBackupSnapshotter` — WAL-safe `VACUUM INTO` on a dedicated
    connection, gzip, atomic publish; every scratch file cleaned on failure.
  - `DesktopBackupManager` — timestamped backups under `backups/` with the
    shared `DatabaseBackupPolicy` 7-daily/4-weekly retention pruning.
  - `DesktopBackupRestoreValidator` — bounded streamed gzip decompression
    (reuses `:backup-core` `BackupSpaceBudget` cap/reserve), SQLite-magic
    check, read-only `PRAGMA user_version`/`quick_check`/settings-table facts
    fed to `:core` `BackupRestorePolicy`.
  - `DesktopStagedRestoreApplier` — `stage`/`apply` mirroring the Android
    sequence on `java.nio`: pre-restore safety backup → durable
    `RestoreMarkerCodec` SAFETY_READY marker → atomic replace → sidecar
    cleanup → marker delete, with marker-only resume and marker-bearing
    `BLOCK_STARTUP`.
  - `CrossPlatformRestorePlanner` (`:backup-core`) + `DesktopCrossPlatformRestoreFinalizer`
    — plan the device-local key reset (`PortableBackupSanitizer`) and provider
    revalidation requirement for a restore, then delete the device-local
    `settings` rows from the restored database so the destination falls back to
    its own defaults.
- Validation: `:data-desktop:check` and `:backup-core:check` pass at 100% class
  coverage. Backup/restore is proven end-to-end on real bundled SQLite: a
  populated v34 database snapshots to gzip and round-trips back to the same
  setting value; `stage`→`apply` swaps the live database for a foreign one
  while preserving a pre-restore safety backup; the finalizer drops a foreign
  host's device-local rows and keeps portable ones.
- Live gates: not required; the whole surface is unit-tested on the bundled
  driver and `java.nio`.
- Rollback: revert the nine commits above (newest first); all are additive to
  `:data-desktop`/`:backup-core` and not yet wired into `:desktop-app`.
- Gaps/blockers: what remains for Goal 186 is product wiring, not core logic —
  calling `DesktopProfileOpener`/`DesktopStagedRestoreApplier` from the desktop
  app startup, exposing export/import through the desktop file picker, and
  Windows ACL hardening (the POSIX path is covered; Windows currently relies on
  the per-user data root). Goal 185 commit 3 (cross-platform compatibility
  fixtures) can now build on this host. Goals 187+ (desktop UI parity, provider
  bridge, packaging, signing, release) remain.

### Goal 187 partial completion evidence (2026-07-31)

- Started from: `92c50036` on `desktop/support`. Adds the AnkiConnect transport
  security core and protocol layer (the safe-availability determination path).
- Commits (in order):
  - `86c61571 provider: add AnkiConnect loopback endpoint validation + outbound action allowlist`
  - `5143b80a provider: add AnkiConnect JSON codec and v6 request/response envelopes`
  - `d2b54507 provider: add bounded loopback AnkiConnect HTTP transport`
  - `7aaba4e5 provider: add AnkiConnect capability handshake`
- Implemented: the new pure-JVM `:provider-ankiconnect` module (deps
  `{platform-contracts, sync-api}`, 100% class coverage).
  - `AnkiConnectEndpoint` — fail-closed literal URL validation (http(s) +
    loopback host only; rejects userinfo/path/query/fragment/missing-port) plus
    a post-resolution loopback check.
  - `AnkiConnectActions` — the positive outbound action allowlist (required vs
    optional tiers) with `apiReflect` required-gap / available-optional
    analysis and a `requireAllowed` guard.
  - `AnkiConnectJson` — a bounded dependency-free JSON codec (objects/arrays/
    strings/numbers/bool/null, `MAX_DEPTH`, malformed→null). Shipped
    integer-only; corrected in Goal 191 to decode fractions as a distinct
    `Json.Frac`, because real Anki's `getDeckConfig` carries fractional values
    and rejecting them made the whole response a protocol error.
  - `AnkiConnectEnvelope` — API v6 single/`multi` request builders (pin
    version 6, attach key only when present, allowlist-check every nested
    action) and fail-closed `{result,error}` response parsing with per-nested
    `multi` validation.
  - `AnkiConnectTransport` + `JdkHttpExchange` — the bounded loopback transport:
    post-resolution loopback re-check (never touches the socket off-loopback),
    status/oversize/timeout/cancel mapping, redacted failure detail; production
    JDK `HttpClient` adapter with redirects disabled, no proxy, connect/request
    deadlines, and a hard response-body byte cap.
  - `AnkiConnectHandshake` — the keyless-first capability probe
    (`requestPermission` → `version` → `apiReflect` → `getMediaDirPath`)
    mapping to an actionable `Status`; reads no collection data. The final step
    shipped as `getActiveProfile` and was corrected during the first live run:
    that action does not exist, so every real Anki reported unavailable.
- Validation: `:provider-ankiconnect:check` and `ciDesktop` pass at 100% class
  coverage. `JdkHttpExchange` is exercised against a real in-process loopback
  `HttpServer` (200/error-status/oversize-cap/dead-endpoint); every other class
  is driven through injected fakes.
- Live gates: the read-only handshake run against a local Anki/AnkiConnect
  session is still outstanding (needs a running Anki desktop).
- Rollback: revert the four commits above (newest first); the module is
  additive and not yet wired into `:desktop-app`.
- Gaps/blockers: rest of Goal 187 is the higher-level typed action DTOs
  (`modelNamesAndIds`, `findNotes`/`notesInfo`, `findCards`/`cardsInfo`, media,
  etc.), the `SecretStore` key lifecycle wiring, the full fake-server failure
  matrix (unauthorized key, wrong version, missing action, retry), and the
  live read-only handshake against a real Anki. Goals 188–191 (reads, writes,
  equivalence) build on this transport.

### Goal 187 remainder completion evidence (2026-07-31)

- Started from: `7aaba4e5` on `desktop/integration` (pushes to
  `origin/desktop/support`). Completes everything Goal 187 left open except the
  live handshake.
- Commits (in order):
  - `4dd096a5 provider: add AnkiConnect API-key lifecycle over the SecretStore port`
  - `3ea8ba87 provider: add typed AnkiConnect read requests and result parsers`
  - `34a01817 test: add AnkiConnect failure matrix over a fake loopback server`
- Implemented:
  - `AnkiConnectKeyStore` — the API-key lifecycle over the `SecretStore`
    platform port. A key is persisted only where a tested vault adapter exists;
    otherwise it is held for the process session only. There is no plaintext
    fallback, and the store never logs or returns a key in a diagnostic.
  - `AnkiConnectRequests` / `AnkiConnectReads` — typed builders and parsers for
    the planned read surface (`modelNamesAndIds`, `modelFieldNames`,
    `deckNamesAndIds`, `getMediaDirPath`, `findNotes`, `notesInfo`,
    `findCards`, `cardsInfo`, plus the bounded `modelFieldNamesMulti` group).
    Every builder routes through `AnkiConnectEnvelope.request`, so the
    allowlist, the pinned `version: 6`, and the present-only key are structural
    rather than per-call discipline.
  - `FakeAnkiConnectServer` — a real in-process loopback `HttpServer` with a
    per-action script and transport-level fault injection, driving the full
    failure matrix: malformed JSON, oversize body, HTTP error status, HTTP-200
    protocol error, unauthorized key, wrong version, missing action, and retry.
- Validation: `:provider-ankiconnect:check` (100% class coverage) and
  `ciDesktop` pass. `AnkiConnectFailureMatrixTest` 8 tests,
  `AnkiConnectKeyStoreTest` 6, `AnkiConnectRequestsTest` 7,
  `AnkiConnectReadsTest` 11.
- Live gates: still outstanding. This machine has no Anki Desktop installed
  (`which anki` finds nothing, no `~/.local/share/Anki2`) and nothing answers on
  `127.0.0.1:8765`, so the read-only handshake run cannot be performed here.
- Decisions: confirmed — no plaintext key persistence, and the outbound
  allowlist is enforced at the single envelope choke point rather than per call
  site.
- Rollback: revert the three commits above (newest first); all additive to
  `:provider-ankiconnect`, which is not yet wired into `:desktop-app`.
- Gaps/blockers: the live read-only handshake against a running Anki
  Desktop/AnkiConnect. Needs Anki installed on the target host; it is the same
  external dependency that blocks Goals 188 and 191's live runs.

### Goal 188 completion evidence (2026-07-31)

- Started from: `34a01817`. Delivers the provider-neutral snapshot from
  AnkiConnect.
- Commits (in order):
  - `27b6696c anki-connect: add bounded read planner and card normalization`
  - `501c61df anki-connect: implement model note and card reads`
  - `8a331a06 sync: normalize missing FSRS memory without fabrication`
  - `bb91b2d0 fix(ankiconnect): intersect browser-query notes with the configured model`
  - `19b3eb2a anki-connect: fetch note-type fields through bounded multi groups`
  - `23faebb5 feat(ankiconnect): bind the source identity to the active Anki profile`
  - `8556a21e refactor(ankiconnect): route the profile probe through AnkiConnectRequests`
  - `02d762dc feat(ankiconnect): expose the reader as a CollectionGateway under the shared contract`
  - `801f7409 test(sync-engine): pin the capability-present/absent admission goldens`
  - `7b705bfc refactor(core): share Anki field normalization across both providers`
- Implemented:
  - `AnkiConnectReadPlanner` — the bounding layer: `MAX_MULTI_ACTIONS = 25`,
    detail batches starting at 100 and adapting down by encoded byte size
    (`TARGET_BATCH_BYTES`) within `MIN_BATCH`/`MAX_BATCH`, and the explicit
    `MAX_ID_COUNT = 250_000` cap that fails with `OversizeIdResponseException`
    rather than trying to process an unbounded ID array.
  - `AnkiConnectCollectionReader` / `AnkiConnectGateway` — model discovery,
    configured-note population, browser-query population, note and card detail
    reads, deck/template/suspension/interval/repetition/lapse/tag mapping, and
    cancellation plus progress checks before metadata, before every batch, and
    around every row transformation. Browser-query matches are intersected with
    the configured model before merging, and configured cards whose template
    ord is not `0` stay rejected.
  - Suspension follows Android's `queue < 0`, deliberately *not* AnkiConnect's
    narrower `areSuspended`. `FSRS_MEMORY_STATE` is emitted as nullable and the
    capability is not declared for stock AnkiConnect, so admission sees absence
    rather than a fabricated value.
  - `AnkiConnectSourceKey` binds the open Kani database to the active Anki
    profile, so a profile change blocks sync instead of silently replacing the
    source.
  - `7b705bfc` moved field normalization into `:core` so both providers share
    one implementation rather than two that can drift.
- Validation: `:provider-ankiconnect:check` and `ciDesktop` pass at 100% class
  coverage. `AnkiConnectCollectionReaderTest` 42 tests, `AnkiConnectGatewayTest`
  22, `AnkiConnectReadPlannerTest` 10, `AnkiConnectCardNormalizationTest` 3,
  plus the capability-present/absent admission goldens in `:sync-engine`.
- Live gates: the read-only configured-sync snapshot against a running Linux
  Anki session is outstanding for the same reason as Goal 187's handshake — no
  Anki Desktop on this host.
- Decisions: confirmed — capability absence is reported, never synthesized; the
  Android suspension rule wins over the provider's narrower one.
- Rollback: revert the ten commits above (newest first). `7b705bfc` also touches
  `:core`; reverting it restores the per-provider normalizers.
- Gaps/blockers: live read-only snapshot only.

### Goal 189 completion evidence (2026-07-31)

- Started from: `7b705bfc`.
- Commits (in order):
  - `48847d81 feat(anki-connect): implement collection inventory scanning`
  - `f187a3a8 feat(anki-connect): add browser handoff and bounded media reads`
  - `9f78e810 test(anki-connect): prove inventory isolation and media safety`
- Implemented:
  - `AnkiConnectInventoryGateway` — collection-wide scanning for Missing Kanji
    analysis, kept separate from configured-model sync and from historical
    snapshots. The full note-ID discovery is bounded by the planner's ID cap and
    is *not* claimed to be streaming; only the detail passes are batched.
    Results report `notesRead`, `skippedNotes`, `modelCount`, and `queryMode`,
    so malformed-row isolation stays visible against the warning threshold.
    Cancellation and progress are checked per batch.
  - `AnkiConnectBrowseHandoff` — `guiBrowse` with the exact query Kani would
    show the user, so what Anki's browser displays is what Kani claimed. The
    returned card ids are deliberately discarded: the point is the handoff.
  - `AnkiConnectMediaReader` — bounded `retrieveMediaFile` with filename
    validation, a byte cap accounting for base64's 4/3 expansion, cache limits,
    and no arbitrary path writes.
- Validation: `:provider-ankiconnect:check` and `ciDesktop` pass at 100% class
  coverage. `AnkiConnectInventoryGatewayTest` 16 tests,
  `AnkiConnectMediaReaderTest` 14, `AnkiConnectBrowseHandoffTest` 9. The
  isolation tests assert no raw note text or media byte reaches a log or
  persistent inventory row.
- Live gates: not required for the deterministic surface; the live inventory run
  is folded into Goal 191's fixture.
- Decisions: confirmed — inventory never persists raw note text, and ID
  discovery is honestly described as bounded rather than streaming.
- Rollback: revert the three commits above (newest first).
- Gaps/blockers: none for the deterministic surface.

### Goal 190 completion evidence (2026-07-31)

- Started from: `9f78e810`. Delivers the whole desktop write surface.
- Commits (in order):
  - `aa078c2e anki-connect: add isolated archive and repaired tag writes`
  - `d3273c43 anki-connect: add idempotent Missing Kanji creation`
  - `5fcd385e test: enforce the desktop provider write surface`
- Implemented:
  - `AnkiConnectTagWriter` — `kani_archived`/`kani_repaired` writes as one-note
    `addTags` actions inside bounded `multi` batches, with every nested envelope
    inspected so one failure cannot hide another. Failures are isolated per note
    and reported for a later retry; the writer never throws for a provider
    problem, so a sync cannot fail because a tag did not land. Legacy
    `kanji_anki_archived` stays recognized on the read side.
  - `AnkiConnectMissingKanjiWriter` — Missing Kanji deck/model discovery, shape
    validation, `createDeck`, `createModel`, `addNotes` in batches of at most
    100, and stable `SourceId` reconciliation. Five properties are load-bearing
    and documented in the class KDoc:
    - **Reconciliation is the source of truth, not the write's own answer.**
      `addNotes` is not treated as batch-atomic: mixed ids, `null` entries, a
      wrong-length array, a non-array result, a protocol error, a timeout, a
      cancellation, and a dropped connection all lead to the same thing — re-read
      the intended batch by `SourceId` before recording a receipt or retrying.
    - **A collision is never rewritten.** An existing model of Kani's name is
      reused only when field order, CSS, and the single template's name and both
      formats all match exactly; otherwise `MODEL_COLLISION`.
    - **Unprovable means refused.** `getDeckConfig` is the only standard-action
      way to tell an ordinary deck from a filtered one (`deckNamesAndIds`
      reports no `dyn` flag), so a deck that cannot be proven ordinary fails
      closed to CSV rather than being assumed compatible. This is why two read
      actions are as required as the writes.
    - **`destination_key` binds to the source, not just the endpoint.** It is
      `ankiconnect:<sha256(endpoint|profile)>:<modelId>`, so after a restore or
      rebind a local receipt cannot suppress a write the new collection does not
      have. The digest also keeps the endpoint and profile name out of stored
      state.
    - **CSV stays fully usable** for unsupported capability, collision, auth
      loss, and any unfinished write.
  - `AnkiConnectWriteSurfaceTest` — the write surface enforced three ways, each
    catching what the others miss: positively at the allowlist (every action
    literal in the module's main sources is scanned out and must be allowlisted,
    and the reverse — an allowlisted action nobody sends is unreviewed surface);
    negatively by name (~50 real scheduling/note-rewrite/deck-options/
    collection-wide actions asserted absent from the allowlist and refused by
    both the envelope builder and `multi` nesting); and behaviorally, by running
    a full export, a full tag write, a refused export, and a full inventory scan
    against a collection with real review history and asserting every
    pre-existing card, deck options group, and note field is unchanged. New
    entries are allowed, because additive is the point.
  - `FakeAnkiCollection` — a stateful in-process Anki. It exists because a
    scripted exchange cannot express what the writer is built around: that a
    write changes what a later read returns. It also *really implements* the
    denied actions (moving queue, due, interval, reps, deck, config id, note
    fields), because a fake that errored on them would let the behavioral
    assertion pass for the wrong reason;
    `theSnapshotDetectsASchedulingWrite` proves each one is detectable and
    `theFakeImplementsTheDeniedActionsItClaimsTo` stops the two lists drifting.
- Validation: `:provider-ankiconnect:check` (100% class coverage) and
  `ciDesktop` pass; `python3 tools/test_module_boundaries.py` 22 tests OK.
  `AnkiConnectMissingKanjiWriterTest` 50 tests,
  `AnkiConnectTagWriterTest` 18, `AnkiConnectWriteSurfaceTest` 14. Module total
  302 tests, 0 failures. First write, retry, partial failure, cancellation,
  collision, auth loss, mixed-ID/null `addNotes`, and zero-duplicate
  reconciliation all pass; the tag-write tests assert a sync survives every
  write failure.
- Notable defect found and fixed during the work: the post-write reconciliation
  honored cancellation, so cancelling mid-run discarded the only record of notes
  Anki had already committed and the next run would have created them again.
  Reconciliation is now unconditional; cancellation is honored *before* the next
  batch instead, matching the tag writer's shape.
- Live gates: no write action was issued against any real profile. Per the plan's
  live-gate rule, Goal 190's write tests run only against a throwaway
  copied/sanitized profile, and none exists on this host — see Goal 191.
- Decisions: confirmed — the desktop write surface is note tags plus additive
  notes in Kani's own deck and note type, and nothing else; `getDeckConfig` is
  accepted as a required read because it is the fail-closed filtered-deck proof.
- Rollback: revert the three commits above (newest first); all additive to
  `:provider-ankiconnect`, which is not yet wired into `:desktop-app`.
- Gaps/blockers: none for the deterministic surface. The live write qualification
  is Goal 191 and needs Anki Desktop plus a throwaway profile on the host.

### Goal 191 completion evidence (2026-07-31)

- Started from: `f3ba9b56` on `desktop/integration` (pushes to
  `origin/desktop/support`).
- Commits (in order):
  - `29430df5 test: add the pinned live Anki Desktop fixture`
  - `8290d1c0 fix: probe the profile identity with an action Anki actually has`
  - `90e0c3e6 test: add cross-provider normalized snapshot conformance`
  - `aefcdc90 fix(ankiconnect): decode fractional JSON instead of failing the response`
  - `8193b644 test: seed a sanitized Kiku collection into the Anki Desktop fixture`
  - `a333d967 test: qualify the desktop provider against real Anki Desktop`
  - `<runbook commit> docs: record the desktop provider qualification runbook`
- Implemented:
  - `ci/scripts/run_anki_desktop_fixture.sh` — the pinned Linux fixture: verified
    Anki 26.05 + AnkiConnect `4064fa142785975255457abd6a496015f5b71f38`
    downloads, a seeded `KaniFixture` profile with the first-run language dialog
    suppressed, AnkiConnect installed into the fixture's own add-on directory,
    and Anki launched under `xvfb-run` against the fixture's own base directory.
    Refuses to bind port 8765.
  - `ci/scripts/seed_anki_desktop_kiku_collection.py` — the sanitized 8-note Kiku
    collection, seeded *through AnkiConnect* rather than by authoring a `.anki2`,
    because Anki 26.05's schema is backend-migrated and a hand-pinned schema is a
    maintenance trap. Idempotent and converging. Covers mature/suspended/buried/
    learning/new/browser-tagged states.
  - `ProviderCardPolicy` in `:sync-domain` + `CrossProviderSnapshotSpec` in
    `sync-api` test fixtures — the shared normalization and the one conformance
    contract both providers are held to, rather than a provider-local test each.
  - `LiveAnkiDesktopQualificationTest` — 17 opt-in live tests over the whole
    surface: handshake/capabilities, configured read, progress, cancellation,
    provider snapshot, inventory, browser-query merge, archive + repaired tag
    writes, Missing Kanji create/retry, browser handoff, and source-key binding.
  - `docs/desktop-provider-qualification-runbook.md` — the repeatable procedure
    and the sanitized evidence.
- Validation: `./gradlew ciFast` BUILD SUCCESSFUL;
  `python3 tools/test_module_boundaries.py` 22 tests OK;
  `python3 tools/test_source_hygiene.py` 6 tests OK;
  `ci/tests/test_seed_anki_desktop_kiku_collection.py` 23 tests OK.
- Live gates: Anki Desktop `26.05`, AnkiConnect
  `4064fa142785975255457abd6a496015f5b71f38`, API version 6, port `18765`,
  throwaway profile `KaniFixture`. `OK (17 tests)`, 0 failures, 0 skipped, on two
  consecutive runs; 3.2 s wall clock. Required actions 12/12 present, optional
  10/10. Gateway capabilities `READ_COLLECTION`, `LIST_NOTE_TYPES`,
  `NOTE_TAG_WRITE`; writer capability `MISSING_KANJI_WRITE`;
  `FSRS_MEMORY_STATE` never advertised. Read 8 notes / 8 cards, 0 skipped, no
  malformed-row warning, 2 suspended (`queue < 0`: 1 suspended + 1 buried), 3
  mature active, 0 negative intervals. Archive tag write tagged 2 notes, deleted
  0, idempotent on re-run. Missing Kanji export created 1 note then reported it
  already present on retry with 0 duplicates and a stable `destination_key`.
  Evidence is aggregate and sanitized; no field, deck, or model content from any
  personal collection was recorded, and no write action was issued against the
  operator's profile.
- Two defects found that no mock could find, both now fixed and pinned:
  1. The profile-identity probe called `getActiveProfile`, which is not an
     AnkiConnect action. Every real Anki reported unavailable while the mock
     passed. `getMediaDirPath` is the correct probe — it reports the *loaded*
     profile, which `getProfiles` cannot.
  2. `AnkiConnectJson` rejected fractional numbers, so real Anki's
     `getDeckConfig` (`"delays": [1.0, 10.0]`, `"ease4": 1.3`) decoded to `null`,
     surfaced as a protocol error, and `ensureDeck` correctly read that as an
     unprovable deck and reported `DECK_COLLISION`. **The Missing Kanji export
     could not complete against any real Anki.** Fractions now decode to a
     distinct `Json.Frac`, so nothing reading an id or counter can be handed one.
  Both share a shape worth remembering: a mock written from the same
  understanding as the code under test agrees with it, including where that
  understanding is wrong.
- Decisions: confirmed — shared cross-provider normalization lives in
  `:sync-domain` (transitively visible to both providers) and the conformance
  spec in `sync-api`'s test fixtures, not duplicated per provider. Confirmed —
  the live suite is opt-in per Gradle invocation rather than via daemon system
  properties, so a reused daemon cannot silently re-enable a writing suite.
  Recorded — AnkiConnect refuses a negative `ivl` outright, so the legacy
  negative-seconds encoding is not reachable through this fixture and
  `ProviderCardPolicy`'s interval floor stays covered only by the AnkiDroid
  synthetic-cursor test.
- Rollback: revert the commits above (newest first). The fixture, seeder, suite,
  and runbook are additive; the two fixes are in `:provider-ankiconnect`, which
  is not yet wired into `:desktop-app`.
- Gaps/blockers: two items of Goal 191 are **not** done. The stricter local
  copied-user-profile gate at the 7,000-note threshold has not been run on this
  branch (the fixture is a conformance gate, not a scale gate). The
  Windows/macOS throwaway-profile smoke is defined in the runbook but not
  executed; it is due before Goal 207 and Linux success is not evidence for
  either.

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
