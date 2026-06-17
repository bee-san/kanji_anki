# Kani kanji-detail dropdowns on study answer cards

Date: 2026-06-17
Branch/worktree: `feature/kani-kanji-details-dropdowns-20260617` at `/Users/autumnskerritt/kanji_anki_worktrees/kani-kanji-details-dropdowns-20260617`
Board tenant: `kani-kanji-details-dropdowns-20260617`
Source screenshot: `Screenshot_20260617_073523_Kani.jpg`

## User request

The study answer card has a large unused area after revealing an answer. Use that space for expandable kanji details, especially:

- stroke order / stroke data,
- radicals / kanji breakdown,
- “used in Anki” rows showing each synced word/card where the kanji appears,
- deep links into the Anki/AnkiDroid card or note when possible.

## Product ideas

### 1. Default collapsed “Kanji details” area

Add a small accordion stack under the existing answer row inside `StudyAnswerPanel`, default-collapsed so it does not slow review flow.

Suggested sections:

1. **Details**
   - meaning glosses already visible plus compact metadata: stroke count, grade, radical number/name, KANJIDIC frequency, Jiten rank.
   - on/kun/nanori readings as chips.
   - empty-state copy if dictionary data is unavailable.
2. **Radical / breakdown**
   - start with radical number/name and stroke count, because `dictionary-core` already exposes `strokeCount`, `radical`, `grade`, `kanjidicFrequency`, and `jitenRank`.
   - leave component decomposition behind a data-source gate if no component asset exists yet.
3. **Stroke order**
   - MVP: show stroke count and a placeholder/detail row only when animation assets are absent.
   - Full version: offline stroke-order SVG/animation from an auditable licensed source, with step/play controls and no network dependency during study.
4. **Used in Anki**
   - list local synced notes/cards containing this kanji, grouped by expression/word.
   - each row: expression, reading, meaning, optional deck/model/source status, plus card/note id metadata hidden behind copy/long-press if noisy.
   - tap row opens AnkiDroid note/card when a supported intent/deep link exists; otherwise fall back to copy note/card ID + toast.
   - cap initially visible rows, with “Show all” if many words use the kanji.
5. **Why this card?**
   - keep the current `From: 抗議` cue at top, then show “also appears in…” for other examples from local Anki data.

### 2. UX constraints from screenshot

- The answer card has abundant vertical blank space, but rating buttons and bottom nav must remain reachable without accidental scrolling traps.
- Accordions should be collapsed by default and should not shift the `Again` / `Good` controls off-screen on first reveal.
- Expanded content may scroll within the overall study page, but should not introduce nested scroll jank.
- Touch targets must be comfortable on narrow phones; no tiny chevrons only.
- The feature must preserve the current cute Kani theme/tokens and avoid a dense dictionary-wall look.
- The answer reveal must stay fast; loading extra metadata must not block answering or rating.

### 3. Technical starting points

Observed code map:

- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyAnswerModel.kt`
  - current `StudyAnswerPanelModel` only has `title`, `glyph`, `lines`, and `helperText`.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyAnswerCompose.kt`
  - current `StudyAnswerPanel` renders answer title, big glyph, answer lines, optional helper text.
- `dictionary-core/src/main/kotlin/dev/bee/kanjianki/core/DictionaryLookup.kt`
  - already exposes kanji metadata: readings, stroke count, grade, radical, frequency, rank.
- `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreBase.kt`
  - has `HistoricalNoteSnapshot` fields for note id, expression, reading, meaning, sentence, tags, fields JSON.
- `app/src/main/kotlin/dev/bee/kanjianki/anki/AnkiDroidCardReader.kt`
  - reads AnkiDroid card ids per note during sync.

Implementation should add a small model layer rather than stuffing lookup logic directly into Compose rendering.

## Acceptance criteria

### Functional

- Study answer reveal still shows the current answer content first: glyph, meaning, reading, and `From:` source.
- A new collapsed details region appears only when there is a current kanji/item.
- Expanding “Details” shows available dictionary metadata from local/offline data.
- Expanding “Used in Anki” shows local synced source words/cards containing the kanji, with expression/reading/meaning and stable ordering.
- AnkiDroid opening behavior is audited and implemented safely:
  - if supported, row taps open the corresponding card/note in AnkiDroid;
  - if unsupported or AnkiDroid is unavailable, row taps degrade gracefully with copy/toast fallback.
- Stroke-order support is honest:
  - no fake animation;
  - if assets are not available, show stroke count and route a follow-up card for licensed offline stroke-order assets rather than pretending the feature is done.

### Visual / UX

- Default collapsed state does not make the answer card feel heavier or distract from grading.
- Expanded sections use Kani theme tokens and match the rounded/pink card style.
- No clipped text at 360dp phone width.
- Rating controls remain reachable.
- Empty states are cute and concise, e.g. “No other synced Anki words yet.”
- Screenshot evidence must include at least:
  - one answer card with all sections collapsed,
  - “Details” expanded,
  - “Used in Anki” expanded with multiple rows,
  - fallback empty state,
  - narrow phone width.

### Performance / safety

- No synchronous AnkiDroid provider query on answer reveal.
- Use local synced data / cached indexes for “Used in Anki.”
- Expansion should be instant on representative local data.
- No secrets, raw note field dumps, or unexpected PII in logs/artifacts.
- Deep links/intents must be scoped to AnkiDroid and have a fallback if unavailable.

### Validation

- Unit/model tests for dictionary metadata mapping, local source-word selection, truncation, empty states, and deep-link/fallback decision logic.
- Compose/unit tests or screenshot tests for collapsed/expanded rendering.
- `./gradlew ciFast` before QA if implementation touches app/core code.
- Real app screenshot artifacts before design critique and before QA.

## Kanban graph

KANBAN_GRAPH_PLACEHOLDER
