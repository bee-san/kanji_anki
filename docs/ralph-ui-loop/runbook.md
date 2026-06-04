# Ralph UI loop runbook

Use this when a Kani UI change needs the Ralph file-by-file audit/apply loop, remote Android screenshots, validation, and PR handoff. This document folds in the completed Kanban runbook branch `kanban/t_a859a823-ralph-ui-runbook` plus the later remote-screenshot runbook that already landed on `main`.

## File-by-file audit and apply loop

This is the durable operating note for the file-by-file Ralph loop in Kani.
It is the handoff future agents should read before auditing, reviewing, or
implementing a UI slice.

## What this loop is for

Ralph is the small-slice UI workflow around the current Kani UI manifest,
button-contract matrix, screenshot capture, and validation gates.
It is meant to keep each iteration PR-sized and reviewable.

Current code state:

- `scripts/ralph_loop/orchestrator.py` is a controller, not a full autonomous
  editor. It dispatches screenshot capture and profile reviews, and it validates
  the `--iterations <= --max-iterations` cap, but it does not currently run a
  full multi-iteration apply/commit engine by itself.
- `scripts/ralph_loop/ui_manifest.py`, `button_contract.py`, and
  `validation.py` are the durable gate builders.
- Prompts live under `scripts/prompts/` and are loaded through
  `scripts/ralph_loop/prompts.py`.
- Remote screenshots use `.github/workflows/android-screenshots.yml` and
  `ci/scripts/capture_android_screenshots.sh`.

If you need the broader CI/Sonar/release map, read
`docs/ci-sonar-reliability-runbook.md` as well.

## Important artifact paths

These are the generated outputs the loop expects to read and reuse:

| Artifact | Producer | Purpose |
| --- | --- | --- |
| `.ralph-loop/current/ui-manifest.json` | `scripts/ralph_loop/ui_manifest.py` | Deterministic UI inventory, buckets, risk tags, nearest tests |
| `.ralph-loop/current/button-contract.json` | `scripts/ralph_loop/button_contract.py` | Button-contract matrix with direct selector/click evidence |
| `.ralph-loop/current/button-contract.md` | `scripts/ralph_loop/button_contract.py` | Human-readable matrix report |
| `.ralph-loop/current/remote-visual-context.json` | `scripts/ralph_loop/github_screenshots.py` or `orchestrator.py` | Remote screenshot result and metadata |
| `.ralph-loop/current/remote-screenshots/manifest.json` | Android Screenshots workflow | Workflow artifact manifest |
| `.ralph-loop/current/validation.json` | `scripts/ralph_loop/validation.py` | Gate summary for the current slice |

The schemas are stable and should stay parseable:

- `ui-manifest-v1`
- `button-contract-v1`
- `ralph-validation-v1`

## The safe operating sequence

1. Start from a non-protected PR branch.
2. Run an audit pass to build or refresh the manifest, contract, and remote
   screenshot evidence.
3. Inspect the design critic and button-contract reviewer output.
4. If exactly one accepted issue remains, implement exactly that one issue in the
   UI slice and the directly related test(s).
5. Rebuild the manifest/contract, rerun validation, and only then push/update the
   PR.
6. Watch GitHub Actions and Sonar evidence before merging.

Do not treat this loop as a place to do broad refactors. One issue, one slice,
one PR-sized change.

## Apply / commit / push in practice

- Apply: this is the coding pass, not the audit pass. Use
  `scripts/prompts/ralph_ui_implementer.md` and keep it to one accepted issue.
- Commit: keep the change PR-sized, then rerun the manifest/contract/validation
  trio after the commit.
- Push: use `--push-pr-branch` only when the branch must exist on GitHub before
  remote screenshot dispatch. The validation gate keeps the branch at or below
  one unpushed commit.

## CLI entry points

Use these from the repo root.

### 1) Build the UI manifest

```sh
python3 -m scripts.ralph_loop.ui_manifest \
  --repo-root . \
  --out .ralph-loop/current/ui-manifest.json
```

### 2) Build the button-contract matrix

