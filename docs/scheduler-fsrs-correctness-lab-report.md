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

## Snapshot reference
The matching compact snapshot lives in the test resources and is asserted by `SchedulerParitySnapshotTest`.

```text
scheduler parity snapshot
...
```
