# Anki manual parity checklist for Kani

Scope: current Kani study/sync flows versus Anki manual behavior for learning/relearning, bury/suspend, leeches, deck options, FSRS, browser/search, stats, import, and sync.

Current Kani snapshot used for this review:

- Scheduler facade: `core/src/main/kotlin/dev/bee/kanjianki/core/BridgeScheduler.kt`
- Review transitions: `core/src/main/kotlin/dev/bee/kanjianki/core/ReviewTransitionEngine.kt`
- Learning/default scheduler settings: `core/src/main/kotlin/dev/bee/kanjianki/core/RecordsSchedulerModels.kt`
- Ladder/state/import defaults: `core/src/main/kotlin/dev/bee/kanjianki/core/RecordsBase.kt`
- Local study persistence/stats: `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudy.kt`, `StudyStatsQueries.kt`
- Import/sync rules: `sync-domain/src/main/kotlin/dev/bee/kanjianki/syncdomain/ImportRuleMatch.kt`, `ProviderCardPolicy.kt`, `SyncMirrorPolicy.kt`

## P0 / must align before calling parity “done”

1. Relearning steps support the Anki empty-step behavior.

   Current Kani: `LearningStepsSettingsPolicy` and `LocalStoreStudySettings` preserve an explicit blank `review_relearning_steps_minutes` value, `LearningStepSettings` keeps `reviewStepsMinutes` empty when allowed, and `ReviewTransitionEngine.applyReviewAgain()` skips `RELEARNING` with the default 1-day post-lapse interval when relearning steps are empty.

   Manual anchor: Anki deck options say: “The same as learning steps, but for lapsed cards. When you fail a review card (press **Again**), the card goes through _relearning steps_, before it becomes a review card again. If you leave the steps blank, the card will skip relearning, and will be assigned a new interval of 1 day by default.” Source: https://docs.ankiweb.net/deck-options.html#relearning-steps

   Checklist:
   - [x] Preserve a true empty `reviewStepsMinutes` value instead of falling back to `[10]` when the user explicitly blanks relearning steps.
   - [x] On review `Again`, if relearning steps are empty, skip `RELEARNING`, set review phase/state, and schedule the default 1-day post-lapse interval.
   - [x] Add scheduler and app settings tests for blank relearning steps, while keeping non-empty relearning-step tests green.

2. Add explicit leech parity or intentionally document a Kani alternative.

   Current Kani: tracks `lapses`, `taskLapses`, weak-card import thresholds (`DEFAULT_IMPORT_WEAK_LAPSES = 2`), and recent mistakes, but I found no leech threshold/action equivalent that tags, warns, or suspends a local item/card after repeated review lapses.

   Manual anchor: “Leeches are cards that you keep forgetting… Each time a review card ‘lapses’ (is failed while it is in review mode), a counter increases. When this counter reaches 8, Anki tags the note as a leech and suspends the card. The threshold, and whether to suspend or not, can be adjusted in the deck options.” Source: https://docs.ankiweb.net/leeches.html

   Checklist:
   - [ ] Decide whether Kani should implement leech detection or explicitly remain “leech-informed” only.
   - [ ] If implementing: add configurable threshold/action, record leech events, surface them in stats/timeline, and decide what “suspend” maps to for a Kani `StudyItem`.
   - [ ] If not implementing: add docs/UI copy that Kani imports weak/lapsed Anki cards but does not apply Anki’s leech tag/suspend policy locally.

3. Verify sibling burying semantics against Anki’s new/review/learning order.

   Current Kani: `BridgeScheduler.seedQueue()` and related active queue methods pass seeded items through `SiblingSuppressionPolicy`; this looks like Kani’s bury/sibling suppression equivalent. Targeted `BridgeSchedulerTest` and `FocusQueuePolicyTest` coverage now keeps due learning and relearning repeats ahead of due review siblings in the same family for both study-session selection and the home focus queue, and study-ahead/interday learning repeats now outrank due review siblings during same-family session selection. Additional targeted coverage confirms Kani's same-family selection order follows Anki's gather order: intraday learning, interday learning, review, then new. There is no manual unbury UI/control in the scheduler/app code searched; Kani currently has session/queue same-family selection plus persistent mature-sibling suppression that clears when no valid dominator remains, not an Anki-style “unbury buried cards now” action.

   Manual-unbury boundary: same-session same-family hiding is covered by `coreSessionSelectionHidesSameFamilyWithoutPermanentSuppression()` and related learning-repeat regressions; persistent `suppressedByTaskType` is for mature or writing-remediation sibling dominance and clears in `SiblingSuppressionPolicy` when the dominator no longer qualifies.

   Manual anchors:
   - Studying: “When you answer a card that has siblings, Anki can prevent the card’s siblings from being shown in the same session by automatically ‘burying’ them. Buried cards are hidden from review until the clock rolls over to a new day or you manually unbury them…” Source: https://docs.ankiweb.net/studying.html#siblings-and-burying
   - Deck options: “When Anki gathers cards, it first gathers intraday learning cards, then interday learning cards, then review cards, and finally new cards.” It also exposes separate bury toggles for new, review, and interday learning siblings. Source: https://docs.ankiweb.net/deck-options.html#burying

   Checklist:
   - [x] Confirm Kani’s suppression order prefers intraday learning, interday learning, review, then new.
   - [x] Confirm due learning/relearning cards are not buried behind due review siblings when they are time-critical.
   - [x] Confirm study-ahead/interday learning repeats are selected before due review siblings in the same family.
   - [x] Confirm the home focus queue surfaces that same due learning/relearning sibling instead of a higher-rung review sibling.
   - [x] Add explicit tests named around Anki bury semantics for due learning/relearning repeats, not just generic sibling suppression.
   - [x] Decide whether “manual unbury” has a Kani concept; if not, document that burying is session/queue suppression only.

