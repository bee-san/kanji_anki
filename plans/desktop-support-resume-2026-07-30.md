# Desktop support resume checkpoint (2026-07-30)

> **Status:** Superseded by `desktop-support-resume-2026-07-31.md` (Goals
> 185–186 progress recorded there). Kept for its Goal 179 CI-triage detail.
> Supersedes `desktop-support-resume-2026-07-29.md`.
> Written when the prior Codex session (`019fae69-0ea4-70a2-9933-94b0c74343b9`)
> died on context overflow mid-Goal-180, not on a task failure.

## Objective

Complete the desktop roadmap through Goal 205 in
`plans/desktop-support-goals-2026-07-26.md`.

## Progress (session 2026-07-30, resumed from Claude)

Goals 179, 180, 181, and 182 are **done** — implemented, tested at 100%
`:data-sql` class coverage, and their completion evidence recorded in the
roadmap. Local commits on `desktop/integration` (not yet pushed):

```text
5c3b8465 data: port settings and Home repositories to shared SQL
512cd7c4 test: add cross-driver read and settings conformance
51e14085 docs: record Goal 179 and Goal 180 completion evidence
6b5b1fbe data: port Study queue reads and token-first review writes
d6dabc93 test: conform and fault-inject the Study transaction across drivers
9ff5a90b docs: record Goal 181 completion evidence
7a81ec15 data: port sync publication, history, and write-back to shared SQL
7fa78605 test: prove sync publication parity and rollback across drivers
92343983 docs: record Goal 182 completion evidence
```

The shared conformance harness lives in `:data-api` testFixtures
(`RepositoryConformanceSuite`, `StudyRepositoryConformanceSuite`,
`SyncRepositoryConformanceSuite`) and runs from one fixture against both the
legacy Android `LocalStore` repositories (Robolectric) and the shared
`:data-sql` repositories (bundled SQLite). Fault-injection tests
(`SqlStudyReviewFaultInjectionTest`, `SqlSyncPublicationFaultInjectionTest`)
prove transaction atomicity/rollback.

### Goal 183 — substantially done; two pieces remain

All work through Goal 182 is pushed to `origin/desktop/support`, plus the Goal
183 pieces below. User decisions taken 2026-07-31: proceed with the stats port
(dependency-free), and build `:reference-assets` infra with placeholder assets.

**Done for Goal 183:**
- `:reference-assets` module (manifest + streaming SHA-256 verifier +
  extract/upgrade/reuse cache policy + platform-neutral loader), placeholder
  hashes, 100% class coverage. Commit `b5131e34`.
- Stats/analytics repository ported to `:data-sql` via a new dependency-free
  `KaniJson` in `:core` (Double support). `SqlStatsData` + `SqlKanjiImpactReport`
  + `SqlStatsCodec` + `SqlStatsRepository`. Commits `8555fe2b`, `4e13aa04`.
- **All five data-api repository contracts (Home, Settings, Study, Sync, Stats)
  now have `:data-sql` implementations** with cross-driver conformance suites in
  `:data-api` testFixtures.

**Missing Kanji persistence — DONE** (commits `5a8036c0`, `8e97803e`): DTOs
promoted to `:core`, `MissingKanjiRepository` contract in `:data-api`,
`SqlMissingKanjiRepository` + `SqliteMissingKanjiRepository`, cross-driver
conformance suite. All six repository surfaces (Home, Settings, Study, Sync,
Stats, MissingKanji) are now in `:data-sql`; no repository operation remains
`LocalStore`-only.

**Remaining for Goal 183 (the ONLY open item):**
- **Real licensed reference-asset binaries** (dictionary/rank/stroke/font) must
  replace the placeholder hashes in `ReferenceAssetManifest.bundled()`, and both
  hosts must be wired to load through `:reference-assets`. Blocked on the user
  supplying binaries + licensing.

**Goal 184 (switch Android production to shared SQL) — prerequisites built;
the flip itself is blocked by an architectural constraint + the live gate.**

Prerequisite done: `SqlSourceBindingStore` (commit `b1e4f84c`) ports the last
settings-table writer that was LocalStore-only. `:data-android`'s
`AndroidFrameworkSqlDriver` (Goal 179) already exists.

Why the flip can't be a partial repo-by-repo switch (verified in code):
"Do not dual-write in production" (roadmap line 1601) requires ONE owner per
table. `AndroidKaniContainer` exposes `localStore` directly to ~26 files, and
several still WRITE tables the shared repos also write:
- `FsrsFitWorker.commitFsrsFitOutcome` → `settings` (FSRS weights) — same table
  as `SqlSettingsRepository`.
