# Legacy v30 Ladder And Admission Notes (Agent Reference)

Moved out of `CLAUDE.md`/`AGENTS.md` to keep the always-loaded agent context
focused on the canonical v31 model. This material describes the legacy v30
ten-rung scheduler that is retained for lazy conversion, downgrade
compatibility, and reading old rows. For current study behavior read
`docs/adaptive-two-core-scheduler.md` and `docs/rwkv-memory-model.md` first;
for the full legacy design see `docs/ladder-and-srs-system.md`.

The ladder/rung and admission material below, through the end of
`Ingestion And Admission Notes`, describes the legacy v30 scheduler retained
for lazy conversion and compatibility. It is not the canonical v31 routing
model. Even during legacy routing, the core scheduler consumes only
`study_items`: every persisted item has one current rung and one phase.

Default ladder order from most-scaffolded (bottom) to least-scaffolded (top)
rung:

1. `write_kanji`
2. `type_meaning`
3. `meaning_kanji`
4. `reading_kanji`
5. `similar_kanji`
6. `kanji_meaning`
7. `font_meaning`
8. `kanji_reading`
9. `word_reading`
10. `sentence_reading`

`similar_kanji` sits directly below `kanji_meaning` (the new-card start rung)
so the first demotion reaches discrimination practice — the app's signature
remediation — in one demotion step for cards that have confusion data, instead
of three (Goal 65). This only affects fresh installs and stored configs that
lack `similar_kanji`; a user's stored order is preserved verbatim.

`reading_kanji` (Goal 79) is the phonetic sibling of `similar_kanji` and sits
directly below it (not between `similar_kanji` and `kanji_meaning`), preserving
the Goal 65 invariant that `similar_kanji` stays directly below the start rung.
It is a conditional homophone-discrimination rung ("だつ — which kanji is 〇出?")
gated by `hasReadingKanji` (some attested reading of the kanji is shared by ≥ 2
other inventory kanji, so a ≥ 3-choice card exists).

`kanji_reading` (Goal 78) sits directly below `word_reading` so a
`word_reading` fail streak demotes straight into targeted reading
discrimination ("How is 脱 read in 脱出?"). It is a conditional rung: the
`hasKanjiReading` predicate (answered by the `kanji_reading_usage` /
`kanji_reading_pool` tables — the kanji has an attested usage word and at least
two distinct candidate readings) must hold, else promotion and demotion cross
over it without pausing, exactly like `similar_kanji`.

`sentence_reading` (Goal 80) is the new ladder ceiling: read the target word
inside the user's own mined sentence. It is conditional on `hasSentenceReading`
(≥ 1 example with both a non-blank sentence and reading); cards without sentence
data keep `word_reading` as their effective ceiling because `nextRung` returns
the current rung when no higher valid rung exists.

`meaning_kanji`, `reading_kanji`, `kanji_reading`, and `sentence_reading` are
present in the editable default order and, like every other rung, enabled by
default. Stored configurations that predate a rung get it auto-enabled on load
and spliced into the stored order adjacent to its default neighbors
(`StudyLadderSettings.AUTO_ENABLE_RUNGS`, generalizing the original
single-`meaning_kanji` clause; D-R4).
Users can turn rungs on/off or move them in Settings. New cards start at
`kanji_meaning`; if that rung is disabled, they start at the nearest enabled
rung, preferring the lower/more-scaffolded rung when the distance ties. The
conditional rungs (`similar_kanji`, `kanji_reading`, `reading_kanji`,
`sentence_reading`) exist only when the app can build a valid card for that
card's kanji. When a conditional predicate is false, promotion and demotion
cross over that rung without pausing (a `<wire>_unavailable` trace reason is
recorded per skipped rung; a move that crosses several conditional rungs at once
records one code each). Settings must keep at least one always-available rung
enabled; the conditional rungs alone are not enough because they depend on
per-card data.

Phases: `new_learning`, `review`, `relearning`. Learning and relearning follow
Anki semantics:

- `Again` returns to the first step.
- `Good` advances one step; graduates past the last step.
- `Hard` on the first step uses a delay between Again and Good (the
  first-two-step midpoint, or 1.5x the step when only one step exists); on
  later steps it repeats the current step.
- `Easy` graduates the card immediately.

Learning and relearning repeats are practice-only. They do not advance
promotion, demotion, or any long-term scheduler threshold (the memory model
still observes them; see RWKV Memory Model Notes). Only persisted
model-scheduled due review attempts in the `review` phase count toward ladder
movement. The boundary is the task's persisted due time, not the calendar day
or any learning-repeat queue.

