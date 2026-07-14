# Auto-sync reliability

Kani keeps the configured wall-clock daily sync as a persisted JobScheduler job and uses WorkManager only for bounded follow-up attempts. AnkiDroid is a local content provider, so these attempts deliberately have no network constraint.

## Outcome classification

The sync path preserves one retry decision from the provider boundary through `ManualSyncEngine`, `AutoSyncRunner`, the daily job, and the retry worker:

- An explicit non-permanent `AnkiDroidGateway.SyncFailure` is transient.
- A sync deferred because another foreground or background sync already owns the process gate is transient.
- Missing provider access, permission, note-type, field, or other configuration failures are terminal until the user changes something.
- An unexpected runtime exception is terminal for automatic retry. Its stored sync-run row retains the historical `retryable_error` / `unexpected` labels for compatibility, but that legacy label does not drive scheduling.
- JVM `Error` types are not converted into provider failures and propagate instead of starting a retry loop.

Disabled auto-sync, a successful sync already recorded for the local day, a successful retry, and every terminal result complete any outstanding retry chain.

## Bounded retry policy

One unique WorkManager request named `kani_auto_sync_retry` is enqueued with `ExistingWorkPolicy.KEEP`. It starts after approximately 15 minutes and uses a 15-minute exponential backoff. At most three executions run, approximately 15, 30, and 60 minutes after the daily attempt; Android may delay them further for system scheduling reasons. Executions zero and one may return `Result.retry()`. Execution two always completes the chain, while the ordinary next-day job remains scheduled.

A successful manual sync cancels stale retry work when it re-arms the daily schedule. Turning daily sync off cancels both daily JobScheduler IDs and the unique retry chain.

## JobService lifecycle

The daily scheduler alternates IDs `3801` and `3802`. Before a normal run calls `jobFinished`, it requests tomorrow's job under the other ID and waits up to 15 seconds for WorkManager to confirm that retry work was enqueued or cancelled. This avoids replacing the currently running ID, which Android documents would stop immediately. A rejected daily job, persistence failure, or timeout closes the store and asks JobScheduler for prompt recovery.

Completion is handed back on the service main thread and checked against `onStopJob` state there. Durable work is prepared off the lifecycle monitor, so Android's main-thread stop callback is never blocked on WorkManager. If Android stops a run before continuation is ready, `onStopJob` marks that exact execution cancelled and returns `true`; the background task closes its store but never calls `jobFinished`. JobScheduler then owns the interrupted-run reschedule. A stop after continuation is ready returns `false` because the alternate daily job and bounded retry chain already own continuation.

## Local verification

The deterministic tests and Android test compilation run without a device:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :app:testDebugUnitTest \
  --tests 'dev.bee.kanjianki.sync.AutoSync*' \
  --tests dev.bee.kanjianki.sync.ManualSyncEngineFailureTest \
  --tests dev.bee.kanjianki.sync.SyncSettingsCoverageTest \
  :app:compileDebugAndroidTestJavaWithJavac \
  --no-daemon --console=plain
```
