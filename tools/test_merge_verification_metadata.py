from __future__ import annotations

import hashlib
import json
import os
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from tools import merge_verification_metadata as merger


NS = merger.VERIFICATION_NAMESPACE
XSI = merger.XSI_NAMESPACE
ET.register_namespace("", NS)
ET.register_namespace("xsi", XSI)


class VerificationMetadataMergeTest(unittest.TestCase):
    def test_repository_pins_verification_metadata_to_lf_checkouts(self) -> None:
        attributes = (
            Path(__file__).resolve().parents[1] / ".gitattributes"
        ).read_text(encoding="utf-8").splitlines()
        self.assertIn(
            "gradle/verification-metadata.xml text eol=lf",
            attributes,
        )

    def test_merges_additive_platform_metadata_in_canonical_order(self) -> None:
        with self.fixture() as paths:
            self.add_artifact(
                paths["linux"],
                group="z.group",
                name="runtime",
                version="1",
                artifact="runtime-linux.jar",
                checksum="2" * 64,
            )
            self.add_artifact(
                paths["windows"],
                group="a.group",
                name="runtime",
                version="1",
                artifact="runtime-windows.jar",
                checksum="3" * 64,
            )
            self.add_artifact(
                paths["macos"],
                group="z.group",
                name="runtime",
                version="1",
                artifact="runtime-macos.jar",
                checksum="1" * 64,
            )

            result = self.merge_fixture(paths)

            self.assertEqual(
                [
                    ("a.group", "runtime", "1"),
                    ("base.group", "base", "1"),
                    ("z.group", "runtime", "1"),
                ],
                [component.key for component in result.merged.components],
            )
            output = paths["output"].read_text(encoding="utf-8")
            self.assertLess(output.index('group="a.group"'), output.index('group="base.group"'))
            self.assertLess(output.index("runtime-linux.jar"), output.index("runtime-macos.jar"))
            self.assertEqual(
                result.merged,
                merger.parse_verification_metadata(paths["output"]),
            )

            manifest = json.loads(paths["manifest"].read_text(encoding="utf-8"))
            self.assertEqual(
                {"components": 2, "artifacts": 3, "sha256_checksums": 3},
                manifest["merged"]["additions"]["counts"],
            )
            self.assertIn(
                "+ artifact a.group:runtime:1/runtime-windows.jar",
                paths["review"].read_text(encoding="utf-8"),
            )
            diff = paths["diff"].read_text(encoding="utf-8")
            self.assertIn("--- baseline/verification-metadata.xml", diff)
            self.assertIn("+++ merged/verification-metadata.xml", diff)

    def test_rejects_every_baseline_deletion_level(self) -> None:
        mutators = {
            "component": lambda root: root.find(f"{merger.NAMESPACED}components").remove(
                root.find(f"{merger.NAMESPACED}components")[0]
            ),
            "artifact": lambda root: root.find(
                f".//{merger.NAMESPACED}component"
            ).remove(root.find(f".//{merger.NAMESPACED}artifact")),
            "checksum": lambda root: root.find(
                f".//{merger.NAMESPACED}artifact"
            ).remove(root.find(f".//{merger.NAMESPACED}sha256")),
        }
        for level, mutate in mutators.items():
            with self.subTest(level=level), self.fixture() as paths:
                self.mutate(paths["linux"], mutate)
                expected = (
                    "has no checksums"
                    if level == "checksum"
                    else f"baseline {level} deleted"
                )
                with self.assertRaisesRegex(
                    merger.VerificationMetadataError,
                    expected,
                ):
                    self.merge_fixture(paths)

    def test_rejects_baseline_checksum_attribute_change(self) -> None:
        with self.fixture() as paths:
            self.mutate(
                paths["windows"],
                lambda root: root.find(f".//{merger.NAMESPACED}sha256").set(
                    "origin",
                    "changed origin",
                ),
            )
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "baseline checksum attributes changed",
            ):
                self.merge_fixture(paths)

    def test_rejects_configuration_schema_and_security_changes(self) -> None:
        mutations = {
            "verify metadata": lambda root: self._set_text(
                root,
                "verify-metadata",
                "false",
            ),
            "verify signatures": lambda root: self._set_text(
                root,
                "verify-signatures",
                "true",
            ),
            "trusted artifacts": lambda root: ET.SubElement(
                root.find(f"{merger.NAMESPACED}configuration"),
                f"{merger.NAMESPACED}trusted-artifacts",
            ),
            "ignored keys": lambda root: ET.SubElement(
                root.find(f"{merger.NAMESPACED}configuration"),
                f"{merger.NAMESPACED}ignored-keys",
            ),
            "schema": lambda root: root.set(
                f"{{{XSI}}}schemaLocation",
                f"{NS} {NS}/dependency-verification-9.9.xsd",
            ),
        }
        for label, mutate in mutations.items():
            with self.subTest(label=label), self.fixture() as paths:
                self.mutate(paths["macos"], mutate)
                with self.assertRaises(merger.VerificationMetadataError):
                    self.merge_fixture(paths)

    def test_rejects_conflicting_sha_for_same_added_artifact(self) -> None:
        with self.fixture() as paths:
            for platform, value in (
                ("linux", "a" * 64),
                ("windows", "b" * 64),
            ):
                self.add_artifact(
                    paths[platform],
                    group="native.group",
                    name="runtime",
                    version="1",
                    artifact="runtime.jar",
                    checksum=value,
                )
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "conflicting SHA-256 additions",
            ):
                self.merge_fixture(paths)

    def test_rejects_non_sha256_addition(self) -> None:
        with self.fixture() as paths:
            self.add_artifact(
                paths["linux"],
                group="native.group",
                name="runtime",
                version="1",
                artifact="runtime.jar",
                checksum="a" * 64,
                algorithm="sha512",
            )
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "non-sha256 checksum addition",
            ):
                self.merge_fixture(paths)

    def test_requires_exactly_one_named_input_for_every_platform(self) -> None:
        with self.fixture() as paths:
            paths["windows"].unlink()
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "missing required platform input verification-metadata-windows.xml",
            ):
                self.merge_fixture(paths)

        with self.fixture() as paths:
            duplicate = paths["inputs"] / "duplicate" / paths["linux"].name
            duplicate.parent.mkdir()
            duplicate.write_bytes(paths["linux"].read_bytes())
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "expected exactly one verification-metadata-linux.xml",
            ):
                self.merge_fixture(paths)

        with self.fixture() as paths:
            unexpected = paths["inputs"] / "verification-metadata-freebsd.xml"
            unexpected.write_bytes(paths["linux"].read_bytes())
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "unexpected platform verification metadata",
            ):
                self.merge_fixture(paths)

    def test_merge_is_idempotent_and_independent_of_xml_input_order(self) -> None:
        with self.fixture() as paths:
            for platform, artifact, checksum in (
                ("linux", "z.jar", "f" * 64),
                ("windows", "a.jar", "e" * 64),
                ("macos", "m.jar", "d" * 64),
            ):
                self.add_artifact(
                    paths[platform],
                    group="shared.group",
                    name="runtime",
                    version="1",
                    artifact=artifact,
                    checksum=checksum,
                )
                self.reverse_components_and_artifacts(paths[platform])

            self.merge_fixture(paths)
            first_outputs = {
                name: paths[name].read_bytes()
                for name in ("output", "manifest", "review", "diff")
            }
            self.merge_fixture(paths)
            second_outputs = {
                name: paths[name].read_bytes()
                for name in ("output", "manifest", "review", "diff")
            }
            self.assertEqual(first_outputs, second_outputs)

    def test_rejects_output_overwriting_pristine_baseline(self) -> None:
        with self.fixture() as paths:
            paths["output"] = paths["baseline"]
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "outputs must be separate",
            ):
                self.merge_fixture(paths)

    def test_rejects_hardlinked_output_alias_without_mutating_baseline(self) -> None:
        with self.fixture() as paths:
            baseline_before = paths["baseline"].read_bytes()
            os.link(paths["baseline"], paths["output"])
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "must not alias",
            ):
                self.merge_fixture(paths)
            self.assertEqual(baseline_before, paths["baseline"].read_bytes())

    def test_rejects_non_utf8_entity_documents_before_xml_expansion(self) -> None:
        with self.fixture() as paths:
            malicious = f"""<?xml version="1.0" encoding="UTF-16"?>
<!DOCTYPE verification-metadata [
  <!ENTITY expanded "unexpected">
]>
<verification-metadata xmlns="{NS}" xmlns:xsi="{XSI}" xsi:schemaLocation="{NS} {NS}/dependency-verification-1.3.xsd">
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>&expanded;</components>
</verification-metadata>
"""
            paths["macos"].write_bytes(malicious.encode("utf-16"))
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "must be UTF-8",
            ):
                self.merge_fixture(paths)

    def test_outputs_are_lf_bytes_and_manifest_hashes_exact_xml(self) -> None:
        with self.fixture() as paths:
            self.add_artifact(
                paths["windows"],
                group="native.group",
                name="runtime",
                version="1",
                artifact="runtime-windows.jar",
                checksum="a" * 64,
            )
            self.merge_fixture(paths)

            for name in ("output", "manifest", "review", "diff"):
                with self.subTest(name=name):
                    self.assertNotIn(b"\r\n", paths[name].read_bytes())
            manifest = json.loads(paths["manifest"].read_text(encoding="utf-8"))
            self.assertEqual(
                hashlib.sha256(paths["output"].read_bytes()).hexdigest(),
                manifest["merged"]["sha256"],
            )

    def test_expected_baseline_digest_pins_pristine_input(self) -> None:
        with self.fixture() as paths:
            with self.assertRaisesRegex(
                merger.VerificationMetadataError,
                "pristine baseline SHA-256 mismatch",
            ):
                self.merge_fixture(paths, expected_baseline_sha256="0" * 64)

    def fixture(self):
        temporary = tempfile.TemporaryDirectory(prefix="kani-metadata-merge-test-")
        root = Path(temporary.name)
        inputs = root / "artifacts"
        inputs.mkdir()
        baseline = root / "verification-metadata.xml"
        baseline.write_text(self.base_xml(), encoding="utf-8")
        paths: dict[str, Path | tempfile.TemporaryDirectory[str]] = {
            "temporary": temporary,
            "inputs": inputs,
            "baseline": baseline,
            "output": inputs / "verification-metadata-merged.xml",
            "manifest": inputs / "merge-manifest.json",
            "review": inputs / "review.md",
            "diff": inputs / "merged.diff",
        }
        for platform, filename in merger.EXPECTED_PLATFORM_FILENAMES.items():
            platform_directory = inputs / platform
            platform_directory.mkdir()
            destination = platform_directory / filename
            destination.write_bytes(baseline.read_bytes())
            paths[platform] = destination
        return _FixtureContext(paths)

    def merge_fixture(
        self,
        paths,
        *,
        expected_baseline_sha256: str | None = None,
    ) -> merger.MergeResult:
        return merger.merge_files(
            baseline_path=paths["baseline"],
            input_directory=paths["inputs"],
            output_path=paths["output"],
            manifest_path=paths["manifest"],
            review_summary_path=paths["review"],
            diff_path=paths["diff"],
            expected_baseline_sha256=expected_baseline_sha256,
        )

    @staticmethod
    def base_xml() -> str:
        return f"""<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="{NS}" xmlns:xsi="{XSI}" xsi:schemaLocation="{NS} {NS}/dependency-verification-1.3.xsd">
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="base.group" name="base" version="1">
         <artifact name="base-1.jar">
            <sha256 value="{'0' * 64}" origin="Reviewed baseline">
               <also-trust value="{'9' * 64}"/>
            </sha256>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""

    @staticmethod
    def mutate(path: Path, mutation) -> None:
        tree = ET.parse(path)
        mutation(tree.getroot())
        tree.write(path, encoding="UTF-8", xml_declaration=True)

    @staticmethod
    def _set_text(root: ET.Element, name: str, value: str) -> None:
        root.find(f".//{merger.NAMESPACED}{name}").text = value

    @staticmethod
    def add_artifact(
        path: Path,
        *,
        group: str,
        name: str,
        version: str,
        artifact: str,
        checksum: str,
        algorithm: str = "sha256",
    ) -> None:
        tree = ET.parse(path)
        root = tree.getroot()
        components = root.find(f"{merger.NAMESPACED}components")
        component = next(
            (
                candidate
                for candidate in components
                if candidate.attrib
                == {"group": group, "name": name, "version": version}
            ),
            None,
        )
        if component is None:
            component = ET.SubElement(
                components,
                f"{merger.NAMESPACED}component",
                {"group": group, "name": name, "version": version},
            )
        artifact_element = ET.SubElement(
            component,
            f"{merger.NAMESPACED}artifact",
            {"name": artifact},
        )
        ET.SubElement(
            artifact_element,
            f"{merger.NAMESPACED}{algorithm}",
            {"value": checksum, "origin": "Generated by Gradle"},
        )
        tree.write(path, encoding="UTF-8", xml_declaration=True)

    @staticmethod
    def reverse_components_and_artifacts(path: Path) -> None:
        tree = ET.parse(path)
        root = tree.getroot()
        components = root.find(f"{merger.NAMESPACED}components")
        components[:] = reversed(list(components))
        for component in components:
            component[:] = reversed(list(component))
        tree.write(path, encoding="UTF-8", xml_declaration=True)


class _FixtureContext:
    def __init__(self, paths):
        self.paths = paths

    def __enter__(self):
        return self.paths

    def __exit__(self, exc_type, exc_value, traceback):
        self.paths["temporary"].cleanup()
        return False


if __name__ == "__main__":
    unittest.main()
