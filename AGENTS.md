# Kani 1.0

Kani 1.0 is a rewrite of the app around one job: cure kanji blindness. The
design lives in the "Kani App Design" artifact; the app is in
`app/src/main/kotlin/dev/bee/kanjianki/kani/`:

- `core/`: pure, unit-tested logic: `AnkiText` (field cleanup), `FieldPicker`
  (auto-picks fields), `ScanAnalyzer` (problem kanji and culprit attribution),
  `Training` (3-day solid rule, session planner, sprint builder), `Strokes`
  (KanjiVG stroke diff for "the part that decides" and handwriting checks),
  `Romaji` (live romaji → hiragana), `Memory` (how answers map onto the RWKV
  memory model: ratings, stream ids, the in-process `MemoryModel`, `Recall`).
- `memory/KaniMemory`: runs RWKV SM92 over the answer log in the background
  and caches each card's forgetting curve in `kani1.db`.
- `anki/AnkiSource`: read-only AnkiDroid access through Anki search strings,
  plus the opt-in `kani_solid` tag.
- `data/`: `KaniStore` (SQLite `kani1.db`), `KaniPrefs`, `Reference`
  (bundled dictionary, look-alike pairs, strokes), `Scanner`.
- `KaniViewModel` + `KaniActivity`: one screen state, no tab bar.
- `ui/`: Compose screens matching the design; light and AMOLED tokens in
  `KaniTheme`.

The app depends only on `:core` (which exposes `:rwkv`) and
`:dictionary-core`. The adaptive scheduler, FSRS, sync, writing and update
modules below are no longer used by the app; their notes are kept for the
modules themselves. Kani 1.0 deliberately starts fresh: it deletes the pre-1.0
`kanji_anki_simple.db`.

Kani 1.0 uses RWKV as its memory model, in a smaller form than the v34 design
in the "RWKV Memory Model Notes" below:

- **Cards.** Each kanji is a note with two cards (meaning check, reading
  check); the check is the deck; one preset. Every check answer is fed once,
  in `answer.id` order: right and fast (≤ `Progress.FAST_MS`) is Good, right
  but slow is Hard, a miss or "I don't know" is Again. Sprints, mix-up cards,
  the reading retry and writing are practice and are never fed.
- **State.** The `answer` table is the source of truth. The model state lives
  only in memory: the first update in a process replays the whole log, later
  ones feed new answers. The `memory` table caches each card's curve
  (`RwkvCardHistory`), and `memory_meta` records `MemoryRules.IDENTITY` and
  the last answer fed. A changed identity triggers a full replay. Readers (the
  planner, Today, the reminder) only read the cache and fall back to the plain
  rules without it.
- **Scheduling.** The 3-day solid rule still decides when training ends. The
  model orders training kanji (lowest predicted recall first, after chains
  and mix-ups) and brings a solid kanji back for a check once either check's
  predicted recall falls below `MemoryRules.TARGET_RECALL` (90%), asking the
  weaker check; a quarter of the session is kept for these, and a miss sends
  the kanji back to training.
- **Weights.** `rwkv/weights` is an app asset source dir; the APK carries
  `assets/sm92/weights.f32.bin`, verified against `RwkvAlgorithmInfo` on load.
  `KaniMemoryTest` (Robolectric) and `KaniMemoryModelTest` run the real
  weights.

# Agent Testing Runbook

This repo is an Android app that syncs against AnkiDroid's flashcard content
provider. Provider bugs can look fixed in unit tests while still failing on a
real AnkiDroid install, so release fixes must include a live AnkiDroid emulator
run when the change touches sync/provider behavior.

## Gradle Command Note

This repo includes a checked-in Gradle wrapper. Run Gradle tasks from the repo
root with `./gradlew`, for example `./gradlew :core:test`.

