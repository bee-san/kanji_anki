# Kani Theme Selector Implementation Plan

> **For Hermes:** Use subagent-driven-development or the Kanban graph below to implement this plan task-by-task. Do not edit Bee's dirty original checkout; use the dedicated worktree named in this plan.

**Goal:** Add a polished Kani theme selector in Settings with Light, Dark, Follow system, and a light pastel pink **Girlypop** theme that becomes the default color scheme, then prove the result is visually appealing with screenshots and a separate UX/design critique gate.

**Architecture:** Introduce a small app-theme domain model (`KaniThemeChoice`, `KaniThemePalette`, `KaniThemeTokens`) and route all Compose + legacy view colors through that token layer instead of hard-coded `MainActivityUiSupport` constants. Persist the user choice in the existing settings store, show a preview-card selector under a new Settings appearance panel, and use screenshot routes to capture key screens in every required theme. Use a design/UX reviewer as a hard gate: if screenshots are not pretty/readable, route a follow-up polish card before QA/merge.

**Tech Stack:** Kotlin, Android, Jetpack Compose Material3, existing `SettingsRepository` / `SettingsStorage`, existing Kani settings categories, Gradle `:app` unit/androidTest tasks, GitHub PR + CI/Sonar flow.

**Created:** 2026-06-13 09:08 BST  
**Board:** `kani`  
**Tenant:** `kani-theme-selector-20260613`  
**Implementation worktree:** `/Users/autumnskerritt/kanji_anki_worktrees/kani-theme-selector-20260613`  
**Implementation branch:** `feat/kani-theme-selector-20260613`  
**Observed base:** `origin/main` at `8206d4cb` (`docs(readme): check off screenshot-driven UX loop (#437)`)  
**Related active work:** original checkout `/Users/autumnskerritt/kanji_anki` is dirty with active Stats-page UX work; do not clobber it. Existing Kani board has active stats speed work and a separate stats screenshot UX chain on board `jcnd-jiten-frequency`; this theme project should use its own worktree and PR.

---

## Product requirements

Required themes / behavior:

1. **Girlypop** — default. Light pastel pink Kani palette; pretty, soft, rounded, cute, but still readable and not cluttered.
2. **Light** — calmer neutral light mode for users who do not want the pastel look.
3. **Dark** — dark mode with accessible contrast, no pure-black glare, charts/buttons still readable.
4. **Follow system** — follows Android system dark/light preference. Use neutral Light/Dark by default unless design decides Girlypop should have its own dark companion.
5. **Autumn** — warm amber/maple optional-but-planned palette if it fits within the implementation budget.
6. **Open-source popular palette research** — verify license and attribution before adding one optional palette such as Catppuccin/Dracula/Nord/Tokyo Night. Do not ship copied palette values without a license note.

Settings UX:

- Add a new **Appearance** or **Theme** section/panel in Settings.
- The selector should show preview swatches/cards, not only raw radio text.
- Current selection must be clear and accessible.
- Changing a theme should persist and apply without requiring app restart.
- Default for existing users with no saved preference is **Girlypop**.
- Settings copy should be concise and Kani-ish, not verbose.

Visual acceptance:

- Screenshots must be captured for Home, Study, Stats/Progress, Settings, and Update/error-ish surfaces where feasible.
- Girlypop must look intentionally pretty: soft pink background, white/pink cards, plum text, coral accent, restrained sparkle/mascot usage only if already present.
- Dark mode must be actually dark across shell, cards, buttons, system bars, nav, charts, and setting panels.
- No invisible text, low contrast, clipped pills, neon overload, or washed-out chart colors.
- UX/design agent must compare screenshots and approve, or create one specific polish/fix iteration before QA.

Safety / coordination:

- Use the dedicated worktree and branch above.
- Do not overwrite the original dirty checkout or active Stats-page UX work.
- Do not touch sync/provider/release/signing files unless a later QA/release gate requires normal workflow inspection.
- No secrets in Kanban, logs, screenshots, or docs.
- Normal Kani performance rule applies: do not introduce route-open recomputation or slow startup/theme switching.

