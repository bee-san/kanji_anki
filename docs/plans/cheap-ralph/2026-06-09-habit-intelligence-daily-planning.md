# Habit Intelligence: Notifications, Streak Protection, and Daily Planning Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Help the user do exactly enough Kani repair each day, at the right time, without spam or app-addiction pressure.

**Architecture:** Build a pure, explainable `DailyStudyPlanPolicy` first, expose it as a Today card on Home, then reuse the same plan object to drive conservative notification scheduling. Keep notification delivery Android-specific and thin; all decisions should live in tested core/app policies with reason strings so the app can explain why it reminded or stayed quiet.

**Tech Stack:** Kotlin/JVM core policies, Android/Kotlin app data adapters, Compose Home cards, existing `ReminderScheduler`/`ReminderSchedulePolicy`/`ReminderCopyPolicy`, existing `StudyStatsStore.studyStreak`, `StudyStreakPolicy`, `AdaptiveLoadPlanner`, SQLite local store, WorkManager/AlarmManager only after the policy is proven, Gradle tests via `./gradlew :core:test :app:testDebugUnitTest`.

## Current repo anchors verified before writing this plan

- `core/src/main/kotlin/dev/bee/kanjianki/core/StudyStreakPolicy.kt`
  - Produces `Streak(currentDays, bestDays, studiedToday, reviewsToday, lastStudyAtMillis)` from local study days.
- `app/src/main/kotlin/dev/bee/kanjianki/data/StudyStatsStore.kt` and `StudyStatsQueries.kt`
  - App-level stats source for streak, review counts, task time, recent mistakes, and outcome metrics.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStatsCards.kt`
  - Existing stats card source can reuse streak/task-time summaries.
- `app/src/main/kotlin/dev/bee/kanjianki/HomeScreenModel.kt`, `HomeScreenCompose.kt`, `HomeMetricsModel.kt`, `HomeMetricsCompose.kt`, `HomeFocusQueueModel.kt`, `HomeFocusQueueCompose.kt`, `HomeEmptyStateCompose.kt`
  - Home already has model/Compose seams for adding a Today card.
- `app/src/main/kotlin/dev/bee/kanjianki/HomeStudyQueueActions.kt`
  - Study queue seeding and similar-kanji availability annotation happen from Home.
- `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreInventory.kt`
  - Provides `activeDashboardRows()`, `activeDashboardRowsByKanji()`, and `studyItemsForKanji(...)` for due-plan inputs.
- `app/src/main/kotlin/dev/bee/kanjianki/reminders/ReminderScheduler.kt`
  - Existing reminder implementation schedules alarms, checks notification permission/channel state, loads `activeDashboardRows`, `studyItemsForKanji`, `studiedKanjiSince(startOfLocalDay(now))`, `reviewStatsSince(...)`, and `studyStreak(now)`, then asks `ReminderSchedulePolicy`/`ReminderCopyPolicy`.
- `core/src/main/kotlin/dev/bee/kanjianki/core/ReminderSchedulePolicy.kt`, `ReminderNotificationPolicy.kt`, `ReminderCopyPolicy.kt`, `ReminderSettingsSavePolicy.kt`, and matching tests.
  - Existing rule/copy seams for notification decisions.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsAutomationReminder*.kt`
  - Existing Settings surface for daily reminder permission/enabled/time.
- `plans/android_rewrite.md`
  - Intended architecture points to WorkManager for durable background work, daily auto-sync, backup, and update checks.

## Product principles

- Calm intervention beats engagement-maximization.
- Remind only when there is useful work or a real streak/sync reason.
- Every reminder decision must have a reason string.
- No notification spam after dismiss; cap streak nudges and due-work nudges.
- Kani's philosophy is minimum effective repair so the user can return to immersion.
- Build the Today home card first. It creates value and proves the decision model before Android notification complexity.

## Domain model target

