#!/usr/bin/env python3

from pathlib import Path
import sys
import tempfile
import unittest

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parents[0]
sys.path.insert(0, str(TOOLS_DIR))

import remove_logo_background as logo_bg


LOGO_ASSETS = sorted((REPO_ROOT / "app/src/main/res").glob("mipmap-*/ic_launcher*.png"))


class LogoBackgroundRemovalTest(unittest.TestCase):
    def test_logo_assets_have_transparent_matte(self):
        self.assertTrue(LOGO_ASSETS, "expected checked-in Kani launcher assets")
        for path in LOGO_ASSETS:
            with self.subTest(path=path.relative_to(REPO_ROOT)):
                width, height, pixels = logo_bg.read_rgba_png(path)
                background = logo_bg.find_background_component(width, height, pixels)
                self.assertEqual(background, set())
                transparent_pixels = sum(
                    1 for pixel_index in range(width * height)
                    if pixels[pixel_index * logo_bg.BYTES_PER_PIXEL + 3] == 0
                )
                self.assertGreater(transparent_pixels, width * height // 5)

    def test_removal_keeps_enclosed_white_artwork(self):
        width = 5
        height = 5
        pixels = bytearray([0, 0, 0, 0] * width * height)

        def set_pixel(x, y, red, green, blue, alpha=255):
            base = (y * width + x) * logo_bg.BYTES_PER_PIXEL
            pixels[base:base + 4] = bytes((red, green, blue, alpha))

        # Edge-connected off-white matte.
        for x in range(1, 4):
            set_pixel(x, 1, 250, 249, 247)
        set_pixel(1, 2, 250, 249, 247)
        set_pixel(3, 2, 250, 249, 247)
        for x in range(1, 4):
            set_pixel(x, 3, 250, 249, 247)

        # Red outline enclosing a white artwork pixel that should not be removed.
        set_pixel(2, 1, 240, 80, 80)
        set_pixel(1, 2, 240, 80, 80)
        set_pixel(3, 2, 240, 80, 80)
        set_pixel(2, 3, 240, 80, 80)
        set_pixel(2, 2, 255, 255, 255)

        removed = logo_bg.remove_background(width, height, pixels)
        self.assertEqual(removed, 4)
        self.assertEqual(pixels[(2 * width + 2) * 4 + 3], 255)

    def test_removal_ignores_near_white_artwork_adjacent_to_interior_transparency(self):
        width = 5
        height = 5
        pixels = bytearray([20, 20, 20, 255] * width * height)

        def set_pixel(x, y, red, green, blue, alpha=255):
            base = (y * width + x) * logo_bg.BYTES_PER_PIXEL
            pixels[base:base + 4] = bytes((red, green, blue, alpha))

        # Opaque border around a transparent interior hole.
        set_pixel(2, 2, 0, 0, 0, 0)
        set_pixel(2, 1, 250, 249, 247)
        set_pixel(1, 2, 250, 249, 247)
        set_pixel(3, 2, 250, 249, 247)
        set_pixel(2, 3, 250, 249, 247)

        removed = logo_bg.remove_background(width, height, pixels)
        self.assertEqual(removed, 0)
        self.assertEqual(pixels[(1 * width + 2) * 4 + 3], 255)

    def test_processing_round_trips_png(self):
        source = LOGO_ASSETS[-1]
        with tempfile.TemporaryDirectory() as tmp_dir:
            copy = Path(tmp_dir) / source.name
            copy.write_bytes(source.read_bytes())
            removed = logo_bg.process_png(copy)
            self.assertEqual(removed, 0)
            width, height, pixels = logo_bg.read_rgba_png(copy)
            self.assertGreater(width, 0)
            self.assertGreater(height, 0)
            self.assertEqual(len(pixels), width * height * logo_bg.BYTES_PER_PIXEL)


if __name__ == "__main__":
    unittest.main()
