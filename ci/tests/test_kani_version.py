from __future__ import annotations

import hashlib
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ci/scripts"))

from kani_version import (  # noqa: E402
    CHECKSUMS_NAME,
    MANIFEST_NAME,
    MANIFEST_SIGNATURE_NAME,
    MSI_MAJOR_MINOR_MAX,
    MSI_PATCH_MAX,
    Version,
    checksums_text,
    desktop_metadata_lines,
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


class DesktopPackagingContractTest(unittest.TestCase):
    def test_desktop_metadata_derives_every_version_and_asset_from_the_tag(self) -> None:
        self.assertEqual(
            [
                "release_tag=v0.4.33",
                "version_name=0.4.33",
                "msi_version=0.4.33",
                # jpackage rejects a zero leading component, so the macOS short
                # version offsets the major; the build version is the version code.
                "macos_short_version=1.4.33",
                "macos_build_version=4033",
                "deb_version=0.4.33-1",
                f"manifest_name={MANIFEST_NAME}",
                f"manifest_signature_name={MANIFEST_SIGNATURE_NAME}",
                f"checksums_name={CHECKSUMS_NAME}",
                "desktop_assets="
                "kani-desktop-windows-x64-0.4.33.msi "
                "kani-desktop-macos-arm64-0.4.33.dmg "
                "kani-desktop-linux-x64-0.4.33.deb "
                "kani-desktop-linux-x64-0.4.33.tar.gz",
            ],
            desktop_metadata_lines("v0.4.33"),
        )

    def test_msi_version_fails_closed_above_the_installer_bounds(self) -> None:
        self.assertEqual(
            f"{MSI_MAJOR_MINOR_MAX}.{MSI_MAJOR_MINOR_MAX}.999",
            Version(MSI_MAJOR_MINOR_MAX, MSI_MAJOR_MINOR_MAX, 999).msi_version,
        )
        with self.assertRaisesRegex(ValueError, "MSI major"):
            _ = Version(MSI_MAJOR_MINOR_MAX + 1, 0, 0).msi_version
        with self.assertRaisesRegex(ValueError, "MSI minor"):
            _ = Version(0, MSI_MAJOR_MINOR_MAX + 1, 0).msi_version
        with self.assertRaisesRegex(ValueError, "MSI patch"):
            _ = Version(0, 0, MSI_PATCH_MAX + 1).msi_version

    def test_macos_build_version_is_monotonic_across_a_minor_bump(self) -> None:
        self.assertLess(
            int(parse_tag("v0.4.999").macos_build_version),
            int(parse_tag("v0.5.0").macos_build_version),
        )

    def test_checksums_are_sorted_by_filename_and_use_basenames(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            names = (
                "kani-desktop-windows-x64-1.0.0.msi",
                "kani-desktop-linux-x64-1.0.0.deb",
                "kani-desktop-macos-arm64-1.0.0.dmg",
            )
            for index, name in enumerate(names):
                (root / name).write_bytes(b"payload-%d" % index)

            # Collected in an arbitrary order, as a release job would.
            text = checksums_text([root / name for name in names])

            lines = text.splitlines()
            self.assertEqual(
                sorted(names),
                [line.split("  ", 1)[1] for line in lines],
            )
            expected = hashlib.sha256(b"payload-1").hexdigest()
            self.assertEqual(
                f"{expected}  kani-desktop-linux-x64-1.0.0.deb",
                lines[0],
            )
            # Reproducible: the same asset set in any order yields the same bytes.
            self.assertEqual(
                text,
                checksums_text([root / name for name in reversed(names)]),
            )
            self.assertTrue(text.endswith("\n"))

    def test_duplicate_asset_filenames_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "a").mkdir()
            (root / "b").mkdir()
            for parent in ("a", "b"):
                (root / parent / "kani-desktop-linux-x64-1.0.0.deb").write_bytes(b"x")

            with self.assertRaisesRegex(ValueError, "duplicate asset filename"):
                checksums_text(
                    [
                        root / "a" / "kani-desktop-linux-x64-1.0.0.deb",
                        root / "b" / "kani-desktop-linux-x64-1.0.0.deb",
                    ],
                )


if __name__ == "__main__":
    unittest.main()
