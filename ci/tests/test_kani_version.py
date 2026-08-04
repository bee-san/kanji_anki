from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ci/scripts"))

from kani_version import (  # noqa: E402
    Version,
    metadata_lines,
    next_patch_tag,
    parse_tag,
    validate_new_tag,
)


class KaniVersionTest(unittest.TestCase):
    def test_normal_tag_uses_canonical_android_code(self) -> None:
        version = parse_tag("v0.4.193")

        self.assertEqual("0.4.193", version.name)
        self.assertEqual(4_193, version.code)

    def test_beta_tag_preserves_suffix_while_sharing_numeric_android_code(self) -> None:
        version = parse_tag("v0.5.12-beta")

        self.assertEqual("0.5.12-beta", version.name)
        self.assertEqual("v0.5.12-beta", version.tag)
        self.assertEqual(5_012, version.code)

    def test_patch_999_is_valid_but_cannot_be_auto_incremented(self) -> None:
        self.assertEqual(4_999, parse_tag("v0.4.999").code)
        self.assertEqual(4_999, parse_tag("v0.4.999-beta").code)

        with self.assertRaisesRegex(ValueError, "bump minor"):
            next_patch_tag(["v0.4.999-beta"], beta=True)

    def test_patch_1000_fails_with_actionable_guard(self) -> None:
        with self.assertRaisesRegex(ValueError, "bump minor"):
            parse_tag("v0.4.1000")

    def test_noncanonical_leading_zero_tag_fails_closed(self) -> None:
        with self.assertRaisesRegex(ValueError, "vMAJOR.MINOR.PATCH"):
            parse_tag("v0.04.194")

    def test_noncanonical_prerelease_suffixes_fail_closed(self) -> None:
        for tag in ("v0.5.12_beta", "v0.5.12-alpha", "v0.5.12-beta.1"):
            with self.subTest(tag=tag), self.assertRaisesRegex(ValueError, "vMAJOR.MINOR.PATCH"):
                parse_tag(tag)

    def test_minor_boundary_remains_monotonic(self) -> None:
        self.assertLess(parse_tag("v0.4.999").code, parse_tag("v0.5.0").code)

    def test_next_patch_uses_semantic_maximum_and_ignores_unrelated_tags(self) -> None:
        self.assertEqual(
            "v0.5.1",
            next_patch_tag(["v0.4.999", "release-candidate", "v0.5.0"]),
        )

    def test_next_beta_patch_advances_past_stable_legacy_and_suffixed_tags(self) -> None:
        self.assertEqual(
            "v0.5.13-beta",
            next_patch_tag(["v0.5.10", "v0.5.11", "v0.5.12-beta"], beta=True),
        )

    def test_new_tag_must_advance_past_every_stable_and_beta_numeric_core(self) -> None:
        validate_new_tag("v0.5.13", ["v0.5.11", "v0.5.12-beta"])

        with self.assertRaisesRegex(ValueError, "numeric core newer"):
            validate_new_tag("v0.5.12", ["v0.5.12-beta"])
        with self.assertRaisesRegex(ValueError, "numeric core newer"):
            validate_new_tag("v0.5.11-beta", ["v0.5.12"])

    def test_tag_push_can_ignore_its_exact_tag_but_not_a_sibling_core(self) -> None:
        validate_new_tag(
            "v0.5.12-beta",
            ["v0.5.11", "v0.5.12-beta"],
            ignore_existing_exact=True,
        )

        with self.assertRaisesRegex(ValueError, "already exists"):
            validate_new_tag("v0.5.12-beta", ["v0.5.12-beta"])
        with self.assertRaisesRegex(ValueError, "numeric core newer"):
            validate_new_tag(
                "v0.5.12-beta",
                ["v0.5.12", "v0.5.12-beta"],
                ignore_existing_exact=True,
            )

    def test_metadata_is_ready_for_github_output(self) -> None:
        self.assertEqual(
            [
                "release_tag=v2.7.9",
                "release_name=v2.7.9",
                "prerelease=false",
                "build_sha=abc123",
                "version_name=2.7.9",
                "version_code=2007009",
                "apk_name=kani-android-2.7.9.apk",
                "checksum_name=kani-android-2.7.9.apk.sha256",
            ],
            metadata_lines("v2.7.9", "abc123"),
        )

    def test_beta_metadata_uses_suffix_for_tag_apk_and_android_version_name(self) -> None:
        self.assertEqual(
            [
                "release_tag=v2.7.9-beta",
                "release_name=v2.7.9-beta",
                "prerelease=true",
                "build_sha=abc123",
                "version_name=2.7.9-beta",
                "version_code=2007009",
                "apk_name=kani-android-2.7.9-beta.apk",
                "checksum_name=kani-android-2.7.9-beta.apk.sha256",
            ],
            metadata_lines("v2.7.9-beta", "abc123"),
        )

    def test_numeric_metadata_can_be_published_as_a_legacy_compatible_prerelease(self) -> None:
        self.assertEqual(
            [
                "release_tag=v2.7.9",
                "release_name=v2.7.9",
                "prerelease=true",
                "build_sha=abc123",
                "version_name=2.7.9",
                "version_code=2007009",
                "apk_name=kani-android-2.7.9.apk",
                "checksum_name=kani-android-2.7.9.apk.sha256",
            ],
            metadata_lines("v2.7.9", "abc123", prerelease=True),
        )

    def test_android_version_code_bound_is_enforced(self) -> None:
        with self.assertRaisesRegex(ValueError, "Android"):
            _ = Version(2_101, 0, 0).code


if __name__ == "__main__":
    unittest.main()