```kotlin
data class DailyStudyPlan(
    val dateLocalDay: Long,
    val dueNow: Int,
    val dueLater: Int,
    val newProblemKanjiAvailable: Int,
    val streakStatus: StreakStatus,
    val estimatedMinutes: Int,
    val recommendedAction: RecommendedAction,
    val nextUsefulReminderAtMillis: Long,
    val dueLookahead: DueLookaheadWindow,
    val syncStatus: SyncStatus,
    val reasons: List<String>,
)

data class DueLookaheadWindow(
    val dueNow: Int,
    val dueSoon: Int,
    val nextClusterAtMillis: Long,
    val clusterSize: Int,
    val recommendedReminderAtMillis: Long,
)
```

Suggested enums/sealed types:

- `StreakStatus`: `SAFE`, `NEEDS_ONE_REVIEW`, `NOT_STARTED`, `NO_STREAK_ACTIVE`.
- `RecommendedAction`: `STUDY_NOW`, `STUDY_ONCE_FOR_STREAK`, `WAIT_UNTIL_LATER`, `SYNC_FIRST`, `NOTHING_USEFUL_NOW`.
- `SyncStatus`: `CURRENT`, `SYNC_NEEDED_TO_JUDGE_PROGRESS`, `NO_MANUAL_SYNC_YET`, `UNKNOWN`.
- `ReminderReason`: machine-readable reason IDs plus human copy.

## Phase plan

### Phase 1 — DailyStudyPlanPolicy and Today home card

Build a pure policy that consumes:

- current local day/time,
- active dashboard rows,
- current `study_items`,
- study streak summary,
- studied-today evidence,
- due timestamps,
- optional sync freshness/last successful sync,
- workload/estimated-time settings if already available.

Example outputs:

- `4 due now · about 2 min`.
- `Nothing useful now. 3 learning repeats due around 20:30.`
- `Study once today to keep your streak.`
- `Sync needed before Kani can judge progress.`

### Phase 2 — Explainable notification policy

Add a policy that consumes `DailyStudyPlan` and settings:

Rules:

- remind only if not studied today for streak reminders,
- do not remind for due work if no useful work is due or clustered soon,
- batch nearby due times into one reminder,
- obey quiet hours,
- max one streak reminder per day,
- at most two due-learning-repeat reminders per day,
- no spam after dismiss/click,
- separate settings for sync, due, and streak reminder families.

Decision object should include:

```kotlin
data class NotificationDecision(
    val shouldSchedule: Boolean,
    val triggerAtMillis: Long,
    val channel: ReminderChannel,
    val title: String,
    val body: String,
    val reasonIds: List<String>,
    val humanReason: String,
)
```

### Phase 3 — Due-later lookahead clustering

Add `DueLookaheadWindow` policy:

- group learning/relearning repeats due in 20m, 45m, and 2h into one useful reminder when reasonable,
- do not schedule three separate nags,
- prefer the latest useful cluster before quiet hours,
- surface `nextUsefulReminderAt` to the Today card.

### Phase 4 — Android scheduling implementation

Only after policy and Today card tests pass:

- update `ReminderScheduler` to delegate reasoned scheduling to the new policy,
- use WorkManager for durable periodic work where appropriate,
- use AlarmManager only for exact/local user-facing reminder moments when genuinely needed,
- handle notification permission denial,
- handle timezone changes, reboot, app update, midnight rollover, and opening study from a notification,
- store minimal daily notification state to enforce caps/dismissal.

### Phase 5 — Settings and copy polish

- Expose separate toggles for sync/due/streak reminders if feasible.
- Keep all notification copy short and action-oriented.
- Show the reason string somewhere discoverable in Settings or debug/testing surfaces.

## Task breakdown

### Task 1: Add DailyStudyPlan core model and policy skeleton

**Objective:** Define the explainable plan object and simplest rule cases with tests.

**Files:**
- Create: `core/src/main/kotlin/dev/bee/kanjianki/core/DailyStudyPlanPolicy.kt`
- Create: `core/src/test/kotlin/dev/bee/kanjianki/core/DailyStudyPlanPolicyTest.kt`
- Read: `StudyStreakPolicy.kt`, `AdaptiveLoadPlanner.kt`, `ReminderCopyPolicy.kt`, `ReminderSchedulePolicy.kt`

