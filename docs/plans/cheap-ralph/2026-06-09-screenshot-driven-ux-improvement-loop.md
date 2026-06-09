# Screenshot-Driven Cheap Ralph UX Improvement Loop Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Add a safe, deterministic Cheap Ralph loop that captures each Kani Android view from stable fixtures, asks a design critic for one highest-impact machine-readable UX improvement target, implements exactly that accepted issue in an isolated scratch checkout, captures real before/after screenshots, and only applies or commits the patch when explicit acceptance flags and all gates pass.

**Architecture:** Extend the existing Ralph UI loop instead of creating a separate system: deterministic screenshot harness + UI view/button matrix + JSON-only critic/implementer prompts + scratch-worktree implementer + acceptance/guard validator + orchestration CLI + runbook/smoke-test coverage. The default behavior remains review-only; source checkout mutation and commits are opt-in.

**Tech Stack:** Python 3 `scripts/ralph_loop/*` orchestration and tests, Git/GitHub CLI, GitHub Actions Android emulator artifacts, existing Gradle/Compose test stack, existing `ci/scripts/capture_android_screenshots.sh`, existing Hermes/ChatGPT profile commands via JSON-only prompts, Markdown runbooks/docs.

## Repository context inspected

- `docs/ralph-ui-loop/runbook.md` already defines the current remote screenshot workflow and says GitHub Actions is the preferred renderer; local emulator capture is only a debugging fallback.
- `.github/workflows/android-screenshots.yml` builds `:app:assembleDebug`, launches a Pixel 6 API 35 emulator, runs `ci/scripts/capture_android_screenshots.sh`, and uploads the `android-screenshots` artifact.
- `ci/scripts/capture_android_screenshots.sh` already supports screenshot routes `all`, `home`, `study`, `stats`, `settings`, `games`, `narrow`, `wide`, and `update`; `all` currently captures `home`, `study`, `stats`, `settings`, `games`, `narrow`, and `wide`. It writes a `manifest.json` with git SHA/ref, workflow run metadata, package/device metadata, requested route, routes, and files.
- `scripts/ralph_loop/github_screenshots.py` already validates remote artifact shape, exact `all` route coverage/order, protected branches, expected GitHub repo, and explicit branch push behavior.
- `scripts/ralph_loop/orchestrator.py` already has `--audit-only`, `--iterations`, `--max-iterations`, `--file-bucket`, manifest/button-contract generation, remote visual review mode, design/button reviewer command hooks, and generated `.ralph-loop/current/*` artifacts.
- `scripts/ralph_loop/validation.py` already has branch, dirty-work, forbidden-path, diff-size, button-contract, tests, screenshot, design comparison, remote CI/Sonar, and independent-review gates. It currently treats screenshot/design validation as gates, but does not yet enforce before/after comparison with a score delta.
- `scripts/prompts/ralph_design_critic.md` and `scripts/prompts/ralph_ui_implementer.md` exist, but need to be tightened for target-view specs, one-issue iterations, scratch-checkout implementation, and before/after comparison.
- `README.md` has the current unchecked Cheap Ralph broad UX queue item: “Go through every user-facing view, dialog, empty/error state, onboarding/import path, study flow, Settings surface, and stats/history screen; analyse the whole UX/UI…”. This loop should support that future UX work with repeatable evidence. It must not replace or weaken learning/scheduler correctness; Kani remains a learning app first.

## Non-negotiable safety principles

