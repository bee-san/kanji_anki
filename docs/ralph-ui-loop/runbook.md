# Ralph remote screenshot runbook

Use this when a UI change needs remote Android screenshots plus the Ralph design/button review loop.
GitHub Actions is the default renderer. Do not rely on the local emulator on `character-dictionary`
unless you are explicitly debugging the capture script; use GitHub Actions for the real evidence.

If you are comparing against the older plan at
`.hermes/plans/2026-05-27_093413-kani-ralph-ui-file-by-file-loop.md`, treat this document as the
final command sequence.

## Source files

- `.github/workflows/android-screenshots.yml`
- `ci/scripts/capture_android_screenshots.sh`
- `scripts/ralph_loop/github_screenshots.py`
- `scripts/ralph_loop/orchestrator.py`
- `scripts/ralph_loop/validation.py`

## What the screenshot workflow produces

The GitHub workflow uploads one artifact named `android-screenshots`. The helper and validator expect:

- `manifest.json`
- one or more `*.png` screenshots
- the requested route recorded in the manifest

Expected run-dir outputs after the full Ralph loop:

- `.ralph-loop/current/remote-screenshots/manifest.json`
- `.ralph-loop/current/remote-screenshots/*.png`
- `.ralph-loop/current/remote-visual-context.json`
- `.ralph-loop/current/ui-manifest.json`
- `.ralph-loop/current/button-contract.json`
- `.ralph-loop/current/button-contract.md`
- `.ralph-loop/current/audit-report.json`
- `.ralph-loop/current/audit-report.md`
- `.ralph-loop/current/validation.json`

## Preferred command sequence

1. Dispatch and validate screenshots in one shot:

   ```sh
   GH_CONFIG_DIR=/Users/autumnskerritt/.config/gh \
     python3 scripts/ralph_loop/github_screenshots.py \
       --repo-root . \
       --workflow android-screenshots.yml \
       --artifact android-screenshots \
       --screenshot-route all \
       --out .ralph-loop/current/remote-screenshots \
       --push-pr-branch
   ```

   This helper wraps the raw GitHub steps below.

2. Run the Ralph controller once the screenshots are present:

   ```sh
   python3 scripts/ralph_loop/orchestrator.py \
     --repo-root . \
     --run-dir .ralph-loop/current \
     --push-pr-branch \
     --require-remote-screenshots \
     --remote-screenshot-workflow android-screenshots.yml \
     --screenshot-artifact android-screenshots \
     --screenshot-route all
   ```

   The controller writes `remote-visual-context.json` and the review artifacts under
   `.ralph-loop/current/remote-visual/`.

3. Validate the bundle and gate state:

   ```sh
   python3 scripts/ralph_loop/validation.py \
     --repo-root . \
     --run-dir .ralph-loop/current \
     --state-json .ralph-loop/current/remote-visual-context.json \
     --require-remote-green \
     --out .ralph-loop/current/validation.json
   ```

## Raw GitHub fallback

If you need the individual GitHub steps instead of the helper script, use this sequence from the repo
root on a non-protected branch:

```sh
GH_CONFIG_DIR=/Users/autumnskerritt/.config/gh gh workflow run android-screenshots.yml \
  --repo bee-san/kanji_anki \
  --ref <branch> \
  -f screenshot_route=all

GH_CONFIG_DIR=/Users/autumnskerritt/.config/gh gh run list \
  --repo bee-san/kanji_anki \
  --workflow 'Android Screenshots' \
  --branch <branch> \
  --json databaseId,headSha,status,conclusion

GH_CONFIG_DIR=/Users/autumnskerritt/.config/gh gh run watch <run_id> \
  --repo bee-san/kanji_anki \
  --exit-status

GH_CONFIG_DIR=/Users/autumnskerritt/.config/gh gh run download <run_id> \
  --repo bee-san/kanji_anki \
  --name android-screenshots \
  --dir .ralph-loop/current/remote-screenshots
```

## Local emulator fallback

Use the local emulator only when you are debugging the capture script or reproducing a workflow failure. It is not the source of truth for the final evidence bundle.

Local capture requires:

- `adb` on PATH.
- Android build-tools with `aapt` available.
- A booted emulator or attached device visible in `adb devices`.
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` pointing at the SDK that contains that device tooling.
- A built debug APK under `app/build/outputs/apk`.
- A Google APIs AVD, not the bundled ATD image. Local validation here only produced usable PNGs with `system-images;android-35;google_apis;arm64-v8a` (for example the `kanji_anki_api35_google_apis_local` AVD).

If the local run still produces black PNGs, ANR/dialog text, or the wrong route, treat that as a debugging signal and fall back to GitHub Actions for the final artifact.

## Failure states

- `remote_visual_pending`: GitHub auth is missing, the branch could not be pushed or dispatched, or the
  run has not been found yet. Retry later; this is not a terminal failure.
- `remote_visual_failed` (surfaced by the helper as `failed`): the screenshot workflow ran but the emulator/capture step failed or the run was cancelled. Inspect the Actions logs first.
- `missing_artifact`: the run completed but `manifest.json`, the PNGs, or the requested route are missing
  or mismatched. Fix the capture and rerun the workflow.
- Blank screenshot: the PNG exists but is black, empty, or stuck on the wrong route. Re-run with the same
  route and inspect the step that waits for the route text.
- `CI/Sonar pending`: validation reports `pending` or `needs_host` on `ci_fast_gate`, `ci_quality_gate`,
  or `remote_ci_sonar_gate`. That means evidence has not landed yet; do not treat it as a regression.
- Reviewer unavailable: `independent_review_gate` reports `needs_host` when the reviewer command is missing
  or the reviewer model does not match `gpt5.4-codex-mini`.

## Real-world notes

- The Linux GitHub runner often boots the emulator slowly without KVM. That is normal; do not abort the
  run just because boot takes several minutes.
- If the job sits on `Launch app and capture screenshots` for a long time, the capture script is usually
  waiting for the requested route or an Android dialog/ANR. Treat that as a workflow problem if it never
  advances to `Upload screenshots`.
- Capture immediately once the route marker appears; we removed the fixed post-route settle sleep because
  it gave System UI time to raise an ANR/dialog over an otherwise valid frame.
- Screenshot launches skip the app's background startup side effects when `EXTRA_SCREENSHOT_ROUTE` is
  present. Keep that guard in place so route captures don't wait on reminder/sync/update/backup setup.
- Home screenshot rendering should short-circuit before any synchronous store precompute or provider
  checks; use a lightweight screenshot-only model if the route starts ANRing.
- If the emulator fails with `Timeout waiting for emulator to boot`, raise the workflow's
  `emulator-boot-timeout` before retrying.
- On GitHub-hosted Ubuntu in this investigation, `system-images;android-35;google_apis;x86` was not
  available; the workflow needed the `google_apis` `x86_64` image instead.
- One failure mode we saw was the emulator runner shell logging `/usr/bin/sh: 1: \\: not found`. When
  that happens, treat the workflow step as malformed and fix the workflow command before retrying.
- The artifact validator is strict about routes. `all` must include `home`, `study`, `stats`, `settings`,
  `games`, `narrow`, and `wide`.

## Related outputs

- `.ralph-loop/current/remote-screenshots/manifest.json`
- `.ralph-loop/current/remote-screenshots/*.png`
- `.ralph-loop/current/remote-visual-context.json`
- `.ralph-loop/current/audit-report.md`
- `.ralph-loop/current/validation.json`