---

## Discovery notes from live repo inspection

- Current UI color layer is mostly hard-coded:
  - `app/src/main/kotlin/dev/bee/kanjianki/MainActivityUiSupport.kt` defines `BG`, `INK`, `MUTED`, `CORAL`, `TEAL`, `BLUE`, `STUDY_*` ints and currently sets light status/nav bars in `styleSystemBars()`.
  - `app/src/main/kotlin/dev/bee/kanjianki/MainActivityUiTokens.kt` wraps some of those constants in Compose `Color`s (`KaniUiTokens.Ink`, `Primary`, `PanelFill`, `PanelBorder`, etc.).
  - Many screens still directly reference `MainActivityUiSupport.STUDY_*`, so implementation needs an incremental compatibility bridge rather than one huge refactor.
- Settings architecture:
  - `SettingsScreenModel` and `SettingsPanelModel` are in `MainActivitySettingsScreenModel.kt`.
  - `SettingsScreen` dispatches panels in `MainActivitySettingsScreenCompose.kt`.
  - Categories are assembled in `MainActivitySettingsScreenSections.kt` and `MainActivitySettingsScreenCoordinator.kt`.
  - Current Settings categories: Anki source, Study behavior, Automation, Reference data.
  - Existing setting persistence is via `SettingsRepository`, `SettingsStorage`, and `LocalStoreStudySettings` helpers.
- Screenshot harness:
  - `MainActivityStartup.renderScreenshotRoute()` supports screenshot routes for home, study, stats, settings, games, update.
  - Add theme-aware screenshot extras/routes instead of relying on tap coordinates.

---

## Proposed implementation slices

### Task 1: Theme architecture and palette spec audit

**Objective:** Produce the exact token map and palette list before implementation so workers do not hard-code random pinks.

**Files:**
- Read: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityUiSupport.kt`
- Read: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityUiTokens.kt`
- Read: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsScreenSections.kt`
- Create/update: `docs/design/kani-theme-palettes.md`

**Steps:**
1. Map all color constants and high-use direct references (`MainActivityUiSupport.STUDY_*`, `KaniUiTokens.*`).
2. Define `KaniThemeChoice` values: `GIRLYPOP`, `LIGHT`, `DARK`, `SYSTEM`, `AUTUMN`, and optional licensed palette(s).
3. Define required token categories: background, surface, surfaceAlt, card, border, text, textMuted, primary, primaryContainer, secondary, success, warning, error, chart palette, nav selected/unselected, system-bar appearance.
4. Verify any open-source palette license before including exact values; record source, license, and attribution.
5. Keep Girlypop custom and default.

**Acceptance:**
- A design doc exists with token names, palette values, contrast notes, and required attribution.
- The doc explicitly says Girlypop is default.
- The doc names which optional open-source palette is safe to ship, or defers it if license verification is inconclusive.

### Task 2: Theme domain model and persistence

**Objective:** Add a small testable model for reading/writing theme choice, defaulting to Girlypop.

**Likely files:**
- Create: `app/src/main/kotlin/dev/bee/kanjianki/theme/KaniThemeChoice.kt`
- Create: `app/src/main/kotlin/dev/bee/kanjianki/theme/KaniThemeRepository.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudySettings.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/SettingsWriteActions.kt`
- Test: `app/src/test/kotlin/dev/bee/kanjianki/theme/KaniThemeRepositoryTest.kt`

**TDD steps:**
1. RED: unknown/missing saved value returns `KaniThemeChoice.GIRLYPOP`.
2. RED: normalized values round-trip for `girlypop`, `light`, `dark`, `system`, `autumn`.
3. RED: invalid strings are ignored and do not crash.
4. GREEN: implement repository on top of `SettingsRepository.getString/putString`.

**Suggested key:** `app_theme_choice`

**Acceptance:**
- Existing users default to Girlypop.
- All choices persist safely.
- No DB migration is required if settings table already stores arbitrary keys.

### Task 3: Theme token layer and system-bar behavior

**Objective:** Centralize theme colors so screens can read active tokens instead of hard-coded constants.

**Likely files:**
- Create: `app/src/main/kotlin/dev/bee/kanjianki/theme/KaniThemeTokens.kt`
- Create: `app/src/main/kotlin/dev/bee/kanjianki/theme/KaniThemePalettes.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityUiSupport.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityUiTokens.kt`
- Test: `app/src/test/kotlin/dev/bee/kanjianki/theme/KaniThemeTokensTest.kt`

**Implementation notes:**
- Provide a compatibility bridge first: keep old constants, but expose `activeThemeTokens()` and use tokens in Compose wrappers.
- System bars must use dark icons for light/Girlypop/autumn and light icons for Dark.
- Avoid recomputing theme state inside tight render loops.

**Acceptance:**
- Unit tests cover token lookup and system-bar icon decisions.
- Theme resolution supports Follow system with injectable `isSystemInDarkTheme`/boolean for tests.
- Dark token contrast is explicitly tested for text-on-surface and primary button text.

### Task 4: Settings Appearance selector model and copy

**Objective:** Add the Settings model data needed for a pretty selector panel.

**Likely files:**
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsScreenModel.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsScreenSections.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsScreenCoordinator.kt`
- Create: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsThemePanel.kt`
- Create/modify copy in `core` settings copy files if available.
- Tests: `app/src/test/kotlin/dev/bee/kanjianki/SettingsThemePanelModelsTest.kt`

**Model shape suggestion:**

```kotlin
internal data class SettingsThemePanelModel(
    val title: String,
    val body: String,
    val choices: List<SettingsThemeChoiceModel>,
) : SettingsPanelModel