1. **Deterministic visual evidence:** screenshots must come from stable fixture routes, fixed device/emulator settings, disabled animations, fixed orientation/viewport, and seeded app state. No production user data, network-dependent state, random content, or current-time content may determine acceptance.
2. **Manifest everything:** every run writes a manifest containing run ID, UTC timestamp, branch, commit SHA, dirty state, command argv, environment/tool versions where useful, screenshot routes, fixture IDs and hashes, artifact run IDs, file list with hashes, prompt/result paths, patch paths, tests run, validation gates, and final decision.
3. **One issue per iteration:** the critic returns machine-readable JSON and at most one highest-impact accepted issue for the selected view/route. If the critic cannot identify one evidence-backed issue, the iteration is review-only/no-op.
4. **Scratch implementation only:** implementer edits happen in a disposable scratch checkout/worktree. The main checkout is not mutated unless `--apply-accepted` is set, and no commit is made unless `--commit-accepted` is also set.
5. **Visual validation is mandatory:** compile/test success is necessary but never sufficient. Acceptance requires real before and after screenshot artifacts plus critic comparison that says the after image is better by a configured score delta.
6. **Small, reversible patches:** enforce forbidden paths, changed-file limits, diff-line limits, and one-surface scope. UX polish must not touch sync/provider/storage/release/signing/build-system/CI paths unless a separate explicit task authorizes that.
7. **Remote screenshots are the default evidence when local emulator is unavailable:** prefer GitHub Actions emulator artifacts over pretending visual validation happened. If neither remote nor local screenshots are available, the iteration remains pending/blocked.
8. **Default review-only:** `--audit-only` / no mutation is the safe default. `--dry-run` may let the scratch checkout create a candidate patch but must leave the main checkout clean. `--apply-accepted` and `--commit-accepted` are explicit opt-ins.

## Target end-to-end loop

For each iteration:

1. Preflight repo state and create a run directory, e.g. `.ralph-loop/runs/<timestamp>-<short-sha>/iteration-001/` with `current` symlink/copy behavior preserved if desired.
2. Build or load the UI view/button matrix.
3. Capture **before** screenshots for the selected route(s) from stable fixtures.
4. Call the design critic with the screenshot(s), fixture metadata, UI manifest, and Cheap Ralph UX queue context.
5. Require JSON output with one selected issue and a target-view spec; optionally include a generated target screenshot/artifact URI when the critic tooling supports it. Do not fabricate target screenshots when unavailable.
6. Create a scratch checkout from the exact source SHA.
7. Call the implementer with exactly one accepted issue and the target spec. The implementer edits only the scratch checkout and returns JSON plus a patch path.
8. Run targeted tests, compile/smoke tests, and guard checks in the scratch checkout.
9. Capture **after** screenshots from the scratch checkout using the same route, fixture, device profile, and capture path as before.
10. Call the design critic comparison prompt with before screenshots, after screenshots, and the target spec/screenshot. Require `after_better=true` and `score_delta >= threshold`.
11. Validate forbidden paths, diff limits, tests, screenshots, design comparison, button matrix, and optional remote CI/Sonar.
12. If validation passes:
    - `--audit-only`: record accepted candidate but do not implement.
    - `--dry-run`: keep patch/artifacts in `.ralph-loop`, leave main checkout clean.
    - `--apply-accepted`: apply the accepted patch to the main checkout, do not commit.
    - `--commit-accepted`: after applying, commit with a message that references the run manifest and accepted issue.

## Artifact layout

Use a per-run immutable layout and keep existing `.ralph-loop/current/*` compatibility where useful:

```text
.ralph-loop/
  runs/<run-id>/
    manifest.json
    iteration-001/
      before-screenshots/
        manifest.json
        *.png
      critic/
        request.prompt.txt
        result.json
        target-spec.json
        target-screenshot.png        # optional, only if really generated
      scratch/
        path.txt                     # scratch checkout path, not source files
      implementer/
        request.prompt.txt
        result.json
        candidate.patch
      after-screenshots/
        manifest.json
        *.png
      comparison/
        request.prompt.txt
        result.json
      validation.json
      summary.md
  current/                           # compatibility pointer/copy for existing runbook commands
```

Minimum `manifest.json` fields:

```json
{
  "schema": "cheap-ralph-screenshot-loop-manifest-v1",
  "run_id": "2026-06-09T000000Z-abcd1234",
  "started_at_utc": "ISO-8601 timestamp",
  "mode": "audit-only|dry-run|apply-accepted|commit-accepted",
  "repo": {"root": "...", "branch": "...", "head_sha": "...", "dirty_paths": []},
  "commands": [{"label": "...", "argv": ["..."], "cwd": "...", "status": "passed|failed|pending"}],
  "fixtures": [{"id": "home-default", "route": "home", "sha256": "..."}],
  "files": [{"path": "app/src/...", "sha256_before": "...", "sha256_after": "..."}],
  "screenshots": {"before": [], "after": []},
  "prompts": {"critic": "...", "implementer": "...", "comparison": "..."},
  "patch": {"path": "...", "changed_files": [], "diff_lines": 0},
  "validation": {"path": "...", "status": "pending"},
  "decision": "reviewed|blocked|accepted|applied|committed"
}
```

## Implementation tasks

### Task 1 — Preserve current behavior with baseline tests

**Purpose:** establish a green baseline before modifying the loop.

**Work:**

- Run the existing Python Ralph tests and a narrow Gradle smoke command before changes.
- Record any pre-existing failures in the task notes; do not hide them by weakening tests.

**Suggested commands:**

```sh
python3 -m unittest discover -s scripts/tests
./gradlew :core:test :app:assembleDebug
```

**Acceptance:** existing tests either pass or pre-existing failures are documented with exact output and not made worse.

### Task 2 — Extend deterministic screenshot fixtures and capture metadata

**Purpose:** make every view screenshot reproducible and tied to stable app state.

**Work:**

- Extend the existing screenshot route support around `MainActivityBase.EXTRA_SCREENSHOT_ROUTE`, `MainActivityStartup`, `MainActivityShellModel`, and `ci/scripts/capture_android_screenshots.sh` instead of adding a second capture mechanism.
- Introduce a durable fixture route registry, e.g. JSON/YAML or Python data in `scripts/ralph_loop`, that maps:
  - `view_id`
  - route (`home`, `study`, `stats`, `settings`, `games`, `narrow`, `wide`, future dialogs/empty/error/import/onboarding states)
  - fixture ID and fixture hash
  - orientation/profile
  - expected UI terms/test tags
  - primary source files
  - known learning/scheduler invariants to preserve
- Ensure screenshot-only app launch continues to skip background startup side effects and heavy provider/import work.
- Add fixture metadata to screenshot `manifest.json`: `captured_at_utc`, command argv, fixture set hash, app/build SHA, route/view IDs, orientation, emulator profile, and PNG SHA-256 hashes.
- Keep GitHub Actions route `all` exact-order validation for current routes; add tests when new routes are introduced.

**Acceptance:**

- Re-running the same route on the same SHA produces the same route list, fixture IDs, and deterministic metadata, with image bytes allowed to differ only when the actual UI changed.
- Artifact validation fails closed on missing manifest fields, missing PNGs, wrong routes, ANR/dialog screenshots, black/empty screenshots, or unexpected fixture IDs.

### Task 3 — Build the UI manifest and button matrix by view

**Purpose:** connect visual evidence to source files, buttons, tests, and safe edit scope.

**Work:**

- Extend `scripts/ralph_loop/ui_manifest.py` or add a companion module to generate a `ui-view-matrix.json` from source analysis plus the fixture route registry.
- Preserve existing file bucket behavior (`home`, `study`, `settings`, `stats`, `games`, `shell`, `theme`, `shared`) and add view-level fields:
  - `view_id`, `route`, `fixture_id`, `screenshot_names`
  - primary/secondary source files
  - Compose test files and nearest tests
  - visible buttons/interactive controls from `button_contract.py`
  - test tags/content descriptions/accessibility labels
  - risk tags and forbidden behavior areas
- Produce Markdown summary tables for human review.

**Acceptance:**

- A selected screenshot route can be traced to the exact source files and button rows the implementer may touch.
- Interactive views without button contract/test coverage fail the appropriate gate instead of being silently accepted.

