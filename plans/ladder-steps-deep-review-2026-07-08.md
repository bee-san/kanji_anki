# Ladder Steps Deep Review — 2026-07-08

## Asks

The requests this document answers, verbatim:

1. "Do a deep review of the ladder steps."
2. "Write suggestions to a plan."
3. "Think about the core principles of this app."
4. "Include the asks at the top of the file."
5. "Include suggestions on how to fix these with clearly defined goals."

Interpretation: audit the 7-rung ladder itself — the rung set, the default
order, the promotion/demotion mechanics, and how each rung earns its place —
against the product's stated principles; record the findings; and turn each
finding into a self-contained, machine-checkable fix goal in the house
format of `plans/deep-review-goals-2026-07-08.md`. Goal numbers continue
from 62 so cross-references stay unambiguous: "Goal 63" always means this
document.

This is a design/product review with code evidence, not a bug sweep; it
deliberately does not re-litigate what `docs/full-codebase-review-2026-07.md`
and `plans/deep-review-goals-2026-07-08.md` already verified sound.

## Review scope and method

Read in full, with line evidence cited below (line numbers as of this
review; search symbols if they drift):

- `core/.../RecordsBase.kt` (rung enum, `StudyLadderSettings`, phases)
- `core/.../ReviewTransitionEngine.kt` (all phase transitions, movement)
- `core/.../StudyLadderRules.kt`, `core/.../LadderHealthPolicy.kt`
- `core/.../LatestFsrsAdapter.kt`, `core/.../StudySessionSelector.kt`
  (priority buckets, family collapse), `core/.../StudyQueueSeeder.kt`
  (starting rung, retirement)
- `docs/ladder-and-srs-system.md` (855-line code-verified reference),
  `docs/srs.md` (historical intent), `docs/anki-parity-gap-map.md`,
  AGENTS.md scheduler notes, README product contract.

---

## Core principles (stated and implicit)

The stated principles, consolidated from README, AGENTS.md, and
`docs/ladder-and-srs-system.md` §13:

- **P1 — Companion, not replacement.** Kani repairs kanji blindness; it
  never rewrites Anki's schedule. Real repair evidence comes from AnkiDroid
  (retirement fires on Anki-side `matureSupportCount`, not on the ladder
  ceiling — `StudyQueueSeeder.shouldRetireSeedItem`).
- **P2 — Pareto repair focus.** A small queue (24 active, 3 new/day) of the
  kanji most worth studying. Time in the app should concentrate on the
  characters causing real misses.
- **P3 — Single state machine.** One rung + one phase per item;
  `study_items` is the only scheduler queue.
- **P4 — Evidence-gated movement.** Only persisted FSRS due-review answers
  move the ladder, one evidence credit per due slot
  (`countsAsRealDue`, `ReviewTransitionEngine.kt:408-414`). Practice never
  inflates progression.
- **P5 — Anki/FSRS semantics with documented deviations.**
- **P6 — Config-safe degradation.** Any stored ladder config produces a
  usable ladder.
- **P7 — Two-button simplicity.** Pass/Fail at the boundary; the engine
  keeps four ratings.
- **P8 — Auditability.** Full before/after snapshots, decision traces,
  golden timelines.

Two principles are implicit in the rung design but written down nowhere.
They explain the ladder better than the current "easier/harder" language
and should be made explicit (Goal 71):

