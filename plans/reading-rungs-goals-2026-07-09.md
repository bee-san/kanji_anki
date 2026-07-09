# Reading-Aware Ladder Rungs — Goals 75–81 (2026-07-09)

## Asks

The requests this document answers, verbatim:

1. "Can we add more rungs into the ladder? Like if user fails a kanji
   repeatedly in anki with reading Y, but they have mature kanji with
   reading X we can assume its because they are unfamiliar with that
   reading. Make a plan for this and come up with other ladder rung ideas,
   I have Anki running on this laptop with my personal collection so you
   can explore that too."
2. Scope decision (interactive): implement **both reading rungs plus a
   sentence-reading rung** ("Both + sentence_reading").
3. Placement decision (interactive): `kanji_reading` sits **below
   `word_reading`** so word-reading failures demote straight into
   targeted reading drills.
4. Auto-enable decision (interactive): existing installs get the new
   rungs **auto-enabled** in their stored ladder config, like
   `meaning_kanji` was.
5. Anki-lapse decision (delegated: "you decide what is best"): ladder
   **movement stays purely in-app**; Anki-side maturity/lapse data is used
   **only inside content planners** (distractor eligibility and contrast
   targeting), never as movement evidence. Rationale in D-R1 below.
6. "Write the plan extensively to a new file, make each step have a /goal
   that defines what the end state is, whether tests pass etc, this is
   for claude's /goal feature."

Goal numbers continue from 74 (`plans/ladder-steps-deep-review-2026-07-08.md`
ends at Goal 74) because goal numbers are globally unique across all plan
files in this repo — commit messages and AGENTS.md reference bare goal
numbers, so "Goal 78" must always mean exactly one thing.

## How to use this file (the `/goal` blocks)

Each goal below contains a fenced block starting with `/goal`. That block
is a **self-contained end-state statement** written for an AI agent's goal
feature: paste it as the goal definition and the agent has the complete
success criteria without reading anything else. The surrounding sections
(Problem / Design / Implementation map / Done when) are the extended
context the agent should read when it starts work. The "Done when
(machine-checkable)" list is the authoritative completion contract; the
`/goal` block is a faithful compression of it.

Work the goals **in order**. Goals 75–77 are foundations; Goals 78–80 are
one rung each and each is independently shippable once its dependencies
have landed; Goal 81 is the release gate.

| Goal | Deliverable | Depends on |
| --- | --- | --- |
| 75 | Generalized conditional-rung availability (`RungAvailability`) | — |
| 76 | `KanjiReadingAligner` (pure reading–kanji alignment engine) | — |
| 77 | `kanji_reading_usage` table + sync-save maintenance + predicates (DB v26) | 76 |
| 78 | `kanji_reading` rung — "how is 脱 read in 脱出?" (DB v27) | 75, 77 |
| 79 | `reading_kanji` rung — homophone discrimination (DB v28) | 75, 77 (78 recommended first) |
| 80 | `sentence_reading` rung — new ladder ceiling (DB v29) | 75 |
| 81 | Docs consistency pass + full local gate + live AnkiDroid gate | 75–80 |

## Base state (as of this plan)

- HEAD: `e936d866` "feat(scheduler): Goal 67 — require clean-write
  evidence to leave write_kanji". Goals 63/64/65/67/70 from the ladder
  deep review have landed.
- The working tree has **uncommitted work** touching
  `core/.../SimilarKanjiChoicePlanner.kt`,
  `app/.../data/LocalStoreInventory.kt` and their tests (looks like
  Goal 69, "availability means a choice card can actually be built").
  Land or stash that before starting Goal 75; Goal 75 rewires the same
  seams.
- `LocalStoreSchema.DB_VERSION == 25`
  (`app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreSchema.kt:7`).
  This plan allocates v26 (Goal 77), v27 (Goal 78), v28 (Goal 79),
  v29 (Goal 80). If the tree has moved when you start a goal, take the
  next free version and keep the per-goal ordering.
- Current 7-rung default ladder (bottom → top):
  `write_kanji, type_meaning, meaning_kanji, similar_kanji, kanji_meaning,
  font_meaning, word_reading` (`RecordsBase.defaultsOrder()`,
  `RecordsBase.kt:310-328`). Note `similar_kanji` sits **directly below**
  `kanji_meaning` — that is Goal 65's invariant (first demotion from the
  start rung reaches the signature discrimination remediation in one
  step) and this plan must preserve it.
- Scheduler goldens on disk (9 files in
  `core/src/test/resources/dev/bee/kanjianki/core/scheduler-goldens/`):
  `ceilingCardDemotesOneRungWhenCold`, `demotionWithEmptyRelearningSteps`,
  `newKanjiEntersKanjiMeaning`, `promotionRequiresSecondRealDuePass`,
  `relearningBeatsSameFamilyReviewSibling`,
  `reviewPassPromotesAfterLongFsrsInterval`,
  `similarKanjiSkippedWithoutContent`, `threeDueReviewAgainsDemote`,
  `writeKanjiExitRequiresCleanWrites`.
- Line numbers cited below are as of this review; **search for the symbol
  if they drift**.

---

## Evidence — validated against the author's live Anki collection

All exploration was read-only (`sqlite3 "file:...collection.anki2?immutable=1"`)
against `~/Library/Application Support/Anki2/User 1/collection.anki2` on
2026-07-09, cross-referenced with the app's bundled KANJIDIC2 asset
`app/src/main/assets/dictionaries/kanji_dictionary.db` (13,108 kanji;
table `kanji(literal, meanings, on_readings, kun_readings, …)`, list
fields `\x1f`-separated).

### The collection

- Kiku note model (mid `1762071852534`): **8,597 notes**, fields
  `Expression / ExpressionFurigana / ExpressionReading / … / Sentence /
  SentenceFurigana / …` — 626 single-kanji RRTK-style notes (no reading),
  **5,653 multi-character word notes**, 8,240 notes with a non-empty
  `Sentence`.
- Card maturity: 5,615 of 8,597 Kiku cards mature (ivl ≥ 21).
- `ExpressionFurigana` holds either plain whole-word kana (`勉強` →
  `べんきょう`) or Anki bracket furigana, sometimes multi-segment
  (`一[いっ]か 月[げつ]`, `知[し]り 合[あ]い`).
- `ExpressionReading` is always plain kana. **This is the field the app
  already syncs** (settings key `reading_field`, auto-guessed by
  `NoteTypeFieldMappingPolicy`), stored per example in `kanji_examples`.
  No sync-schema change is required by this plan.

### The alignment experiment

A dynamic-programming aligner (spec in Goal 76) maps each word's kana to
per-kanji readings using only KANJIDIC2 readings plus three closed
phonological rules (rendaku, sokuon, 々). Measured coverage:

- **92%** of the 5,593 eligible word notes align via the furigana field.
- **95%** (5,239/5,514) align via the plain `ExpressionReading` field —
  i.e. the field the app already has. Plain-kana DP alignment is
  sufficient; furigana parsing is a bonus, not a requirement.
- Residual failures are jukujikun/ateji (明日=あした, 今日=きょう,
  大人=おとな, 部屋=へや, お母さん…) and lexicalized truncations
  (日本=にほん where 日=に). These words simply produce no reading-usage
  rows. Because the new rungs are data-conditional, that is safe by
  construction — those cards just never see the rung.

### The hypothesis check (the user's core idea)

Definition used: a card is *struggling* if `lapses ≥ 3` or
(`reps ≥ 8` and `ivl < 21`). A reading is *mature for kanji K* if some
aligned word using K with that reading has `ivl ≥ 21` and `lapses ≤ 2`.

- Struggling word cards in the collection: **736**.
- Struggling cards where at least one kanji is used with a reading that
  has **no mature evidence, while the same kanji IS mature under a
  different reading**: **153 cards across 131 kanji (~22% of struggles)**.
- Real examples found (these become planner/aligner test fixtures):

