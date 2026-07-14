<!-- markdownlint-disable MD013 MD033 MD041 -->

<p align="center">
  <img alt="Kani app logo" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160">
</p>

<h1 align="center">Kani</h1>

<p align="center">
  <a href="https://github.com/bee-san/kanji_anki/releases">
    <img alt="GitHub downloads" src="https://img.shields.io/github/downloads/bee-san/kanji_anki/total?style=for-the-badge&logo=github&label=downloads">
  </a>
  <a href="https://github.com/bee-san/kanji_anki/actions/workflows/sonarqube.yml">
    <img alt="SonarCloud quality gate" src="https://sonarcloud.io/api/project_badges/measure?project=bee-san_kanji_anki&metric=alert_status">
  </a>
  <a href="https://sonarcloud.io/summary/overall?id=bee-san_kanji_anki">
    <img alt="SonarCloud code smells" src="https://sonarcloud.io/api/project_badges/measure?project=bee-san_kanji_anki&metric=code_smells">
  </a>
</p>

Kani cures your Kanji blindness by analysing your Anki, locating your hard kanji, working out why it's hard for you, and creating specialised learning loops to teach you the Kanji in the least amount of time possible.

1. Find kanji that keep causing trouble in your AnkiDroid reviews.
2. Focus on why they are hard, such as unfamiliar characters, visually similar kanji, unuusal readings
3. Study them through a small, structured queue instead of another full SRS backlog.
4. Compare later AnkiDroid evidence so repaired kanji can retire from the queue.

Features:
* FSRS 5
* Specially designed to make you spend the least amount of time in app as possible
* Customised study ladders for each kanji, based on why you fail that kanji
* Can use your GSM + Subminer data to optimise the algorithm

Think of it as a mix between Full on Wanikani / RTK and vocab with no Kanji study.

If you study vocab with no individual Kanji study but find some Kanji are hard, this app is for you.

Kani is an AnkiDroid companion app for Japanese learners who repeatedly miss the same kanji — the painful "kanji blindness" loop where similar-looking characters keep tripping you up.

Kani borrows the parts of Anki that make recall durable: saved evidence from your own cards, FSRS-style review timing, and a repeatable review habit. It intentionally differs from Anki by narrowing the surface area to kanji repair. Kani reads AnkiDroid, builds a small local focus queue, and asks you to practice the characters that are causing real misses instead of managing another general-purpose deck.

Kani helps you:

1. Find kanji that keep causing trouble in your AnkiDroid reviews.
2. Focus on why they are hard, such as unfamiliar characters or visually similar kanji.
3. Study them through a small, structured queue instead of another full SRS backlog.
4. Compare later AnkiDroid evidence so repaired kanji can retire from the queue.

# Features

## AnkiDroid companion model

| Area | Like Anki / AnkiDroid | Kani's kanji-study difference |
| --- | --- | --- |
| Source material | Uses your own cards and review evidence. | Reads AnkiDroid locally and turns weak kanji evidence into a focused repair queue. |
| Scheduling | Uses FSRS-style spacing for review timing. | Schedules Kani practice items only; it does not replace or rewrite Anki's deck schedule. |
| Card shape | Keeps recall practice deliberate and repeatable. | Uses a configurable ladder: handwriting, typed meaning, recognition, font variation, readings, and similar-kanji practice. |
| Scope | Supports daily review habits. | Optimizes for kanji blindness, not general vocabulary, grammar, or sentence mining workflows. |
| Suspended cards | Preserves evidence from cards you no longer actively review. | Archives suspended-card evidence locally by default so missed kanji can still be studied without unsuspending Anki cards. |
| Progress evidence | Review history remains the source of truth. | Compare later AnkiDroid evidence with Kani reviews to show whether the repair work is helping. |

## Flashcards

- FSRS scheduling for Kani study items.
- A progressive ladder that starts with easier prompts and moves toward harder kanji recall as you improve.

The study ladder can surface these prompt styles:

- Handwriting practice with stroke guidance.
- Typed meaning prompts.
- Recognition cards for kanji meanings.
- Font variation to help recognition across different typefaces.
- Word-to-reading prompts based on words imported from AnkiDroid.
- Similar-kanji practice when Kani has comparison data for a card.

