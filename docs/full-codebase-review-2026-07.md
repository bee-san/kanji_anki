# Full Codebase + UI Review (July 2026)

> Historical findings snapshot. Status labels below describe the accompanying
> review change set, not the current repository. DB31 supersedes the ladder
> findings and resolves new-repair side-queue routing by storing repair inline;
> `similar_kanji_repair_queue` is now drain-only. It also adds revision-CAS
> review persistence, adaptive settings/stats, successful-run-only sync
> history. Later work added bounded backup/restore, and the 2026-07-13 safety
> audit removed its unsafe API 26–29 checkpoint-copy and non-atomic move
> fallbacks. See
> [`adaptive-two-core-scheduler.md`](adaptive-two-core-scheduler.md).

Six parallel deep reviews were run against the whole repository: ladder state
machine, FSRS integration, AnkiDroid provider sync, DB migrations/persistence,
architecture drift, and a complete UI review. This document records every
finding, its severity, and whether it was fixed in the accompanying PR or
deferred with rationale.

Legend: **FIXED** (in this PR) / **DOCUMENTED** (spec reconciled) /
**DEFERRED** (real finding, follow-up recommended).

## 1. AnkiDroid provider sync

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 1.1 | critical | Batch card reads query the top-level `/cards` URI, which does not exist before AnkiDroid 2.24.0; the per-note fallback was removed in the batching change, so every sync fails on AnkiDroid ≤ 2.23.x and is misreported as retryable. | **FIXED**: `AnkiDroidCardReader` detects the unsupported-URI error and falls back to per-note `notes/<id>/cards` reads for the rest of the run. New fake-provider flag `legacyTopLevelCardsUnsupported` + instrumentation test cover it. |
| 1.2 | major | The first FSRS projection requests columns (`fsrs_retrievability`, bare `stability`/`difficulty`/`retrievability`, `data`) that no real AnkiDroid supports, so FSRS memory state was silently never imported from a real install (real 2.24.0 supports `fsrs_stability`/`fsrs_difficulty`). | **FIXED**: added `CARD_COLUMNS_WITH_PROVIDER_FSRS` (exactly the 2.24.0-supported FSRS columns) between the wishlist and the scheduler fallback. |
| 1.3 | major | A failure after `saveSuccessfulSync` (provider tagging or study-item seeding) left a committed sync mirror alongside stale `study_items`, wrote a contradictory failed-sync row, and suppressed auto-sync for the day. | **FIXED**: `ManualSyncEngine` now persists study items before provider tagging, and a tagging failure degrades to a removal-message warning on a successful sync instead of a failed sync. |
| 1.4 | major | Real AnkiDroid returns a **null cursor** for a valid browser query matching zero notes; that was classified as a permanent "could not run the browser query" failure. | **FIXED**: null browser-query cursor is treated as zero matches. Instrumentation test updated. |
| 1.5 | major | The projection-fallback `catch (Exception)` conflated transient errors (locked DB, permission revocation, process death) with unsupported-column errors, silently degrading imported data quality to `CARD_COLUMNS_MINIMAL` (fabricated queue/type/due). | **FIXED**: only `IllegalArgumentException`/`UnsupportedOperationException` advance the projection; `SecurityException` is rethrown (classified permanent by the gateway); other exceptions fail as retryable without degrading. |
| 1.6 | major | Deferred cursor-time errors in `querySuspendedNoteIds` escaped the tolerance guard (only the `query()` call was wrapped), so a window-fill SQLite error failed the whole sync despite the explicit design that suspended-search unavailability is tolerated. | **FIXED**: cursor iteration moved inside the guard. |
| 1.7 | minor | `tagNoteArchived` read-modify-write can clobber pre-existing note tags when the tags read yields no row, and the archive loop has no per-note error isolation. | **DEFERRED**: low exposure on real AnkiDroid (`NOTES_ID` always returns a cursor); blast radius reduced by 1.3. |
| 1.8 | minor | No retry/backoff for retryable failures; auto-sync's JobService finishes with `needsReschedule=false`, losing the day's auto-sync on a transient lock. | **DEFERRED**: needs a deliberate retry-policy design. |
| 1.9 | minor | Auto-sync opens a second `SQLiteOpenHelper` connection to the same DB as the activity store (no WAL), allowing cross-connection lock errors. | **DEFERRED**: singleton-store/WAL refactor. |
| 1.10 | info | Dead authority `com.ichi2.anki.api.provider` checked first in provider resolution. | **DEFERRED** (harmless). |
| 1.11 | info | `catch (Throwable)` in `readCollection`/`ManualSyncEngine` converts `Error`s (OOM) into retryable failures. | **DEFERRED**. |

