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

Releases are cut automatically: every successful `Android CI` run on a `main`
push triggers `android-release.yml` through a `workflow_run` trigger, which
computes the next `vMAJOR.MINOR.PATCH` patch tag, builds and verifies the
signed APK at that CI run's commit, and publishes the GitHub release (creating
the tag at publish time). The release path is deliberately self-contained: it
does not poll SonarQube/CodeQL check runs and it never runs emulator jobs.
Those were the top causes of blocked, flaky, and multi-hour releases; SonarQube
and CodeQL are advisory scans on `main`, and live AnkiDroid provider coverage
lives in the nightly/dispatch `android-instrumented.yml` workflow plus the
stricter local gate below. `tools/test_release_workflows.py` locks these
invariants in. Manual tag pushes and `workflow_dispatch` with an explicit
`release_tag` still work for deliberate versions.

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
./gradlew clean :fsrs-java:compileJava :core:compileJava :app:compileDebugJavaWithJavac --no-daemon --no-build-cache
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

The study scheduler is centered on a single ladder state machine, not on side
queues. Every persisted study item has exactly one current rung and one phase.

Default ladder order from most-scaffolded (bottom) to least-scaffolded (top)
rung:

1. `write_kanji`
2. `type_meaning`
3. `meaning_kanji`
4. `reading_kanji`
5. `similar_kanji`
6. `kanji_meaning`
7. `font_meaning`
8. `kanji_reading`
9. `word_reading`
10. `sentence_reading`

`similar_kanji` sits directly below `kanji_meaning` (the new-card start rung)
so the first demotion reaches discrimination practice — the app's signature
remediation — in one demotion step for cards that have confusion data, instead
of three (Goal 65). This only affects fresh installs and stored configs that
lack `similar_kanji`; a user's stored order is preserved verbatim.

`reading_kanji` (Goal 79) is the phonetic sibling of `similar_kanji` and sits
directly below it (not between `similar_kanji` and `kanji_meaning`), preserving
the Goal 65 invariant that `similar_kanji` stays directly below the start rung.
It is a conditional homophone-discrimination rung ("だつ — which kanji is 〇出?")
gated by `hasReadingKanji` (some attested reading of the kanji is shared by ≥ 2
other inventory kanji, so a ≥ 3-choice card exists).

`kanji_reading` (Goal 78) sits directly below `word_reading` so a
`word_reading` fail streak demotes straight into targeted reading
discrimination ("How is 脱 read in 脱出?"). It is a conditional rung: the
`hasKanjiReading` predicate (answered by the `kanji_reading_usage` /
`kanji_reading_pool` tables — the kanji has an attested usage word and at least
two distinct candidate readings) must hold, else promotion and demotion cross
over it without pausing, exactly like `similar_kanji`.

`sentence_reading` (Goal 80) is the new ladder ceiling: read the target word
inside the user's own mined sentence. It is conditional on `hasSentenceReading`
(≥ 1 example with both a non-blank sentence and reading); cards without sentence
data keep `word_reading` as their effective ceiling because `nextRung` returns
the current rung when no higher valid rung exists.

`meaning_kanji`, `reading_kanji`, `kanji_reading`, and `sentence_reading` are
present in the editable default order and, like every other rung, enabled by
default. Stored configurations that predate a rung get it auto-enabled on load
and spliced into the stored order adjacent to its default neighbors
(`StudyLadderSettings.AUTO_ENABLE_RUNGS`, generalizing the original
single-`meaning_kanji` clause; D-R4).
Users can turn rungs on/off or move them in Settings. New cards start at
`kanji_meaning`; if that rung is disabled, they start at the nearest enabled
rung, preferring the lower/more-scaffolded rung when the distance ties. The
conditional rungs (`similar_kanji`, `kanji_reading`, `reading_kanji`,
`sentence_reading`) exist only when the app can build a valid card for that
card's kanji. When a conditional predicate is false, promotion and demotion
cross over that rung without pausing (a `<wire>_unavailable` trace reason is
recorded per skipped rung; a move that crosses several conditional rungs at once
records one code each). Settings must keep at least one always-available rung
enabled; the conditional rungs alone are not enough because they depend on
per-card data.

Phases: `new_learning`, `review`, `relearning`. Learning and relearning follow
Anki semantics:

- `Again` returns to the first step.
- `Good` advances one step; graduates past the last step.
- `Hard` on the first step uses a delay between Again and Good (the
  first-two-step midpoint, or 1.5x the step when only one step exists); on
  later steps it repeats the current step.
- `Easy` graduates the card immediately.

Learning and relearning repeats are practice-only. They do not advance
promotion, demotion, or any long-term scheduler threshold. Only persisted
FSRS-due review attempts in the `review` phase count toward ladder movement.
The boundary is the task's persisted FSRS due time, not the calendar day or
any learning-repeat queue.