| Word | Failing reading | Mature contrast | Evidence |
| --- | --- | --- | --- |
| 脱出 | 脱=だつ | 脱=ぬ (脱ぐ) | lapses=11 |
| 低い | 低=ひく | 低=てい | lapses=10 |
| 撮影 | 撮=さつ | 撮=と (撮る) | lapses=8 |
| 音楽 | 音=おん | 音=おと/ね | lapses=8 |
| 教える | 教=おし | 教=おそ (教わる) | lapses=7 |
| 開く | 開=ひら | 開=あ/かい | lapses=7 |
| 心配 | 配=ぱい(はい) | 配=はい mature ✱rendaku canonicalization | lapses=7 |
| 方向 | 向=こう | 向=む | lapses=5 |
| 学ぶ | 学=まな | 学=がく | lapses=5 |
| 余計 | 余=よ | 余=あま | lapses=4 |

- Kanji with ≥ 2 distinct attested readings in the user's own words
  (eligibility pool for `kanji_reading`): **606**.
- Readings with ≥ 3 mature kanji sharing them (homophone distractor
  pools for `reading_kanji`): **143** — e.g. し×8, き×8, か×7, い×6,
  た×6, せい×5, しょう×5.
- Notes with sentences (eligibility pool for `sentence_reading`):
  8,240/8,597.

Conclusion: the user's hypothesis is validated on their real data, the
required data is already on-device after a normal sync (examples +
readings) plus the already-bundled dictionary, and all three rungs have
large eligible populations.

---

## Design

### New default ladder (bottom = most scaffolded → top = least scaffolded)

```
 1. write_kanji                       existing floor
 2. type_meaning                      existing
 3. meaning_kanji                     existing
 4. reading_kanji     NEW conditional — homophone discrimination:
                                       reading + blanked word → pick the kanji
                                       among same-reading kanji
 5. similar_kanji         conditional existing — visual discrimination,
                                       stays directly below kanji_meaning
                                       (Goal 65 invariant preserved)
 6. kanji_meaning                     existing — new-card start rung (unchanged)
 7. font_meaning                      existing
 8. kanji_reading     NEW conditional — "How is 脱 read in 脱出?" —
                                       choice over the kanji's other readings
 9. word_reading                      existing
10. sentence_reading  NEW conditional — new ceiling: read the word inside
                                       the user's own mined sentence
```

Placement rationale:

- `kanji_reading` directly below `word_reading`: a `word_reading` fail
  streak demotes **directly into** targeted reading discrimination — the
  user's exact scenario. Promotion out of `font_meaning` validates
  reading choice before full reading recall.
- `reading_kanji` directly below `similar_kanji`: it is the phonetic
  sibling of the visual-discrimination rung, and both are
  forced-choice/objectively-graded (respects implicit principle P10,
  "grading objectivity decreases as trust increases", from
  `plans/ladder-steps-deep-review-2026-07-08.md`). Placing it *below*
  `similar_kanji` — not between `similar_kanji` and `kanji_meaning` —
  preserves the Goal 65 invariant that the first demotion from the start
  rung reaches `similar_kanji` in one step; phonetic discrimination is
  the second demotion stop for cards with data.
- `sentence_reading` as new ceiling: contextual reading is the
  least-scaffolded skill (P9, scaffolding gradient). Cards without
  sentence data keep `word_reading` as their effective ceiling because
  `nextRung` returns the current rung when no higher rung is valid.

### Naming

Wire names follow the existing prompt→answer convention
(`kanji_meaning` = see kanji, answer meaning; `meaning_kanji` = see
meaning, pick kanji):

- `kanji_reading` — see kanji (in word context), answer its reading.
- `reading_kanji` — see reading (plus word/meaning cue), pick the kanji.
- `sentence_reading` — see sentence, answer the target word's reading.

Enum constants: `KANJI_READING`, `READING_KANJI`, `SENTENCE_READING`.
Enum declaration order is storage-compatibility-only (serialization is by
wire name — `RecordsBase.kt:13-18`); append new constants at the end of
the enum and control position exclusively via `defaultsOrder()`.

### Conditional availability predicates (computed on read, never persisted,
exactly like `hasSimilarKanji`)

- `hasKanjiReading(kanji)` — the kanji has ≥ 1 aligned usage row (a real
  word to prompt with) AND ≥ 2 distinct canonical readings are available
  for choices (attested readings ∪ bundled-dictionary readings). Per the
  Goal 69 lesson, availability must mean **a choice card can actually be
  built**.
- `hasReadingKanji(kanji)` — some canonical reading attested for this
  kanji is shared by ≥ 2 other inventory kanji (attested usage preferred,
  dictionary readings of inventory kanji as fallback), so a ≥ 3-choice
  card can be built.
- `hasSentenceReading(kanji)` — ≥ 1 example row for the kanji has a
  non-blank sentence AND a non-blank reading.

### Decisions of record

