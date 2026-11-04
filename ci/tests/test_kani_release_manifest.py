from __future__ import annotations

import base64
import hashlib
import json
import re
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ci/scripts"))

from kani_release_manifest import (  # noqa: E402
    SCHEMA_VERSION,
    build_manifest,
    canonical_bytes,
    describe_asset,
    generate_key,
    main,
    manifest_json,
    sign_manifest,
    verify_manifest,
)

KOTLIN_CODEC = (
    ROOT
    / "update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/ReleaseManifestCodec.kt"
)
# The exact bytes a signature covers, asserted from both sides of the boundary: here,
# and by ReleaseManifestGoldenTest in :update-core. A drift between the generator and
# the verifier is otherwise undetectable until a real release fails to install.
CANONICAL_GOLDEN = ROOT / "ci/fixtures/release-manifest-canonical-golden.txt"
# :update-core has no repo-root system property wired into its tests, so the fixture is
# copied into that module's test resources. The two copies must stay byte-identical or
# each side would pin a different format and neither test would notice.
CANONICAL_GOLDEN_KOTLIN_COPY = (
    ROOT / "update-core/src/test/resources/release-manifest-canonical-golden.txt"
)


class ReleaseManifestGeneratorTest(unittest.TestCase):
    def setUp(self) -> None:
        self._directory = tempfile.TemporaryDirectory()
        self.root = Path(self._directory.name)
        self.addCleanup(self._directory.cleanup)

    def asset(self, name: str, payload: bytes = b"installer payload") -> Path:
        path = self.root / name
        path.write_bytes(payload)
        return path

    def manifest(self, *names: str) -> dict[str, object]:
        assets = [
            self.asset(name, payload=f"payload for {name}".encode())
            for name in (names or ("kani-desktop-linux-x64-1.2.3.deb",))
        ]
        return build_manifest(
            tag="v1.2.3",
            build_sha="abc123",
            key_id="kani-release-key-1",
            assets=assets,
        )

    def test_the_manifest_binds_every_fact_the_client_verifies(self) -> None:
        payload = b"the linux installer"
        asset = self.asset("kani-desktop-linux-x64-1.2.3.deb", payload=payload)

        manifest = build_manifest("v1.2.3", "abc123", "kani-release-key-1", [asset])

        self.assertEqual(SCHEMA_VERSION, manifest["schemaVersion"])
        self.assertEqual("v1.2.3", manifest["releaseTag"])
        self.assertEqual("1.2.3", manifest["semanticVersion"])
        self.assertEqual("abc123", manifest["buildSha"])
        self.assertEqual("kani-release-key-1", manifest["keyId"])
        self.assertEqual(
            [
                {
                    "filename": "kani-desktop-linux-x64-1.2.3.deb",
                    "sizeBytes": len(payload),
                    "sha256": hashlib.sha256(payload).hexdigest(),
                    "os": "linux",
                    "arch": "x64",
                    "packageType": "deb",
                },
            ],
            manifest["assets"],
        )

    def test_the_platform_is_derived_from_the_canonical_name_not_supplied(self) -> None:
        for name, expected in (
            ("kani-desktop-windows-x64-1.2.3.msi", ("windows", "x64", "msi")),
            ("kani-desktop-macos-arm64-1.2.3.dmg", ("macos", "arm64", "dmg")),
            ("kani-desktop-linux-x64-1.2.3.tar.gz", ("linux", "x64", "tar.gz")),
        ):
            described = describe_asset(self.asset(name), "1.2.3")
            self.assertEqual(
                expected,
                (described["os"], described["arch"], described["packageType"]),
            )

    def test_a_noncanonical_or_mismatched_asset_cannot_be_signed(self) -> None:
        with self.assertRaisesRegex(ValueError, "canonical desktop asset name"):
            describe_asset(self.asset("kani-android-1.2.3.apk"), "1.2.3")
        # A version drift between the tag and an asset name means the release job built
        # the wrong thing; signing it would attest to the mismatch.
        with self.assertRaisesRegex(ValueError, "expected 1.2.3"):
            describe_asset(self.asset("kani-desktop-linux-x64-1.2.4.deb"), "1.2.3")
        with self.assertRaisesRegex(ValueError, "unsupported package type"):
            describe_asset(self.asset("kani-desktop-linux-x64-1.2.3.rpm"), "1.2.3")
        with self.assertRaisesRegex(ValueError, "is empty"):
            describe_asset(self.asset("kani-desktop-linux-x64-1.2.3.deb", b""), "1.2.3")

    def test_manifest_construction_rejects_incomplete_input(self) -> None:
        with self.assertRaisesRegex(ValueError, "at least one asset"):
            build_manifest("v1.2.3", "abc123", "kani-release-key-1", [])
        asset = self.asset("kani-desktop-linux-x64-1.2.3.deb")
        with self.assertRaisesRegex(ValueError, "build sha must not be blank"):
            build_manifest("v1.2.3", "   ", "kani-release-key-1", [asset])
        with self.assertRaisesRegex(ValueError, "key id must not be blank"):
            build_manifest("v1.2.3", "abc123", " ", [asset])
        with self.assertRaisesRegex(ValueError, "duplicate asset filename"):
            build_manifest("v1.2.3", "abc123", "kani-release-key-1", [asset, asset])

    def test_canonical_bytes_are_deterministic_and_order_independent(self) -> None:
        one = self.manifest(
            "kani-desktop-windows-x64-1.2.3.msi",
            "kani-desktop-linux-x64-1.2.3.deb",
        )
        other = self.manifest(
            "kani-desktop-linux-x64-1.2.3.deb",
            "kani-desktop-windows-x64-1.2.3.msi",
        )

        self.assertEqual(canonical_bytes(one), canonical_bytes(other))
        text = canonical_bytes(one).decode("utf-8")
        # No wall-clock field: the bytes must be reproducible from the artifacts forever.
        self.assertNotIn("time", text)
        self.assertTrue(text.endswith("\n"))
        self.assertNotIn("\r", text)
        # Assets in filename order, so the client's re-serialization matches.
        self.assertLess(
            text.index("asset:kani-desktop-linux-x64-1.2.3.deb"),
            text.index("asset:kani-desktop-windows-x64-1.2.3.msi"),
        )

    def test_canonical_bytes_match_the_shared_golden_fixture(self) -> None:
        asset = self.asset("kani-desktop-linux-x64-1.2.3.deb", b"x" * 1024)
        manifest = build_manifest("v1.2.3", "abc123", "kani-release-key-1", [asset])
        # The golden fixture's digest is a placeholder, so substitute it: the point of
        # this assertion is the byte layout, not this asset's content.
        manifest["assets"][0]["sha256"] = "a" * 64

        self.assertEqual(
            CANONICAL_GOLDEN.read_bytes(),
            canonical_bytes(manifest),
        )
        # ReleaseManifestGoldenTest asserts the Kotlin codec against its own copy; if the
        # copies drift, the two sides silently stop testing the same format.
        self.assertEqual(
            CANONICAL_GOLDEN.read_bytes(),
            CANONICAL_GOLDEN_KOTLIN_COPY.read_bytes(),
        )

    def test_the_canonical_field_order_matches_the_kotlin_verifier(self) -> None:
        """The signed bytes are produced here and verified there; they must agree.

        A drift between the two is undetectable until a real release fails to install,
        so the field order is read out of the Kotlin codec rather than restated.
        """
        kotlin = KOTLIN_CODEC.read_text(encoding="utf-8")
        kotlin_fields = re.findall(r'"([A-Za-z0-9]+):\$', kotlin)
        python_fields = [
            line.split(":", 1)[0]
            for line in canonical_bytes(self.manifest()).decode("utf-8").splitlines()
        ]

        self.assertEqual(kotlin_fields, python_fields)
        # The Kotlin side joins with "\n" and appends one; assert that literally, so a
        # change to either newline convention fails here rather than in the field.
        self.assertIn('private const val LF = "\\n"', kotlin)
        self.assertIn("joinToString(LF) + LF", kotlin)

    def test_a_signature_verifies_and_a_tampered_manifest_does_not(self) -> None:
        private_der, public_der = generate_key()
        manifest = self.manifest()

        signature = sign_manifest(manifest, private_der)

        self.assertTrue(verify_manifest(manifest, signature, public_der))
        # Swap a digest after signing: the canonical bytes change, so the signature is
        # no longer valid over them.
        tampered = json.loads(json.dumps(manifest))
        tampered["assets"][0]["sha256"] = "b" * 64
        self.assertFalse(verify_manifest(tampered, signature, public_der))
        # A different key never verifies, however well-formed.
        _, other_public = generate_key()
        self.assertFalse(verify_manifest(manifest, signature, other_public))

    def test_signing_with_a_key_that_is_not_ed25519_fails_without_echoing_it(self) -> None:
        with self.assertRaises(ValueError) as caught:
            sign_manifest(self.manifest(), b"not a key")
        self.assertNotIn("not a key", str(caught.exception))

    def test_the_published_json_is_stable_and_newline_terminated(self) -> None:
        manifest = self.manifest()

        text = manifest_json(manifest)

        self.assertEqual(manifest, json.loads(text))
        self.assertTrue(text.endswith("\n"))
        # Sorted keys so two runs over the same release produce the same document.
        self.assertEqual(text, manifest_json(json.loads(text)))
        self.assertLess(text.index('"assets"'), text.index('"buildSha"'))

    def test_the_cli_generates_a_manifest_and_signature_that_verify(self) -> None:
        private_path = self.root / "signing-key.der"
        public_path = self.root / "signing-key.pub.der"
        self.assertEqual(
            0,
            main(
                [
                    "generate-key",
                    "--private-out", str(private_path),
                    "--public-out", str(public_path),
                ],
            ),
        )
        # The private key is created 0600, never briefly world-readable.
        self.assertEqual(0o600, private_path.stat().st_mode & 0o777)

        asset = self.asset("kani-desktop-linux-x64-1.2.3.deb", b"the installer")
        manifest_path = self.root / "release-manifest-v1.json"
        signature_path = self.root / "release-manifest-v1.json.sig"

        self.assertEqual(
            0,
            main(
                [
                    "generate",
                    "--tag", "v1.2.3",
                    "--build-sha", "abc123",
                    "--key-id", "kani-release-key-1",
                    "--private-key", base64.b64encode(private_path.read_bytes()).decode(),
                    "--manifest-out", str(manifest_path),
                    "--signature-out", str(signature_path),
                    str(asset),
                ],
            ),
        )

        published = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertTrue(
            verify_manifest(
                published,
                signature_path.read_bytes(),
                public_path.read_bytes(),
            ),
        )

    def test_the_cli_accepts_a_key_file_and_fails_closed_on_a_bad_tag(self) -> None:
        private_path = self.root / "key.der"
        public_path = self.root / "key.pub.der"
        main(["generate-key", "--private-out", str(private_path), "--public-out", str(public_path)])
        asset = self.asset("kani-desktop-linux-x64-1.2.3.deb")

        with self.assertRaisesRegex(SystemExit, "release manifest error"):
            main(
                [
                    "generate",
                    "--tag", "not-a-tag",
                    "--build-sha", "abc123",
                    "--key-id", "kani-release-key-1",
                    "--private-key", f"@{private_path}",
                    "--manifest-out", str(self.root / "m.json"),
                    "--signature-out", str(self.root / "m.sig"),
                    str(asset),
                ],
            )


if __name__ == "__main__":
    unittest.main()
