#!/usr/bin/env python3

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN_RELEASE_WORKFLOW = ROOT / ".github/workflows/main-bugfix-release.yml"
ANDROID_RELEASE_WORKFLOW = ROOT / ".github/workflows/android-release.yml"


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


if __name__ == "__main__":
    unittest.main()
