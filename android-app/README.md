# Android App Scaffold

This directory is the first Android migration slice for the Kotlin/Compose port described in `plans/android-port-github-backup-plan.md`.

Implemented features:

- Live AnkiDroid note/card sync through the exported `flashcards` content provider when runtime permission is granted.
- Honest empty-state boot when no AnkiDroid snapshot has been synced yet, with explicit install/permission guidance instead of demo fallback data.
- Room-backed persistence for settings, source snapshots, problem-kanji dashboard rows, sync runs, study items, and review logs.
- Compose dashboard, detail, study, and settings screens with manual sync and latest-sync health visibility.
- Editable on-device settings for note-model filtering, field mapping, study thresholds, and background polling.
- Local study queue generation, session creation, handwriting policy enforcement, and review progression after bootstrap.
- GitHub Releases updater support for checking, downloading, and installing newer APK assets from a public feed.
- Tag-driven GitHub Actions release CI that builds signed APK releases and publishes matching SHA-256 checksum files.
- Local JVM unit tests for sync-failure policy, default Android runtime settings, and dashboard/detail derivation.

Current scope:

- `parity-fixtures/oracle-v1.json` remains the frozen Python migration oracle for parity work and contract comparison; it is no longer packaged into the production APK as a runtime fallback.
- `app/` is a Compose shell backed by a Room-cached repository that reads live note/card data from AnkiDroid's exported content provider, derives dashboard/problem rows locally, and speaks the planned Android service surface.
- `app/src/main/java/.../data/local` mirrors the current SQLite schema in Room entities and DAOs.
- `app/src/main/java/.../data/sync` now configures periodic WorkManager polling from cached Android settings and keeps the manual/background sync boundary separate from the repository logic.
- `app/src/main/java/.../data/ankidroid` now defines a collection-snapshot contract plus a live content-provider gateway for AnkiDroid's `flashcards` provider.
- `app/src/main/java/.../data/update` checks GitHub Releases, downloads the latest APK asset, and hands it to Android's installer flow.

Current limitations:

- The Room cache now owns source snapshot persistence, dashboard derivation, study seed refresh, overview generation, session selection, handwriting enforcement, review progression, and review-log persistence after bootstrap.
- The Compose dashboard can now trigger manual sync directly, and runtime health shows the latest recorded sync run from Room.
- The Compose settings screen can now edit note-model filtering, field mapping, study thresholds, and polling configuration, inspect AnkiDroid install/permission state, request the runtime permission, then resync the local cache immediately after saving or granting access.
- The dashboard now drills directly into whichever kanji row you tap, instead of keeping the detail screen pinned to a single fixture example.
- Static kanji content is now derived from the synced collection itself; the Android app still does not load KANJIDIC2 or KanjiVG locally.
- WorkManager now routes through the same live repository boundary as manual sync, follows `pollingEnabled` plus `pollingIntervalSeconds`, returns hard failure for permanent AnkiDroid/configuration problems, and still respects Android's 15-minute minimum interval.
- GitHub release auto-update only works against a public release feed. If the source repo stays private, point the updater at a public mirror repo using Gradle properties.

AnkiDroid integration notes:

- The app declares `com.ichi2.anki.permission.READ_WRITE_DATABASE` plus the debug equivalent and queries both release/debug provider authorities.
- If that permission is not granted, sync fails closed and the app stays in an explicit empty state until access is granted.
- If AnkiDroid is installed and the permission is granted, manual sync and background sync read notes/cards from `content://com.ichi2.anki.flashcards/...` (or the debug authority).

GitHub release updater configuration:

- `KANJI_ANKI_RELEASE_OWNER`
- `KANJI_ANKI_RELEASE_REPO`
- `KANJI_ANKI_RELEASE_APK_NAME`

If `KANJI_ANKI_RELEASE_APK_NAME` is blank, the updater uses the first `.apk` asset in the latest release.

Release signing configuration:

- Release builds read signing settings from Gradle properties, environment variables, or gitignored `local.properties`.
- Supported keys:
  - `KANJI_ANKI_SIGNING_STORE_FILE`
  - `KANJI_ANKI_SIGNING_STORE_PASSWORD`
  - `KANJI_ANKI_SIGNING_KEY_ALIAS`
  - `KANJI_ANKI_SIGNING_KEY_PASSWORD`
- CI and other automated builds can also override:
  - `KANJI_ANKI_VERSION_NAME`
  - `KANJI_ANKI_VERSION_CODE`
- The local checkout can keep those values in `android-app/local.properties` alongside `sdk.dir`, or supply them as Gradle properties in another private location.
- `./gradlew :app:assembleRelease` becomes an installable signed APK only when those signing properties are present.

GitHub Actions release CI:

- The repo now includes `.github/workflows/android-release.yml`.
- Pushing a tag like `v0.1.1` builds a signed release APK, writes a SHA-256 file, uploads both as workflow artifacts, and publishes them to the matching GitHub release.
- Manual runs are also supported through `workflow_dispatch`, but they still require a semantic tag input in `vMAJOR.MINOR.PATCH` form.
- Required repository secrets:
  - `KANJI_ANKI_SIGNING_KEYSTORE_BASE64`
  - `KANJI_ANKI_SIGNING_STORE_PASSWORD`
  - `KANJI_ANKI_SIGNING_KEY_ALIAS`
  - `KANJI_ANKI_SIGNING_KEY_PASSWORD`
- The workflow derives `versionName` from the tag without the `v` prefix and derives a monotonic `versionCode` from `MAJOR.MINOR.PATCH`.
- The workflow intentionally expects real signing credentials. If those secrets are absent, the release job should fail rather than publishing an unsigned APK.

Operational release notes:

1. Keep the Android signing keystore private and export only its base64 bytes plus passwords into the four GitHub repository secrets above.
2. Push a semantic tag such as `v0.1.1`.
3. The workflow emits `kanji-anki-android-0.1.1.apk` plus `kanji-anki-android-0.1.1.apk.sha256`.
4. The Android in-app updater compares its current `versionName` to the latest GitHub release tag, so the tag format must stay compatible with the app's numeric parser.

Bootstrap flow:

1. Regenerate the oracle fixture from the Python server when service behavior changes.
2. Import `android-app/` into Android Studio or use the checked-in Gradle wrapper.
3. Keep the Android runtime aligned with the live AnkiDroid + Room path while using the checked-in oracle only for parity work and contract checks.
