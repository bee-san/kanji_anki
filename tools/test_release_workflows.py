#!/usr/bin/env python3

from __future__ import annotations

import fnmatch
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN_RELEASE_WORKFLOW = ROOT / ".github/workflows/main-bugfix-release.yml"
ANDROID_RELEASE_WORKFLOW = ROOT / ".github/workflows/android-release.yml"
DEBUG_MANIFEST = ROOT / "app/src/debug/AndroidManifest.xml"
FAKE_PROVIDER_DEBUG_SOURCE = (
    ROOT / "app/src/debug/kotlin/dev/bee/kanjianki/anki/FakeAnkiDroidProvider.kt"
)
FAKE_PROVIDER_ANDROID_TEST_SOURCE = (
    ROOT / "app/src/androidTest/kotlin/dev/bee/kanjianki/anki/FakeAnkiDroidProvider.kt"
)


def android_fixture_gate_patterns(workflow: str) -> tuple[str, ...]:
    body = workflow.split('case "${changed_file}" in', maxsplit=1)[1]
    body = body.split("ankidroid_fixture_required=true", maxsplit=1)[0]
    patterns: list[str] = []
    for raw_line in body.splitlines():
        line = raw_line.strip()
        if line.endswith("|\\"):
            patterns.append(line[:-2])
        elif line.endswith(")"):
            patterns.append(line[:-1])
    return tuple(patterns)


class MainBugfixReleaseWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = MAIN_RELEASE_WORKFLOW.read_text(encoding="utf-8")

    def test_is_manual_only_not_main_push_triggered(self) -> None:
        self.assertIn("workflow_dispatch:", self.workflow)
        self.assertNotRegex(self.workflow, r"(?m)^\s+push:\s*$")
        self.assertNotRegex(self.workflow, r"(?m)^\s+-\s+main\s*$")

    def test_does_not_create_tags_or_publish_releases(self) -> None:
        forbidden_commands = (
            "git tag",
            "git push origin",
            "gh release create",
            "gh release upload",
        )
        for command in forbidden_commands:
            with self.subTest(command=command):
                self.assertNotIn(command, self.workflow)

    def test_downstream_release_dispatch_requires_boolean_and_confirmation(self) -> None:
        self.assertIn("dispatch_android_release:", self.workflow)
        self.assertIn("default: false", self.workflow)
        self.assertIn("dispatch_confirmation:", self.workflow)
        self.assertIn("dispatch android-release.yml", self.workflow)
        self.assertIn("if: needs.plan.outputs.should_dispatch == 'true'", self.workflow)
        self.assertIn("environment: android-release-approval", self.workflow)
        self.assertIn("gh workflow run android-release.yml", self.workflow)

    def test_rejects_existing_tags_and_non_semver_tags_before_dispatch(self) -> None:
        self.assertRegex(self.workflow, r"\^v\[0-9\]\+\\\.\[0-9\]\+\\\.\[0-9\]\+\$")
        self.assertIn("git fetch --tags", self.workflow)
        self.assertIn("refs/tags/${RELEASE_TAG}", self.workflow)
        self.assertIn("Tag ${RELEASE_TAG} already exists", self.workflow)


class AndroidReleaseWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = ANDROID_RELEASE_WORKFLOW.read_text(encoding="utf-8")

    def test_existing_release_assets_fail_closed_by_default(self) -> None:
        self.assertIn("allow_asset_overwrite:", self.workflow)
        self.assertIn("default: false", self.workflow)
        self.assertIn("ALLOW_ASSET_OVERWRITE", self.workflow)
        self.assertIn('if [[ "${ALLOW_ASSET_OVERWRITE}" == "true" ]]; then', self.workflow)
        self.assertIn("upload_args+=(--clobber)", self.workflow)

        unconditional_uploads = re.findall(r"gh release upload [^\n]*--clobber", self.workflow)
        self.assertEqual([], unconditional_uploads)

    def test_publish_release_waits_for_fixture_success_or_skip(self) -> None:
        publish_job = self.workflow.split("  publish-release:", maxsplit=1)[1]
        self.assertIn("needs.ankidroid-fixture.result == 'success'", publish_job)
        self.assertIn("needs.ankidroid-fixture.result == 'skipped'", publish_job)
        self.assertIn("needs.validate.result == 'success'", publish_job)
        self.assertIn("needs.quality-status.result == 'success'", publish_job)

    def test_ankidroid_fixture_gate_matches_release_sensitive_paths(self) -> None:
        patterns = android_fixture_gate_patterns(self.workflow)
        required_paths = (
            "app/src/main/kotlin/dev/bee/kanjianki/NoteTypeFieldMappings.kt",
            "app/src/main/kotlin/dev/bee/kanjianki/anki/AnkiDroidGateway.kt",
            "app/src/main/kotlin/dev/bee/kanjianki/sync/ManualSyncEngine.kt",
            "app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreSync.kt",
            "app/src/debug/AndroidManifest.xml",
            "app/src/debug/kotlin/dev/bee/kanjianki/anki/FakeAnkiDroidProvider.kt",
            "app/src/androidTest/kotlin/dev/bee/kanjianki/MainActivityInstrumentedTest.kt",
            "app/src/androidTest/kotlin/dev/bee/kanjianki/anki/AnkiDroidGatewayProviderInstrumentedTest.kt",
            "build.gradle.kts",
            "app/build.gradle.kts",
            "ci/scripts/run_ankidroid_fixture.sh",
            ".github/workflows/android-instrumented.yml",
        )
        for path in required_paths:
            with self.subTest(path=path):
                self.assertTrue(
                    any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns),
                    f"{path} did not match any fixture gate pattern: {patterns}",
                )


class FakeAnkiDroidProviderPackagingTest(unittest.TestCase):
    def test_fake_provider_source_lives_in_debug_app_not_android_test_apk(self) -> None:
        self.assertTrue(FAKE_PROVIDER_DEBUG_SOURCE.exists())
        self.assertFalse(FAKE_PROVIDER_ANDROID_TEST_SOURCE.exists())
        android_test_fake_providers = sorted(
            path.relative_to(ROOT).as_posix()
            for path in (ROOT / "app/src/androidTest").rglob("FakeAnkiDroidProvider.*")
        )
        self.assertEqual([], android_test_fake_providers)

    def test_debug_manifest_declares_non_exported_fake_provider(self) -> None:
        manifest = ET.parse(DEBUG_MANIFEST)
        namespace = "{http://schemas.android.com/apk/res/android}"
        providers = manifest.findall(".//provider")
        fake_providers = [
            provider
            for provider in providers
            if provider.attrib.get(f"{namespace}name")
            == "dev.bee.kanjianki.anki.FakeAnkiDroidProvider"
        ]
        self.assertEqual(1, len(fake_providers))
        provider = fake_providers[0]
        self.assertEqual("dev.bee.kanjianki.test.ankidroid", provider.attrib.get(f"{namespace}authorities"))
        self.assertEqual("false", provider.attrib.get(f"{namespace}exported"))


if __name__ == "__main__":
    unittest.main()