Ladder movement uses real FSRS due-review evidence, not learning-repeat
practice. A due-review `Hard`, `Good`, or `Easy` promotes the rung only when
**both** hold: the card's memory strength — measured as the interval it
would schedule at a fixed 0.90 target retention, not the user's actual
retention-scaled interval — is strictly more than
`ladder_promotion_interval_days` (default 21 days), and the
card has accumulated at least `ladder_promotion_min_passes` real-due passes
on its current rung (default 2). Keying promotion off the fixed-0.90
interval means changing the retention setting no longer silently changes
ladder progression speed (closed decision D4). The min-pass gate stops a mature card from
cascading up two rungs in two reviews after the promotion cap clones
above-threshold stability onto the newly promoted rung, and it guards against
demotion bounce-back (a demotion resets the pass streak to 0, so the first
post-demotion pass cannot re-promote). Setting `ladder_promotion_min_passes`
to 1 reproduces the pre-gate single-pass promotion. A
due-review `Again` increments a consecutive fail streak and demotes the rung
when it reaches `ladder_demotion_fail_streak` (default 3 fails). At
`write_kanji` the demotion floor is reached and further `Again`s keep the
card on that rung. At `word_reading` the promotion ceiling is reached and
further passes keep the card on that rung.

FSRS weights are default-neutral but may be personalized when the user opts
in. `scheduler_fsrs_weights` stores all 21 values as a full-precision
comma-separated string; malformed vectors fail open to the built-in defaults.
The same selected weights drive normal/relearning review intervals, sync
seeding, forecast simulation, and the fixed-0.90 `promotionIntervalDays`
memory-strength signal above (D-S7). The weekly on-device fitter trains only
from persisted `review`-phase evidence and adopts a vector only with at least
400 samples in the 80% training partition (about 500 total samples) and at
least a 1% time-ordered validation log-loss improvement;
the toggle is off by default and turning it off immediately clears the live
custom vector.

Exception (fail-fast demotion): the first real review after a promotion is the
capped validation review. If that first attempt is an `Again`, the rung demotes
immediately instead of waiting for the full fail streak, because a failed
validation is direct evidence the promotion was premature. This only applies
above the starting rung and only while the item is still on the capped
first-review interval (`ReviewTransitionEngine.isFailedPromotionValidation`); a
brand-new card's first failed review at the starting rung never demotes below
where new cards begin.

On a due review `Again`, the card enters `relearning` at step 0 if
relearning steps exist. If relearning steps are empty, the card skips
relearning and is rescheduled straight from the FSRS post-lapse memory
state, matching Anki's FSRS behavior. One exception preserves the
"practiced soon" promise of demotion (Goal 70): when a due `Again` both
demotes the rung AND the relearning list is empty, the newly demoted rung's
first review is capped at one day (`capDemotedRungFirstReview`), mirroring
the promotion first-review cap. A non-demoting `Again` with empty relearning
steps keeps the pure FSRS post-lapse interval unchanged.

When a promotion fires, the newly promoted rung's first review is capped at
one third of `ladder_promotion_interval_days` (7 days at the default 21) so
the new skill is validated sooner than a full promotion-sized interval.

## Ingestion And Admission Notes

Queue admission and evidence-based seeding are owned by `StudyQueueSeeder` and
the pure `AdmissionEvidencePolicy`. Core principle: do not make the learner
study kanji they do not need to.

- **Evidence-based seeding.** A kanji supported by at least one mature active
  Anki card and never suspended (`AdmissionEvidencePolicy.isAlreadyReadInContext`)
  is seeded straight into the `review` phase at the highest enabled rung, due
  now, with stability/difficulty derived from Anki's own memory state. It is
  validated once and then rides a real FSRS interval instead of climbing the
  whole ladder. Everything else keeps the conservative `new_learning` start at
  `kanji_meaning` (the nearest enabled rung if that is disabled). New cards
  still start at `kanji_meaning`.
- **Admission gate.** A row whose `matureSupportCount` already meets
  `matureSupportThreshold` (default 2) is never admitted (it is already
  repaired), unless per-kanji repair evidence is `REGRESSING`. This removes the
  old admit-then-retire loop.
- **Ceiling parking.** An item at the highest enabled rung in `review` phase
  whose scheduled interval grows past `CEILING_PARK_INTERVAL_MULTIPLIER`
  (default 4) times the promotion threshold is "parked": it stays studyable when
  due but no longer counts against `activeQueueCap`, so a mature suspended-only
  kanji riding a long interval can never permanently block admission of new
  repairs.