## P1 / high-value parity gaps

4. Deck-option surface should map Anki concepts to Kani settings one-for-one where Kani has an equivalent.

   Current Kani: exposes learning steps, relearning steps, target retention, interval multipliers, frequency retention, study-ahead, adaptive workload, new-card sort, ladder promotion/demotion, active/suspended/tagged/weak/browser-query import filters, and auto-sync. It is not a full deck model with per-deck new/review limits or preset inheritance.

   Manual anchors:
   - “Deck options primarily control the way Anki schedules cards.” Source: https://docs.ankiweb.net/deck-options.html
   - “New Cards/Day… controls how many new cards can be introduced each day…” and “Maximum Reviews/Day… set an upper limit on the number of review cards to show each day.” Source: https://docs.ankiweb.net/deck-options.html#daily-limits

   Checklist:
   - [ ] Add/confirm UI copy that Kani settings are global/app-level unless otherwise stated; they are not Anki deck presets.
   - [ ] Map Anki “new cards/day” to Kani adaptive new admission / workload controls, or document the mismatch.
   - [ ] Map Anki “maximum reviews/day” to Kani daily workload cap if present; otherwise document no direct equivalent.
   - [ ] Keep learning/relearning step text examples compatible with Anki (`1m 10m`, `h` suffix); Kani parser currently supports `m`/`h`.

5. FSRS parity should distinguish “uses FSRS-like scheduling” from “Anki FSRS feature parity.”

   Current Kani: has a bundled `fsrs-java` module, `LatestFsrsAdapter`, `SchedulerParameters.targetRetention`, rating multipliers, and optional frequency-based retention. It uses FSRS for local scheduling and stores imported FSRS memory (`stability`, `difficulty`, `retrievability`), but it does not appear to import Anki deck preset FSRS parameters wholesale or expose Anki's optimizer/reschedule controls.

   Local FSRS parameters: Kani's scheduler defaults are `targetRetention=0.90`, `againMultiplier=0.45`, `hardMultiplier=1.20`, `goodMultiplier=2.00`, and `easyMultiplier=3.10` in `RecordsSchedulerModels.SchedulerParameters.defaults()`. Settings persist those values under `scheduler_target_retention` and `scheduler_*_multiplier`, clamp tuned multipliers in Kani-specific ranges, and optionally override target retention by Jiten rank via `scheduler_frequency_retention_*`; this is local scheduler tuning, not imported Anki deck preset FSRS parameters.

   Manual anchors:
   - “The Free Spaced Repetition Scheduler (FSRS) is an alternative to Anki’s legacy SuperMemo 2 (SM-2) algorithm…” Source: https://docs.ankiweb.net/deck-options.html#fsrs
   - “Desired retention controls how likely you are to remember cards when they are scheduled for a review… The default value of 90%…” and Anki recommends staying below 97%. Source: https://docs.ankiweb.net/deck-options.html#desired-retention

   FSRS memory import boundary: the AnkiDroid reader asks for `fsrs_stability`, `fsrs_difficulty`, `fsrs_retrievability`, legacy `stability`, `difficulty`, `retrievability`, and serialized `data`. `ProviderCardPolicy` keeps the first finite explicit-column value for each memory field, preferring `fsrs_*` over legacy columns; only when no explicit memory field is present does it parse serialized `data` keys `stability`, `difficulty`, `retrievability` or aliases `s`, `d`, `r`. It does not import Anki optimizer parameters, review logs, or reschedule history through this path.

   Checklist:
   - [x] Document that Kani uses FSRS for local scheduling but has its own parameters/default multipliers.
   - [x] If importing from AnkiDroid FSRS memory, document exactly which fields are read (`stability`, `difficulty`, `retrievability` from explicit columns or serialized data).
   - [x] Add parity tests around target retention and rating-specific interval ordering (`Again < Hard < Good < Easy`) if missing.
   - [ ] Decide whether to expose Anki-style parameter optimization/reschedule controls; otherwise mark non-goal.