Ladder movement uses real due-review evidence, not learning-repeat
practice. A due-review `Hard`, `Good`, or `Easy` promotes the rung only when
**both** hold: the card's memory strength — measured as the interval it
would schedule at a fixed 0.90 target retention, not the user's actual
retention-scaled interval — is strictly more than
`ladder_promotion_interval_days` (default 21 days), and the
card has accumulated at least `ladder_promotion_min_passes` real-due passes
on its current rung (default 2). Keying promotion off the fixed-0.90
interval means changing the retention setting no longer silently changes
ladder progression speed (closed decision D4). The min-pass gate stops a mature card from
cascading up two rungs in two reviews after the promotion cap clones
above-threshold stability onto the newly promoted rung, and it guards against
demotion bounce-back (a demotion resets the pass streak to 0, so the first
post-demotion pass cannot re-promote). Setting `ladder_promotion_min_passes`
to 1 reproduces the pre-gate single-pass promotion. A
due-review `Again` increments a consecutive fail streak and demotes the rung
when it reaches `ladder_demotion_fail_streak` (default 3 fails). At
`write_kanji` the demotion floor is reached and further `Again`s keep the
card on that rung. At the item's highest valid enabled rung, further passes
keep the card there; in the default order that is `sentence_reading` when
sentence data exists and otherwise `word_reading`.

The memory model is RWKV-Curve (SM92), not FSRS, and it is not personalized:
there are no fitted weights and no fitter. The fixed-0.90 memory-strength
signal above is the model's S90 (days until predicted recall falls to 90%),
which is FSRS's own definition of stability, so the 21-day threshold keeps its
meaning. The legacy `scheduler_fsrs_weights`, `scheduler_fsrs_fit_summary`
and `scheduler_fsrs_personalization_enabled` settings stay stored, unread, for
downgrade compatibility.

Exception (fail-fast demotion): the first real review after a promotion is the
capped validation review. If that first attempt is an `Again`, the rung demotes
immediately instead of waiting for the full fail streak, because a failed
validation is direct evidence the promotion was premature. This only applies
above the starting rung and only while the item is still on the capped
first-review interval (`ReviewTransitionEngine.isFailedPromotionValidation`); a
brand-new card's first failed review at the starting rung never demotes below
where new cards begin.

On a due review `Again`, the card enters `relearning` at step 0 if
relearning steps exist. If relearning steps are empty, the card skips
relearning and is rescheduled straight from the memory model's post-lapse
curve, matching Anki's behavior without relearning steps. One exception preserves the
"practiced soon" promise of demotion (Goal 70): when a due `Again` both
demotes the rung AND the relearning list is empty, the newly demoted rung's
first review is capped at one day (`capDemotedRungFirstReview`), mirroring
the promotion first-review cap. A non-demoting `Again` with empty relearning
steps keeps the model's post-lapse interval unchanged.

When a promotion fires, the newly promoted rung's first review is capped at
one third of `ladder_promotion_interval_days` (7 days at the default 21) so
the new skill is validated sooner than a full promotion-sized interval.

## Ingestion And Admission Notes

Queue admission and evidence-based seeding are owned by `StudyQueueSeeder` and
the pure `AdmissionEvidencePolicy`. Core principle: do not make the learner
study kanji they do not need to.

- **Evidence-based seeding.** A kanji supported by at least one mature active
  Anki card and never suspended (`AdmissionEvidencePolicy.isAlreadyReadInContext`)
  is seeded straight into the `review` phase at the highest enabled rung, due
  now, with stability/difficulty derived from Anki's own memory state. Before
  its first Kani answer the memory model is warmed with a synthetic all-Good
  history ending one stability before that answer (`RwkvSeedBurstPolicy`), so
  it is validated once and then rides a real long interval instead of
  climbing the whole ladder. Everything else keeps the conservative `new_learning` start at
  `kanji_meaning` (the nearest enabled rung if that is disabled). New cards
  still start at `kanji_meaning`.
- **Admission gate.** A row whose `matureSupportCount` already meets
  `matureSupportThreshold` (default 2) is never admitted (it is already
  repaired), unless per-kanji repair evidence is `REGRESSING`. This removes the
  old admit-then-retire loop.
- **Ceiling parking.** An item at the highest enabled rung in `review` phase
  whose scheduled interval grows past `CEILING_PARK_INTERVAL_MULTIPLIER`
  (default 4) times the promotion threshold is "parked": it stays studyable when
  due but no longer counts against `activeQueueCap`, so a mature suspended-only
  kanji riding a long interval can never permanently block admission of new
  repairs.
- **Signature reshuffle.** A suspend/unsuspend flip in Anki can change which
  example is preferred and thus the answer signature. When only the
  expression/reading changes but the meaning is unchanged, the seeder preserves
  all earned scheduler state and just adopts the new signature. A genuine
  meaning change still resets and demotes one rung.

Import and default notes: weak-card import (active leeches: FSRS difficulty
`>= 7.5` or lapses `>= 3`) is on by default; the default new-card sort is
`balanced_priority`; unknown-Jiten-rank suspended kanji import by default and
sort last; `active_queue_cap` is user-editable (Deck limits panel, bounded
8-200); ladder thresholds are bounded above (promotion days `<= 365`, fail
streak `<= 30`).

Graduation (new-learning or relearning) takes its interval from the memory
model's answer to the graduating rating. Every learning and relearning step
answer before it is also fed to the model (RWKV learns from every answer, as it
was trained on full Anki histories) but its next appearance still uses the
configured step delay. The FSRS-era graduation rules (initial state from the
graduating rating alone; a second `nextDifficulty` on relearning graduation)
are retired with FSRS; `FsrsTestMemoryModel` keeps them only as a closed-form
test double for policy tests.

