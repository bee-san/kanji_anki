# Scheduler / FSRS Correctness Lab Report

Current version/date: 2026-06-12

This report is a focused snapshot of the scheduler lab in the current worktree. It does not claim full Anki FSRS parity; it records the deterministic trace and timeline behavior that is currently exercised by the tests.

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
| `newKanjiEntersKanjiMeaning` | a new card enters the kanji meaning rung and is selected deterministically | baseline queue admission / selection trace |
| `reviewPassPromotesAfterLongFsrsInterval` | a long-interval `good` review promotes the card and records the FSRS interval call | FSRS still drives interval math, not full deck-level Anki behavior |
| `threeDueReviewAgainsDemote` | repeated `again` answers eventually demote the card after the configured streak threshold | lapse handling remains deterministic and test-only here |
| `similarKanjiSkippedWithoutContent` | a demoting review still respects the missing similar-kanji content path | the scheduler reports `similar_kanji_unavailable` instead of inventing content |
| `relearningBeatsSameFamilyReviewSibling` | relearning wins over a same-family review sibling in the same session | same-session same-family hiding is distinct from persistent mature-sibling suppression |

## Current boundary
- FSRS memory/interval only; Kani still owns queue selection, ladder movement, learning/relearning steps, sibling suppression, and user-facing scheduler wording.
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

## Snapshot reference
The matching compact snapshot lives in the test resources and is asserted by `SchedulerParitySnapshotTest`.

```text
scheduler parity snapshot
...
```