6. Browser/search parity should lean on Anki query semantics for import filters.

   Current Kani: has an import source for `browser_query`, active/suspended/tagged/weak filters, and settings UI for browser query text. It does not look like Kani implements a full local Anki browser; it likely delegates or stores a configured Anki query used during provider sync.

   Manual anchors:
   - Searching card state terms include `is:due`, `is:new`, `is:learn`, `is:review`, `is:suspended`, `is:buried`, `is:buried-sibling`, and `is:buried-manually`. Source: https://docs.ankiweb.net/searching.html#card-state
   - Recent-event searches include `rated:1`, `rated:1:2`, `rated:7:1`, and `rated:31:4`. Source: https://docs.ankiweb.net/searching.html#recent-events
   - Browser card rows use state coloring; “if the card is flagged, use the flag colour, if the card is suspended, yellow, if the card’s note is marked, purple.” Source: https://docs.ankiweb.net/browsing.html

   Checklist:
   - [ ] Treat `browser_query` as raw Anki/AnkiDroid search syntax, and validate errors clearly instead of silently importing nothing.
   - [ ] Add docs/examples for useful Kani import queries: `is:suspended`, `rated:31:1`, tag filters, FSRS properties if available through AnkiDroid.
   - [ ] If Kani has a local browse/detail screen, decide whether it should show suspended/flagged/marked/buried state badges from Anki.

## P2 / important but less blocking

7. Stats should clearly say which Anki stats are replicated and which Kani-specific stats are intentional.

   Current Kani: `StudyStatsQueries` provides time-on-task, recent mistakes, streaks, impact, outcome stats, answer-button counts (`again`, `hard`, `good`, `easy`), writing metrics, and sync-derived before/after weakness/support snapshots.

   Manual anchor: Anki stats include “Again, Hard, Good, or Easy” answer-button graphs and “percentage of correct reviews for each type of card.” Source: https://docs.ankiweb.net/stats.html#answer-buttons

   Checklist:
   - [ ] Keep answer-button counts aligned with Anki rating names.
   - [ ] Label writing/ladder/impact metrics as Kani-specific, not Anki manual parity.
   - [ ] Consider adding mature/young/new/relearn breakdown if parity with Anki stats is a product goal.

8. Import/sync should document source-of-truth and conflict boundaries.

   Current Kani: syncs from AnkiDroid’s provider, scans active/suspended/tagged/weak/browser-query sources, extracts FSRS memory state, and mirrors active/suspended card indexes. Auto-sync exists locally, but this is not AnkiWeb multi-device sync.

   Manual anchors:
   - AnkiWeb sync “allows you to keep your collection synchronized across multiple devices, and to study online.” Source: https://docs.ankiweb.net/syncing.html
   - “Once syncing is enabled, Anki will automatically sync each time your collection is closed or opened.” Source: https://docs.ankiweb.net/syncing.html#automatic-syncing
   - Initial sync conflicts can be one-way, and Anki recommends backups before manual merging. Source: https://docs.ankiweb.net/syncing.html#merging-conflicts
   - Text import supports plain text, CSV, TSV, semicolon-delimited files, and HTML-in-fields options. Source: https://docs.ankiweb.net/importing/text-files.html

   Checklist:
   - [ ] Make clear that Kani sync is AnkiDroid-provider import/mirror, not AnkiWeb collection sync.
   - [ ] Document what data Kani writes back, if any, versus what remains read-only/imported.
   - [ ] Document conflict behavior and backup expectations before destructive provider/archive operations.
   - [ ] If text/CSV import is planned, define whether it mirrors Anki text import behavior or is out of scope.

## Evidence summary

Likely already close:

- Four ratings are present (`again`, `hard`, `good`, `easy`), with UI able to reduce to Pass/Fail on selected Kani rungs.
- Learning flow broadly follows Anki: Again resets to first step; Good advances; Hard repeats/uses mid-step behavior; Easy graduates.
- Kani tracks FSRS memory and local review state, including stability/difficulty/retrievability import parsing.
- Import filters cover active/suspended/tagged/weak/browser-query sources, which is a practical bridge to Anki Browser search workflows.
- Stats include answer-button counts plus Kani-specific study/writing/impact views.

Remaining highest-risk mismatches:

- Leech behavior appears absent as a first-class local policy.
- Sibling burying is present conceptually but needs tests/docs for Anki’s exact gathering order and learning-card exceptions.
- Kani has FSRS scheduling, but not full Anki FSRS deck-option parity such as optimizer/reschedule/preset semantics.
