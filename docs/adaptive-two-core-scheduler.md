# Adaptive Two-Core Scheduler

Status: production design for database version 31 (July 2026).

> Database version 32 adds only local per-kanji mnemonic-note storage; the
> version 31 scheduler contract below is unchanged.

This document supersedes the long-term routing model in
`ladder-and-srs-system.md`. The old ten-rung behavior remains readable and
executable during lazy conversion, but new canonical items have two mandatory
core skills and use the old task types as presentation variants or targeted
repair tools.

## Invariants

- One `(kanji, answer_signature)` row in `study_items` remains the only
  scheduler item. There is no adaptive side queue.
- The two long-term memories are recognition (`kanji_meaning_memory`) and
  contextual reading (`word_reading_memory`).
- Recognition uses standard-glyph and font-glyph presentations. Contextual
  reading uses plain-word and mined-sentence presentations. Variants alternate
  deterministically from the persisted core review count and share memory.
- A real-due core failure calls FSRS `Again` exactly once. Every following
  repair appearance is practice-only: it cannot add another lapse, change
  stability/difficulty, or move a long-term threshold.
- Contextual reading is the terminal core. It never demotes back to recognition.
- Kani's AnkiDroid write surface remains note tags only.

## Persisted routing state

DB v31 adds:

- `study_items.scheduler_revision`: monotonic compare-and-swap revision.
- `study_items.routing_version`: legacy is 1; adaptive is 2.
- `study_items.adaptive_route_state_json`: compact versioned inline repair and
  revalidation state.
- `review_log.core_skill`, `failure_cause`, `evidence_source`,
  `selected_answer`, `correct_answer`, and `answer_evidence_json`.

All legacy rung, task-memory, wire, and settings columns are retained for
backup/downgrade compatibility. Malformed or future JSON fails open. A malformed
active repair recovers to its core review memory without an FSRS call.

Every review is one token-first transaction:

1. Insert the unique review token.
2. Compare-and-swap `study_items` on `scheduler_revision`.
3. Write timeline, task timing, choice evidence/state, and stats dirtiness.
4. Commit, then invalidate process-wide caches and apply UI/session effects.

The result is `APPLIED`, `DUPLICATE`, or `STALE`. Only `APPLIED` advances the
session, captures Undo, records outcome analytics, shows the completion toast,
or routes to the next card. Undo restores pre-rating scheduler state at a new
revision while deliberately preserving elapsed-time and objective-choice
observations.

## Lazy conversion

There is no mass migration or feature toggle.

- Legacy learning and relearning finish under their existing semantics.
- When a legacy transition returns to review, the item converts in the same
  commit.
- Writing, typed meaning, reverse meaning, similar-kanji, standard recognition,
  and font recognition map to the recognition core.
- Kanji-reading, reading-kanji, word-reading, and sentence-reading map to the
  contextual-reading core.
- The just-exercised legacy `TaskMemory` is copied into the core owner; every
  old slot remains stored.
- Movement streaks reset and the item anchors at `kanji_meaning` or
  `word_reading`.
- Evidence-strong imports seed directly at contextual reading, due now. New
  learning still starts conservatively and converts after graduation.
- A same-meaning source/signature reshuffle preserves adaptive repair focus. A
  genuine meaning change clears adaptive state and restarts recognition
  learning.

## Presentations and failure evidence

The first recognition check is the standard glyph; later checks alternate with
the font variant when enabled. The first contextual check is a plain word;
later checks alternate with a mined sentence when enabled and available. Font
selection is a stable function of kanji and core review count.

Recognition Fail asks for one cause before submitting:

- "I didn't know the meaning" -> `meaning_unknown`.
- "I mixed up the kanji" -> `visual_confusion`.

Dismissing the cause dialog submits nothing. Word/sentence failure records
`wrong_reading` and the exact rendered word/reading. Choice and handwriting
surfaces record objective/evaluator evidence automatically.

`type_reading` is a repair-only wire. It compares the full word reading exactly
after NFKC normalization, bracket-furigana extraction, katakana-to-hiragana
conversion, and punctuation/whitespace removal. Small kana, sokuon, dakuten,
and the prolonged sound mark remain significant. There is no romaji or fuzzy
matching. Words that cannot be aligned per kanji (including jukujikun/ateji)
fall directly to full-word typed reading.

