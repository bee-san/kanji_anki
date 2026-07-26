from __future__ import annotations

import ast
import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
DESKTOP_WORKFLOW = ROOT / ".github/workflows/desktop-ci.yml"
ANDROID_WORKFLOW = ROOT / ".github/workflows/android-ci.yml"
ANDROID_SDK_ACTION = ROOT / ".github/actions/setup-android-sdk/action.yml"


def _mapping_block(document: str, key: str, indentation: int) -> str:
    header = f"{' ' * indentation}{key}:"
    lines = document.splitlines()
    matches = [index for index, line in enumerate(lines) if line == header]
    if len(matches) != 1:
        raise AssertionError(f"expected one {header!r} mapping, found {len(matches)}")

    body = []
    for line in lines[matches[0] + 1 :]:
        stripped = line.lstrip()
        if stripped and not stripped.startswith("#"):
            current_indentation = len(line) - len(stripped)
            if current_indentation <= indentation:
                break
        body.append(line)
    return "\n".join(body)


def _string_list(document: str, key: str, indentation: int) -> list[str]:
    body = _mapping_block(document, key, indentation)
    prefix = f"{' ' * (indentation + 2)}- "
    values = []
    for line in body.splitlines():
        if not line.startswith(prefix):
            continue
        scalar = line[len(prefix) :].strip()
        value = ast.literal_eval(scalar) if scalar.startswith(("'", '"')) else scalar
        if not isinstance(value, str):
            raise AssertionError(f"expected a string under {key}: {scalar}")
        values.append(value)
    return values


class DesktopCiWorkflowContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = DESKTOP_WORKFLOW.read_text(encoding="utf-8")

    def test_permanent_trigger_is_exact_and_required_gate_has_no_path_filter(
        self,
    ) -> None:
        on_block = _mapping_block(self.workflow, "on", 0)
        push_block = _mapping_block(on_block, "push", 2)

        self.assertEqual(["main"], _string_list(push_block, "branches", 4))
        self.assertIn("workflow_dispatch:", on_block)
        self.assertIn("pull_request:", on_block)
        self.assertNotIn("pull_request_target:", self.workflow)
        self.assertNotIn("paths:", on_block)
        classify = _mapping_block(self.workflow, "classify", 2)
        self.assertNotIn("bee-san/kanji_anki", classify)
        self.assertNotIn("desktop/support", self.workflow)
        self.assertIn(
            "python3 ci/scripts/classify_desktop_ci.py --force run",
            classify,
        )
        attributes = (ROOT / ".gitattributes").read_text(encoding="utf-8")
        self.assertIn(
            "gradle/verification-metadata.xml text eol=lf",
            attributes,
        )
        self.assertIn("branding/kani-app-icon.svg text eol=lf", attributes)
        self.assertIn(
            "core/src/test/resources/**/*.txt text eol=lf",
            attributes,
        )

    def test_matrix_pins_exact_supported_host_labels_and_architectures(self) -> None:
        matrix_job = _mapping_block(self.workflow, "desktop_matrix", 2)
        entries = re.findall(
            r"          - host_id: ([^\n]+)\n"
            r"            runner: ([^\n]+)\n"
            r"            runner_arch: ([^\n]+)\n"
            r"            gradle_command: ([^\n]+)",
            matrix_job,
        )
        self.assertEqual(
            [
                ("linux", "ubuntu-24.04", "X64", "xvfb-run -a ./gradlew"),
                ("windows", "windows-2025", "X64", "./gradlew.bat"),
                ("macos", "macos-15", "ARM64", "./gradlew"),
            ],
            entries,
        )
        self.assertEqual(1, matrix_job.count("xvfb-run -a ./gradlew"))
        self.assertIn("runs-on: ${{ matrix.runner }}", matrix_job)
        self.assertIn("actual = os.environ['RUNNER_ARCH']", matrix_job)
        self.assertIn("assert actual == expected", matrix_job)

    def test_matrix_has_one_unconditional_strict_desktop_gate(self) -> None:
        matrix_job = _mapping_block(self.workflow, "desktop_matrix", 2)
        strict = matrix_job.split(
            "      - name: Run strict desktop gate",
            maxsplit=1,
        )[1].split(
            "      - name: Upload failed desktop diagnostics",
            maxsplit=1,
        )[0]

        self.assertNotRegex(strict, r"(?m)^        if:")
        self.assertEqual(1, matrix_job.count("${{ matrix.gradle_command }}"))
        self.assertIn("ciDesktop ciDesktopPackage", strict)
        self.assertNotIn(":app:processDebugResources", strict)
        self.assertIn("--dependency-verification=strict", strict)
        self.assertNotIn("--write-verification-metadata", strict)
        self.assertNotIn("-x testBuildLogic", strict)
        self.assertIn("--no-daemon --no-build-cache", strict)
        self.assertIn(
            "python ci/scripts/assert_verification_metadata_scope.py",
            matrix_job,
        )
        scope_script = (
            ROOT / "ci/scripts/assert_verification_metadata_scope.py"
        ).read_text(encoding="utf-8")
        self.assertIn('"--others"', scope_script)
        self.assertIn('"--exclude-standard"', scope_script)
        self.assertIn(
            "git diff --exit-code -- gradle/verification-metadata.xml",
            matrix_job,
        )
        self.assertNotIn("--write-verification-metadata", self.workflow)
        self.assertNotIn("VERIFICATION_MODE", self.workflow)
        self.assertNotIn("verification-bootstrap", self.workflow)
        self.assertNotIn("secrets.", self.workflow)

    def test_gradle_wrapper_java_and_cache_policy_are_pinned(self) -> None:
        matrix_job = _mapping_block(self.workflow, "desktop_matrix", 2)
        self.assertIn(
            "gradle/actions/wrapper-validation@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e",
            matrix_job,
        )
        self.assertLess(
            matrix_job.index("gradle/actions/wrapper-validation@"),
            matrix_job.index("${{ matrix.gradle_command }}"),
        )
        self.assertIn(
            "actions/setup-java@0f481fcb613427c0f801b606911222b5b6f3083a",
            matrix_job,
        )
        self.assertIn("distribution: temurin", matrix_job)
        self.assertIn("java-version: '17'", matrix_job)
        self.assertIn("cache-disabled: true", matrix_job)
        self.assertNotIn("cache-encryption-key:", matrix_job)
        self.assertNotIn("actions/cache@", matrix_job)

    def test_all_remote_actions_use_immutable_full_shas(self) -> None:
        uses = re.findall(
            r"^\s*uses:\s*([^\s#]+)",
            self.workflow,
            flags=re.MULTILINE,
        )
        self.assertTrue(uses)
        for action in uses:
            with self.subTest(action=action):
                if action.startswith("./"):
                    continue
                self.assertRegex(action, r"^[^@]+@[0-9a-f]{40}$")

    def test_each_host_retains_failure_diagnostics_and_mutation_guards(self) -> None:
        matrix_job = _mapping_block(self.workflow, "desktop_matrix", 2)

        diagnostics = matrix_job.split(
            "      - name: Upload failed desktop diagnostics",
            maxsplit=1,
        )[1].split(
            "      - name: Confirm tracked change scope",
            maxsplit=1,
        )[0]
        self.assertIn("if: failure()", diagnostics)
        self.assertIn(
            "name: desktop-diagnostics-${{ matrix.host_id }}",
            diagnostics,
        )
        self.assertIn("desktop-app/build/compose/logs/", diagnostics)
        self.assertIn("build-logic/build/test-results/test/", diagnostics)
        self.assertIn("core/build/test-results/test/", diagnostics)
        self.assertIn("core/build/reports/tests/test/", diagnostics)
        self.assertIn("if-no-files-found: warn", diagnostics)
        self.assertIn("retention-days: 7", diagnostics)
        self.assertEqual(1, matrix_job.count("retention-days: 7"))

    def test_bootstrap_and_metadata_aggregation_paths_are_absent(self) -> None:
        for forbidden in (
            "Run authorized verification-metadata bootstrap",
            "Validate build logic against generated host metadata",
            "Capture full host verification metadata",
            "Upload full host verification metadata",
            "aggregate_verification_metadata",
            "capture_verification_metadata.py",
            "validate_verification_metadata_artifacts.py",
            "merge_verification_metadata.py",
            "actions/download-artifact@",
            "--expected-mode bootstrap-write",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.workflow)

    def test_always_present_confidence_gate_validates_run_and_skip_states(self) -> None:
        gate = _mapping_block(self.workflow, "desktop_confidence_gate", 2)
        self.assertIn("name: Desktop confidence gate", gate)
        self.assertIn("if: always()", gate)
        self.assertIn("permissions: {}", gate)
        self.assertEqual(
            ["classify", "desktop_matrix"],
            _string_list(gate, "needs", 4),
        )
        self.assertIn('test "${MATRIX_RESULT}" = "success"', gate)
        self.assertIn('test "${MATRIX_RESULT}" = "skipped"', gate)
        self.assertNotIn("AGGREGATE_RESULT", gate)
        self.assertNotIn("BOOTSTRAP", gate)

    def test_workflow_permissions_are_read_only(self) -> None:
        self.assertRegex(self.workflow, r"(?m)^permissions: \{\}$")
        for job_name in ("classify", "desktop_matrix"):
            with self.subTest(job=job_name):
                job = _mapping_block(self.workflow, job_name, 2)
                permissions = _mapping_block(job, "permissions", 4)
                self.assertEqual(
                    "      contents: read",
                    permissions.strip("\n"),
                )
        gate = _mapping_block(self.workflow, "desktop_confidence_gate", 2)
        self.assertIn("    permissions: {}", gate)
        self.assertEqual(2, self.workflow.count("contents: read"))
        self.assertNotRegex(self.workflow, r"(?m)^\s+[A-Za-z-]+:\s*write\s*$")


