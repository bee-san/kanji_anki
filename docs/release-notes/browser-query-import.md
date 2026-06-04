# Browser query import release-note draft

Draft only; do not publish from this file.

## User-facing note

Kani now supports opt-in Anki Browser query imports from Settings. Suspended cards remain the default import source, and Browser query import stays off until you enable it and enter a query such as `is:suspended`, `rated:31:1`, `deck:Japanese tag:kani`, or `prop:due<=0 -is:suspended`.

Kani uses the query locally against AnkiDroid as raw Anki search syntax, keeps only cards from the configured note type, and still applies the rank range and minimum matching-card threshold before adding kanji to practice. Query text may contain private deck or tag names, so Kani redacts it from import audit output. When a query selects suspended cards, Kani archives those suspended cards locally before provider cleanup hides them from later syncs.
