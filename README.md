# Kanji Companion Server

Kanji Companion Server is a single-user self-hosted Python web app for finding weak kanji in an Anki collection and turning that analysis into a browser-based study workflow. It syncs from Anki through AnkiConnect, keeps its own SQLite-backed working copy and study state, and serves the first-party web UI from the same origin as the API.

## Product shape

- Backend: FastAPI app served with uvicorn.
- Source of truth: Anki notes and cards via AnkiConnect, usually at `http://127.0.0.1:8765`.
- Companion state: local SQLite database for synced source rows, derived kanji analysis, and internal kanji SRS history.
- Frontend: first-party hosted web app served by the same process.
- User model: one person, one trusted machine, one active collection workflow.

The default workflow is the server plus its same-origin web app.

## CLI

The server exposes three operational commands:

- `kanji-companion-server run`
  Starts the FastAPI app under uvicorn and serves both `/api/*` and the hosted web UI.
- `kanji-companion-server sync-now`
  Pulls the current note/card snapshot from AnkiConnect, upserts synced rows, tombstones missing rows, and rebuilds derived analysis.
- `kanji-companion-server rebuild-analysis`
  Recomputes dashboard/problem-kanji projections from the local synced database without talking to Anki again.

Typical flow:

1. Keep Anki desktop open with AnkiConnect enabled.
2. Start the server with `kanji-companion-server run`.
3. Open the served web app in a browser.
4. Use the manual sync action in the UI or `kanji-companion-server sync-now` whenever collection state changes.
5. Refresh study seeds to repopulate the internal kanji queue from current problem-child kanji.

Study bridge SRS:

- The companion now runs a temporary kanji bridge deck rather than a second full kanji curriculum.
- New and lapsed items go through a short deterministic packet:
  1. context production with handwriting,
  2. an immediate confusable/font check,
  3. one scheduled bridge review about 10 minutes later.
- After that short packet, day-scale reviews use an FSRS-style difficulty/stability scheduler with a 90% target retention.
- Review tasks rotate through context production, confusable recognition, and sampled handwriting so writing stays in the loop without dominating every mature review.
- The active queue is intentionally capped for low maintenance: at most 25 live items, with at most 3 first-time introductions per day.

Study retirement rule:

- A bridge kanji retires only after it has reached at least a 30-day interval inside the companion SRS.
- Seed refresh then checks the synced Anki mirror and retires the kanji only when:
  - at least 3 mature active Anki cards still contain it,
  - those cards cover at least 2 distinct lexical items,
  - and recent review evidence exists for at least 1 supporting item when that metadata is available.
- Retired items are not forgotten permanently: they can reactivate if the kanji becomes a problem seed again or if the stored mature support collapses soon after retirement.

## Runtime

- API framework: FastAPI
- ASGI server: uvicorn
- HTTP style: versioned JSON API under `/api/*`
- Storage: SQLite
- Data assets: server-managed KANJIDIC2 and KanjiVG cache under the app data directory

Architectural boundary:

- Anki remains the system of record for notes and cards.
- The companion server mirrors the required collection state through AnkiConnect.
- The web app talks only to the companion server, not directly to Anki.

## Install for development

```bash
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install --upgrade pip
python3 -m pip install -r requirements-dev.txt
```

## Run

```bash
kanji-companion-server run
```

By default the server binds to `127.0.0.1:8768`.

## Test workflow

The default `pytest` suite targets the redesign-oriented server, analysis, study, sync, and API tests:

```bash
pytest
```

The default suite covers the active server, study flow, hosted web app, sync, and API surfaces.

## Repository map

- `kanji_leech_dashboard/`: active server package, domain logic, CLI, API, and hosted web app.
- `tests/`: server-first pytest suite for the active product.
- `dist/`: historical build output, not part of the active workflow.
- `android-app/`: Android port scaffold, parity fixtures, Compose shell, and Room schema mirror.

## Android Port Scaffold

The repo now includes an Android migration slice under `android-app/`. It does not replace the Python server yet; it uses a checked-in parity oracle exported from the current Python implementation to bootstrap settings plus static kanji detail content, prefers AnkiDroid's exported content provider for live note/card reads when its runtime permission is granted, falls back to the parity snapshot when AnkiDroid is missing or unavailable, derives dashboard/problem rows locally, records sync runs in Room, exposes manual sync plus latest-sync health in the Compose shell, lets the Android settings form update note-model filtering and polling config on-device, surfaces AnkiDroid install/permission state directly in Settings, supports dashboard-to-detail drill-down for any cached kanji row, and runs study seed refresh, overview generation, session selection, handwriting enforcement, and review progression from Room so the Kotlin app can start acting like a real standalone client while the direct Android port catches up.

Regenerate the Android oracle fixture from the current Python implementation with:

```bash
kanji-companion-server export-android-parity-fixtures --output android-app/parity-fixtures/oracle-v1.json
```

The Android project is intentionally repo-local scaffolding at this stage. The Gradle wrapper is checked in under `android-app/`.

The Android scaffold now also includes a GitHub Releases updater path. It expects a public release feed with an APK asset; if the source repository remains private, configure the Android build to point at a public mirror repo for release delivery.

The Android scaffold's WorkManager layer now respects the cached polling settings for periodic background sync, subject to Android's minimum 15-minute repeat interval, and uses the same live-AnkiDroid-or-fixture-fallback sync boundary as the manual sync path.

Signed Android release builds are now supported through gitignored local/Gradle properties for the keystore path, store password, key alias, and key password, so the repo can produce installable `assembleRelease` APKs without committing signing material.

## Current API surface

- `GET /api/health`
- `GET /api/settings`
- `PUT /api/settings`
- `POST /api/sync/ankiconnect`
- `GET /api/dashboard`
- `GET /api/kanji/{kanji}`
- `GET /api/study/overview`
- `POST /api/study/seeds/refresh`
- `POST /api/study/sessions`
- `POST /api/study/reviews`
- `GET /api/assets/stroke-order/{kanji}.svg`