For Android tasks on this machine, prefer the prepared SDK under `/tmp` when no
`local.properties` file is present:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
```

## CI And Static Analysis Notes

For the full operator triage checklist, gate map, and release path
invariants, see `docs/ci-sonar-reliability-runbook.md`.

The normal local confidence gate is `./gradlew ciFast`. It runs deterministic
JVM tests, coverage reports/checks, app unit tests, Android instrumentation
compilation, lint, and Python asset tests. GitHub's default Android CI splits
that same deterministic surface across parallel jobs and aggregates them under
the `Fast confidence gate` check. Do not use SonarCloud or CodeQL as a
substitute for the normal Android CI workflow.

`./gradlew ciQuality` produces the deterministic bytecode and coverage inputs
used by SonarQube. `./gradlew ciRelease` runs the release confidence gate and
assembles the signed release APK when signing environment variables are set.
In CI, the deterministic test surface is enforced by `Android CI` itself: the
auto-release only triggers off a successful `Android CI` run, so the release
workflow's validate job just assembles and verifies the signed APK. Manual tag
pushes and `workflow_dispatch` releases (which can target commits no CI run
has vouched for) run the deterministic unit-test surface inline before
assembling. `ciRelease` is the local gate, not the CI release build command.

Beta releases are cut automatically: every successful `Android CI` run on a `main`
push triggers `android-release.yml` through a `workflow_run` trigger, which
computes the next numeric `vMAJOR.MINOR.PATCH` tag, verifies the APK at that CI
run's commit, and publishes it as a GitHub prerelease (creating the tag at
publish time). Numeric tags preserve update discovery for Kani 0.5.10 and
0.5.11; GitHub's prerelease flag, rather than a tag suffix, keeps stable users
on the latest stable release. The parser still accepts explicit SemVer
`vMAJOR.MINOR.PATCH-beta` tags for compatibility and deliberate manual beta
releases. The release path is deliberately self-contained: it
does not poll SonarQube/CodeQL check runs and it never runs emulator jobs.
Those were the top causes of blocked, flaky, and multi-hour releases; SonarQube
and CodeQL are advisory scans on `main`, and live AnkiDroid provider coverage
lives in the nightly/dispatch `android-instrumented.yml` workflow plus the
stricter local gate below. `tools/test_release_workflows.py` locks these
invariants in. Manual tag pushes and `workflow_dispatch` with an explicit
`release_tag` still work for deliberate stable versions.
Choose a stable version whose numeric core is newer than every published beta;
do not turn `vX.Y.Z-beta` into `vX.Y.Z`, because both map to the same Android
`versionCode`.

SonarCloud and CodeQL run on pushes to `main`, and can also be run manually.
CodeQL also has a scheduled weekly run. If you change either workflow, push it
and watch the first GitHub Actions run to completion; local Gradle success
alone is not enough to validate the service integration.

For SonarCloud, keep the hard target on correctness and maintainability: the
quality gate must pass, and code smells, bugs, vulnerabilities, and unreviewed
security hotspots should be driven to zero. Treat 100% coverage as an
optimization target only. Do not slow the normal CI path just to chase perfect
coverage; prefer the fast deterministic coverage path by default and reserve
full connected coverage for manual investigation or release-risk checks.

The deterministic AnkiDroid fixture workflow runs nightly and through
workflow-dispatch only; it is not part of the release path. It generates a
small sanitized Kiku collection in CI, installs pinned AnkiDroid in an
emulator, grants the real provider permission, and runs the live-provider
sync subset with `kanjiLiveAnkiDroid=true` and a small
`kanjiLiveMinimumNotes` value.

The local real-collection live gate remains stricter. Do not cut a release for
provider/sync changes unless the local copied user-collection AnkiDroid run
passes with the default 7,000-note threshold.

Useful commands:

```sh
gh run list --repo bee-san/kanji_anki --limit 10
gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status
```

The CodeQL workflow uses manual Java/Kotlin analysis for this Android Gradle
project. Keep the CodeQL build step after `github/codeql-action/init` as a
forced clean compile:

```sh
./gradlew clean :fsrs-java:compileJava :rwkv:compileJava :core:compileJava :app:compileDebugJavaWithJavac --no-daemon --no-build-cache
```

Do not simplify that to a normal compile. Gradle can mark the compile tasks
up-to-date from cache, and then CodeQL fails with "this run didn't build any of
it" because it saw no compiler activity to extract.

## Kotlin compiler version coupling

The version catalog (`gradle/libs.versions.toml`) pins a single `kotlin`
version used for BOTH the Kotlin JVM plugin (library modules) and the Compose
compiler plugin (`kotlin-jvm` and `kotlin-compose` share `version.ref =
"kotlin"`). The `:app` module does not apply the Kotlin JVM plugin; it compiles
with AGP's built-in kotlinc (see the `built_in_kotlinc` intermediates paths in
the root `build.gradle.kts` Sonar config), so the app's compiler is coupled to
the AGP version (`agp` in the catalog), not to the catalog `kotlin` version.

Keep this pairing deliberate: when bumping `agp`, verify AGP's embedded Kotlin
is compatible with the catalog `kotlin` (Compose compiler) version, and bump
`kotlin` in lockstep if needed. Renovate updates both `kotlin-jvm` and
`kotlin-compose` together because they share the version ref. A future cleanup
(deep-review Goal 24) may apply KGP to `:app` explicitly to unify all modules
on one compiler; until then, treat an `agp` bump as requiring a Kotlin-compat
check.

For SonarCloud test assertions, avoid direct `assertFalse(value.equals(...))`
because `java:S5785` asks for `assertNotEquals`. Also avoid `assertNotEquals`
between intentionally different types because `java:S5845` reports it as a bug.
If you need to cover the non-matching `equals` branch, assign the result to a
boolean and assert on that boolean:

```java
Object nonPoint = "not a point";
boolean equalsNonPoint = point.equals(nonPoint);
assertFalse(equalsNonPoint);
```

## Study Scheduler Notes

Database v31 activates the integrity-first two-core adaptive scheduler. Read
`docs/adaptive-two-core-scheduler.md` before changing study behavior. Canonical
adaptive items have exactly two long-term core memories: recognition anchored
at `kanji_meaning`, and contextual reading anchored at `word_reading`. Font and
sentence tasks are deterministic variants sharing those memories; every other
task is targeted inline repair. `type_reading` is repair-only and has no
long-term memory. A real-due core Fail feeds the memory model one Again, repair
is practice-only (never fed to the model), and
the same core is revalidated within one day. Never create a second scheduler
queue, re-enable writes to `similar_kanji_repair_queue`, or let a repair attempt
add lapses/stability changes. Legacy rungs/columns/wires/settings stay stored for
lazy conversion and downgrade compatibility.

Review persistence is token-first and revision-CAS. Item, review evidence,
timeline, task timing, choice state/log, and stats dirtiness belong in one
transaction; only an APPLIED result may advance UI/session state. Sync history
is successful-run-only. Backups/restores must retain the v31 bounded-stream and
atomic-publication guarantees.

Settings presents the two core checks as required, font/sentence presentations
as optional variants, and repair tools as a separate enabled/priority list.
`study_ladder_order` and `study_ladder_enabled` remain compatibility state;
adaptive repair ordering and enablement live in `adaptive_repair_order` and
`adaptive_repair_enabled`. Do not derive adaptive repair priority from the old
ladder order. Stats cache format 11 is the live analytics contract: it reports
core progress, inline repair task/cause, revalidation, escalation risk, and
stuck repair state, with a legacy fallback only for unconverted items.

Still-current rules that used to live in the legacy section:

- The study UI shows `Pass`/`Fail`; the wire format stays
  `good`/`again`/`hard`/`easy` (`Pass` → `good`, `Fail` → `again`). Handwriting
  offers only `Pass`/`Fail`, except a `CLOSE` ink verdict whose single action is
  `Save hard` (submits `hard`). "Mark right anyway" records `hard`; "I got it
  wrong" records `again` as a failed, non-clean write.
- Queue admission and evidence-based seeding are owned by `StudyQueueSeeder`
  and the pure `AdmissionEvidencePolicy`: do not make the learner study kanji
  they do not need to. Rows already meeting `matureSupportThreshold` are not
  admitted unless repair evidence is `REGRESSING`.
- Graduation takes its interval from the memory model's answer to the
  graduating rating; learning/relearning step answers are fed to the model but
  their next appearance uses the configured step delay. The FSRS-era graduation
  rules survive only in the `FsrsTestMemoryModel` test double.
- `learning_repeats`, `similar_kanji_choice_state`, and `similar_kanji_pairs`
  are compatibility/content state, never scheduler sources.

Legacy v30 ladder/rung, conditional-rung, promotion/demotion, ceiling-parking,
rung-rendering, and DB v16 field-mapping notes live in
`docs/legacy-v30-ladder-agent-notes.md`. Read them only when touching legacy
routing, lazy conversion, or compatibility columns.

## RWKV Memory Model Notes

Read `docs/rwkv-memory-model.md` before changing memory scheduling. The model
is a pure-Kotlin port of the small RWKV-Curve engine (SM92) from
`Pedrubik2000/kuma3-anki-core` (AGPL-3.0-or-later) in the `:rwkv` module;
`RwkvAlgorithmInfo` pins the upstream commit and the weights' sha256, and the
weights ship as `rwkv/weights/sm92/weights.f32.bin`. The published
SM92 vectors are the parity oracle (`RwkvPublishedVectorsTest`, bound 1e-5).

- **History rule.** The model is fed exactly once per core answer: every
  legacy-routing answer (learning and relearning steps included) and every
  adaptive core or revalidation answer. Adaptive repair appearances,
  similar-kanji practice and undone reviews are never fed. Card = (kanji, core,
  signature meaning), note = kanji, deck = core, one preset (`RwkvStreamKeys`).
- **State is a derived cache** of the ordered history: `review_log` rows with
  `mm_seq > 0` (their exact model inputs live in the `mm_*` columns) merged with
  `rwkv_burst` seed bursts by sequence. `RwkvStateStore` writes the state delta,
  undo journal and `rwkv_meta` in the review transaction behind a
  `state_version` compare-and-swap (a mismatch is STALE). Undo restores from the
  journal only when that review was the last state change; otherwise it marks
  the state for rebuild. Never write model state outside that transaction.
- **Rebuilds** (`RwkvStateRebuilder`) classify pre-v34 rows once, replay the
  whole history and publish with the same compare-and-swap. Study answers are
  handed back for retry (`RwkvRuntime.readyNow`) until the state is ready.
- **Bindings.** `BridgeScheduler()` has no memory model and throws on review;
  reviews use `BridgeScheduler.withMemoryModel(RwkvReviewBinding.model)`, and the
  forecast uses single-pass RWKV over a copy-on-write overlay.
- **Numeric guards.** Negative day gaps are clamped (a clock moving back must
  not NaN the shared user stream) and a non-finite step is never committed.
- **Goldens.** After a deliberate scheduler or memory-model change, regenerate
  with `./gradlew :core:test -PupdateSchedulerGoldens` and review the diff.
- **Licensing.** The `:rwkv` port is AGPL-derived and stays AGPL-3.0-or-later (`rwkv/LICENSE`)
  while the rest of Kani is EUPL-1.2 (`LICENSE`); see
  `THIRD_PARTY_NOTICES.md` (including the open question on the weights' training
  data terms) before changing what ships.

## Provider Write-Back, Backup, And Widget Notes

Kani's AnkiDroid write surface is deliberately note-tag-only. Sync may add
`kani_archived` to fully imported suspended notes and, when the user enables
the default-off `tag_repaired_cards` setting, add `kani_repaired` to fully
suspended notes whose kanji have passed the repair gate. Both paths use
idempotent per-note read-modify-write, isolate failures, and retry on a later
sync; neither may fail an otherwise committed sync. Repaired tagging is also
manual-confirm-only: the Home confirmation shows the proposal count and the
automatic sync runner is not authorized to perform this write-back. Kani never writes card
queue, due date, interval, ease, deck, or any other Anki scheduling state.
Successful repaired-note writes stamp `suspended_archive.restored_at`, and the
Home hand-off copies `tag:kani_repaired is:suspended` so the user can review
and unsuspend the cards in AnkiDroid.

On API 30+, automatic database backups are WAL-safe `VACUUM INTO` gzip
snapshots with the tiered 7-daily/4-weekly retention policy. A backup is
compressed into a same-directory `.partial`, fsynced, and strictly atomically
published; any snapshot/publication failure must preserve the prior final
archive. Stock API 26–29 cannot provide this live-snapshot contract, so Kani
cancels automatic backup work and disables fresh export/new restore there while
preserving the current database and all existing archives. Never restore the
removed checkpoint/main-file-copy fallback.

Settings > Automation > Backup & restore can export a fresh snapshot through
Android's document picker and can validate and atomically stage a whole-file
restore on API 30+. Restore decompression is streamed, capped at 512 MiB, and
requires a 64 MiB free-space reserve. Startup first makes the staged file and a
pre-restore safety snapshot durable, then publishes a versioned `SAFETY_READY`
marker before strict `ATOMIC_MOVE` replacement. It fsyncs both rename-parent
directories, deletes stale WAL/SHM sidecars, fsyncs the database directory, and
only then deletes the marker and fsyncs the restore directory. There is no
non-atomic replacement fallback. Any marker-bearing failure blocks
database-capable startup. Only versioned ready marker-only state may perform
cleanup; ambiguous legacy marker-only state preserves the live database and
sidecars for manual recovery.

The home-screen widget follows the same `ReminderEligibilityPolicy` filter as
notifications (D-S6): its due count cannot advertise work that the Study route
will reject. Widget refresh is event-driven after committed sync, completed
study, daily reminder evaluation, and system widget updates; it has no separate
periodic worker.

## Earlier Provider Validation

The original v0.3.6 `_id is unknown` provider-fix validation record is in
`docs/release-notes/v0.3.6-provider-validation.md`.

## Latest Provider Gate For Goals 82–95

The Goal 94 write-back batch was revalidated on 2026-07-10 against the same
real AnkiDroid 2.24.0 provider and copied user collection, with the default
7,000-note threshold. The targeted command below completed `OK (62 tests)`:
one foreground sync-button test, 58 fake-provider contract tests, and two real
provider tests. The non-destructive card update probe wrote the card's existing
queue value back to its own card URI; AnkiDroid rejected it with
`IllegalArgumentException` (`updatedRows=-1`) and the reread queue remained `2`.
This confirms that Kani's supported write surface remains note tags only.

The RWKV memory-model migration (database v34) was gated on 2026-11-18 against
AnkiDroid 2.24.0 with the copied user collection (15,116 notes, 3,746 unique
kanji): the targeted command completed `OK (67 tests)` (the suite has grown to
62 fake-provider contract tests, one foreground sync-button test and four real
provider tests). The first run on a freshly wiped emulator showed nine
fake-provider failures cascading from a still-running live sync ("Sync already
running"); the class passes alone and the full gate passed on re-run.

## Live AnkiDroid Emulator Setup

Use a throwaway emulator copy of the user's collection. Do not modify the
desktop collection directly.

1. Start or create an Android emulator with Google APIs available. For the
   previous release, the AVD was `kanji_anki_api35`.

   ```sh
   env ANDROID_SDK_ROOT=/tmp/android-sdk ANDROID_HOME=/tmp/android-sdk \
     /tmp/android-sdk/emulator/emulator \
     -avd kanji_anki_api35 \
     -wipe-data \
     -no-window \
     -no-audio \
     -no-snapshot \
     -gpu swiftshader_indirect
   ```

2. Wait for boot.

   ```sh
   adb wait-for-device
   adb shell getprop sys.boot_completed
   ```

   Continue only when the second command prints `1`.

3. Install real AnkiDroid.

   ```sh
   adb install -r /tmp/ankidroid-2.24.0/variant-abi-AnkiDroid-2.24.0-x86_64.apk
   ```

4. Launch AnkiDroid once so it creates its preferences.

   ```sh
   adb shell monkey -p com.ichi2.anki 1
   ```

5. Copy the live collection into AnkiDroid's app-specific emulator directory.
   Media is not needed for the sync tests because the app reads note/card data
   from the provider, not rendered card media.

   ```sh
   adb shell mkdir -p /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media
   adb push "$HOME/.local/share/Anki2/User 1/collection.anki2" \
     /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.anki2
   ```

   If the provider smoke query returns `No result found` or AnkiDroid logs
   `No write access to AnkiDroid directory`, inspect ownership:

   ```sh
   adb shell ls -ld /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid
   ```

   After a root/shell push, the copied `AnkiDroid` directory may be owned by
   `shell` instead of the AnkiDroid app uid. Fix the emulator copy before
   continuing:

   ```sh
   adb shell 'chown -R u0_aNNN:ext_data_rw /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid && chmod -R u+rwX,g+rwX /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid'
   ```

   Replace `u0_aNNN` with the owner of
   `/storage/emulated/0/Android/data/com.ichi2.anki`.

6. Point AnkiDroid at that copied collection path.

   ```sh
   adb root
   adb shell sed -i \
     s#/storage/emulated/0/AnkiDroid#/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid# \
     /data/user/0/com.ichi2.anki/shared_prefs/com.ichi2.anki_preferences.xml
   adb shell am force-stop com.ichi2.anki
   adb shell monkey -p com.ichi2.anki 1
   ```

7. Smoke-test the real provider.

   ```sh
   adb shell content query --uri content://com.ichi2.anki.flashcards/models
   ```

   This should return model rows from the copied collection. Avoid huge repeated
   provider dumps after this point.

## Running The Live Tests

Build and install the debug app plus instrumentation APK.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant dev.bee.kanjianki com.ichi2.anki.permission.READ_WRITE_DATABASE
```