## 2. FSRS integration (fsrs-java + adapter)

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 2.1 | major | Same-day (`elapsedDays == 0`) `Again` on a review card used forget stability; pinned upstream py-fsrs v6.3.1 routes **all** same-day reviews through short-term stability (~2x stability difference). Reachable via study-ahead and 1-day intervals. | **FIXED**: branch order corrected in `DefaultFsrsEngine.nextState`; regression test added. |
| 2.2 | major | Learning-step history is discarded: FSRS state is seeded from the graduation rating alone, so a card that failed five times graduates with the same stability as a clean pass. | **DEFERRED**: deliberate simplification aligned with the "practice-only learning repeats" model; changing it alters every graduation interval and needs product sign-off. |
| 2.3 | minor | Relearning-phase `Again` with empty relearning steps hard-coded a 1-day interval with no FSRS call (spec: reschedule from the FSRS post-lapse memory state). | **FIXED**: the empty-steps transition now always graduates through the FSRS adapter. |
| 2.4 | minor | `Hard` with a stale step index past the configured steps (settings shrank mid-learning) repeated a clamped step forever; upstream graduates. | **FIXED** + regression tests (`hardWithStaleStepIndexPastConfiguredStepsGraduates`). |
| 2.5 | minor | `nextIntervalDays` wrapped `Long → Int` for extreme stabilities, collapsing to a 1-day interval instead of clamping at `maximumInterval`. | **FIXED**: clamp in `Long` before narrowing; test added. |
| 2.6 | minor | Learning `Hard` first-step midpoint has an extra `max(step0, avg)` clamp not in upstream (only observable with descending step configs). | **DEFERRED**: intentional monotonicity guard; only differs for pathological configs. |
| 2.7 | info | Interval fuzz is not implemented (upstream default ±5–15%). | **DEFERRED**: deterministic scheduling keeps ladder promotion deterministic; deliberate. |
| 2.8 | info | The `upstream-reference-cases.json` fixture is self-generated (circular oracle) and its generator script is missing from the repo; it contained no same-day-Again case, which is why 2.1 survived. | **DEFERRED**: regenerate by executing py-fsrs v6.3.1 directly. |
| 2.9 | info | Persisted stability/difficulty rounded to 2 decimals per review; elapsed days reconstructed from `due − interval` instead of a persisted last-review timestamp. | **DEFERRED**: consistent today; fragile against future due-time adjustments. |

## 3. Ladder state machine

The ladder core verified correct against the spec: rung order, enable/disable
normalization, `meaning_kanji` auto-enable, nearest-enabled-rung start with
lower-rung tie-break, similar-kanji gating in both directions incl. chained
skips, practice-only learning repeats, persisted-due real-review boundary,
strictly-greater promotion threshold, 1/3 promotion cap, streak semantics,
floor/ceiling, and study_items-only scheduling.

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 3.1 | minor | Promotion traces emitted `similar_kanji_unavailable` for promotions that never crossed the similar rung (the demotion branch already used the rank-range check). | **FIXED**: promotion branch now uses `skipsSimilarRungWithoutContent`; goldens regenerated; a negative-case trace test added. |
| 3.2 | minor | The similar-kanji "skip" regression tests never actually crossed the similar rung (adjacent moves); the real crossing behavior was untested. | **FIXED**: tests now cover promote `write_kanji → type_meaning`, demote `type_meaning → write_kanji`, chained skip across a disabled rung, and end-to-end `applyReview` promotion/demotion skips. |
| 3.3 | minor | `saveLadderThresholds` mirrored the fail-streak count into the legacy `writing_trigger_miss_days` setting — a *days* value — a latent data-corruption trap. | **FIXED**: mirror removed (the `real_due_reviews_to_move` fallback is kept); tests updated. |
| 3.4 | info | New-card starting rung computed with `hasSimilarKanji` hard-coded false at admission, so `similar_kanji` can never be a starting rung. | **DEFERRED**: acceptable (similar_kanji is per-card conditional; `alignRungToLadder` realigns at load). |
| 3.5 | info | `effectiveRung` terminal fallback returns `KANJI_MEANING` without validity check (currently unreachable). | **DEFERRED**: defensive-only. |

