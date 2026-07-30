# meaning_kanji must ask for the KANJIDIC gloss, not the word's JMdict gloss

Date: 2026-07-28
Status: Planned
Decisions: dictionary-first copy; hard gate (no KANJIDIC gloss ⇒ no choice
card, plain flashcard fallback); dictionary-aware decoy collision guard
included; Games Confusable Clash fixed in the same change (gloss-first with
word-meaning fallback, no gate).

## Problem

The `meaning_kanji` rung renders "Which kanji means Mystery something
inexplicable wonder miracle?" with choices 厨 / 議 / 勢 / 件 (user screenshot
`kanji_look.png`). That gloss is the **JMdict definition of the word 不思議**,
not the kanji-dictionary meaning of the answer 議 ("deliberation;
consultation"). No single kanji "means" mystery/miracle, so the question is
canonically wrong and unanswerable from kanji knowledge alone.

## Root cause

Two things combine:

1. **Kani never receives a kanji-level meaning from Anki.** Each Anki note is
   a *word* with a JMdict gloss. Sync builds per-kanji rows by copying the
   meaning of the first example word containing that kanji
   (`KanjiAnalyzer.RowSummary.addExample`,
   `core/src/main/kotlin/dev/bee/kanjianki/core/KanjiAnalyzer.kt:193-195` →
   `DashboardRow.primaryMeaning`). So 議's row meaning literally *is*
   不思議's JMdict definition.
2. **PR #87 (commit `26788e0f`, 2026-05-31) flipped the copy to
   word-gloss-first.** The app bundles KANJIDIC2
   (`app/src/main/assets/dictionaries/kanji_dictionary.db`, 13,108 kanji) and
   `meaning_kanji` used to prefer it. #87 changed
   `StudyTextCopy.meaningKanjiChoiceMeaning`
   (`core/src/main/kotlin/dev/bee/kanjianki/core/StudyTextCopy.kt:771-782`) to
   prefer `card.primaryMeaning` so the *result* line said "脱 means Loss of
   strength" while studying 脱力. Side effect: the *question* now asks "which
   kanji means <compound word meaning>". The KANJIDIC fallback inside that
   helper is dead code because `MeaningKanjiChoicePlanner` refuses to build a
   card when the row meaning is empty
   (`core/src/main/kotlin/dev/bee/kanjianki/core/MeaningKanjiChoicePlanner.kt:29-31`).

It is also internally inconsistent: `similar_kanji` uses the identical "Which
kanji means X?" copy but is **dictionary-first** (`StudyTextCopy.sessionClue`
→ `canonicalKanjiMeaning`, `StudyTextCopy.kt:18-44`), and the recognition
answer panel is dictionary-first too (`DictionaryLookup.studyCue`). Only
`meaning_kanji` and the Games "Confusable Clash"
(`core/src/main/kotlin/dev/bee/kanjianki/core/KanjiGameEngine.kt:119,230`)
show the word gloss. The instrumented smoke test shows the split directly for
the same kanji 裂: similar_kanji asserts "Which kanji means Split, rend?"
(KANJIDIC) while meaning_kanji asserts "Which kanji means Split?" (row word
meaning)
(`app/src/androidTest/kotlin/dev/bee/kanjianki/MainActivityStudyRouteSmokeInstrumentedTest.kt:208,236`).

## Data flow (for the record)

Anki note field (`note.meaning(settings)`, JMdict word gloss) →
`KanjiAnalyzer` example → first-example meaning copied to the per-kanji
`DashboardRow.primaryMeaning` → persisted to `dashboard_rows.primary_meaning`
(`LocalStoreInventoryMaintenance.saveRows`) → `StudySessionSelector` builds
the session → `MeaningKanjiChoicePlanner.buildChoiceCard` puts
`target.primaryMeaning` on the card → `StudyTextCopy.meaningKanjiChoiceQuestion`
prefers it → rendered by `prepareMeaningKanjiRender`
(`app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyChoiceSessions.kt:126-183`).

## Design

### 1. Copy — dictionary-first (revert of the #87 preference)

`StudyTextCopy.meaningKanjiChoiceMeaning` (`StudyTextCopy.kt:771-782`) returns
to:

```kotlin
canonicalKanjiMeaning(dictionaryLookup, card?.targetKanji, card?.primaryMeaning ?: prompt, maxChars)
```

KANJIDIC gloss (2 glosses via `StudyCueFormatter.displayGlosses`) when the
kanji is in the dictionary; word-gloss/prompt fallback otherwise. The question
and both result branches share this helper, so "Which kanji means
Deliberation, consultation?" / "Correct. 議 means Deliberation, consultation."
stay consistent — the #87 question/result mismatch cannot re-appear in
reverse.

The answer panel is untouched: `StudyCuePolicy.meaningChoiceAnswerLines`
(`core/src/main/kotlin/dev/bee/kanjianki/core/StudyCuePolicy.kt:27-61`) keeps
headlining the compound word meaning with "From 不思議" and the
"Individual kanji meanings" supplement, so the word-level context that #87
cared about remains visible after answering.

### 2. Gate — no KANJIDIC gloss ⇒ no meaning_kanji choice card

