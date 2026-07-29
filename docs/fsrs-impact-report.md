# FSRS Engine Notes

Date: 2026-05-15

> The ladder-routing bullets below are a DB30 snapshot. In DB31, only the
> recognition and contextual-reading core memories call FSRS; variants share
> those memories and inline repair attempts are practice-only. See
> [`adaptive-two-core-scheduler.md`](adaptive-two-core-scheduler.md).

Scope: Kani's scheduler uses the `:bee-fsrs` 21-parameter FSRS-6.x engine. That module is
now a vendored checkout of [`bee-san/bee-fsrs`](https://github.com/bee-san/bee-fsrs) 0.2.0
rather than in-repo source; the vendored release also carries an unused FSRS-7 engine. The
scheduler behaviour described below is unchanged by that vendoring. See
`bee-fsrs/PROVENANCE.md`.

Behavior at capture time:

- `BridgeScheduler` always routes scheduling through `LatestFsrsAdapter`.
- `SchedulerParameters.defaults()` starts from local Kani tuning (`targetRetention=0.90`, `againMultiplier=0.45`, `hardMultiplier=1.20`, `goodMultiplier=2.00`, `easyMultiplier=3.10`); app settings persist those values and optional rank-based target-retention ranges rather than importing Anki deck preset FSRS parameters.
- The legacy Kani FSRS-5 engine and adapter were removed; there is no `kani.fsrs.engine` runtime switch.
- First reviews and relearning graduation use the latest engine's initial-state path, with relearning preserving the current post-lapse difficulty.
- Review elapsed time is computed from the previous task-memory due timestamp minus the previous scheduled interval, so on-time reviews feed FSRS the full elapsed interval rather than `0` overdue days.
- Ladder promotion and demotion update the newly active rung's task memory, preventing newly promoted rungs from falling back to empty memory.

Verification coverage:

- `:bee-fsrs:test` exercises the upstream-style generated reference fixture in `bee-fsrs/testdata/upstream-reference-cases.json`.
- `:core:test` covers the scheduler adapter path, on-time review elapsed days, relearning graduation difficulty, and active-rung memory handoff after ladder movement.

Rollback decision:

- Rollback is now a source-control revert, not an in-process FSRS-5 switch.
