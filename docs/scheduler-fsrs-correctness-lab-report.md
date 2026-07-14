# Scheduler / FSRS Correctness Lab Report

> Historical DB30 lab snapshot. The named goldens, rung transitions, and deep
> demotion experiments describe the ladder implementation at capture time.
> They are not the DB31 routing contract; see
> [`adaptive-two-core-scheduler.md`](adaptive-two-core-scheduler.md).

Snapshot date: 2026-06-12

This report is a focused snapshot of the scheduler lab at that date. It does not claim full Anki FSRS parity; it records the deterministic trace and timeline behavior exercised by the tests in that snapshot.

## DB31 lazy-conversion boundary

The current scheduler contract is defined in
[`adaptive-two-core-scheduler.md`](adaptive-two-core-scheduler.md): one
`study_items` row owns two long-term core memories, and variants share their
core memory. A real-due core Fail calls FSRS Again once; every following repair
appearance is practice-only inline repair and cannot add another lapse or alter
memory strength.

The timeline filenames below are retained as compatibility manifests. A DB30
item finishes an in-progress learning/relearning sequence under its stored
rules. Its transition back to review converts it to routing version 2 in the
same commit, preserving the just-exercised memory while retaining all legacy
columns for downgrade compatibility. The old rung trace before that boundary
is evidence of lazy conversion, not a second live scheduler or side queue.

## Source evidence
- `core/src/test/kotlin/dev/bee/kanjianki/core/SchedulerDecisionTraceTest.kt`
- `core/src/test/kotlin/dev/bee/kanjianki/core/SchedulerTimelineSimulatorTest.kt`
- `core/src/test/kotlin/dev/bee/kanjianki/core/SchedulerParitySnapshotTest.kt`
- `docs/anki-manual-parity-checklist.md`
- `docs/fsrs-impact-report.md`

## Deterministic golden scenarios
These are the scenarios covered by the timeline goldens and the parity snapshot.

| Scenario | What it proves | Current note |
| --- | --- | --- |
| `newKanjiEntersKanjiMeaning` | a new card starts conservative legacy learning at recognition | it converts only after graduation returns it to review |
| `reviewPassPromotesAfterLongFsrsInterval` | the final legacy review converts to recognition, then the shared font variant uses that core | the historical filename is retained for manifest compatibility |
| `promotionRequiresSecondRealDuePass` | adaptive recognition needs its own two real-due passes before contextual reading unlocks | conversion cannot reuse a legacy pass to cascade immediately |
| `threeDueReviewAgainsDemote` | a real-due core failure enters inline repair and later repair answers make no FSRS call | one lapse per failed core check; repair is practice-only |
| `writeKanjiExitRequiresCleanWrites` | a final legacy writing review preserves its evidence, converts, and yields to recognition-core scheduling | the old writing-rung filename now pins conversion |
| `similarKanjiSkippedWithoutContent` | an in-progress legacy relearning path remains legacy until it returns to review | lazy conversion does not interrupt an active step sequence |
| `relearningBeatsSameFamilyReviewSibling` | relearning wins over a same-family review sibling in the same session | same-session same-family hiding is distinct from persistent mature-sibling suppression |

## Snapshot boundary

- The DB30 experiments below are retained as historical decision evidence, not current ladder routing.
- DB31 owns queue selection, two-core routing, practice-only inline repair, same-family hiding, and user-facing scheduler wording.
- Local due dates are kept in Kani; this lab does not rewrite Anki deck schedules.

## Intentional differences that remain open
- Kani is leech-informed only; no leech tag/suspend policy is implemented.
- The report and snapshot are intentionally conservative and do not claim byte-for-byte parity with Anki internals.
- Same-session same-family hiding is covered as a scheduling rule, but persistent mature-sibling suppression remains a separate policy choice.

## Goal 66 experiment — evidence-scaled (deep) demotion (REJECTED)

**Question.** Should the demotion threshold, when it fires, sometimes demote by
two rungs instead of one — specifically when (a) the reviewed task memory's
FSRS difficulty is ≥ 9.0, or (b) this is the second demotion within the item's
last 6 real-due reviews — so a cold or hard card reaches deep remediation
faster?

