# Deep Review: Anki Ingestion & Default Settings vs Kani's Core Principle

Date: 2026-07-08. Reviewed at current `main` working tree. All file:line
references verified against source during this review.

## The measuring stick

Kani's stated principle (README.md:61,74; docs/plans/cheap-ralph/
2026-06-09-habit-intelligence-daily-planning.md:5,40; docs/plans/simple.md:111-113):

> Pareto focus on the kanji most worth studying today. Minimum effective
> repair, then return to immersion. A bridge SRS, not a second curriculum.
> Retire items once real Anki evidence shows repair.

Every suggestion below is scored against one question: **does this reduce
exposures the user doesn't need, or redirect exposures to where they buy the
most real-world reading ability?**

## How ingestion actually works today (verified summary)

1. Sync reads the AnkiDroid provider: models, notes for the configured note
   type (default `Kiku`), and cards with a degrading projection that includes
   `queue`, `type`, `due`, `interval`, `reps`, `lapses`, and FSRS
   `stability`/`difficulty` when available
   (`app/.../anki/AnkiDroidCardReader.kt:314-361`). The revlog and decks are
   never read.
2. Default import scope: **suspended cards only**, Jiten rank 100–3000, min 1
   matching card (`core/.../RecordsBase.kt:370-378`). Active, tagged, weak,
   and browser-query imports are opt-in and off.
3. `KanjiAnalyzer` turns examples into dashboard rows with
   `weakness = suspended*12 + supportDeficit*5 + min(8, lapses*2) +
   min(6, intervalPressure*2) + min(12, fsrsPressure)`
   (`core/.../KanjiAnalyzer.kt:66-70`); a row exists iff `weakness > 0`.
4. `StudyQueueSeeder` admits rows in new-card-sort order (default: Jiten
   frequency) under three caps: `newPerDay = 3`, hidden `activeQueueCap = 24`,
   and the adaptive plan's focus limit (default auto, max 5)
   (`core/.../StudyQueueSeeder.kt:470-473`, `RecordsSyncModels.kt:127-128`,
   `AdaptiveLoadPlanner.kt:253-257`).
5. **Every admitted item is born identical**: rung `kanji_meaning`, phase
   `new_learning`, due now, placeholder stability 0.4 / difficulty 5.0
   (`core/.../StudyQueueSeeder.kt:319-359`). The Anki card's FSRS
   stability/difficulty, interval, reps, and lapses are read, persisted, shown
   in analytics — and never seed the study item.
6. Items retire when the kanji has ≥ 2 mature (21d+) **active** supporting
   cards in Anki AND the item has `totalReviews > 0`
   (`StudyQueueSeeder.kt:236-251`). Mature support is only counted for active
   examples (`KanjiAnalyzer.kt:195-199`).
7. Ladder movement: promotion when a real due review's FSRS interval exceeds
   21 days; first review on the promoted rung capped at 7 days; the FSRS
   memory is cloned across rungs (`core/.../ReviewTransitionEngine.kt:357-406`,
   `:462-466`). Demotion after 3 consecutive real-due Agains.

The healthy-card lifetime is lean: start at rung 5 of 7, ~5 graded exposures
over ~20 days to reach the `word_reading` ceiling, then multiplicatively
growing intervals. The scheduler core is not the burden problem. The burden
problems are in **what gets admitted, what state it starts with, and when it
leaves**.

---

## Findings and suggestions

Ordered by expected impact on the core principle.

### 1. HIGH — Suspended-only items can never retire, and they clog the hidden 24-item cap

**Finding.** Retirement requires ≥ 2 mature *active* Anki cards
(`StudyQueueSeeder.kt:248`), but mature support is only accumulated for
active examples (`KanjiAnalyzer.kt:195-199`). The default import source is
suspended-only. A kanji whose only Anki presence is suspended cards therefore
has `matureSupportCount = 0` forever unless the user later mines/unsuspends
active vocab containing it. Its Kani item never retires; it rides
`word_reading` with ever-growing FSRS intervals — but it still counts against
`activeQueueCap = 24` (`StudyQueueSeeder.kt:470-473`), which is not
user-visible or editable (`RecordsSyncModels.kt:127`).

**Consequence.** At 3 admissions/day the cap fills in ~8 days. From then on,
new kanji enter only when something retires. For a user whose workflow is
"suspend and move on" (the default persona!), nothing retires, so admission
starves: the app silently stops introducing new problem kanji after week two
while immortal ceiling-riders hold all 24 slots. This both blocks the
highest-value work (new repairs) and preserves a perpetual trickle of
low-value reviews (R≈0.99 ceiling reviews of kanji already repaired as far as
Kani can measure).

