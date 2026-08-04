"""Keep `docs/desktop-native-packaging.md`'s licensing audit honest.

The audit records both what Kani's desktop packages ship and what they still owe. Both
halves rot in the same direction: someone resolves a gap and the document keeps claiming
it is open, or -- worse -- someone reads a stale "open gap" list and re-does work that is
already done. A licensing document that is wrong about its own gaps is worse than no
document, because it is quoted.

These tests only assert claims that are mechanically checkable from the repository. The
audit's other claims (the licences of the components statically linked into `libskiko`,
for instance) come from reading the shipped binary and cannot be re-derived here; they are
recorded in the document with how they were found.
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AUDIT = ROOT / "docs/desktop-native-packaging.md"
DICTIONARY_MANIFEST = ROOT / "app/src/main/assets/dictionaries/dictionary_sources.json"
CONVENTION = ROOT / "build-logic/src/main/kotlin/kani.desktop-application-conventions.gradle.kts"


class DesktopPackagingAuditTest(unittest.TestCase):
    def setUp(self) -> None:
        self.audit = AUDIT.read_text(encoding="utf-8")

    def test_the_audit_documents_every_format_the_convention_builds(self) -> None:
        # A format configured but undocumented ships with no recorded licensing basis.
        convention = CONVENTION.read_text(encoding="utf-8")
        for target_format, heading in (("Msi", "## Windows"), ("Deb", "## Linux"), ("Dmg", "## macOS")):
            self.assertIn(f"TargetFormat.{target_format}", convention)
            self.assertIn(heading, self.audit)

    def test_the_missing_licence_gap_closes_when_a_licence_is_added(self) -> None:
        # Kani has no LICENSE/COPYING file and the README declares no terms, so the audit
        # says so. Choosing a licence is the repository owner's decision, not one to make
        # on their behalf -- but the moment one exists, this claim is false and misleading.
        licence_files = [
            path
            for pattern in ("LICENSE*", "COPYING*", "LICENCE*")
            for path in ROOT.glob(pattern)
        ]
        claims_no_licence = "Kani has no licence" in self.audit
        if licence_files:
            self.assertFalse(
                claims_no_licence,
                "a licence file exists now; the audit's open-gap list must be updated: "
                f"{[path.name for path in licence_files]}",
            )
        else:
            self.assertTrue(
                claims_no_licence,
                "Kani still ships no licence file; the audit must keep saying so",
            )

    def test_every_dictionary_source_licence_is_recorded_or_named_as_a_gap(self) -> None:
        # The dictionary licences are a real obligation (CC BY-SA), and the manifest is
        # their source of truth. A source added there without reaching the audit ships
        # unattributed.
        manifest = json.loads(DICTIONARY_MANIFEST.read_text(encoding="utf-8"))
        for source in manifest["sources"]:
            name = source.get("name") or source["id"]
            self.assertIn(
                name,
                self.audit,
                f"dictionary source {name!r} is bundled but absent from the audit",
            )
            licence = source.get("license")
            if licence:
                self.assertIn(
                    licence,
                    self.audit,
                    f"{name!r} ships under {licence!r}, which the audit does not record",
                )
            else:
                # An unlicensed source must be listed as an open gap, not omitted.
                self.assertIn(
                    "no recorded licence",
                    self.audit,
                    f"{name!r} has no licence in the manifest and no gap in the audit",
                )

    def test_the_audit_does_not_claim_ankiconnect_is_bundled(self) -> None:
        # Goal 204 permits bundling AnkiConnect only after an explicit license/security
        # review, which has not happened.
        self.assertIn("separately installed user prerequisite", self.audit)

    def test_the_unqualified_host_claims_are_marked_unqualified(self) -> None:
        # Goal 204 requires qualifying the advertised minimums, not the current host, and
        # raising the floor where a gate is unavailable. None of the three gates exists
        # yet, so the audit must not read as though the minimums were tested.
        self.assertIn("declared* deployment target, not a\ntested one", self.audit)
        for claim in ("Windows 10/11 on real VMs", "Ubuntu 20.04", "macOS 13 on Apple silicon"):
            self.assertIn(claim, self.audit)

    def test_the_entitlements_files_point_at_a_document_that_exists(self) -> None:
        # Both entitlements files cite this document; a dangling reference in a file that
        # is only read during a signing investigation is the worst place for one.
        packaging = ROOT / "desktop-app/src/main/packaging/macos"
        referenced = False
        for entitlements in sorted(packaging.glob("*.entitlements")):
            text = entitlements.read_text(encoding="utf-8")
            if "docs/desktop-native-packaging.md" in text:
                referenced = True
        self.assertTrue(referenced, "no entitlements file references the packaging document")
        self.assertTrue(AUDIT.is_file())


if __name__ == "__main__":
    unittest.main()