### Task 4 — Tighten the design critic prompt for target-view JSON

**Purpose:** make the critic produce a deterministic, auditable target instead of broad subjective polish.

**Work:**

- Update `scripts/prompts/ralph_design_critic.md` or add a new prompt used by the loop.
- Require JSON only. Reject markdown, prose wrappers, multiple accepted issues, vague redesigns, or issues without screenshot/manifest evidence.
- Include the existing README Cheap Ralph UX queue item as context, while explicitly preserving learning/scheduler correctness and current product behavior.
- Require exactly one highest-impact issue when any issue is accepted.
- Require a target-view spec with concrete layout/copy/accessibility/touch-target/loading/empty/error-state expectations.
- Allow `target_screenshot` only when the model/tool genuinely generated one; otherwise set it to `null` with `target_screenshot_unavailable_reason`.

**Proposed critic output schema:**

```json
{
  "schema": "cheap-ralph-design-critic-v1",
  "passed": false,
  "view_id": "home-default",
  "before_screenshot_sha256": "...",
  "score_before": 0.62,
  "accepted_issue": {
    "id": "stable-slug",
    "title": "one-line issue",
    "severity": "low|medium|high",
    "evidence": "specific screenshot/manifest evidence",
    "primary_file": "app/src/...",
    "expected_fix": "small PR-sized fix",
    "acceptance_criteria": ["..."],
    "do_not_touch": ["scheduler semantics", "sync/provider/storage"]
  },
  "target_view_spec": {
    "summary": "what the after screenshot should show",
    "hierarchy": ["..."],
    "copy_changes": ["..."],
    "spacing_touch_targets": ["..."],
    "accessibility": ["..."],
    "material_expectations": ["..."]
  },
  "target_screenshot": null,
  "target_screenshot_unavailable_reason": "image generation not configured",
  "rejected_issues": [{"title": "...", "reason": "..."}]
}
```

**Acceptance:** malformed JSON, multiple accepted issues, missing target spec, missing evidence, broad rewrites, or forbidden behavior changes all fail closed.

### Task 5 — Tighten the implementer prompt and scratch-checkout contract

**Purpose:** keep implementation narrow, reversible, and isolated.

**Work:**

- Update `scripts/prompts/ralph_ui_implementer.md` so the implementer receives exactly one accepted issue, the target-view spec, the view/button matrix slice, forbidden paths, diff limits, and required tests.
- Require implementation in a scratch checkout path supplied by the orchestrator, never in the source checkout.
- Require tests-first only when behavior changes; for visual-only changes, require the narrowest relevant screenshot/manual/Compose assertion update available.
- Require JSON output with changed files, tests run, patch path, blocked reason, and whether the after screenshot should be expected to improve.
- Reject zero or multiple issues.

**Acceptance:** implementer cannot proceed without exactly one accepted issue and cannot claim success without a patch plus test evidence or a clear blocked reason.

### Task 6 — Add orchestrator modes and scratch worktree flow

**Purpose:** make the loop executable without accidental source mutation.

**Work:**

- Extend `scripts/ralph_loop/orchestrator.py` parser and execution model with:
  - `--audit-only` (default review/proposal only; preserve current behavior)
  - `--dry-run` (allow scratch implementation and validation, leave main checkout unchanged)
  - `--apply-accepted` (apply the accepted patch to main checkout only after all gates pass)
  - `--commit-accepted` (commit only after apply and gates pass)
  - `--iterations N` with a hard cap, default `1`
  - existing remote screenshot flags such as `--push-pr-branch`, `--require-remote-screenshots`, `--remote-screenshot-workflow`, `--screenshot-artifact`, and `--screenshot-route`
  - score threshold flag such as `--min-design-score-delta 0.10`
- Implement mode validation:
  - `--commit-accepted` requires `--apply-accepted`.
  - `--apply-accepted` and `--commit-accepted` are invalid on protected/default branches.
  - `--audit-only` cannot be combined with apply/commit.