- **P9 (implicit) — The ladder is a scaffolding gradient, not a difficulty
  gradient.** Bottom = maximum support and deliberate practice (guided
  handwriting with hints and stroke guides); top = minimum support and
  contextual use (raw word reading). Demotion adds scaffolding; promotion
  removes it. Calling `write_kanji` the "lowest/easiest" rung
  (doc §3, AGENTS.md) is misleading — production is the most demanding
  skill; it is the most *supported* rung. The current wording actively
  confuses reasoning about order changes (e.g. `effectiveRung`'s
  "ties prefer the easier rung" really means "prefer the more intensive,
  more scaffolded rung", `RecordsBase.kt:152-166`).
- **P10 (implicit) — Grading objectivity decreases as trust increases.**
  Bottom rungs grade objectively (ML-kit ink evaluation, forced-choice
  correctness, typed-answer matching); top rungs are self-graded reveal
  cards (`kanji_meaning`, `font_meaning`, `word_reading`). The ladder
  hands grading back to the learner as the card earns trust. This is a
  good design property; movement rules should not undermine it
  (Goals 63, 67).

---

## Verified sound — do not change without new evidence

- Evidence gating and per-due-slot dedupe (P4) are correctly implemented
  and pinned by `LadderSchedulerTest` and goldens.
- Skip-over behavior for the conditional `similar_kanji` rung
  (`isValidForItem` + `nextRung`/`previousRung`, `RecordsBase.kt:88-90,
  171-193`) is clean and correct.
- Learning/relearning Anki semantics (Again/Hard/Good/Easy, first-step
  midpoint, 1.5x single-step Hard, empty-steps FSRS reschedule) match
  AGENTS.md after the G7/G8 fixes.
- The promotion first-review cap (G5,
  `capPromotedRungFirstReview`, `ReviewTransitionEngine.kt:389-402`) is
  directionally right — see S2/Goal 63 for its remaining gap.
- Floor streak accumulation for `LadderHealthPolicy` reporting
  (`ReviewTransitionEngine.kt:344-353`) matches AGENTS.md (the design doc
  contradicts it — S10/Goal 73).
- `write_kanji`/relearning items in due-priority bucket 0
  (`StudySessionSelector.duePriority`, `:449-457`) is aligned with
  repair-first (P2).
- Token idempotency, undo boundary, config degradation (P6): sound.

---

## Findings

Ordered by product impact. Each finding records the problem and evidence
only; the fix lives in the referenced goal below.

### S1 (High) — The path to the app's signature remediation is far too long

Kani's thesis is repairing kanji blindness — confusing similar-looking
characters — yet the two rungs that directly treat it (`similar_kanji`
discrimination, `write_kanji` production) are nearly unreachable through
normal ladder movement. New cards start at `kanji_meaning`
(`RecordsBase.kt:31-32`, seeded at `StudyQueueSeeder.kt:325`). In the
default order (`RecordsBase.kt:310-320`) that is index 4 of
`[write_kanji, similar_kanji, type_meaning, meaning_kanji, kanji_meaning,
font_meaning, word_reading]`. Demotion moves one rung per
`ladder_demotion_fail_streak` (default 3) real-due `again`s
(`ReviewTransitionEngine.kt:337-354`). So from the starting rung a card
needs **9 real-due failures to reach `similar_kanji` and 12 to reach
`write_kanji`** (9 without similar-kanji content). Each failure must be a
separate FSRS-scheduled due review with a relearning round-trip in
between, so the calendar cost is weeks-to-months of repeated failure
before the app applies its strongest medicine. This contradicts P2: the
user's time is spent failing recognition cards instead of practicing
discrimination/production.

The product already knows fast writing access matters — similar-kanji
wrong picks enqueue a writing repair that *bypasses the scheduler
entirely* (doc §4.2). That bypass is a symptom: the ladder cannot express
"this card needs deep remediation now", so a side path was built around it
(in tension with P3).

**Fix: Goal 65 (default reorder) + Goal 66 (evidence-scaled demotion).**

### S2 (High) — One capped pass per rung promotes again: promotion cascade

Promotion fires when the FSRS result schedules strictly more than
`ladder_promotion_interval_days` (21) out (`promotesByFsrsInterval`,
`ReviewTransitionEngine.kt:404-406`), and the promoted rung inherits a
**clone of the promoting review's memory** (`updatedStudyItem`,
`:462-466`) with its first review capped at 7 days (`:389-402`). At the
0.90 retention default, interval ≈ stability, and a successful recall
always increases stability. So a card that promotes once necessarily has
cloned stability > 21; its single capped 7-day check on the new rung —
one pass — produces an interval > 21 again and promotes again. **Each
rung above the first promotion gets exactly one test, and a mature card
climbs `kanji_meaning → font_meaning → word_reading` in two passes spaced
7 days apart.** Each rung is a different skill (P9/P10); one self-graded
test per skill is thin evidence to retire that skill's practice, and it
means `font_meaning` (typeface robustness) is effectively a one-shot
checkpoint.

`realPassStreak` is the obvious gate and is already persisted,
incremented on real-due passes (`:373-386`), and reset on movement — but
it gates nothing: `LadderHealthPolicy.recordReviewEvidence` reads only
`matureIntervalDays` and `realAgainStreak`
(`LadderHealthPolicy.kt:221-231`), and its only other consumers are UI
progress display (`StudySessionProgressTracker.kt:240-241`) and
repair-evidence reporting.

**Fix: Goal 63.** Also resolves doc §14 improvement idea I5 (demotion
bounce-back) with no extra mechanism.

### S3 (High) — Promotion speed is coupled to target retention (open D4)

Already recorded as open decision D4 (doc §14): promotion keys off the
*scheduled interval*, which scales with the user's retention setting
(0.80 ≈ 3.3x stability, 0.95 ≈ 0.4x). A user who tunes retention is
silently tuning ladder progression speed — two unrelated knobs coupled
through one comparison. With Goal 63 in place the interval trigger
remains the sole memory-strength signal, so the coupling still matters.

**Fix: Goal 64 (close D4).**

### S4 (Medium) — The ladder's real axes are unnamed (scaffolding, objectivity)