**Steps:**
1. Write failing tests for:
   - no due work and studied today => `NOTHING_USEFUL_NOW`,
   - due now => `STUDY_NOW`,
   - not studied today with active streak => `STUDY_ONCE_FOR_STREAK`,
   - no sync/freshness evidence => `SYNC_FIRST` when progress cannot be judged.
2. Implement minimal model and policy.
3. Run:
   - `./gradlew :core:test --tests dev.bee.kanjianki.core.DailyStudyPlanPolicyTest`
4. Commit:
   - `feat(habit): add daily study plan policy`

### Task 2: Add DueLookaheadWindow clustering

**Objective:** Convert due timestamps into one useful next reminder moment.

**Files:**
- Modify: `DailyStudyPlanPolicy.kt`
- Test: `DailyStudyPlanPolicyTest.kt`

**Steps:**
1. Add tests for due repeats at 20m/45m/2h producing one cluster.
2. Add tests for no due-later work.
3. Add tests for quiet-hours cutoff behavior if quiet-hours settings already exist; otherwise model a policy input with defaults.
4. Implement clustering and reason IDs.
5. Run core tests.
6. Commit:
   - `feat(habit): cluster due-later reminders`

### Task 3: App data adapter for DailyStudyPlan

**Objective:** Build a thin app adapter that loads real local-store inputs and calls the pure policy.

**Files:**
- Create/modify: `app/src/main/kotlin/dev/bee/kanjianki/data/DailyStudyPlanStore.kt` or closest local-store adapter.
- Modify/read: `LocalStoreInventory.kt`, `StudyStatsStore.kt`, `ReminderScheduler.kt`
- Test: app unit tests under `app/src/test/kotlin/dev/bee/kanjianki/data/`

**Steps:**
1. Write a fake-store test for dueNow/dueLater counts from `studyItemsForKanji`.
2. Write a test using `studyStreak(now)` and `studiedKanjiSince(startOfLocalDay(now))`.
3. Implement the adapter without embedding decision logic.
4. Run:
   - `./gradlew :app:testDebugUnitTest --tests '*DailyStudyPlan*'`
5. Commit:
   - `feat(habit): load daily plan from local study state`

### Task 4: Today home card model

**Objective:** Add a Home model that exposes the daily plan in compact copy.

**Files:**
- Modify/create: `HomeTodayPlanModel.kt`, `HomeScreenModel.kt`, `HomeTextCopy.kt` or matching existing model/copy files.
- Test: `HomeTextCopyTest.kt`, Home model tests.

**Steps:**
1. Add tests for copy examples:
   - `4 due now · 2 min`,
   - `3 learning repeats later`,
   - `Streak safe after 1 review`,
   - `Next useful time: 20:30`,
   - `Sync needed to verify progress`.
2. Implement model/copy.
3. Run:
   - `./gradlew :core:test --tests dev.bee.kanjianki.core.HomeTextCopyTest`
   - targeted app home tests.
4. Commit:
   - `feat(home): show today's study plan`

### Task 5: Compose Today home card

**Objective:** Render the Today card without disrupting existing Home layout.

**Files:**
- Modify/create: `HomeTodayPlanCompose.kt`, `HomeScreenCompose.kt` or nearest Home Compose host.
- Test: Home Compose unit/instrumentation tests.

**Steps:**
1. Add UI/model test for the Today card appearing above or near existing study actions.
2. Add accessibility labels/content descriptions.
3. Implement compact card.
4. Run home-focused tests.
5. Commit:
   - `feat(home): render today study plan card`

### Task 6: Explainable notification decision policy

**Objective:** Make notification scheduling decisions rule-based and testable before touching Android services.

**Files:**
- Create: `core/src/main/kotlin/dev/bee/kanjianki/core/DailyReminderDecisionPolicy.kt`
- Create: `core/src/test/kotlin/dev/bee/kanjianki/core/DailyReminderDecisionPolicyTest.kt`
- Read/possibly reuse: `ReminderSchedulePolicy.kt`, `ReminderNotificationPolicy.kt`, `ReminderCopyPolicy.kt`

