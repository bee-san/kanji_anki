# Kani widget screenshot evidence

This directory stores launcher screenshots captured by
`ci/scripts/capture_kani_widget_screenshots.sh`. Every PNG must have sibling
`.json`, `.uiautomator.xml`, and `.appwidget.txt` files with the same capture ID.
The JSON sidecar must validate against `manifest.schema.json`.

The capture script never crops screenshots. Place and resize the real widget in
the launcher first, then pass the exact `appWidgetId` and the four dimension
values shown for that ID by `adb shell dumpsys appwidget`.

Seed the sanitized deterministic fixture on a test emulator only:

```sh
ci/scripts/capture_kani_widget_screenshots.sh --seed-fixture
```

Required baseline capture IDs:

- `overview-due-regular`
- `overview-up-to-date-regular`
- `quick-study-due-compact`
- `quick-study-due-wide`
- `quick-study-up-to-date-wide`
- `activity-history-compact`
- `activity-history-wide`
- `activity-empty-regular`
- `focus-due-compact`
- `focus-due-wide`

Capture every ID in light and dark system UI on API 26 and API 35. Record app
theme, locale, font scale, launcher package/version/grid, exact widget options,
fixture ID, commit, capture command, and operator in the generated sidecar.
Also capture focused accessibility cases at font scales `1.0`, `1.3`, and `2.0`
and retain any invalid candidates as negative evidence outside the baseline set.

Example after placing a Focus widget:

```sh
KANI_WIDGET_LAUNCHER_PACKAGE=com.google.android.apps.nexuslauncher \
KANI_WIDGET_LAUNCHER_GRID=5x5 \
KANI_WIDGET_APP_THEME=system \
KANI_WIDGET_MIN_WIDTH=120 KANI_WIDGET_MIN_HEIGHT=120 \
KANI_WIDGET_MAX_WIDTH=120 KANI_WIDGET_MAX_HEIGHT=120 \
ci/scripts/capture_kani_widget_screenshots.sh \
  focus-due-compact \
  dev.bee.kanjianki/dev.bee.kanjianki.widget.FocusKanjiWidgetReceiver \
  42
```
