# Missing Kanji release-note draft

Draft only; do not publish from this file.

## User-facing note

Kani can now scan every note field in your AnkiDroid collection and show which
kanji from a selected Jiten frequency range are missing. Search the report,
select individual or visible results, and inspect meanings, readings, and rank
details.

Selected kanji can be added to Kani without bypassing the normal daily-new
limit or adaptive study pipeline. Supported AnkiDroid versions can also receive
an additive `Kani::Missing Kanji` deck directly. Interrupted exports can be
retried without duplicating confirmed notes, and an Anki-ready UTF-8 CSV is
available when direct creation is unsupported or unfinished.

Raw Anki note text is processed only in memory. Kani stores aggregate kanji
membership and the minimal source/export metadata needed for recovery.

## Verification evidence

- Full pure-Kotlin and app unit suites, Android-test compilation, and Android
  lint with warnings as errors.
- API 35 fake-provider inventory and writer suites, including malformed rows,
  legacy fallback, partial writes, cancellation, collisions, and receipt
  recovery.
- A 5,000-note direct export and idempotent retry completed in 0.812 seconds on
  the local API 35 emulator, using exactly 50 batches.
- Real AnkiDroid 2.24.0 sanitized-fixture result and phone/tablet screenshot
  paths are recorded during the final release gate.
