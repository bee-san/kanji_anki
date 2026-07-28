# Offline Resilience Audit (Kani / bee-san/kanji_anki)

Branch: `fix/offline-resilience`
Scope: this document began as the audit + regression-harness baseline for the
audit card (`t_51ce84ae`). It now also records the fixes delivered by the child
card (`t_22aceb86`) and the hermetic network-fault CI coverage built on top of
the harness (JVM matrix in `ciFast` plus `@DeviceRisk` on-device parity in the
API 26/35 emulator lanes). It remains the evidence-backed baseline and the
regression matrix later cards work against.

## 1. Method

- Read `AGENTS.md` and the update/security/provider docs first.
- Statically mapped every outbound network touchpoint and every user-visible
  surface reachable without an account.
- Built a reusable, dependency-injected network-fault harness
  (`app/src/test/java/dev/bee/kanjianki/offline/NetworkFaultTransport.kt`) that
  reproduces the full transport-fault catalogue locally. **No test touches the
  public Internet.**
- Pinned the offline contract of the one Kani-controlled touchpoint (the
  in-app updater) with `UpdaterOfflineContractTest`, using Robolectric so the
  real `GitHubUpdater` + `LocalStore` persistence path runs on the JVM.

## 2. Network touchpoint map

Kani is a local-first flashcard app. A full source scan for raw HTTP
(`HttpURLConnection`, `URL(...).openConnection()`, OkHttp) and for
connectivity APIs found exactly the touchpoints below.

| # | Touchpoint | Transport owner | Kani-controlled? | Offline contract |
|---|-----------|-----------------|------------------|------------------|
| 1 | In-app updater — GitHub `releases/latest` JSON, `.sha256`, APK download (`update/GitHubUpdater.kt`) | Kani (`HttpURLConnection` via `AndroidUpdateClient`) | **Yes** | Fail soft; classify connectivity vs. permanent; keep a retry affordance; never install unverified bytes |
| 2 | Handwriting recognition model download — ML Kit Digital Ink (`study/MlKitJapaneseWritingRecognizer.kt`) | Google Play Services | No (Play owns the wire) | Fail soft; `write_kanji` degrades to "checker not downloaded"; the study ladder keeps working on other rungs |
| 3 | AnkiDroid content-provider sync (`sync-*`, `anki/*`) | Local IPC (content provider), **not network** | n/a | Local-only; no Internet dependency |

There is **no** analytics, telemetry, remote-config, account, or remote-asset
touchpoint. The AnkiDroid provider is local IPC, not a network call, so an
offline device does not affect sync/provider behavior at the transport layer.

## 3. Surface inventory (reachable without an account / offline)

All of the following are backed by the local SQLite store and Room migrations;
none performs network I/O on any code path:

- Startup / onboarding, dependency init, DB open + migrations.
- Local dictionary / search.
- Study session creation, answer persistence, scheduler transitions.
- Review history / stats / widgets.
- Settings.
- Backup / export / import (`VACUUM INTO` snapshots on API 30+).

Static review found **no startup gate on connectivity** and **no network I/O on
the main thread** in these surfaces. They are correctly local-first. The audit
therefore focuses fault injection on touchpoint #1, the only surface where an
offline device changes observable behavior and the only one whose transport
Kani owns.

## 4. Transport-fault catalogue (harness)

`NetworkFaultTransport.Fault` maps each real-world connectivity failure named
in the audit card to a deterministic effect on an HTTP read:

| Fault | Reproduced as | Card scenario covered |
|-------|---------------|-----------------------|
| `NO_ROUTE` | `NoRouteToHostException` | airplane / no-route |
| `DNS_FAILURE` | `UnknownHostException` | DNS failure |
| `CONNECTION_REFUSED` | `ConnectException` | connection refused |
| `BLACK_HOLE_TIMEOUT` | `SocketTimeoutException` | black-holed timeout |
| `SLOW_RESPONSE_TIMEOUT` | `SocketTimeoutException` ("slow response") | slow / high-latency response, packet loss (surfaces as a read timeout) |
| `TLS_HANDSHAKE_FAILURE` | `SSLHandshakeException` | TLS / proxy / captive-portal TLS |
| `MID_REQUEST_DISCONNECT` | `SocketException` | mid-request disconnect / midstream drop |
| `CANCELLED_READ` | `InterruptedIOException` | request cancellation (thread interrupt / WorkManager stop / process backgrounded) |
| `TRUNCATED_JSON` | HTTP 200 + partial/corrupt JSON body | malformed / partial payload |
| `CAPTIVE_PORTAL_HTML` | HTTP 200 + login HTML body | captive-portal / transparent-proxy interception |

