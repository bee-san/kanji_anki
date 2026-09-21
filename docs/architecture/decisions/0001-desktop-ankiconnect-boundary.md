# ADR 0001: Desktop collection access uses loopback AnkiConnect

- Status: Accepted
- Date: 2026-07-26
- Owners: Kani maintainers

## Context

Kani needs a supported desktop collection boundary without racing Anki's live
SQLite database, depending on Anki's private storage layout, or claiming that a
copied `collection.anki2` is safe while Anki is running. AnkiConnect API v6
provides an explicit application boundary with permission, action, and response
semantics that can be fixture-tested.

## Decision

Desktop Kani accesses Anki Desktop only through AnkiConnect API v6.

- The default endpoint is `http://127.0.0.1:8765`.
- The first release accepts loopback literals and `localhost` only. Every host
  name must resolve entirely to loopback addresses.
- The transport bypasses system HTTP proxies, rejects redirects, bounds request
  and response sizes, enforces connect/read deadlines, and supports cooperative
  cancellation.
- An HTTP success with a non-null AnkiConnect `error` is a failed action.
- Permission discovery calls `requestPermission` without an API key. After any
  required authentication is configured, Kani probes `version` and
  `apiReflect` and validates every required action's response shape.
- An optional API key lives only in the platform secret store. Kani-controlled
  logs, diagnostics, command lines, process titles, databases, and backups may
  not contain it.
- Desktop code never opens, copies, watches, repairs, or writes Anki's live
  `collection.anki2` directly. Remote and LAN endpoints are outside desktop GA.
- Live qualification pins the Anki Desktop version, AnkiConnect source commit
  and archive checksum, API version, and required action list.

Normal collection write-back remains limited by ADR-tested provider
capabilities and the shared write policy. This transport decision does not
authorize Anki scheduling writes.

## Consequences

Anki Desktop and a compatible, configured AnkiConnect must be running for
desktop sync. Permission, authentication, version, capability, malformed
response, timeout, cancellation, and unreachable states must be distinct UI
states. Supporting a different transport or a remote endpoint requires a new
security and source-identity decision.

## Plan references

- `plans/desktop-support-goals-2026-07-26.md`, "Desktop Anki boundary"
- Goals 187-191
