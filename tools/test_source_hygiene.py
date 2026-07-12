#!/usr/bin/env python3
"""Repo source-hygiene guards.

Ensures tracked text sources never contain raw control bytes. A literal NUL
(or other C0 control byte) inside a Kotlin/Gradle/YAML/Python source makes the
file classify as binary: `git diff` renders "Binary files differ" and
grep/ripgrep and file-reading tools skip it, so a correctness-critical change
can become invisible to code review (see Goal 41).
"""

from __future__ import annotations

from collections.abc import Iterable
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# C0 control bytes that must never appear in tracked text sources, excluding the
# ordinary whitespace bytes TAB (0x09), LF (0x0A), and CR (0x0D).
FORBIDDEN_CONTROL_BYTES = (
    set(range(0x00, 0x09))
    | {0x0B, 0x0C}
    | set(range(0x0E, 0x20))
)

# Extensions whose tracked files are checked for control bytes.
TEXT_SOURCE_GLOBS = ("*.kt", "*.kts", "*.yml", "*.yaml", "*.py")


def _tracked_files(globs: tuple[str, ...], *, root: Path = ROOT) -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z", "--", *globs],
        cwd=root,
        check=True,
        capture_output=True,
    )
    names = result.stdout.split(b"\x00")
    return [root / name.decode("utf-8") for name in names if name]


def _forbidden_control_bytes(data: bytes) -> list[int]:
    return sorted(FORBIDDEN_CONTROL_BYTES.intersection(data))


def _control_byte_offenders(paths: Iterable[Path], *, root: Path = ROOT) -> list[str]:
    offenders: list[str] = []
    for path in paths:
        if not path.is_file():
            continue
        found = _forbidden_control_bytes(path.read_bytes())
        if found:
            rel = path.relative_to(root)
            hexes = ", ".join(f"0x{byte:02X}" for byte in found)
            offenders.append(f"{rel}: {hexes}")
    return offenders


class ConventionPluginLazyExcludesTest(unittest.TestCase):
    """Goal 51: the convention plugin must resolve coverageExcludes lazily.

    Reading `coverageExcludes.orNull` eagerly inside the `fileTree { }` action at
    plugin-apply time captured an empty list (module scripts add excludes only
    after the plugin applies), making every exclude a silent no-op. Guard that
    the excludes are wired through a `provider { }` and not read eagerly with
    `.orNull` inside a fileTree configuration action.
    """

    PLUGIN = ROOT / "build-logic/src/main/kotlin/kani.kotlin-library-conventions.gradle.kts"

    def test_excludes_resolved_through_a_provider(self) -> None:
        text = self.PLUGIN.read_text(encoding="utf-8")
        self.assertIn("coverageClassDirectories", text)
        self.assertIn("provider {", text)
        self.assertNotIn(
            "coverageExcludes.orNull?.forEach",
            text,
            "eager coverageExcludes read regressed — excludes will be a silent no-op",
        )

    def test_effective_excludes_probe_task_exists(self) -> None:
        text = self.PLUGIN.read_text(encoding="utf-8")
        self.assertIn('tasks.register("printCoverageExcludes")', text)


class SourceControlByteHygieneTest(unittest.TestCase):
    def test_forbidden_control_bytes_are_reported(self) -> None:
        data = bytes(sorted(FORBIDDEN_CONTROL_BYTES))
        self.assertEqual(sorted(FORBIDDEN_CONTROL_BYTES), _forbidden_control_bytes(data))

    def test_unicode_and_ordinary_whitespace_are_accepted(self) -> None:
        data = "name: 日本語 café ✅\n\tcommand: pass\r\n".encode("utf-8")
        self.assertEqual([], _forbidden_control_bytes(data))

    def test_tracked_workflow_yaml_and_python_are_scanned_without_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tracked = {
                ".github/workflows/check.yml": b"name: check\x00\n",
                ".github/workflows/nightly.yaml": "name: 夜間\n".encode("utf-8"),
                "tools/helper.py": b"print('ok')\x1f\n",
                "src/Main.kt": "val greeting = \"こんにちは\"\n".encode("utf-8"),
                "assets/blob.png": b"\x00\x01\x02binary",
            }
            untracked = {
                "build/generated.py": b"generated = '\x00'\n",
                "loop.md": b"loop\x00\n",
                "plans/learning.md": b"plan\x00\n",
            }
            for relative, data in tracked.items() | untracked.items():
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)

            subprocess.run(["git", "init", "-q"], cwd=root, check=True, capture_output=True)
            (root / ".git/internal.py").write_bytes(b"metadata\x00\n")
            subprocess.run(
                ["git", "add", "--", *tracked],
                cwd=root,
                check=True,
                capture_output=True,
            )

            paths = _tracked_files(TEXT_SOURCE_GLOBS, root=root)
            relative_paths = {path.relative_to(root).as_posix() for path in paths}
            self.assertEqual(
                {
                    ".github/workflows/check.yml",
                    ".github/workflows/nightly.yaml",
                    "src/Main.kt",
                    "tools/helper.py",
                },
                relative_paths,
            )
            self.assertEqual(
                [
                    ".github/workflows/check.yml: 0x00",
                    "tools/helper.py: 0x1F",
                ],
                _control_byte_offenders(paths, root=root),
            )

    def test_no_control_bytes_in_tracked_text_sources(self) -> None:
        offenders = _control_byte_offenders(_tracked_files(TEXT_SOURCE_GLOBS))
        self.assertEqual(
            [],
            offenders,
            "tracked text sources contain forbidden control bytes:\n"
            + "\n".join(offenders),
        )


if __name__ == "__main__":
    unittest.main()
