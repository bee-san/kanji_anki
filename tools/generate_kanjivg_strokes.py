#!/usr/bin/env python3
"""Generate compact normalized stroke guides from a KanjiVG XML export."""

from __future__ import annotations

import argparse
import gzip
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path


COMMAND_RE = re.compile(r"[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?")
KVG_NS = "{http://kanjivg.tagaini.net}"
VIEWBOX_SIZE = 109.0
SAMPLES_PER_CURVE = 4
KANJI_RANGES = (
    (0x3400, 0x4DBF),
    (0x4E00, 0x9FFF),
    (0xF900, 0xFAFF),
    (0x20000, 0x2A6DF),
    (0x2A700, 0x2B73F),
    (0x2B740, 0x2B81F),
    (0x2B820, 0x2CEAF),
    (0x2CEB0, 0x2EBEF),
    (0x2EBF0, 0x2EE5F),
    (0x2F800, 0x2FA1F),
    (0x30000, 0x3134F),
    (0x31350, 0x323AF),
    (0x323B0, 0x3347F),
)


def tokenize_path(data: str) -> list[str]:
    return COMMAND_RE.findall(data.replace(",", " "))


def is_command(token: str) -> bool:
    return len(token) == 1 and token.isalpha()


class PathReader:
    def __init__(self, data: str) -> None:
        self.tokens = tokenize_path(data)
        self.index = 0

    def done(self) -> bool:
        return self.index >= len(self.tokens)

    def peek(self) -> str | None:
        if self.done():
            return None
        return self.tokens[self.index]

    def command(self) -> str:
        token = self.tokens[self.index]
        self.index += 1
        return token

    def number(self) -> float:
        token = self.tokens[self.index]
        if is_command(token):
            raise ValueError(f"Expected number, got command {token}")
        self.index += 1
        return float(token)

    def has_number(self) -> bool:
        return not self.done() and not is_command(self.tokens[self.index])


def normalize(point: tuple[float, float]) -> tuple[float, float]:
    x, y = point
    return (clamp(x / VIEWBOX_SIZE), clamp(y / VIEWBOX_SIZE))


def clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def cubic(
    p0: tuple[float, float],
    p1: tuple[float, float],
    p2: tuple[float, float],
    p3: tuple[float, float],
    t: float,
) -> tuple[float, float]:
    inv = 1.0 - t
    return (
        inv**3 * p0[0] + 3 * inv**2 * t * p1[0] + 3 * inv * t**2 * p2[0] + t**3 * p3[0],
        inv**3 * p0[1] + 3 * inv**2 * t * p1[1] + 3 * inv * t**2 * p2[1] + t**3 * p3[1],
    )


def quadratic(
    p0: tuple[float, float],
    p1: tuple[float, float],
    p2: tuple[float, float],
    t: float,
) -> tuple[float, float]:
    inv = 1.0 - t
    return (
        inv**2 * p0[0] + 2 * inv * t * p1[0] + t**2 * p2[0],
        inv**2 * p0[1] + 2 * inv * t * p1[1] + t**2 * p2[1],
    )


def append_point(points: list[tuple[float, float]], point: tuple[float, float]) -> None:
    normalized = normalize(point)
    if points and math.hypot(points[-1][0] - normalized[0], points[-1][1] - normalized[1]) < 0.002:
        return
    points.append(normalized)