In addition to the single-fault catalogue, the harness scripts a `flappingClient`
that walks a sequence of faults and healthy responses across successive
`getText` calls, driving the online↔offline flapping scenario directly at the
updater seam.

Cold/warm launch, force-stop relaunch, flapping, high-latency/packet-loss, and
recovery are all reducible, at the updater seam, to one of the transport
outcomes above plus the persisted `LocalStore` flag state that survives process
death; the harness drives that seam directly and asserts on the persisted flag.
The flapping and process-restart invariants are pinned explicitly
(`flappingConnectivityKeepsRetryFlagInSyncWithReality`,
`retryFlagSurvivesProcessRestart`).

## 5. Regression matrix — updater offline contract

Command:

```sh
./gradlew :app:testDebugUnitTest \
  --tests "dev.bee.kanjianki.offline.UpdaterOfflineContractTest" --no-daemon
```

Current (branch `fix/offline-resilience`): `15 tests completed, 0 failed` —
every fault classifies correctly and the two originally-confirmed defects are
now locked as passing guards. Full JUnit XML:
`app/build/test-results/testDebugUnitTest/TEST-dev.bee.kanjianki.offline.UpdaterOfflineContractTest.xml`.

RED evidence (TDD): the 6 tests added on top of the original 5 socket guards
fail against pre-fix production behavior. Temporarily removing the
`isValidSemver` unusable-body guard collapses the captive-portal and
truncated-JSON cases to "already on version" (5 failures across the two
`captivePortalHtml*`/`truncatedJson*` families), and naively adding
`InterruptedIOException` to `retryableFailure`'s allowlist makes
`cancelledReadIsNotRetryableAndDoesNotLightRetryFlag` fail — confirming each
new test guards a real production decision, not tautology.

| Fault | Expected offline contract | Classification today | Crash/ANR/data-loss | Recovery | Status |
|-------|---------------------------|----------------------|---------------------|----------|--------|
| `NO_ROUTE` | retryable failure, flag lit, not "up to date" | correct | none | retry when online | ✅ guard passes |
| `DNS_FAILURE` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `CONNECTION_REFUSED` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `BLACK_HOLE_TIMEOUT` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `SLOW_RESPONSE_TIMEOUT` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `MID_REQUEST_DISCONNECT` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `CANCELLED_READ` | **non-retryable**, flag NOT lit, not "up to date" | correct | none | resumes on next check; no false retry banner | ✅ guard passes |
| `TRUNCATED_JSON` | retryable failure, flag lit, not "up to date" | correct | none | retry when online | ✅ guard passes (was Defect A family) |
| `TLS_HANDSHAKE_FAILURE` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes (fixed Defect B) |
| `CAPTIVE_PORTAL_HTML` | not "up to date"; retryable; flag lit | correct | none | retry when online | ✅ guard passes (fixed Defect A) |
| flapping (DNS → healthy → refused) | flag tracks reality: lit, cleared, re-lit | correct | none | banner matches live connectivity | ✅ guard passes |
| process restart with lit flag | persisted flag survives store reopen | correct | none | Home shows retry after relaunch | ✅ guard passes |

On-device (API 26 + API 35, via the device-smoke / instrumented emulator lanes):
`UpdateFlowInstrumentedTest` pins the same classification contract on a real
Android runtime with `@DeviceRisk`-annotated tests
(`offlineNoRouteIsRetryableConnectivityFailureOnDevice`,
`offlineTlsHandshakeFailureIsRetryableConnectivityFailureOnDevice`,
`offlineCaptivePortalHtmlIsNotReportedAsAlreadyCurrentOnDevice`,
`offlineCancelledReadIsNotRetryableAndDoesNotLightRetryFlagOnDevice`,
`offlineRetryFlagSurvivesProcessRestartOnDevice`). These run on the emulator
lanes only; instrumentation compilation stays in `ciFast` so a broken device
test fails the fast gate even without an emulator.

