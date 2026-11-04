# Architecture Index

This index points to Kani's active architecture contracts. Historical plans
remain available for context, but they do not override the active production
invariants or an explicitly superseding plan.

## Active contracts

- [Desktop support goals](../../plans/desktop-support-goals-2026-07-26.md) is
  the execution plan for the supported Android, Windows, Linux, and macOS
  architecture. It supersedes the Android-only target shape of pending Goals
  148-163 without reopening completed Goals 145-147.
- [Desktop conversion baseline](../desktop-conversion-baseline.md) freezes the
  pre-conversion module, source, toolchain, test, compile-time, and startup
  measurements used to prove later moves preserve behavior and build health.
- [Adaptive two-core scheduler](../adaptive-two-core-scheduler.md) is the
  canonical database-v31 scheduler and review-persistence contract.
- [Database backup and restore](../database-backup-restore.md) defines the
  durable Android snapshot and recovery guarantees that shared persistence and
  desktop adapters must preserve.
- [Modularization roadmap](../modularization-roadmap.md) records the current
  module shape and links pending extraction work to the desktop-support plan.

## Accepted decision records

| ADR | Decision |
| --- | --- |
| [0001](decisions/0001-desktop-ankiconnect-boundary.md) | Desktop collection access is loopback AnkiConnect only. |
| [0002](decisions/0002-independent-local-kani-profiles.md) | Android and desktop Kani state is installation-local for desktop GA. |
| [0003](decisions/0003-provider-fsrs-capabilities.md) | Provider FSRS memory is nullable and capability-declared. |
| [0004](decisions/0004-shared-sql-platform-drivers.md) | Both hosts share SQL operations and migrations behind platform drivers. |
| [0005](decisions/0005-handwriting-capability-routing.md) | Scheduled handwriting is routed by real recognizer capability. |
| [0006](decisions/0006-independent-platform-releases.md) | Android and desktop publication is exact-commit but failure-independent. |

New decisions that alter scheduler behavior, persisted state, provider writes,
backup durability, source binding, or release trust require a new ADR and the
golden/live validation required by the affected contract.