- `MainActivityStartup.saveAppThemeChoice` → `settings`.
- `MainActivityMissingKanji.publishInventory` → `settings` + `study_items`.
- backup/restore (`DatabaseBackupWorker`, `StagedRestoreApplier`) → whole DB.
So flipping the five repos to a separate `SqlDatabase` while these keep writing
via `LocalStore` on the same file = dual-write of `settings`/`study_items` +
two connection pools/WAL owners. Forbidden.

To flip safely, ALL writers of every shared table must move to the shared layer
first: FSRS-fit, theme choice, missing-kanji publish, backup/restore, plus the
sync/auto-sync composition and widget reads. That spans work the roadmap
schedules later (backup core is Goal 185). Then:
1. `AndroidKaniContainer` owns one shared `SqlDatabase`
   (`DedicatedWriterSqlDatabase(AndroidFrameworkSqlDriver("<path>/kanji_anki_simple.db"))`
   + `SchemaManager.initialize`), constructs every repository from `:data-sql`,
   and keeps `LocalStore` only as a test-only oracle.
2. Validate: fresh install, every migration corpus fixture, downgrade, WAL,
   backup/restore instrumentation, process recreation, `ciFast ciQuality`, and
   the **strict real-collection AnkiDroid gate** (user's ~7,000-note collection
   on a throwaway emulator). Do NOT ship the switch without it (CLAUDE.md).

Needs from the user: (a) Goal 183's licensed asset binaries; (b) go-ahead +
collection setup to run the live gate; (c) confirmation to undertake the
multi-goal writer-porting the safe flip requires (or a decision to reorder so
backup/FSRS/theme port before the flip).

NOTE: `:data-android:jacocoDebugUnitTestCoverageVerification` fails at 0.82 in a
plain JVM `check` (its framework driver needs `androidTest`); this is
pre-existing and unrelated to this session (module untouched since `26965ba2`).

## Workspace

- Worktree: `/local/home/skerraut/work/kani-desktop-integration`
- Local branch: `desktop/integration` (HEAD `26965ba2`)
- Push target: `origin/desktop/support` (in sync with HEAD)
- Draft PR: `#592`
- **Do not use `/local/home/skerraut/kanji_anki`.** It is stale and carries an
  unrelated uncommitted edit to `MainActivityInstrumentedTest.kt`.
- Android SDK for this worktree: `ANDROID_HOME=ANDROID_SDK_ROOT=/home/skerraut/android-sdk`
  (note: *not* the `/tmp/android-sdk` path in the root `CLAUDE.md`).

## Goal 179 — implemented, pushed, evidence not yet recorded

Commits (all on `origin/desktop/support`):

```text
5fb612e8 data: add the shared SQL driver contract
3ec50a59 data: implement dedicated-writer transaction semantics
8d0ff22d data: port schema and migrations through SchemaManager
17367853 test: prove Android and desktop SQLite driver parity
bba1ca7c test: include shared SQL in desktop gate contract
c52ea66f test: close shared SQL class coverage
26965ba2 fix: preserve Android read snapshot semantics
```

Read-snapshot correction in `26965ba2`:

- Explicit `SqlConnectionMode.READ_WRITE` / `READ_ONLY`.
- Dedicated writer is read-write; snapshots are read-only.
- Android read-only connections use `OPEN_READONLY`.
- API 35 uses `beginTransactionReadOnly()`.
- API 26-34 use comment-prefixed public query SQL so Android does not rewrite
  `BEGIN DEFERRED` into `BEGIN EXCLUSIVE`.
- Bundled SQLite uses `PRAGMA query_only=ON`.
- Shared tests prove read-only write rejection and that WAL snapshots do not
  block writers. Robolectric covers API 26, 34, 35.

Local gate passed before push — `BUILD SUCCESSFUL` in 4m33s, 391 tasks:

```sh
ANDROID_HOME=/home/skerraut/android-sdk ANDROID_SDK_ROOT=/home/skerraut/android-sdk \
  ./gradlew ciFast ciQuality ciDesktop sonarPreflight \
  --no-daemon --dependency-verification=strict --console=plain
```

### Hosted results at `26965ba2` (all four settled)

| Workflow | Run | Result |
| --- | --- | --- |
| Android CI | `30530270283` | success |
| SonarQube | `30530270291` | success |
| Desktop CI | `30530270419` | **failure** (Windows only) |
| Android device smoke | `30530270409` | **failure** (all 3 emulator lanes) |