**Method.** `SchedulerTimelineSimulator`-style chains of nine consecutive
real-due `again`s (each on a fresh due slot, `ladder_demotion_fail_streak = 3`)
under the current single-step demotion, for three corpora, with the Goal 65
default order (`write_kanji, type_meaning, meaning_kanji, similar_kanji,
kanji_meaning, font_meaning, word_reading`; card has no similar-kanji content):

- **Ceiling card goes cold** (`word_reading`, difficulty 5): three fails per
  step — `word_reading → font_meaning → kanji_meaning → meaning_kanji`. Nine
  fails move it three rungs.
- **Mid-ladder hard card** (`kanji_meaning`, difficulty 9.5): three fails per
  step — `kanji_meaning → meaning_kanji → type_meaning → write_kanji` (nine
  fails reach the writing floor). Under the current rule this is *identical* to
  a difficulty-5 card, because demotion does not consult difficulty.
- **Healthy card, one bad week** (`kanji_meaning`, difficulty 5): the first two
  fails hold the rung (streak 1, 2); a single-step demotion fires only on the
  third fail. A transient bad week (≤ 2 due fails) never demotes at all.

**Findings.**

1. Goal 65 already resolves the S1 headline for the signature remediation:
   discrimination practice is now one demotion step (3 fails) from the start
   rung, not three (9 fails), and the writing floor is three steps (9 fails)
   down. Deep demotion would shave the writing-floor case to roughly 6 fails —
   a smaller marginal gain than the reorder already delivered.
2. Candidate trigger (a) (difficulty ≥ 9) would make the mid-ladder-hard and
   healthy corpora diverge — a hard card would drop two rungs per threshold —
   but it couples ladder movement to raw FSRS difficulty, a second
   memory-strength signal layered on top of the interval trigger the ladder
   already uses. That reintroduces exactly the kind of hidden coupling Goal 64
   just removed for promotion (D4), only on the demotion side.
3. Candidate trigger (b) (second demotion within 6 reviews) needs new
   per-item history state (a windowed demotion counter) that the single
   state-machine model (P3) does not carry today; adding it is a schema and
   contract expansion for a tail case.
4. The healthy-card corpus shows the current rule is already appropriately
   conservative: a transient bad week does not demote, and deep demotion would
   risk over-reacting to a two-fail blip if paired with any relaxation of the
   streak threshold.

**Decision: REJECTED.** Keep single-step demotion. Goal 65's reorder plus the
Goal 63 min-pass gate cover the S1 concern (fast reach to the remediation
rungs, no cheap re-promotion) without adding a difficulty-coupled or
history-windowed demotion rule that would complicate P3/P4 and re-introduce a
D4-style coupling. Chronic floor cases are addressed by Goal 68 (surfacing and
parking stuck cards), not by demoting faster. The current single-step demotion
from the ceiling is pinned by the `ceilingCardDemotesOneRungWhenCold` golden so
a future review does not re-derive this decision.

## Goal 74 experiment — graduation state from learning history (D2) (REJECTED for now; harness retained)

**Question (open decision D2).** New-learning graduation seeds FSRS from the graduating rating alone (`LatestFsrsAdapter.initialReview`, `isNewLearning = true` → `engine.initialState(graduationRating)`), so a card that needed several `again`s in learning graduates with the same initial memory as one that passed on the first Good. Should graduation instead evolve the initial state through the recorded learning answers via the FSRS same-day short-term chain (`DefaultFsrsEngine.nextState` with `elapsedDays = 0`)?

**Method.** `GraduationHistoryExperimentTest` (test scope, read-only — no production behavior changed) drives two adapter paths at fixed 0.90 retention:
- *current* — `initialState(graduatingRating)` then `nextIntervalDays`.
- *history* — `initialState(firstAnswer)`, then `nextState(state, answer, elapsedDays = 0)` for each subsequent learning answer, ending on the graduating answer.

Corpora: a breeze-through card `[Good]` and struggling cards with 1/3/5 `Again`s before the graduating `Good`.

**Findings (first-interval at 0.90 retention).**

| learning answers | current stability / interval | history stability / interval |
| --- | --- | --- |
| `[Good]` (breeze) | 2.307 → 2d | 2.307 → 2d |
| `[Again, Good]` | 2.307 → 2d | 0.247 → 1d |
| `[Again×3, Good]` | 2.307 → 2d | 0.046 → 1d |
| `[Again×5, Good]` | 2.307 → 2d | 0.010 → 1d |

