# FSRS Engine Notes

Date: 2026-05-15

Scope: Kani's scheduler uses the in-repo `:fsrs-java` 21-parameter FSRS engine.

Current behavior:

- `BridgeScheduler` always routes scheduling through `LatestFsrsAdapter`.
- The legacy Kani FSRS-5 engine and adapter were removed; there is no `kani.fsrs.engine` runtime switch.
- First reviews and relearning graduation use the latest engine's initial-state path, with relearning preserving the current post-lapse difficulty.
- Review elapsed time is computed from the previous task-memory due timestamp minus the previous scheduled interval, so on-time reviews feed FSRS the full elapsed interval rather than `0` overdue days.
- Ladder promotion and demotion update the newly active rung's task memory, preventing newly promoted rungs from falling back to empty memory.

Verification coverage:

- `:fsrs-java:test` exercises the upstream-style generated reference fixture in `fsrs-java/testdata/upstream-reference-cases.json`.
- `:core:test` covers the scheduler adapter path, on-time review elapsed days, relearning graduation difficulty, and active-rung memory handoff after ladder movement.

Rollback decision:

- Rollback is now a source-control revert, not an in-process FSRS-5 switch.