Run the targeted live release gate with the live fixture enabled. This keeps the
slow real-collection run focused on the provider and the foreground sync button
path; unrelated UI tests use the fake provider in the normal local gate.

```sh
adb logcat -c
adb shell am instrument -w \
  -e kanjiLiveAnkiDroid true \
  -e class dev.bee.kanjianki.MainActivityInstrumentedTest#testManualSyncButtonWorksAgainstLiveAnkiDroid,dev.bee.kanjianki.anki.AnkiDroidGatewayProviderInstrumentedTest,dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest \
  dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner
```

Expected result:

```text
OK (67 tests)
```

Important live tests:

- `MainActivityInstrumentedTest.testManualSyncButtonWorksAgainstLiveAnkiDroid`
  taps `Sync AnkiDroid`, confirms `Sync cards`, and verifies a
  successful sync, dashboard rows, and study items.
- `RealAnkiDroidLiveProviderInstrumentedTest` reads the copied Kiku collection
  through the real AnkiDroid provider once and asserts at least 7,000 Kiku
  notes/cards plus real scheduler state. Its second test probes a same-value
  card-queue update, asserts the queue is unchanged, and prints the provider's
  observed result for the parity record.
- `AnkiDroidGatewayProviderInstrumentedTest` uses the fake provider to reject
  explicit `_id` projections, unsupported scheduler projections, and deferred
  cursor-time errors such as `Queue "queue" is unknown`.