Docs and code comments describe the order as "lowest/easiest →
highest/hardest" (doc §3, AGENTS.md, `RecordsBase.kt` comments). By any
skill-difficulty reading that is inverted — handwriting production is the
most demanding task on the ladder. The ladder actually encodes P9
(scaffolding depth) and P10 (grading objectivity). The wrong axis names
make order/movement discussions error-prone and will mislead future
contributors and users reading the settings screen.

**Fix: Goal 71.**

### S5 (Medium) — A card can leave `write_kanji` without one clean write

On the writing rung, a messy-but-passing attempt (`CLOSE`) submits `hard`
("Save hard"), and manual override also resolves to `hard`
(`resolveRating`, `ReviewTransitionEngine.kt:553-568`). `hard` counts as
a pass and feeds `applyReviewPass`; FSRS recall on `hard` still grows
stability, so a chain of messy CLOSE passes will eventually cross the
21-day interval and promote the card out of `write_kanji` — production
"mastered" without a single clean, hint-free write. The engine already
tracks exactly the right signal: `writingLevel` (0-3) rises only on
clean, hint-free passes (`updateWritingLevel`, `:416-425`) but today it
only seeds hint state.

**Fix: Goal 67.**

### S6 (Medium) — Chronic floor-failers accumulate forever with no intervention

At the `write_kanji` floor the fail streak deliberately keeps
accumulating so `LadderHealthPolicy` can report it
(`ReviewTransitionEngine.kt:344-353`, `demotionReady` count). But nothing
consumes that signal beyond a stats card: there is no leech-style
intervention (the unwired `StudyLeechPolicy` was deleted — G11), and
floor items sort into due-priority bucket 0
(`StudySessionSelector.kt:449-457`). A kanji that simply is not sticking
occupies one of 24 queue slots indefinitely, jumps the queue every
session, and blocks new admissions — the exact opposite of P2.

**Fix: Goal 68.**

### S7 (Medium) — `similar_kanji` predicate and renderer can disagree

Rung availability is decided by `hasSimilarKanji` (row exists in
`similar_kanji_pairs`), but the choice UI needs ≥ 2 buildable choices;
when the planner cannot build them it falls back to the flashcard
renderer *while keeping the `similar_kanji` task type* (doc §4.2). The
engine attributes memory by the item's rung
(`context.reviewedTaskType = context.rung.wireName()`,
`ReviewTransitionEngine.kt:539`), so the review is recorded against
`similar_kanji_memory` although the exercise performed was plain
recognition — discrimination evidence polluted by a different skill,
weakening P4's spirit and P10.

**Fix: Goal 69.**

### S8 (Medium) — The top rung switches the tested dimension (meaning → reading)

Rungs 1-6 all test meaning knowledge in some form; `word_reading` tests
pronunciation. As an exit exam this is coherent with P1 (contextual use
is the goal, and true retirement is Anki-driven), but the movement
semantics are odd at the seam: 3 real-due reading failures demote to
`font_meaning` — more *meaning* practice — which does not remediate a
reading failure; and under the current cascade (S2) the reading skill
gets its first test after months of meaning-only history.

**Fix: Goal 72 (document the seam; larger options recorded as non-goals).**

### S9 (Low) — Demotion loses "practice immediately" with empty relearning steps

Doc §7.1 sells demotion as "the easier skill is practiced immediately"
via the ~10-minute relearning due. With the relearning list configured
empty (allowed), a real-due `again` reschedules straight from the FSRS
post-lapse interval (`ReviewTransitionEngine.kt:321-335`), so a
third-fail demotion moves the card to the more-scaffolded rung but its
next appearance is days out. The property silently depends on a setting.

**Fix: Goal 70.**

### S10 (Low) — Design doc contradicts code on floor streak semantics

`docs/ladder-and-srs-system.md` claims the floor streak resets — §4.1
("streak resets each time the demotion threshold fires", `:221-222`) and
§7 ("the streak keeps resetting every N fails", `:458-459`) — but the
code deliberately keeps accumulating at the floor so `LadderHealthPolicy`
sees chronic failers (`ReviewTransitionEngine.kt:344-353`), and AGENTS.md
documents the accumulate behavior. The doc advertises itself as
code-verified; these two sentences are not.

**Fix: Goal 73.**

### S11 (Low) — New-learning graduation ignores learning struggle (open D2)

Open decision D2: new-learning graduation seeds FSRS from the graduating
rating alone (`LatestFsrsAdapter.kt:20-27`), so a card that needed five
`again`s in learning graduates with the same initial memory as one that
breezed through. For a generic deck this is a mild parity deviation; for
Kani it is systematically biased *against* the product: the queue is
deliberately loaded with difficult, confusable kanji (P2), exactly the
cards whose initial stability gets overestimated, inflating first
intervals and delaying the failure evidence the ladder needs
(compounding S1).

**Fix: Goal 74 (run the D2 experiment).**

