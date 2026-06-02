# Anki parity gap map

Status captured from `main` at repo-health loop 01. This map is scoped to Anki scheduling, import, and study behavior parity; it is not a release checklist.

## Current parity anchors

- Provider sync reads AnkiDroid's flashcard provider, mirrors Kiku / Mining notes, and imports active, suspended, tagged, weak, and browser-query cards.
- FSRS memory state is imported when AnkiDroid card columns or serialized `data` expose finite `stability`, `difficulty`, and `retrievability` values; explicit `fsrs_*` columns win over legacy columns, and serialized aliases `s`, `d`, `r` are a fallback only when no explicit memory field is present.
- Kani's scheduler keeps Anki-style `new_learning`, `review`, and `relearning` phases.
- Learning and relearning support `Again`, `Hard`, `Good`, and `Easy` transitions: `Again` resets to the first step, `Hard` repeats/uses the Anki first-step midpoint behavior, `Good` advances, and `Easy` graduates immediately.
- Review cards use FSRS for interval/stability/difficulty updates and enter relearning on `Again`.
- The app intentionally overlays Anki with a Kani-specific ladder: `write_kanji`, `similar_kanji`, `type_meaning`, `meaning_kanji`, `kanji_meaning`, `font_meaning`, and `word_reading`.
- The study entry point is `Study now`; settings expose import sources, rank/retention ranges, learning steps, study-ahead behavior, workload, ladder order, and promotion/demotion thresholds.

## Gap map

| Area | Current behavior | Anki parity gap / decision needed | Suggested next check |
| --- | --- | --- | --- |
| Deck / card identity | Kani focuses on kanji-family study items derived from imported notes/cards. | There is no user-facing deck browser or per-card template route; decide whether Kani should remain a companion repair queue or expose deck/card identity for parity diagnostics. | Trace `DashboardRow` and `StudyQueueSeeder` fields against provider deck/card metadata. |
| Daily limits | Workload and focused study policies cap how many kanji Kani selects. | Anki's new/review daily limits and deck option inheritance are not modeled one-for-one. | Compare `AdaptiveLoadPlanner` / workload settings against Anki deck options users expect. |
| Bury / siblings | `StudySessionSelector` collapses active items by kanji family and chooses one active family item; `SiblingSuppressionPolicy` records mature/writing-remediation dominance and clears stale dominance when no valid dominator remains. | This approximates sibling suppression, but does not expose Anki's bury-new, bury-review, bury-interday, or manual-unbury controls. | Treat current behavior as Kani session/queue suppression unless Anki-style bury settings become a product goal. |
| Lapses / leeches | Review `Again` increments lapses and drives relearning plus Kani ladder demotion after configured fail streaks. | Anki leech tagging/suspension policy is not mirrored as an explicit feature. | Decide whether Kani should import/display leech tags or leave leech handling to AnkiDroid. |
| Filtered / custom study | Kani supports import via browser query and app-side study-ahead / workload controls. | Anki filtered-deck semantics are not implemented as a first-class study mode. | Validate whether browser-query import is enough for the target Kiku workflow. |
| Scheduling source of truth | Kani persists its own FSRS task memory after import and review. | Ongoing two-way scheduler sync with AnkiDroid review logs is not documented as supported. | Document whether Kani is intentionally local-after-import or needs review-log reconciliation. |
| UI ratings | Study UI maps `Pass` to `good` and `Fail` to `again`; `write_kanji` only shows Pass/Fail. | Full Anki four-button review UX is intentionally hidden for writing and simplified elsewhere. | Keep this as an explicit product decision when evaluating parity requests. |
| Live provider confidence | AGENTS documents real AnkiDroid emulator gates for provider/sync changes. | Local live collection validation is required for release-risk sync/provider changes, but routine docs/scheduler work can use deterministic gates. | Keep CI fixture and local live gate responsibilities separate in future runbooks. |

## Near-term non-risky follow-ups

1. If users ask for Anki-style bury controls, define product scope for bury-new, bury-review, bury-interday, and manual unbury before adding code.
2. Decide and document whether leech tags/suspension should be imported from AnkiDroid or intentionally owned by AnkiDroid.
3. Document scheduler source-of-truth boundaries: one-time import of Anki FSRS memory versus ongoing two-way review synchronization.
4. If deck parity matters, inventory which provider columns expose deck id/name and whether they survive current mirror storage.