- Create scratch checkouts under `.ralph-loop/scratch/<run-id>/iteration-XXX` using `git worktree add` or an equivalent safe checkout from the exact HEAD SHA.
- Export candidate changes as a patch and validate the patch before touching the main checkout.
- Clean up scratch worktrees only after manifest and patch artifacts are written, or leave them with an explicit `cleanup_pending` field for debugging.

**Acceptance:**

- Audit-only never calls the implementer and never edits source files.
- Dry-run may produce a patch, but `git status --short` in the main checkout remains clean except ignored/generated `.ralph-loop` artifacts.
- Apply/commit modes mutate only after validation passes and only with explicit flags.

### Task 7 — Enforce forbidden paths, diff limits, and visual acceptance gates

**Purpose:** ensure the loop cannot land risky or unvalidated changes.

**Work:**

- Extend `scripts/ralph_loop/validation.py` or add a focused validator for screenshot-loop state.
- Keep existing forbidden globs for CI/build paths and add UX-loop default forbiddens for learning correctness risk unless separately authorized:
  - `.github/workflows/**`
  - `ci/**` during normal UX iterations
  - Gradle wrapper/build files
  - release/signing files
  - sync/provider/storage/database migration paths
  - scheduler/FSRS/domain behavior paths unless the accepted issue explicitly requires and Bee approved it
- Keep or tighten default diff limits (`MAX_CHANGED_FILES`, `MAX_DIFF_LINES`) for PR-sized UI changes.
- Add before/after visual gates:
  - before screenshot manifest exists and validates
  - after screenshot manifest exists and validates
  - same route/view/fixture/device profile for before and after
  - PNG hashes recorded
  - comparison critic JSON exists and says `after_better=true`
  - `score_after - score_before >= --min-design-score-delta`
  - no new critical residual issue
- Ensure compile-only success cannot mark visual gates passed.

**Acceptance:** missing after screenshots, mismatched fixtures, missing comparison JSON, score delta below threshold, changed forbidden paths, excessive diff size, or unrelated dirty files all block apply/commit.

### Task 8 — Add design comparison prompt

**Purpose:** independently verify that the actual after screenshot improved relative to the before screenshot and target spec.

**Work:**

- Add a comparison prompt or mode for `ralph_design_critic.md` that receives:
  - before screenshot manifest and image paths/URLs
  - after screenshot manifest and image paths/URLs
  - target-view spec and optional target screenshot
  - accepted issue JSON
  - changed-file summary
- Require JSON only.

**Proposed comparison schema:**

```json
{
  "schema": "cheap-ralph-design-comparison-v1",
  "passed": true,
  "after_better": true,
  "score_before": 0.62,
  "score_after": 0.77,
  "score_delta": 0.15,
  "issue_resolved": true,
  "new_regressions": [],
  "learning_correctness_risk": false,
  "rationale": "specific visual evidence"
}
```

**Acceptance:** comparison fails closed if the model returns non-JSON, omits scores, says after is not better, flags learning correctness risk, or reports unresolved target criteria.

### Task 9 — Integrate GitHub Actions screenshots as the preferred remote evidence path

**Purpose:** avoid local emulator flakiness and avoid fake visual validation.

**Work:**

- Reuse `scripts/ralph_loop/github_screenshots.py` for before and after captures.
- For after screenshots from scratch patches, create a safe evidence branch only when the user supplied an explicit push flag. Otherwise, attempt local capture if available or leave the visual gate pending/blocked.
- Record GitHub workflow run IDs, artifact names, head SHA, branch, route, and downloaded artifact paths in the loop manifest.
- Keep protection against dispatching from GitHub Actions itself to avoid self-loops.
- Do not treat `remote_visual_pending` as success for apply/commit.

**Acceptance:** when a local emulator is unavailable, the loop either obtains a valid GitHub Actions artifact or blocks with a clear pending/needs-host status; it never substitutes compile/test success for screenshots.