### S12 (Low) — Golden timelines do not cover the movement seams

The five golden scenarios cover the basics (new-card entry, single
promotion, 3-fail demotion, similar-skip, family visibility) but none
covers the seams this review flagged: the post-cap promotion cascade
(S2), floor-streak accumulation (S6/S10), empty-relearning demotion (S9),
or retention-coupled promotion (S3).

**Fix: distributed** — every scheduler goal below ships its golden; the
validation section requires pinning current behavior for any rejected
goal.

---

## Fix plan — clearly defined goals

Work goals one at a time. Every scheduler-behavior goal here is
golden-sensitive: goldens under
`core/src/test/resources/dev/bee/kanjianki/core/scheduler-goldens/` are
produced from `SchedulerTimelineSimulator.renderText()` output and the
parity snapshot under `scheduler-parity/` is the flattened manifest —
regenerate both from simulator output, never hand-edit
(`SchedulerTimelineSimulatorTest.assertGolden`,
`SchedulerParitySnapshotTest`). Contract changes must update AGENTS.md
scheduler notes, `docs/ladder-and-srs-system.md`, and tests in the same
change set.

### Goal 63: Require minimum real-due passes on a rung before promotion

**Problem:** Finding S2 — after the 7-day promotion cap, cloned stability
above the threshold guarantees every subsequent rung promotes on its
single first pass; `realPassStreak` is persisted but gates nothing.

**Goal:** Add a minimum-passes gate to promotion, defaulting to 2:

- `core/.../RecordsBase.kt`: add
  `DEFAULT_LADDER_PROMOTION_MIN_PASSES: Int = 2` beside the existing two
  defaults (`:368-369`).