You can enable, disable, and reorder ladder rungs in Settings. Study items move through the ladder according to review results, and `Study now` is the single entry point for practice.

Kani uses the Pareto principle: focus on the kanji most worth studying today instead of reviewing everything. By default, the suspended-kanji import focuses on Jiten ranks `100` through `3000`, and you can change that range in Settings.

### Browser query import

Suspended cards remain the default import source. Browser query import is opt-in from Settings for cases where you want Kani to consider the same cards you would find with an Anki browser search, such as:

- `is:suspended`
- `rated:31:1`
- `deck:Japanese tag:kani`
- `prop:due<=0 -is:suspended`

Kani sends the query to AnkiDroid unchanged, then filters the returned notes by the configured note type, rank range, and minimum matching-card threshold before adding kanji to the practice queue. Query text can include private deck names, tag names, or other collection-specific labels; it is used locally for the AnkiDroid provider search and is redacted from import audit output. If the query selects suspended cards, Kani archives those selected suspended cards locally before the provider cleanup hides them from later syncs.

The goal is to spend less time managing study queues and more time reading, listening, and immersing.

Other product areas:

- Suspended AnkiDroid cards are archived locally by default and imported through a dedicated suspended-kanji module.
- Jiten kanji frequency ranks are bundled offline for filtering.
- Manual sync reads AnkiDroid's exported flashcard provider; daily auto sync starts after the first successful manual sync and gives explicitly transient failures three bounded follow-up attempts.
- Releases are signed and published with APK and SHA-256 checksum artifacts.

## Product Contract

- Manual sync reads AnkiDroid's exported flashcard provider.
- Daily auto sync starts after the first successful manual sync, uses the same provider sync path, and follows the documented [bounded retry and JobService lifecycle contract](docs/auto-sync-reliability.md).
- The expected note type is `Kiku`, with the `Mining` card template.
- Required fields are `Expression`, `ExpressionReading`, `MainDefinition`, `Sentence`, `Frequency`, and `FreqSort`.
- Suspended cards are archived locally and processed by the dedicated suspended-kanji import module.
- Browser query import is opt-in. Suspended-only import remains the default, and query text stays local because it may contain private deck or tag names.
- Query-selected suspended cards are archived locally before provider cleanup hides them from later syncs.
- Jiten kanji frequency ranks are bundled in the offline dictionary DB for filtering. The default suspended import range is ranks `100` through `3000`, and it can be changed in Settings.
- Weak-kanji rows and details are derived from the active mirror plus the suspended archive.
- Kani keeps its own local FSRS-style queue and never rewrites Anki's deck schedule.
- `Study now` is the single study entry point.
- Per-kanji mnemonic notes created in Browse stay local, are not imported from AnkiDroid fields, and are included in Kani's database backups where safe backups are available.
- Releases are signed, tagged as `vMAJOR.MINOR.PATCH`, and published with an APK plus SHA-256 checksum.
- On Android 11+, Kani keeps WAL-safe local database backups under the app-private `backups/` directory, retaining seven recent daily snapshots plus up to four older weekly snapshots. Android 8–10 leaves the current database and existing archives unchanged because stock SQLite cannot create the required safe live snapshot. App files, databases, and preferences remain excluded from Android cloud backup or device transfer.
- Backup diagnostics are sanitized: logs name the backup action and exception type only, not database paths, backup paths, note contents, or raw exception messages.

## Build

```bash
./gradlew :core:test :app:assembleDebug
```

For a narrow copy check while iterating on centralized product text, run:

```bash
./gradlew :core:test --tests dev.bee.kanjianki.core.HomeTextCopyTest
```

If Gradle daemons from other workspaces interfere on a shared machine, isolate the user home for a retry:

```bash
GRADLE_USER_HOME="$PWD/.gradle-task" ./gradlew :core:test --tests dev.bee.kanjianki.core.HomeTextCopyTest
```

For CI and SonarQube triage, see `docs/ci-sonar-reliability-runbook.md`.

Release builds require signing environment variables:

```bash
KANI_SIGNING_STORE_FILE=/path/to/release.jks
KANI_SIGNING_STORE_PASSWORD=...
KANI_SIGNING_KEY_ALIAS=...
KANI_SIGNING_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

## Release

Push a semver tag such as `v0.3.0`, or create/publish a GitHub Release with that tag name. GitHub Actions builds the signed APK, writes a matching `.sha256`, and publishes both files to the release.

The download, validation, retry, permission, and cache invariants for updates installed from inside Kani are documented in the [in-app update security contract](docs/in-app-update-security.md).

## Runbook pointers

- Product contract checks: read this README and the centralized strings in `core/src/main/kotlin/dev/bee/kanjianki/core/HomeTextCopy.kt` before changing user-facing copy.
- Targeted copy regression: `./gradlew :core:test --tests dev.bee.kanjianki.core.HomeTextCopyTest`.
- Full local smoke check: `./gradlew :core:test :app:assembleDebug`.
- Remote Android screenshots and Ralph loop: `docs/ralph-ui-loop/runbook.md`.
- Release verification: confirm the GitHub Release contains both the signed APK and matching `.sha256` artifact.

## Cheap Ralph queue

Cheap Ralph's forever loop reads this checklist from top to bottom. It should keep taking one small PR-sized slice for the first unchecked item, tick an item only when it is truly done with PR/review/CI evidence, and then move to the next unchecked item. Learning-experience and scheduler-correctness items belong above cosmetic polish because Kani is a learning app first.

If no unchecked items remain, Cheap Ralph should not silently stop or invent work unaudited. It should draft 10 concise candidate ideas, Telegram them to Bee, and ask which ones Cheap Ralph should work on next. The 10 ideas may include Kani and other Cheap Ralph-suitable projects; bias toward Kani while it is Bee's active project, but do not assume every idea must be Kani. Do not create new cards or PRs from those ideas until Bee chooses; once Bee selects items, append the approved ideas as unchecked queue entries in the requested order.

<!-- cheap-ralph-queue:start -->
- [x] Continue Kotlin conversion across Java/Kotlin source; do not tick complete until Bee accepts it or inventory shows only documented exceptions remain.
- [x] Prioritize the learning experience: audit every FSRS, learning-step, relearning-step, review-button, custom-setting, session-selection, due-repeat, graduation/lapse, ladder-movement, and study-copy path against Anki/AnkiDroid behavior; fix discrepancies so Kani's scheduler feels predictably Anki-like and pleasant before continuing non-learning polish. Do not tick complete until core/app tests and manual-use evidence cover custom steps, due learning repeats, lapses, graduation, and Settings defaults.
- [x] Take a long hard look at all Settings: add missing useful settings, remove redundant or confusing ones, recategorise everything into simple user-understandable groups, improve names/descriptions/defaults, and make the whole Settings experience easy to understand. Selecting a Settings category must update in place without refreshing/recreating the whole Settings page, losing scroll position, or jumping back to the top. Do not tick complete until manual-use evidence and tests cover category navigation, deep Settings sections, accessibility/test tags, preserved warnings, defaults, and any intentional scheduler/storage semantic changes.
- [x] Ensure notifications are good. We should notify the user if they are about to lose their streak, for example notifications at 8am, 9am, 1pm, 8pm, 10pm. We should give the option to turn off all notifications. We should only notify if they have not hit their streak today. if they did a study session and then learning items came back later in the day, we should notify about that too like "You have more Kanji to review", we should only show this twice a day. The times depend on when the kanji will come back, so you need to think about this. For example if user studies at 9am, and fsrs says "come back in 3 hours", we should show it in 3 hours. But we need to be careful not to spam the user with too many notifications, so we should look ahead. If 2 kanji come in 3,4,5 hours and 5 hours is before 10pm we should notify the user then. Basically we need some magical logic
- [x] Benchmark every aspect of the app, including every screen, navigation path, and every single button press, then fix the slow interactions that make Kani feel laggy. Start with real before/after timings, traces, and a per-button latency inventory; focus on practical wins such as avoiding unnecessary recomposition, repeated database/import work, blocking main-thread calls, or whole-screen refreshes. Do not do a giant rewrite, risky data migration, release/signing change, or cosmetic redesign just for speed. Do not tick complete until Android Studio/profiling evidence, regression tests, and a manual-use pass cover all buttons/options/ladder stages, any remaining waits over about 1 second are justified, and known 10-second interactions are gone or clearly escalated.
- [x] Go through every user-facing view, dialog, empty/error state, onboarding/import path, study flow, Settings surface, and stats/history screen; analyse the whole UX/UI, not just copy. For each surface, remove non-essential text, make the primary action and next step obvious, improve information hierarchy, spacing, touch targets, accessibility labels/test tags, loading/empty/error states, and visual consistency with Android/Material expectations. Keep learning and scheduler correctness intact, avoid a cosmetic-only redesign or giant architecture rewrite, and split risky changes into small PR-sized slices. Completion evidence: `docs/cheap-ralph/2026-06-10-ux-copy-final-pass.md`. Do not tick complete until there is a view-by-view inventory with before/after rationale, regression tests or screenshot/manual evidence for the changed surfaces, and a final pass confirming every screen feels clear, useful, and pleasant to use.
- [x] Analyse the stats page. Really think in depth, what stats would help the user understand if Kani is working for them? What stats would be cool to show off? What stats would help them? Implement them. Do not stop at 1 or 2 stats. Feel free to modify existing stats.
- [x] Add a Japanese translation to the app
- [x] Build the Scheduler / FSRS Correctness Lab from `docs/plans/cheap-ralph/2026-06-09-scheduler-fsrs-correctness-lab.md`: add debug/test-only `SchedulerDecisionTrace` for `BridgeScheduler.nextSession()` and `applyReview()`, golden life-of-kanji scenario simulation, Anki parity snapshots, and eventually a scheduler playground. Keep FSRS as memory/interval math only; Kani policy still owns queue selection, ladder movement, learning/relearning steps, sibling suppression, and UI wording. Do not tick complete until the five golden scenarios and trace/explanation acceptance tests pass with PR/review/CI evidence.
- [x] Build the Kanji Repair Evidence Engine from `docs/plans/cheap-ralph/2026-06-09-kanji-repair-evidence-engine.md`: create a confidence-aware `KanjiRepairEvidencePolicy`, per-kanji repair evidence surfaces, cohort dashboards, and later repair-attribution summaries using correlation language rather than false causality. Do not tick complete until Kani can explain why a kanji is queued, what changed before/after Kani reviews, whether evidence is insufficient/improving/stable/regressing, and which sync/review evidence supports that claim.
- [x] Build Habit Intelligence from `docs/plans/cheap-ralph/2026-06-09-habit-intelligence-daily-planning.md`: ship `DailyStudyPlanPolicy`, a Today home card, due-later lookahead clustering, and conservative explainable reminder policy before deeper WorkManager/AlarmManager hardening. Keep reminders calm, opt-out-friendly, and minimum-effective-repair oriented; do not tick complete until the Today card and notification decisions have reason strings, caps, quiet-hour behavior, tests, and PR/CI evidence.
- [x] Build the World-Class Similar Kanji + Writing Study Experience from `docs/plans/cheap-ralph/2026-06-09-similar-kanji-writing-study-experience.md`: implement undo-last-rating UI first, then similar-kanji explanation cards, tutor-only writing feedback, adaptive hint progress, and study-flow polish. Keep writing diagnosis helpful rather than punitive and never let similar-kanji repair become a separate scheduler queue. Completion evidence: `docs/cheap-ralph/2026-06-12-similar-kanji-writing-study-evidence.md`.
- [x] Build the screenshot-driven Cheap Ralph UX improvement loop from `docs/plans/cheap-ralph/2026-06-09-screenshot-driven-ux-improvement-loop.md`: deterministically capture every view, store screenshot manifests, ask a design critic for machine-readable improved-view targets, implement one accepted issue per iteration in a scratch checkout, and gate apply/commit on before/after screenshots, compile/tests, forbidden-path guards, diff limits, and design-critic improvement. Default to review-only and never treat compile-only success as visual validation.
<!-- cheap-ralph-queue:end -->
