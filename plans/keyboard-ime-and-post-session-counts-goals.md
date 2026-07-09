# Keyboard IME Jump + Post-Session Due Counts — Goals (2026-07-09)

Source: user report with screenshot (`~/Downloads/pls_look.jpeg`, home screen at
08:33 showing "Study now — 3 to study", "Today: 3 due now · about 3 min", and a
Study tab badge, taken right after finishing a session) plus a code trace at
commit `2f218957` (branch `ladder-steps-deep-review-2026-07-08`). Line numbers
are correct as of that commit and may drift — search the named symbols.

Two user-facing problems:

1. **Keyboard jump:** on typing cards the keyboard opens automatically and the
   whole screen reshuffles (card shrinks, nav bar vanishes, footer jumps up).
   The user wants the screen to *start* in the keyboard-open position so
   nothing moves when the IME appears.
2. **Post-session counts:** immediately after finishing a session the home
   screen says "3 to study" / "3 due now". Ideal behavior: cards that come due
   during/right after a session should have been served *inside* that session,
   and the home screen should read 0 for a while after finishing ("you studied
   everything in that session").

Each goal below is self-contained: context with file/line evidence, the change
to make, and machine-checkable acceptance criteria. Work goals one at a time
with `/goal`. Goal IDs are prefixed (`KB` = keyboard, `PS` = post-session) so
they cannot be confused with the deep-review goal numbering in
`plans/deep-review-goals*.md`.

Validation gates (see AGENTS.md for full detail):

- `./gradlew ciFast` — required for every goal. On machines without
  `local.properties`, prefix Android tasks with
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk`.
- None of these goals touch the AnkiDroid provider/sync path, so the live
  AnkiDroid emulator gate is not mandatory; a manual emulator pass of the
  study flow is still recommended before any release containing PS1/PS2.
- No FSRS timing semantics change in any goal — goldens must NOT be
  regenerated. If a golden diff appears, the implementation is wrong.

Suggested order:

1. **PS2** — core-only count semantics; smallest change, delivers the headline
   "says 0 for a bit" behavior on its own.
2. **PS1** — session learn-ahead so the session actually finishes its own
   learning repeats; completes the ideal behavior.
3. **PS3** — end-to-end regression pinning the combined zero-state.
4. **KB1** — keyboard-ready typing-card layout; independent of PS goals.
5. **KB2** — optional polish (edge-to-edge smooth IME animation); needs
   on-device validation, do last or skip.

---

## Background: how the pieces work today (shared evidence)

### Keyboard path

- Manifest: single activity with `android:windowSoftInputMode="adjustResize"`
  (`app/src/main/AndroidManifest.xml:35`). No
  `WindowCompat.setDecorFitsSystemWindows` / `enableEdgeToEdge` anywhere; the
  activity chain bottoms out at `ComponentActivity`
  (`MainActivityUiSupport.kt:19`), status/nav bar colors via
  `WindowInsetsControllerCompat` (`MainActivityUiSupport.kt:25-33`).
- The `type_meaning` rung renders a `BasicTextField`
  (`app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyTypingAnswerCompose.kt:88-147`)
  that **auto-focuses on card entry**, force-opening the keyboard:
  `LaunchedEffect(state) { focusRequester.requestFocus() }` (`:61-69`).
- The shell column applies `.systemBarsPadding().imePadding()`
  (`app/src/main/kotlin/dev/bee/kanjianki/MainActivityShell.kt:171-172`),
  puts card content in a weighted `verticalScroll` Box (`:175-182`), pins the
  action bar as `footerContent()` (`:183`), and **hides the bottom nav only
  while the IME is visible** (`:184-194`,
  `if (navActions != null && !imeVisible)`).
- IME reactivity: `kaniImeVisible()` reads the ime inset each frame
  (`app/src/main/kotlin/dev/bee/kanjianki/KaniImeInsets.kt:14-17`);
  `studyCardImeCompact(imeVisible, hasTypingAnswer, revealed)` is true only
  while the keyboard is open on an unrevealed typing card (`:25-31`).
- Compact mode consumption:
  `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyFlashcardContentCompose.kt`
  — `FlashcardCard` computes `compact` (`:56-66`), the card uses
  `.animateContentSize()` + `heightIn(min = if (compact) 0.dp else 360.dp)`
  (`:71-72`), header chrome is dropped and the question shrinks 27sp→21sp in
  compact (`:161-175`), hero min height 210dp→120dp and glyph 116sp→64sp
  (`:244-261`).
- Net effect when a typing card appears: layout renders full-size for a few
  frames, the auto-focus opens the IME, then **three things move at once** —
  window resize pushes the footer up, the bottom nav disappears (~90dp
  reclaimed), and the card animates into compact. That is the jump.

### Post-session count path

- Learning steps (Anki semantics): defaults new `[1, 10]` minutes, relearning
  `[10]` minutes
  (`core/src/main/kotlin/dev/bee/kanjianki/core/RecordsSchedulerModels.kt:92-104`).
  In-session answers reschedule 1–10 minutes out
  (`core/src/main/kotlin/dev/bee/kanjianki/core/ReviewTransitionEngine.kt:223-294`
  learning; `:336-350` review-`Again` lapse into relearning).
- Session end: `pendingRepairOrDoneRender` renders the done screen the moment
  the completed count reaches the target —
  `if (study.studySessionTracker.atHardCap(...)) { ... renderStudyRunDone(plan) }`
  (`app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyQueueCoordinator.kt:203-206`);
  `atHardCap` = `targetCount > 0 && completedCount >= targetCount`
  (`core/.../StudySessionProgressTracker.kt:83-85`). This fires **before** any
  learning-repeat consideration, so cards from this very session that are due
  again in 1–10 minutes are abandoned mid-learning.
- Same-session re-serve exists but only for repeats already due *now*:
  `StudySessionTracker.dueCompletedLearningRepeatTaskKeys` filters
  `item.dueAtMillis <= nowMillis` and phase `NEW_LEARNING`/`RELEARNING`
  (`app/src/main/kotlin/dev/bee/kanjianki/StudySessionTracker.kt:81-99`);
  consumed by `StudySessionActions.plannedStudySession` /
  `dueLearningRepeatFirst`
  (`app/src/main/kotlin/dev/bee/kanjianki/StudySessionActions.kt:51-86`).
  There is no learn-ahead: a repeat due in 30 seconds is invisible, and the
  hard cap ends the run first anyway.
- Home "N to study" / "Focus x/y left" / Study badge all derive from
  `plan.remaining`:
  - `AdaptiveLoadPlanner.remainingCount` counts a focus kanji when
    `!studiedToday.contains(kanji) || AdaptiveLoadCandidate.isRecoveryDue(item, nowMillis)`
    (`core/src/main/kotlin/dev/bee/kanjianki/core/AdaptiveLoadPlanner.kt:419-433`).
  - `isRecoveryDue` returns **true for any `STATE_LEARNING` item
    unconditionally** — due time is never consulted
    (`core/src/main/kotlin/dev/bee/kanjianki/core/AdaptiveLoadCandidate.kt:107-116`).
  - Consumers: home CTA pill (`MainActivityHome.kt:104-105`,
    `MainActivityHomeOverviewCompose.kt:151`,
    `core/.../HomeTextCopy.kt:207-212`), focus metric
    (`HomeTextCopy.focusHeadline`, `core/.../HomeTextCopy.kt:32-44`), Study
    badge fallback (`app/.../MainActivityShellHost.kt:14-23`, cache refreshed
    at `MainActivityStudyQueueCoordinator.kt:216-218` and
    `MainActivityHome.kt:105`).
- "Today: N due now" is `dueTimes.count { it <= nowMillis }` over all study
  items (`core/src/main/kotlin/dev/bee/kanjianki/core/DailyStudyPlanPolicy.kt:73`)
  — already respects due time; the just-failed cards flip into it as their
  1–10 minute steps elapse. `DailyReminderDecisionPolicy` consumes the same
  `plan.dueNow` for notifications
  (`core/.../DailyReminderDecisionPolicy.kt:143,200,241,284`).
- There is **no cooldown/grace-period concept** anywhere (grep for
  `cooldown|grace|recentlyStudied` finds nothing).

So the screenshot state is explained twice over: `isRecoveryDue`'s
unconditional-learning clause makes "3 to study" appear zero seconds after the
done screen, and the abandoned learning-step cards mature into "3 due now"
minutes later.

---

## Goal PS2: Learning cards count as "to study" only when actually due

**Problem:** `AdaptiveLoadCandidate.isRecoveryDue`
(`core/src/main/kotlin/dev/bee/kanjianki/core/AdaptiveLoadCandidate.kt:107-116`):

```kotlin
if (StudyLadderRules.STATE_LEARNING == item.state) {
    return true                     // due time ignored
}
return item.totalReviews > 0 && item.dueAtMillis <= nowMillis
```

Because `remainingCount` ORs this with `!studiedToday`
(`AdaptiveLoadPlanner.kt:428`), a card answered `Again` in the session that
just ended (state `learning`, due in 1–10 min) counts as remaining instantly.
The home pill, focus metric, and Study badge all show non-zero the moment the
done screen renders — the user's exact complaint.

**Goal:** a `STATE_LEARNING` item is recovery-due only when its due time has
arrived:

- Change the learning clause to `return item.dueAtMillis <= nowMillis` (keep
  the retired-exclusion and the reviewed-card clause unchanged; note the
  reviewed clause requires `totalReviews > 0`, while the learning clause must
  not — a mid-learning card with `totalReviews == 0` that is past due must
  still count, e.g. a card abandoned mid-steps yesterday).
- Audit and deliberately accept the knock-on effects on every `recoveryDue`
  consumer, updating tests that pin the old behavior:
  - Candidate ordering `MANUAL_ORDER`/`AUTO_ORDER`
    (`AdaptiveLoadCandidate.kt:80, 97-101`): a not-yet-due learning card no
    longer outranks an overdue review card. Intended.
  - `recoveryDueCount` → target planning and status copy
    (`AdaptiveLoadPlanner.kt:30-47, 293-295, 409-415`;
    `AdaptiveLoadFocusPolicy.kt:47-64, 152, 183`;
    `AdaptiveLoadStatusFormatter.kt:18-51`). Not-yet-due learning cards stop
    inflating the recovery number. Intended.
  - `FocusedStudyPlanPolicy` (check `itemDueForFocus`) already gates learning
    items on `dueAtMillis <= now` — verify the two policies now agree.
- Document the semantic in `docs/ladder-and-srs-system.md` (or the closest
  scheduler doc section): *mid-learning cards are "to study" only once their
  step delay elapses; immediately after a session the home counts read 0 until
  then.*
- No change to `DailyStudyPlanPolicy.dueNow` (already due-time-gated) and no
  change to FSRS scheduling — goldens must not move.

**Done when (machine-checkable):**

1. Core tests (extend the existing planner/candidate test classes or add
   `core/src/test/kotlin/dev/bee/kanjianki/core/AdaptiveLoadRecoveryDueTest.kt`)
   pass via `./gradlew :core:test`, covering at minimum:
   - learning item due `now + 1min` → `isRecoveryDue == false`;
   - learning item due `now` (and `now - 1ms`) → `true`;
   - learning item with `totalReviews == 0` past due → `true`;
   - retired item → `false` regardless;
   - `remainingCount` returns 0 when every focus kanji is in `studiedToday`
     and its item is mid-learning with a future due time;
   - `remainingCount` counts the same items once `nowMillis` passes their due
     time.
2. App-level home-model regression (suggested
   `app/src/test/kotlin/dev/bee/kanjianki/MainActivityHomePostSessionCountsTest.kt`
   or extension of the existing home model tests) passes: seed a store with
   review-log rows stamped today plus study items in `learning` phase due
   `now + 1..10 min`; build the home screen model; assert
   `studyRemainingCount == 0`, the badge fallback
   (`studySessionBadgeCount(0, 0, cachedPlanRemaining)`) is 0, and the today
   plan reports `dueNow == 0`. Advance `now` past the due times and assert the
   counts reappear.
3. All existing core/app tests updated deliberately (no blind deletions);
   `git diff` contains no changes under any `testdata`/golden directory.
4. `./gradlew ciFast` exits 0.

---

## Goal PS1: Serve same-session learning repeats before declaring the run done (learn-ahead)

**Problem:** the run ends on raw completed-count
(`MainActivityStudyQueueCoordinator.kt:203-206` →
`StudySessionProgressTracker.atHardCap`, `:83-85`) even when cards answered in
this session are sitting in learning steps due 1–10 minutes later
(`ReviewTransitionEngine.kt:223-294, 336-350`; defaults
`RecordsSchedulerModels.kt:92-104`). The in-session re-serve mechanism
(`StudySessionTracker.dueCompletedLearningRepeatTaskKeys`,
`StudySessionTracker.kt:81-99`, consumed via `dueLearningRepeatFirst`,
`StudySessionActions.kt:51-86`) only sees repeats already due at `nowMillis`
and is never consulted by the done-screen branch. Result: "finished" sessions
routinely abandon their own learning-step cards, which then resurface on the
home screen minutes later — the user's complaint that "they should appear in
the current session".

**Goal:** adopt Anki's *learn ahead limit* semantics scoped to the active run:
when the only remaining work is this session's own learning-step repeats, keep
serving them (up to a learn-ahead horizon, default 20 minutes) instead of
rendering the done screen.

- Add a learn-ahead constant (suggest
  `StudyLadderRules.LEARN_AHEAD_MILLIS`/`learnAheadMillis()` or a
  `LearningStepSettings` field; default 20 minutes, matching Anki). No
  settings UI needed in this goal; wire the default only. 20 minutes covers
  the default `1m/10m` steps entirely, so with defaults a session now ends
  only when every served card has graduated past its steps.
- `pendingRepairOrDoneRender`
  (`MainActivityStudyQueueCoordinator.kt:158-208`): before the
  `atHardCap` → `renderStudyRunDone` branch, compute
  `tracker.dueCompletedLearningRepeatTaskKeys(items, now + learnAheadMillis)`;
  when non-empty, do NOT render done — fall through so the normal
  `plannedStudySession` path serves the repeat. (The tracker method takes a
  horizon timestamp already; passing `now + learnAhead` is enough, or add an
  explicit horizon parameter for clarity.)
- `StudySessionActions.plannedStudySession` (`StudySessionActions.kt:51-64`):
  pass the same widened horizon into
  `dueCompletedLearningRepeatTaskKeys(...)`, and make sure the selector can
  actually return those items — `nextSessionForTaskKeys` filters by
  `now + clampStudyAheadMillis(studyAheadMillis)`
  (`core/.../StudySessionSelector.kt`, e.g. `:112, :159`), and the default
  study-ahead is 0 (`SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES = 0`,
  `core/.../SettingsInputRules.kt:7`). Widen the effective horizon **only
  when serving same-session learning repeats** (e.g. use
  `max(studyAheadMillis, learnAheadMillis)` only for the repeat-key pass, or
  serve repeat keys through a dedicated selector call). Ordinary queue
  building for new/other cards must keep the user's configured study-ahead —
  do not globally raise the horizon.
- Invariants to preserve:
  - Learning repeats are practice-only: re-serves must NOT increment
    `completedCount`, promotion/demotion streaks, or any long-term threshold
    (AGENTS.md scheduler rules). Verify the existing repeat path already
    keeps `markTaskCompleted` semantics correct for re-serves and pin it.
  - The done screen renders when no same-session learning repeat is pending
    within the horizon AND (`atHardCap` or queue exhausted). A user answering
    `Again` forever keeps the session alive — Anki parity, accepted.
  - Custom learning steps longer than the learn-ahead horizon legitimately
    leave the session with pending learning cards; PS2 handles home display
    for that case (0 until due).
  - Leaving the Study route mid-run keeps today's abandon semantics
    (`abandonActiveStudyTask`, `resetProgress`) unchanged.
  - No FSRS timing change: answering a repeat early still schedules
    `now + step` at the actual answer time. Goldens must not move.
- Also cover the `renderNoStudySession` branch
  (`MainActivityStudyQueueCoordinator.kt:74-83`): if the selector returns no
  session but same-session repeats are pending within the horizon, the run
  should serve them rather than showing "Nothing due now".

**Done when (machine-checkable):**

1. New app tests (suggested
   `app/src/test/kotlin/dev/bee/kanjianki/StudySessionLearnAheadTest.kt`, or
   split across coordinator/actions tests) pass via
   `./gradlew :app:testDebugUnitTest`, covering at minimum:
   - target-3 session where all 3 cards are answered `again`: the next study
     render does not produce the done screen; it serves the earliest
     learning repeat even though its due time is up to `learnAheadMillis` in
     the future;
   - re-serving a repeat does not change `completedCount()` /
     `atHardCap(...)`;
   - answering the repeats to graduation (`good` on the final step) ends the
     run with the done screen;
   - a learning card whose next step delay exceeds the learn-ahead horizon
     does not block the done screen;
   - ordinary (non-repeat) queue building still uses the configured
     study-ahead of 0 — a fresh not-in-session learning card due in 5 minutes
     is NOT served early.
2. Core selector tests updated/passing if the selector grew a horizon
   parameter: `./gradlew :core:test` exits 0.
3. `git diff` contains no changes under any `testdata`/golden directory.
4. `./gradlew ciFast` exits 0.
5. Manual emulator sanity pass before any release containing this goal: run a
   session, fail every card once, confirm the session keeps serving them
   until passed, then confirm the done screen and a home screen reading 0.

---

## Goal PS3: End-to-end post-session zero-state regression

**Problem:** PS1 and PS2 are independently landable halves of one user-visible
behavior ("finish a session → home reads 0 for a bit"). Nothing currently
pins the combined outcome, so a future planner/session refactor could
reintroduce the instant "3 to study" without failing any test.

**Goal:** one integration-level regression that exercises the full loop with
both fixes landed:

- Drive a complete session at the app-test level (store + coordinator +
  tracker, no UI needed): seed N focus cards, answer each `again` once, then
  answer the repeats through their steps to graduation (per PS1 the session
  keeps serving them), reaching the done screen.
- Immediately rebuild the home screen model at the same `now`:
  `studyRemainingCount == 0`, today plan `dueNow == 0`, focus headline
  reports complete (`plan.focusComplete()` true /
  `remaining == 0`), Study badge resolves to 0.
- Advance the clock past the graduated cards' FSRS due times and assert the
  counts become non-zero again (proves the zero-state is a timing behavior,
  not a suppressed count).
- Add a short "post-session counts" subsection to
  `docs/ladder-and-srs-system.md` describing the intended behavior and
  pointing at this test as the pin.

**Done when (machine-checkable):**

1. New test (suggested
   `app/src/test/kotlin/dev/bee/kanjianki/PostSessionZeroStateRegressionTest.kt`)
   passes via `./gradlew :app:testDebugUnitTest --tests "dev.bee.kanjianki.PostSessionZeroStateRegressionTest"`.
2. The doc section exists and names the test class.
3. `./gradlew ciFast` exits 0.

---

## Goal KB1: Typing cards render keyboard-ready from the first frame

**Problem:** the `type_meaning` card auto-focuses its answer field and
force-opens the keyboard on every card
(`MainActivityStudyTypingAnswerCompose.kt:61-69`), but the keyboard-adapted
layout is gated on *live IME visibility*, so the user always sees the
full-size layout for a few frames and then everything moves at once:

- compact mode requires `imeVisible`
  (`studyCardImeCompact`, `KaniImeInsets.kt:25-31`), so the card animates
  hero 210dp→120dp, glyph 116sp→64sp, header chrome out, min-height 360dp→0
  (`MainActivityStudyFlashcardContentCompose.kt:56-92, 161-175, 244-261`)
  exactly while the IME slides in;
- the bottom nav is removed only once `imeVisible` flips
  (`MainActivityShell.kt:184-194`), reclaiming ~90dp mid-animation;
- the window resize (`adjustResize`, `AndroidManifest.xml:35`) plus
  `.imePadding()` (`MainActivityShell.kt:172`) moves the footer action bar up
  at the same time.

Since the keyboard is guaranteed to open on these cards, the pre-IME layout is
a transient state that exists only to be animated away — the "jump" the user
dislikes.

**Goal:** for an unrevealed typing card, the screen starts in its
keyboard-open configuration; the only motion when the IME animates in is the
footer riding up with the window resize (unavoidable — the keyboard physically
takes that space):

- Gate compact mode on card state, not IME state: `studyCardImeCompact`
  becomes true for `hasTypingAnswer && !revealed` regardless of
  `imeVisible` (keep the function but change the predicate, or replace the
  `imeVisible` argument at the call site
  `MainActivityStudyFlashcardContentCompose.kt:56-66`). Non-typing cards keep
  today's behavior exactly (they never enter compact because
  `hasTypingAnswer` is false).
- Hide the bottom nav for the whole unrevealed-typing-card state, not just
  while the IME is visible: thread a "study card is keyboard-resident" flag
  from the study render into the shell (e.g. a field on
  `MainActivityShellModel` consumed at `MainActivityShell.kt:188`, condition
  becoming `!imeVisible && !model.studyCardKeyboardResident`-style — the
  `imeVisible` clause must stay so the nav also hides if the IME opens
  anywhere else). The nav is then absent from the typing card's first frame,
  so its disappearance never coincides with the keyboard animation; it
  toggles only at card boundaries, where the whole content changes anyway.
- The reveal transition (keyboard closes, full layout + nav return) stays
  as-is; this goal only stabilizes card entry / IME-open.
- Keep `.animateContentSize()`
  (`MainActivityStudyFlashcardContentCompose.kt:71`) — with compact decided
  at composition time it no longer animates on IME-open; it still smooths the
  reveal expansion.
- Update the stale comments that explain compact/nav-hiding in terms of IME
  visibility (`KaniImeInsets.kt:19-24`, `MainActivityShell.kt:184-187`,
  `MainActivityStudyFlashcardContentCompose.kt:58-61`) to describe the new
  state-driven rule.

**Done when (machine-checkable):**

1. `app/src/test/kotlin/dev/bee/kanjianki/StudyCardImeCompactUnitTest.kt`
   updated deliberately — its line 15 currently pins the exact case this goal
   flips (`imeVisible = false, hasTypingAnswer = true, revealed = false` →
   false must become → true). Final coverage:
   - unrevealed typing card, IME hidden → compact true (the changed case);
   - unrevealed typing card, IME visible → compact true;
   - revealed typing card → compact false;
   - non-typing card, IME visible → compact false.
2. A shell/nav-visibility test (Compose UI test in the existing app test
   harness — follow the pattern used by current shell/compose tests under
   `app/src/test` or `app/src/androidTest`) asserts: rendering the study
   route with an unrevealed typing-card model composes no bottom nav node
   even with IME hidden, and rendering a non-typing card model still composes
   the nav.
3. `./gradlew :app:compileDebugAndroidTestJavaWithJavac` exits 0 (existing
   instrumentation still compiles).
4. `./gradlew ciFast` exits 0.
5. Manual emulator/device check documented in the PR/commit message: open a
   typing card — the card renders compact with no nav from the first frame,
   and the keyboard sliding in causes no reshaping above the footer.

---

## Goal KB2 (optional polish): Smooth IME transition via edge-to-edge insets

**Problem:** with the default decor-fits-system-windows mode plus
`adjustResize` (`AndroidManifest.xml:35`), the window snaps to its new size
when the IME opens instead of tracking the keyboard animation frame-by-frame.
Compose's `imePadding()` (`MainActivityShell.kt:172`) only animates smoothly
in sync with the IME when the app draws edge-to-edge
(`WindowCompat.setDecorFitsSystemWindows(window, false)` /
`enableEdgeToEdge()`), which the app never enables
(`MainActivityUiSupport.kt:19-33` only sets bar colors). After KB1, the
remaining visible motion on IME-open is the footer snapping up; edge-to-edge
turns that snap into a smooth slide.

**Goal:** enable edge-to-edge and let the existing inset modifiers own the
system-bar/IME space:

- Call `enableEdgeToEdge()` (androidx.activity) or
  `WindowCompat.setDecorFitsSystemWindows(window, false)` before
  `setContent`, reconciling with the current
  `WindowInsetsControllerCompat` bar-color logic
  (`MainActivityUiSupport.kt:25-33`) — with edge-to-edge, bar backgrounds
  come from the app surface, so verify contrast/scrim on both gesture nav and
  3-button nav.
- `.systemBarsPadding().imePadding()` already on the shell column
  (`MainActivityShell.kt:171-172`) should keep every route's visible layout
  identical; audit all routes (Home, Study, Stats, Settings) plus dialogs for
  double-padding or newly-uncovered areas.
- Risk containment: this is a visual-blast-radius change. Use the screenshot
  harness (`docs/design/kani-theme-screenshot-harness.md`) to diff every
  route before/after; any unexpected pixel shift outside the bar regions is a
  failure.

**Done when (machine-checkable):**

1. Edge-to-edge enabled in one place; no per-screen inset hacks added.
2. Screenshot-harness run before/after shows diffs confined to system-bar
   regions (attach the diff summary to the PR/commit).
3. `./gradlew ciFast` exits 0.
4. Manual validation on emulators at API 30 and API 35, both gesture and
   3-button nav: status/nav bar readability on all four routes, keyboard
   open/close on a typing card animates the footer smoothly, no content
   hidden behind bars.
5. If any of the manual checks cannot be performed, do not land — this goal
   is optional and must not ship blind.