**Steps:**
1. Tests:
   - no useful work => do not schedule due reminder,
   - studied today => no streak reminder,
   - not studied today + quiet-hours approaching => schedule one streak reminder,
   - dismissed today => suppress same-family reminder,
   - due cluster => one due reminder with reason string.
2. Implement policy with machine-readable reason IDs.
3. Run core tests.
4. Commit:
   - `feat(reminders): explain daily reminder decisions`

### Task 7: Android reminder scheduler integration

**Objective:** Replace ad hoc scheduling inputs in `ReminderScheduler` with `DailyStudyPlan` + `DailyReminderDecisionPolicy` without regressing permission/channel behavior.

**Files:**
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/reminders/ReminderScheduler.kt`
- Modify/test: `app/src/test/kotlin/dev/bee/kanjianki/reminders/ReminderSchedulerTest.kt`
- Possibly modify: `ReminderReceiver.kt`, `ReminderReceiverDailyActions.kt`, `BootReminderReceiver.kt`

**Steps:**
1. Add tests preserving cancel behavior when disabled.
2. Add tests preserving notification permission/channel suppression.
3. Add tests that schedule time/reason comes from the new decision policy.
4. Implement adapter wiring.
5. Run app reminder tests.
6. Commit:
   - `feat(reminders): schedule from daily study plan`

### Task 8: Reminder settings split and daily caps

**Objective:** Add conservative user controls and cap state only after the decision policy is stable.

**Files:**
- Modify: settings model/compose around `MainActivitySettingsAutomationReminder*.kt`.
- Modify: local store settings schema only if necessary; otherwise keep this as a later PR.
- Test: settings model/copy tests, reminder policy tests.

**Steps:**
1. Add settings model tests for due/streak/sync toggles if schema changes are small.
2. Add cap-state tests for max one streak reminder/day and max two due reminders/day.
3. Implement minimally.
4. Run targeted settings/reminder tests.
5. Commit:
   - `feat(reminders): add calm reminder controls`

### Task 9: WorkManager/reboot/timezone hardening

**Objective:** Make background scheduling durable only after the core decision logic is proven.

**Files:**
- Modify/create: WorkManager worker(s), manifest receivers, boot/timezone receivers if absent.
- Test: app unit tests where feasible; rely on Android CI for integration.

**Steps:**
1. Inventory existing manifest receivers and WorkManager dependencies.
2. Add one worker for periodic plan refresh/scheduling, not repeated notifications.
3. Preserve AlarmManager exactness only for chosen next reminder moments.
4. Add tests or documented manual/CI checks for reboot/timezone/midnight rollover.
5. Commit:
   - `feat(reminders): harden daily plan scheduling`

## Acceptance criteria

- `DailyStudyPlanPolicy` returns clear action/reason output for due-now, due-later, streak, sync-needed, and nothing-useful states.
- Home shows a Today card before notification changes ship.
- Notifications are never scheduled when there is no useful work and no streak/sync reason.
- Notification decisions expose machine-readable and human-readable reasons.
- Due-later repeats are clustered into calm reminders, not one notification per due item.
- Existing notification permission/channel safeguards continue to pass tests.
- User can turn reminders off; future split toggles do not force users into streak pressure.
- The app keeps the low-time/Pareto philosophy in copy and behavior.

## Suggested Cheap Ralph slicing

1. PR 1: pure `DailyStudyPlanPolicy` + tests.
2. PR 2: due-lookahead clustering.
3. PR 3: local-store adapter.
4. PR 4: Today home card model/copy.
5. PR 5: Today home card Compose.
6. PR 6: explainable notification decision policy.
7. PR 7: `ReminderScheduler` integration.
8. PR 8+: settings/caps/WorkManager hardening after the Today card and policy prove useful.

Do not tick the README item complete until both the Today card and conservative notification policy are implemented with tests and PR/CI evidence; if Android background hardening remains, leave the item unchecked with a precise remaining-work list.
