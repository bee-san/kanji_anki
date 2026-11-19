#!/usr/bin/env python3

from pathlib import Path
import unittest

REPO_ROOT = Path(__file__).resolve().parents[1]
RES = REPO_ROOT / "app/src/main/res"


class LauncherIconTest(unittest.TestCase):
    def test_launcher_icon_is_the_ka_seal(self):
        # Kani 1.0 ships a vector adaptive icon: a vermilion seal with カ in paper colour.
        self.assertEqual([], sorted(RES.glob("mipmap-*/ic_launcher*.png")), "raster icons were replaced by the seal")
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            with self.subTest(icon=name):
                icon = (RES / "mipmap-anydpi" / name).read_text(encoding="utf-8")
                self.assertIn("@drawable/ic_kani_seal_fg", icon)
                self.assertIn("@color/kani_seal", icon)


if __name__ == "__main__":
    unittest.main()