internal data class SettingsThemeChoiceModel(
    val key: String,
    val title: String,
    val subtitle: String,
    val selected: Boolean,
    val swatches: List<Int>,
    val contentDescription: String,
    val onSelect: Runnable,
)
```

**Acceptance:**
- Settings model includes an Appearance/Theme panel, preferably in an App Appearance category near Reference/Automation rather than buried in Study behavior.
- Model tests prove Girlypop selected by default and exactly one choice selected.

### Task 5: Settings Appearance selector Compose UI

**Objective:** Render a beautiful, accessible theme selector with preview swatches/cards.

**Likely files:**
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsScreenCompose.kt`
- Create: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsThemeCompose.kt`
- Android tests: `app/src/androidTest/kotlin/dev/bee/kanjianki/SettingsThemeComposeTest.kt`

**UI requirements:**
- 48dp+ touch targets.
- Clear selected state (checkmark or selected border + text).
- Preview swatches show background/card/primary/text or miniature card.
- Content descriptions name choice and selected state.
- Tapping a card persists and re-renders without losing the user.

**Acceptance:**
- Android/Compose tests can find all required choices: Girlypop, Light, Dark, Follow system, Autumn.
- Selecting Dark/Light/Girlypop triggers the write callback.
- UI remains readable at narrow phone width.

### Task 6: Apply themes across shell and core screens

**Objective:** Make the selected theme visibly affect Kani, not only the Settings panel.

**Likely files:**
- Modify: `MainActivityBase.kt`, `MainActivityHome.kt`, `MainActivityStudy.kt`, `MainActivityStats.kt`, `MainActivitySettings.kt`, `MainActivityGames.kt`, `MainActivityUpdate*`, `Home*Compose.kt`, `MainActivityStatsCompose.kt`, `MainActivityStudy*Compose.kt` as needed.
- Prefer surgical replacements through `KaniUiTokens` / `KaniThemeTokens` wrappers.

**Implementation notes:**
- Start with the shell background, nav, panels, cards, buttons, and key chart colors.
- Do not require every one-off color to change in the first patch if it is low-risk, but no flagship route may visibly remain hard-coded light-pink in Dark mode.
- Update `styleSystemBars()` to use active theme tokens.

**Acceptance:**
- Home, Study, Stats/Progress, Settings, Update, and Games where present visibly use the selected theme.
- Dark mode is not a light screen with dark system bars only.
- Girlypop is the default and matches the requested pastel pink aesthetic.

### Task 7: Add deterministic screenshots for each theme

**Objective:** Produce the screenshot evidence Bee requested.

**Likely files:**
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStartup.kt`
- Possibly create: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityScreenshotThemes.kt`
- Add script: `ci/scripts/capture_kani_theme_screenshots.sh` or `scripts/kani_theme_screenshots.sh`
- Add manifest schema/docs under `docs/design/` or `scripts/`.

**Screenshot matrix:**
- Theme: Girlypop, Light, Dark, System-light, System-dark if feasible, Autumn.
- Routes: Home, Study, Stats/Progress, Settings.
- Optional routes: Update, Games.
- Widths: representative phone/narrow width first; tablet/wide if existing screenshot route supports it.

**Acceptance:**
- Script emits PNGs and `manifest.json` with commit SHA, theme, route, width/device, command, and file list.
- If local emulator is unavailable, use existing screenshot model tests or GitHub Actions artifact flow; do not claim visual pass from compile-only evidence.

### Task 8: UX/design critique gate on screenshots

**Objective:** Have a separate UX/design agent judge screenshots and produce actionable feedback before QA.

**Reviewer instructions:**
- Review screenshots for hierarchy, prettiness, consistency, contrast, button affordance, chart readability, nav clarity, Japanese text readability, and product fit.
- Return JSON with fields: `approved`, `score_0_10`, `highest_impact_issue`, `must_fix`, `nice_to_have`, `theme_notes`.
- Approval threshold: score >= 8 and no `must_fix` issues.

**Acceptance:**
- Design/UX handoff includes screenshot paths and machine-readable verdict.
- If not approved, create/route a polish card rather than sending to QA as done.

### Task 9: Visual polish iteration from UX feedback

**Objective:** Fix the highest-impact visual issue discovered by the UX critic.

**Rules:**
- One focused polish patch, not a broad redesign.
- Preserve tested theme selector behavior.
- Re-run screenshot capture after changes.
- Re-run design comparison if needed.

**Acceptance:**
- Before/after screenshots prove the issue improved.
- UX/design reviewer approves or explicitly says remaining issues are minor.

### Task 10: Regression tests, accessibility, and contrast checks

**Objective:** Lock the selector and theme rendering against regressions.

**Required checks:**
- Unit tests for theme persistence/defaults.
- Compose tests for selector choices and write callback.
- Screenshot/model tests for themes on core routes.
- Contrast/unit tests for foreground/background pairs.
- `git diff --check`.

**Suggested commands:**

```bash
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew :app:testDebugUnitTest --no-daemon
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --no-daemon
git diff --check
```

### Task 11: QA, PR, CI/Sonar, and merge

**Objective:** Review and merge through normal Kani flow.

**Acceptance:**
- PR opened from `feat/kani-theme-selector-20260613`.
- PR body links plan, screenshots, UX critique, tests.
- GitHub checks and Sonar are green or non-applicable with evidence.
- Squash merge to `main` only after QA approves.
- Remote `origin/main` contains the merge commit.

### Task 12: Release/update verification and final handoff

**Objective:** Ensure Bee can actually get the theme selector via the normal Kani update/download path.

**Acceptance:**
- Release/update workflow is triggered or verified only through normal Kani release policy.
- If credentials/signing/workflow state block release, block with exact action required.
- Final handoff includes PR URL, merge SHA, released/downloadable version if available, screenshot artifact paths, default theme confirmation, and any remaining follow-up.

---

## Kanban task graph

This section is patched after cards are created.

Board: `kani`  
Tenant: `kani-theme-selector-20260613`  
Worktree: `/Users/autumnskerritt/kanji_anki_worktrees/kani-theme-selector-20260613`  
Branch: `feat/kani-theme-selector-20260613`

<!-- KANBAN_GRAPH_START -->
Pending card creation.
<!-- KANBAN_GRAPH_END -->
