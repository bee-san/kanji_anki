# Notification System Deep Review + Anti-Spam Plan (2026-07-08)

Goal: tell the user when studying is actually worth it, and never feel like
spam. This document reviews the current reminder/notification system end to
end, lists concrete defects and risks, and lays out a phased plan.

## 1. Current architecture (as-built)

Two notification surfaces exist. This review covers the study reminder
surface; the app-update surface (`update/UpdateNotifier.kt`, channel
`kani_app_updates`) is healthy and out of scope.

### Trigger chain

1. `ReminderScheduler.schedule(...)`
   (`app/src/main/kotlin/dev/bee/kanjianki/reminders/ReminderScheduler.kt:98-119`)
   arms ONE inexact alarm (`AlarmManager.set(RTC_WAKEUP, ...)`, request code
   2701, `ReminderScheduler.kt:237-245`):
   - Reminder disabled -> cancel alarm.
   - Not studied today -> configured daily time (default 19:00,
     `core/.../TimeOfDaySettingsPolicy.kt:8-9`), today if still ahead, else
     tomorrow (`core/.../ReminderSchedulePolicy.kt:42-53`).
   - Studied today -> next review batch from
     `ReminderReviewBatchPolicy.nextBatch` (`core/.../ReminderReviewBatchPolicy.kt:12-57`);
     if none, tomorrow's daily time.
2. Alarm fires -> `ReminderReceiver` (`reminders/ReminderReceiver.kt:66-76`)
   -> `showReminderNotification()` then re-arm via `schedule(settings)`
   (`reminders/ReminderReceiverDailyActions.kt:9-15`).
3. `showReminderNotification` (`ReminderScheduler.kt:181-217`) recomputes from
   fresh state at fire time:
   - Review batch available -> "N reviews ready" copy, post notification
     (single ID 2702, so notifications replace instead of stack), then
     `recordReviewReminderNotificationShown` increments the persisted per-day
     counter (`data/LocalStoreStudySettings.kt:147-154`, keys
     `review_reminder_day_start` / `review_reminder_count`).
   - Otherwise -> `DailyReminderDecisionPolicy.decide(...)` title/body; if the
     decision says nothing useful, no notification is posted at all.

### Re-arm points (when the schedule is recomputed)

- App cold start (`MainActivityStartup.kt:63`).
- After every saved review, undo, and repair submit/skip
  (`MainActivityStudyReviewFlow.kt:87,109,220,247`).
- After the alarm fires (receiver re-arms).
- Boot, package replace, time set, timezone change
  (`reminders/BootReminderReceiver.kt:38-44`, manifest lines 75-84).
- Settings save/turn-off + POST_NOTIFICATIONS grant/deny
  (`MainActivitySettingsAutomationReminder.kt:71,84`,
  `MainActivityPermissionHandler.kt:45,60`).

### Anti-spam mechanisms that already work

These are why the app does NOT ping every minute today, even though learning
steps (1m/10m) write near-future `due_at` values directly onto `study_items`
(`core/.../ReviewTransitionEngine.kt:197-279`):

1. **Single alarm, single notification ID.** There is exactly one pending
   alarm and one notification slot (2702). No stacking, no parallel timers.
2. **Review-batch clustering.** Future due items within a 2h window collapse
   into one trigger at the END of the cluster
   (`ReminderReviewBatchPolicy.kt:45-56`), so a burst of learning-step cards
   yields one notification, not many.
3. **Hard daily cap for review notifications.** Max 2/day, persisted and reset
   implicitly at local-day rollover (`ReminderReviewBatchPolicy.kt:9,17-19`;
   `LocalStoreStudySettings.kt:138-154`).
4. **22:00 cutoff.** No review notifications late at night
   (`ReminderReviewBatchPolicy.kt:7,26-28`; also
   `ReminderSchedulePolicy.kt:29-32`).
5. **Studied-today suppression.** Once you studied, the generic daily reminder
   moves to tomorrow; only review batches may fire today
   (`ReminderScheduler.kt:108-118`).
6. **Skip-if-nothing-useful.** The daily path consults
   `DailyStudyPlanPolicy` + `DailyReminderDecisionPolicy` and posts nothing
   when there is no useful action (`ReminderScheduler.kt:371-377`,
   `DailyReminderDecisionPolicy.kt:139-171`).
7. **Fresh-state recompute at fire time.** Copy/decision is computed when the
   alarm fires, not when it was armed, so stale alarms self-correct into
   silence.

## 2. Defects and gaps found

### D1. Double-fire burns the daily review budget back-to-back (bug)

