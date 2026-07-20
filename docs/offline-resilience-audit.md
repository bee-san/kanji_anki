# Offline Resilience Audit (Kani / bee-san/kanji_anki)

Branch: `fix/offline-resilience`
Scope: audit + regression harness only. **No production fix is made by the
audit card** (`t_51ce84ae`); the fixes belong to the child card
(`t_22aceb86`). This document is the evidence-backed baseline and the
regression matrix that later cards work against.

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
| `TLS_HANDSHAKE_FAILURE` | `SSLHandshakeException` | TLS / proxy / captive-portal TLS |
| `MID_REQUEST_DISCONNECT` | `SocketException` | mid-request disconnect |
| `CAPTIVE_PORTAL_HTML` | HTTP 200 + login HTML body | captive-portal / transparent-proxy interception |

Cold/warm launch, force-stop relaunch, flapping, high-latency/packet-loss, and
recovery are all reducible, at the updater seam, to one of the transport
outcomes above plus the persisted `LocalStore` flag state that survives process
death; the harness drives that seam directly and asserts on the persisted flag.

## 5. Regression matrix — updater offline contract

Command:

```sh
./gradlew :app:testDebugUnitTest \
  --tests "dev.bee.kanjianki.offline.UpdaterOfflineContractTest" --no-daemon
```

Observed: `9 tests completed, 4 failed` (5 pass as regression guards, 4 fail as
confirmed defects). Full JUnit XML:
`app/build/test-results/testDebugUnitTest/TEST-dev.bee.kanjianki.offline.UpdaterOfflineContractTest.xml`.

| Fault | Expected offline contract | Observed today | Crash/ANR/data-loss | Recovery | Status |
|-------|---------------------------|----------------|---------------------|----------|--------|
| `NO_ROUTE` | retryable failure, flag lit, not "up to date" | correct | none | retry when online | ✅ guard passes |
| `DNS_FAILURE` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `CONNECTION_REFUSED` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `BLACK_HOLE_TIMEOUT` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `MID_REQUEST_DISCONNECT` | retryable failure, flag lit | correct | none | retry when online | ✅ guard passes |
| `TLS_HANDSHAKE_FAILURE` | retryable failure, flag lit | **non-retryable; flag cleared** | none (silent) | **no retry affordance until next manual check** | ❌ **Defect B** |
| `CAPTIVE_PORTAL_HTML` | not "up to date"; retryable; flag lit | **reported "Already on 0.4.204"; non-retryable; flag cleared** | none (silent, misleading) | **no retry; user believes they are current** | ❌ **Defect A** |

## 6. Confirmed defects

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
- **Evidence:** `captivePortalHtmlMustNotBeReportedAsAlreadyOnVersion`
  (actual message: `Already on 0.4.204.`),
  `captivePortalHtmlIsRetryableConnectivityFailure`,
  `captivePortalHtmlLightsTheUpdateCheckFailedFlag` — all fail.

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
- **Evidence:** `tlsHandshakeFailureIsRetryableConnectivityFailure` fails
  (`result.retryable` is `false`).

Both defects are misclassification bugs, not crashes; there is **no crash, ANR,
or data-loss risk** on any updater path. No local surface is blocked by either
defect — they only affect the update-check UX.

## 7. Fix guidance for the child card (`t_22aceb86`)

Keep the harness and tests; make them go green one invariant at a time. Do
**not** widen scope beyond classification:

1. **Defect B:** add `SSLException`/`SSLHandshakeException` (and, defensively,
   the `javax.net.ssl` family) to the retryable cause-walk in
   `retryableFailure`.
2. **Defect A:** treat a successful HTTP read that yields no usable
   `tag_name`/semver as a connectivity/interception failure, not an
   "already on version" result — return a retryable failure so `recordResult`
   lights (does not clear) the update-check-failed flag. Guard against the
   empty-tag → `0.0.0` collapse in the "not newer" branch.

Preserve the existing signature-verification and APK-validation behavior; the
captive-portal `download` path already writes HTML that must continue to be
rejected by checksum/APK validation.

## 8. Coverage limits / residual risk

- Emulator/KVM was not exercised in this audit run; API 26/35 device behavior
  for the update UI is covered by existing instrumented tests and the CI
  emulator lanes. All evidence here is host-side JVM (Robolectric `sdk = [35]`)
  and is deterministic and Internet-free.
- ML Kit model download (touchpoint #2) is transport-owned by Play Services;
  its offline behavior is asserted through the `WritingRecognizer` fakes in the
  study tests, not this harness, by design.
