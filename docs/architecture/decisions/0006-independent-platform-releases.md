# ADR 0006: Publish Android and desktop independently from one exact commit

- Status: Accepted
- Date: 2026-07-26
- Owners: Kani maintainers

## Context

Kani's successful Android CI and release chain is already production-critical.
Desktop artifacts require native operating-system builders, independent smoke
tests, platform signing, notarization, and an authenticated multi-asset
manifest. Desktop service or signing failures must not invalidate a verified
Android APK.

## Decision

- Android keeps package identity `dev.bee.kanjianki` and its existing
  Android-CI-triggered release path.
- Desktop artifacts attach to the same canonical release tag only after
  proving that the tag, trusted Android release metadata, and detached checkout
  all resolve to the exact same `build_sha`.
- Windows, Linux, and macOS artifacts are built and installed-smoke-tested on
  their native supported operating systems. Production artifacts are signed or
  notarized where the platform provides that chain.
- Android APK publication depends only on Android build and verification.
  Desktop signing, notarization, Anki service, or packaging failure may delay
  desktop assets but may not block or invalidate an already-valid Android
  release.
- The desktop workflow uses least privilege and protected environments.
  Untrusted compilation happens before signing secrets are imported.
- Final checksums, SBOM, notices, and `release-manifest-v1.json` are assembled
  after native signing. The exact manifest bytes receive a protected Ed25519
  signature, uploaded last as the desktop-readiness marker.
- Existing asset names are immutable: identical bytes are idempotent; different
  bytes fail closed and require explicit authorization for a new tag.
- Each published asset is downloaded independently and rechecked for hash,
  manifest signature, native signature/notarization, identity, version, launch,
  and data retention.

This ADR records architecture only. It does not authorize pushing a release
workflow, using signing secrets, tagging, publishing, or releasing.

## Consequences

One source commit can produce a coherent multi-platform Kani release without
making Android availability depend on desktop infrastructure. Desktop updater
metadata must treat a missing signed manifest as "desktop assets not ready",
never as permission to install raw unsigned assets.

## Plan references

- `plans/desktop-support-goals-2026-07-26.md`, Goals 168, 202, 205, and 206