- **D-R1 — Ladder movement stays in-app.** Anki-side lapse counts do NOT
  feed promotion/demotion. The scheduler contract (AGENTS.md: "only
  persisted FSRS-due review attempts in the review phase count toward
  ladder movement") is untouched. Anki-side maturity/lapse data is used
  only inside planners: mature attested readings qualify as distractors,
  and lapse-heavy readings are preferred as the tested contrast. This is
  the same pattern `similar_kanji` already uses (confusion-weighted
  choices) — targeting without touching movement semantics.
- **D-R2 — Canonical readings.** Usage rows and choice options are keyed
  by *canonical* reading: on-readings converted to hiragana; kun-readings
  canonicalized to the pre-`.` stem (教=おし.える → おし); rendaku/sokuon
  surface variants map back to their base (配=ぱい → はい, 脱=だっ →
  だつ). This groups evidence correctly (心配's ぱい counts as はい
  evidence) and keeps choice lists free of near-duplicate options.
- **D-R3 — Jukujikun exclusion is by design.** Words that fail alignment
  produce no usage rows and therefore never gate or feed the new rungs.
  Do not add per-word special cases.
- **D-R4 — Auto-enable on upgrade.** All three rungs are auto-enabled for
  stored configs that predate them, generalizing the existing
  `MEANING_KANJI` clause in `StudyLadderSettings.fromStored`. A user's
  stored *order* is still preserved verbatim modulo the documented
  `insertMissingRung` splice.
- **D-R5 — No parallel queues.** All three rungs are ordinary ladder
  rungs over `study_items`. `kanji_reading_usage` is a content/data
  table like `similar_kanji_pairs`, not a scheduler queue (AGENTS.md
  single-state-machine rule).

### Out of scope (evaluated, deliberately deferred)

- Audio rungs / pitch-accent rungs — require media sync or new field
  mapping; the app deliberately does not read media (AGENTS.md).
- Okurigana-choice variant (教える vs 教わる) — natural future extension
  of `kanji_reading`; KANJIDIC `.`-split already provides the data.
- `word_meaning` rung — low differentiation vs `kanji_meaning`.
- Speeded-recognition rung — orthogonal; needs no new data, can be a
  later experiment.
- Anki-lapse-driven placement/seeding — rejected for v1 (D-R1).

---

## Cross-cutting reference — the "add a rung" touch-point map

Verified against the tree at `e936d866`. Every rung goal (78/79/80) walks
this list; it exists once here so the goals can reference it as
"the touch-point map".

1. **Enum + wire name:** `RecordsBase.LadderRung`
   (`core/src/main/kotlin/dev/bee/kanjianki/core/RecordsBase.kt:19-48`) —
   append constant at the end; `fromWireName` defaults unknown names to
   `KANJI_MEANING` (safe downgrade). Add the wire-name constant to
   `core/.../StudyTaskTypes.kt` and the `TASK_*` re-export in
   `core/.../BridgeScheduler.kt:548-557`.
2. **Default order + auto-enable:** `defaultsOrder()`
   (`RecordsBase.kt:310-328`) at the position specified by the goal.
   `normalizeOrder()`/`insertMissingRung()` (`RecordsBase.kt:242-282`)
   automatically splice the new rung into old stored orders adjacent to
   its default neighbors — add a `StudyLadderSettingsTest` case proving
   it. Auto-enable: extend the `fromStored` clause
   (`RecordsBase.kt:207-221`); Goal 78 generalizes the single
   `MEANING_KANJI` special case into an auto-enable set.
3. **Conditional plumbing (after Goal 75):** add the flag to
   `RungAvailability`, exclude the rung in `alwaysAvailable`
   (`RecordsBase.kt:224-226` — conditional rungs are NOT
   always-available), add the item flag + annotate-on-read (see step 6),
   and add the `<wire>_unavailable` trace reason in the generalized
   skip-reason helper (`core/.../ReviewTransitionEngine.kt:139-150`).
4. **Per-rung memory slot:** new field on `RecordsStudyModels.StudyItem`
   plus routing in `memoryForRung` (`core/.../RecordsStudyModels.kt:371-382`),
   `memoryForTaskType` (`:356-369`), `withTaskMemory` (`:384-397`), and
   `copyBuilder`. Extend `StudyItemArgs.from`
   (`RecordsStudyModels.kt:666-748`) with exactly one new positional
   arg-count shape (the new memory goes at the end, following the
   `meaning_kanji_memory`/`similar_kanji_memory` precedent); keep all
   existing shapes frozen. Prefer `copyBuilder()` for all new call
   sites (open decision D5 in `docs/ladder-and-srs-system.md` — do not
   let test code proliferate new positional calls).
5. **DB column + migration:** one TEXT memory column, default `''`.
   Template: migration v19 (`app/.../data/LocalStoreMigrations.kt:116-118`
   added `meaning_kanji_memory`). Touch: `LocalStoreBase.STUDY_ITEMS_TABLE_SQL`
   + column constants (`app/.../data/LocalStoreBase.kt:482-587`),
   `upsertStudyItem`/`readStudyItem`
   (`app/.../data/LocalStoreHistory.kt:436-531`), `DB_VERSION` bump
   (`app/.../data/LocalStoreSchema.kt:7`), and a
   `LocalStoreMigrationsTest`/`StudySchedulerMigrationTest`-style unit
   test proving old→new upgrade preserves rows and defaults the column.
6. **Availability annotation on read:** follow the
   `kanjiWithSimilarNeighbors` pattern
   (`app/.../data/LocalStoreInventory.kt:473-504`): cached set query +
   annotate in `studyItems()`, `studyItemsForKanji()`, and the
   post-seeding annotate seam used by
   `ManualSyncEngine.runLocked` (`app/.../sync/ManualSyncEngine.kt:144`),
   `HomeStudyQueueActions`, `MainActivityHomeFocusQueue`,
   `StudyMoreNewCardActions`.
7. **Session routing + UI:** `core/.../StudySessionRoute.kt`
   (destinations `WRITING / SIMILAR_KANJI / MEANING_KANJI / FLASHCARD`) —
   choice rungs add a destination + a `prepare*Render` in
   `app/.../MainActivityStudyChoiceSessions.kt` with the `< minChoices` →
   flashcard fallback pattern (`:70-76`); flashcard-variant rungs instead
   extend `composeFlashcardRouteModel`
   (`app/.../MainActivityStudyFlashcard.kt:56-96`) and the task-type
   predicates in `core/.../StudyTaskCopy.kt:98-113`. Ratings at the
   boundary stay `good`/`again` (Pass/Fail labels via
   `core/.../StudyReviewButtonCopy.kt`).
8. **Wrong-pick logging (choice rungs):** reuse
   `recordChoiceReviewLog` (`app/.../data/LocalStoreSimilarKanji.kt:206-233`);
   the `similar_kanji_review_log.rung` column already exists (migration
   v24) — pass the new rung's wire name.
9. **Copy (compiler-enforced exhaustive `when(rung)` switches, EN + JA):**
   `core/.../StatsTextCopy.kt:304-326` (`ladderRungLabel`),
   `core/.../SettingsStudyPlanTextCopy.kt:236-255` (label + the
   conditional-rung subtitle pattern "Included when …"),
   `core/.../FocusQueueCopy.kt:73-95`,
   `core/.../StudyTaskCopy.kt:22-65` (`labelForTask`, `flashcardTitle`,
   mode label), plus question/prompt strings in
   `core/.../StudyTextCopy.kt`.
10. **Scheduler behavior tests:**
    `core/src/test/kotlin/dev/bee/kanjianki/core/LadderSchedulerTest.kt`
    (promotion/demotion through the rung, skip-when-unavailable incl.
    chained skips), `StudyLadderSettingsTest` (defaults order,
    auto-enable, splice), `LadderHealthPolicyTest` (distribution map
    auto-includes new rungs via `LadderRung.values()` — assert it),
    copy tests (`SettingsStudyPlanTextCopyTest`, `StatsTextCopyTest`,
    `FocusQueueCopyTest`, `StudyTaskCopyTest`, `SettingsTextCopyTest`),
    `RecordsValueModelsTest` (memory slot round-trip), golden timelines +
    `SchedulerParitySnapshotTest` (regenerate deliberately, review the
    diff), app tests (`StatsCacheCodecTest`, migration test), instrumented
    (`LadderSchedulerEndToEndTest`, `SettingsStudyLadderComposeTest`,
    `LocalStoreInstrumentedTest` rung persistence).
11. **Docs in the same commit:** AGENTS.md "Study Scheduler Notes"
    (default order list + rung rendering list + legacy note "no legacy
    source; reached through configured ladder movement"),
    `docs/ladder-and-srs-system.md` (§ rung table, § conditional rungs,
    §14 decision log).

Golden regeneration procedure (there is no regen flag): run
`SchedulerTimelineSimulatorTest`, take the failing diff's actual
`renderText()` output, write it into the corresponding
`core/src/test/resources/dev/bee/kanjianki/core/scheduler-goldens/<name>.timeline.txt`,
re-run, and **review the diff like a code review** — every changed line
must be explainable by the intended behavior change (e.g. a new
`<wire>_unavailable` reason code appearing on crossings). Same for
`scheduler-parity/scheduler-parity.snapshot.txt` via
`SchedulerParitySnapshotTest`.

---

## Goal 75: Generalize conditional-rung availability beyond `hasSimilarKanji`

**Problem:** Every conditional-rung seam is hardcoded to one boolean.
`StudyLadderSettings.isValidForItem(rung, hasSimilarKanji: Boolean)`
(`RecordsBase.kt:88-90`) special-cases `SIMILAR_KANJI`;
`startingRung`/`effectiveRung`/`nextRung`/`previousRung`
(`RecordsBase.kt:138-193`) all thread that single boolean;
`ReviewTransitionEngine.skipsSimilarRungWithoutContent`
(`ReviewTransitionEngine.kt:139-150`) hardcodes the
`similar_kanji_unavailable` trace code; `StudyItem.hasSimilarKanji`
(`RecordsStudyModels.kt:256-257`) is the only availability flag. Three
new conditional rungs (Goals 78–80) cannot be added without either this
generalization or a 4-boolean parameter list in a dozen signatures.

**Design:** Introduce `RecordsBase.RungAvailability`, an immutable value
object answering `isAvailable(rung: LadderRung): Boolean`. Always-available
rungs return true unconditionally; conditional rungs consult a flag. At
this goal it carries only `hasSimilarKanji`; Goals 78–80 each add one
flag. This goal is a **pure behavior-neutral refactor**: all goldens and
the parity snapshot must remain byte-identical.

**Implementation map:**

- `core/.../RecordsBase.kt`: add `RungAvailability` (suggested:
  `data class`-like plain class with `@JvmField val hasSimilarKanji:
  Boolean`, a `NONE`/`none()` constant meaning "no conditional data", and
  `isAvailable(rung)` returning `alwaysAvailable(rung) || flagFor(rung)`).
  Change `isValidForItem`, `startingRung`, `effectiveRung`, `nextRung`,
  `previousRung` to take `availability: RungAvailability` instead of
  `hasSimilarKanji: Boolean`. Keep thin `@Deprecated` boolean overloads
  ONLY if the call-site sweep is too large for one commit; prefer a full
  sweep (call sites: `StudyLadderRules`, `ReviewTransitionEngine`,
  `StudyQueueSeeder`, `TargetedStudySessionPolicy`, `StudySessionSelector`,
  `LadderHealthPolicy` if it filters, tests).
- `core/.../RecordsStudyModels.kt`: keep the existing
  `hasSimilarKanji` field and `withHasSimilarKanji` (widely used,
  never persisted); add `fun rungAvailability(): RungAvailability`
  assembling the value object from the item's flags. Goals 78–80 add
  sibling fields + `with*` setters and extend `rungAvailability()`.
- `core/.../ReviewTransitionEngine.kt`: replace
  `skipsSimilarRungWithoutContent` with a generalized helper that, given
  the crossed-over rungs between the old and new rung, emits one
  `"<wireName>_unavailable"` reason code per skipped conditional rung.
  For `similar_kanji` the emitted string must remain exactly
  `similar_kanji_unavailable` (golden compatibility).
- `core/.../StudyLadderRules.kt` `rungsForItem`/`alignRungToLadder`:
  switch to `RungAvailability`.
- App annotate-on-read seams keep setting `withHasSimilarKanji` — no app
  behavior change in this goal.

```
/goal Refactor the kanji_anki ladder's conditional-rung plumbing to a
RungAvailability value object with zero behavior change. End state:
(a) RecordsBase.RungAvailability exists; isValidForItem, startingRung,
effectiveRung, nextRung, previousRung on StudyLadderSettings accept it
instead of a hasSimilarKanji boolean, and all core/app call sites compile
against the new signatures; (b) StudyItem gains rungAvailability() while
keeping the existing hasSimilarKanji field and withHasSimilarKanji;
(c) ReviewTransitionEngine emits skip-reason codes via a generalized
per-rung "<wire>_unavailable" helper that still emits exactly
"similar_kanji_unavailable" for similar_kanji; (d) every golden timeline in
core/src/test/resources/dev/bee/kanjianki/core/scheduler-goldens/ and the
scheduler-parity snapshot are byte-identical to before (git diff clean on
those resources); (e) ./gradlew :core:test passes with zero golden edits;
(f) ./gradlew ciFast exits 0. If any golden changes, the refactor is
wrong — fix the code, never the golden.
```

**Done when (machine-checkable):**

1. `./gradlew :core:test` exits 0 with `git diff --stat` showing **no
   change** under `core/src/test/resources/` (goldens + parity snapshot
   byte-identical).
2. New `LadderSchedulerTest`/`StudyLadderSettingsTest` cases:
   `RungAvailability` with `hasSimilarKanji=false` reproduces today's
   skip behavior; `alwaysAvailable` rungs are available regardless of
   flags; `LadderRung.SIMILAR_KANJI` crossing still yields
   `similar_kanji_unavailable`.
3. No production call site passes a bare boolean for availability any
   more (grep for `hasSimilarKanji: Boolean` in `core/` signatures —
   only `StudyItem`'s field and `withHasSimilarKanji` remain).
4. `./gradlew ciFast` exits 0.

---

## Goal 76: `KanjiReadingAligner` — pure reading–kanji alignment engine

**Problem:** Nothing in the app can attribute a word's kana reading to
its constituent kanji. That attribution is the data foundation for all
three new rungs. The algorithm below was validated against the author's
live collection at 92–95% coverage (see Evidence).

**Design:** A pure, deterministic Kotlin object in `core` (it may live in
`dictionary-core` if `core` cannot see `DictionaryLookup`; check module
deps — `DictionaryLookup` is in `dictionary-core`,
`dictionary-core/src/main/kotlin/dev/bee/kanjianki/core/DictionaryLookup.kt`,
and `core` already consumes it for typing match; place the aligner next
to its data). No Android imports. No I/O — the caller supplies a
`DictionaryLookup`.

Algorithm (validated spec — implement exactly, each rule has a fixture):

1. **Normalization:** katakana → hiragana by codepoint shift (U+30A1–
   U+30F6 minus 0x60); strip HTML tags; strip `、。,，` and whitespace
   from kana; `&nbsp;` → space before segmenting.
2. **Reading inventory per kanji** from `DictionaryLookup.KanjiEntry`:
   - on-readings → hiragana, canonical = the hiragana on-reading itself;
   - kun-readings: for `おし.える` add stem `おし` and full `おしえる`,
     both canonical = stem (`おし`); readings with `-` prefixes/suffixes
     are used with the `-` stripped;
   - **rendaku variants:** if the reading starts with か/き/く/け/こ/さ/し/
     す/せ/そ/た/ち/つ/て/と/は/ひ/ふ/へ/ほ, add the voiced form(s)
     (k→g, s→z, t→d, h→b AND h→p), canonical = base reading;
   - **sokuon variants:** if the reading ends in つ/ち/く/き, add the
     form with the final syllable replaced by っ (だつ → だっ), canonical
     = base; also add the combined rendaku+sokuon form;
   - the variant→canonical map is exposed so callers store canonical
     readings (D-R2).
3. **Iteration mark:** 々 (and only 々; treat 〆/ヶ as ordinary literal
   chars) takes the *previous* kanji's reading inventory and attributes
   the match to the previous kanji's literal with its canonical reading
   (人々 → 人=ひと, 人=ひと via びと).
4. **Two input shapes:**
   - `alignPlain(expression, kana)` — DP over expression positions ×
     kana positions: kanji positions consume any inventory variant
     (longest-first for determinism), non-kanji positions consume exactly
     their own normalized char; success requires consuming both strings
     fully. Memoized; first successful parse wins.
   - `alignFurigana(expression, furigana)` — if the furigana contains
     `[`, parse Anki bracket segments `text[kana]` (regex
     `\s?([^\s\[\]]+)\[([^\]]+)\]`); pure-kanji segments align via the
     run aligner; mixed segments via `alignPlain`; if any segment fails,
     the whole word fails. If no `[`, delegate to `alignPlain`.
5. **Output:** `null` on failure (jukujikun etc. — D-R3), else an ordered
   list of `(kanjiLiteral, canonicalReading)` pairs, one per kanji
   occurrence.

**Implementation map:** new file
`dictionary-core/src/main/kotlin/dev/bee/kanjianki/core/KanjiReadingAligner.kt`
+ test `dictionary-core/src/test/kotlin/dev/bee/kanjianki/core/KanjiReadingAlignerTest.kt`
(create the test source set if `dictionary-core` lacks one; mirror
another module's test wiring in its `build.gradle.kts`). The test builds
a small in-memory `DictionaryLookup` fixture with the exact KANJIDIC
readings for the fixture kanji (copy them from
`app/src/main/assets/dictionaries/kanji_dictionary.db`, e.g.
好=コウ/この.む,す.く,よ.い,い.い; 脱=ダツ/ぬ.ぐ,ぬ.げる;
配=ハイ/くば.る; 学=ガク/まな.ぶ; 人=ジン,ニン/ひと).

**Required fixtures (all validated against the live collection):**

| Input (expr, kana) | Expected |
| --- | --- |
| 勉強, べんきょう | 勉=べん, 強=きょう |
| 時間, じかん | 時=じ, 間=かん |
| 好き, すき | 好=す |
| 学ぶ, まなぶ | 学=まな |
| 脱出, だっしゅつ | 脱=だつ (sokuon canonicalized), 出=しゅつ |
| 心配, しんぱい | 心=しん, 配=はい (rendaku h→p canonicalized) |
| 引っ張る, ひっぱる | 引=ひ, 張=は (canonicalized from ぱ) |
| 人々, ひとびと | 人=ひと, 人=ひと (々 + rendaku) |
| 毎日, まいにち | 毎=まい, 日=にち |
| 見る, みる | 見=み |
| カタカナ reading input (e.g. 時間, ジカン) | same as hiragana |
| 勉強中[べんきょうちゅう] (furigana form) | 勉=べん, 強=きょう, 中=ちゅう |
| 知[し]り 合[あ]い (multi-segment furigana) | 知=し, 合=あ |
| 今日, きょう | null (jukujikun) |
| 明日, あした | null |
| 大人, おとな | null |
| 日本, にほん | null (lexicalized truncation 日=に — accepted miss) |

```
/goal Implement KanjiReadingAligner in the kanji_anki repo: a pure,
deterministic Kotlin aligner (no Android/no I/O; DictionaryLookup passed
in) that attributes a Japanese word's kana reading to its constituent
kanji as (kanjiLiteral, canonicalReading) pairs, per the spec in
plans/reading-rungs-goals-2026-07-09.md Goal 76 (kata→hira normalization,
on/kun inventories with kun stem canonicalization, rendaku h→b/p + k→g +
s→z + t→d variants, sokuon variants incl. combined, 々 iteration mark
attributed to the previous kanji, plain-kana DP alignment plus Anki
bracket-furigana segment parsing, null on failure). End state: (a) the
aligner lives in dictionary-core with zero Android deps; (b) every
fixture in the Goal 76 fixture table passes exactly as specified,
including the four expected-null jukujikun cases and the two furigana
forms; (c) alignment is deterministic (longest-reading-first) — a
property test or repeated-run assertion proves stable output; (d)
./gradlew :dictionary-core:test (or the module's test task) exits 0;
(e) ./gradlew ciFast exits 0. No app wiring, no DB changes in this goal.
```

**Done when (machine-checkable):**

1. All fixture-table cases pass in `KanjiReadingAlignerTest` via
   `./gradlew :dictionary-core:test` (adjust task name to the module's
   actual test task; add the test source set if missing).
2. Null-on-failure proven for 今日/明日/大人/日本 with the real
   dictionary readings for those kanji in the fixture lookup.
3. Determinism test passes (same input → identical output across runs;
   longest-first tie-break pinned by at least one ambiguous fixture).
4. No `android.*` imports in the new file (grep).
5. `./gradlew ciFast` exits 0.

---

## Goal 77: `kanji_reading_usage` table, sync-save maintenance, and availability predicates (DB v26)

**Problem:** The aligner (Goal 76) is pure; nothing persists its output.
The three rungs need queryable per-kanji reading-usage data with Anki
maturity evidence, rebuilt on every sync like `similar_kanji_pairs`.

**Design:** One new content table (NOT a scheduler queue — D-R5):

```sql
CREATE TABLE IF NOT EXISTS kanji_reading_usage (
  kanji TEXT NOT NULL,
  reading TEXT NOT NULL,           -- canonical (D-R2)
  expression TEXT NOT NULL,        -- the aligned word
  note_id INTEGER NOT NULL,
  source_type TEXT NOT NULL,       -- active | suspended (mirror Example.sourceType)
  mature INTEGER NOT NULL,         -- Anki-side: ivl >= 21
  lapses INTEGER NOT NULL,         -- Anki-side lapse count for the card(s)
  interval_days INTEGER NOT NULL,
  PRIMARY KEY (kanji, reading, note_id)
)
```

Rebuild delete-all + reinsert inside the sync save, exactly where
`rebuildSimilarKanjiPairs` runs (`app/.../data/LocalStoreSimilarKanji.kt:16-25`
→ `app/.../data/LocalStoreSimilarKanjiMaintenance.kt:14-29`): iterate the
freshly analyzed rows' examples (`DashboardRow.examples` /
`kanji_examples` source data — expression, reading, mature, lapses,
intervalDays already exist per example,
`core/.../RecordsImportModels.kt:188-251`), call
`KanjiReadingAligner.alignPlain(expression, reading)`, and insert one row
per aligned (kanji, canonicalReading) pair. Words that fail alignment are
skipped silently (D-R3). Single-kanji expressions with an empty reading
field contribute nothing.

Predicate queries in `app/.../data/LocalStoreInventory.kt` following the
`kanjiWithSimilarNeighbors` pattern (`:473-491`, cached set + invalidation
on rebuild):

- `kanjiWithKanjiReading(db)` — kanji having ≥ 1 usage row AND ≥ 2
  distinct available readings, where "available readings" = attested
  readings in the table ∪ dictionary readings of that kanji (the planner
  needs ≥ 2 total choices; dictionary lookup happens at the app seam
  where `DictionaryLookup` is available — if plumbing the dictionary into
  the store is awkward, persist a second tiny table or a
  `dict_reading_count` column computed at rebuild time; choose the
  simplest thing that keeps the predicate a pure SQL query).
- `kanjiWithReadingKanji(db)` — kanji K having some attested canonical
  reading r such that ≥ 2 *other* kanji in `kanji_inventory` also have r
  attested (usage rows) — dictionary-only fallback pools are computed in
  the planner, but availability requires the stronger attested pool so a
  card is always buildable (Goal 69 lesson).
- `kanjiWithSentenceReading(db)` — kanji having ≥ 1 example row with
  non-blank sentence AND non-blank reading (queries `kanji_examples`,
  no new table needed; verify the columns via
  `app/.../data/LocalStoreSchema.kt` / `LocalStoreTableCreator`).

Migration **v26** creates the table (+ index on `(kanji)` and
`(reading)`); bump `DB_VERSION` 25 → 26. Table DDL lives with the other
creators in `app/.../data/LocalStoreTableCreator.kt`.

Also annotate items on read: extend the annotate seams to set the three
new `StudyItem` flags introduced by Goals 78–80 — at THIS goal, only add
the queries + wire `hasKanjiReading` groundwork behind them; the item
flags land with their rungs. Keep this goal's public surface: table,
rebuild, three predicate set-queries, each unit-tested.

```
/goal Add the kanji_reading_usage data foundation to kanji_anki. End
state: (a) migration v26 (DB_VERSION 25→26) creates kanji_reading_usage
(kanji, reading, expression, note_id, source_type, mature, lapses,
interval_days; PK kanji+reading+note_id) with indexes, and a migration
unit test proves a v25 database upgrades cleanly with existing rows
preserved; (b) every sync save rebuilds the table (delete-all+reinsert)
from the analyzed examples via KanjiReadingAligner.alignPlain on
(expression, reading), storing canonical readings, skipping alignment
failures, in the same transaction/seam as rebuildSimilarKanjiPairs — a
unit test over a fake/in-memory store proves rows appear after a save and
stale rows are purged on the next save; (c) LocalStoreInventory exposes
cached set queries kanjiWithKanjiReading (>=1 usage row and >=2 available
readings incl. dictionary readings), kanjiWithReadingKanji (some attested
reading shared with >=2 other inventory kanji), kanjiWithSentenceReading
(>=1 example with sentence and reading), each with its own unit test
including the boundary cases exactly-1-reading, exactly-2-readings,
pool-of-2 vs pool-of-3; (d) fixture data in tests reuses the validated
examples (脱出/だっしゅつ, 心配/しんぱい canonicalizing ぱい→はい,
好き/すき, plus a jukujikun word 今日/きょう producing no rows);
(e) ./gradlew :app:testDebugUnitTest and ./gradlew ciFast exit 0. The
table is content data, not a scheduler queue: no scheduler code reads it
in this goal.
```

**Done when (machine-checkable):**

1. Migration test proves v25 → v26 (create table, preserve data,
   idempotent `shouldRun` guard per `LocalStoreMigrations.kt:176-178`
   pattern); `DB_VERSION == 26`.
2. Maintenance unit test: after a sync save with fixture examples, the
   table contains exactly the expected canonical rows (incl. ぱい→はい
   canonicalization and zero rows for 今日); a second save with changed
   examples purges stale rows.
3. Predicate tests cover the boundaries listed in the /goal block.
4. Live-provider surface untouched in this goal — no changes under
   `app/.../anki/` or `sync-domain/` (grep the diff); the rebuild hangs
   off the existing local save path only.
5. `./gradlew :core:test :app:testDebugUnitTest` and `./gradlew ciFast`
   exit 0.

---

## Goal 78: The `kanji_reading` rung (DB v27) — the user's core idea

**Problem:** A learner who fails 脱出 while knowing 脱ぐ is not confused
about the kanji's meaning or shape — they lack the だつ reading. Today
the ladder demotes them toward meaning/shape scaffolds
(`font_meaning` → `kanji_meaning` → …), which drills the wrong deficit.
22% of the author's struggling cards show exactly this pattern (see
Evidence).

**Design:** New conditional rung `kanji_reading` (`KANJI_READING`),
default order position **between `font_meaning` and `word_reading`**.
Forced-choice card:

- **Prompt:** the attested word with the target kanji visually
  highlighted, question copy "How is 〈kanji〉 read in 〈word〉?"
  (EN; JA equivalent e.g. 「〈語〉の〈漢字〉の読みは？」). Word selection:
  prefer a usage row whose reading has weak evidence (not mature, or
  lapses > 0 — the unfamiliar reading), else any attested word;
  deterministic given equal evidence (stable sort by note_id).
- **Choices (2–4):** the correct canonical reading for that word +
  distractors from the kanji's OTHER canonical readings ordered:
  mature-attested first (the exact confusion, D-R1), then other attested,
  then dictionary-only readings. All kana strings. If < 2 total choices
  can be built, fall back to a plain flashcard render (same pattern as
  `MainActivityStudyChoiceSessions.kt:70-76`) — availability should make
  this rare (Goal 77 predicate), but the fallback is mandatory.
- **Grading:** correct → `good`, wrong → `again` (objective grading,
  P10); wrong picks logged via `recordChoiceReviewLog` with wire name
  `kanji_reading`.

New stateless planner `core/.../KanjiReadingChoicePlanner.kt` modeled on
`MeaningKanjiChoicePlanner.buildChoiceCard`
(`core/.../MeaningKanjiChoicePlanner.kt:17-76`): inputs = target kanji,
usage rows for that kanji (new store read), dictionary readings; output =
prompt word + ordered choices + correct index. Pure and unit-testable.

Walk the **touch-point map** (§ above) end to end. Goal-specific values:

- Wire `kanji_reading`; enum `KANJI_READING` appended at enum end;
  `defaultsOrder()` = …, `KANJI_MEANING`, `FONT_MEANING`,
  **`KANJI_READING`**, `WORD_READING`.
- Auto-enable (D-R4): generalize the `fromStored` `MEANING_KANJI` clause
  (`RecordsBase.kt:211-216`) into an auto-enable set
  (`MEANING_KANJI`, `KANJI_READING`, later `READING_KANJI`,
  `SENTENCE_READING`) applied when the stored order predates the rung and
  an always-available rung is enabled.
- `alwaysAvailable(KANJI_READING) == false`; `RungAvailability` gains
  `hasKanjiReading`; `StudyItem` gains `hasKanjiReading` +
  `withHasKanjiReading`, annotated on read from
  `kanjiWithKanjiReading` (Goal 77) at every annotate seam.
- Memory slot `kanjiReadingMemory`, task type routed in
  `memoryForRung`/`memoryForTaskType`/`withTaskMemory`; one new
  `StudyItemArgs` positional shape (new memory at the end); DB column
  `kanji_reading_memory` TEXT default `''`, migration **v27**
  (v19 template).
- Session routing: new `StudySessionRoute.Destination.KANJI_READING`
  mapped from `StudyTaskTypes.KANJI_READING`; renderer
  `prepareKanjiReadingRender` in `MainActivityStudyChoiceSessions.kt`
  reusing the choice grid Compose — note the grid currently renders
  kanji glyphs at display size; kana choice strings need an appropriate
  text size (follow how meanings render on the `meaning_kanji` prompt
  side, or add a size parameter — keep it a UI-only tweak).
- Trace: `kanji_reading_unavailable` skip reason via the Goal 75 helper;
  a demotion from `word_reading` on an item without data must cross to
  `font_meaning` with that reason code in the trace.
- Copy: all switches in touch-point 9, EN + JA; settings subtitle
  "Included when the kanji has multiple known readings" (mirror the
  `similar_kanji` conditional subtitle pattern,
  `SettingsStudyPlanTextCopy.kt:236-242`).
- Goldens: existing goldens WILL legitimately change where a promotion or
  demotion crosses `font_meaning`↔`word_reading` on data-less items (a
  `kanji_reading_unavailable` reason code appears; rungs and timings must
  NOT change for data-less cards). Regenerate deliberately; add a new
  golden `kanjiReadingSkippedWithoutContent.timeline.txt` (mirror
  `similarKanjiSkippedWithoutContent`) plus a scheduler test where an
  item WITH data demotes from `word_reading` into `kanji_reading` after
  the fail streak, and promotes out after interval + min-pass gates.

```
/goal Implement the kanji_reading ladder rung in kanji_anki end to end,
per plans/reading-rungs-goals-2026-07-09.md Goal 78. End state: (a) enum
KANJI_READING ("kanji_reading") exists, defaultsOrder places it between
FONT_MEANING and WORD_READING, stored orders auto-splice it there and
auto-enable it (StudyLadderSettingsTest cases prove splice + auto-enable
+ order-preservation for configs that already contain it); (b) it is
conditional: alwaysAvailable=false, RungAvailability.hasKanjiReading flag,
StudyItem.hasKanjiReading annotated on read from
LocalStoreInventory.kanjiWithKanjiReading at all annotate seams, items
without data skip the rung in both directions emitting a
kanji_reading_unavailable trace reason; (c) per-rung memory works:
kanji_reading_memory TEXT column via migration v27 (DB_VERSION 26→27,
migration unit test), StudyItem slot + memoryForRung/withTaskMemory/
StudyItemArgs routing with a RecordsValueModelsTest round-trip; (d) a pure
KanjiReadingChoicePlanner builds prompt word + 2-4 kana choices (correct
reading of the word's target kanji; distractors = the kanji's other
canonical readings, mature-attested first, then attested, then
dictionary-only; deterministic ordering) with unit tests using the 脱出
だつ-vs-ぬ and 低い ひく-vs-てい fixtures; (e) the study UI routes
taskType kanji_reading to a new choice destination that renders the word
with the target kanji highlighted, kana choices in the choice grid,
correct=good wrong=again, wrong picks logged through recordChoiceReviewLog
with rung "kanji_reading", and falls back to flashcard when <2 choices;
(f) all four exhaustive when(rung) copy switches plus StudyTaskCopy labels
have EN+JA strings and copy tests pass; (g) LadderSchedulerTest covers
demote-into/promote-out-of the rung for items with data, skip-over for
items without, and a new golden kanjiReadingSkippedWithoutContent plus
deliberately regenerated existing goldens/parity snapshot whose diffs
contain only kanji_reading_unavailable reason-code additions (no rung or
timing changes for data-less cards); (h) instrumented sources compile and
LadderSchedulerEndToEndTest/SettingsStudyLadderComposeTest are extended
for the new rung; (i) AGENTS.md scheduler notes and
docs/ladder-and-srs-system.md are updated in the same commit; (j)
./gradlew ciFast exits 0.
```

**Done when (machine-checkable):**

1. `./gradlew :core:test` passes with the new scheduler cases:
   skip-in-both-directions without data (reason
   `kanji_reading_unavailable`), demotion from `word_reading` lands on
   `kanji_reading` with data, promotion out respects interval + min-pass
   gates, ceiling/floor semantics unchanged.
2. Golden diffs reviewed and limited to reason-code additions for
   data-less cards; new `kanjiReadingSkippedWithoutContent.timeline.txt`
   exists; `SchedulerParitySnapshotTest` passes with regenerated
   snapshot.
3. `StudyLadderSettingsTest`: defaults order equals the 8-rung sequence
   with `KANJI_READING` between `FONT_MEANING` and `WORD_READING`;
   stored 7-rung configs splice + auto-enable it; stored configs already
   containing it are preserved verbatim.
4. Migration test proves v26 → v27; upsert/read round-trips
   `kanji_reading_memory`.
5. Planner unit tests pass with the fixture words; `< 2` choices
   triggers the flashcard fallback (UI-model test).
6. Copy tests pass (EN + JA in all four switches + task copy).
7. `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac`
   passes; instrumented ladder end-to-end and settings Compose tests
   updated.
8. AGENTS.md + `docs/ladder-and-srs-system.md` updated in the same
   commit (default order list, rung rendering list, conditional
   predicate, "no legacy source" note for the v16 mapping table).
9. `./gradlew ciFast` exits 0.

---

## Goal 79: The `reading_kanji` rung — homophone discrimination (DB v28)

**Problem:** The ladder remediates *visual* confusion (`similar_kanji`)
but not *phonetic* confusion: picking the right kanji among same-reading
candidates (し has 8 mature kanji in the author's collection; 143 such
pools exist). This is the production-side mirror of Goal 78's
recognition-side gap.

**Design:** New conditional rung `reading_kanji` (`READING_KANJI`),
default order position **between `meaning_kanji` and `similar_kanji`**,
i.e. directly below `similar_kanji` (phonetic sibling of the
visual-discrimination rung; objectively graded, P10). Deliberately NOT
between `similar_kanji` and `kanji_meaning`: `similar_kanji` must remain
directly below the start rung so the first demotion still reaches the
signature visual-discrimination remediation in one step (Goal 65
invariant); phonetic discrimination is the second demotion stop.
Forced-choice card:

- **Prompt:** the target reading in kana + the attested word with the
  target kanji **blanked** (e.g. だつ — 「〇出」 'escape'), including the
  word's meaning gloss as the semantic cue. Question copy: "Which kanji is
  read 〈kana〉 here?" / JA equivalent. Word selection mirrors Goal 78
  (prefer weak-evidence usage).
- **Choices (3–4 kanji glyphs):** the target kanji + distractor kanji
  sharing the same canonical reading. Distractor preference: kanji whose
  shared reading is mature-attested (usage table), then other inventory
  kanji whose dictionary readings include the reading. Minimum 3 total
  choices, else flashcard fallback (a 2-option homophone card is a coin
  flip — stricter than `similar_kanji`'s ≥ 2 on purpose; availability
  predicate `kanjiWithReadingKanji` already guarantees an attested pool
  of 3).
- **Grading:** correct → `good`, wrong → `again`; wrong picks via
  `recordChoiceReviewLog` with wire `reading_kanji`. (Wrong homophone
  picks are future `ConfusionPairMiner`-style input; out of scope now.)

Walk the **touch-point map**. Goal-specific values: enum appended;
`defaultsOrder()` = `WRITE_KANJI`, `TYPE_MEANING`, `MEANING_KANJI`,
**`READING_KANJI`**, `SIMILAR_KANJI`, `KANJI_MEANING`, …;
`alwaysAvailable == false`; availability flag
`hasReadingKanji` from `kanjiWithReadingKanji` (Goal 77); memory column
`reading_kanji_memory`, migration **v28**; new planner
`core/.../ReadingKanjiChoicePlanner.kt` (stateless, same template);
destination `READING_KANJI` rendering kanji glyphs in the existing choice
grid (no sizing tweak needed — glyph choices are the grid's native case);
trace reason `reading_kanji_unavailable`; auto-enable set extended; copy
in all switches (settings subtitle "Included when other known kanji share
a reading"); goldens regenerated (demotions crossing from `kanji_meaning`
toward `meaning_kanji` on data-less items gain the reason code; the
`similarKanjiSkippedWithoutContent` golden now shows BOTH conditional
skip codes on that crossing — the demotion skips `similar_kanji` AND
`reading_kanji` in one move — assert the chained skip explicitly; the
existing chained-skip test in `LadderSchedulerTest` (search "chained")
is the model).

```
/goal Implement the reading_kanji homophone-discrimination rung in
kanji_anki end to end, per plans/reading-rungs-goals-2026-07-09.md
Goal 79. End state: (a) enum READING_KANJI ("reading_kanji") exists,
defaultsOrder places it between MEANING_KANJI and SIMILAR_KANJI (directly
below similar_kanji, preserving the Goal 65 invariant that similar_kanji
stays directly below kanji_meaning), stored
orders auto-splice + auto-enable it (StudyLadderSettingsTest cases);
(b) conditional: alwaysAvailable=false, RungAvailability.hasReadingKanji,
StudyItem.hasReadingKanji annotated from
LocalStoreInventory.kanjiWithReadingKanji at all annotate seams,
data-less items skip with reading_kanji_unavailable trace reason, and a
scheduler test pins the chained double-skip (similar_kanji AND
reading_kanji both unavailable) crossing in one move with both reason
codes; (c) reading_kanji_memory column via migration v28 (DB_VERSION
27→28, migration test) with full memory-slot routing + round-trip test;
(d) a pure ReadingKanjiChoicePlanner builds reading-kana prompt + blanked
word + meaning gloss + 3-4 kanji choices (target + same-reading kanji,
mature-attested distractors first, dictionary-reading inventory kanji as
fallback, deterministic order) with unit tests including a
pool-of-exactly-3 case and a <3-choices flashcard-fallback case; (e) the
study UI routes taskType reading_kanji to a choice destination rendering
kanji-glyph choices, correct=good wrong=again, wrong picks logged via
recordChoiceReviewLog with rung "reading_kanji"; (f) EN+JA copy in all
four exhaustive when(rung) switches + task copy, copy tests pass;
(g) goldens/parity snapshot deliberately regenerated with diffs limited
to reading_kanji_unavailable reason-code additions on data-less
crossings; (h) instrumented sources compile, end-to-end + settings
Compose tests extended; (i) AGENTS.md and docs/ladder-and-srs-system.md
updated in the same commit; (j) ./gradlew ciFast exits 0.
```

**Done when (machine-checkable):**

1. Scheduler tests: skip without data (both directions), chained
   double-skip with `similar_kanji` (both reason codes, single move),
   demote-into/promote-out with data; `./gradlew :core:test` green.
2. `StudyLadderSettingsTest`: 9-rung default order correct; splice +
   auto-enable proven; existing stored orders preserved.
3. Migration test v27 → v28; memory round-trip test.
4. Planner tests incl. pool-of-3 boundary and fallback.
5. Copy tests green (EN + JA).
6. Goldens/parity regenerated with reviewed, explainable diffs only.
7. `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac`
   green; instrumented tests extended.
8. AGENTS.md + design doc updated in the same commit.
9. `./gradlew ciFast` exits 0.

---

## Goal 80: The `sentence_reading` rung — new ladder ceiling (DB v29)

**Problem:** `word_reading` is the current ceiling, but reading a word in
isolation is not the terminal skill — reading it in running text is.
8,240 of the author's 8,597 notes carry a mined sentence; the data is
already synced (`sentence` field → `kanji_examples`). The ladder top
should hand the card back to real context before Anki-side retirement.

**Design:** New conditional rung `sentence_reading` (`SENTENCE_READING`),
default order position **above `word_reading`** (new ceiling). This is a
**flashcard-variant** rung (like `word_reading`/`font_meaning`), NOT a
choice rung — self-graded Pass/Fail (P10: top rungs return grading trust
to the learner):

- **Front:** the example sentence with the target word visually
  emphasized (bold/color span). Sentence font size well below the 116sp
  kanji hero — follow the `word_reading` 44sp precedent
  (`MainActivityStudyFlashcard.kt:65-66`) and go smaller as needed for
  sentence length. If the expression does not appear verbatim in the
  sentence (conjugation), show the sentence unhighlighted with the word
  printed beneath — never skip the card for highlight failure.
- **Back:** the word's reading (primary answer) + the word + its
  meaning; sentence translation optional if present.
- **Example selection:** new `StudyExampleSelector.sentenceReadingExample`
  beside `wordReadingExample` (`core/.../StudyExampleSelector.kt:20-35`):
  prefer an example with BOTH sentence and reading; among those prefer
  `suspended` then `active` (same trust ordering as `word_reading`);
  deterministic.
- **Grading:** standard Pass/Fail → `good`/`again` flashcard buttons.

Walk the **touch-point map**. Goal-specific values: enum appended;
`defaultsOrder()` = …, `WORD_READING`, **`SENTENCE_READING`**;
`alwaysAvailable == false`; availability flag `hasSentenceReading` from
`kanjiWithSentenceReading` (Goal 77); memory column
`sentence_reading_memory`, migration **v29**; NO new
`StudySessionRoute.Destination` — it stays `FLASHCARD`; add
`isSentenceReadingTask` beside the predicates at
`StudyTaskCopy.kt:98-113`; extend `composeFlashcardRouteModel`
(`MainActivityStudyFlashcard.kt:56-96`) with the sentence hero branch;
prompt/answer copy via `StudyTextCopy` (mirror `wordPrompt`,
`StudyTextCopy.kt:46-53`); trace reason `sentence_reading_unavailable`;
auto-enable set extended.

**Ceiling semantics (critical):** `nextRung` already returns the current
rung when no higher valid rung exists, so items WITHOUT sentence data
keep `word_reading` as their effective ceiling — pin this with an
explicit test. Items WITH data get the new ceiling; the ceiling-hold
tests currently asserting `word_reading` as absolute top (search
`LadderSchedulerTest` for `WORD_READING` ceiling cases and the
`ceilingCardDemotesOneRungWhenCold` golden) must be split into
with-data/without-data variants. Retirement is untouched: it fires on
Anki-side evidence (`StudyQueueSeeder.shouldRetireSeedItem`), not on
reaching the ceiling.

```
/goal Implement the sentence_reading ladder-ceiling rung in kanji_anki
end to end, per plans/reading-rungs-goals-2026-07-09.md Goal 80. End
state: (a) enum SENTENCE_READING ("sentence_reading") exists,
defaultsOrder places it above WORD_READING as the new top, stored orders
auto-splice + auto-enable it (StudyLadderSettingsTest cases); (b)
conditional: alwaysAvailable=false, RungAvailability.hasSentenceReading,
StudyItem.hasSentenceReading annotated from
LocalStoreInventory.kanjiWithSentenceReading at all annotate seams;
explicit scheduler tests prove items WITHOUT sentence data still
ceiling-hold at word_reading (promotion returns current rung, trace shows
sentence_reading_unavailable) while items WITH data promote into and
ceiling-hold at sentence_reading; (c) sentence_reading_memory column via
migration v29 (DB_VERSION 28→29, migration test) with full memory-slot
routing + round-trip test; (d) StudyExampleSelector.sentenceReadingExample
prefers examples having both sentence and reading with the
suspended-then-active ordering, unit-tested including the
no-sentence-examples null case; (e) the rung renders as a FLASHCARD
variant (no new destination): front = sentence with the target word
emphasized (fallback: unhighlighted sentence + word beneath when the
expression is not a verbatim substring), back = word reading + word +
meaning, Pass/Fail = good/again, with StudyTaskCopy.isSentenceReadingTask
and composeFlashcardRouteModel extended and a route-model unit test
covering both highlight and fallback shapes; (f) EN+JA copy in all four
exhaustive when(rung) switches + task copy + StudyTextCopy prompt, copy
tests pass; (g) goldens/parity regenerated deliberately — the
ceiling-related goldens change only for with-data scenarios and diffs are
limited to the new rung/reason codes; (h) instrumented sources compile,
end-to-end + settings Compose tests extended; (i) AGENTS.md and
docs/ladder-and-srs-system.md updated in the same commit (10-rung order,
ceiling note, conditional predicate); (j) ./gradlew ciFast exits 0.
```

**Done when (machine-checkable):**

1. Scheduler tests: without-data ceiling-hold at `word_reading` (with
   `sentence_reading_unavailable` in the trace), with-data promotion
   into and ceiling-hold at `sentence_reading`; demotion from the new
   ceiling lands on `word_reading`. `./gradlew :core:test` green.
2. `StudyLadderSettingsTest`: 10-rung default order; splice +
   auto-enable proven.
3. Migration test v28 → v29; memory round-trip.
4. `StudyExampleSelector` tests green (both-fields preference,
   suspended-first, null case).
5. Flashcard route-model test covers highlighted and fallback rendering
   shapes; Pass/Fail wiring unchanged.
6. Copy tests green (EN + JA).
7. Goldens/parity regenerated with reviewed diffs;
   `ceilingCardDemotesOneRungWhenCold` updated or split per the
   with/without-data distinction.
8. `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac`
   green; instrumented tests extended.
9. AGENTS.md + design doc updated in the same commit.
10. `./gradlew ciFast` exits 0.

---

## Goal 81: Consistency pass, full local gate, live AnkiDroid gate

**Problem:** Goals 75–80 each update docs incrementally, but the ladder
contract now spans three documents (AGENTS.md,
`docs/ladder-and-srs-system.md`, settings copy) and the sync-save path
gained a rebuild step (Goal 77). AGENTS.md requires the local
real-collection AnkiDroid gate before any release that touches sync
behavior, and the golden/parity surface has been regenerated three times.

**Goal:** A final verification-only goal (no feature code):

1. **Docs consistency:** AGENTS.md "Study Scheduler Notes" lists the
   exact 10-rung default order with the three new conditional predicates
   described; the v16 legacy mapping section states the three new rungs
   have no legacy source; `docs/ladder-and-srs-system.md` rung table,
   conditional-rung section, and §14 decision log (record D-R1…D-R5)
   match the code; settings copy matches the shipped rung set. Grep for
   stale "7-rung"/"seven rung"/old-order phrasings across `docs/`,
   `plans/`, AGENTS.md, README.
2. **Full deterministic gate:** `./gradlew ciFast` and
   `./gradlew ciQuality` green at the final commit.
3. **Live AnkiDroid gate (mandatory — sync-save changed):** follow
   AGENTS.md "Live AnkiDroid Emulator Setup" + "Running The Live Tests"
   verbatim: emulator with real AnkiDroid 2.24.0, the user's copied
   collection, permission granted, then the targeted live run
   (`MainActivityInstrumentedTest#testManualSyncButtonWorksAgainstLiveAnkiDroid`,
   `AnkiDroidGatewayProviderInstrumentedTest`,
   `RealAnkiDroidLiveProviderInstrumentedTest`) with
   `kanjiLiveAnkiDroid=true` and the default 7,000-note threshold.
   Additionally verify post-sync on the live collection:
   `kanji_reading_usage` is non-empty, and at least one study item
   reports each new availability flag (adb shell into the app DB or an
   added instrumented assertion — an instrumented assertion in the live
   suite is preferred so the check is repeatable).
4. **Release:** merge to `main` and let the automatic release flow run
   (`android-release.yml` off the successful `Android CI` run); watch it
   to completion with `gh run watch RUN_ID --exit-status`. Manual tags
   only for a deliberate non-patch version.

```
/goal Ship the reading-rungs release of kanji_anki (Goals 75-80 already
merged) with full verification. End state: (a) AGENTS.md,
docs/ladder-and-srs-system.md, and settings copy consistently describe
the 10-rung ladder (write_kanji, type_meaning, meaning_kanji,
reading_kanji, similar_kanji, kanji_meaning, font_meaning, kanji_reading,
word_reading, sentence_reading), the three conditional predicates, the
auto-enable-on-upgrade behavior, and decisions D-R1..D-R5; no stale
old-order text remains (grep proves it); (b) ./gradlew ciFast and
./gradlew ciQuality exit 0 at the release commit; (c) the live AnkiDroid
emulator gate from AGENTS.md passes against the copied real collection
with kanjiLiveAnkiDroid=true and the default 7,000-note threshold,
including new assertions that the post-sync kanji_reading_usage table is
non-empty and at least one study item carries each of hasKanjiReading,
hasReadingKanji, hasSentenceReading; (d) the change is merged to main,
the automatic Android CI run succeeds, the triggered android-release.yml
run succeeds (gh run watch ... --exit-status), and the published GitHub
release contains the signed APK whose versionName matches the computed
tag; (e) no release is cut if any live test fails - SQLITE_BUSY during
the live poller is retried per AGENTS.md, not treated as failure.
```

**Done when (machine-checkable):**

1. Doc-consistency grep script (or manual grep transcript in the PR
   description) shows zero stale-order references.
2. `./gradlew ciFast` and `./gradlew ciQuality` exit 0.
3. Live run output `OK` for the full targeted class list, with the new
   live assertions included; recorded in the PR/commit message.
4. `gh run watch <Android CI run> --exit-status` and
   `gh run watch <android-release run> --exit-status` both exit 0;
   `gh release view <tag> --json assets` shows the signed APK.

---

## Appendix A — re-running the collection validation

The numbers in Evidence came from read-only queries; to re-validate after
implementation (or against a fresh collection copy), the aligner's Kotlin
tests are authoritative, but the quick external check is:

```sh
sqlite3 "file:$HOME/Library/Application Support/Anki2/User 1/collection.anki2?immutable=1" \
  "SELECT COUNT(*) FROM notes WHERE mid=1762071852534"
```

then reproduce the DP alignment against
`app/src/main/assets/dictionaries/kanji_dictionary.db` (`kanji` table,
`\x1f`-separated reading lists). Note: Anki's SQLite uses a custom
`unicase` collation — avoid `ORDER BY` on `name` columns when querying
its DB externally; `?immutable=1` avoids WAL locks while Anki runs.

## Appendix B — why not other rung ideas

Recorded so the next review does not re-litigate: audio rungs need media
sync the app deliberately avoids; pitch-accent needs a new field mapping
and has niche value; `word_meaning` duplicates `kanji_meaning` at low
marginal value; okurigana choice is a planned extension of
`kanji_reading` (KANJIDIC kun `.`-splits already carry the data);
speeded-recognition needs no new data and can be trialed independently;
Anki-lapse-driven seeding/placement was rejected (D-R1) to keep the
movement contract auditable.
