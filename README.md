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

- `rated:1`
- `deck:Japanese tag:kani`
- `prop:due<=0 -is:suspended`

Kani combines the query with the configured note type, then still applies the rank range and minimum matching-card threshold before adding kanji to the practice queue. Query text can include private deck names, tag names, or other collection-specific labels; it is used locally for the AnkiDroid provider search and is redacted from import audit output. If the query selects suspended cards, Kani archives those selected suspended cards locally before the provider cleanup hides them from later syncs.

The goal is to spend less time managing study queues and more time reading, listening, and immersing.

Other product areas:

- Suspended AnkiDroid cards are archived locally by default and imported through a dedicated suspended-kanji module.
- Jiten kanji frequency ranks are bundled offline for filtering.
- Manual sync reads AnkiDroid's exported flashcard provider; daily auto sync starts after the first successful manual sync.
- Releases are signed and published with APK and SHA-256 checksum artifacts.

## Product Contract

- Manual sync reads AnkiDroid's exported flashcard provider.
- Daily auto sync starts after the first successful manual sync and uses the same provider sync path.
- The expected note type is `Kiku`, with the `Mining` card template.
- Required fields are `Expression`, `ExpressionReading`, `MainDefinition`, `Sentence`, `Frequency`, and `FreqSort`.
- Suspended cards are archived locally and processed by the dedicated suspended-kanji import module.
- Browser query import is opt-in. Suspended-only import remains the default, and query text stays local because it may contain private deck or tag names.
- Query-selected suspended cards are archived locally before provider cleanup hides them from later syncs.
- Jiten kanji frequency ranks are bundled in the offline dictionary DB for filtering. The default suspended import range is ranks `100` through `3000`, and it can be changed in Settings.
- Weak-kanji rows and details are derived from the active mirror plus the suspended archive.
- Kani keeps its own local FSRS-style queue and never rewrites Anki's deck schedule.
- `Study now` is the single study entry point.
- Releases are signed, tagged as `vMAJOR.MINOR.PATCH`, and published with an APK plus SHA-256 checksum.
- Kani keeps local database backups under the app-private `backups/` directory, retaining the newest 31 snapshots and excluding app files, databases, and preferences from Android cloud backup or device transfer.
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

## Runbook pointers

- Product contract checks: read this README and the centralized strings in `core/src/main/kotlin/dev/bee/kanjianki/core/HomeTextCopy.kt` before changing user-facing copy.
- Targeted copy regression: `./gradlew :core:test --tests dev.bee.kanjianki.core.HomeTextCopyTest`.
- Full local smoke check: `./gradlew :core:test :app:assembleDebug`.
- Release verification: confirm the GitHub Release contains both the signed APK and matching `.sha256` artifact.

## Cheap Ralph queue

Cheap Ralph's forever loop reads this checklist from top to bottom. It should keep taking one small PR-sized slice for the first unchecked item, tick an item only when it is truly done with PR/review/CI evidence, and then move to the next unchecked item. If no unchecked items remain, the loop stops creating new cards.

<!-- cheap-ralph-queue:start -->
- [x] Continue Kotlin conversion across Java/Kotlin source; do not tick complete until Bee accepts it or inventory shows only documented exceptions remain.
- [ ] Reduce excessive Settings copy, starting with Settings copy files and preserving warnings, accessibility labels, test tags, and behavior.
- [ ] Slim and compact Settings one section at a time without broad redesigns, navigation rewrites, or scheduler/storage semantic changes.
- [ ] Go through each view in the app and analyse the copy on the page. Make sure it is absolutely essential for that page, if it isn't remove it.
- [ ] Emulate the whole app in Android Studio and use the app for 30 minutes, going through every single ladder stage and option. Fix as many issues as you find.
<!-- cheap-ralph-queue:end -->
