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
- The new adapter remains behind the internal `kani.fsrs.engine=latest21p` flag; FSRS-5 remains the default until a real-data report is reviewed.

Rollout decision:

- Do not enable the 21-parameter adapter by default from this synthetic report alone.
- Before production rollout, run the same old-vs-new comparison over a copy of real Kani study data and review the interval-ratio distribution.