1. The current path is **blind to in-learning struggle**: every corpus graduates with the identical 2-day first interval, confirming the D2 concern — for Kani's deliberately difficult, confusable queue (P2) this systematically over-estimates initial stability for exactly the hardest cards and delays the failure evidence the ladder needs.
2. The history path **does** differentiate, but the FSRS same-day short-term chain treats each learning `Again` as a same-day forget that multiplies stability down hard, so even one `Again` collapses the graduating memory to ~0.25 (and five `Again`s to ~0.01) — effectively discarding the graduating `Good`. At the 0.90 default all struggling variants floor at a 1-day first interval, which is directionally right (validate sooner) but likely **over-corrected**: it makes the graduating rating almost irrelevant and would route nearly every once-failed new card into an immediate re-test, blurring the learning/review boundary the AGENTS.md contract keeps deliberate ("learning-step answers are practice-only and do not feed short-term stability").

**Decision: REJECTED for now; harness retained.** The current struggle-blind path is a real deviation, but the naive history chain over-corrects and would silently change every early interval and the learning/review boundary. Adopting D2 should wait for a *tempered* variant (e.g. cap the number of learning answers fed into the chain, or blend `initialState(graduatingRating)` with the history stability) evaluated against regenerated goldens and the pinned relearning double-update (`RelearningGraduationDifficultyTest`, Goal 60), which must stay unchanged. Until then the production path is unchanged and `GraduationHistoryExperimentTest` keeps the comparison discoverable. If adopted later, it lands as its own follow-up with regenerated goldens and updated AGENTS.md graduation notes.

## Goal 122 — Tempered D2 variants (2026-07-14)

**Method.** Extended `GraduationHistoryExperimentTest` with two tempered variant families:

- **Cap-N:** feed at most N (sweep N ∈ {1, 2}) most-recent learning answers into the same-day chain before the graduating rating. This limits the number of collapse-multiplications.
- **Blend-α:** `stability = α · initialState(graduatingRating).stability + (1-α) · historyChain.stability` (sweep α ∈ {0.5, 0.7, 0.9}). Difficulty from the graduating rating alone.

Corpora: breeze `[Good]`, struggling-1 `[Again, Good]`, struggling-5 `[Again×5, Good]`.

**Findings.**

| variant | breeze stab | strug-1 stab | strug-5 stab | strug-5 ≥ 25% breeze? |
| --- | --- | --- | --- | --- |
| Current (baseline) | 2.307 | 2.307 | 2.307 | ✓ (100%) |
| Cap-1 | 2.307 | 0.247 | 0.247 | ✗ (10.7%) |
| Cap-2 | 2.307 | 0.247 | 0.046 | ✗ (2.0%) |
| Blend-0.5 | 2.307 | 1.277 | 1.159 | ✓ (50.2%) |
| Blend-0.7 | 2.307 | 1.689 | 1.621 | ✓ (70.3%) |
| Blend-0.9 | 2.307 | 2.101 | 2.082 | ✓ (90.2%) |

**Analysis.**

1. **Cap-N** still collapses too heavily: even Cap-1 (only the immediate pre-graduating answer) produces strug-5 stability at 10.7% of the breeze card's baseline. Cap-2 is worse. The cap limits the chain length but the FSRS same-day `nextState` multiplier is so aggressive that even a single `Again` before `Good` drops stability below the 25% floor.
2. **Blend-α** variants all preserve the ordering (struggling < breeze) while staying well above the 25% floor. Blend-0.7 is the recommended candidate: struggling-5 graduates at ~70% of baseline stability (rather than identically), which provides meaningful differentiation without the boundary collapse.

**Decision: RE-DEFERRED with evidence (D-P10).** Blend-0.7 qualifies as a viable candidate. Adoption is a follow-up work item requiring: regenerated goldens, `RelearningGraduationDifficultyTest` re-pinned intentionally, AGENTS.md "Do not change either path" paragraph updated. Production behavior unchanged by this goal.

## Snapshot reference
The matching compact snapshot lives in the test resources and is asserted by `SchedulerParitySnapshotTest`.

```text
scheduler parity snapshot
...
```
