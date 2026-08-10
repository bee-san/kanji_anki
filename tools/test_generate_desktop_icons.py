from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

from tools import generate_desktop_icons as icons


class DesktopIconVerificationTest(unittest.TestCase):
    def test_verification_accepts_matching_deterministic_render(self) -> None:
        with self.icon_fixture() as fixture:
            source, output_directory, manifest_path = fixture

            icons.verify_icon_set(
                source=source,
                output_directory=output_directory,
                manifest_path=manifest_path,
                renderer=self.copy_committed_outputs,
            )

    def test_verification_rejects_stale_outputs_after_source_change(self) -> None:
        with self.icon_fixture() as fixture:
            source, output_directory, manifest_path = fixture
            source.write_text(
                source.read_text(encoding="utf-8").replace(
                    'fill="#fff8f2"',
                    'fill="#fff7ed"',
                    1,
                ),
                encoding="utf-8",
            )
            manifest_path.write_text(
                json.dumps(
                    icons.output_manifest(source, output_directory),
                    indent=2,
                    sort_keys=True,
                ) + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                icons.IconVerificationError,
                "do not match a deterministic render",
            ):
                icons.verify_icon_set(
                    source=source,
                    output_directory=output_directory,
                    manifest_path=manifest_path,
                    renderer=self.render_changed_source,
                )

    @contextmanager
    def icon_fixture(self) -> Iterator[tuple[Path, Path, Path]]:
        with tempfile.TemporaryDirectory(prefix="kani-icon-test-") as temporary:
            root = Path(temporary)
            source = root / "branding/kani-app-icon.svg"
            output_directory = root / "icons"
            source.parent.mkdir(parents=True)
            output_directory.mkdir(parents=True)
            shutil.copyfile(icons.CANONICAL_SOURCE, source)
            for filename in (*icons.OUTPUT_FILENAMES, "icon-manifest.json"):
                shutil.copyfile(
                    icons.OUTPUT_DIRECTORY / filename,
                    output_directory / filename,
                )
            yield (
                source,
                output_directory,
                output_directory / "icon-manifest.json",
            )

    @staticmethod
    def copy_committed_outputs(source: Path, output_directory: Path) -> None:
        del source
        for filename in icons.OUTPUT_FILENAMES:
            shutil.copyfile(icons.OUTPUT_DIRECTORY / filename, output_directory / filename)

    @staticmethod
    def render_changed_source(source: Path, output_directory: Path) -> None:
        DesktopIconVerificationTest.copy_committed_outputs(source, output_directory)
        png_path = output_directory / "kani.png"
        png_bytes = bytearray(png_path.read_bytes())
        png_bytes[-1] ^= 1
        png_path.write_bytes(png_bytes)


if __name__ == "__main__":
    unittest.main()