```sh
python3 -m scripts.ralph_loop.button_contract \
  --repo-root . \
  --manifest .ralph-loop/current/ui-manifest.json \
  --out-json .ralph-loop/current/button-contract.json \
  --out-md .ralph-loop/current/button-contract.md
```

### 3) Capture remote screenshots

```sh
python3 -m scripts.ralph_loop.github_screenshots \
  --repo-root . \
  --workflow android-screenshots.yml \
  --artifact android-screenshots \
  --screenshot-route all \
  --out .ralph-loop/current/remote-screenshots
```

Add `--push-pr-branch` only when you explicitly want the current branch pushed
before dispatching the screenshot workflow.

Safety notes for screenshots:

- The helper refuses protected branches such as `main`, `master`, `develop`,
  `release`, and `production`.
- It also refuses to self-loop from inside GitHub Actions.
- The workflow supports `all`, `home`, `study`, `stats`, `settings`, `games`,
  `narrow`, `wide`, and `update` routes. `all` captures the standard portrait
  routes plus narrow/wide home screenshots.

### 4) Run the orchestrator in audit-only mode

```sh
python3 -m scripts.ralph_loop.orchestrator \
  --repo-root . \
  --audit-only \
  --remote-screenshot-workflow android-screenshots.yml \
  --screenshot-artifact android-screenshots \
  --screenshot-route all \
  --require-remote-screenshots \
  --max-iterations 1
```

What the current orchestrator actually wires:

- `--audit-only` keeps the checkout untouched.
- `--remote-screenshot-workflow`, `--screenshot-artifact`, and
  `--screenshot-route` control the remote screenshot helper.
- `--push-pr-branch` is passed through to the screenshot helper.
- `--critic-cmd` defaults to `hermes -p design chat -Q -t safe -q {prompt}`.
- `--button-cmd` defaults to `hermes -p uitester chat -Q -t safe -q {prompt}`.
- `--reviewer-model` defaults to `gpt5.4-codex-mini`.

Current caveat: `--agent-cmd`, `--reviewer-cmd`, and `--pr-branch` are parser
knobs but are not consumed by the current `run()` implementation. Treat them as
reserved for a higher-level wrapper until the controller is extended.

### 5) Validate the slice

```sh
python3 -m scripts.ralph_loop.validation \
  --repo-root . \
  --run-dir .ralph-loop/current \
  --out .ralph-loop/current/validation.json
```

Add `--require-remote-green` when the slice needs remote CI/Sonar evidence.
Use `--state-json` when replaying a captured loop state or overlaying workflow
results.

## Profile roles

The prompt files define the role contracts.

| Role | Prompt file | Job |
| --- | --- | --- |
| `design` | `scripts/prompts/ralph_design_critic.md` and `scripts/prompts/ralph_design_file_auditor.md` | Compare rendered screenshots or a single UI file against the manifest and identify the highest-value UI issue |
| `uitester` | `scripts/prompts/ralph_button_contract_reviewer.md` | Check the button-contract matrix against selectors, click tests, disabled/loading coverage, and accessibility gaps |
| `coding` | `scripts/prompts/ralph_ui_implementer.md` | Implement exactly one accepted issue, only in UI files and directly related UI/test files |
| `reviewer` | validation gate, default model `gpt5.4-codex-mini` | Independent review before merge; validation rejects the wrong model |

Coding pass rules from the prompt:

- Implement exactly one accepted issue.
- If behavior changes, write/update the focused failing test first and make it
  pass.
- Do not modify sync, provider, storage, release, signing, CI workflows, or
  broad architecture.
- Keep the patch small, reviewable, and reversible.

## Branch / PR workflow

The loop is branch-first, not main-first.

1. Make a PR-sized branch for one accepted slice.
2. Keep the branch close to the base branch; the validation gate fails if the
   branch is more than one commit ahead of upstream.