def parse_path(data: str) -> list[tuple[float, float]]:
    reader = PathReader(data)
    points: list[tuple[float, float]] = []
    current = (0.0, 0.0)
    start = (0.0, 0.0)
    command = ""
    last_cubic_control: tuple[float, float] | None = None
    last_quadratic_control: tuple[float, float] | None = None

    while not reader.done():
        if is_command(reader.peek() or ""):
            command = reader.command()
        if not command:
            raise ValueError("Path data did not start with a command")

        relative = command.islower()
        op = command.upper()

        if op == "M":
            first = True
            while reader.has_number():
                x = reader.number()
                y = reader.number()
                current = offset(current, x, y, relative)
                if first:
                    start = current
                    append_point(points, current)
                    first = False
                else:
                    append_point(points, current)
                last_cubic_control = None
                last_quadratic_control = None
            command = "l" if relative else "L"
        elif op == "L":
            while reader.has_number():
                current = offset(current, reader.number(), reader.number(), relative)
                append_point(points, current)
            last_cubic_control = None
            last_quadratic_control = None
        elif op == "H":
            while reader.has_number():
                x = reader.number() + (current[0] if relative else 0.0)
                current = (x, current[1])
                append_point(points, current)
            last_cubic_control = None
            last_quadratic_control = None
        elif op == "V":
            while reader.has_number():
                y = reader.number() + (current[1] if relative else 0.0)
                current = (current[0], y)
                append_point(points, current)
            last_cubic_control = None
            last_quadratic_control = None
        elif op == "C":
            while reader.has_number():
                p1 = offset(current, reader.number(), reader.number(), relative)
                p2 = offset(current, reader.number(), reader.number(), relative)
                p3 = offset(current, reader.number(), reader.number(), relative)
                for i in range(1, SAMPLES_PER_CURVE + 1):
                    append_point(points, cubic(current, p1, p2, p3, i / SAMPLES_PER_CURVE))
                current = p3
                last_cubic_control = p2
                last_quadratic_control = None
        elif op == "S":
            while reader.has_number():
                p1 = reflect(current, last_cubic_control) if last_cubic_control is not None else current
                p2 = offset(current, reader.number(), reader.number(), relative)
                p3 = offset(current, reader.number(), reader.number(), relative)
                for i in range(1, SAMPLES_PER_CURVE + 1):
                    append_point(points, cubic(current, p1, p2, p3, i / SAMPLES_PER_CURVE))
                current = p3
                last_cubic_control = p2
                last_quadratic_control = None
        elif op == "Q":
            while reader.has_number():
                p1 = offset(current, reader.number(), reader.number(), relative)
                p2 = offset(current, reader.number(), reader.number(), relative)
                for i in range(1, SAMPLES_PER_CURVE + 1):
                    append_point(points, quadratic(current, p1, p2, i / SAMPLES_PER_CURVE))
                current = p2
                last_cubic_control = None
                last_quadratic_control = p1
        elif op == "T":
            while reader.has_number():
                p1 = reflect(current, last_quadratic_control) if last_quadratic_control is not None else current
                p2 = offset(current, reader.number(), reader.number(), relative)
                for i in range(1, SAMPLES_PER_CURVE + 1):
                    append_point(points, quadratic(current, p1, p2, i / SAMPLES_PER_CURVE))
                current = p2
                last_cubic_control = None
                last_quadratic_control = p1
        elif op == "Z":
            current = start
            append_point(points, current)
            last_cubic_control = None
            last_quadratic_control = None
        else:
            raise ValueError(f"Unsupported SVG path command {command}")

    return points


def offset(current: tuple[float, float], x: float, y: float, relative: bool) -> tuple[float, float]:
    if relative:
        return (current[0] + x, current[1] + y)
    return (x, y)


def reflect(current: tuple[float, float], control: tuple[float, float] | None) -> tuple[float, float]:
    if control is None:
        return current
    return (2 * current[0] - control[0], 2 * current[1] - control[1])


def point_text(point: tuple[float, float]) -> str:
    return f"{point[0]:.3f},{point[1]:.3f}"


def stroke_text(points: list[tuple[float, float]]) -> str:
    return ";".join(point_text(point) for point in points)


def read_kanjivg(input_path: Path) -> list[tuple[str, list[list[tuple[float, float]]]]]:
    opener = gzip.open if input_path.suffix == ".gz" else open
    records: list[tuple[str, list[list[tuple[float, float]]]]] = []
    with opener(input_path, "rb") as source:
        for _event, elem in ET.iterparse(source, events=("end",)):
            if elem.tag != "kanji":
                continue
            code_hex = elem.attrib["id"].split("_")[-1]
            char = chr(int(code_hex, 16))
            if not should_include(char):
                elem.clear()
                continue
            strokes: list[list[tuple[float, float]]] = []
            for path in elem.iter("path"):
                data = path.attrib.get("d")
                if data:
                    points = parse_path(data)
                    if len(points) >= 2:
                        strokes.append(points)
            if strokes:
                records.append((char, strokes))
            elem.clear()
    records.sort(key=lambda record: ord(record[0]))
    return records


def should_include(char: str) -> bool:
    code = ord(char)
    return (
        any(start <= code <= end for start, end in KANJI_RANGES)
        or 0x3005 <= code <= 0x3007
        or 0x30A0 <= code <= 0x30FF
    )


def write_tsv(records: list[tuple[str, list[list[tuple[float, float]]]]], output_path: Path, source_name: str) -> None:
    with output_path.open("w", encoding="utf-8", newline="\n") as out:
        out.write("# Compact normalized stroke guides generated from KanjiVG.\n")
        out.write(f"# Source: {source_name}\n")
        out.write("# Format: kanji<TAB>x,y;x,y|x,y;x,y\n")
        for char, strokes in records:
            out.write(char)
            out.write("\t")
            out.write("|".join(stroke_text(stroke) for stroke in strokes))
            out.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path, help="KanjiVG XML or XML.GZ export")
    parser.add_argument("output", type=Path, help="Output TSV path")
    args = parser.parse_args()

    records = read_kanjivg(args.input)
    write_tsv(records, args.output, args.input.name)
    print(f"Wrote {len(records)} stroke guides to {args.output}")


if __name__ == "__main__":
    main()
