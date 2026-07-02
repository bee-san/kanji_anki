# FSRS Ladder With Mature-Sibling Suppression

> **Historical design sketch — superseded.** This document describes an early
> 3-rung ladder plus a persistent mature-sibling suppression layer. The
> shipped scheduler uses a 7-rung single-item-per-family ladder, and the
> suppression layer was removed because a family can never contain a second
> item. See `documentation/ladder-and-srs-system.md` for the current,
> code-verified reference.

## Summary

Use FSRS to decide when a higher-context sibling is mature, then suppress easier siblings for that kanji family. This replaces the prior low-frequency maintenance idea: once the learner can recall the word-level prompt maturely, Kani should stop showing the single-kanji prompts unless the higher sibling lapses or its anchor changes.

The ladder is contextual kanji practice, not kanji-reading drill practice:

1. `kanji_meaning`: kanji shape -> meaning.
2. `font_meaning`: font-varied kanji shape -> meaning.
3. `word_reading`: the source word the user got stuck on, especially a suspended/missed source word -> reading.

Research basis:

- Anki defines mature review cards as interval `>= 21 days`: https://docs.ankiweb.net/getting-started.html
- FSRS controls intervals from memory state and desired retention: https://github.com/open-spaced-repetition/awesome-fsrs/wiki/ABC-of-FSRS
- Anki sibling burying avoids related prompts appearing too close together: https://docs.ankiweb.net/studying.html
- WaniKani separates meaning/reading demands, but higher associated vocabulary becomes the practical long-term use case: https://knowledge.wanikani.com/wanikani/japanese/readings-vs-meanings/

## Key Changes

- Keep FSRS scheduling state for the ladder steps Kani actually uses: `kanji_meaning`, `font_meaning`, and `word_reading`.
- Treat `writing_remediation` as a repair overlay rather than a normal review sibling.
- Add a sibling dominance layer:
  - `word_reading` dominates `font_meaning` and `kanji_meaning`.
  - `font_meaning` dominates `kanji_meaning`.
  - `writing_remediation` blocks normal siblings while active.
- Use the existing `matureDays = 21` concept as the suppression threshold, but apply it to Kani's internal FSRS step interval, not directly to AnkiDroid source cards.
- Remove long-term maintenance reviews for dominated lower-context siblings.

## Scheduling Rules

- A sibling is mature when its latest FSRS-scheduled interval is `>= settings.matureDays`, it has at least one successful due review, and its last rating was not `Again`.
- When a higher sibling becomes mature, mark lower siblings as `suppressed_by=<higher_task_type>` and exclude them from `nextSession()` and due counts.
- When a higher sibling is promoted but not mature yet, lower siblings are still hidden while that higher sibling is active. They are fallback state, not normal reviews.
- If the dominating sibling gets `Again`, loses its anchor, or is reset because the prompt changed materially, clear suppression and resume from the nearest useful lower sibling after sibling burying rules.
- For kanji with multiple materially different readings or meanings, dominance applies only within the same answer signature. One mature word for `しょう` should not suppress a future separate `ぞう` target if the app later supports both.

## Data And UI

- Add `suppressed_by_task_type`, `suppressed_at`, `mature_interval_days`, and `answer_signature` to ladder state.
- Persist separate FSRS task memories for `kanji_meaning`, `font_meaning`, `word_reading`, and the `writing_remediation` overlay so maturity is never inferred from a different prompt shape.
- Keep `matureDays` defaulted to `21`; do not add a new user setting unless the existing mature threshold becomes user-facing later.
- Settings remains focused on FSRS desired retention. No missed-day trigger and no separate "hide siblings after X days" control.
- Queue previews show only the active unsuppressed sibling for each kanji family.
- Word-reading prompts prefer the suspended or missed source word when one exists.

## Test Plan

- Mature `word_reading` suppresses `kanji_meaning` and `font_meaning`.
- Immature promoted `word_reading` hides lower siblings but does not permanently suppress them.
- `Again` on the dominating sibling clears suppression.
- Anchor reset clears suppression only for the affected answer signature.
- Multiple siblings for one kanji never appear in the same session/local day.
- Due counters match `nextSession()` with suppressed siblings excluded.
- Migration initializes old ladder rows without accidentally suppressing siblings.
- Full gate:

```sh
gradle :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:lintDebug
```

## Assumptions

- The product goal is contextual kanji competence: if the learner can recall the kanji in a mature word context, single-kanji prompts should stop appearing.
- FSRS decides maturity through its scheduled interval; Kani only applies the dominance rule after FSRS reaches the mature threshold.
- AnkiDroid source-card maturity still helps choose weak kanji, but Kani's sibling suppression uses Kani's own internal FSRS state.
- Implementation work should happen in a `/tmp` worktree, use focused commits, merge back to `main`, and verify with the Gradle gate.
