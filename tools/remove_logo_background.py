#!/usr/bin/env python3
"""Remove the opaque off-white matte from the Kani logo PNG assets.

The checked-in launcher artwork is already RGBA, but its visible logo is drawn on
an opaque off-white square. This script keeps the logo itself and makes only the
edge-connected, low-chroma near-white matte transparent. It intentionally avoids
manual or paid background-removal tools so asset regeneration is reproducible.
"""

from __future__ import annotations

import argparse
from collections import deque
from pathlib import Path
import struct
import zlib

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
BYTES_PER_PIXEL = 4

# Background is an off-white/cream matte. The logo contains white book pages, so
# only remove pixels connected to the transparent perimeter.
MIN_BACKGROUND_CHANNEL = 235
MAX_BACKGROUND_CHROMA = 22


def _paeth(left: int, up: int, upper_left: int) -> int:
    estimate = left + up - upper_left
    distances = (
        abs(estimate - left),
        abs(estimate - up),
        abs(estimate - upper_left),
    )
    if distances[0] <= distances[1] and distances[0] <= distances[2]:
        return left
    if distances[1] <= distances[2]:
        return up
    return upper_left


def read_rgba_png(path: Path) -> tuple[int, int, bytearray]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError(f"{path} is not a PNG file")

    width = height = bit_depth = color_type = interlace = None
    idat = bytearray()
    offset = len(PNG_SIGNATURE)
    while offset < len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_data = data[offset + 8 : offset + 8 + length]
        offset += length + 12
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _compression, _filter, interlace = struct.unpack(
                ">IIBBBBB", chunk_data
            )
        elif chunk_type == b"IDAT":
            idat.extend(chunk_data)
        elif chunk_type == b"IEND":
            break

    if width is None or height is None:
        raise ValueError(f"{path} does not contain a PNG IHDR chunk")
    if bit_depth != 8 or color_type != 6 or interlace != 0:
        raise ValueError(f"{path} must be an 8-bit non-interlaced RGBA PNG")

    raw = zlib.decompress(bytes(idat))
    stride = width * BYTES_PER_PIXEL
    rows: list[bytearray] = []
    previous = bytearray(stride)
    source_offset = 0
    for _y in range(height):
        filter_type = raw[source_offset]
        source_offset += 1
        filtered = raw[source_offset : source_offset + stride]
        source_offset += stride
        row = bytearray(stride)
        for index, value in enumerate(filtered):
            left = row[index - BYTES_PER_PIXEL] if index >= BYTES_PER_PIXEL else 0
            up = previous[index]
            upper_left = previous[index - BYTES_PER_PIXEL] if index >= BYTES_PER_PIXEL else 0
            if filter_type == 0:
                reconstructed = value
            elif filter_type == 1:
                reconstructed = value + left
            elif filter_type == 2:
                reconstructed = value + up
            elif filter_type == 3:
                reconstructed = value + ((left + up) // 2)
            elif filter_type == 4:
                reconstructed = value + _paeth(left, up, upper_left)
            else:
                raise ValueError(f"Unsupported PNG filter type {filter_type} in {path}")
            row[index] = reconstructed & 0xFF
        rows.append(row)
        previous = row
    return width, height, bytearray().join(rows)


def write_rgba_png(path: Path, width: int, height: int, pixels: bytearray) -> None:
    stride = width * BYTES_PER_PIXEL
    scanlines = bytearray()
    for y in range(height):
        scanlines.append(0)  # filter type: None
        start = y * stride
        scanlines.extend(pixels[start : start + stride])

    def chunk(kind: bytes, payload: bytes) -> bytes:
        checksum = zlib.crc32(kind)
        checksum = zlib.crc32(payload, checksum) & 0xFFFFFFFF
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", checksum)

    png = bytearray(PNG_SIGNATURE)
    png.extend(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
    png.extend(chunk(b"IDAT", zlib.compress(bytes(scanlines), level=9)))
    png.extend(chunk(b"IEND", b""))
    path.write_bytes(png)


def is_background_candidate(pixels: bytearray, pixel_index: int) -> bool:
    base = pixel_index * BYTES_PER_PIXEL
    red, green, blue, alpha = pixels[base : base + BYTES_PER_PIXEL]
    if alpha == 0:
        return False
    return min(red, green, blue) >= MIN_BACKGROUND_CHANNEL and (max(red, green, blue) - min(red, green, blue)) <= MAX_BACKGROUND_CHROMA


def _neighbors(width: int, height: int, pixel_index: int):
    x = pixel_index % width
    y = pixel_index // width
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        nx = x + dx
        ny = y + dy
        if 0 <= nx < width and 0 <= ny < height:
            yield ny * width + nx


def find_exterior_transparent_component(width: int, height: int, pixels: bytearray) -> set[int]:
    seeds = set()
    for x in range(width):
        seeds.add(x)
        seeds.add((height - 1) * width + x)
    for y in range(height):
        seeds.add(y * width)
        seeds.add(y * width + width - 1)

    visited: set[int] = set()
    queue: deque[int] = deque()
    for pixel_index in seeds:
        if pixels[pixel_index * BYTES_PER_PIXEL + 3] == 0:
            visited.add(pixel_index)
            queue.append(pixel_index)

    while queue:
        pixel_index = queue.popleft()
        for neighbor in _neighbors(width, height, pixel_index):
            if neighbor in visited or pixels[neighbor * BYTES_PER_PIXEL + 3] != 0:
                continue
            visited.add(neighbor)
            queue.append(neighbor)
    return visited


def exterior_transparent_neighbor_exists(width: int, height: int, exterior: set[int], pixel_index: int) -> bool:
    return any(neighbor in exterior for neighbor in _neighbors(width, height, pixel_index))


def find_background_component(width: int, height: int, pixels: bytearray) -> set[int]:
    total_pixels = width * height
    exterior = find_exterior_transparent_component(width, height, pixels)
    visited: set[int] = set()
    queue: deque[int] = deque()

    for pixel_index in range(total_pixels):
        if is_background_candidate(pixels, pixel_index) and exterior_transparent_neighbor_exists(width, height, exterior, pixel_index):
            visited.add(pixel_index)
            queue.append(pixel_index)

    while queue:
        pixel_index = queue.popleft()
        for neighbor in _neighbors(width, height, pixel_index):
            if neighbor in visited or not is_background_candidate(pixels, neighbor):
                continue
            visited.add(neighbor)
            queue.append(neighbor)
    return visited


def remove_background(width: int, height: int, pixels: bytearray) -> int:
    background = find_background_component(width, height, pixels)
    for pixel_index in background:
        base = pixel_index * BYTES_PER_PIXEL
        pixels[base] = 0
        pixels[base + 1] = 0
        pixels[base + 2] = 0
        pixels[base + 3] = 0
    return len(background)


def process_png(path: Path) -> int:
    width, height, pixels = read_rgba_png(path)
    removed = remove_background(width, height, pixels)
    write_rgba_png(path, width, height, pixels)
    return removed


def default_logo_paths(repo_root: Path) -> list[Path]:
    return sorted(repo_root.glob("app/src/main/res/mipmap-*/ic_launcher*.png"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        help="PNG files to process. Defaults to app/src/main/res/mipmap-*/ic_launcher*.png.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd()
    paths = args.paths or default_logo_paths(repo_root)
    if not paths:
        raise SystemExit("No logo PNG assets found")

    for path in paths:
        removed = process_png(path)
        print(f"{path}: removed {removed} matte pixels")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