## Repair routing

Known causes use a fixed scaffold, filtered by enabled tools and exact data
availability:

| Cause | First repair | Same-issue escalation |
| --- | --- | --- |
| Meaning unknown | `meaning_kanji` | `type_meaning` |
| Visual confusion | exact `similar_kanji` | `write_kanji` |
| Wrong reading | exact `kanji_reading` | `type_reading` |
| Homophone confusion | exact `reading_kanji` | `kanji_reading`, then `type_reading` |
| Writing shape | `write_kanji` | remain until the clean-write gate |
| Unknown | nearest valid enabled support tool | same priority policy |

Only a real-due core/revalidation failure increments same-issue recurrence. A
different cause resets it; a validation pass clears it. The existing demotion
threshold setting is the same-issue escalation threshold.

Repair appearances snapshot the configured relearning delays. An empty list is
treated as `[10]`, guaranteeing one ten-minute repair. If task and delay counts
differ, the last task or delay is reused so both sequences are honored.

- `Again`: restart at the first repair appearance.
- `Hard`: repeat the current appearance.
- `Good`: advance one appearance.
- Writing repeats until `writingLevel >= 2` from clean, hint-free passes.

After repair, the same core is revalidated at
`min(post-lapse core due, now + 1 day)`. Recognition promotes to contextual
reading only when the fixed-0.90 strength and minimum real-due pass gates both
hold.

The legacy `similar_kanji_repair_queue` is retained only to drain existing rows
for one compatibility release. No new row is enqueued; new repair state lives
on the owning `study_items` row.

## Settings and analytics

Settings present:

- required recognition and contextual-reading checks (locked on in adaptive
  behavior while their stored legacy bits remain untouched),
- optional font and sentence variants,
- enabled repair tools,
- repair-tool priority.

The optional variants continue to use their legacy enabled bits for downgrade
compatibility. Repair enablement and priority are independent, persisted under
`adaptive_repair_enabled` and `adaptive_repair_order`; the stored
`study_ladder_enabled` and `study_ladder_order` values are not rewritten into a
new ladder and do not define adaptive repair order. Restoring defaults resets
the adaptive repair list as well as the compatibility values. The legacy
demotion threshold is reused only as the same-cause escalation threshold.

Stats cache format 10 reports the two cores, active repairs by task/cause,
revalidation, escalation risk, and stuck repairs. Completion and parking mean a
contextual core in review with no repair/revalidation and at least one
contextual due-review pass. Legacy items continue to use the old fallback until
conversion. Review commits and material sync/settings changes dirty the cache
inside their owning transaction; readers accept only a same-day format-10
snapshot whose source version still matches.

## Sync and backup integrity

The seeded queue replacement and pending `sync_runs -> success` transition are
one transaction. Historical snapshots, timelines, baselines, analytics, and
retention only consume successful runs. Pending/failed rows are inert and are
purged so an interrupted run cannot poison stable dedupe keys. Material sync
changes increment `scheduler_revision`; a mid-sync review with the newer
revision wins. Sync reconciliation seeds from the complete durable study-item
set: kanji absent from the provider/analyzer result are explicitly retired
with their scheduler memories intact instead of being physically deleted. The
commit boundary retains any kanji a narrowed caller still omits. Foreground
Study reseeding uses a capped dashboard view, so it preserves out-of-scope
kanji rows unchanged and leaves retirement decisions to the full sync path.

Restore decompression is streamed with a 512 MiB output limit and a 64 MiB free
space reserve, with distinct too-large and insufficient-space errors. Backups
gzip and fsync a same-directory `.partial`, then publish with an atomic replace;
a failed publication never deletes the prior final archive.

## Required gates

Run `./gradlew ciFast --no-daemon --console=plain`. Scheduler/provider or
sync-seeding changes additionally require the documented real AnkiDroid 2.24
copied-collection gate (`OK (62 tests)`, default 7,000-note threshold). Before a
release, run `ciRelease`, verify the APK signature and metadata, and validate the
first GitHub workflow runs for any CI/service workflow change.