`hard`/`good`/`easy` all count as a ladder-streak pass and `again` as a fail.
A `write_kanji` remediation judged `CLOSE` submits `hard`, which still passes.
Leaving the `write_kanji` rung additionally requires clean-write evidence
(Goal 67): promotion off `write_kanji` fires only when `writingLevel >= 2`
(two net clean, hint-free passes; `writingLevel` rises only on clean hint-free
passes and falls on failures via `updateWritingLevel`). A chain of messy
`CLOSE`/"Save hard" passes therefore cannot promote production out of the
writing rung without at least one clean write, even once the interval and
min-pass gates are met. The blocked non-move records
`promotion_blocked_writing_level` in the trace. `updateWritingLevel` runs
before the ladder transition so the gate sees the current attempt; this
reorder is behavior-neutral for every non-writing rung.
The demotion fail-streak resets only when a demotion actually moves the rung;
at the `write_kanji` floor (where demotion cannot move) the streak keeps
accumulating so chronically-failing floor cards keep reporting to
`LadderHealthPolicy`.

The scheduler core keeps all four ratings (`again`, `hard`, `good`, `easy`).
For ladder-streak counting, `hard`, `good`, and `easy` all count as a pass;
only `again` counts as a fail.

`word_reading` switches the tested dimension from meaning to pronunciation.
`sentence_reading` extends that contextual check into a mined sentence and is
the default ceiling when sentence data exists. Reading lapses demote through
the configured reading-focused rungs when they are available before reaching
meaning practice. True retirement remains Anki-evidence-driven (it fires on
Anki-side `matureSupportCount`, not the ladder ceiling).

Study UI renders one current rung at a time. Rung rendering:

- `write_kanji` → handwriting pad and writing evaluation. The prompt is a
  recall cue (mined sentence or word with the target kanji blanked, else
  meaning + reading). Trace/Outline are guarded tracing; Minimal/Blind are
  free recall with no guide and no ink blocking. After Check the learner's
  strokes are shown beside the model, numbered and colour-coded. A kanji's
  first writing appearance (opening at Trace) starts with an ungraded,
  skippable primer (watch the strokes, write from memory, compare). A failed write repair comes back in
  the same session via the normal learn-ahead repeat path, labelled
  "Once more".
- `type_meaning` → typed answer box.
- `similar_kanji` → multiple-choice selector from visually similar kanji.
- `reading_kanji` → multiple-choice selector of same-reading kanji (reading +
  blanked word + meaning gloss); ≥ 3 choices required, else a plain flashcard.
- `meaning_kanji` → multiple-choice selector from four local kanji.
- `kanji_meaning` → standard recognition card.
- `font_meaning` → recognition card with font variation.
- `kanji_reading` → multiple-choice selector of kana readings ("How is
  〈kanji〉 read in 〈word〉?"); falls back to a plain flashcard when fewer than
  two choices can be built.
- `word_reading` → reading prompt.
- `sentence_reading` → flashcard front is the mined sentence (target word
  emphasized; small font); back is the word reading + word + meaning. Self-graded
  Pass/Fail. Falls back to the plain word when no sentence example exists.

The study UI exposes `Pass` and `Fail` labels. In the core scheduler the
wire format stays `good`/`again`/`hard`/`easy`; the UI translates
`Pass` → `good` and `Fail` → `again` at the boundary. The `write_kanji` rung
offers only `Pass` and `Fail`; `Hard` and `Easy` are never offered as
user-selectable ratings for that rung. One documented exception: when the
writing evaluation judges an ink attempt as `CLOSE` (passed, but not clean),
the single primary action is labeled `Save hard` and submits `hard`, which
still counts as a pass for ladder-streak purposes. The user never chooses
between multiple ratings on `write_kanji`. The checker's verdict can be
corrected either way: "Mark right anyway" (manual override, recorded as
`hard`) on a graded fail, and "I got it wrong" on a graded pass, which
submits `again` and is recorded as a failed, non-clean write.

The study subsystem must never create a parallel "main study item plus side
task queue" model. `learning_repeats` and `similar_kanji_choice_state` are
compatibility/content state, not scheduler sources. Existing
`similar_kanji_repair_queue` rows may still render only so the v31 compatibility
release can drain them; no production path may enqueue a new row. New repair
state is inline on the owning `study_items` row. `similar_kanji_pairs` remains
content for visual-confusion choices, not a queue.

Legacy field mapping used by the DB v16 migration (fresh start):

- `writing_remediation_pending = 1` → rung `write_kanji`
- `recognition_stage = -1` → rung `type_meaning`
- `recognition_stage = 0` → rung `kanji_meaning` (new-card default)
- `recognition_stage = 1` → rung `font_meaning`
- `recognition_stage = 2` → rung `word_reading`

The `similar_kanji`, `kanji_reading`, `reading_kanji`, and `sentence_reading`
rungs have no legacy source; they are reached through configured ladder movement
when `hasSimilarKanji` / `hasKanjiReading` / `hasReadingKanji` /
`hasSentenceReading` is true.
