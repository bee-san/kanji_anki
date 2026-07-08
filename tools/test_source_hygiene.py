#!/usr/bin/env python3
"""Repo source-hygiene guards.

Ensures tracked text sources never contain raw control bytes. A literal NUL
(or other C0 control byte) inside a Kotlin/Gradle/YAML/Python source makes the
file classify as binary: `git diff` renders "Binary files differ" and
grep/ripgrep and file-reading tools skip it, so a correctness-critical change
can become invisible to code review (see Goal 41).
"""

from __future__ import annotations

import subprocess
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


def _tracked_files(globs: tuple[str, ...]) -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z", "--", *globs],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    names = result.stdout.split(b"\x00")
    return [ROOT / name.decode("utf-8") for name in names if name]


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
    def test_no_control_bytes_in_tracked_text_sources(self) -> None:
        offenders: list[str] = []
        for path in _tracked_files(TEXT_SOURCE_GLOBS):
            if not path.is_file():
                continue
            data = path.read_bytes()
            found = sorted({b for b in data if b in FORBIDDEN_CONTROL_BYTES})
            if found:
                rel = path.relative_to(ROOT)
                hexes = ", ".join(f"0x{b:02X}" for b in found)
                offenders.append(f"{rel}: {hexes}")
        self.assertEqual(
            [],
            offenders,
            "tracked text sources contain forbidden control bytes:\n"
            + "\n".join(offenders),
        )


if __name__ == "__main__":
    unittest.main()
