# Kani

Kani is a native Android companion for a single AnkiDroid collection. It syncs live `Kiku` notes from AnkiDroid, archives suspended trouble cards locally, derives weak kanji from real examples, runs a small bridge SRS, and updates itself from GitHub Releases.

The runtime is Android-first. There is no Python server, fixture runtime, or polling loop. After the first successful manual sync, Kani schedules one background AnkiDroid sync per local day.

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
