import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "run_local_ankidroid_fixture.sh"
CHECKSUMS = REPO_ROOT / "ci" / "fixtures" / "ankidroid" / "ankidroid-2.24.0.sha256"


def write_executable(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")
    path.chmod(0o755)


class RunLocalAnkiDroidFixtureTest(unittest.TestCase):
    def test_refuses_connected_physical_device(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            collection = root / "collection.anki2"
            collection.write_bytes(b"collection")
            apk = root / "ankidroid.apk"
            apk.write_bytes(b"apk")
            prepared_sdk = root / "android-sdk"
            prepared_sdk.mkdir()
            sdk_capture = root / "resolved-android-home.txt"

            write_executable(
                bin_dir / "emulator",
                """#!/usr/bin/env bash
printf '%s\n' "${ANDROID_HOME:-}" > "${SDK_CAPTURE_PATH}"
if [[ "$*" == "-list-avds" ]]; then
  echo kanji_anki_api35_local
fi
""",
            )
            write_executable(bin_dir / "avdmanager", "#!/usr/bin/env bash\nexit 0\n")
            write_executable(
                bin_dir / "adb",
                """#!/usr/bin/env bash
case "$*" in
  start-server) exit 0 ;;
  devices) printf 'List of devices attached\\nphysical-123\\tdevice\\n' ;;
  get-state) echo device ;;
  get-serialno) echo physical-123 ;;
  "shell getprop ro.kernel.qemu") echo 0 ;;
  *) exit 0 ;;
esac
""",
            )

            env = os.environ.copy()
            env.update(
                {
                    "PATH": f"{bin_dir}:{env['PATH']}",
                    "HOME": str(root),
                    "ANKIDROID_APK": str(apk),
                    "KANJI_ANKIDROID_WORK_DIR": str(root / "work"),
                    "SDK_CAPTURE_PATH": str(sdk_capture),
                }
            )
            env.pop("ANDROID_HOME", None)
            env.pop("ANDROID_SDK_ROOT", None)
            result = subprocess.run(
                ["bash", str(SCRIPT), str(collection)],
                cwd=REPO_ROOT,
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=10,
                check=False,
            )
            resolved_android_home = sdk_capture.read_text(encoding="utf-8").strip()

        self.assertEqual(2, result.returncode, result.stdout)
        self.assertIn("Refusing to overwrite AnkiDroid data on non-emulator", result.stdout)
        self.assertIn("physical-123", result.stdout)
        self.assertEqual(str(prepared_sdk), resolved_android_home)

    def test_uses_checked_in_hashes_and_partial_downloads(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")

        self.assertIn('android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"', source)
        self.assertIn("ci/fixtures/ankidroid/ankidroid-2.24.0.sha256", source)
        self.assertIn('local partial_path="${apk_path}.partial"', source)
        self.assertIn("verify_apk_sha256", source)
        self.assertIn('"${HOME}/android-sdk"', source)
        self.assertIn('"/tmp/android-sdk"', source)

    def test_checksums_pin_both_supported_emulator_architectures(self) -> None:
        checksums = {
            filename: digest
            for digest, filename in (
                line.split()
                for line in CHECKSUMS.read_text(encoding="utf-8").splitlines()
                if line.strip()
            )
        }

        self.assertEqual(
            {
                "AnkiDroid-2.24.0-arm64-v8a.apk": "7f498e77b372344ec3f2da98590be1c476c40e706b88d27cac0a45bd734489f8",
                "variant-abi-AnkiDroid-2.24.0-x86_64.apk": "b8aaef8c8ed13e96b7bbafbc46e690490684192147ab445db8a193c4ef6989b0",
            },
            checksums,
        )


if __name__ == "__main__":
    unittest.main()