### Task 10 — Update runbook documentation

**Purpose:** make the operational path durable for future Cheap Ralph runs.

**Work:**

- Update `docs/ralph-ui-loop/runbook.md` with:
  - audit-only UX inventory command
  - dry-run one-issue screenshot loop command
  - apply-accepted and commit-accepted examples with warnings
  - GitHub Actions screenshot artifact path and troubleshooting
  - validation/manifest locations
  - explanation that this loop supports the README broad UX queue item and does not replace learning correctness work
- Keep the existing remote screenshot command sequence intact unless changed by code.

**Acceptance:** a future agent can run the loop from the runbook without knowing this plan.

### Task 11 — Add smoke/unit tests

**Purpose:** lock down safety behavior and JSON contracts.

**Work:** add or extend tests under `scripts/tests/` for:

- CLI parser accepts `--audit-only`, `--dry-run`, `--apply-accepted`, `--commit-accepted`, `--iterations`, and rejects invalid combinations.
- Audit-only does not create scratch edits, does not invoke implementer, and leaves source files unchanged.
- Dry-run creates scratch checkout/patch artifacts and leaves main checkout clean.
- Apply/commit require all gates and explicit flags.
- Critic prompt/schema rejects non-JSON, multiple issues, missing evidence, missing target spec, and forbidden behavior changes.
- Implementer prompt/schema rejects zero/multiple issues and source-checkout edits.
- Manifest includes SHA/branch/timestamp/commands/file list/screenshot hashes.
- Screenshot artifact validation rejects missing/mismatched before/after fixtures.
- Validation rejects compile-only success when screenshots/comparison are missing.
- Forbidden path and diff-limit guards block risky patches.
- Design comparison requires after-better and minimum score delta.
- GitHub remote screenshot fallback returns pending/blocked, not passed, when auth/artifact/emulator evidence is unavailable.

**Suggested test command:**

```sh
python3 -m unittest discover -s scripts/tests
```

### Task 12 — Final verification commands for implementation PR

A candidate implementation is not done until it has real command output for:

```sh
python3 -m unittest discover -s scripts/tests
./gradlew :core:test :app:assembleDebug
python3 scripts/ralph_loop/orchestrator.py \
  --repo-root . \
  --run-dir .ralph-loop/current \
  --audit-only \
  --screenshot-route all \
  --iterations 1
```

If testing a full visual dry run, also require either local screenshots or GitHub Actions artifacts:

```sh
python3 scripts/ralph_loop/orchestrator.py \
  --repo-root . \
  --run-dir .ralph-loop/current \
  --dry-run \
  --require-remote-screenshots \
  --remote-screenshot-workflow android-screenshots.yml \
  --screenshot-artifact android-screenshots \
  --screenshot-route all \
  --iterations 1
```

Do not mark the full dry run accepted unless `.ralph-loop/current/validation.json` shows passing screenshot and design-comparison gates.

## Rollout strategy

1. Land schema/tests and no-op audit improvements first.
2. Land deterministic fixture metadata and manifest extensions.
3. Land critic/implementer prompt schema tightening.
4. Land scratch-checkout dry-run support.
5. Land after-screenshot comparison and score-delta gate.
6. Only then enable `--apply-accepted`; keep `--commit-accepted` as the final, most restricted mode.

## Definition of done

- The default loop can inventory current Kani UI views and propose one evidence-backed UX issue without modifying source files.
- A dry run can implement one accepted issue in a scratch checkout, run tests, capture real before/after screenshots, and produce a patch plus validation report.
- Apply/commit modes are impossible without explicit flags and passing gates.
- The run manifest is sufficient to reproduce what was reviewed: branch/SHA, timestamp, commands, fixtures, screenshots, prompt outputs, changed files, and decision.
- The README Cheap Ralph broad UX queue item is supported by a view-by-view evidence loop while learning correctness, scheduler behavior, and product contract remain protected.
