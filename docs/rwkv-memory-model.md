# RWKV memory model

Status: production design for database version 34. RWKV-Curve replaces FSRS-6
as Kani's long-term memory model. The two-core scheduler policy
(`adaptive-two-core-scheduler.md`) is unchanged. Only the component that turns
an answer into an interval was swapped.

## What it is

- The model is **SM92**, the small RWKV-Curve model (331,511 parameters).
  - The srs-benchmark RWKV architecture was retrained by JSchoreels and ships
    in `Pedrubik2000/kuma3-anki`.
  - It is a sequence model over the whole collection's ordered review history,
    with five recurrent streams (card, deck, note, preset, user) and 92 input
    features per review.
  - Its curve head outputs a forgetting curve
    `p(t) = Σ wᵢ (1 + t/Sᵢ)^(−Dᵢ)` (three components). From it Kani reads the
    interval at any target retention and the S90 (days until recall falls to
    90%).
- The engine is a **pure-Kotlin port** in `:rwkv`.
  - It covers the feature builder, the PyTorch MT19937 ID encodings, the
    interleaved RWKV-7 forward pass, the curve head, upstream's interval search
    with grade-order PAVA, and the int8 card/note state compaction.
  - There is no native code or ML runtime; it runs on every ABI.
  - `RwkvAlgorithmInfo` pins the upstream commit and the weights' SHA-256,
    which is verified on load.
- **Parity:** `RwkvPublishedVectorsTest` replays the published SM92 vectors.
  - 1,000 rows, two users.
  - Maximum absolute probability error 2.5e-6, against upstream's bound of
    1e-5.
  - The interval inputs match to 7.3e-7.
- **Provenance and licensing:** see `THIRD_PARTY_NOTICES.md`. The port is
  AGPL-3.0-or-later, and the weights' training-data terms are an open
  upstream question.

## Mapping Kani onto the model