## 4. Database and persistence

Verified correct: complete migration chain 2→25 with correct cross-step
ordering, framework-transactional migrations, fresh-install vs migrated schema
parity, the documented v16 legacy-field mapping (a safe superset), single-value
rung/phase invariants, ladder-config upgrade-on-load, transactional sync/study
persistence.

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 4.1 | major | Daily backup copies the live DB file with no lock/quiescence and never copies WAL sidecars; a failed checkpoint only warns; torn backups possible, and no restore path exists. | **FIXED LATER**: API 30+ now uses `VACUUM INTO` only; API 26–29 cancels/disables live backup and restore rather than copying the main file. Restore publication is strict-atomic with no ordinary move fallback. |
| 4.2 | minor | No `onDowngrade` override: installing an older APK over a newer DB hard-crashes on open. | **DEFERRED**: needs an explicit downgrade policy decision. |
| 4.3 | minor | `addNullableColumn` idempotence depended on parsing the "duplicate column" error-message substring; long-jump upgrades (<7 → 25) rely on it. | **FIXED**: checks `PRAGMA table_info` first; message parse kept as a secondary guard. |
| 4.4 | minor | Corrupted/blank `study_ladder_enabled` silently re-enables all rungs. | **DEFERRED**: only reachable via corruption; consider defaults-fallback + logging. |
| 4.5 | info | No CHECK constraints on `rung`/`phase`; invalid strings coerce silently on read. No FKs (orphan windows self-heal at next sync). Thresholds have no upper bound. | **DEFERRED**. |

## 5. Architecture / design conformance

Verified upheld: core scheduler consumes `study_items` only; wire format
good/again/hard/easy stays in core; no Android imports in pure modules;
dependency graph is acyclic and app-topped; ladder thresholds single-sourced.

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 5.1 | critical* | `similar_kanji_repair_queue` acts as a due-at-ordered side queue that preempts the scheduler-produced study queue in the app rendering layer (`renderPendingRepairOrDone`). Repair outcomes are practice-only, so long-term scheduling is unaffected, but session sequencing is queue-driven — in tension with the "no parallel side-task queue" rule. | **DEFERRED**: needs a product decision — either amend the spec to carve out repair-preemption UX explicitly, or refactor repairs to render as an intro step of the owning study_items session. Flagged for maintainer. |
| 5.2 | major | `learning_repeats` retains a full scheduler-queue CRUD API (`enqueue`/`dueLearningRepeats`) with zero production callers — one call-site away from regressing into the banned model. | **DEFERRED**: delete the dead API surface in a follow-up (touches instrumentation tests only). |
| 5.3 | major | Flashcard rungs labeled buttons "Again"/"Good" while writing/choice rungs use "Pass"/"Fail" — inconsistent within the app and contrary to the spec. | **FIXED**: `StudyReviewButtonCopy` now emits Pass/Fail (EN + JA), matching `StudyTextCopy`; content descriptions and undo toasts follow; tests updated and now assert parity with `StudyTextCopy`. |
| 5.4 | major | `write_kanji` shows a third "Save hard" action (submits `hard`) for CLOSE writing analyses, contradicting the spec's "only Pass and Fail". | **DOCUMENTED**: this is a deliberate, extensively-tested evaluation outcome (not a user-chosen rating). AGENTS.md now documents the CLOSE → "Save hard" exception. |
| 5.5 | major | Rating wire strings/strength ordering defined in four places (domain `StudyRatings`, writing-core `StudyRating`, app literals, LocalStore constant); writing-core lacks a `:domain` dependency. | **DEFERRED**: module-dependency refactor. |
| 5.6 | minor | Pass/Fail copy duplicated between core and writing-core; task-type wire strings duplicated in `MainActivityBase`; `StudyStatsStore` overloads silently substitute default thresholds. Legacy Stats previously used enum order instead of the configured ladder order. | **PARTIALLY FIXED**: the production legacy Stats fallback now presents rows in normalized `study_ladder_order`; adaptive v31 health remains independent. The remaining consolidation follow-ups are deferred (a copy-parity test guards the button labels). |
| 5.7 | minor | Legacy `recognition_stage`/`writing_remediation_pending` are still dual-written on every review outside the migration and participate in undo-boundary equality. | **DEFERRED**: schema-rev follow-up. |