## 6. Originally-confirmed defects (now fixed)

Both defects below were confirmed by this audit and subsequently fixed by the
child card `t_22aceb86` (commits `58c7fac7` / `387a3dd5`). The audit tests that
originally reproduced them are now passing regression guards; the descriptions
are retained for provenance.

### Defect A — captive portal misreported as "already up to date"

- **Where:** `GitHubUpdater.checkDownloadAndInstall` +
  `GitHubReleaseMetadataParser.parseLatest` + `ReleaseVersion.isNewerSemver` +
  `recordResult`.
- **Root cause:** A captive portal answers the releases API with HTTP 200 and
  an HTML login page. The tolerant JSON parser finds no `tag_name` and returns
  `""`. `ReleaseVersion.isNewerSemver(current, "")` parses `""` as `0.0.0`, so
  it is never "newer", and the method returns
  `UpdateTextPolicy.alreadyOnVersionMessage(...)` with `retryable = false`.
  `recordResult` then takes the `!result.retryable` branch and calls
  `clearUpdateCheckFailed()`.
- **User-visible effect:** Home shows no retry affordance and the user believes
  they are on the latest version while actually offline behind a portal. A
  network outage is represented as an empty *successful* remote response —
  exactly the failure mode the child card's contract forbids.
- **Evidence (at audit time, since fixed):**
  `captivePortalHtmlMustNotBeReportedAsAlreadyOnVersion`
  (actual message was `Already on 0.4.204.`),
  `captivePortalHtmlIsRetryableConnectivityFailure`,
  `captivePortalHtmlLightsTheUpdateCheckFailedFlag` — all failed pre-fix, all
  pass now.

### Defect B — TLS handshake failure classified as permanent

- **Where:** `GitHubUpdater.retryableFailure(IOException)`.
- **Root cause:** The retryable-classification cause-walk matches only
  `SocketTimeoutException`, `ConnectException`, `UnknownHostException`,
  `NoRouteToHostException`, and `SocketException`. `SSLHandshakeException`
  (and its `javax.net.ssl.SSLException` family) is not matched, so a TLS
  negotiation failure — the signature of an intercepting proxy / captive
  portal with a bad cert, or a broken TLS-terminating middlebox — falls through
  to `retryable = false`. `recordResult` then clears the update-check-failed
  flag.
- **User-visible effect:** A recoverable connectivity/interception failure is
  treated as permanent; the retry affordance is cleared and the outage is
  swallowed silently.
- **Evidence (at audit time, since fixed):**
  `tlsHandshakeFailureIsRetryableConnectivityFailure` failed pre-fix
  (`result.retryable` was `false`) and passes now.

Both defects were misclassification bugs, not crashes; there was **no crash,
ANR, or data-loss risk** on any updater path. No local surface was blocked by
either defect — they only affected the update-check UX.

## 7. Resolution (child card `t_22aceb86`, commits `58c7fac7` / `387a3dd5`)

The harness and tests were kept and made green one invariant at a time, without
widening scope beyond classification:

1. **Defect B:** `SSLException`/`SSLHandshakeException` (and the `javax.net.ssl`
   family, via `SSLException`) were added to the retryable cause-walk in
   `GitHubUpdater.retryableFailure`.
2. **Defect A:** a successful HTTP read that yields no usable `tag_name`/semver
   is now treated as a connectivity/interception failure via the
   `!ReleaseVersion.isValidSemver(latest.tagName())` guard in
   `checkDownloadAndInstall`, which returns a retryable failure so
   `recordResult` lights (does not clear) the update-check-failed flag instead
   of collapsing the empty tag to `0.0.0` and reporting "already on version".

Signature-verification and APK-validation behavior are unchanged; the
captive-portal `download` path still writes HTML that is rejected by
checksum/APK validation. Commit `387a3dd5` additionally surfaces the lit flag
as a recoverable Home banner with retry.

