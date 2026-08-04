# In-app update security

Kani's in-app updater accepts a GitHub Release APK only after bounded network reads and local artifact checks. These checks fail early and give useful status to the app, but Android's package installer remains the final authority on whether an APK is a valid update.

Sections up to and including "Focused local verification" describe the **Android** path. The desktop path is separate code with a stronger trust root, described in "Desktop update trust root" at the end; the Android updater and its asset selection are unchanged by it.

## Network and download bounds

- The latest-release API response is limited to 1 MiB. A checksum URL ending in `.sha256` is limited to 64 KiB. A declared `Content-Length` over either limit is rejected before the body is read, and an undeclared or chunked body is stopped when it crosses the same limit.
- For a non-successful HTTP response, the updater reads at most 4 KiB from the error body. It flattens newlines and includes at most 160 characters in the reported error.
- APK downloads are limited to 200 MiB using both declared-length and streamed-byte checks. An over-limit or partially failed stream is deleted instead of being left in the cache.
- Release-provided filenames are reduced to their basename before use under the app-private `cache/updates/` directory.

## Artifact acceptance gates

The updater performs these checks before opening an installer session:

1. The release must contain an `.apk` asset and a checksum asset named exactly `<apk-name>.sha256`.
2. The checksum text must contain a 64-character hexadecimal SHA-256 digest. Kani hashes the complete downloaded APK and requires the two digests to match.
3. Android must be able to read the archive metadata. The archive package name must equal Kani's installed package name, its version must be strictly newer than the running version, and its version must match the release tag after removing an optional leading `v`.
4. Android's package metadata must identify compatible signing-certificate relationships for the installed app and archive. Concurrent current-signer sets must match exactly (order-independent); an archive cannot add or remove a simultaneous signer. A forward extension is accepted only when Android reports both sides as verified single-signer proof-of-rotation histories and the archive history contains every certificate in the installed history. Mixed signer-set/history modes, shorter histories, and disjoint identities are rejected.

The SHA-256 file detects corruption but is not by itself a publisher-identity guarantee because a compromised release account could replace both assets. The certificate check adds an early fail-closed identity check. Android's package installer still performs the final cryptographic signature and rotation-lineage verification when the session is committed.

A cached pending APK is inspected again for package, version, and signing certificates before installation resumes. A checksum mismatch, package or version rejection, or certificate rejection blocks installation and deletes any candidate APK that was downloaded.

## Retry classification

Only explicitly transient check/download failures are marked retryable:

- HTTP `408`, `425`, `429`, and every `5xx` status;
- socket timeouts, refused connections, DNS failures, no-route-to-host failures, and socket failures, including those found in the bounded cause chain.

The automatic WorkManager job maps those failures to `Result.retry()`. Other failures—including HTTP `400`/`404`, generic local I/O such as a full disk, malformed release data, checksum or metadata rejection, signing rejection, and runtime failures—are recorded but do not start a WorkManager retry loop. A worker completion result therefore does not mean the update succeeded; it can mean a permanent failure was recorded and the worker stopped retrying.

## Install permission and Android hand-off

If the APK is verified but Kani lacks permission to request package installs, the updater keeps the verified APK as pending state and directs the user to Android's per-app "install unknown apps" settings. Automatic checks also post the pending-update notification instead of launching settings from the background.

The Home route loads the prompt inputs—stored update status and the package-manager permission check—in its background route loader. The rendered UI receives an immutable prompt snapshot and does not perform those database or package-manager reads. The prompt appears only after an update check has completed; after dismissal, it asks again only when a verified update for a different, not-yet-prompted version is waiting.

When the app resumes after permission is granted, `ResumeUpdateInstaller` reads pending status and checks package-install permission on a background executor. It starts at most one in-flight cached install attempt. The PackageInstaller callback may require final user action; manual and resumed cached installs can open Android's confirmation activity, while an automatic callback posts a notification.

## Cached-file lifecycle

- Before a new download, Kani deletes cached `.apk` files at least seven days old, while preserving the current pending APK, fresh APKs, and non-APK files.
- Checksum, package, version, and certificate rejection deletes the candidate APK. An oversized or interrupted download deletes its partial file.
- A PackageInstaller success or terminal failure deletes the cached APK. A pending-user-action callback retains it until the hand-off completes.
- Cache deletion failure is logged; it does not turn a completed installer callback into a crash.

## Focused local verification

The pure validation, retry, prompt, and cache policies run without Android:

```sh
./gradlew :update-core:test \
  --tests dev.bee.kanjianki.updatecore.UpdateArtifactValidatorTest \
  --tests dev.bee.kanjianki.updatecore.UpdateCacheFilePolicyTest \
  --tests dev.bee.kanjianki.updatecore.AutoUpdateRunPolicyTest \
  --tests dev.bee.kanjianki.updatecore.InstallPermissionPromptPolicyTest \
  --tests dev.bee.kanjianki.updatecore.PackageInstallStatusPolicyTest
```

The Android/Robolectric integration surface is covered by:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :app:testDebugUnitTest \
  --tests dev.bee.kanjianki.update.GitHubUpdaterTest \
  --tests dev.bee.kanjianki.update.AutoUpdateWorkerTest \
  --tests dev.bee.kanjianki.update.ResumeUpdateInstallerTest \
  --tests dev.bee.kanjianki.HomeUpdatePermissionDialogModelTest
```

Implementation sources: [GitHubUpdater](../app/src/main/kotlin/dev/bee/kanjianki/update/GitHubUpdater.kt), [UpdateArtifactValidator](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/UpdateArtifactValidator.kt), [UpdateCacheFilePolicy](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/UpdateCacheFilePolicy.kt), [AutoUpdateRunPolicy](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/AutoUpdateRunPolicy.kt), [InstallPermissionPromptPolicy](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/InstallPermissionPromptPolicy.kt), [ResumeUpdateInstaller](../app/src/main/kotlin/dev/bee/kanjianki/update/ResumeUpdateInstaller.kt), and [PackageInstallStatusReceiver](../app/src/main/kotlin/dev/bee/kanjianki/update/PackageInstallStatusReceiver.kt). The test classes above pin the corresponding contract.

## Desktop update trust root

Desktop has no equivalent of Android's package installer to act as the final authority, so Kani has to be the authority itself. It is a signed-manifest scheme, not a checksum scheme.

A co-hosted `SHA256SUMS.txt` is published as a convenience for people verifying downloads by hand. **The updater does not trust it.** Anyone who can replace a release asset can replace a checksum file sitting next to it, so a checksum alone proves only that the download was not corrupted in transit. Trust comes from `release-manifest-v1.json` and its detached Ed25519 signature.

### What the manifest binds

One signed document commits to the schema version, the release tag, the semantic version, the exact build SHA, the signing key id, and — for every asset — its filename, byte size, SHA-256, OS, architecture, and package type. The download's expected size and digest are taken from the verified manifest, never from GitHub's asset listing, which is not signed.

The signature covers *canonical bytes*, not the JSON: UTF-8, a fixed field order, assets sorted by filename, LF newlines, a trailing newline, and no wall-clock field. A general JSON encoder's key ordering and whitespace are implementation details that could drift the signed bytes between library versions.

Those bytes are produced by [`ci/scripts/kani_release_manifest.py`](../ci/scripts/kani_release_manifest.py) and verified by [`ReleaseManifestCodec`](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/ReleaseManifestCodec.kt). A drift between the two would be invisible until a real release failed to install on every host at once, so it is pinned twice: a structural test reads the field order and newline convention out of the Kotlin source rather than restating them, and `ci/fixtures/release-manifest-canonical-golden.txt` pins the exact bytes, asserted from both sides with a byte-equality check between the two copies of the fixture.

Changing the canonical format is a **breaking change to every already-published release**, because old signatures verify only against the old bytes. It requires a schema version bump.

### Key custody, rotation, and revocation

The signing key is Ed25519, generated offline with `kani_release_manifest.py generate-key`.

- The private key lives in the GitHub Actions secret `KANI_RELEASE_SIGNING_KEY` (base64 PKCS#8) and in an offline backup held outside CI. Nothing else holds it.
- The public key ships inside the app, keyed by its key id, so verification needs no network and cannot be redirected.
- **Rotation** is staged: a rotation release ships *both* the old and the new public key, so a client updating from a release signed by the old key still verifies. Only a later release, once the rotation has propagated, drops the old key. Dropping it in the same release would strand every client that had not yet updated.
- **Emergency revocation** is a release that drops the compromised key id. A client that has not updated still trusts the compromised key, so a revocation is a reason to publish quickly — it is not a substitute for key custody.

Signing shells out to `openssl`, which is present on every runner. `cryptography` is not preinstalled, and adding a pip install to the release path would put an unpinned third-party wheel between the artifacts and their signature. The generator never prints or logs private key bytes, and passes keys through mode-0600 files in a private temporary directory rather than argv, which is visible in the process table.

### Acceptance gates before anything is downloaded

[`DesktopUpdatePolicy`](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/DesktopUpdatePolicy.kt) checks, in order:

1. The installation channel is one Kani updates (see below).
2. The release tag is valid semver and strictly newer than what is running. A malformed tag is skipped, not read as `0.0.0`.
3. The manifest's signature verifies under a shipped public key that the manifest's key id names.
4. The manifest names the release tag it was fetched from, and its semantic version matches that tag. A manifest that verifies but names a different release is an older signed manifest replayed onto a newer tag.
5. The manifest describes an asset with the canonical name for this exact OS, architecture, and package type, correctly labelled, that the release actually hosts.

There is **no unsigned path.** A release hosting a matching asset with no valid manifest is skipped, never installed from its co-hosted checksum.

Two conditions are deliberately *not* failures: a newest release carrying only the Android APK, and a release whose desktop manifest is not published yet. Both are normal staged rollout. The search therefore walks up to the 10 newest releases looking for the newest fully valid one, rather than giving up at the first release without a desktop build — bounded, because an unbounded walk would eventually reach releases predating desktop support and spend a request on each.

### Download and hand-off

[`DesktopUpdateStager`](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/DesktopUpdateStager.kt) writes to a private `.partial`, enforces the manifest's exact byte size and a 512 MiB hard ceiling, cuts an over-long or endless stream off mid-stream rather than after it has filled the disk, verifies the SHA-256, fsyncs, and publishes with a strict `ATOMIC_MOVE`. There is no non-atomic fallback: a copy-then-delete would recreate the partial-file-at-the-final-path window the stager exists to close. Any failure deletes the partial.

[`DesktopUpdateHandoffPolicy`](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/DesktopUpdateHandoffPolicy.kt) then requires per-version confirmation. `mayOpenInstaller` returns true only when the confirmed version equals the offered one, so a background check, or a stale confirmation of a different release, can never reach the OS installer.

### Installation channel

[`DesktopInstallationChannelPolicy`](../update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/DesktopInstallationChannelPolicy.kt) works out how the running app was installed from jpackage's `jpackage.app-path` launcher path. This selects the update's package type rather than letting the updater prefer one: handing a `.deb` to someone who unpacked a tarball installs a second, divergent copy.

| Evidence | Channel | Automatic hand-off |
| --- | --- | --- |
| No launcher path (Gradle, source, IDE run) | `UNKNOWN` | No |
| Windows, any install directory | `WINDOWS_MSI` | Yes |
| macOS | `MACOS_DMG` | Yes |
| Linux, `/opt/...` (jpackage deb root) | `LINUX_DEB` | Yes |
| Linux, `/usr/...`, `/app/...` (Flatpak), `/snap/...` | `UNKNOWN` | No |
| Linux, `/usr/local/...` or anywhere else | `LINUX_TAR_GZ` | No (revealed for manual install) |

Windows is not keyed off `Program Files`, because an MSI can be installed per-user or to a chosen directory. Packages Kani does not ship are left to whoever owns them. The portable tarball is downloaded and revealed but never opened, because Kani did not create that layout and must not replace it in place.

The resolved channel is stored in device-local settings (`desktop_installation_channel`) for Settings and diagnostics to report, and is excluded from portable backups — restoring a Linux `.deb` profile onto Windows must not tell the updater it is a `.deb` install. The stored value is never authoritative: the path is re-read every launch, so a tarball upgraded to a `.deb` is noticed rather than remembered wrongly, and an install that stopped being packaged stops being offered updates for the package it used to be.

One acknowledged ambiguity: a tarball deliberately unpacked into `/opt` reads as a `.deb` install and would be offered a `.deb`, leaving the unpacked copy behind. Distinguishing them would need a marker file inside the image; `/opt` is the documented deb root, and unpacking a tarball there is choosing to look like one.

### Desktop verification

```sh
./gradlew :update-core:test \
  --tests dev.bee.kanjianki.updatecore.ReleaseManifestTest \
  --tests dev.bee.kanjianki.updatecore.ReleaseManifestGoldenTest \
  --tests dev.bee.kanjianki.updatecore.DesktopReleaseAssetSelectorTest \
  --tests dev.bee.kanjianki.updatecore.DesktopInstallationChannelPolicyTest \
  --tests dev.bee.kanjianki.updatecore.DesktopUpdatePolicyTest \
  --tests dev.bee.kanjianki.updatecore.DesktopUpdateStagerTest \
  --tests dev.bee.kanjianki.updatecore.DesktopUpdateHandoffPolicyTest \
  --tests dev.bee.kanjianki.updatecore.DesktopUpdateChainTest

python3 -m unittest ci.tests.test_kani_release_manifest ci.tests.test_kani_version
```

`DesktopUpdateChainTest` is the composed claim: it runs channel detection, policy evaluation, staging, and hand-off together and asserts that a correct-but-unsigned release, an attacker-signed manifest, a wrong-architecture asset, a signed downgrade, and a tampered download each leave nothing staged and nothing installable.
