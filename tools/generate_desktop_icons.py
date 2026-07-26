#!/usr/bin/env python3
"""Generate and verify Kani's desktop package icons from one vector source."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from collections.abc import Callable
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
CANONICAL_SOURCE = REPO_ROOT / "branding/kani-app-icon.svg"
CANONICAL_SOURCE_REFERENCE = "branding/kani-app-icon.svg"
OUTPUT_DIRECTORY = REPO_ROOT / "desktop-app/src/main/packaging/icons"
MANIFEST_PATH = OUTPUT_DIRECTORY / "icon-manifest.json"
OUTPUT_FILENAMES = ("kani.png", "kani.ico", "kani.icns")
PNG_SIZES = (16, 24, 32, 48, 64, 128, 256, 512, 1024)
ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)
ICNS_SIZES = (16, 32, 48, 128, 256, 512, 1024)
EXPECTED_ICNS_CHUNKS = (
    "is32",
    "s8mk",
    "il32",
    "l8mk",
    "ih32",
    "h8mk",
    "it32",
    "t8mk",
    "ic08",
    "ic09",
    "ic10",
)
EXPECTED_TOOLS = {
    "imagemagick": "7.1.2-27",
    "optipng": "7.9.1",
    "png2icns": "0.8.1",
    "rsvg-convert": "2.62.3",
}
ALLOWED_SVG_ELEMENTS = {
    "circle",
    "ellipse",
    "path",
    "rect",
    "svg",
}


class IconVerificationError(RuntimeError):
    """Raised when a committed icon diverges from its canonical contract."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as input_file:
        for block in iter(lambda: input_file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def png_dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        raise IconVerificationError(f"{path} is not a PNG")
    return struct.unpack(">II", data[16:24])


def ico_sizes(path: Path) -> tuple[int, ...]:
    data = path.read_bytes()
    if len(data) < 6:
        raise IconVerificationError(f"{path} has a truncated ICO header")
    reserved, image_type, count = struct.unpack("<HHH", data[:6])
    if reserved != 0 or image_type != 1 or count == 0:
        raise IconVerificationError(f"{path} has an invalid ICO header")
    entries_end = 6 + count * 16
    if entries_end > len(data):
        raise IconVerificationError(f"{path} has truncated ICO entries")

    sizes = []
    for index in range(count):
        entry_offset = 6 + index * 16
        width_byte, height_byte, _, _, _, _, byte_count, image_offset = struct.unpack(
            "<BBBBHHII",
            data[entry_offset : entry_offset + 16],
        )
        width = width_byte or 256
        height = height_byte or 256
        if width != height:
            raise IconVerificationError(f"{path} contains a non-square ICO frame")
        if byte_count == 0 or image_offset + byte_count > len(data):
            raise IconVerificationError(f"{path} contains an invalid ICO frame")
        sizes.append(width)
    return tuple(sizes)


def icns_chunks(path: Path) -> tuple[str, ...]:
    data = path.read_bytes()
    if len(data) < 8 or data[:4] != b"icns":
        raise IconVerificationError(f"{path} has an invalid ICNS header")
    declared_length = struct.unpack(">I", data[4:8])[0]
    if declared_length != len(data):
        raise IconVerificationError(f"{path} has an invalid ICNS length")

    chunks = []
    offset = 8
    while offset < len(data):
        if offset + 8 > len(data):
            raise IconVerificationError(f"{path} has a truncated ICNS chunk")
        chunk_type = data[offset : offset + 4].decode("ascii")
        chunk_length = struct.unpack(">I", data[offset + 4 : offset + 8])[0]
        if chunk_length < 8 or offset + chunk_length > len(data):
            raise IconVerificationError(f"{path} has an invalid ICNS chunk")
        chunks.append(chunk_type)
        offset += chunk_length
    return tuple(chunks)


def verify_vector_source(path: Path) -> None:
    source_text = path.read_text(encoding="utf-8")
    lowered = source_text.lower()
    if "<image" in lowered or "data:" in lowered or "href=" in lowered:
        raise IconVerificationError("canonical SVG must not embed a raster or external asset")
    root = ET.fromstring(source_text)
    if root.attrib.get("viewBox") != "0 0 1024 1024":
        raise IconVerificationError("canonical SVG must use the 1024-square viewBox")
    element_names = {
        element.tag.rsplit("}", 1)[-1]
        for element in root.iter()
    }
    unexpected = element_names - ALLOWED_SVG_ELEMENTS
    if unexpected:
        raise IconVerificationError(
            f"canonical SVG contains unsupported elements: {sorted(unexpected)}",
        )


def command_version(command: list[str]) -> str:
    result = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
    )
    return f"{result.stdout}\n{result.stderr}"


def run_generator_command(command: list[str]) -> None:
    result = subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        output = f"{result.stdout}\n{result.stderr}".strip()
        raise IconVerificationError(
            f"icon generator command failed: {' '.join(command)}\n{output}",
        )


