from __future__ import annotations

import hashlib
import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "ci/scripts/capture_verification_metadata.py"
SPEC = importlib.util.spec_from_file_location("capture_verification_metadata", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
CAPTURE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CAPTURE
SPEC.loader.exec_module(CAPTURE)


class VerificationMetadataArtifactTest(unittest.TestCase):
    def test_snapshot_and_capture_preserve_full_metadata_and_record_diff(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            checked_in = root / "verification-metadata.xml"
            baseline = root / "baseline.xml"
            output = root / "artifact"
            checked_in.write_text("<verification-metadata>old</verification-metadata>\n")
            CAPTURE.snapshot(checked_in, baseline)
            checked_in.write_text("<verification-metadata>new</verification-metadata>\n")

            metadata, diff, manifest = CAPTURE.capture(
                baseline=baseline,
                metadata=checked_in,
                host_id="windows",
                runner_label="windows-2025",
                runner_os="Windows",
                runner_arch="X64",
                event_name="push",
                repository="bee-san/kanji_anki",
                ref="refs/heads/desktop/support",
                commit_sha="a" * 40,
                mode="bootstrap-write",
                output_directory=output,
            )

            self.assertEqual(checked_in.read_bytes(), metadata.read_bytes())
            self.assertIn("-<verification-metadata>old", diff.read_text())
            self.assertIn("+<verification-metadata>new", diff.read_text())
            document = json.loads(manifest.read_text())
            self.assertEqual("windows", document["host_id"])
            self.assertEqual("windows-2025", document["runner_label"])
            self.assertEqual("bootstrap-write", document["mode"])
            self.assertTrue(document["changed"])
            self.assertEqual(
                hashlib.sha256(checked_in.read_bytes()).hexdigest(),
                document["metadata_sha256"],
            )

    def test_unchanged_strict_capture_is_explicit(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            metadata = root / "metadata.xml"
            baseline = root / "baseline.xml"
            metadata.write_text("<verification-metadata />\n")
            baseline.write_bytes(metadata.read_bytes())

            _, diff, manifest = CAPTURE.capture(
                baseline=baseline,
                metadata=metadata,
                host_id="linux",
                runner_label="ubuntu-24.04",
                runner_os="Linux",
                runner_arch="X64",
                event_name="pull_request",
                repository="bee-san/kanji_anki",
                ref="refs/pull/1/merge",
                commit_sha="b" * 40,
                mode="strict",
                output_directory=root / "artifact",
            )

            self.assertEqual("", diff.read_text())
            self.assertFalse(json.loads(manifest.read_text())["changed"])

    def test_windows_crlf_metadata_produces_a_canonical_lf_diff(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            baseline = root / "baseline.xml"
            metadata = root / "metadata.xml"
            baseline.write_bytes(
                b"<verification-metadata>\n"
                b"  <components />\n"
                b"</verification-metadata>\n"
            )
            metadata.write_bytes(
                b"<verification-metadata>\r\n"
                b"  <components>\r\n"
                b"    <component group=\"org.example\" />\r\n"
                b"  </components>\r\n"
                b"</verification-metadata>\r\n"
            )

            _, diff, _ = CAPTURE.capture(
                baseline=baseline,
                metadata=metadata,
                host_id="windows",
                runner_label="windows-2025",
                runner_os="Windows",
                runner_arch="X64",
                event_name="push",
                repository="bee-san/kanji_anki",
                ref="refs/heads/desktop/support",
                commit_sha="a" * 40,
                mode="bootstrap-write",
                output_directory=root / "artifact",
            )

            diff_bytes = diff.read_bytes()
            self.assertNotIn(b"\r", diff_bytes)
            self.assertIn(b'+    <component group="org.example" />\n', diff_bytes)

    def test_unknown_host_and_mode_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            metadata = root / "metadata.xml"
            metadata.write_text("<verification-metadata />\n")
            kwargs = {
                "baseline": metadata,
                "metadata": metadata,
                "host_id": "freebsd",
                "runner_label": "unknown",
                "runner_os": "FreeBSD",
                "runner_arch": "X64",
                "event_name": "push",
                "repository": "bee-san/kanji_anki",
                "ref": "refs/heads/desktop/support",
                "commit_sha": "c" * 40,
                "mode": "strict",
                "output_directory": root / "artifact",
            }
            with self.assertRaisesRegex(ValueError, "unsupported host id"):
                CAPTURE.capture(**kwargs)

            kwargs["host_id"] = "linux"
            kwargs["mode"] = "disabled"
            with self.assertRaisesRegex(ValueError, "unsupported verification mode"):
                CAPTURE.capture(**kwargs)


if __name__ == "__main__":
    unittest.main()