Neither red check is Goal 179 implementation evidence; both need triage before
Goal 179 completion evidence is written into
`plans/desktop-support-goals-2026-07-26.md` (evidence sections follow the
pattern at line ~3770, `### Goal 178 completion evidence (2026-07-29)`).

## Two open CI failures

### 1. Desktop CI — Windows `:build-logic:test` (recurring)

```text
AndroidLibraryConventionsFunctionalTest >
  composeLibraryConventionCompilesLintsTestsAndPublishesCoverage FAILED
27 tests completed, 1 failed
> Task :build-logic:test FAILED   BUILD FAILED in 11m 11s
```

Linux and macOS pass. Same lane also failed at `8914ad13`, where MSI packaging
and the installed-image smoke had already succeeded — i.e. a build-logic
functional-test timeout on the Windows runner, downstream of the packaging that
actually matters. Confirm whether it is a genuine timeout or a real convention
regression before writing it off.

### 2. Android device smoke — ROOT-CAUSED AND FIXED (2026-07-30)

**Root cause.** Goal 170 (`a13594bb`, "move Android dependency lifetime out of the
activity") moved `LocalStore` ownership from each activity into the process container:
`MainActivityBase.attachProcessDependencies` now does `store = container.localStore`,
and `AndroidKaniContainer` creates that store eagerly in `KaniApplication.onCreate`.
Instrumented tests still reset state with `context.deleteDatabase("kanji_anki_simple.db")`
in `@Before`/`@After`. Previously each activity opened a fresh helper, so the next test
always got one that ran `onCreate` against the new file. A process-cached
`SQLiteOpenHelper` instead keeps its connection pool across the deletion, producing two
observable failure modes on real devices:

- the pool reopens the unlinked path as an *empty* database without rerunning
  `onCreate` → `no such table: kanji_mnemonic_notes` / `no such table: sync_runs`;
- or it keeps serving the unlinked inode → **stale rows survive deletion** (what API 26
  actually does).

Both produce the observed Compose timeouts, because routes await state that never
arrives. This was never a Goal 179 defect: Goal 179 is additive to `:data-sql` and does
not touch app persistence.

**Corrected bisect.** The earlier note below (green at `778b5e1e`) was wrong — those green
runs were on other branches. On `desktop/support` the identical signature first appears at
`ed2eb13f` (2026-07-27), inside the Goal 170/171 repository-routing range
`291c66e7..ed2eb13f`, and `291c66e7` was the last green run on this branch.

**Fix.**

- `LocalStore.resetForTestDatabaseReplacement()` — closes the helper (so the next
  `getWritableDatabase` reopens and recreates the schema) and drops cached projections
  and settings snapshots. `SQLiteOpenHelper.close()` is idempotent and permits reopen, so
  the container keeps handing out the same instance.
- `LocalStoreInventory.clearAllProjectionCachesForTest()` — clears every in-memory
  projection. Production invalidation stays targeted.
- `KaniTestDatabase.delete(context)` (debug source set) — detaches, deletes, detaches
  again, so neither a lingering nor a lazily reopened connection holds the stale file.
- All 29 instrumented test files now call it instead of `deleteDatabase` directly; the
  orphaned `DATABASE_NAME` constants and three emptied companion objects were removed.

**Evidence.** New `ProcessStoreDatabaseResetInstrumentedTest` (2 tests) pins the contract.
On a real API 26 emulator it passed `OK (2 tests)` with the fix, and with the detach
temporarily disabled it failed `expected:<[]> but was:<[before deletion]>` — so the test
genuinely catches the regression rather than passing vacuously.

### 2b. Original (superseded) note on the device-smoke failure

Failures at `26965ba2`:

```text
MainActivityStudyRouteSmokeInstrumentedTest >
  flashcardAndWritingRoutesRenderProductionComposeScreens FAILED
  RepositoryOperationException: load study mnemonic failed (permanent)
  Caused by: SQLiteException: no such table: kanji_mnemonic_notes
    while compiling: SELECT note FROM kanji_mnemonic_notes WHERE kanji=? LIMIT 1

MainActivityStudyRouteSmokeInstrumentedTest >
  pendingAnswerRestartRendersLatestLocalMnemonic FAILED
  AssertionError: Semantic Node has no parent layout with a Scroll SemanticsAction

BrowseStudyQueueInstrumentedTest > suspendedScopeSurvivesDetailBackAndReactivation
MainActivityPrimaryRouteSmokeInstrumentedTest > primaryRoutesRenderProductionComposeScreens
  ComposeTimeoutException: Condition still not satisfied after 30000 ms
```

The same run set at `8914ad13` also produced `no such table: sync_runs`. So the
missing-table class of failure spans more than one table.

Bisect boundary — device smoke was **green at `778b5e1e`** and has failed since:

```text
778b5e1e success   (last green)
420e1e92 cancelled (Merge origin/main into desktop/integration)
8914ad13 failure   (test: make schema corpus generation portable)
26965ba2 failure
```

`8914ad13` itself only touched `tools/test_schema_corpus.py` and a fixture
README, so the likely culprit is the `origin/main` merge at `420e1e92` (which
touched Compose Settings/Study UI, plausibly explaining the Scroll-semantics
and Compose-timeout failures) interacting with the Goal 179 schema work — not
the corpus-portability commit. Verify rather than assume.

Relevant table-creation sites:

- `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreTableCreator.kt:88`
  (`TABLE_KANJI_MNEMONIC_NOTES`)
- `data-sql/src/main/kotlin/dev/bee/kanjianki/data/sql/SchemaMigrations.kt:56`
  (`32 -> createTables(session, "kanji_mnemonic_notes")`)
- `data-sql/src/main/resources/dev/bee/kanjianki/data/sql/schema-v34.sql:10`

## Goal 180 — in progress, uncommitted

Plan section: `plans/desktop-support-goals-2026-07-26.md:1456`. Done when both
implementations return identical typed snapshots and settings round-trips.
Planned commits: `data: port settings and Home repositories to shared SQL`,
then `test: add cross-driver read and settings conformance`.

### Uncommitted tree (verified `BUILD SUCCESSFUL` on `:core:compileKotlin :data-sql:compileKotlin`)

Modified:

- `core/.../AdaptiveRoutingCodec.kt` (+42) — adds `StringListJsonCodec`, a
  dependency-free JSON codec for small persisted string arrays. `:data-sql` has
  no kotlinx-serialization/Jackson/Gson dependency, hence the hand-rolled codec.

New, untracked, under `data-sql/src/main/kotlin/dev/bee/kanjianki/data/sql/`:

```text
SqlRepositorySupport.kt    96 lines   safeSqlStoreCall, row accessors, NamedSql
SqlSettingsRepository.kt  507 lines
SqlHomeData.kt            852 lines
SqlHomeRepository.kt      181 lines
SqlStudyItemMapper.kt     145 lines
```

Design decisions already made in that code:

- Shared read layer executes each Home aggregate inside **one** database
  snapshot, reconstructing identical bounded projections: 120 dashboard rows,
  8 examples per kanji, 300 browse rows, exact-match restoration, manual-source
  merge, conditional-rung flags, timeline ordering.
- Writes are small atomic transactions with post-commit projection
  invalidation; `stats_source_version` updates in the same commit as the
  Android implementation does.
- `SyncSettings`' legacy repair behavior is preserved; settings read one
  immutable snapshot; write command families go in one SQL transaction.
- Safe-call mapping: `SqlBusyException` transient, other SQL/closed failures
  permanent.
- Only `SqlDatabase`/`SqlSession` are used — no Android imports.

### Remaining Goal 180 work

1. Cross-implementation conformance fixtures: seed identical state and run the
   same command sequence against legacy Android `LocalStore` and shared SQL
   (bundled SQLite via `data-sql/src/test/.../BundledTestSqlDriver.kt`).
   Reference contract test: `app/src/test/.../RepositoryAdaptersTest.kt`.
2. Preserve defaults, malformed-value fail-open, cache invalidation timing,
   ordering, paging.
3. **Do not switch Android runtime composition** — production stays on
   `LocalStore` until Goal 184.
4. Run the aggregate gate, commit as the two planned commits, push, record
   evidence.

## Remaining roadmap after 180

181 Study persistence -> 182 sync publication/history -> 183 remaining
persistence/reference assets -> 184 switch Android production to shared SQL ->
185 backup core -> 186 desktop profile storage -> 187-191 AnkiConnect
transport/reads/writes/equivalence -> 192-199 shared presentation and Android
host -> 200 desktop composition root -> 201 reminders/tray -> 202 update
handoff -> 203 Study keybindings -> 204 native distributions -> 205 CI and
live integration coverage.

## Why the prior session ended

Five consecutive turns failed with
`prompt tokens (281926) exceed customer model maximum (278528)`. The session
had already auto-compacted 18 times. No work was lost beyond what is listed as
uncommitted above; consider committing Goal 180 in smaller slices.
