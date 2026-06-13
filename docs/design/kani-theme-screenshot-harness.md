# Kani theme screenshot harness

The theme screenshot harness lives in `ci/scripts/capture_kani_theme_screenshots.sh` and uses the route launcher in `ci/scripts/capture_android_screenshots.sh`.

Default capture matrix:
- routes: `all` (home, study, stats, settings, games, narrow, wide, update)
- themes: `girlypop`, `light`, `dark`, `system-light`, `system-dark`, `autumn`

Environment overrides:
- `SCREENSHOTS_DIR` — output root, default `screenshots/kani-theme-matrix`
- `SCREENSHOT_ROUTE` — route passed to the base launcher, default `all`
- `SCREENSHOT_THEME_MATRIX` — comma-separated theme labels

Theme label handling:
- `girlypop`, `light`, `dark`, and `autumn` are passed directly to the app via `EXTRA_SCREENSHOT_THEME`
- `system-light` and `system-dark` map to the app's `SYSTEM` theme choice and toggle the emulator's night mode before capture

Manifest shape:
- root `manifest.json` is written under `SCREENSHOTS_DIR`
- per-theme subdirectories contain the raw manifests emitted by `capture_android_screenshots.sh`
- top-level fields include:
  - `capture_status`: `complete` or `visual_pending`
  - `requested_route`
  - `requested_theme_matrix`
  - `theme_runs[]`
  - `files[]`
  - `manifest_files[]`
- each theme run records the nested route manifest fields, including `device`, `command_argv`, `routes`, `files`, and `captures`

If no adb device is available, the wrapper writes a root manifest with `capture_status: visual_pending` so the output cannot be mistaken for visual proof.