**Suggestions.**
- Add a **ceiling graduation** ("Kani-mastered" soft-retire): when an item is
  at the highest enabled rung in `review` phase and its scheduled interval
  exceeds a threshold (e.g. `4 × ladder_promotion_interval_days` = 84 days),
  retire it with a distinct state/reason (`mastered_in_kani` vs
  `mature_anki_support`). Reopen rules already exist and can reuse the
  REGRESSING-evidence path.
- Alternatively (or additionally), **exclude long-interval ceiling items from
  `activeQueueCap` counting** so they don't block admission even if kept
  nominally active.
- Surface both retirement reasons in the timeline copy (there is already
  `TimelineCopy.studyStateDetail`) so the user sees *why* Kani stopped asking.
- This is the single biggest alignment fix: it re-opens the pipeline for new
  high-value repairs and ends studying that no longer buys anything.

### 2. HIGH — Anki evidence is read but discarded: every kanji starts as a total stranger

**Finding.** `newStudyItem` hardcodes rung `kanji_meaning`, S=0.4, D=5.0, due
now (`StudyQueueSeeder.kt:319-359`). FSRS stability/difficulty from AnkiDroid
(read at `AnkiDroidCardReader.kt:314-349`) never seeds anything. A kanji the
user reviews daily in active Anki cards (imported via the weak-cards rule)
restarts from the same zero as a kanji they've never studied.

**Why it matters.** Kani asks different questions than Anki (single-kanji
recognition/writing vs vocab cards), so blind 1:1 state transfer would be
wrong — but the Anki state is a strong *prior*, and ignoring it costs 2–4
redundant early exposures per kanji (learning steps + a 2-day and an 11-day
review before intervals get realistic). Multiply by every admitted kanji, and
that's the largest recurring source of unneeded studying after finding #1.

**Suggestions (in increasing ambition; the first two are cheap and safe).**
- **Seed initial difficulty from Anki evidence.** At admission, map the best
  supporting card's FSRS difficulty (or lapses when FSRS is absent) into the
  placeholder difficulty instead of the constant 5.0. Difficulty only shapes
  growth rates, so this is low-risk and immediately personalizes pacing.
- **Evidence-based graduation rating.** Keep the learning steps, but derive
  the *effective* graduation floor from evidence: a kanji with ≥ 1 mature
  active example graduates as if `easy` (S≈8.3, first interval 8d) instead of
  `good` (S≈2.3, 2d) unless the user actually failed a learning step. This
  skips one near-guaranteed-pass review per already-familiar kanji.
- **Evidence-based starting rung.** Suspended-with-lapses kanji keep the
  current `kanji_meaning` start. Kanji imported via the weak-active rule
  (user still passes them sometimes in context) could start at `font_meaning`
  or `word_reading` — test the terminal skill first, and let the existing
  demotion machinery pull them down only if they actually fail. Starting high
  and demoting on evidence is the minimum-study design; starting mid-ladder
  and promoting through 7-day validation gates costs 2 extra reviews per
  kanji even when the user already has the skill.
- Longer-term option (bigger lift, defer): read the AnkiDroid revlog for
  supporting cards and fit a real prior. Not needed for the wins above.

### 3. HIGH — Admit-then-retire loop: Kani makes you study kanji it already knows are repaired

**Finding.** Admission (`addNewSeedItemIfRoom`, `StudyQueueSeeder.kt:263-277`)
has **no mature-support check**; retirement requires `totalReviews > 0`
(`StudyQueueSeeder.kt:249`). A kanji with ≥ 2 mature active cards plus one
suspended example still has weakness ≥ 12, gets a dashboard row, gets
admitted, must be pushed through learning steps and at least one review, and
only then retires at the next seed.

**Suggestion.** Gate admission on the same evidence retirement uses: skip
rows with `matureSupportCount >= matureSupportThreshold` and no REGRESSING
evidence (exactly the `shouldRetireSeedItem` predicate minus the
`totalReviews > 0` clause). Optionally record them as "pre-retired" so the
timeline can say "already supported by your Anki reviews — not admitted".
This deletes 3–4 wasted exposures per already-repaired kanji and frees
admission slots for real problems.

### 4. MEDIUM — The default import source misses the user's actual "repeatedly missed" kanji

