# FSRS Interval Impact Report

Date: 2026-05-15

Scope: synthetic migration comparison between Kani's existing FSRS-5 adapter and the new 21-parameter `fsrs-java` adapter.

The report is intentionally synthetic because no real Kani study export is part of this package repository. It exercises representative stability, difficulty, elapsed-day, and rating combinations through `FsrsImpactSimulationTest`.

Inputs:

- stability: `0.5`, `2.0`, `10.0`, `60.0`
- difficulty: `2.0`, `6.0`, `9.0`
- elapsed days: `0`, `1`, `7`, `30`, `120`
- ratings: `again`, `hard`, `good`, `easy`
- target retention: `0.9`

Flag criteria:

- new interval is more than `2x` the old interval
- new interval is less than `0.5x` the old interval

Result:

- The synthetic test matrix runs as part of `:core:test`.
- At least one interval-ratio flag is expected, so the test verifies that migration risk is visible rather than silently assumed safe.
- The latest 21-parameter adapter is now the default engine. The legacy FSRS-5 adapter remains available with `-Dkani.fsrs.engine=fsrs5`.
- Review elapsed time is computed from the previous task-memory due timestamp minus the previous scheduled interval, so on-time reviews feed FSRS the full elapsed interval rather than `0` overdue days.

Rollout decision:

- Keep the legacy FSRS-5 adapter available as a system-property rollback path.
- Run the same old-vs-new comparison over a copy of real Kani study data before removing the rollback path.