If the live test fails with `SQLiteDatabaseLockedException` in a test poller,
retry only after confirming the app did not crash. The test poller should treat
`SQLITE_BUSY` as "sync still committing", not as a product failure.

## Local Production Gate

Before pushing a release tag, run:

```sh
./gradlew ciRelease \
  -PKANI_SIGNING_STORE_FILE=/tmp/kanji_anki_temp_release.jks \
  -PKANI_SIGNING_STORE_PASSWORD=temporary123 \
  -PKANI_SIGNING_KEY_ALIAS=kanji_temp \
  -PKANI_SIGNING_KEY_PASSWORD=temporary123
```

Then verify the generated APK:

```sh
/tmp/android-sdk/build-tools/36.0.0/apksigner verify --verbose \
  app/build/outputs/apk/release/app-release.apk
/tmp/android-sdk/build-tools/36.0.0/aapt dump badging \
  app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

For `v0.3.6`, the local test APK was only a local keystore build. The public APK
must come from the GitHub Actions release workflow, which uses repository
secrets.

## Release Flow

The default path is automatic: push (or merge) to `main`, and a successful
`Android CI` run on that push triggers `android-release.yml`, which bumps the
patch version, appends the canonical `-beta` suffix to the tag, APK
`versionName`, and asset filename, builds and verifies the signed APK, and
publishes the prerelease with no further gating. Watch it with:

```sh
gh run list --repo bee-san/kanji_anki --workflow android-release.yml --limit 5
gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status
```

For a deliberate (non-patch or re-cut) version, use the manual flow:

Pick a numeric version newer than all existing stable and beta tags. In
particular, publish `vX.Y.(Z+1)` or later after `vX.Y.Z-beta`; stable and beta
variants of the same numeric core share an Android `versionCode`.

1. Commit only the relevant files.
2. Push the branch.
3. Confirm the release tag is unused.

   ```sh
   git ls-remote --tags origin vX.Y.Z
   ```

4. Create and push the tag.

   ```sh
   git tag -a vX.Y.Z -m "vX.Y.Z"
   git push origin vX.Y.Z
   ```

5. Watch the release workflow.

   ```sh
   gh run list --repo bee-san/kanji_anki --workflow android-release.yml --limit 5
   gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status
   ```

Creating or publishing a GitHub Release directly in the UI does not trigger
this workflow. Use a tag push or `workflow_dispatch` for a deliberate release.

6. Fetch the release asset URLs.

   ```sh
   gh release view vX.Y.Z --repo bee-san/kanji_anki \
     --json url,tagName,name,assets,isDraft,isPrerelease,publishedAt
   ```

Do not cut a release for provider/sync changes unless the live AnkiDroid
instrumentation suite and local production gate both pass.