- **Signature reshuffle.** A suspend/unsuspend flip in Anki can change which
  example is preferred and thus the answer signature. When only the
  expression/reading changes but the meaning is unchanged, the seeder preserves
  all earned scheduler state and just adopts the new signature. A genuine
  meaning change still resets and demotes one rung.

Import and default notes: weak-card import (active leeches: FSRS difficulty
`>= 7.5` or lapses `>= 3`) is on by default; the default new-card sort is
`balanced_priority`; unknown-Jiten-rank suspended kanji import by default and
sort last; `active_queue_cap` is user-editable (Deck limits panel, bounded
8-200); ladder thresholds are bounded above (promotion days `<= 365`, fail
streak `<= 30`).

Graduation derives the initial FSRS memory state differently for new-learning
vs relearning, and both paths are intentional:

- **New-learning graduation** uses `engine.initialState(graduationRating)`
  alone (`LatestFsrsAdapter.initialReview` with `isNewLearning = true`),
  independent of any intermediate `Again`/`Hard` answers taken during the
  learning steps. Learning-step answers are practice-only and do not feed
  short-term stability, so the card graduates as if answered fresh at the
  graduating rating.
- **Relearning graduation** (`isNewLearning = false`) keeps the post-lapse
  stability produced by the lapse's `review(AGAIN, …)` and applies
  `engine.nextDifficulty(graduationRating)` once more on top of it. This means
  a lapse followed by relearning graduation moves difficulty by the lapse's
  update *and* the graduating rating's update. This is a deliberate deviation
  from strict "no memory change on relearning-step answers" parity: the extra
  difficulty step reflects that the card was just relapsed. `ReviewTransitionEngine`
  intentionally graduates relearning cards this way; `RelearningGraduationDifficultyTest`
  pins the double-update value.

Do not change either path (e.g. route learning answers through short-term
stability, or drop the relearning second `nextDifficulty`) without a deliberate
parity decision and golden-timeline regeneration.

`hard`/`good`/`easy` all count as a ladder-streak pass and `again` as a fail.
A `write_kanji` remediation judged `CLOSE` submits `hard`, which still passes.
Leaving the `write_kanji` rung additionally requires clean-write evidence
(Goal 67): promotion off `write_kanji` fires only when `writingLevel >= 2`
(two net clean, hint-free passes; `writingLevel` rises only on clean hint-free
passes and falls on failures via `updateWritingLevel`). A chain of messy
`CLOSE`/"Save hard" passes therefore cannot promote production out of the
writing rung without at least one clean write, even once the interval and
min-pass gates are met. The blocked non-move records
`promotion_blocked_writing_level` in the trace. `updateWritingLevel` runs
before the ladder transition so the gate sees the current attempt; this
reorder is behavior-neutral for every non-writing rung.
The demotion fail-streak resets only when a demotion actually moves the rung;
at the `write_kanji` floor (where demotion cannot move) the streak keeps
accumulating so chronically-failing floor cards keep reporting to
`LadderHealthPolicy`.

The scheduler core keeps all four ratings (`again`, `hard`, `good`, `easy`).
For ladder-streak counting, `hard`, `good`, and `easy` all count as a pass;
only `again` counts as a fail.

The top rung (`word_reading`) switches the tested dimension from meaning to
pronunciation. This is deliberate: it is the contextual exit check — a card
proves it can be read in a real word before leaving active ladder practice.
A reading lapse deliberately demotes back through the meaning rungs because
Kani remediates recognition, not readings, and true retirement remains
Anki-evidence-driven (retirement fires on Anki-side `matureSupportCount`, not
the ladder ceiling). A reading-focused rung or failure-dimension tracking is
an explicit non-goal.

Study UI renders one current rung at a time. Rung rendering:

- `write_kanji` → handwriting pad and writing evaluation.
- `type_meaning` → typed answer box.
- `similar_kanji` → multiple-choice selector from visually similar kanji.
- `reading_kanji` → multiple-choice selector of same-reading kanji (reading +
  blanked word + meaning gloss); ≥ 3 choices required, else a plain flashcard.