The current offline-first policy further scopes that persisted Home retry flag
to **manual** update checks. Automatic checks remain retryable for WorkManager,
but a transient background GitHub/DNS/TLS failure no longer creates a prominent
Home warning; successful or permanent outcomes still clear stale flags. This
keeps local study fully calm and usable regardless of Internet availability.

The regression matrix was subsequently expanded (this branch) to cover slow /
high-latency responses, malformed/truncated payloads, request cancellation,
online↔offline flapping, and process restart with pending work, and mirrored
on-device at API 26/35 via `@DeviceRisk` instrumentation.

## 8. Coverage limits / residual risk

- Host-side JVM evidence (Robolectric `sdk = [35]`, `UpdaterOfflineContractTest`)
  is deterministic and Internet-free and runs in `ciFast`. On-device parity
  (`UpdateFlowInstrumentedTest`, `@DeviceRisk`) runs in the API 26/35 emulator
  lanes (`android-device-smoke.yml` risk suite and the nightly/dispatch
  `android-instrumented.yml`); local runs require KVM, which the audit/CI-worker
  hosts may lack, so on-device execution is delegated to the CI emulator lanes
  while its compilation is enforced in `ciFast`.
- Genuine radio-level shaping (airplane toggling, `tc`-based latency/packet
  loss) is deliberately not used: the offline audit established the updater's
  `UpdateClient` as the app's single outbound network touchpoint, so injecting
  the transport fault at that seam is both hermetic and exhaustive for the
  observable contract, and avoids the emulator-networking flakiness the release
  path is explicitly kept free of.
- ML Kit model download (touchpoint #2) is transport-owned by Play Services;
  its offline behavior is asserted through the `WritingRecognizer` fakes in the
  study tests, not this harness, by design.

## 9. End-to-end validation run (2026-07-20, task t_2fc51229)

Full-matrix validation of the branch head `65757ed8` on this task. No new
offline regression was found; all discovered defects were already fixed and
regression-pinned by the child tasks above, so this run is verification-only
(no production-code change).

Host-side (no local `/dev/kvm`; emulator binaries/system-images absent, so
on-device execution was delegated to the authorized CI emulator lanes):

- `ciFast` and `ciQuality`: BUILD SUCCESSFUL.
- Forced-rerun (`--rerun-tasks`, 56 tasks executed) of the JVM surface:
  full `:app:testDebugUnitTest` = 1334 tests / 0 failures / 0 errors / 0 skipped;
  `UpdaterOfflineContractTest` 15/0, `AutoSyncRunnerTest` 4/0,
  `HomeUpdateCheckFailedBannerComposeTest` 3/0, `ReleaseVersionTest` 5/0.
- Debug + androidTest APKs assembled and verified: `dev.bee.kanjianki`,
  versionName `0.4.204`, versionCode `4204`, minSdk 26, targetSdk 36,
  APK Signature Scheme v2 with the Android Debug certificate (debug build,
  not a release). `app-debug.apk` SHA-256
  `b424d5c6efdba04ec44ced7223f6392d95cc05404622a3af82e3a9b372f0b567`.

Authorized CI emulator evidence (all green on this branch head):

- `Android CI` (Fast confidence gate): success — JVM/app-unit/lint/androidTest
  compile/dependency-safety all green.
- `android-device-smoke.yml`: Device smoke API 26 (Android 8.0.0, 6 tests on
  emulator) success; Device smoke API 35 (6 tests) success; Device risk suite
  API 35 (103 tests on emulator, includes the `@DeviceRisk` offline update-flow
  parity tests) success.
- `android-instrumented.yml` (live AnkiDroid provider fixture, `workflow_dispatch`
  on `fix/offline-resilience`, run 29731132733): API 26 and API 35 both
  `OK (62 tests)` against real AnkiDroid 2.24.0 using the pinned sanitized Kiku
  CI fixture (`ci/scripts/create_ankidroid_kiku_fixture.py`; never a private
  collection), plus the retired-lifecycle sub-fixtures. Note tags remain the
  only supported provider write surface; no provider/sync production code
  changed on this branch.