- `core/.../RecordsSyncModels.kt`: add `ladderPromotionMinPasses` to
  `Settings`, clamped ≥ 1 (1 reproduces today's behavior). Note the D5
  positional-vararg hazard: extend the constructor shapes once, in one
  place, and prefer the builder for new call sites.
- `app/.../sync/SyncSettings.kt`: new key
  `ladder_promotion_min_passes`, read in `fromStore` with the same
  int-setting pattern as `ladder_promotion_interval_days`
  (`SyncSettings.kt:74-78`). No settings UI in this goal; the threshold
  panel extension (`StudyLadderThresholdPolicy.saveRequest` currently
  takes exactly two fields) is a separate follow-up if users ask.
- `core/.../ReviewTransitionEngine.kt` `applyReviewPass` (`:373-386`):
  promotion condition becomes
  `promotesByFsrsInterval(...) && state.realPassStreak >= settings.ladderPromotionMinPasses`.
  `realPassStreak++` already runs before the check, so the first real-due
  pass on a rung yields streak 1 (no promotion at the default) and the
  second qualifying pass promotes.
- Optional (small): add a `promotion_blocked_min_passes` reason code in
  `transitionReasonCodes` when the interval qualified but the streak
  blocked, so traces explain the non-move.
- Update AGENTS.md ladder-movement contract and doc §7; close doc §14
  I5 (bounce-back) as resolved-by-63.

**Done when (machine-checkable):**

1. `LadderSchedulerTest` new cases pass via
   `./gradlew :core:test --tests "dev.bee.kanjianki.core.LadderSchedulerTest"`:
   - real-due pass with qualifying interval and `realPassStreak == 1`
     does not promote;
   - second qualifying real-due pass promotes;
   - with `ladderPromotionMinPasses = 1`, the legacy single-pass
     promotion behavior is reproduced exactly;
   - demotion bounce-back scenario: demoted formerly-mature card does
     not re-promote on its first post-demotion pass.
2. New golden timeline (suggested name
   `promotionRequiresSecondRealDuePass.timeline.txt`) shows a mature card
   no longer climbing two rungs in two reviews; the existing
   `reviewPassPromotesAfterLongFsrsInterval` golden is regenerated from
   simulator output; `SchedulerParitySnapshotTest` passes with the
   regenerated snapshot.
3. `SyncSettingsTest` (or equivalent app test) proves the key round-trips
   and clamps to ≥ 1.
4. AGENTS.md and `docs/ladder-and-srs-system.md` §7/§14 describe the
   min-pass gate in the same commit.
5. `./gradlew ciFast` exits 0.

### Goal 64: Gate promotion on retention-independent memory strength (close D4)

**Problem:** Finding S3 — `promotesByFsrsInterval` compares the
*scheduled* interval, which scales with target retention, so the
retention knob silently tunes ladder speed.

**Goal:** Compare a 0.90-equivalent interval instead:

- `core/.../KaniFsrsReviewResult.kt`: add a
  `promotionIntervalMillis` (or `promotionIntervalDays`) field computed by
  the adapter at fixed retention 0.90 —
  `engine.nextIntervalDays(state.stability, 0.90, MAXIMUM_INTERVAL_DAYS)`
  in `LatestFsrsAdapter.review`/`initialReview`. Keeping the FSRS math in
  the adapter preserves the engine/adapter seam.
- `core/.../ReviewTransitionEngine.kt`: `promotesByFsrsInterval`
  (`:404-406`) compares the new field; rename to
  `promotesByMemoryStrength` for honesty.
- Update the fixed-interval fake adapters in `LadderSchedulerTest` to
  fixed-stability fakes (the exact/just-over threshold tests pin the new
  contract).
- Docs: close D4 in doc §14, update §7 and AGENTS.md.

At the 0.90 default the decision is identical, which gives a strong
regression check: goldens must not change.

**Done when (machine-checkable):**

1. New `LadderSchedulerTest` case: identical memory states reviewed at
   target retentions 0.80 / 0.90 / 0.95 produce identical promotion
   decisions (only due dates differ).
2. Exact-threshold and just-over-threshold promotion tests updated to the
   stability-normalized contract and passing.
3. All existing goldens and the parity snapshot are **byte-identical**
   (no regeneration needed) — verified by
   `./gradlew :core:test` exiting 0 without golden edits. If any golden
   changes, the implementation is wrong (all simulator scenarios run at
   default retention).
4. AGENTS.md + doc §7/§14 updated (D4 marked closed) in the same commit.
5. `./gradlew ciFast` exits 0.

### Goal 65: Move `similar_kanji` directly below `kanji_meaning` in the default order

**Problem:** Finding S1 — discrimination practice, the app's signature
remediation, is 9 real-due failures away from the starting rung because
`similar_kanji` sits at index 1 of the default order.

**Goal:** Change `defaultsOrder()` (`RecordsBase.kt:310-320`) to:

```
write_kanji, type_meaning, meaning_kanji, similar_kanji,
kanji_meaning, font_meaning, word_reading
```

First demotion from the starting rung then lands on discrimination
practice (3 fails instead of 9) exactly for the cards that have confusion
data, and skips to `meaning_kanji` for those that do not (unchanged
destination — the skip-over rule already handles it). Scope notes:

- Stored orders are preserved (`fromStored` keeps user order); only fresh
  installs and stored configs *missing* `similar_kanji` are affected.
  `insertMissingRung` (`RecordsBase.kt:259-282`) anchors on default
  neighbors, so a stored order lacking `similar_kanji` now inserts it
  between `meaning_kanji` and `kanji_meaning` — add an explicit test.
- No migration: `rungToLegacyStage`, the v16 migration, and wire names
  are order-independent.
- Update AGENTS.md default-order list, doc §3, and the README ladder
  description if it names an order.

**Done when (machine-checkable):**

1. `StudyLadderSettingsTest` default-order assertions updated and
   passing; new cases: stored full order round-trips unchanged; stored
   order missing `similar_kanji` inserts it between `meaning_kanji` and
   `kanji_meaning`.
2. `LadderSchedulerTest` demotion-path test: starting-rung card with
   `hasSimilarKanji = true` reaches `similar_kanji` after exactly
   `ladder_demotion_fail_streak` real-due fails; with
   `hasSimilarKanji = false` it reaches `meaning_kanji` (unchanged).
3. Goldens crossing the moved rung (`similarKanjiSkippedWithoutContent`,
   `threeDueReviewAgainsDemote` if affected) regenerated from simulator
   output; parity snapshot regenerated; `./gradlew :core:test` exits 0.
4. `SettingsStudyLadderComposeTest` still passes (order labels are
   settings-driven).
5. AGENTS.md + doc §3 updated in the same commit.
6. `./gradlew ciFast` exits 0.

### Goal 66: Evidence-scaled demotion depth (experiment, then decide)

**Problem:** Finding S1 — even after Goal 65, a card demoting from the
ceiling still needs 3 fails per rung to descend; the ladder cannot
express "this card needs deep remediation now", which is why the
scheduler-bypassing writing-repair queue exists.

**Goal:** Run a simulator experiment before committing to a contract
change. Candidate rule (keep P3/P4 intact — single machine, real-due
evidence only): when the demotion threshold fires, demote by 2 rungs
instead of 1 if either (a) the reviewed task memory's FSRS difficulty is
≥ 9.0, or (b) this is the second demotion within the item's last 6
real-due reviews. Implementation sketch if adopted:

- `ReviewTransitionEngine.applyReviewAgain` (`:337-354`): compute the
  demotion target by applying `StudyLadderRules.demoteRung` once or twice
  per the rule; streak-reset logic unchanged (reset only when the rung
  actually moved).
- Trace: extend `movementReason` with `again_streak_demotes_deep`.

**Done when (machine-checkable):**

1. Experiment write-up committed under `docs/` (or appended to
   `docs/scheduler-fsrs-correctness-lab-report.md`) with simulator
   timelines comparing 1-step vs scaled demotion on: a ceiling card that
   goes cold, a mid-ladder card with difficulty 9+, and a healthy card
   with one bad week. The write-up states adopt / reject.
2. If adopted: `LadderSchedulerTest` cases for both trigger conditions
   and for the unchanged base case; new golden timeline
   (`deepDemotionOnHighDifficulty.timeline.txt`); parity snapshot
   regenerated; AGENTS.md + doc §7 updated; `./gradlew ciFast` exits 0.
3. If rejected: the write-up records why, and a golden pinning current
   1-step demotion from the ceiling is added anyway (S12).

### Goal 67: Require clean-write evidence to promote out of `write_kanji`

**Problem:** Finding S5 — a chain of messy `CLOSE` ("Save hard") passes
can promote a card out of the writing rung without one clean, hint-free
write; `writingLevel` already tracks clean passes but gates nothing.

**Goal:**

- `core/.../ReviewTransitionEngine.kt` `applyReview` (`:26-27`): move
  `updateWritingLevel(context, state)` *before*
  `applyLadderTransition(context, state)`. The reorder is behavior-neutral
  today (the transition never reads `writingLevel`) and lets the gate see
  the current attempt's effect.
- In `applyReviewPass`, when `state.rung == WRITE_KANJI` and the
  promotion trigger fires, additionally require `state.writingLevel >= 2`
  (two net clean, hint-free passes). Combined with Goal 63 the promotion
  condition on the writing rung is: interval qualifies AND
  `realPassStreak >= min` AND `writingLevel >= 2`.
- Trace reason code `promotion_blocked_writing_level` (optional, same
  pattern as Goal 63's).
- AGENTS.md `write_kanji` notes + doc §4.1/§7 updated. If the product
  owner rejects the gate, record the rejection as an explicit open
  decision next to D1 in doc §14 instead (that is also an acceptable
  completion of this goal).

**Done when (machine-checkable):**

1. `LadderSchedulerTest` cases pass: CLOSE-pass chain reaching a
   qualifying interval with `writingLevel < 2` does not promote;
   clean-pass path with `writingLevel >= 2` promotes; non-writing rungs
   are unaffected by `writingLevel`.
2. Reorder regression: full `:core:test` suite passes, proving the
   `updateWritingLevel` move changed no other outcome.
3. New golden timeline for the writing rung exit
   (`writeKanjiExitRequiresCleanWrites.timeline.txt`); parity snapshot
   regenerated.
4. AGENTS.md + doc updated in the same commit (or the D1-adjacent open
   decision recorded, if rejected).
5. `./gradlew ciFast` exits 0.

### Goal 68: Surface and park chronically stuck floor cards

**Problem:** Finding S6 — floor cards accumulate `realAgainStreak`
forever, hold a queue slot, and jump every session via priority bucket 0;
nothing acts on the signal.

**Goal:** Two phases; phase 1 is safe to land alone.

Phase 1 — detection and surfacing (no scheduler change):

- `core/`: new `StuckCardPolicy` with
  `isStuck(item, ladder, failStreak): Boolean` = phase `REVIEW`, rung is
  the item's demotion floor
  (`ladder.previousRung(rung, hasSimilarKanji) == rung`), and
  `realAgainStreak >= 2 * failStreak`.
- Wire the count into `LadderHealthPolicy`/`StudyStatsStore` as
  `stuckCount` and show it on the ladder-health stats card; show a
  "stuck" chip on the kanji detail screen with copy suggesting mnemonic
  work.

Phase 2 — parking (product decision required before implementation):
decide between (a) a user-initiated "pause this kanji" that sets a new
`parked` flag excluded from selection and admission counting (schema
change, DB v26), or (b) reusing manual suspension in AnkiDroid terms.
Record the decision in doc §14 before coding; do not silently reuse
`retired` (it has seeder reopen semantics,
`StudyQueueSeeder.canReopenRetiredSeedItem`, that user intent must not
fight).

**Done when (machine-checkable):**

1. `StuckCardPolicyTest` passes: floor + streak ≥ 2x threshold is stuck;
   non-floor rung, non-review phase, or lower streak is not; the floor is
   computed per-item (a no-similar-content card's floor differs).
2. Stats plumbing test: `StudyStatsStore` snapshot includes `stuckCount`;
   `StatsCacheCodec` round-trips it.
3. Compose test for the stuck chip/stat rendering.
4. Phase 2: a recorded decision in doc §14 (goal complete when the
   decision + phase-1 code land; parking implementation becomes its own
   follow-up goal with schema criteria).
5. `./gradlew ciFast` exits 0.

### Goal 69: Make `similar_kanji` availability mean "a choice card can actually be built"

**Problem:** Finding S7 — `hasSimilarKanji` is true whenever a pair row
exists, but the renderer needs ≥ 2 buildable choices; on fallback the
flashcard exercise is recorded into `similar_kanji_memory` because the
engine attributes memory by rung (`ReviewTransitionEngine.kt:539`).

**Goal:** Strengthen the predicate at annotation time (option (a); it
fixes selection *and* application because both pass through
`effectiveRung`):

- `app/.../data/LocalStoreInventory.kt` (annotation query, `:378-452`):
  a kanji counts as having similar-kanji content only when at least one
  partner from `similar_kanji_pairs` is itself present in the local
  inventory/dictionary glyph set the planner draws from — i.e. the
  planner's minimum input (1 valid distractor + the answer = 2 choices)
  provably exists. Mirror the exact source-set rule
  `SimilarKanjiChoicePlanner` uses so predicate and planner cannot
  diverge; extract that rule into a shared core helper if needed
  (`SimilarKanjiIndex` is the natural home).
- Keep the renderer fallback as a last-resort safety net, but log a
  warning when it fires (it should now be unreachable in practice).

**Done when (machine-checkable):**

1. Unit test: a kanji whose only pair partners are absent from local
   inventory is annotated `hasSimilarKanji = false`, its stored
   `similar_kanji` rung resolves via `effectiveRung` to a neighbor, and
   the review records into that neighbor's memory slot.
2. Unit test: a kanji with one in-inventory partner remains available and
   renders a 2-choice card (planner test).
3. Existing `SimilarKanji*Test` suites pass unchanged for the available
   case.
4. Fallback-warning assertion (log or trace) covered by a test.
5. `./gradlew ciFast` exits 0.

### Goal 70: Keep demotion's "practice soon" promise when relearning steps are empty

**Problem:** Finding S9 — with an empty relearning list, a third-fail
demotion reschedules from the FSRS post-lapse interval
(`ReviewTransitionEngine.kt:321-335`), so the newly demoted, more
scaffolded skill is first practiced days out; doc §7.1's "practiced
immediately" property silently depends on configuration.

**Goal:** Mirror the promotion cap on the demotion side:

- `core/.../ReviewTransitionEngine.kt` `applyReviewAgain`: when the
  demotion actually moves the rung and the relearning list is empty, cap
  the due at `min(current, nowMillis + 1 * DAY)` and clamp
  `scheduledIntervalDays` to match (`capDemotedRungFirstReview`, same
  shape as `capPromotedRungFirstReview` `:389-402`). With relearning
  steps configured, behavior is unchanged (the ~10-minute step already
  delivers the promise).
- Doc §7.1 + AGENTS.md updated to state both branches.

**Done when (machine-checkable):**

1. `LadderSchedulerTest` case: empty relearning steps + third real-due
   `again` → rung moves down and due is ≤ now + 1 day; non-demoting
   `again` with empty steps keeps the pure FSRS post-lapse interval
   (G8 behavior preserved).
2. New golden timeline (`demotionWithEmptyRelearningSteps.timeline.txt`);
   parity snapshot regenerated.
3. AGENTS.md + doc §7.1 updated in the same commit.
4. `./gradlew ciFast` exits 0.

### Goal 71: Name the ladder's real axes in docs, comments, and settings copy

**Problem:** Finding S4 — "lowest/easiest → highest/hardest" is the wrong
mental model (P9/P10 are the real axes) and actively misleads.

**Goal:** Documentation/copy pass, no scheduler change:

- `docs/ladder-and-srs-system.md` §3/§4: rewrite the order description in
  scaffolding terms ("demotion adds support, promotion removes it");
  state P9 and P10 explicitly in §13 (design properties).
- AGENTS.md scheduler notes: same terminology fix, including the
  `effectiveRung` tie-break sentence ("prefers the more scaffolded
  neighbor").
- `core/.../RecordsBase.kt` comments (`:13-18` and the
  `StudyLadderSettings` doc comments): replace easier/harder language.
- `core/.../SettingsTextCopy.kt` rung labels/subtitles: audit for
  easiest/hardest phrasing; adjust copy and its tests if present. Wire
  names and storage untouched.

**Done when (machine-checkable):**

1. `rg -n "lowest/easiest|easiest rung|hardest rung" docs/ AGENTS.md core/`
   returns no matches (modulo this plan file).
2. Copy tests (`SettingsTextCopyTest` and friends) updated and passing.
3. No golden or scheduler test changes (comment/doc-only for core).
4. `./gradlew ciFast` exits 0.

### Goal 72: Document the meaning→reading seam at `word_reading`

**Problem:** Finding S8 — the top rung switches the tested dimension;
reading failures demote into meaning practice.

**Goal:** Smallest honest fix: extend doc §4.7 and the AGENTS.md ladder
notes with the exit-exam framing — `word_reading` is the contextual exit
check, a reading lapse deliberately sends the card back through the
meaning ladder because Kani remediates recognition, not readings; true
retirement remains Anki-evidence-driven (P1). Record "reading-focused
rung / failure-dimension tracking" as an explicit non-goal in doc §14
unless the product direction changes. Goal 63 already guarantees
`word_reading` is validated more than once.

**Done when (machine-checkable):**

1. Doc §4.7 and AGENTS.md contain the seam paragraph; doc §14 records the
   non-goal.
2. No code change; `./gradlew ciFast` exits 0 (docs-only).

### Goal 73: Fix the floor-streak contradiction and document `realPassStreak`

**Problem:** Finding S10 — doc §4.1 (`:221-222`) and §7 (`:458-459`) say
the floor streak resets; code (`ReviewTransitionEngine.kt:344-353`) and
AGENTS.md accumulate it.

**Goal:** Correct both sentences to "the streak keeps accumulating at the
floor; only an actual rung move resets it". In the same pass, document
`realPassStreak`'s real role in doc §2.1/§7: bookkeeping surfaced in UI
progress and repair evidence, no scheduler decisions — superseded by the
Goal 63 gate once landed (update the sentence again in that goal).

**Done when (machine-checkable):**

1. `rg -n "streak resets each time|keeps resetting" docs/ladder-and-srs-system.md`
   returns no matches.
2. Doc §2.1 or §7 contains the `realPassStreak` role sentence.
3. Docs-only; `./gradlew ciFast` exits 0.

### Goal 74: Run the D2 experiment — graduation state from learning history

**Problem:** Finding S11 — graduation ignores in-learning struggle,
overestimating initial stability exactly for the difficult cards Kani
selects for (P2).

**Goal:** Execute the experiment doc §14 D2 calls for, without touching
production behavior until decided:

- Implement an alternative adapter path (test scope or a second
  `KaniFsrsAdapter` implementation, e.g.
  `LearningHistoryFsrsAdapter`) that evolves the initial state through
  the recorded learning answers via the FSRS same-day/short-term chain
  (`DefaultFsrsEngine.nextState` same-day branch), instead of
  `initialState(graduationRating)` alone.
- Drive both adapters through `SchedulerTimelineSimulator` on a
  struggling-card corpus (multiple `again`s before graduation) and a
  breeze-through corpus; compare first-interval distributions and
  time-to-first-demotion.
- Coordinate with the pinned relearning double-update
  (`RelearningGraduationDifficultyTest`, Goal 60): the relearning
  graduation path must stay deliberate and unchanged by this experiment.
- Write up adopt / reject in
  `docs/scheduler-fsrs-correctness-lab-report.md`.

**Done when (machine-checkable):**

1. The experiment adapter + simulator harness exist under test sources
   and run via `./gradlew :core:test` (tagged or named so they are
   discoverable, e.g. `GraduationHistoryExperimentTest`).
2. The lab report contains the comparison timelines and a decision.
3. If adopted: production adapter change lands as its own follow-up with
   regenerated goldens, updated `RelearningGraduationDifficultyTest`
   expectations only if deliberately changed, and AGENTS.md graduation
   notes updated together.
4. `./gradlew ciFast` exits 0.

---

## Suggested sequencing

1. **Batch L1 — movement contract (one reviewed change set):** Goals 63 +
   64. These rewrite the promotion contract once: minimum real-due passes
   per rung, retention-independent trigger. Highest leverage,
   golden-sensitive; Goal 64's "goldens byte-identical" check keeps the
   pair honest.
2. **Batch L2 — reach the remediation rungs:** Goal 65 (default reorder),
   then Goal 66 (experiment → decide). Pairs naturally with Goal 68
   phase 1, since both govern where failing cards spend time.
3. **Batch L3 — rung integrity:** Goals 67 (writing exit gate), 69
   (similar-kanji buildability), 70 (demotion due cap).
4. **Batch L4 — docs and principles (can land anytime, 73 immediately):**
   Goals 71, 72, 73.
5. **Backlog / experiments:** Goal 66 decision, Goal 68 phase 2 decision,
   Goal 74 (D2) when simulator time is available.

## Validation gates

Per AGENTS.md: `./gradlew ciFast` for every goal. Golden-sensitive goals
(63, 65, 66, 67, 70) regenerate `scheduler-goldens/` timelines and the
`scheduler-parity` snapshot from `SchedulerTimelineSimulator` output only
— never hand-edit. Goal 64 must land with goldens byte-identical. Any
contract change (63, 64, 65, 66, 67, 70) updates AGENTS.md scheduler
notes, `docs/ladder-and-srs-system.md`, and tests in the same change set.
If any goal is rejected, add the golden pinning the *current* behavior
instead (S12) so the next review does not re-derive it. None of these
goals touch provider/sync behavior, so the live AnkiDroid emulator gate
is not required unless a change unexpectedly crosses into sync paths.
