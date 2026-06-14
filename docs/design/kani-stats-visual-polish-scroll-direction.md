# Kani Stats visual polish direction

Task: `t_999a85d0` / `design/audit: make Kani Stats visually pretty, not just bug-free`

Inputs inspected:

- Prior broken screenshot: `/Users/autumnskerritt/.hermes/image_cache/img_bba9b412613b.jpg`
- Prior release screenshot: `/Users/autumnskerritt/kanji_anki_worktrees/kani-stats-real-screenshot-20260614/artifacts/release-v0.4.127-verification/stats-release-ja.png`
- Plan: `docs/plans/2026-06-14-kani-stats-visual-polish-scroll-screenshots.md`
- Current UI code:
  - `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStats.kt`
  - `app/src/main/kotlin/dev/bee/kanjianki/MainActivityShell.kt`
  - `app/src/main/kotlin/dev/bee/kanjianki/KaniBottomNavCompose.kt`
  - `app/src/main/kotlin/dev/bee/kanjianki/ProgressAnalyticsCompose.kt`
  - Legacy/simple stats code still present in `MainActivityStatsCompose.kt`, but the current route uses `ProgressAnalyticsDashboardScreen` through the standard shell.

## Visual diagnosis

The old broken screenshot is unacceptable, not just rough:

- It has two bottom navs at once: legacy `進捗 / プロフィール` plus standard `統計 / 設定`.
- Distribution legends wrap into vertical Japanese and digit-by-digit percentages (`意 / 味 / 1 / . / 2 / %`), so the chart data is effectively unreadable.
- Donuts sit low in tall cards while legends float on the right edge; the chart and labels do not feel connected.
- The screen reads as a dense white/pink wireframe with cramped columns, not a cute analytics dashboard.
- Bottom content is hidden/competing with stacked nav bars and the gesture area.

The shipped release screenshot fixes the most severe nav/wrapping problem and uses the single standard nav, but still looks visually weak:

- The first viewport is dominated by one huge outer overview card with six equal metric cards. It feels like a spreadsheet inside a border rather than a lovable Kani screen.
- All six metric cards have identical size and weight, so there is no hierarchy. Important values (`0`, `0%`, `0日`) are not prioritized or explained.
- The mascot block is cute in isolation, but it sits as a large decorative island that competes with the title instead of anchoring a hero summary.
- The 3-column metric grid is too tight on a narrow phone. Labels and details wrap awkwardly (`連続学習を / 続けましょ / う`) and lower-row cards are clipped by the bottom nav in the top screenshot.
- Pink borders outline nearly everything. With little elevation/tonal contrast, the page feels boxy and unfinished.
- The top card border continues under the nav, implying the content is not comfortably separated from the persistent bottom bar.

## Target visual direction

Make Stats feel like a cute, readable learning dashboard, not a raw analytics dump.

Personality:

- Soft Girlypop Kani: warm pink background, plum text, coral primary, teal/gold/blue accents.
- Rounded and friendly, but with fewer visible boxes. Use soft tinted panels, section cards, chips, and progress tracks instead of equal bordered tiles everywhere.
- Calm hierarchy: one clear hero message, then grouped insights, then deeper charts.

Information hierarchy:

1. Hero summary: "today / streak / accuracy / reviews" in one reassuring summary area.
2. Quick metrics: 2-column cards on narrow phones, not 3 columns.
3. Visual trends/distributions: charts get enough width and legends attach to them.
4. Detail lists: weak kanji, support-needed, by-level rows, tips.

## Prioritized must-fix changes

### P0 — Preserve hard constraints while polishing

Do not regress these:

- Exactly one standard bottom nav: `ホーム / 学習 / 統計 / 設定` from `KaniBottomNavBar`.
- No legacy `進捗 / プロフィール` nav from `ProgressAnalyticsBottomNav`.
- No vertical Japanese/digit wrapping anywhere in legends, chips, card labels, percentages, or nav text.
- No content behind nav or gesture area at top/mid/bottom scroll screenshots.
- Loaded usable state must stay under 2 seconds; do not add synchronous recomputation on route open beyond the existing cache/snapshot path.

### P1 — Replace the cramped 3-column overview grid on narrow phones

Current code: `ProgressMetricGrid(columns = 3)` in `ProgressOverviewSection`, with each `ProgressMetricCard` constrained to one third of the phone width.

Direction:

- On widths below ~420dp, render overview metrics in 2 columns, or better: one hero row plus two-column secondary cards.
- Promote the most meaningful metric into a hero strip: current streak + reviews/accuracy should be instantly scannable.
- Use min-height cards, but avoid very tall narrow cards. Cards should be roughly 120-140dp wide on narrow phones, not 90-100dp.
- Keep Japanese labels horizontal by using `maxLines = 1` with ellipsis only for secondary details, or by shortening copy where needed.

Acceptance evidence to look for:

