This repository now centers on a single-user self-hosted Python web app that syncs from Anki through AnkiConnect and serves a browser-based kanji companion workflow.

## Project Purpose

The active product is the companion server and same-origin web app. The server owns sync, cached snapshots, kanji analysis, assets, and study state. The browser UI consumes that server API.

The study workflow now includes a progressive handwriting check: the server decides when writing is required and validates review submissions, while the browser runs the guided canvas/scoring experience and manual override flow.

## Main Components

- `kanji_leech_dashboard`: active server/domain code, CLI, API, and hosted web app assets.
- `tests`: redesign-oriented pytest suite. The default run targets the server, browser UI, analysis, sync, study, and API subset.
- `dist`: historical build output, not a primary edit surface.

## Working Areas

- Prefer edits in `kanji_leech_dashboard/`, `tests/`, repo docs, and packaging files for server work.
- Keep `dist/` treated as build output, not as an active product surface.
- Check `README.md` first if you need the current runtime model, CLI contract, or API surface.
- `android-app/` is a valid active surface only when the task is explicitly about the Android port, release flow, or mobile UI/runtime behavior.

## Agent Expectations

- Preserve the boundary: Anki data comes in through AnkiConnect; the Python server owns sync, caching, derived analysis, and API responses; the hosted web app owns presentation.
- Keep the default narrative focused on the FastAPI/uvicorn server plus the first-party hosted web app.
- Preserve the handwriting contract: the server emits `requiresWriting` and `handwritingPolicy`, enforces rating limits after failed writing checks, and advances guide levels; the browser owns stroke capture, guided scoring, progressive hints, and manual pass/retry overrides.
- When behavior changes, update the relevant redesign docs and tests together.
- Treat handwriting changes as cross-surface work: keep `study.py`, the API/service layer, and browser tests aligned so the same writing rules are enforced in both client and server paths.
- Do not reintroduce sorter-specific or add-on packaging steps into the workflow.
- Avoid changing packaged artifacts or unrelated generated files unless the task specifically requires it.

## Android Notes

- The Android runtime should stay on the live `AnkiDroid + Room` path. The parity oracle is for migration/parity work and contract comparison, not as a production runtime fallback.
- Verify Android changes with `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --no-daemon` before calling the mobile build ready.
- The tag-driven Android release flow expects `vMAJOR.MINOR.PATCH`. The GitHub Actions `sdkmanager --licenses` step must not run under `pipefail`, or the expected `yes | sdkmanager --licenses` broken pipe will fail the release.
- Treat large-phone validation as required for Android UI work. A layout that looks acceptable in code or on a smaller profile can still look bad on an S24 Ultra class device, so do a real emulator/device pass before calling the UI finished.

## Common Commands

```bash
pytest
kanji-companion-server run
kanji-companion-server sync-now
kanji-companion-server rebuild-analysis
```
