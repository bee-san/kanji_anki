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

Board: `kani`
Tenant: `kani-stats-visual-polish-scroll-20260614`
Worktree: `/Users/autumnskerritt/kanji_anki_worktrees/kani-stats-visual-polish-scroll-20260614`
Branch: `fix/kani-stats-visual-polish-scroll-20260614`

Task graph:

1. `t_999a85d0` — design/audit: make Kani Stats visually pretty, not just bug-free (`design`)
   - Parents: none
2. `t_2369f960` — implement: polish real Kani Stats visual layout (`coding`)
   - Parents: `t_999a85d0`
3. `t_06e28445` — implement: Kani Stats scroll screenshot harness/artifact path (`coding`)
   - Parents: `t_999a85d0`
4. `t_9162411a` — uitest: scroll real Kani Stats and capture top/mid/bottom screenshots (`uitester`)
   - Parents: `t_2369f960`, `t_06e28445`
5. `t_edcad26e` — design: critique Kani Stats scrolled screenshots before QA (`design`)
   - Parents: `t_9162411a`
6. `t_358d2efd` — implement: focused Kani Stats polish from design critique (`coding`)
   - Parents: `t_edcad26e`
7. `t_f6825a98` — test: Kani Stats visual polish regression, contrast, and performance gates (`coding`)
   - Parents: `t_358d2efd`
8. `t_60593038` — uitest: final Kani Stats scroll screenshots and XML evidence (`uitester`)
   - Parents: `t_f6825a98`
9. `t_22c8c169` — design: final UX signoff for polished Kani Stats screenshots (`design`)
   - Parents: `t_60593038`
10. `t_80b59c0a` — qa/release: ship polished Kani Stats visual refresh (`qa`)
    - Parents: `t_22c8c169`
11. `t_0768896d` — ops: verify downloadable Kani release with scrolled Stats screenshots (`ops`)
    - Parents: `t_80b59c0a`
12. `t_580a62f9` — notify: polished Kani Stats release and scrolled screenshots complete (`ops`)
    - Parents: `t_0768896d`

Hard gates encoded in the cards:

- Use the fresh worktree/branch above; do not clobber dirty original checkout `/Users/autumnskerritt/kanji_anki`.
- Real app route verification: package `dev.bee.kanjianki`, normal shell navigation to `統計`, Japanese locale where practical.
- Scroll screenshots are mandatory: top, middle, and bottom positions before design critique and again before QA/release; release-build screenshots after publish.
- Design approval threshold: score >= 8/10 and no must-fix issues.
- Downloadable/released APK verification is mandatory before final completion.