class AndroidDesktopReleasePathContractTest(unittest.TestCase):
    REQUIRED_PATHS = {
        ".gitattributes",
        "application/**",
        "automation-android/**",
        "backup-core/**",
        "branding/**",
        "data-api/**",
        "data-android/**",
        "data-desktop/**",
        "data-sql/**",
        "desktop-app/**",
        "feature-*/**",
        "platform-android/**",
        "platform-contracts/**",
        "platform-desktop/**",
        "presentation-api/**",
        "provider-ankiconnect/**",
        "provider-ankidroid/**",
        "reference-assets/**",
        "sync-api/**",
        "sync-engine/**",
        "ui-common/**",
        "widget/**",
        "build-logic/**",
        ".github/workflows/**",
        "build.gradle*",
        "settings.gradle*",
        "gradle/**",
        "gradlew*",
        "gradle.properties",
        "gradle/libs.versions.toml",
        "docs/dependency-updates.md",
    }

    def setUp(self) -> None:
        self.workflow = ANDROID_WORKFLOW.read_text(encoding="utf-8")

    def test_main_push_and_pr_filters_cover_every_desktop_shared_release_input(self) -> None:
        on_block = _mapping_block(self.workflow, "on", 0)
        push = _mapping_block(on_block, "push", 2)
        pull_request = _mapping_block(on_block, "pull_request", 2)
        self.assertEqual(["main"], _string_list(push, "branches", 4))

        for trigger, block in (("push", push), ("pull_request", pull_request)):
            paths = set(_string_list(block, "paths", 4))
            with self.subTest(trigger=trigger):
                self.assertFalse(self.REQUIRED_PATHS - paths)

    def test_expanded_paths_still_run_the_full_android_confidence_surface(self) -> None:
        gradle_checks = _mapping_block(self.workflow, "gradle-checks", 2)
        asset_tests = _mapping_block(self.workflow, "asset-tests", 2)
        gate = _mapping_block(self.workflow, "fast-confidence-gate", 2)

        self.assertNotRegex(gradle_checks, r"(?m)^    if:")
        self.assertNotRegex(asset_tests, r"(?m)^    if:")
        self.assertIn("- gradle-checks", gate)
        self.assertIn("- asset-tests", gate)
        self.assertIn('run: "true"', gate)


class AndroidSdkActionCrossPlatformContractTest(unittest.TestCase):
    def test_sdkmanager_is_selected_and_checked_on_unix_and_windows(self) -> None:
        action = ANDROID_SDK_ACTION.read_text(encoding="utf-8")
        self.assertIn("if: runner.os != 'Windows'", action)
        self.assertIn("test -x \"${sdkmanager}\"", action)
        self.assertIn("if: runner.os == 'Windows'", action)
        self.assertIn("'cmdline-tools/latest/bin/sdkmanager.bat'", action)
        self.assertIn("Test-Path -PathType Leaf $sdkmanager", action)
        self.assertIn("$LASTEXITCODE -ne 0", action)
        self.assertEqual(2, action.count("'platform-tools'"))
        self.assertEqual(2, action.count("platforms;android-${{ inputs.platform }}"))
        self.assertEqual(2, action.count("build-tools;${{ inputs.build-tools }}"))


if __name__ == "__main__":
    unittest.main()