**Finding.** The README pitch is "kanji you repeatedly miss" (README.md:21-23),
but the default import is suspended-only (`RecordsBase.kt:372-375`). The
weak-cards rule — FSRS difficulty ≥ 7 or lapses ≥ 2 on *active* cards, i.e.
the direct evidence of repeated real misses — is **off by default**. Users on
a non-Kiku workflow (who don't suspend missed cards) import nothing of value;
users on the Kiku workflow still miss their active leeches.

**Suggestions.**
- Enable `importWeakCards` by default, with slightly stricter thresholds to
  keep the queue Pareto-shaped (e.g. lapses ≥ 3 OR FSRS difficulty ≥ 7.5, and
  respect the existing frequency range). Active leeches are the
  highest-ROI targets the app can find: every repair directly reduces failed
  reviews the user is *already* doing in Anki.
- Keep suspended-only as the default *only if* the onboarding dialog
  (`HomeImportOnboardingPolicy`) explains the trade and offers the weak-cards
  toggle inline. Right now the choice is buried in Settings > Import filters.

### 5. MEDIUM — Unknown-rank kanji are silently excluded, contradicting the import spec

**Finding.** `importInFrequencyRange` requires `jitenRank != null`
(`core/.../SuspendedImportPolicy.kt:75-78`), so kanji without a Jiten rank are
never imported. The module spec says unknown-rank kanji import by default
(`docs/plans/suspended-kanji-import.md`). Rare-but-personally-relevant kanji
(names, domain vocabulary — precisely what miners suspend) are dropped.

**Suggestion.** Decide deliberately: either honor the spec (unknown rank →
import, treat as `rank = MAX` for sorting so they queue last), or update the
spec and the settings copy to state the exclusion. Recommendation: import
them but sort them last — the user suspended them for a reason, and the
frequency sort already prevents them from crowding out common kanji.

### 6. MEDIUM — Default new-card sort ignores half of the value signal

**Finding.** Default sort is raw Jiten frequency (`RecordsBase.kt:386`).
Frequency is the "value" axis (AdaptiveLoadCandidate.kt:29-31), but between
two equally common kanji, the one currently causing failed Anki reviews
(weakness, fsrsPressure, lapses) is worth strictly more. The
`balanced_priority` mode already encodes exactly this (weights 0.30 weakness /
0.25 risk / 0.20 difficulty / 0.15 suspended-pressure / 0.10 inverse
frequency, `NewCardSortPlanner.kt:19-23`) but is not the default.

**Suggestion.** Make `balanced_priority` the default sort for new
installations (keep stored settings untouched). It is the Pareto principle
applied to admission; plain frequency remains available for purists.

### 7. MEDIUM — Content-signature flips destroy earned progress and force re-study

**Finding.** Already tracked as Goal 42 (`plans/deep-review-goals-2026-07-08.md:274-321`)
plus Goal 39 (sync hard-deletes items the home path would retire): a remote
suspend/unsuspend flip changes the preferred example, changes
`answerSignature`, and `alignAnswerSignature` resets the scheduler (S=0.4,
rung demoted, phase new_learning; `StudyQueueSeeder.kt:361-405`). Months of
ladder/FSRS state die, and the user re-does all of it.

**Suggestion.** Treat these as core-principle bugs, not hygiene: protecting
earned scheduler state *is* minimizing unneeded studying. Prioritize Goals 39
and 42 above new features. Concretely: key the signature to the kanji family
rather than the volatile "preferred example", and only reset when the
*meaning* materially changes, not when suspension status reshuffles which
example is preferred.

### 8. LOW-MEDIUM — Post-promotion 7-day validation reviews are the cheapest place to trim

**Finding.** Promotion clones a > 21-day-stability memory into the next rung
and caps only the first due date at 7 days (`ReviewTransitionEngine.kt:389-402`).
At that point retrievability is ≈ 0.98, so each upper rung is a
single-review formality; a healthy card pays 2 such validation reviews
(days ~20 and ~27) to climb from `kanji_meaning` to `word_reading`.

**Assessment.** This is mostly fine — one test per new skill is a defensible
minimum, and the burden is small. Two refinements worth considering:
- When the promotion happens at very high stability (e.g. FSRS interval
  > 2× threshold), allow a **double promotion** or promote directly to the
  highest enabled rung and validate once there. The intermediate recognition
  variants (`font_meaning`) add little once `word_reading` is passed.
- Do **not** shorten the 7-day cap; a shorter cap adds reviews (anti-principle)
  and a longer one delays skill validation.

### 9. LOW — Demotion needs 3 consecutive months-apart failures before help arrives

**Finding.** Demotion requires 3 consecutive real-due Agains
(`RecordsBase.kt:369`; `ReviewTransitionEngine.kt:342-353`). At ceiling
intervals (months), a struggling kanji fails for close to a year before Kani
demotes it into actual remediation. That's not over-study, it's *under*-help:
the user keeps paying failed reviews without getting the repair the app
exists to provide.

**Suggestions.**
- Demote immediately when the **first review after a promotion** fails — a
  failed validation is direct evidence the promotion was wrong; waiting for
  two more multi-month cycles serves nobody.
- Consider weighting the streak by predicted retrievability at failure time:
  an Again at R ≈ 0.95 is much stronger evidence of a skill gap than an Again
  at R ≈ 0.70. (FSRS post-lapse shrink already shortens the interval, so this
  is about *rung* movement only.)
- Keep the default streak at 3 for ordinary review failures.

### 10. LOW — Hidden knobs that shape everything: `activeQueueCap`, `matureDays`, `matureSupportThreshold`

**Finding.** All three exist only as `kikuDefaults()` constructor args
(`RecordsSyncModels.kt:123-127`) with no settings key or UI, yet they control
admission throughput and the entire retirement lifecycle. Meanwhile
`ladder_promotion_interval_days` is editable but `matureDays` (both default
21) is not — editing one silently desynchronizes "mature" analytics and
retirement from promotion behavior.

**Suggestions.**
- Expose `activeQueueCap` in the Deck limits panel (bounded, e.g. 8–100) —
  especially important if suggestion #1 is not adopted.
- Keep `matureDays`/`matureSupportThreshold` internal, but derive `matureDays`
  from `ladder_promotion_interval_days` (or document why they are independent)
  so the two 21s stop being a coincidence.
- Add upper bounds to the ladder threshold inputs (currently unbounded,
  `docs/full-codebase-review-2026-07.md` 4.5); a promotion interval of 100000
  days silently freezes the ladder.

### 11. LOW — Defaults that are right and should be defended

For the record, these defaults were reviewed and are well-aligned with the
principle; do not "fix" them:

- `newPerDay = 3`, adaptive auto focus max 5, strain governor — the tight
  intake is the product.
- Starting rung `kanji_meaning` (rung 5 of 7) for suspended imports — lower
  rungs stay demotion-only remedial territory; a healthy card never sees
  them.
- Learning answers being practice-only, and no max-reviews/day — with a
  24-item ceiling the review load is naturally bounded; adding an Anki-style
  review limit would just create invisible backlog.
- Retention 0.90; rank floor 100 (excluding the top-99 kanji the user
  certainly knows); Pass/Fail-only UI on `write_kanji`.
- Frequency-retention override staying **off** by default is fine, but the
  bounds mismatch (global slider 80–97% vs per-rank 10–99%,
  `FrequencyRetentionRanges.kt:8-11` vs `SettingsInputRules.kt:66-68`) should
  be unified before anyone recommends the feature.

---

## Suggested execution order

| # | Change | Principle payoff | Size | Risk |
|---|--------|------------------|------|------|
| 1 | Ceiling graduation / cap-exclusion for immortal items (finding 1) | Unblocks all future high-value admissions; ends zero-value reviews | M | Low — additive state + reopen already exists |
| 2 | Admission gate on mature support (finding 3) | Deletes forced study of already-repaired kanji | S | Low |
| 3 | Goals 39/42: stop deleting/resetting earned state (finding 7) | Prevents forced re-study of months of progress | M | Medium — sync-path change, needs live AnkiDroid gate |
| 4 | Evidence-seeded difficulty + graduation rating (finding 2, first two bullets) | 1–3 fewer redundant exposures per admitted kanji | M | Low — bounded FSRS inputs |
| 5 | `balanced_priority` as default sort (finding 6) | Better kanji chosen per slot | S | Low |
| 6 | Weak-cards import default-on with stricter thresholds (finding 4) | Targets actual repeated misses | S | Medium — changes first-sync scope; needs onboarding copy |
| 7 | Unknown-rank import decision (finding 5) | Stops silently dropping personally relevant kanji | S | Low |
| 8 | Evidence-based starting rung (finding 2, third bullet) | Skips 2 validation reviews for already-competent kanji | M | Medium — ladder semantics |
| 9 | Fail-fast demotion after failed promotion validation (finding 9) | Faster remediation, fewer wasted failed reviews | S | Low |
| 10 | Expose/derive hidden knobs, bound thresholds (finding 10) | Transparency; prevents silent misconfiguration | S | Low |

Notes for implementers:
- Items 1–3 change scheduler/persistence behavior: regenerate golden
  timelines (`core/src/test/resources/.../scheduler-goldens/`) deliberately
  and update `docs/ladder-and-srs-system.md` §14 and AGENTS.md scheduler
  notes in the same change.
- Items 3 and 6 touch sync/provider behavior: per AGENTS.md, they require the
  live AnkiDroid emulator gate before release.
- Every default change must apply to new installs only (stored settings win),
  following the existing `repairOldDefaultImportSettings` pattern
  (`SyncSettings.kt:148-162`) only where a forced migration is genuinely
  justified.
