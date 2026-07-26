from __future__ import annotations

import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
CAPTURE_SCRIPT = ROOT / "ci/scripts/capture_verification_metadata.py"
VALIDATE_SCRIPT = ROOT / "ci/scripts/validate_verification_metadata_artifacts.py"
SCOPE_SCRIPT = ROOT / "ci/scripts/assert_verification_metadata_scope.py"


def _load(name: str, path: pathlib.Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


CAPTURE = _load("capture_metadata_for_validation_test", CAPTURE_SCRIPT)
VALIDATE = _load("validate_verification_metadata_artifacts", VALIDATE_SCRIPT)
SCOPE = _load("assert_verification_metadata_scope", SCOPE_SCRIPT)


class VerificationMetadataArtifactValidationTest(unittest.TestCase):
    def make_artifacts(self, root: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path]:
        baseline = root / "baseline.xml"
        baseline.write_text("<verification-metadata>baseline</verification-metadata>\n")
        artifacts = root / "artifacts"
        for host_id, (label, arch, runner_os) in VALIDATE.HOST_CONTRACT.items():
            metadata = root / f"{host_id}.xml"
            metadata.write_text(
                f"<verification-metadata>{host_id}</verification-metadata>\n"
            )
            CAPTURE.capture(
                baseline=baseline,
                metadata=metadata,
                host_id=host_id,
                runner_label=label,
                runner_os=runner_os,
                runner_arch=arch,
                event_name="push",
                repository="bee-san/kanji_anki",
                ref="refs/heads/desktop/support",
                commit_sha="d" * 40,
                mode="bootstrap-write",
                output_directory=artifacts / f"verification-metadata-{host_id}",
            )
        return baseline, artifacts

    def validate(self, baseline: pathlib.Path, artifacts: pathlib.Path) -> None:
        VALIDATE.validate_artifacts(
            input_directory=artifacts,
            baseline=baseline,
            expected_commit_sha="d" * 40,
            expected_repository="bee-san/kanji_anki",
            expected_ref="refs/heads/desktop/support",
            expected_event_name="push",
            expected_mode="bootstrap-write",
        )

    def test_three_bound_host_artifacts_validate(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            baseline, artifacts = self.make_artifacts(pathlib.Path(temp))
            manifests = VALIDATE.validate_artifacts(
                input_directory=artifacts,
                baseline=baseline,
                expected_commit_sha="d" * 40,
                expected_repository="bee-san/kanji_anki",
                expected_ref="refs/heads/desktop/support",
                expected_event_name="push",
                expected_mode="bootstrap-write",
            )
            self.assertEqual({"linux", "windows", "macos"}, set(manifests))

    def test_windows_crlf_metadata_and_lf_review_diff_validate_end_to_end(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            baseline, artifacts = self.make_artifacts(root)
            windows_metadata = root / "windows-crlf.xml"
            windows_metadata.write_bytes(
                b"<verification-metadata>\r\n"
                b"  <component>windows</component>\r\n"
                b"</verification-metadata>\r\n"
            )
            label, arch, runner_os = VALIDATE.HOST_CONTRACT["windows"]
            CAPTURE.capture(
                baseline=baseline,
                metadata=windows_metadata,
                host_id="windows",
                runner_label=label,
                runner_os=runner_os,
                runner_arch=arch,
                event_name="push",
                repository="bee-san/kanji_anki",
                ref="refs/heads/desktop/support",
                commit_sha="d" * 40,
                mode="bootstrap-write",
                output_directory=artifacts / "verification-metadata-windows",
            )

            diff = next(artifacts.rglob("verification-metadata-windows.diff"))
            self.assertNotIn(b"\r", diff.read_bytes())
            self.validate(baseline, artifacts)

    def test_mutated_xml_is_rejected_even_when_artifact_name_is_valid(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            baseline, artifacts = self.make_artifacts(pathlib.Path(temp))
            xml = next(artifacts.rglob("verification-metadata-windows.xml"))
            xml.write_text("<verification-metadata>tampered</verification-metadata>\n")
            with self.assertRaisesRegex(ValueError, "metadata_sha256"):
                self.validate(baseline, artifacts)

    def test_manifest_disagreement_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            baseline, artifacts = self.make_artifacts(pathlib.Path(temp))
            manifest_path = next(
                artifacts.rglob("verification-metadata-macos.manifest.json")
            )
            manifest = json.loads(manifest_path.read_text())
            manifest["commit_sha"] = "e" * 40
            manifest_path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "commit_sha"):
                self.validate(baseline, artifacts)

    def test_wrong_runner_os_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            baseline, artifacts = self.make_artifacts(pathlib.Path(temp))
            manifest_path = next(
                artifacts.rglob("verification-metadata-windows.manifest.json")
            )
            manifest = json.loads(manifest_path.read_text())
            manifest["runner_os"] = "Linux"
            manifest_path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "runner_os"):
                self.validate(baseline, artifacts)

    def test_missing_host_and_mutated_diff_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            baseline, artifacts = self.make_artifacts(pathlib.Path(temp))
            next(artifacts.rglob("verification-metadata-linux.xml")).unlink()
            with self.assertRaisesRegex(ValueError, "host XML set mismatch"):
                self.validate(baseline, artifacts)

        with tempfile.TemporaryDirectory() as temp:
            baseline, artifacts = self.make_artifacts(pathlib.Path(temp))
            diff = next(artifacts.rglob("verification-metadata-linux.diff"))
            diff.write_text("not the generated diff\n")
            with self.assertRaisesRegex(ValueError, "diff does not match"):
                self.validate(baseline, artifacts)

        with tempfile.TemporaryDirectory() as temp:
            baseline, artifacts = self.make_artifacts(pathlib.Path(temp))
            diff = next(artifacts.rglob("verification-metadata-windows.diff"))
            diff.write_bytes(diff.read_bytes().replace(b"\n", b"\r\n"))
            with self.assertRaisesRegex(ValueError, "diff does not match"):
                self.validate(baseline, artifacts)

    def test_tracked_change_scope_rejects_every_other_path(self) -> None:
        self.assertEqual(
            [],
            SCOPE.unexpected_paths(
                ["gradle/verification-metadata.xml"],
            ),
        )
        scope_source = SCOPE_SCRIPT.read_text(encoding="utf-8")
        self.assertIn('"ls-files"', scope_source)
        self.assertIn('"--others"', scope_source)
        self.assertIn('"--exclude-standard"', scope_source)
        self.assertEqual(
            ["build.gradle.kts", "desktop-app/build.gradle.kts"],
            SCOPE.unexpected_paths(
                [
                    "gradle/verification-metadata.xml",
                    "desktop-app/build.gradle.kts",
                    "build.gradle.kts",
                ],
            ),
        )


if __name__ == "__main__":
    unittest.main()