When items are already overdue and the user studied today,
`nextBatch` returns `ReviewBatch(nowMillis, ...)`
(`ReminderReviewBatchPolicy.kt:35-38`). The alarm fires immediately, the
notification posts (count=1), the receiver re-arms, the items are STILL
overdue (the user hasn't opened the app in the intervening seconds), so the
next alarm is again `nowMillis` -> second notification seconds later
(count=2), then the cap silences the day. Effects:

- Two buzzes back-to-back (same notification ID, so one visible card, but two
  alert sounds since `setOnlyAlertOnce` is not set).
- The entire 2/day budget is spent at one instant instead of spaced through
  the day; later, genuinely-new due clusters get nothing.

Root cause: no minimum gap between posts and no "don't re-notify for the same
overdue set" memory. The cap is the only brake and it acts as a fuse, not a
spacer.

### D2. Review batch counts retired/suspended/off-dashboard items (bug)

`reviewReminderBatch` feeds raw `store.studyItems()` into `nextBatch`
(`ReminderScheduler.kt:419-423`). That list includes `retired` rows (seeding
retires instead of deleting, `core/.../StudyQueueSeeder.kt:314`) and items for
locally-suspended kanji / kanji no longer on the dashboard. The canonical due
count (`core/.../StudySessionSelector.kt:189-204`) excludes those. So "N
reviews ready" can overcount, or fire when the study queue is actually empty
— the worst kind of spam: a notification that opens to nothing. The same
unfiltered `items.map { it.dueAtMillis }` feeds the daily plan
(`ReminderScheduler.kt:396`).

Note: `ReminderScheduler.activeReminderDueAtMillis` / `isActiveReminderItem`
(`ReminderScheduler.kt:428-440`) were written to filter retired items and are
dead code — never called. `reminderCopy(...)` (`ReminderScheduler.kt:341-369`)
is also dead. Someone started this fix and never wired it.

### D3. The decision policy's scheduling intelligence is unwired (gap)

`DailyReminderDecisionPolicy` supports quiet hours, per-family caps
(due 2/streak 1/sync 1), and dismissed-family suppression
(`core/.../DailyReminderDecisionPolicy.kt:12-24,68-106`). But the app builds
the request with only `plan` + `nowMillis` (`ReminderScheduler.kt:379-387`),
so:

- Quiet hours are effectively OFF (`quietHoursStartMinuteOfDay = null`).
- Per-family shown-counters are always 0 -> those caps never engage. Only the
  separate review-batch counter is persisted.
- `dismissedFamiliesToday` is always empty, and nothing could populate it:
  the notification has no `deleteIntent`, so dismissals are invisible.
- `decision.triggerAtMillis` (cluster-end + quiet-hour pull-forward) is
  computed and then thrown away — alarm scheduling never uses it. The
  WAIT_UNTIL_LATER intelligence exists but does not drive the alarm.

### D4. Sync completion never reschedules the reminder (gap)

Manual sync (`MainActivityHome.runSync` -> `sync/ManualSyncEngine.kt`) and
auto-sync (`sync/AutoSyncRunner.kt`) replace the entire study queue but never
call `ReminderScheduler.schedule(...)` (grep confirms zero `Reminder`
references under `app/.../sync/`). A sync that lands 50 overdue cards leaves
the previously-armed alarm stale (possibly tomorrow), and a sync that clears
the queue leaves a pointless alarm armed. Fresh-state recompute at fire time
limits the damage but the *timing* stays wrong.

### D5. Learning-step tail notification (noise)

If a session ends with a card mid-learning (due in 10 min), the studied-today
path arms an alarm ~10 minutes after the user just closed the app, for
"1 review ready". Capped and clustered, but it is exactly the "you just left,
come back!" nag users report as spam. There is no minimum due-count threshold
and no grace period after recent study activity.

### D6. Inexact alarm + Doze reliability (risk, deliberate trade-off)

`AlarmManager.set` is batched and deferred in Doze; delivery can slip by
minutes to hours on idle devices. Acceptable for a habit nudge (and
battery-polite), but worth documenting as the chosen trade-off. Recovery
paths exist (boot/time-change receiver, app-open re-arm). No
`SCHEDULE_EXACT_ALARM` is requested — keep it that way; a study reminder does
not justify exact alarms and Play policy scrutiny.

### D7. Notification UX gaps (polish)

- No `setOnlyAlertOnce(true)` -> replacing the same notification re-buzzes
  (amplifies D1).
- No action buttons ("Study now", "Snooze 1h", "Skip today") and no
  `deleteIntent` -> the user's strongest anti-spam signal (dismissal) is
  ignored (feeds D3).
- One channel for all reminder families; users cannot mute streak nudges
  while keeping due reminders.
- `MainActivityLifecycle.onResume` does not clear/recompute — opening the app
  from the notification leaves it until auto-cancel, and an app-open (the
  ultimate "I got the message") doesn't inform scheduling.

## 3. Target behavior ("when to notify, without spam")

Principles, in priority order:

1. **Notify only for actionable work.** A reminder must map to a non-empty,
   studyable queue at fire time (D2 fix) or a concrete streak/sync action.
2. **Budget + spacing, not just budget.** Global max 2 study notifications
   per local day (existing), PLUS a minimum gap (default 90 min) between any
   two posts, PLUS never re-notify for the same unchanged overdue set (D1 fix).
3. **Batch by cluster, threshold small tails.** Keep 2h clustering; add a
   minimum batch size (default: notify only if >= 3 due, OR streak at risk,
   OR it is the user's configured daily time). Single learning-step tails
   wait for the daily reminder (D5 fix).
4. **Grace period after activity.** No notification within 45 min of the last
   recorded review — the user is or was just here (D5 fix).
5. **Respect explicit user signals.** Swipe-dismiss suppresses that family
   for the rest of the day (wire `dismissedFamiliesToday`); opening the app
   cancels the posted notification and re-arms from fresh state.
6. **Quiet hours.** Wire the existing quiet-hours support with default
   22:00–08:00 (aligns with the current 22:00 cutoff); pull a reminder
   earlier rather than into quiet hours when a cluster ends late.
7. **Recompute on every state change.** Add sync-completion re-arm (D4);
   keep review/boot/startup re-arms.
8. **Stay inexact and battery-polite.** Keep `AlarmManager.set`; document
   Doze slippage as accepted (D6).

## 4. Plan

Follow repo conventions: decision logic as pure policies in `:core` with
exhaustive JVM tests (100% class coverage gate), thin Android adapters in
`:app`, settings via the SQLite `settings` key/value table, gate with
`./gradlew ciFast`. None of this touches the AnkiDroid provider/sync read
path, so the live-emulator release gate is not required; normal CI + unit +
instrumented compilation suffice (run the reminder instrumented tests when
touching receiver/alarm plumbing).

### Phase 0 — Cleanup and truth-telling (small, immediate)

- Delete dead code in `ReminderScheduler`: `reminderCopy(...)` overloads,
  `activeReminderDueAtMillis`, `isActiveReminderItem` (or wire them in Phase
  1 instead of deleting — decide in review; do not leave them dangling).
- Add `setOnlyAlertOnce(true)` to the reminder notification builder so
  in-place replacement never re-buzzes.
- Tests: existing `ReminderSchedulerTest` still green; add a builder-flag
  assertion.

### Phase 1 — Correct inputs: only studyable items (fixes D2)

- Add a core filter (e.g. `ReminderEligibilityPolicy.eligibleDueTimes(items,
  rows)`) that mirrors `StudySessionSelector` semantics: exclude
  `STATE_RETIRED`, exclude items with no active dashboard row (covers local
  suspension), one active item per family.
- Use it for both `reviewReminderBatch` and `dailyStudyPlan` inputs in
  `ReminderScheduler`.
- Tests (`:core` JVM): retired excluded; suspended/off-dashboard excluded;
  counts match `StudySessionSelector.dueCount` for the same fixture.

### Phase 2 — Anti-spam state machine (fixes D1, D5)

- Extend persisted reminder state (settings keys, same pattern as
  `review_reminder_*`): `reminder_last_posted_at`,
  `reminder_last_posted_signature` (hash of the notified due-set, e.g.
  count + max dueAt bucket), per-family shown counts for the day.
- New core policy `ReminderThrottlePolicy.shouldPost(nowMillis, lastPostedAt,
  minGapMillis, signature, lastSignature, lastReviewAtMillis,
  activityGraceMillis)`:
  - deny within min gap (default 90 min);
  - deny if signature unchanged since last post (same overdue set);
  - deny within activity grace (default 45 min since last review);
  - allow overrides: user's configured daily time always eligible once/day.
- Apply minimum batch size in `ReminderReviewBatchPolicy` (new param,
  default 3) with the streak/daily-time exceptions decided by the caller.
- Re-arm after a suppressed fire must schedule the NEXT eligible time (e.g.
  `lastPostedAt + minGap`), not `now` — this removes the immediate-fire loop
  structurally, not just via the cap.
- Tests (`:core` JVM): the D1 timeline (overdue set, fire, re-arm) posts once
  and schedules the second attempt >= minGap later; budget spacing across a
  simulated day; grace period; signature change (new cards due) re-enables.

### Phase 3 — Wire the decision policy for real (fixes D3)

- Populate `DailyReminderDecisionRequest` fully from the store: per-family
  shown counts (Phase 2 state), quiet hours (new settings keys
  `reminder_quiet_start_minute` / `reminder_quiet_end_minute`, defaults
  22:00–08:00), `dismissedFamiliesToday` (Phase 4 producer).
- Use `decision.triggerAtMillis` to arm the alarm on the daily path so
  WAIT_UNTIL_LATER cluster-end timing and quiet-hour pull-forward actually
  drive scheduling; keep `ReminderSchedulePolicy` as the fallback.
- Record daily-decision posts in the per-family counters (today only
  review-batch posts are counted).
- Tests: quiet-hours pull-forward reaches the armed alarm; per-family caps
  suppress; counters persist/reset at day rollover.

### Phase 4 — Dismissal + open feedback loop (fixes D7, feeds D3)

- Add `deleteIntent` (new broadcast action on `ReminderReceiver`, e.g.
  `ACTION_REMINDER_DISMISSED` with family extra) that records the family in
  a `reminder_dismissed_families_<localDay>` settings key.
- On app open/`onResume`: cancel notification 2702, clear nothing else, and
  re-arm from fresh state (one `ReminderScheduler.schedule(activity)` call in
  `MainActivityLifecycle.onResume`, throttled to once per few minutes to keep
  resume cheap).
- Optional (behind the same phase): notification actions "Study now" (opens
  MainActivity study route) and "Skip today" (records dismissal for family).
- Tests: receiver dispatch for the new action (`ReminderReceiverPolicy`
  update + JVM test); dismissal suppresses that family same-day and resets
  next day; instrumented test that dismissal broadcast writes the key.

### Phase 5 — Sync-completion re-arm (fixes D4)

- After `markSyncSucceeded` in `ManualSyncEngine` and after a successful
  `AutoSyncRunner` run, call `ReminderScheduler.schedule(context)` (off-main,
  both paths already run on background executors).
- Tests: unit test that the sync success path invokes the scheduler seam
  (inject `ReminderServices`/callback like existing tests do); assert no
  reschedule on failed sync.

### Phase 6 — Settings surface + channels (polish, optional)

- Settings > Automation > Daily reminder: add quiet-hours row and a
  "max reminders per day" stepper (1–3, default 2); persist via the existing
  reminder settings pattern (`LocalStoreStudySettings` + save policy + copy
  in `SettingsAutomationTextCopy`, EN+JA).
- Split channels per family (`kani_study_reminders_due`, `_streak`, `_sync`)
  so OS-level muting is granular; migrate by creating new channels and
  posting to the family channel (old channel left in place — Android does not
  allow silent deletion UX anyway).
- Tests: settings persistence + normalization (JVM), compose panel test tags,
  channel metadata assertions mirroring `ReminderSchedulerTest`'s Japanese
  channel test.

### Explicit non-goals

- No exact alarms (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`) — a habit nudge
  tolerates Doze slippage; keep battery/Play-policy posture.
- No foreground service, no per-minute WorkManager polling — the single
  re-armed alarm + event-driven recompute is the right shape.
- No second notification surface: keep ONE study notification slot (ID 2702)
  that updates in place.

## 5. Acceptance criteria (behavioral)

- With 10 overdue cards and studied-today: exactly one notification now, next
  eligible attempt >= 90 min later, at most 2 study notifications that day.
- Ending a session with one card in a 10-minute learning step: no
  notification 10 minutes later; the card is folded into the next eligible
  batch or the daily-time reminder.
- All notified counts match what the study screen actually shows on open
  (no "N reviews ready" -> empty queue).
- Dismissing a due reminder: no further due-family notifications that day;
  daily rollover restores them.
- Sync that lands new overdue cards: alarm re-armed within the sync
  completion path; notification respects gap/budget/quiet hours.
- Between 22:00 and 08:00 (default quiet hours): nothing posts; late clusters
  pull the reminder earlier, never later into the night.

## 6. Verification per phase

- `./gradlew ciFast` (JVM tests incl. 100% core coverage gate, app unit
  tests, androidTest compilation, lint, asset tests).
- Reminder-focused instrumented tests when touching receiver/alarm plumbing:
  `ReminderReceiverInstrumentedTest`, `SettingsReminderComposeTest`,
  `MainActivityInstrumentedTest#testReminderSettingsPanelCanEnableAndTurnOffReminder`.
- Manual smoke on emulator for Phase 2/4: force an overdue state, trigger the
  alarm broadcast via `adb shell am broadcast -a
  dev.bee.kanjianki.action.DAILY_REMINDER -n
  dev.bee.kanjianki/.reminders.ReminderReceiver`, verify single post +
  spaced re-arm, dismissal suppression, and app-open cancel.
