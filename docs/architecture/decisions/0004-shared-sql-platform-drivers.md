# ADR 0004: Share SQL operations and migrations behind platform drivers

- Status: Accepted
- Date: 2026-07-26
- Owners: Kani maintainers

## Context

Android currently owns schema v33 through `SQLiteOpenHelper`,
`SQLiteDatabase`, cursors, and `ContentValues`. A separate desktop persistence
implementation would risk schema, transaction, review-CAS, sync-publication,
and backup drift. A Room rewrite or a second desktop schema would also weaken
the already-tested migration history.

## Decision

- `:data-sql` will own the canonical schema, migrations, prepared SQL,
  repository behavior, and transaction semantics for Android and desktop.
- `:data-android` and `:data-desktop` provide narrow connection, path, locking,
  and durability drivers. Platform database types do not cross shared
  repository contracts.
- Both drivers run the same schema fingerprints, migration corpus, constraint
  classification, nested-savepoint, rollback, locking, review-CAS,
  sync-publication, and backup compatibility tests.
- Nested transactions use `SAVEPOINT`, `ROLLBACK TO`, and `RELEASE`. Plain
  nested `BEGIN` is not an acceptable approximation.
- Review-token conflicts distinguish a duplicate token from other constraint
  failures. Provider publication, derived state, queue reconciliation, and
  successful sync history remain one transaction; post-commit invalidation
  never observes unpublished state.
- Schema v33, `user_version`, migration order, downgrade behavior, cache
  formats, and whole-database backup portability remain unchanged unless a
  later explicit migration goal changes them.
- Portable product settings live behind shared data APIs. Secrets and
  host-integration settings use platform `DeviceSettingsStore` adapters.
- Android migrates incrementally behind compatibility adapters and stays
  releasable at each goal. This decision does not revive Room or Hilt.

## Consequences

SQL and transaction behavior become a single product implementation while
filesystem durability and connection mechanics remain platform-specific.
Android must prove the shared implementation before the legacy store facade is
removed or desktop persistence is treated as supported.

## Plan references

- `plans/desktop-support-goals-2026-07-26.md`, "SQL and publication"
- Goals 178-186
