# Widget Expansion And App Improvement Plan (2026-07-15)

Primary focus: flesh out the home-screen widget surface from a single
fixed-palette due-count card into a small family of theme-aware, genuinely
useful widgets. A short secondary list of non-widget app improvements follows.

## Current Widget State (baseline)

- One Glance widget (`KaniWidget`) with `SizeMode.Responsive` at two tiers:
  compact (250x72) and expanded (250x130, adds a 7-day activity strip).
- Three states: `NOT_SET_UP`, `NOTHING_DUE`, `DUE_NOW`.
- Colors are hardcoded light-theme hex values (`0xFFFFF8F2`, `0xFFB94962`,
  ...). The widget ignores the in-app `KaniThemeChoice` palettes
  (girlypop / light / dark / autumn / system) and system dark mode.
- One tap target: the whole widget opens `MainActivity`, with
  `EXTRA_OPEN_STUDY` when due.
- Refresh: event-driven via `KaniWidgetUpdater` after committed sync, completed
  study, daily reminder evaluation, and system updates, plus the provider's
  hourly `updatePeriodMillis` fallback. No separate periodic worker (per
  AGENTS.md contract — keep it that way).
- `KaniWidgetSnapshotLoader` reuses `ReminderEligibilityPolicy` (D-S6: the
  widget may never advertise work the Study route would reject) and never
  creates the database just to render.

## Invariants That Must Survive Every Slice

1. D-S6: due counts always flow through `ReminderEligibilityPolicy`.
2. No database creation on launcher-initiated render; corrupt/half-created DB
   must degrade to `NOT_SET_UP`, never crash the launcher host.
3. No new periodic background worker for widget refresh. Event-driven updates
   plus provider metadata fallback remain the model.
4. Localized copy stays centralized in `core` (`WidgetTextCopy`) with the
   existing EN/JA pattern and `WidgetTextCopyTest` coverage.
5. Snapshot loading stays off the main thread (current `ioDispatcher` pattern).

## Widget Work, In Priority Order

### W1. Theme And Dark-Mode Support (foundation, do first)

Problem: the widget is permanently light-girlypop. On a dark launcher or for
users on the dark/autumn themes it looks broken and off-brand.

- Extract the widget's color roles (background, ink, muted, primary, accent,
  heatmap cell) into a small `KaniWidgetPalette` derived from the same palette
  data behind `KaniThemeChoice.resolvePalette(...)`. The Glance module cannot
  use `MaterialTheme`, so map `KaniColors` -> Glance `ColorProvider`s
  explicitly.
- Read the user's stored theme choice in `KaniWidgetSnapshotLoader` (it already
  opens `LocalStore`; add the setting to the snapshot) and resolve
  system-dark at render time.
- Honor system dark mode for the `SYSTEM` choice using day/night
  `ColorProvider`s so the launcher re-render on UI-mode change picks the right
  palette without a data reload.
- Tests: unit-test the palette mapping per theme choice x dark flag; keep a
  render smoke test in the existing instrumented widget test.

Acceptance: widget matches the in-app theme in all five choices, light and
dark, with screenshots as evidence.

### W2. Smarter Content In The Existing Widget

Problem: `DUE_NOW` shows only a count and streak; `NOTHING_DUE` shows only the
next useful time. The snapshot loader already computes a full
`DailyStudyPlanPolicy` plan — most of it is thrown away.

- Add a due-later line to `DUE_NOW` in the expanded tier: "5 more by 18:00"
  from the plan's lookahead clustering, mirroring the Today home card.
- Split the due count into review vs new ("12 reviews · 3 new") when both are
  non-zero; keep the single number in the compact tier.
- `NOTHING_DUE` expanded tier: show the 7-day strip (already built) plus best
  streak so the caught-up state still feels rewarding.
- All new strings go through `WidgetTextCopy` with EN/JA variants and tests.

Acceptance: expanded widget communicates "what now, what later" without
opening the app; copy tests cover every new string in both languages.

### W3. Per-Instance Widget Configuration + A Second Layout: Heatmap Widget

Problem: one widget style fits nobody perfectly. The stats screen already has
a well-liked heatmap; a launcher-resident version is the most-requested kind
of habit widget.

- Add a widget configuration activity (standard `APPWIDGET_CONFIGURE` flow)
  offering, per instance: style (Due card / Heatmap) and theme override
  (Follow app / specific theme). Persist per-`GlanceId` via Glance state.
- Heatmap style: a multi-week (e.g. 5x7) contribution-style grid reusing the
  day-summary query already used for the 7-day strip
  (`StudyStatsQueries.reviewDaySummaries`), with streak + due count in a
  header row. Large tier only; falls back to the due card below minimum size.
- Keep the existing zero-config default (drop the widget, get the due card)
  so the configure step is skippable/finish-fast with defaults.