| Stream | Kani key (`RwkvStreamKeys`) |
| --- | --- |
| card | (kanji, core skill, meaning part of the answer signature) |
| note | kanji (the two cores are siblings, like an Anki note's cards) |
| deck | core skill |
| preset | one for all of Kani |
| user | global |

- **IDs** are the first 63 bits of a SHA-256 of length-prefixed parts.
- **Signature changes:**
  - A same-meaning signature reshuffle keeps the card, matching Kani keeping
    its memory.
  - A genuine meaning change (which resets Kani's memories) starts a new card.
- **Model days** are the noon-anchored day number of the local day start the
  review log records (`RwkvDays.fromDayStart`). 23- and 25-hour days stay
  consecutive.

## History rule (what is fed)

The scheduler feeds the model exactly once per core answer.

| Answer | Fed? | Schedules from the model? |
| --- | --- | --- |
| Legacy learning / relearning step | yes | no (step delay) |
| Legacy graduation | yes | yes |
| Legacy review pass / lapse | yes | yes |
| Adaptive core review or revalidation | yes | yes |
| Adaptive repair appearance | **no** | no (repair is practice-only) |
| Similar-kanji practice, undone reviews | no | no |

- **Learning steps are fed** because RWKV was trained on complete Anki
  histories. Their delays still come from the configured steps.
- **Promotion** previews a first Good on the contextual card without
  recording it (`KaniMemoryModel.previewFirstGood`). The note stream carries
  the kanji's recognition history into that preview.
- **What the scheduler reads:**
  - `promotionIntervalMillis` is S90, so the 21-day promotion gate and
    ceiling parking keep their meaning.
  - `stability` stores S90 (FSRS's definition of stability), so a
    downgraded build still reads it sensibly.
  - `difficulty` passes through unchanged.

## Persistence (database v34)

The model state is a derived cache of the ordered history.

- **The history:**
  - `review_log` rows with `mm_seq > 0`, in `mm_seq` order. Their `mm_*`
    columns hold the exact inputs the model was fed: ids, rating, active
    duration, model day and kind.
  - Merged with `rwkv_burst` seed bursts by `seq`. A burst sorts first on a
    tie.
- **`mm_seq` values:**
  - `NULL`: a pre-v34 row not yet classified.
  - `0`: not fed.
  - Anything above 0: the row's replay position.
- **Tables:**
  - `rwkv_state` holds stream states: card and note at int8, the others f32.
  - `rwkv_card_features`, `rwkv_card_history`, `rwkv_encoding` and
    `rwkv_globals` hold the feature counters, the per-card last answer and
    curve, the ID encodings, and the collection counters.
  - `rwkv_meta` holds `identity` (model plus layout), `state_version`,
    `next_seq` and `needs_rebuild`.
  - `rwkv_undo_journal` holds the pre-review values of everything a review
    wrote, for the last 100 reviews.

### Review transaction

`RwkvReviewBinding` loads the slice of state the answer reads (both cores of
the kanji) and binds `RwkvMemoryModel` to it. `LocalStoreStudy.commitReview`
then does the following in the existing token-first transaction:

1. Inserts the review row with its `mm_*` inputs.
2. Checks `state_version` with a compare-and-swap. A mismatch, a model change
   or `needs_rebuild` throws STALE, and the whole review rolls back.
3. Writes the burst (if any), the state delta, `rwkv_meta`, and a journal row.
4. Runs the existing item revision CAS, timeline and stats writes.

Two related rules:

- **Undo** restores from the journal in the same transaction that deletes the
  review row, but only when that review was the last state change. Otherwise a
  fed review marks the state for rebuild.
- **Readiness gate:** Study answers schedule only from a ready state.
  `RwkvRuntime.readyNow` checks the metadata on the review's background
  thread. While a rebuild runs, the answer is handed back for retry and a
  progress toast is shown.

### Rebuilds

A rebuild runs after the v34 upgrade, a model or layout change, an undo the
journal could not reverse, or a manual "Rebuild from history" in
Settings › Study behavior › Memory model. `RwkvStateRebuilder` runs on a
background thread:

1. Classifies pre-v34 rows once (`RwkvHistoryBackfill`).
   - Historical rows get `mm_seq = 2·id`, and a seed burst gets `2·id − 1`.
   - Live sequence numbers start above them.
   - Durations come from `study_task_log`.
2. Replays the history into memory.
3. Publishes in one transaction behind the same compare-and-swap, retrying if
   a writer won.

Backups need no special handling. A v34 snapshot carries a consistent state.
An older snapshot migrates and rebuilds.

## Evidence-seeded kanji

Kani cannot read Anki's review log, so a kanji seeded from Anki's memory state
has no history the model can see. Before its first Kani answer,
`RwkvReviewBinding` warms the card.

- **The burst:** a short all-Good history (`RwkvSeedBurstPolicy`).
  - It has `1 + ⌈log₂ S⌉` answers (2–8).
  - The gaps grow geometrically to the imported stability S.
  - It ends S days before the first Kani answer.
- **Scope:** `RwkvEngine.warmCard` runs the burst against a scratch copy of the
  collection counters. Only the card and note streams keep its updates.
- **Persistence:** the burst commits with the answer and is undone with it.

The acceptance band (`RwkvHistoryPoliciesTest`, in a collection with 400 days
of other history) compares the interval after an on-time Good with what FSRS-6
would have continued with:

| Imported S (days) | RWKV next interval | FSRS-6 next interval | Ratio |
| --- | --- | --- | --- |
| 7 | 41 | 23 | 1.78 |
| 21 | 71 | 62 | 1.15 |
| 30 | 87 | 85 | 1.02 |
| 60 | 129 | 158 | 0.82 |
| 120 | 201 | 295 | 0.68 |
| 365 | 296 | 807 | 0.37 |
| 1000 | 457 | 2024 | 0.23 |

Typical mature seeds stay within 0.5–2×. Very old memories are scheduled more
conservatively but still months out.

## Deliberate deviations from upstream

- **Day gaps:** a negative day gap (a clock or timezone moving back) is
  clamped to 0. `ln(ln(e + Δ))` is NaN for Δ < 1 − e and would poison the
  shared user stream.
- **Non-finite outputs:** a step with any non-finite output is never
  committed; the answer fails retryably.
- **Decay:** the recurrent decay uses `exp(−e^{−0.5}·σ(x))`, upstream's
  query-path form of `exp(−exp(−0.5 − softplus(−x)))`. Parity is unchanged.
- **Answer duration:** the duration is the answered card's active time,
  measured before the review is applied. Upstream previews intervals without
  a duration.
- **Forecasts:** the completion forecast simulates with one pass per answer
  (no four-answer grade-order search) over a copy-on-write overlay.

## Performance

`RwkvDeviceBenchmarkInstrumentedTest` (API 35 x86_64 emulator, 2026-11-18)
uses a synthetic history of 50,000 reviews over 4,976 cards:

| Measure | Result |
| --- | --- |
| Weights load and verify | 69 ms |
| Full rebuild (replay) | 66 s (1.3 ms per review) |
| Live answer | p50 4.8 ms, p95 5.9 ms |
| Publishing the rebuilt state | 2.5 s |
| State database | 26 MB |

The full rebuild only runs after the v34 upgrade or a forced rebuild, in the
background with a progress status.

On the JVM:

- A live answer takes about 4–5 ms.
- A 200-item, two-year completion forecast takes about 0.3 s.
- Storage is about 3 KB per card state (int8), 1.5 KB per note, and about
  60 KB for the shared deck, preset and user streams.

The minified (R8) build loads, verifies and checks the model state in 55 ms.
The release APK grows by about 1.26 MB, mostly the compressed weights.

## Known behavior

- **Unrepresentative collections:** in a collection holding only one or a
  few cards, consecutive Good intervals can swing up and down. This is the
  model's own curve, not a bug. Upstream notes RWKV curves can be
  unintuitive.
- **Training data:** the model is not personalized and was trained on
  four-button Anki histories. Kani's mostly Pass/Fail data is a different
  distribution.