`MeaningKanjiChoicePlanner.buildChoiceCard` gains a `DictionaryLookup?`
parameter (new overload; existing overloads delegate with `null` = ungated so
current planner tests stay valid). When a lookup is supplied and the target
kanji has no dictionary gloss (`lookupKanji(target) == null` or
`displayGlosses(entry.meanings, 2)` empty), return `null`. The existing
fallback at `MainActivityStudyChoiceSessions.kt:131-137` then renders a plain
recognition flashcard instead — the same degrade pattern `kanji_reading`
already uses. The call site `meaningKanjiChoiceCardForSession`
(`MainActivityStudyChoiceSessions.kt:415-427`) passes
`home.currentDictionaryLookup()`; it already runs on the io executor with the
dictionary install warmed. If the dictionary is genuinely unavailable (cold
install window), the degradation is a flashcard, which is conservative and
correct. Core already depends on dictionary-core (`StudyTextCopy` references
`DictionaryLookup`), so no module graph change.

### 3. Decoy collision guard

Once the displayed meaning is the KANJIDIC gloss, a randomly drawn decoy whose
own gloss displays identically (e.g. target 思 "Think" with a decoy also
displaying "Think") makes the card unanswerable. The planner's existing dedup
compares *word-level* meanings only (`MeaningKanjiChoicePlanner.kt:42-45`).

With the lookup now available in the planner: when drawing each candidate
decoy (confused seeds and random fill), skip any whose dictionary-first
displayed meaning (gloss if present, else its word meaning) normalizes equal
to the target's displayed gloss. Keep the existing word-level dedup as-is.
Lookups are bounded to the candidates actually drawn (~4-20 per card) and the
`DictionaryStore` entry cache is LRU, so no full-inventory scan.

### 4. Games — Confusable Clash uses the gloss too

`KanjiGameEngine` sets `target.meaning = firstNonEmpty(row.primaryMeaning,
exampleMeaning)` (`KanjiGameEngine.kt:119`) and renders "Which kanji means
${target.meaning}?" (`KanjiGameEngine.kt:230`). Plumb a `DictionaryLookup?`
from `MainActivityGames` (`app/src/main/kotlin/dev/bee/kanjianki/MainActivityGames.kt:12,100`,
via `currentDictionaryLookup()`) and prefer the KANJIDIC gloss with
word-meaning **fallback, not a gate** — game rounds should never vanish
because a kanji is missing from the dictionary; stakes are lower than the
scheduler rung. `KanjiGameCopy.questionPrompt` prefix parsing
(`KanjiGameCopy.kt:125`) is unaffected.

## Test plan

Update pinned tests (these deliberately pin the #87 behavior):

- `core/src/test/java/dev/bee/kanjianki/core/StudyTextCopyTest.kt:463-475` —
  rename `meaningKanjiChoiceCopyUsesTestedCompoundMeaningOverIndividualKanjiGloss`
  and assert "Undress, removing" for question and both result branches.
- `app/src/test/kotlin/dev/bee/kanjianki/AppValueBehaviorTest.kt:44-83` —
  assert "Undress, remove"; the test's existing name
  (`meaningKanjiChoiceCopyUsesDictionaryMeaningWhenAvailable`) finally becomes
  true again.
- `app/src/androidTest/kotlin/dev/bee/kanjianki/MainActivityStudyRouteSmokeInstrumentedTest.kt:236`
  — "Which kanji means Split?" → "Which kanji means Split, rend?", matching
  the similar_kanji assertion at line 208 against the bundled dictionary.
- Sweep `MainActivityHelperInstrumentedTest.kt` (~line 1245 meaning_kanji
  section) and Games copy tests (`KanjiGameCopyTest`,
  `MainActivityGamesCopyComposeTest`) for question-text assertions that flow
  through the real pipeline. Compose-model tests that pass literal question
  strings into the UI (`MainActivityStudyChoiceComposeUnitTest`,
  `MainActivityStudyChoiceComposeTest`, `SimilarChoiceRouteScreenshotTest`,
  `ComposeScreenModelsTest`) do not exercise the copy functions and stay
  unchanged.

Tests expected to pass unchanged:

- No-lookup copy tests (`StudyTextCopyTest.kt:228-274`) — they exercise the
  fallback path, which still returns the cleaned word gloss.
- `canonicalKanjiMeaningFallsBackWhenDictionaryHasNoGloss`
  (`StudyTextCopyTest.kt:41-52`).
- The #87 follow-up coverage (dictionary used when tested meaning is blank)
  is subsumed: dictionary-first covers the blank case trivially.

New coverage:

- Planner gate: target kanji absent from the lookup ⇒ `buildChoiceCard`
  returns `null`; `null` lookup preserves today's behavior.
- Planner decoy guard: a decoy sharing the target's displayed gloss is
  excluded; word-level dedup still applies; deterministic `Random` seed.
- Games: Confusable Clash prompt prefers the gloss and falls back to the word
  meaning when the kanji is missing from the lookup.
- UI fallback: meaning_kanji session for a kanji with no dictionary entry
  renders the plain flashcard route (unit-level via
  `prepareMeaningKanjiRender` if practical).

## Validation and release

- `./gradlew ciFast` is the confidence gate (deterministic JVM tests,
  coverage, app unit tests, androidTest compilation, lint, Python asset
  tests).
- No provider/sync surface is touched, so the local live-AnkiDroid gate is
  not required for this change.
- Docs: update `docs/ladder-and-srs-system.md` §4.4 if it describes the
  meaning_kanji meaning source.
- Merge to `main` auto-releases the next patch via `android-release.yml`.

## Out of scope

- Rewording the card ("Which kanji completes 不〇議 (mystery)?") — overlaps
  the `reading_kanji` design; not needed once the gloss is correct.
- Dictionary-based dedup for `similar_kanji` decoys — pre-existing, separate
  concern (its decoys come from curated visually-similar pairs, not random
  fill).
- Restructuring `StudyCuePolicy.meaningChoiceAnswerLines` ordering.
