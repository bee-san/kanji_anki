# In-app update security

Kani's in-app updater accepts a GitHub Release APK only after bounded network reads and local artifact checks. These checks fail early and give useful status to the app, but Android's package installer remains the final authority on whether an APK is a valid update.

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
4. The archive signing-certificate set or signing lineage must contain every certificate in the installed app's set or lineage. The comparison is order-independent. This accepts a valid forward rotation that appends a signer, while rejecting missing certificates, a shorter lineage, or a disjoint signer set.

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
