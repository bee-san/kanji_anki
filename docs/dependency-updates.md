# Dependency update automation

Kani uses Renovate for unattended repository dependency update pull requests.

Renovate owns:

- GitHub Actions updates, grouped into one weekly PR and configured to pin action references by digest.
- Gradle wrapper updates, kept separate from library/plugin updates.
- Gradle dependency and plugin updates from `*.gradle.kts`, `gradle.properties`, and the `gradle/libs.versions.toml` version catalog.

Dependabot version-update PRs are intentionally not configured. Running Dependabot and Renovate against the same Gradle and GitHub Actions files would create duplicate update PRs. Dependabot security alerts can still be enabled in repository settings without adding `.github/dependabot.yml` version-update schedules.

Noise controls:

- Renovate targets `main` only.
- Update PR creation is scheduled before 06:00 on Mondays.
- Non-major Gradle dependency/plugin updates are grouped.
- GitHub Actions updates are grouped.
- Automerge is disabled; dependency PRs must pass CI and be reviewed or merged by the normal repo policy.

Pull-request safety gates live in `.github/workflows/android-ci.yml`: Gradle wrapper validation blocks on wrapper-integrity failures, while dependency review reports new dependency risk when GitHub Dependency graph is enabled for the repository. The dependency review step is intentionally non-blocking until Dependency graph is enabled in repository security settings; otherwise GitHub fails the action before it can inspect the PR.

## Cross-host verification metadata bootstrap

Desktop dependencies include host-specific native artifacts. A dependency
update that changes the desktop graph must therefore collect Gradle
verification metadata independently on all three supported CI hosts:
Ubuntu 24.04 x64, Windows 2025 x64, and macOS 15 arm64. This is a temporary,
explicitly authorized bootstrap procedure, not a permanent CI mode.

Start all three jobs from clean checkouts of the exact same commit SHA and the
same pristine `gradle/verification-metadata.xml`. Record that baseline file's
SHA-256 before running Gradle. Never bootstrap from three independently moving
branch heads.

Each job validates the wrapper and then resolves the complete desktop surface
in write mode:

```sh
# Ubuntu and macOS
./gradlew ciDesktop ciDesktopPackage --write-verification-metadata sha256
```

```powershell
# Windows
.\gradlew.bat ciDesktop ciDesktopPackage --write-verification-metadata sha256
```

Copy each complete generated file to its exact platform name:

- `verification-metadata-linux.xml`
- `verification-metadata-windows.xml`
- `verification-metadata-macos.xml`

Upload those as three separate CI artifacts, together with each job's commit
SHA, baseline SHA-256, generated-file SHA-256, and baseline-to-generated diff.
Do not pass one host's modified Gradle file into another host's job.

After downloading the three artifacts into one directory tree, produce the
review bundle with:

```sh
python3 tools/merge_verification_metadata.py \
  --baseline gradle/verification-metadata.xml \
  --input-directory artifacts \
  --output artifacts/verification-metadata-merged.xml \
  --manifest artifacts/verification-metadata-merge-manifest.json \
  --review-summary artifacts/verification-metadata-review.md \
  --expected-baseline-sha256 BASELINE_SHA256
```

The merger recursively finds exactly one of each required platform filename.
It requires identical schema and verification configuration, proves that no
baseline component, artifact, or checksum changed or disappeared, rejects
trusted-artifact/ignored-key configuration, rejects non-SHA-256 additions and
cross-host checksum conflicts, and creates a sorted additive union. It emits
the merged XML, a deterministic JSON provenance manifest, a Markdown review
summary, and a unified diff at
`artifacts/verification-metadata-merged.xml.diff`.

Review every coordinate, artifact, checksum, platform contribution, and diff
line before replacing the repository metadata. Do not add ignored artifacts,
trusted-artifact bypasses, ignored keys, or weaker verification flags to make a
host pass.

Once the reviewed union is committed, remove every temporary
`--write-verification-metadata` flag, bootstrap-only workflow path, and
write-mode condition. The permanent matrix must use strict verification and
prove that resolution makes no metadata changes:

```sh
# Ubuntu and macOS
./gradlew ciDesktop ciDesktopPackage --dependency-verification=strict
git diff --exit-code -- gradle/verification-metadata.xml
```

```powershell
# Windows
.\gradlew.bat ciDesktop ciDesktopPackage --dependency-verification=strict
git diff --exit-code -- gradle/verification-metadata.xml
```

The permanent strict matrix is the gate. A successful temporary write-mode run
alone is not evidence that the reviewed metadata is complete or safe.
