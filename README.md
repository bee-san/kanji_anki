<p align="center">
  <img alt="Kani app logo" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160">
</p>

<h1 align="center">Kani</h1>

<p align="center">
  <a href="https://github.com/bee-san/kanji_anki/releases">
    <img alt="GitHub downloads" src="https://img.shields.io/github/downloads/bee-san/kanji_anki/total?style=for-the-badge&logo=github&label=downloads">
  </a>
  <a href="https://github.com/bee-san/kanji_anki/actions/workflows/sonarqube.yml">
    <img alt="Core class coverage 100%" src="https://img.shields.io/badge/core%20class%20coverage-100%25-brightgreen?style=for-the-badge&logo=gradle">
  </a>
</p>

Kani is an Ankidroid companion app for Japanese learners who find suffer from Kanji Blindness.

Kani will:
1. Identify problematic Kanji you repeatedly fail
2. Work out _why_ you fail them (Completely unknown? Similar to other Kanji?)
3. Gives you structured, personal flashcards to help you learn them

# Features

## Flashcards
- FSRS for all Kani flashcards
- Progressive ladder, if the Kanji is really hard we break it all the way down for you. If it's a bit hard but possible we show you easier flashcards.

Here are some flashcard types you may see:
- Hand-writing cards with Ringotan style fading away. Trace a Kanji, each time you get it right a stroke will fade away until you 100% mastered it.
- Recognition, Kanji -> Meaning
- Different font cards. Some cards will randomise their font to help you learn it across different fonts too
- Word -> Reading , we take the word(s) with the Kanji you frequently fail in Ankidroid and show you them again so you can learn it this time
- Similar Kanji. If you fail say 5 words and 2 Kanji are identified as problematic, Kani will be able to tell if its because they are similar to one another and we will help teach you how to tell them apart.

Not another SRS app. Kani uses the Pareto principe to identify kanji actually worth doing that day.
You know when you're doing Anki and it shows you 私 and you're just like... what was the point of that?

Kani will pick up (by default) up to 5 Kanji that are the absolute best bang for your buck each day, using all the data we have from Ankidroid + your Kani reviews.

No more wasting time. I want you to spend as little time in this app as possible to spend more time immersing.

**todo**
- similar kanji cards
- writing similar kanji cards




## Install

1. Install AnkiDroid.
2. Download the latest `kani-android-X.Y.Z.apk` from
   [GitHub Releases](https://github.com/bee-san/kanji_anki/releases).
3. Open the APK on your Android device and allow installs from that source.
4. Open Kani, grant the AnkiDroid provider permission, then tap
   `Sync AnkiDroid`.

Kani is sideloaded; it is not distributed through the Play Store.

## Screenshots

These screenshots were captured from the Android emulator used by the test
suite.

<p>
  <img alt="Kani home screen showing active kanji repair queue" src="docs/screenshots/home.png" width="260">
  <img alt="Kani kanji detail screen showing recovery timeline" src="docs/screenshots/timeline.png" width="260">
  <img alt="Kani study repair screen with stroke guide and writing pad" src="docs/screenshots/study-repair.png" width="260">
</p>

## What It Does

- Syncs `Kiku` notes and cards through AnkiDroid's flashcard provider.
- Archives suspended trouble cards locally and tags archived notes in AnkiDroid
  when the provider allows it.
- Builds a weak-kanji queue from real active and suspended examples.
- Shows a per-kanji recovery timeline: first import, support changes, reviews,
  retirements, and reopened repairs.
- Runs a small bridge SRS for focused recall and writing repair.
- Uses bundled Jiten rank data and stroke-order guides offline.
- Schedules one local background sync per day after the first successful manual
  sync.
- Checks GitHub Releases and can install a verified APK update with Android
  confirmation.

The runtime is Android-first. There is no Python server, fixture runtime, or
polling loop.

## Product Contract

- Manual sync reads AnkiDroid's exported flashcard provider.
- Daily auto sync starts after the first successful manual sync and uses the same provider sync path.
- The expected note type is `Kiku`, with the `Mining` card template.
- Required fields are `Expression`, `ExpressionReading`, `MainDefinition`, `Sentence`, `Frequency`, and `FreqSort`.
- Suspended cards are archived locally and processed by the dedicated suspended-kanji import module.
- The full Jiten kanji frequency CSV is bundled for offline filtering. The default suspended import cutoff is rank `3000`, and it can be changed in Settings.
- Weak-kanji rows and details are derived from the active mirror plus the suspended archive.
- `Study now` is the single study entry point.
- Releases are signed, tagged as `vMAJOR.MINOR.PATCH`, and published with an APK plus SHA-256 checksum.

## Build

```bash
gradle :core:test :app:assembleDebug
```

Release builds require signing environment variables:

```bash
KANI_SIGNING_STORE_FILE=/path/to/release.jks
KANI_SIGNING_STORE_PASSWORD=...
KANI_SIGNING_KEY_ALIAS=...
KANI_SIGNING_KEY_PASSWORD=...
gradle :app:assembleRelease
```

## Release

Push a semver tag such as `v0.3.0`. GitHub Actions builds the signed APK, writes a matching `.sha256`, and publishes both files to the release.