- `連続日数`, `学習時間`, `集中セッション`, and their details stay horizontal.
- No metric detail breaks into awkward single-character lines.
- The first screen no longer looks like six narrow bordered boxes.

### P1 — Make the hero section feel intentional/cute

Current release screenshot: title, subtitle, mascot island, then grid all inside one giant border.

Direction:

- Keep the crab, but tie it to a hero message. Example structure:
  - Left: `統計の概要` + short encouragement (`今日のKani進捗を見てみよう`)
  - Right: small crab badge, 56-64dp, not a huge competing tile
  - Below: one tinted summary pill/card, e.g. `今日は復習0件 · 正答率0% · 連続0日`
- Use one outer section card at most; inner metric cards should use softer fill/elevation and less border weight.
- Avoid empty-looking white space above metrics. The top third should feel composed, not like a large blank card.

### P1 — Improve bottom-scroll comfort

Current `MainActivityShell.kt` has the scroll box weighted above the nav and `ProgressAnalyticsDashboardScreen` only adds `padding(bottom = 6.dp)`. The screenshot shows content clipped near the nav at top view, and the plan requires bottom screenshots.

Direction:

- Add route content bottom padding sufficient for the floating nav and gesture area in the scrollable content, not just the shell. Target at least 24-32dp of visible breathing room above the nav in bottom screenshots.
- Final bottom screenshot should show the last card fully ending above the nav, with background visible between content and nav.
- Keep `systemBarsPadding()` and the standard nav; do not compensate by duplicating nav or adding hardcoded system-bar overlays.

### P1 — Make distribution cards readable before adding more decoration

Current risk from broken screenshot: donut/legend row can collapse into vertical text on narrow phones.

Direction:

- Distribution cards should stack chart above legend when width is tight, not donut left / legend right.
- Donut size can be 96-112dp, centered. Legend rows should be full width below with color dot, label, value, percent in one horizontal row.
- If labels are Japanese and long, use a two-line legend row with label on first line and value/percent on second line, never character-by-character wrap.
- Add center text only if it remains legible (`総数`, `正答率`); otherwise keep the donut clean.

### P2 — Soften the visual system

Direction:

- Reduce the all-pink outline effect. Use:
  - outer panels: very soft pink border / low elevation
  - inner cards: white or subtle tinted fill with lighter border
  - chips: filled selected range, outline unselected range
- Use accent backgrounds at low alpha for icon circles; keep coral/teal/gold/blue but do not make every card border pink.
- Consider alternating section fills: overview soft pink, reviews white, accuracy soft mint/pink, weakness soft lavender, as long as contrast stays good.

### P2 — Improve chart polish

Line/bar charts:

- Add soft track/grid lines and rounded strokes, but reduce visual noise.
- Put range chips near chart titles only if they fit; on narrow phones the chip row can wrap as a row below the title.
- Use less cramped axis labels. If x-axis labels collide, reduce label count rather than shrinking below readability.

Progress bars/lists:

- Keep row labels and values on the same line using `Arrangement.SpaceBetween`, but allow the value to move below the label if localized strings are too long.
- Bars should have a visible track and rounded fill.

### P2 — Typography rhythm

Direction:

- Title: 28-32sp on narrow phone, extra-bold, lineHeight +2-4sp.
- Section titles: 20-22sp, extra-bold.
- Metric values: 24-30sp for hero/primary metrics, 20-22sp for secondary cards.
- Labels/details: 11-13sp with lineHeight 15-17sp; do not set lineHeight equal to fontSize for Japanese body copy.
- Use `PlatformTextStyle(includeFontPadding = false)` where nav/short labels need tight vertical centering, but allow healthy lineHeight in card bodies.

## Optional nice-to-haves

- Add tiny Kani-specific empty-state copy for zero-data cards, e.g. "まだこれから🦀" instead of a wall of zeroes.
- Add a small celebratory sticker/chip when streak or accuracy improves.
- Add a subtle background blob/gradient behind the hero, but only if it does not reduce text contrast.
- Add dark-mode screenshot pass after default Girlypop is accepted.
- Add screenshot test tags/semantics for top/mid/bottom scroll capture labels so QA can automate the required evidence.

## Screenshot acceptance checklist for implementation/QA

Required artifacts under `artifacts/kani-stats-visual-polish-scroll-20260614/`:

- Narrow-phone Japanese Stats top screenshot.
- Narrow-phone Japanese Stats mid-scroll screenshot.
- Narrow-phone Japanese Stats bottom-scroll screenshot.
- UIAutomator XML for each position or one final full dump.

Review each screenshot for:

- One standard bottom nav only.
- No legacy `進捗 / プロフィール` nav.
- No vertical Japanese or digit wrapping.
- No clipping at card edges or bottom nav.
- Charts and legends attached and readable.
- Bottom screenshot has visible breathing room above nav/gesture area.
- Loaded state remains under 2 seconds on representative local data.
