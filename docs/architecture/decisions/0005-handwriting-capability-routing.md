# ADR 0005: Route handwriting by verified platform capability

- Status: Accepted
- Date: 2026-07-26
- Owners: Kani maintainers

## Context

`writing-core` is portable, but Android's production recognizer is ML Kit
digital ink and no accepted offline Japanese recognizer has yet passed the
desktop quality and licensing gate. Inventing a desktop result, treating a
recognizer error as a pass, or showing an ungradeable scheduled task would
corrupt the shared scheduler contract.

## Decision

- Android retains the existing ML Kit recognizer and declares
  `WRITE_RECOGNITION` when that production capability is available.
- Desktop GA provides the shared ink canvas for guides and unscheduled
  practice, but does not declare `WRITE_RECOGNITION`.
- A pure scheduler capability policy filters `write_kanji` before task
  selection and deterministically chooses the next enabled compatible repair
  or core revalidation.
- The selection trace records `write_kanji_unavailable_on_platform`.
- Capability routing does not mark writing as passed, mutate stored repair
  order/enablement, create a review or timeline token, change scheduler state,
  or discard the failure cause.
- Restoring the same portable Kani state on a capable Android installation
  makes the writing repair available again.

A desktop recognizer requires a separate decision backed by licensing,
offline-operation, quality, latency, accessibility, and golden-timeline
evidence.

## Consequences

The two hosts share scheduler policy without pretending their platform
capabilities are identical. Goal 196 must regenerate the affected scheduler
goldens and prove both capability-present and capability-absent timelines
before desktop Study is complete.

## Plan references

- `plans/desktop-support-goals-2026-07-26.md`, "Handwriting"
- Goal 196
