# Kani Stats visual polish + scroll screenshots plan

## User complaint / acceptance evidence

Bee said the current shipped Stats fix screenshot “looks ugly” and explicitly asked to “scroll and take screenshots” and make a Kanban.

Inputs:
- Original broken screenshot cache path: `/Users/autumnskerritt/.hermes/image_cache/img_bba9b412613b.jpg`
- Previous shipped release verification screenshot: `/Users/autumnskerritt/kanji_anki_worktrees/kani-stats-real-screenshot-20260614/artifacts/release-v0.4.127-verification/stats-release-ja.png`
- Current verified app/package target: `dev.bee.kanjianki`
- Previous fix/release baseline: `main` commit `5546c04accbc7dc6992957c1578c3371a1d5248e`, release `v0.4.127`

## Scope

Polish the real Kani Stats/Progress analytics screen so it is not merely “not broken,” but visibly pleasant and usable on a narrow phone in Japanese.

Hard constraints:
- Work on a fresh branch/worktree: `fix/kani-stats-visual-polish-scroll-20260614` at `/Users/autumnskerritt/kanji_anki_worktrees/kani-stats-visual-polish-scroll-20260614`.
- Do not clobber the dirty original checkout at `/Users/autumnskerritt/kanji_anki`; it has unrelated Cheap Ralph/UI loop changes.
- Screenshot acceptance must use the real Kani app shell/route (`dev.bee.kanjianki` → `統計`), not only preview/mock composables.
- Preserve the single-bottom-nav fix: exactly one standard nav (`ホーム / 学習 / 統計 / 設定`) and no legacy `進捗 / プロフィール` nav.
- Preserve the Stats/Progress hard performance budget: usable loaded state under 2 seconds on representative local data; do not add synchronous route-open recomputation.
- No visual pass without PNG artifacts from multiple scroll positions.

## Visual acceptance criteria

The final screen should be judged on the actual narrow-phone Japanese render:

- Overall: no “ugly” dense gray wall; spacing, hierarchy, colors, and cards feel deliberate and cute/pleasant while still matching the Kani theme.
- Top/mid/bottom scroll positions all look good in screenshots.
- Cards have consistent padding, radius, elevation/borders, and readable section hierarchy.
- Donut/chart distribution cards are not cramped, clipped, or visually disconnected.
- Japanese labels and percentages remain horizontal/readable; no one-character or digit-by-digit wrapping.
- Legends stay attached to labels and values.
- Content is not hidden by the bottom nav or Android gesture inset at the bottom of scroll.
- Accessibility/contrast remains acceptable; charts expose useful semantic summaries and do not rely on color alone.
- Percent/count math is coherent and labels/slices sum as expected.

## Screenshot matrix

Required artifacts after implementation:

- Narrow-phone Japanese locale, real Kani app route:
  - Stats top screenshot
  - Stats mid-scroll screenshot
  - Stats bottom-scroll screenshot
  - UIAutomator XML for each position or one final full dump
- If the app supports themes and it is cheap to do, include at least the current default theme and dark mode; otherwise note why not.
- Screenshots must be saved under `artifacts/kani-stats-visual-polish-scroll-20260614/` in the worktree.

## Validation commands / gates

Workers should choose the fastest suitable subset, then QA should run the normal project gates before merge/release:

- Local compile/test as needed, generally via `./gradlew ciFast` when practical.
- Relevant screenshot/instrumentation tests or manual emulator run through real `dev.bee.kanjianki`.
- GitHub PR checks/Sonar/CodeQL as applicable before merge.
- Release/download verification: install the published APK/update artifact that includes the fix and re-capture scroll screenshots from the release build.

## Kanban graph

KANBAN_GRAPH_PLACEHOLDER