3. Push the branch and open/update the PR.
4. Watch the required GitHub checks:
   - `Fast confidence gate` (`./gradlew ciFast`)
   - `Build coverage and analyze` (`./gradlew ciQuality`)
   - `Analyze Java/Kotlin` (CodeQL)
   - the Android Screenshots workflow when the slice needs screenshot evidence
5. Merge only after the checks are green and the independent review gate passes.

Useful push rule:

- Use `--push-pr-branch` only when you need the branch to exist on GitHub before
  remote screenshot dispatch.
- Do not rely on protected branches; the screenshot helper refuses them.

## Validation gates to remember

`scripts/ralph_loop/validation.py` enforces the important guardrails:

- branch guard: do not work on protected branches or inconsistent branch state
- dirty work guard: avoid unrelated dirty paths
- forbidden file guard: do not touch workflow/build/Gradle/CI surfaces in a UI slice
- diff size guard: keep the slice small
- button-contract delta guard: interactive UI files need manifest + contract +
  direct selector/click coverage
- targeted Compose tests: touched interactive files need focused Compose test evidence
- `ciFast` gate: required for Kotlin/Java/Android compile surfaces
- `ciQuality` gate: required when Sonar inputs matter
- screenshot availability gate: required for interactive UI changes
- design comparison gate: screenshot-backed design review must pass
- button QA review gate: the contract reviewer must pass
- commit push frequency gate: stay at or below one unpushed commit
- remote CI/Sonar gate: only required when `--require-remote-green` is set
- independent review gate: must use `gpt5.4-codex-mini`

If a gate returns `needs_host`, do not fake it. Either gather the missing
artifact or stop and surface the blocker.

## Button-contract matrix expectations

The current contract seeds these rows:

- `home-study-cta`
- `home-sync-cta`
- `home-action-grid`
- `home-section-header`
- `home-sync-metric`
- `focus-queue-card`
- `study-pass-fail`
- `settings-save-toggle-reorder`

What matters:

- Every interactive row needs direct selector/click evidence, not keyword-only
  mentions.
- Settings-like controls should also carry enabled/disabled or stateful-input
  coverage when the control has state.
- The matrix is intentionally biased toward small direct tests, not broad UI
  screenshots alone.
- Keep the dedicated save-control mapping honest; do not let a weak settings
  test stand in for real coverage.

## Do not touch in a Ralph slice

These are out of bounds for the file loop unless the task explicitly changes the
scope:

- `.github/workflows/**`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle/**`
- `ci/**`
- sync/provider/storage/release/signing code paths
- broad architecture rewrites

If the accepted issue requires one of those areas, stop and re-triage the task.

## KVM / device caveat

Do not make the Ralph file loop depend on a live emulator/device path.
That belongs in the provider/sync gate, not the UI file loop.

If you need real AnkiDroid confidence, use the dedicated local provider docs in
`docs/local-ankidroid-provider-testing.md` or the GitHub Actions fixture.
When the host does not have a usable renderable device path, keep the Ralph loop
on deterministic gates, screenshots, and PR checks rather than burning time on
local device setup.

## Recommended next backlog

The cheap-Ralph queue in `README.md` is the source of truth for the next slice.
The first unchecked item is the Settings overhaul:

- add/remove/re-categorise Settings entries into simple groups
- keep category navigation in place without resetting scroll position
- preserve warnings, accessibility, test tags, and semantics

After that, the queue continues with copy audit, stats work, Japanese
translation, a 24-hour emulator soak, and writing-parity work.

## Provenance

This runbook is based on the Ralph-related PR family and parent handoffs that
introduced and validated the loop:

- reusable prompt files and prompt loading (`#42`)
- manifest / button-contract / screenshot workflow plumbing (`#43`)
- validation gates and tests (`#153`)

If you update the loop, update this note at the same time so future agents do
not have to rediscover the contract from scratch.

## Remote screenshot evidence runbook

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

If the local run produces black PNGs, ANR/dialog text, or the wrong route, treat that as a debugging signal and fall back to GitHub Actions for the final artifact.

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
- If the emulator fails with `Timeout waiting for emulator to boot`, raise the workflow's
  `emulator-boot-timeout` before retrying.
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
