# Android Rewrite Continuation

Last saved: 2026-05-17
Branch: android-rewrite-implementation
Latest implementation commit before this note: 3aa08006 Fix auto sync DST gap scheduling

## Current State

- Room/domain sync boundary work is staged without flipping full runtime ownership.
- Room review stats, local suspension reads, sync request factory, suspendable manual sync runner, successful-sync reads, auto-sync settings repository, and auto-sync policy are in place.
- AutoSyncScheduler now delegates scheduling math to domain AutoSyncPolicy while still reading LocalStore settings.
- Room auto-sync settings repository is intentionally not bound in Hilt until runtime settings ownership moves off legacy LocalStore.
- Data Room sync instrumentation executes in the Android instrumented fixture workflow, and the release predicate includes Room sync DAO/test changes.

## Verified Gates

- `:data:compileDebugAndroidTestKotlin` and focused RoomSyncRunRepositoryTest passed after the SQL test addition.
- `:data:testDebugUnitTest --tests RoomAutoSyncSettingsRepositoryTest` and `:app:compileDebugJavaWithJavac` passed after the staged settings fix.
- `:domain:test --tests AutoSyncPolicyTest` and focused app auto-sync scheduler tests passed after the DST-gap fix.
- Earlier `ciFast` passed at `5fc9413c`; `ciFast` has not been rerun after the final auto-sync policy/scheduler commits.

## Reviews

- Peirce reviewed `3d227bf0 Delegate auto sync scheduling policy` clean.
- Nietzsche reviewed `3aa08006 Fix auto sync DST gap scheduling` clean.

## Next Steps

1. Start in the original repo and check out `android-rewrite-implementation`.
2. Re-run `git status --short --branch`.
3. Re-run the focused auto-sync gate:
   `env ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew :domain:test --tests AutoSyncPolicyTest :app:testDebugUnitTest --tests dev.bee.kanjianki.sync.AutoSyncSchedulerTest --tests dev.bee.kanjianki.sync.SyncSettingsBehaviorTest --tests dev.bee.kanjianki.sync.SyncSettingsCoverageTest :app:compileDebugJavaWithJavac`
4. Run full `ciFast` before merging or broadening runtime cutover.
5. Continue with staged auto-sync runner/status work:
   - keep Room auto-sync settings unbound until LocalStore/Room ownership is explicit
   - add a staged app-level auto-sync runner using AutoSyncSettingsRepository, SyncRunRepository, AutoSyncPolicy, and DomainManualSyncRunner
   - do not wire AutoSyncJobService to Room until manual and background sync move together
6. After auto-sync runner staging, continue moving Home/sync status reads to typed domain models and add tests.
