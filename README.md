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

You can disable / enable / move them up and down the ladder however you want.

Not another SRS app. Kani uses the Pareto principe to identify kanji actually worth doing that day.
You know when you're doing Anki and it shows you 私 and you're just like... what was the point of that?

Kani will pick up (by default) up to 5 Kanji that are the absolute best bang for your buck each day, using all the data we have from Ankidroid + your Kani reviews.

No more wasting time. I want you to spend as little time in this app as possible to spend more time immersing.

Random features I like:
* Frequency based retention. Set 95% retention rate for Kanji with frequency of 1000 or more, and 80% for 1000 - 2000 and so on.
* Fun games to play to memorise your problematic Kanji while on the train etc
* Complete control over what is defined as a problematic kanji. You decide how you want to import problematic Kanji!
* Stats - If Kani is not working for you... it will literally tell you "Kani is not working for you."

## Product Contract

- Manual sync reads AnkiDroid's exported flashcard provider.
- Daily auto sync starts after the first successful manual sync and uses the same provider sync path.
- The expected note type is `Kiku`, with the `Mining` card template.
- Required fields are `Expression`, `ExpressionReading`, `MainDefinition`, `Sentence`, `Frequency`, and `FreqSort`.
- Suspended cards are archived locally and processed by the dedicated suspended-kanji import module.
- Jiten kanji frequency ranks are bundled in the offline dictionary DB for filtering. The default suspended import range is ranks `100` through `3000`, and it can be changed in Settings.
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

Push a semver tag such as `v0.3.0`, or create/publish a GitHub Release with that tag name. GitHub Actions builds the signed APK, writes a matching `.sha256`, and publishes both files to the release.
