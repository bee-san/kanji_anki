# ADR 0002: Kani profiles remain installation-local for desktop GA

- Status: Accepted
- Date: 2026-07-26
- Owners: Kani maintainers

## Context

Anki supplies collection evidence, but Kani owns review tokens, scheduler
memories, adaptive repair state, local suspensions, settings, timelines, and
analytics. Those records cannot be merged safely by copying a live SQLite
database or by assuming AnkiWeb synchronizes them.

## Decision

Android and desktop installations use independent local Kani profiles in the
first desktop release.

- A portable whole-database backup may replace a destination profile only while
  both writers are closed and after the destination validates and durably
  stages it.
- Live databases, WAL files, and application-data directories must not be
  synchronized through Anki media, Dropbox, OneDrive, Syncthing, or another
  file-sync service.
- Portable product state remains in the Kani database. Secrets and
  host-specific window, tray, run-at-login, update, scheduler, notification,
  and path state remain in platform stores and are not activated by restore.
- A restored profile keeps Kani-owned review state but marks provider-derived
  mirrors stale, resets device-local keys, and blocks provider writes until
  source binding is revalidated and a successful sync completes.
- One Kani database is bound to one Anki source. Every provider supplies a
  binding candidate before mirror publication or writes. Binding uses opaque,
  salted evidence derived from provider kind, profile/authority evidence, and
  stable collection-ID overlap; diagnostics do not expose a raw profile name.
- A changed profile or provider kind fails closed. Rebinding requires an
  explicit backup and stable-ID overlap validation. Provider kind or model ID
  alone is insufficient.
- The product must not imply that AnkiWeb, a backup copy, or simultaneous file
  synchronization merges Kani progress.

True concurrent progress synchronization is a separate event-protocol project
and is not part of desktop GA.

## Consequences

Users may deliberately move their Kani state with backup and restore, but there
is one active writer and no automatic conflict merge. Android API 26-29 cannot
perform the destructive new-profile/rebind path because it cannot meet the
required durable snapshot contract.

## Plan references

- `plans/desktop-support-goals-2026-07-26.md`, "Kani state and multi-device behavior"
- Goals 174, 186, and deferred Goal 208