## 6. UI review

Verified correct: rung → component routing matches the spec exactly (one rung
rendered at a time; font variant chosen once per card outside composition);
double-submit protection via consumed tokens + composition disposal; back
navigation abandons the active task; empty/error/loading states exist; strong
accessibility on nav/top bar/choice feedback; night-palette handling in the
drawing pad; `Locale.ROOT` in core formatting.

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 6.1 | major | All study state is lost on rotation/configuration change (no saved state anywhere; recreation lands on Home; rotation during sync kills the in-flight executor silently). | **DEFERRED**: needs a state-restoration design (route + per-card interaction state). Largest UX gap found. |
| 6.2 | major | Flashcard rating buttons violated the Pass/Fail spec. | **FIXED** (see 5.3). |
| 6.3 | major | The study pipeline does synchronous SQLite work on the main thread on every card and rating (`renderStudyInternal`, `submitNormalReview`, choice-card building, theme read per route render); Home/Stats already use the async loader, study does not. | **DEFERRED**: move behind `AsyncHomeRouteLoader`-style loading; jank/ANR risk at 7k-note scale. |
| 6.4 | major | Progress analytics screen is English-only (bypasses the `localizedText` convention), uses `Locale.US` formatting, and synthesizes per-category "retention" rows from a single accuracy number (`accuracy30 + 1 / − 2 / − 12`) presented as real data. | **DEFERRED**: localization pass + honest per-task-type retention (or remove rows). |
| 6.5 | major | The handwriting pad is invisible to TalkBack (no contentDescription/semantics; escape hatches conditional). | **FIXED**: the custom canvas now exposes localized, answer-safe TalkBack/pass-through instructions and separate stroke-count state; writing guidance and evaluation feedback are polite live regions, while Erase/Undo/Check remain separate accessible controls. |
| 6.6 | major | Hardcoded English "Answer hidden until reveal" shown to Japanese users on every unrevealed flashcard. | **FIXED**: `StudyTextCopy.answerHiddenHint()` (EN + JA) + tests. |
| 6.7 | minor | `SyncProgressPanel` initial description hardcoded "Sync progress" for EN while JA derived from `SyncProgressCopy`. | **FIXED**: both locales derive from the first sync stage. |
| 6.8 | minor | Browse-detail round trip desyncs flashcard reveal state (swipe reverts to reveal mode; task-time stats skewed). | **DEFERRED**. |
| 6.9 | minor | Snapshot-state write during composition in the swipe-gesture modifier; Home scroll reset when the sync dialog toggles; rating lambdas resolve `activeSession` at invocation time; post-destroy main-handler callbacks touch the destroyed activity. | **DEFERRED**: state-management hardening batch. |
| 6.10 | minor | Choice-feedback text is fixed white on teal (~2.6:1 contrast, below WCAG 3:1); pre-Compose window background follows system night mode instead of the in-app theme. | **PARTIAL**: choice feedback now resolves a WCAG AA text color against each theme's teal/coral fill and wrong-answer status is announced politely. The pre-Compose window background remains deferred. |
| 6.11 | info | Dead label constants in `MainActivityBase`; no shared dimension tokens; `AsyncHomeRouteLoader.generation` non-volatile cross-thread read (logically safe). | **DEFERRED**. |

\* severity as assigned by the architecture reviewer; the practice-only nature
of repair outcomes keeps long-term scheduling correct, so the immediate risk
is design drift rather than data corruption.
