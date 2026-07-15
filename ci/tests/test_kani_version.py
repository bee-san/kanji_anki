from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ci/scripts"))

from kani_version import Version, metadata_lines, next_patch_tag, parse_tag  # noqa: E402


class KaniVersionTest(unittest.TestCase):
    def test_normal_tag_uses_canonical_android_code(self) -> None:
        version = parse_tag("v0.4.193")

        self.assertEqual("0.4.193", version.name)
        self.assertEqual(4_193, version.code)

    def test_patch_999_is_valid_but_cannot_be_auto_incremented(self) -> None:
        self.assertEqual(4_999, parse_tag("v0.4.999").code)

        with self.assertRaisesRegex(ValueError, "bump minor"):
            next_patch_tag(["v0.4.999"])

    def test_patch_1000_fails_with_actionable_guard(self) -> None:
        with self.assertRaisesRegex(ValueError, "bump minor"):
            parse_tag("v0.4.1000")

    def test_noncanonical_leading_zero_tag_fails_closed(self) -> None:
        with self.assertRaisesRegex(ValueError, "vMAJOR.MINOR.PATCH"):
            parse_tag("v0.04.194")

    def test_minor_boundary_remains_monotonic(self) -> None:
        self.assertLess(parse_tag("v0.4.999").code, parse_tag("v0.5.0").code)

    def test_next_patch_uses_semantic_maximum_and_ignores_unrelated_tags(self) -> None:
        self.assertEqual(
            "v0.5.1",
            next_patch_tag(["v0.4.999", "release-candidate", "v0.5.0"]),
        )

    def test_metadata_is_ready_for_github_output(self) -> None:
        self.assertEqual(
            [
                "release_tag=v2.7.9",
                "build_sha=abc123",
                "version_name=2.7.9",
                "version_code=2007009",
                "apk_name=kani-android-2.7.9.apk",
                "checksum_name=kani-android-2.7.9.apk.sha256",
            ],
            metadata_lines("v2.7.9", "abc123"),
        )

    def test_android_version_code_bound_is_enforced(self) -> None:
        with self.assertRaisesRegex(ValueError, "Android"):
            _ = Version(2_101, 0, 0).code


if __name__ == "__main__":
    unittest.main()