Acceptance: two visually distinct styles placeable side by side with
independent settings; config survives process death and launcher restarts.

### W4. Interactive Tap Targets

Problem: the whole widget is one intent. Users cannot choose between "study"
and "just open the app", and there is no quick sync.

- Split tap targets: title/body opens Home; the action row ("Study now")
  deep-links straight to Study. Keep the current task-reuse intent flags —
  the duplicate-activity bug fixed in `kaniWidgetLaunchIntent` must not
  regress (add a test asserting flags on both intents).
- Optional (evaluate, do not commit blindly): a small "Sync" affordance using
  a Glance action callback that triggers the existing manual-sync path and
  shows a transient "Syncing..." state. Only do this if the sync path can be
  invoked headlessly without violating the manual-confirm-only rules for
  write-backs (repaired tagging must remain out of reach of this button —
  it is manual-confirm-only per the provider write-back contract).

Acceptance: launch tests cover both targets' intents and extras; no
write-back path is reachable from the widget.

### W5. Freshness At Day Boundaries

Problem: the due count can be up to an hour stale, which is most visible right
after midnight or a timezone change when "due today" semantics shift.

- Register the receiver for `ACTION_DATE_CHANGED`, `ACTION_TIMEZONE_CHANGED`,
  and `ACTION_TIME_CHANGED` and request a widget update on each. These are
  event-driven system broadcasts, not a periodic worker, so the refresh
  contract holds.
- When the snapshot computes `nextUsefulAtMillis` within the next hour,
  consider scheduling a one-shot inexact alarm to refresh at that moment so
  "More practice at 14:30" flips to "N reviews ready" on time. Gate this
  behind a small policy with tests; skip if it complicates the reminder
  alarm surface — the daily reminder evaluation hook may already cover it.

Acceptance: a device-time test (emulator `adb shell date` or robolectric
clock) shows the widget flipping states at the boundary without waiting for
the hourly fallback.

### W6. Polish: Picker Preview, Accessibility, Resize Behavior

- Add `previewLayout` (API 31+) and a `previewImage` so the widget picker
  shows a real preview instead of the loading layout.
- Ensure the root composable sets a content description from
  `WidgetTextCopy.widgetDescription(...)` (the copy exists; verify it is
  actually applied) and that the action row reads as a button.
- Add a third responsive tier for very wide placements (tablet/foldable) that
  lays the strip and text side by side instead of stacking.
- Revisit `minResizeWidth`/`targetCellWidth`/`targetCellHeight` metadata for
  modern launchers.

Acceptance: picker preview screenshot, TalkBack pass notes, and provider-info
unit test (`KaniWidgetProviderInfoTest`) updated for new metadata.

### Widget Testing Strategy (applies to all slices)

- Keep pure logic (palette resolution, copy, snapshot state selection,
  boundary policies) in unit-testable objects; extend
  `KaniWidgetSnapshotLoaderTest` and `WidgetTextCopyTest`.
- One instrumented render/launch test per widget style
  (extend `KaniWidgetLaunchInstrumentedTest`).
- Screenshot evidence per theme for W1 and per style for W3, stored the same
  way as the existing `screenshots/` manifests.
- Normal gate: `./gradlew ciFast`. Widget changes do not touch sync/provider
  behavior, so the live AnkiDroid emulator gate is not required — unless W4's
  sync button lands, in which case run the live suite before release.

## Secondary App Improvements (smaller, independent slices)

1. **Today card / widget parity audit.** The home Today card and the widget
   now share `DailyStudyPlanPolicy`; add a test that pins both surfaces to the
   same plan output so they can never disagree about due-later times.
2. **Quick-add from widget/app shortcuts.** Static app shortcuts
   (`shortcuts.xml`) for "Study now" and "Sync" — cheap, complements W4, and
   works on launchers where users prefer long-press shortcuts over widgets.
3. **Stats deep-link from heatmap widget.** Tapping the W3 heatmap opens the
   stats screen scrolled to the heatmap, reusing the existing navigation
   extras pattern.
4. **Onboarding mention of the widget.** After the first successful sync,
   surface a one-time, dismissible hint that a home-screen widget exists
   (respecting the app's calm-notification philosophy — in-app hint only).
5. **Glance dependency check.** Verify the pinned Glance version supports the
   day/night `ColorProvider` and preview APIs needed above before starting W1;
   bump via the version catalog with Renovate-consistent pinning if not.

## Suggested Sequencing

W1 -> W2 -> W6(preview+a11y) -> W3 -> W4 -> W5, with secondary items slotted
in as PR-sized fillers. W1 is the foundation (every later screenshot depends
on correct theming); W3 is the largest slice and should land behind the
zero-config default so it cannot destabilize existing placements.