- `meaning_kanji` → multiple-choice selector from four local kanji.
- `kanji_meaning` → standard recognition card.
- `font_meaning` → recognition card with font variation.
- `kanji_reading` → multiple-choice selector of kana readings ("How is
  〈kanji〉 read in 〈word〉?"); falls back to a plain flashcard when fewer than
  two choices can be built.
- `word_reading` → reading prompt.
- `sentence_reading` → flashcard front is the mined sentence (target word
  emphasized; small font); back is the word reading + word + meaning. Self-graded
  Pass/Fail. Falls back to the plain word when no sentence example exists.

The study UI exposes `Pass` and `Fail` labels. In the core scheduler the
wire format stays `good`/`again`/`hard`/`easy`; the UI translates
`Pass` → `good` and `Fail` → `again` at the boundary. The `write_kanji` rung
offers only `Pass` and `Fail`; `Hard` and `Easy` are never offered as
user-selectable ratings for that rung. One documented exception: when the
writing evaluation judges an ink attempt as `CLOSE` (passed, but not clean),
the single primary action is labeled `Save hard` and submits `hard`, which
still counts as a pass for ladder-streak purposes. The user never chooses
between multiple ratings on `write_kanji`.

The study subsystem must never keep a parallel "main study item plus side
task queue" model. `learning_repeats`, `similar_kanji_choice_state`, and
`similar_kanji_repair_queue` are not scheduler queues. The scheduler
consumes `study_items` only. `similar_kanji_pairs` is retained as the data
source for `hasSimilarKanji(row)`, not as a queue.

Legacy field mapping used by the DB v16 migration (fresh start):

- `writing_remediation_pending = 1` → rung `write_kanji`
- `recognition_stage = -1` → rung `type_meaning`
- `recognition_stage = 0` → rung `kanji_meaning` (new-card default)
- `recognition_stage = 1` → rung `font_meaning`
- `recognition_stage = 2` → rung `word_reading`

The `similar_kanji`, `kanji_reading`, `reading_kanji`, and `sentence_reading`
rungs have no legacy source; they are reached through configured ladder movement
when `hasSimilarKanji` / `hasKanjiReading` / `hasReadingKanji` /
`hasSentenceReading` is true.

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

Automatic database backups remain WAL-safe gzip snapshots with the tiered
7-daily/4-weekly retention policy. Settings > Automation > Backup & restore
can export a fresh snapshot through Android's document picker and can validate
and stage a whole-file restore. A staged restore is applied on the next process
start before ordinary components open the database, after first taking a
pre-restore safety snapshot and before deleting stale WAL/SHM sidecars.

The home-screen widget follows the same `ReminderEligibilityPolicy` filter as
notifications (D-S6): its due count cannot advertise work that the Study route
will reject. Widget refresh is event-driven after committed sync, completed
study, daily reminder evaluation, and system widget updates; it has no separate
periodic worker.

## What Was Tested For v0.3.6

The `_id is unknown` / `queue _id is unknown` fix was validated with:

- Real AnkiDroid `2.24.0` installed in an Android emulator.
- The user's desktop Anki collection copied into the emulator.
- The AnkiDroid provider authority `com.ichi2.anki.flashcards`.
- The app permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` granted to
  `dev.bee.kanjianki`.
- Full Android instrumentation with `kanjiLiveAnkiDroid=true`.
- The actual app button path: tap `Sync AnkiDroid`, confirm
  `Sync cards`, then wait for a successful sync row and non-empty
  dashboard/study queue.
- Local production gate: JVM tests, Android test compilation, lint, and signed
  release APK assembly.
- GitHub Actions release workflow for tag `v0.3.6`.

Passing results for v0.3.6:

- Live emulator instrumentation: `OK (20 tests)`.
- Local production gate: `BUILD SUCCESSFUL`.
- Release APK metadata: `dev.bee.kanjianki`, versionName `0.3.6`, versionCode
  `3006`.
- Release APK signature: verified with APK Signature Scheme v2.
- GitHub Actions release run: success.

## Latest Provider Gate For Goals 82–95

The Goal 94 write-back batch was revalidated on 2026-07-10 against the same
real AnkiDroid 2.24.0 provider and copied user collection, with the default
7,000-note threshold. The targeted command below completed `OK (61 tests)`:
one foreground sync-button test, 58 fake-provider contract tests, and two real
provider tests. The non-destructive card update probe wrote the card's existing
queue value back to its own card URI; AnkiDroid rejected it with
`IllegalArgumentException` (`updatedRows=-1`) and the reread queue remained `2`.
This confirms that Kani's supported write surface remains note tags only.

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
   adb push "/home/bee/.local/share/Anki2/User 1/collection.anki2" \
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
OK (61 tests)
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
patch version, builds and verifies the signed APK, and publishes the release
with no further gating. Watch it with:

```sh
gh run list --repo bee-san/kanji_anki --workflow android-release.yml --limit 5
gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status
```

For a deliberate (non-patch or re-cut) version, use the manual flow:

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

If you create a GitHub Release directly in the UI, publishing that release also
triggers this same workflow automatically.

6. Fetch the release asset URLs.

   ```sh
   gh release view vX.Y.Z --repo bee-san/kanji_anki \
     --json url,tagName,name,assets,isDraft,isPrerelease,publishedAt
   ```

Do not cut a release for provider/sync changes unless the live AnkiDroid
instrumentation suite and local production gate both pass.
