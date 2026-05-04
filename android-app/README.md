# Android App Scaffold

This directory is the first Android migration slice for the Kotlin/Compose port described in `plans/android-port-github-backup-plan.md`.

Current scope:

- `parity-fixtures/oracle-v1.json` is the frozen Python oracle for settings, kanji detail content, and exported source notes/cards used by the Android fixture gateway.
- `app/` is a Compose shell backed by a Room-cached repository that now prefers AnkiDroid's exported content provider for live note/card reads, falls back to the parity fixture when AnkiDroid is absent or its permission is missing, derives dashboard/problem rows locally, and speaks the planned Android service surface.
- `app/src/main/java/.../data/local` mirrors the current SQLite schema in Room entities and DAOs.
- `app/src/main/java/.../data/sync` now configures periodic WorkManager polling from cached Android settings and keeps the manual/background sync boundary separate from the repository logic.
- `app/src/main/java/.../data/ankidroid` now defines a collection-snapshot contract plus a live content-provider gateway for AnkiDroid's `flashcards` provider, while keeping the fixture parser available as a bootstrap fallback.
- `app/src/main/java/.../data/update` checks GitHub Releases, downloads the latest APK asset, and hands it to Android's installer flow.

Current limitations:

- The runtime still depends on parity fixtures for static kanji detail/bootstrap content, but note/card collection sync now prefers live AnkiDroid reads when the permission is granted.
- The Room cache now owns source snapshot persistence, dashboard derivation, study seed refresh, overview generation, session selection, handwriting enforcement, review progression, and review-log persistence after bootstrap.
- The Compose dashboard can now trigger manual sync directly, and runtime health shows the latest recorded sync run from Room.
- The Compose settings screen can now edit note-model filtering, field mapping, study thresholds, and polling configuration, inspect AnkiDroid install/permission state, request the runtime permission, then resync the local cache immediately after saving or granting access.
- The dashboard now drills directly into whichever kanji row you tap, instead of keeping the detail screen pinned to a single fixture example.
- Static kanji content is still fixture-backed; the Android app does not yet load KANJIDIC2 or KanjiVG itself.
- WorkManager now routes through the same repository boundary that prefers live AnkiDroid reads when available and otherwise falls back to the fixture snapshot, while still following `pollingEnabled` plus `pollingIntervalSeconds` with Android's 15-minute minimum.
- GitHub release auto-update only works against a public release feed. If the source repo stays private, point the updater at a public mirror repo using Gradle properties.

AnkiDroid integration notes:

- The app declares `com.ichi2.anki.permission.READ_WRITE_DATABASE` plus the debug equivalent and queries both release/debug provider authorities.
- If that permission is not granted, sync stays on the parity fixture fallback instead of failing open.
- If AnkiDroid is installed and the permission is granted, manual sync and background sync read notes/cards from `content://com.ichi2.anki.flashcards/...` (or the debug authority).

GitHub release updater configuration:

- `KANJI_ANKI_RELEASE_OWNER`
- `KANJI_ANKI_RELEASE_REPO`
- `KANJI_ANKI_RELEASE_APK_NAME`

If `KANJI_ANKI_RELEASE_APK_NAME` is blank, the updater uses the first `.apk` asset in the latest release.

Release signing configuration:

- Release builds read signing settings from gitignored Gradle/local properties.
- Supported keys:
  - `KANJI_ANKI_SIGNING_STORE_FILE`
  - `KANJI_ANKI_SIGNING_STORE_PASSWORD`
  - `KANJI_ANKI_SIGNING_KEY_ALIAS`
  - `KANJI_ANKI_SIGNING_KEY_PASSWORD`
- The local checkout can keep those values in `android-app/local.properties` alongside `sdk.dir`, or supply them as Gradle properties in another private location.
- `./gradlew :app:assembleRelease` becomes an installable signed APK only when those signing properties are present.

Bootstrap flow:

1. Regenerate the oracle fixture from the Python server when service behavior changes.
2. Import `android-app/` into Android Studio or use the checked-in Gradle wrapper.
3. Replace the remaining parity bootstrap paths incrementally with Room + AnkiDroid implementations while keeping the same use-case surface.