def verify_generator_tools() -> None:
    version_outputs = {
        "imagemagick": command_version(["magick", "--version"]),
        "optipng": command_version(["optipng", "-version"]),
        "png2icns": command_version(["pkg-config", "--modversion", "libicns"]),
        "rsvg-convert": command_version(["rsvg-convert", "--version"]),
    }
    for tool, expected_version in EXPECTED_TOOLS.items():
        if expected_version not in version_outputs[tool]:
            raise IconVerificationError(
                f"{tool} does not match the reviewed version {expected_version}",
            )


def output_manifest(
    source: Path,
    output_directory: Path,
    source_reference: str = CANONICAL_SOURCE_REFERENCE,
) -> dict[str, object]:
    png_path = output_directory / "kani.png"
    ico_path = output_directory / "kani.ico"
    icns_path = output_directory / "kani.icns"
    width, height = png_dimensions(png_path)
    return {
        "generator": "tools/generate_desktop_icons.py",
        "generator_version": 2,
        "outputs": {
            "kani.icns": {
                "chunks": list(icns_chunks(icns_path)),
                "sha256": sha256(icns_path),
            },
            "kani.ico": {
                "frames": list(ico_sizes(ico_path)),
                "sha256": sha256(ico_path),
            },
            "kani.png": {
                "height": height,
                "sha256": sha256(png_path),
                "width": width,
            },
        },
        "source": source_reference,
        "source_sha256": sha256(source),
        "tools": EXPECTED_TOOLS,
    }


def render_icon_set(source: Path, output_directory: Path) -> None:
    verify_vector_source(source)
    verify_generator_tools()
    output_directory.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="kani-desktop-icons-") as temporary:
        temporary_directory = Path(temporary)
        rendered_pngs = {}
        for size in PNG_SIZES:
            rendered = temporary_directory / f"kani-{size}.png"
            run_generator_command(
                [
                    "rsvg-convert",
                    "--width",
                    str(size),
                    "--height",
                    str(size),
                    "--output",
                    str(rendered),
                    str(source),
                ],
            )
            run_generator_command(
                ["optipng", "-quiet", "-o7", "-strip", "all", str(rendered)],
            )
            rendered_pngs[size] = rendered

        generated_png = temporary_directory / "kani.png"
        generated_ico = temporary_directory / "kani.ico"
        generated_icns = temporary_directory / "kani.icns"
        shutil.copyfile(rendered_pngs[512], generated_png)
        run_generator_command(
            [
                "magick",
                *(str(rendered_pngs[size]) for size in ICO_SIZES),
                str(generated_ico),
            ],
        )
        run_generator_command(
            [
                "png2icns",
                str(generated_icns),
                *(str(rendered_pngs[size]) for size in ICNS_SIZES),
            ],
        )

        for generated in (generated_png, generated_ico, generated_icns):
            shutil.copyfile(generated, output_directory / generated.name)


def generate_icon_set() -> None:
    render_icon_set(CANONICAL_SOURCE, OUTPUT_DIRECTORY)
    manifest = output_manifest(
        CANONICAL_SOURCE,
        OUTPUT_DIRECTORY,
        CANONICAL_SOURCE_REFERENCE,
    )
    MANIFEST_PATH.write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    verify_icon_set()


def verify_icon_set(
    source: Path = CANONICAL_SOURCE,
    output_directory: Path = OUTPUT_DIRECTORY,
    manifest_path: Path = MANIFEST_PATH,
    source_reference: str = CANONICAL_SOURCE_REFERENCE,
    renderer: Callable[[Path, Path], None] = render_icon_set,
) -> None:
    verify_vector_source(source)
    if not manifest_path.is_file():
        raise IconVerificationError(f"missing icon manifest: {manifest_path}")
    actual_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected_manifest = output_manifest(
        source,
        output_directory,
        source_reference,
    )
    if actual_manifest != expected_manifest:
        raise IconVerificationError("desktop icon manifest does not match committed assets")
    if png_dimensions(output_directory / "kani.png") != (512, 512):
        raise IconVerificationError("Linux icon must be exactly 512x512")
    if ico_sizes(output_directory / "kani.ico") != ICO_SIZES:
        raise IconVerificationError("Windows icon frames do not match the reviewed sizes")
    if icns_chunks(output_directory / "kani.icns") != EXPECTED_ICNS_CHUNKS:
        raise IconVerificationError("macOS icon chunks do not match the reviewed set")

    with tempfile.TemporaryDirectory(prefix="kani-desktop-icon-check-") as temporary:
        regenerated_directory = Path(temporary)
        renderer(source, regenerated_directory)
        regenerated_manifest = output_manifest(
            source,
            regenerated_directory,
            source_reference,
        )
        if regenerated_manifest != actual_manifest:
            differing_outputs = [
                filename
                for filename in OUTPUT_FILENAMES
                if (
                    sha256(regenerated_directory / filename) !=
                    sha256(output_directory / filename)
                )
            ]
            raise IconVerificationError(
                "desktop icons do not match a deterministic render of the " +
                f"canonical SVG: {', '.join(differing_outputs)}",
            )


def main() -> None:
    parser = argparse.ArgumentParser()
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument("--write", action="store_true")
    operation.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.write:
        generate_icon_set()
    else:
        verify_icon_set()
    print("Desktop icon assets verified")


if __name__ == "__main__":
    main()
