# ADR 0003: Provider FSRS memory is an explicit capability

- Status: Accepted
- Date: 2026-07-26
- Owners: Kani maintainers

## Context

Kani's richest AnkiDroid import can receive provider-supplied FSRS stability,
difficulty, and retrievability. Stock AnkiConnect `cardsInfo` exposes useful
identity, state, interval, repetition, and lapse data but does not expose those
memory fields. Treating absent fields as zero or inferred provider values would
misrepresent both the source and Kani's scheduling precision.

## Decision

- Provider snapshot stability, difficulty, and retrievability are nullable.
- A provider declares `FSRS_MEMORY_STATE` only when it actually supplied those
  fields. The AnkiDroid adapter declares it conditionally; the stock
  AnkiConnect adapter does not.
- Missing provider fields are never converted to zero, default values, or
  fabricated retrievability.
- Shared admission policy may continue to use real interval, lapse,
  suspension, and mature-support evidence.
- Kani may preserve its existing policy fallback from missing provider
  stability to real Anki interval and its existing lapse-adjusted difficulty
  fallback. Those results are labeled as Kani-derived policy values, not
  provider FSRS memory.
- Provider-FSRS-specific weak filtering and prioritization is unavailable when
  the capability is absent. UI and diagnostics describe reduced import
  precision rather than a sync failure.
- Golden policy timelines cover capability-present and capability-absent
  providers.

An optional read-only companion add-on is a separate post-GA project. It may
not be silently bundled or use this decision to authorize scheduling writes.

## Consequences

Desktop sync remains useful with stock AnkiConnect, but some import ranking is
less precise than on a provider that exposes real FSRS memory. Provider
capability differences stay visible in typed contracts instead of leaking as
sentinel values.

## Plan references

- `plans/desktop-support-goals-2026-07-26.md`, "FSRS capability policy"
- Goals 174, 176, and 188
