#!/usr/bin/env python3
"""Generate and sign the deterministic desktop release manifest.

Goal 202 defines and tests the manifest schema, generator, and verification with a
fixture key. Goal 206 alone generates the production manifest, from the final
post-signing/notarization bytes, using the custodial key -- this script is the same
code path either way, so what CI runs in production is what the tests exercise.

The canonical bytes must match `:update-core`'s ReleaseManifestCodec exactly, because
that is what the desktop app verifies the signature against. Both sides are pinned by
tests, and `ci/tests/test_kani_release_manifest.py` asserts the two agree.

Key custody, rotation, and revocation
-------------------------------------
The signing key is Ed25519. It is generated offline with `generate-key`, and only the
private key's Actions-secret copy is ever online:

* the private key lives in the GitHub Actions secret `KANI_RELEASE_SIGNING_KEY`
  (base64 PKCS#8) and in an offline backup held outside CI;
* the public key ships inside the app, keyed by its key id, so verification needs no
  network and cannot be redirected;
* a rotation release ships BOTH the old and the new public key, so a client updating
  from a release signed by the old key still verifies. Only a later release, once the
  rotation has propagated, drops the old key;
* an emergency revocation is a release that drops the compromised key id. A client that
  has not updated still trusts it, so a revocation is a reason to publish quickly, not a
  substitute for key custody.

This script never prints or logs private key bytes.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Iterable, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))

from kani_version import (  # noqa: E402
    DESKTOP_TARGETS,
    parse_tag,
)


SCHEMA_VERSION = 1
ASSET_NAME_PATTERN = re.compile(
    r"^kani-desktop-(?P<os>[a-z0-9]+)-(?P<arch>[a-z0-9]+)-"
    r"(?P<version>\d+\.\d+\.\d+)(?P<extension>\.[a-z.]+)$",
)
EXTENSION_PACKAGE_TYPES = {
    extension: extension.lstrip(".")
    for _, _, extensions in DESKTOP_TARGETS
    for extension in extensions
}


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def describe_asset(path: Path, version_name: str) -> dict[str, object]:
    """The manifest entry for one asset, derived from its canonical filename.

    Deriving the platform from the filename rather than accepting it as an argument
    means a mislabelled asset cannot be signed: the name is what the client matches on,
    so the name is the only source for what the asset claims to be.
    """
    match = ASSET_NAME_PATTERN.fullmatch(path.name)
    if match is None:
        raise ValueError(f"asset {path.name} is not a canonical desktop asset name")
    if match.group("version") != version_name:
        raise ValueError(
            f"asset {path.name} is version {match.group('version')}, "
            f"expected {version_name}",
        )
    extension = match.group("extension")
    package_type = EXTENSION_PACKAGE_TYPES.get(extension)
    if package_type is None:
        raise ValueError(f"asset {path.name} has an unsupported package type {extension}")
    size = path.stat().st_size
    if size <= 0:
        raise ValueError(f"asset {path.name} is empty")
    return {
        "filename": path.name,
        "sizeBytes": size,
        "sha256": sha256_of(path),
        "os": match.group("os"),
        "arch": match.group("arch"),
        "packageType": package_type,
    }


def build_manifest(
    tag: str,
    build_sha: str,
    key_id: str,
    assets: Iterable[Path],
) -> dict[str, object]:
    version = parse_tag(tag)
    described = [describe_asset(Path(asset), version.name) for asset in assets]
    if not described:
        raise ValueError("a release manifest must describe at least one asset")
    filenames = [asset["filename"] for asset in described]
    if len(set(filenames)) != len(filenames):
        raise ValueError("duplicate asset filename in manifest")
    if not build_sha.strip():
        raise ValueError("build sha must not be blank")
    if not key_id.strip():
        raise ValueError("key id must not be blank")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "releaseTag": version.tag,
        "semanticVersion": version.name,
        "buildSha": build_sha.strip(),
        "keyId": key_id.strip(),
        # Sorted here so the JSON document and the canonical bytes agree on order.
        "assets": sorted(described, key=lambda asset: asset["filename"]),
    }


def canonical_bytes(manifest: dict[str, object]) -> bytes:
    """The exact bytes the signature covers.

    A flat `key:value` line set, matching :update-core's ReleaseManifestCodec: UTF-8,
    fixed field order, assets sorted by filename, LF newlines, trailing newline, and no
    wall-clock field. Deliberately not the JSON serialization -- JSON whitespace and key
    order are an implementation detail that could drift the signed bytes between library
    versions.
    """
    assets = manifest["assets"]
    assert isinstance(assets, list)
    lines = [
        f"schemaVersion:{manifest['schemaVersion']}",
        f"releaseTag:{manifest['releaseTag']}",
        f"semanticVersion:{manifest['semanticVersion']}",
        f"buildSha:{manifest['buildSha']}",
        f"keyId:{manifest['keyId']}",
        f"assetCount:{len(assets)}",
    ]
    for asset in sorted(assets, key=lambda entry: entry["filename"]):
        lines += [
            f"asset:{asset['filename']}",
            f"size:{asset['sizeBytes']}",
            f"sha256:{asset['sha256']}",
            f"os:{asset['os']}",
            f"arch:{asset['arch']}",
            f"packageType:{asset['packageType']}",
        ]
    return ("\n".join(lines) + "\n").encode("utf-8")


def manifest_json(manifest: dict[str, object]) -> str:
    """The published manifest document: sorted keys, LF, and a trailing newline."""
    return json.dumps(manifest, indent=2, sort_keys=True, ensure_ascii=True) + "\n"


def _openssl(arguments: Sequence[str], stdin: bytes | None = None) -> bytes:
    """Run openssl, which is present on every runner, so no pip dependency is added.

    Ed25519 needs a real implementation; `cryptography` is not preinstalled on the
    runners this repo uses and adding a pip install to the release path would put an
    unpinned third-party wheel between the artifacts and their signature.
    """
    completed = subprocess.run(
        ["openssl", *arguments],
        input=stdin,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return completed.stdout


def sign_manifest(manifest: dict[str, object], private_key_pkcs8: bytes) -> bytes:
    """The detached Ed25519 signature over the canonical bytes.

    The key and the message go through files inside a private temporary directory
    because openssl's Ed25519 signing needs a seekable input, and a key passed on the
    command line would be visible in the process table.
    """
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        key_path = root / "key.der"
        key_path.touch(mode=0o600)
        key_path.write_bytes(private_key_pkcs8)
        message_path = root / "manifest.canonical"
        message_path.write_bytes(canonical_bytes(manifest))
        try:
            return _openssl(
                [
                    "pkeyutl", "-sign", "-rawin",
                    "-inkey", str(key_path), "-keyform", "DER",
                    "-in", str(message_path),
                ],
            )
        except subprocess.CalledProcessError as error:
            # Report the failure without echoing key material.
            raise ValueError("could not sign with the supplied Ed25519 key") from error


def verify_manifest(
    manifest: dict[str, object],
    signature: bytes,
    public_key_x509: bytes,
) -> bool:
    """Verify a signature the way the app does, for a CI self-check after signing."""
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        key_path = root / "public.der"
        key_path.write_bytes(public_key_x509)
        message_path = root / "manifest.canonical"
        message_path.write_bytes(canonical_bytes(manifest))
        signature_path = root / "manifest.sig"
        signature_path.write_bytes(signature)
        try:
            _openssl(
                [
                    "pkeyutl", "-verify", "-rawin",
                    "-pubin", "-inkey", str(key_path), "-keyform", "DER",
                    "-in", str(message_path),
                    "-sigfile", str(signature_path),
                ],
            )
        except subprocess.CalledProcessError:
            # Any verification failure is a rejection, never a retry.
            return False
        return True


def generate_key() -> tuple[bytes, bytes]:
    """A fresh Ed25519 keypair as (PKCS#8 private, X.509 public) DER bytes."""
    with tempfile.TemporaryDirectory() as directory:
        key_path = Path(directory) / "key.der"
        key_path.touch(mode=0o600)
        _openssl(
            ["genpkey", "-algorithm", "ED25519", "-outform", "DER", "-out", str(key_path)],
        )
        private_der = key_path.read_bytes()
        public_der = _openssl(
            ["pkey", "-in", str(key_path), "-inform", "DER", "-pubout", "-outform", "DER"],
        )
    return private_der, public_der


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate = subparsers.add_parser(
        "generate",
        help="write the manifest and its detached signature",
    )
    generate.add_argument("--tag", required=True)
    generate.add_argument("--build-sha", required=True)
    generate.add_argument("--key-id", required=True)
    generate.add_argument(
        "--private-key",
        required=True,
        help="base64 PKCS#8 Ed25519 private key, or @path to a DER file",
    )
    generate.add_argument("--manifest-out", required=True, type=Path)
    generate.add_argument("--signature-out", required=True, type=Path)
    generate.add_argument("assets", nargs="+", type=Path)

    keygen = subparsers.add_parser(
        "generate-key",
        help="write a fresh offline Ed25519 keypair",
    )
    keygen.add_argument("--private-out", required=True, type=Path)
    keygen.add_argument("--public-out", required=True, type=Path)
    return parser


def _read_private_key(value: str) -> bytes:
    if value.startswith("@"):
        return Path(value[1:]).read_bytes()
    return base64.b64decode(value, validate=True)


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "generate-key":
            private_der, public_der = generate_key()
            # 0600 before any bytes land, so the private key is never briefly readable.
            args.private_out.touch(mode=0o600, exist_ok=True)
            args.private_out.write_bytes(private_der)
            args.public_out.write_bytes(public_der)
            print(f"public key base64: {base64.b64encode(public_der).decode('ascii')}")
            return 0

        manifest = build_manifest(
            tag=args.tag,
            build_sha=args.build_sha,
            key_id=args.key_id,
            assets=args.assets,
        )
        signature = sign_manifest(manifest, _read_private_key(args.private_key))
        args.manifest_out.write_text(manifest_json(manifest), encoding="utf-8")
        args.signature_out.write_bytes(signature)
        print(f"signed {len(manifest['assets'])} assets for {manifest['releaseTag']}")
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        # Never echo key material in an error path.
        raise SystemExit(f"release manifest error: {error}") from error
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
