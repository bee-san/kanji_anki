"""Tests for the desktop SBOM and third-party notices generator.

The document is release evidence, so the properties under test are the ones that
make it trustworthy: it describes the shipped bytes, it is byte-identical across
runs, it never guesses a license, and it does not present Kani's own modules or the
bundled JDK as third-party dependencies.
"""

from __future__ import annotations

import hashlib
import json
import unittest
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory

from tools.generate_desktop_sbom import (
    SbomError,
    build_notices,
    describe,
    first_party_modules,
    packaged_jars,
    verified_coordinates,
    write_outputs,
)


def jar_at(path: Path, content: bytes = b"class bytes") -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("a.class", content)
    return path


class GenerateDesktopSbomTest(unittest.TestCase):
    def setUp(self):
        self._temporary = TemporaryDirectory(prefix="kani-sbom-")
        self.addCleanup(self._temporary.cleanup)
        self.root = Path(self._temporary.name)
        self.image = self.root / "image"
        self.image.mkdir()

    def test_an_image_with_no_jars_is_an_error_not_an_empty_document(self):
        # An empty SBOM would be indistinguishable from "this app bundles nothing",
        # which is a claim rather than a gap.
        with self.assertRaises(SbomError) as raised:
            packaged_jars(self.image)
        self.assertIn("no jars found", str(raised.exception))

    def test_a_component_carries_the_digest_of_the_shipped_bytes(self):
        jar = jar_at(self.image / "okio-jvm-3.9.0-abcdef0123456789abcdef.jar")
        expected = hashlib.sha256(jar.read_bytes()).hexdigest()

        component = describe(jar, {}, frozenset(), {})

        # The whole value of building from the image: a reader can verify a component
        # instead of trusting the document.
        self.assertEqual(expected, component["hashes"][0]["content"])
        self.assertEqual("SHA-256", component["hashes"][0]["alg"])

    def test_a_known_digest_identifies_the_component_exactly(self):
        jar = jar_at(self.image / "renamed-by-jpackage-9.9.9-fedcba9876543210fedc.jar")
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()

        component = describe(
            jar,
            {},
            frozenset(),
            {digest: ("com.squareup.okio", "okio-jvm", "3.9.0")},
        )

        # Identified by bytes, so the misleading filename is overridden entirely. A
        # name-based matcher would have attributed this to "renamed-by-jpackage".
        self.assertEqual("com.squareup.okio", component["group"])
        self.assertEqual("okio-jvm", component["name"])
        self.assertEqual("3.9.0", component["version"])
        self.assertEqual("Apache-2.0", component["licenses"][0]["license"]["id"])
        self.assertEqual("pkg:maven/com.squareup.okio/okio-jvm@3.9.0", component["purl"])

    def test_an_unknown_component_is_reported_as_unknown_rather_than_guessed(self):
        jar = jar_at(self.image / "mystery-1.0.0.jar")

        component = describe(jar, {}, frozenset(), {})

        # A notices file that states the wrong license is a legal claim, not a
        # formatting error. An honest gap is auditable.
        self.assertEqual("UNKNOWN", component["group"])
        self.assertEqual("UNKNOWN", component["licenses"][0]["license"]["id"])
        self.assertNotIn("purl", component)

    def test_first_party_modules_are_not_presented_as_dependencies(self):
        jar = jar_at(self.image / "data-api-f095bff0e6f0dd7f4444c8c4636c4cdc.jar")

        component = describe(jar, {}, frozenset({"data-api"}), {})

        # The rename hash is stripped, or the component name changes on every build and
        # the SBOM stops being comparable between releases.
        self.assertEqual("data-api", component["name"])
        self.assertEqual("dev.bee.kanjianki", component["group"])
        self.assertEqual(
            "LicenseRef-Kani-first-party",
            component["licenses"][0]["license"]["id"],
        )
        self.assertIn({"name": "kani:firstParty", "value": "true"}, component["properties"])

    def test_the_bundled_runtime_is_attributed_to_the_jdk_not_as_a_library(self):
        jar = jar_at(self.image / "jrt-fs.jar")

        component = describe(jar, {}, frozenset(), {})

        # `jrt-fs` arrives inside the jlink image: no group, no version, no entry in the
        # verification metadata. Calling it a dependency Kani declares would be wrong.
        self.assertEqual("net.adoptium.temurin", component["group"])
        self.assertEqual(
            "GPL-2.0-with-classpath-exception",
            component["licenses"][0]["license"]["id"],
        )
        self.assertIn(
            {"name": "kani:bundledRuntime", "value": "true"},
            component["properties"],
        )

    def test_notices_exclude_first_party_and_flag_unknowns_for_review(self):
        notices = build_notices(
            [
                {
                    "group": "dev.bee.kanjianki",
                    "name": "data-api",
                    "version": "UNKNOWN",
                    "licenses": [{"license": {"id": "LicenseRef-Kani-first-party"}}],
                },
                {
                    "group": "com.squareup.okio",
                    "name": "okio-jvm",
                    "version": "3.9.0",
                    "licenses": [{"license": {"id": "Apache-2.0"}}],
                },
                {
                    "group": "UNKNOWN",
                    "name": "mystery",
                    "version": "1.0.0",
                    "licenses": [{"license": {"id": "UNKNOWN"}}],
                },
            ],
        )

        self.assertIn("com.squareup.okio:okio-jvm:3.9.0", notices)
        # Kani does not attribute itself; 28 of its own modules would bury the entries a
        # reviewer actually has to check.
        self.assertNotIn("data-api", notices)
        self.assertIn("REQUIRES REVIEW", notices)
        self.assertIn("mystery:1.0.0", notices)

    def test_two_runs_over_one_image_produce_byte_identical_output(self):
        jar_at(self.image / "okio-jvm-3.9.0.jar")
        jar_at(self.image / "annotation-jvm-1.9.1.jar", b"other bytes")
        catalog = self.root / "gradle" / "libs.versions.toml"
        catalog.parent.mkdir(parents=True, exist_ok=True)
        catalog.write_text("[libraries]\n", encoding="utf-8")

        outputs = []
        for index in (1, 2):
            sbom = self.root / f"sbom{index}.json"
            notices = self.root / f"notices{index}.txt"
            write_outputs(self.image, sbom, notices, catalog)
            outputs.append((sbom.read_bytes(), notices.read_bytes()))

        # Release evidence: two builds of identical bytes must produce identical
        # documents, so no timestamp, hostname, or run-scoped UUID may appear.
        self.assertEqual(outputs[0], outputs[1])

    def test_the_serial_number_is_derived_from_content_not_the_clock(self):
        jar_at(self.image / "okio-jvm-3.9.0.jar")
        catalog = self.root / "gradle" / "libs.versions.toml"
        catalog.parent.mkdir(parents=True, exist_ok=True)
        catalog.write_text("[libraries]\n", encoding="utf-8")

        first = self.root / "a.json"
        write_outputs(self.image, first, self.root / "a.txt", catalog)
        original = json.loads(first.read_text(encoding="utf-8"))

        # Adding a component must change the serial; re-running must not.
        jar_at(self.image / "extra-1.0.0.jar", b"extra")
        second = self.root / "b.json"
        write_outputs(self.image, second, self.root / "b.txt", catalog)
        changed = json.loads(second.read_text(encoding="utf-8"))

        self.assertNotEqual(original["serialNumber"], changed["serialNumber"])
        self.assertNotIn("timestamp", original["metadata"])

    def test_the_document_declares_the_cyclonedx_format(self):
        jar_at(self.image / "okio-jvm-3.9.0.jar")
        catalog = self.root / "gradle" / "libs.versions.toml"
        catalog.parent.mkdir(parents=True, exist_ok=True)
        catalog.write_text("[libraries]\n", encoding="utf-8")
        out = self.root / "sbom.json"

        write_outputs(self.image, out, self.root / "notices.txt", catalog)
        document = json.loads(out.read_text(encoding="utf-8"))

        self.assertEqual("CycloneDX", document["bomFormat"])
        self.assertEqual("1.5", document["specVersion"])
        self.assertEqual("Kani", document["metadata"]["component"]["name"])

    def test_first_party_modules_are_read_from_settings_not_hardcoded(self):
        settings = self.root / "settings.gradle.kts"
        settings.write_text(
            'include(":core")\ninclude(":widget")\ninclude(":desktop-app")\n',
            encoding="utf-8",
        )

        modules = first_party_modules(settings)

        # Derived, because the list changes: `:widget` was extracted during this work,
        # and a hardcoded set would report it as a third-party library.
        self.assertIn("widget", modules)
        self.assertIn("core", modules)
        self.assertIn("bee-fsrs", modules)

    def test_a_missing_settings_file_falls_back_rather_than_crashing(self):
        modules = first_party_modules(self.root / "absent.gradle.kts")
        self.assertEqual(frozenset({"bee-fsrs"}), modules)

    def test_verified_coordinates_maps_digests_to_coordinates(self):
        metadata = self.root / "verification-metadata.xml"
        metadata.write_text(
            '<verification-metadata>\n'
            '  <components>\n'
            '    <component group="com.squareup.okio" name="okio-jvm" version="3.9.0">\n'
            '      <artifact name="okio-jvm-3.9.0.jar">\n'
            f'        <sha256 value="{"a" * 64}" origin="Generated by Gradle"/>\n'
            '      </artifact>\n'
            '    </component>\n'
            '  </components>\n'
            '</verification-metadata>\n',
            encoding="utf-8",
        )

        by_digest = verified_coordinates(metadata)

        self.assertEqual(("com.squareup.okio", "okio-jvm", "3.9.0"), by_digest["a" * 64])

    def test_a_missing_verification_file_yields_no_coordinates(self):
        # Every component then falls back to UNKNOWN, which is the honest outcome — not
        # a document full of guesses.
        self.assertEqual({}, verified_coordinates(self.root / "absent.xml"))


if __name__ == "__main__":
    unittest.main()
