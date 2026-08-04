from __future__ import annotations

import contextlib
import io
import json
import re
import tempfile
import unittest
from pathlib import Path

from tools.run_desktop_installed_image_smoke import DesktopInstalledImageSmokeError
from tools.verify_desktop_package import (
    EXPECTED_JAVA_VERSION,
    EXPECTED_MODULES,
    DesktopPackageVerificationError,
    format_report,
    main,
    runtime_release_file,
    verify_installed_package,
)


ROOT = Path(__file__).resolve().parents[1]
BUILD_LOGIC_MAIN = ROOT / "build-logic/src/main/kotlin"
PACKAGING_JDK = BUILD_LOGIC_MAIN / "dev/bee/kanjianki/buildlogic/KaniPackagingJdk.kt"
DESKTOP_IDENTITY = BUILD_LOGIC_MAIN / "dev/bee/kanjianki/buildlogic/KaniDesktopIdentity.kt"


def write_image(
    root: Path,
    *,
    platform: str = "linux",
    java_version: str = EXPECTED_JAVA_VERSION,
    modules: tuple[str, ...] = EXPECTED_MODULES,
    with_launcher: bool = True,
    with_release: bool = True,
) -> Path:
    """Builds a fake installed image with the real per-host layout."""
    launchers = {
        "linux": Path("Kani", "bin", "Kani"),
        "windows": Path("Kani", "Kani.exe"),
        "macos": Path("Kani.app", "Contents", "MacOS", "Kani"),
    }
    if with_launcher:
        launcher = root / launchers[platform]
        launcher.parent.mkdir(parents=True, exist_ok=True)
        launcher.write_text("#!/bin/sh\n", encoding="utf-8")
        launcher.chmod(0o755)
    if with_release:
        release = runtime_release_file(root, platform)
        release.parent.mkdir(parents=True, exist_ok=True)
        release.write_text(
            f'JAVA_VERSION="{java_version}"\n'
            f'MODULES="{" ".join(modules)}"\n',
            encoding="utf-8",
        )
    return root


class DesktopPackageVerificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self._temporary.cleanup)
        self.root = Path(self._temporary.name)

    def test_a_correctly_built_image_verifies_and_reports_what_it_found(self) -> None:
        write_image(self.root)

        verification = verify_installed_package(self.root, platform="linux")

        self.assertEqual("linux", verification["host"])
        self.assertEqual(EXPECTED_JAVA_VERSION, verification["java_version"])
        self.assertEqual(sorted(EXPECTED_MODULES), verification["modules"])

    def test_every_supported_host_layout_is_located_rather_than_assumed_to_be_linux(
        self,
    ) -> None:
        # A path that is wrong for a host must fail that host's gate. The failure mode
        # this guards is the opposite one: a gate that looks only where Linux keeps its
        # runtime would report "no release file" on Windows and macOS forever, or --
        # worse, if written to skip on absence -- pass without checking anything.
        for platform in ("linux", "windows", "macos"):
            with self.subTest(platform=platform):
                with tempfile.TemporaryDirectory() as directory:
                    root = write_image(Path(directory), platform=platform)
                    verification = verify_installed_package(root, platform=platform)
                    self.assertEqual(
                        EXPECTED_JAVA_VERSION,
                        verification["java_version"],
                    )

    def test_a_runtime_from_the_wrong_jdk_version_fails(self) -> None:
        write_image(self.root, java_version="21.0.11")

        with self.assertRaises(DesktopPackageVerificationError) as raised:
            verify_installed_package(self.root, platform="linux")

        message = str(raised.exception)
        self.assertIn("21.0.11", message)
        self.assertIn(EXPECTED_JAVA_VERSION, message)

    def test_the_defect_this_gate_exists_for_is_caught(self) -> None:
        # The literal image that shipped before `jdk.accessibility` was pinned: it
        # launched, rendered, passed every semantics assertion, and was mute to NVDA.
        # And the one before `java.net.http` was pinned, which crashed on the first
        # AnkiConnect call. Both built green.
        for missing in ("jdk.accessibility", "java.net.http"):
            with self.subTest(missing=missing):
                with tempfile.TemporaryDirectory() as directory:
                    root = write_image(
                        Path(directory),
                        modules=tuple(m for m in EXPECTED_MODULES if m != missing),
                    )
                    with self.assertRaises(DesktopPackageVerificationError) as raised:
                        verify_installed_package(root, platform="linux")
                    self.assertIn(missing, str(raised.exception))
                    self.assertIn("missing required modules", str(raised.exception))

    def test_an_unpinned_extra_module_is_reported_rather_than_tolerated(self) -> None:
        write_image(self.root, modules=EXPECTED_MODULES + ("jdk.jfr",))

        with self.assertRaises(DesktopPackageVerificationError) as raised:
            verify_installed_package(self.root, platform="linux")

        self.assertIn("jdk.jfr", str(raised.exception))
        self.assertIn("unpinned modules", str(raised.exception))

    def test_every_problem_is_reported_in_one_run(self) -> None:
        # A wrong runtime version and a missing module are normally one mistake -- the
        # image was built from the wrong JDK -- and learning that one build at a time
        # costs a full package cycle per fact.
        write_image(
            self.root,
            java_version="21.0.11",
            modules=tuple(m for m in EXPECTED_MODULES if m != "java.net.http"),
        )

        with self.assertRaises(DesktopPackageVerificationError) as raised:
            verify_installed_package(self.root, platform="linux")

        message = str(raised.exception)
        self.assertIn("21.0.11", message)
        self.assertIn("java.net.http", message)

    def test_a_missing_release_file_fails_rather_than_passing_unchecked(self) -> None:
        write_image(self.root, with_release=False)

        with self.assertRaises(DesktopPackageVerificationError) as raised:
            verify_installed_package(self.root, platform="linux")

        self.assertIn("no release file", str(raised.exception))

    def test_a_missing_launcher_fails_through_the_shared_smoke_check(self) -> None:
        # Reusing the smoke runner's launcher check keeps one answer to "is this a real
        # installed image" instead of two that can disagree.
        write_image(self.root, with_launcher=False)

        with self.assertRaises(DesktopInstalledImageSmokeError):
            verify_installed_package(self.root, platform="linux")

    def test_release_values_are_read_with_quotes_stripped(self) -> None:
        release = runtime_release_file(self.root, "linux")
        release.parent.mkdir(parents=True, exist_ok=True)
        write_image(self.root, with_release=False)
        release.write_text(
            f'IMPLEMENTOR="Eclipse Adoptium"\n'
            f'JAVA_VERSION="{EXPECTED_JAVA_VERSION}"\n'
            f'MODULES="{" ".join(EXPECTED_MODULES)}"\n'
            "OS_ARCH=x86_64\n",
            encoding="utf-8",
        )

        verification = verify_installed_package(self.root, platform="linux")

        self.assertEqual(EXPECTED_JAVA_VERSION, verification["java_version"])

    def test_the_vendor_is_reported_as_unverifiable_rather_than_as_verified(self) -> None:
        # The packaged jlink image records no IMPLEMENTOR, so this gate genuinely cannot
        # establish vendor. Saying so explicitly matters more than it looks: a release
        # record that omitted the field would read as though provenance had been checked
        # end to end. Vendor is verified at build time by KaniPackagingJdk instead.
        write_image(self.root)

        verification = verify_installed_package(self.root, platform="linux")

        self.assertIsNone(verification["runtime_vendor"])
        self.assertIn("no IMPLEMENTOR", str(verification["runtime_vendor_note"]))
        self.assertIn("KaniPackagingJdk", str(verification["runtime_vendor_note"]))
        self.assertIn("unverifiable-from-image", format_report(verification))

    def test_a_vendor_claim_in_the_image_is_not_treated_as_evidence(self) -> None:
        # If a future JDK build did stamp IMPLEMENTOR into the jlink image, this gate
        # must not start silently depending on it -- an attacker-supplied or hand-edited
        # `release` file is the easiest thing in the artifact to forge, and the honest
        # source is the building JDK.
        write_image(self.root, with_release=False)
        release = runtime_release_file(self.root, "linux")
        release.parent.mkdir(parents=True, exist_ok=True)
        release.write_text(
            'IMPLEMENTOR="Definitely Not Adoptium"\n'
            f'JAVA_VERSION="{EXPECTED_JAVA_VERSION}"\n'
            f'MODULES="{" ".join(EXPECTED_MODULES)}"\n',
            encoding="utf-8",
        )

        verification = verify_installed_package(self.root, platform="linux")

        self.assertIsNone(verification["runtime_vendor"])

    def test_the_cli_writes_a_release_record_and_reports_failure_as_nonzero(self) -> None:
        write_image(self.root)
        record = self.root / "verification.json"

        # Captured so this test does not print a verification report into the Gradle
        # gate's log, where it would read as the real gate's output.
        report = io.StringIO()
        with contextlib.redirect_stdout(report):
            exit_code = main(
                ["--image-root", str(self.root), "--json-out", str(record)],
            )

        self.assertEqual(0, exit_code)
        self.assertIn(EXPECTED_JAVA_VERSION, report.getvalue())
        recorded = json.loads(record.read_text(encoding="utf-8"))
        self.assertEqual(EXPECTED_JAVA_VERSION, recorded["java_version"])
        self.assertIsNone(recorded["runtime_vendor"])

        with tempfile.TemporaryDirectory() as directory:
            broken = write_image(Path(directory), java_version="21.0.11")
            failure = io.StringIO()
            with contextlib.redirect_stderr(failure):
                self.assertEqual(1, main(["--image-root", str(broken)]))
            self.assertIn("21.0.11", failure.getvalue())


class PinnedContractLockstepTest(unittest.TestCase):
    """The Python copies of the pins must match their Kotlin sources.

    This gate must be runnable against a downloaded artifact with no repository
    present, so it cannot parse Kotlin at verification time. The copies are therefore
    checked here instead -- a pin bumped on one side and not the other fails this test
    rather than shipping a gate that verifies the previous release's contract.
    """

    def test_the_expected_java_version_matches_the_build_side_pin(self) -> None:
        source = PACKAGING_JDK.read_text(encoding="utf-8")
        self.assertIn(f'JAVA_VERSION: String = "{EXPECTED_JAVA_VERSION}"', source)

    def test_every_required_module_is_expected_in_the_image(self) -> None:
        source = DESKTOP_IDENTITY.read_text(encoding="utf-8")
        body = source.split("object KaniDesktopRuntimeModules", maxsplit=1)[1]
        # Bounded to the `listOf(...)` itself. Reading to end-of-file instead swept up
        # the `require` messages in KaniDesktopPackageVersions below it, and the test
        # failed for a reason that had nothing to do with modules.
        listing = body.split("listOf(", maxsplit=1)[1].split(")", maxsplit=1)[0]
        declared = set(re.findall(r'"([^"]+)"', listing))
        self.assertTrue(declared)
        self.assertFalse(
            declared - set(EXPECTED_MODULES),
            "KaniDesktopRuntimeModules requires modules this gate does not expect",
        )


if __name__ == "__main__":
    unittest.main()
